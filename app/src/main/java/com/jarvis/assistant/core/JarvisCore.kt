package com.jarvis.assistant.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.accessibility.ScreenController
import com.jarvis.assistant.brain.AgentEvent
import com.jarvis.assistant.brain.AgentLoop
import com.jarvis.assistant.brain.AgentRequest
import com.jarvis.assistant.brain.HttpClientFactory
import com.jarvis.assistant.brain.LlmMessage
import com.jarvis.assistant.brain.ProviderConfig
import com.jarvis.assistant.brain.ProviderException
import com.jarvis.assistant.brain.ProviderRegistry
import com.jarvis.assistant.brain.ProviderTester
import com.jarvis.assistant.memory.MemoryEntity
import com.jarvis.assistant.memory.MemoryExtractionWorker
import com.jarvis.assistant.memory.MemoryExtractor
import com.jarvis.assistant.memory.MemoryRepository
import com.jarvis.assistant.memory.MemoryType
import com.jarvis.assistant.persona.GreetingEngine
import com.jarvis.assistant.persona.PersonaBuilder
import com.jarvis.assistant.service.ListeningServiceController
import com.jarvis.assistant.settings.AppSettings
import com.jarvis.assistant.settings.OwnerProfile
import com.jarvis.assistant.settings.PersonaSettings
import com.jarvis.assistant.settings.ProviderSettings
import com.jarvis.assistant.settings.SettingsRepository
import com.jarvis.assistant.settings.VoiceProvider
import com.jarvis.assistant.settings.VoiceSettings
import com.jarvis.assistant.tools.ActionLogStore
import com.jarvis.assistant.tools.ConfirmationGate
import com.jarvis.assistant.tools.MemoryBridge
import com.jarvis.assistant.tools.PermissionGate
import com.jarvis.assistant.tools.ScreenAccess
import com.jarvis.assistant.tools.ScreenResult
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.voice.AndroidSttEngine
import com.jarvis.assistant.voice.AudioChunk
import com.jarvis.assistant.voice.AudioPlayer
import com.jarvis.assistant.voice.BargeInDetector
import com.jarvis.assistant.voice.ElevenLabsTtsEngine
import com.jarvis.assistant.voice.FishAudioTtsEngine
import com.jarvis.assistant.voice.SttEvent
import com.jarvis.assistant.voice.SystemTtsEngine
import com.jarvis.assistant.voice.TtsConfig
import com.jarvis.assistant.voice.TtsEngine
import com.jarvis.assistant.voice.WakeWord
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One unit of speech to be synthesised and played in order. */
sealed interface SpeechItem {
    data class Sentence(val text: String) : SpeechItem
    data object EndOfReply : SpeechItem
}

/** What the synthesis loop hands to the playback loop. */
sealed interface VoiceSink {
    data class Pcm(
        val bytes: ByteArray,
        val sampleRate: Int,
        val fade: Boolean = false,
    ) : VoiceSink

    data class Text(val text: String) : VoiceSink
    data object EndOfReply : VoiceSink
}

/**
 * The single Jarvis runtime.
 *
 * It owns settings, memory, the agent loop, the voice pipeline and the
 * confirmation and permission gates. The Compose screen and the foreground
 * listening service are both thin clients of this object, which is what lets
 * "Jarvis" keep working with the screen off: the service keeps the process
 * alive and drives the same loop the UI uses.
 */
class JarvisCore private constructor(context: Context) : MemoryBridge, ScreenAccess {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = com.jarvis.assistant.memory.JarvisDatabase.get(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val memoryRepository = MemoryRepository(database.memoryDao())
    val actionLogStore = ActionLogStore(appContext, database.actionLogDao(), scope)

    private val personaBuilder = PersonaBuilder(appContext)
    private val greetingEngine = GreetingEngine()

    private val httpClient = HttpClientFactory.create()
    private val providerRegistry = ProviderRegistry(httpClient)
    val providerTester = ProviderTester(providerRegistry)

    private val confirmationGate = object : ConfirmationGate {
        override suspend fun confirm(title: String, detail: String): Boolean =
            requestConfirmation(title, detail)
    }

    private val permissionGate = object : PermissionGate {
        override fun isGranted(permission: String): Boolean = this@JarvisCore.isGranted(permission)
        override suspend fun ensure(permission: String, rationale: String): Boolean =
            this@JarvisCore.ensurePermission(permission, rationale)
    }

    val toolRegistry = ToolRegistry(
        context = appContext,
        confirmation = confirmationGate,
        permissions = permissionGate,
        actionLog = actionLogStore,
        memory = this,
        screen = this,
    )

    private val agentLoop = AgentLoop(providerRegistry, toolRegistry)

    private val audioPlayer = AudioPlayer(appContext)
    private val systemTts = SystemTtsEngine(appContext)
    private val fishTts = FishAudioTtsEngine(httpClient)
    private val elevenTts = ElevenLabsTtsEngine(httpClient)
    private val sttEngine = AndroidSttEngine(appContext)
    private val bargeInDetector = BargeInDetector(appContext)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    private val _state = MutableStateFlow(CoreState())
    val state: StateFlow<CoreState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val speechLock = Any()
    private var speechInbox = Channel<SpeechItem>(Channel.UNLIMITED)
    private var speechOutbox = Channel<VoiceSink>(Channel.UNLIMITED)
    private var speechJob: Job? = null
    private val ttsBuffer = StringBuilder()

    private var turnJob: Job? = null
    private var manualListenJob: Job? = null
    private var serviceJob: Job? = null
    private var bargeJob: Job? = null
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null
    private var permissionDeferred: CompletableDeferred<Boolean>? = null
    private var focusRequest: AudioFocusRequest? = null
    private var nextId = 0L
    private var voiceTestPending = false
    private var greetingInFlight = false

    /**
     * Hands-free follow-ups only continue a conversation the owner started by
     * voice. The launch greeting must not silently open the microphone.
     */
    private var autoListenAllowed = false

    @Volatile
    private var uiVisible = false

    @Volatile
    private var serviceActive = false

    init {
        startSpeechPipeline()
        observeSettings()
        observeMemory()
        loadHistory()
    }

    // Lifecycle ---------------------------------------------------------------

    fun warmUp() = Unit

    /** True while an attached screen can show confirmation and permission prompts. */
    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) maybeGreet()
    }

    fun isUiVisible(): Boolean = uiVisible

    fun attachScreen() {
        setUiVisible(true)
    }

    fun detachScreen() {
        setUiVisible(false)
    }

    private fun observeSettings() {
        scope.launch {
            settings.collect { current ->
                if (current.profile.name.isNotBlank()) {
                    withContext(Dispatchers.IO) { memoryRepository.syncProfile(current.profile) }
                }
            }
        }
    }

    private fun observeMemory() {
        scope.launch {
            runCatching {
                memoryRepository.memories.collect { list ->
                    _state.update { it.copy(memoryCount = list.size) }
                }
            }
        }
    }

    private fun loadHistory() {
        scope.launch {
            val recent = runCatching { memoryRepository.recentTranscript(HISTORY_LIMIT) }
                .getOrDefault(emptyList())
            val mapped = recent.reversed()
                .filter { it.text.isNotBlank() }
                .map { message ->
                    CoreMessage(
                        id = "db_${message.id}",
                        author = if (message.author == "user") ChatAuthor.USER else ChatAuthor.JARVIS,
                        text = message.text,
                        providerName = message.providerName,
                        createdAt = message.createdAt,
                    )
                }
            _state.update { current ->
                if (current.messages.isEmpty()) {
                    current.copy(messages = mapped, historyLoaded = true)
                } else {
                    current.copy(historyLoaded = true)
                }
            }
        }
    }

    // Greeting ----------------------------------------------------------------

    private fun maybeGreet() {
        if (greetingInFlight) return
        greetingInFlight = true
        scope.launch {
            try {
                val current = settings.filter { it.profile.name.isNotBlank() }.first()
                val snapshot = _state.value
                if (snapshot.generating) return@launch
                val lastAt = snapshot.messages.maxOfOrNull { it.createdAt }
                    ?: current.persona.lastGreetingAt
                val gap = System.currentTimeMillis() - maxOf(lastAt, current.persona.lastGreetingAt)
                if (snapshot.messages.isNotEmpty() && gap < GREETING_GAP_MS) return@launch
                addGreeting(current)
            } finally {
                greetingInFlight = false
            }
        }
    }

    private suspend fun addGreeting(current: AppSettings) {
        val greeting = greetingEngine.next(
            profile = current.profile,
            lastGreetingId = current.persona.lastGreetingId,
        )
        _state.update { state ->
            state.copy(
                messages = state.messages + CoreMessage(
                    id = newId("jarvis"),
                    author = ChatAuthor.JARVIS,
                    text = greeting.text,
                ),
            )
        }
        runCatching {
            settingsRepository.update { document ->
                document.copy(
                    persona = document.persona.copy(
                        lastGreetingId = greeting.id,
                        lastGreetingAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val conversationId = memoryRepository.conversationForTurn()
                memoryRepository.addMessage(conversationId, "jarvis", greeting.text)
            }
        }
        speak(greeting.text)
    }

    /** A short answer to being summoned by name, with a fresh greeting after a gap. */
    private fun summon() {
        val current = settings.value
        val title = current.profile.title.ifBlank { "sir" }
        val sinceGreeting = System.currentTimeMillis() - current.persona.lastGreetingAt
        if (sinceGreeting > SUMMON_GREETING_GAP_MS) {
            scope.launch { addGreeting(current) }
            return
        }
        val line = "Yes, $title?"
        speak(line)
    }

    // Conversation ------------------------------------------------------------

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val current = settings.value
        if (current.profile.name.isBlank()) {
            _state.update { it.copy(error = "Complete onboarding before sending a request.") }
            return
        }

        turnJob?.cancel()
        interruptSpeech()
        ttsBuffer.setLength(0)

        val assistantId = newId("reply")
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.streaming) it.copy(streaming = false) else it
                } + CoreMessage(
                    id = newId("user"),
                    author = ChatAuthor.USER,
                    text = clean,
                ) + CoreMessage(
                    id = assistantId,
                    author = ChatAuthor.JARVIS,
                    text = "",
                    streaming = true,
                ),
                generating = true,
                listening = false,
                partialTranscript = "",
                activeProvider = null,
                toolActivity = null,
                error = null,
                notice = null,
            )
        }

        turnJob = scope.launch { runTurn(clean, assistantId, current) }
    }

    private suspend fun runTurn(userText: String, assistantId: String, current: AppSettings) {
        var conversationId: Long? = null
        try {
            conversationId = withContext(Dispatchers.IO) {
                val id = memoryRepository.conversationForTurn()
                memoryRepository.addMessage(id, "user", userText)
                id
            }

            val providers = resolveProviders(current)
            if (providers.isEmpty()) {
                failTurn(
                    assistantId,
                    "No AI provider is configured. Open Settings, add an API key, then try again.",
                    conversationId,
                )
                return
            }

            val memoryContext = withContext(Dispatchers.IO) {
                runCatching { memoryRepository.promptContext(userText) }.getOrDefault("")
            }
            val systemPrompt = withContext(Dispatchers.IO) {
                personaBuilder.build(current, memoryContext)
            }

            agentLoop.run(
                AgentRequest(
                    systemPrompt = systemPrompt,
                    history = historyForPrompt(),
                    userMessage = userText,
                    providers = providers,
                    tools = toolRegistry.definitions(),
                ),
            ).collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> onTextDelta(assistantId, event)
                    is AgentEvent.ProviderFallback -> _state.update {
                        it.copy(toolActivity = "${event.from} is unavailable. Trying ${event.to}...")
                    }

                    is AgentEvent.ToolStarted -> {
                        flushTtsBuffer()
                        _state.update { state ->
                            state.copy(
                                messages = state.messages.map { message ->
                                    if (message.id == assistantId && message.text.isNotBlank()) {
                                        message.copy(text = message.text.trimEnd() + "\n\n")
                                    } else {
                                        message
                                    }
                                },
                                toolActivity = toolActivityLabel(event.name),
                            )
                        }
                    }

                    is AgentEvent.ToolFinished -> _state.update { state ->
                        state.copy(
                            messages = state.messages.map { message ->
                                if (message.id == assistantId && !message.tools.contains(event.name)) {
                                    message.copy(tools = message.tools + event.name)
                                } else {
                                    message
                                }
                            },
                            toolActivity = if (event.success) "Done." else "A tool reported a problem.",
                        )
                    }

                    is AgentEvent.Completed -> {
                        completeTurn(assistantId, conversationId)
                    }

                    is AgentEvent.Error -> failTurn(assistantId, event.message, conversationId)
                }
            }
        } catch (e: CancellationException) {
            _state.update { state ->
                state.copy(
                    messages = state.messages
                        .filterNot { it.id == assistantId && it.text.isBlank() }
                        .map { if (it.id == assistantId) it.copy(streaming = false) else it },
                    generating = false,
                    activeProvider = null,
                    toolActivity = null,
                )
            }
            interruptSpeech()
            throw e
        } catch (e: Exception) {
            failTurn(
                assistantId,
                e.message?.take(400) ?: "Jarvis could not complete that request.",
                conversationId,
            )
        }
    }

    private fun onTextDelta(assistantId: String, event: AgentEvent.TextDelta) {
        ttsBuffer.append(event.text)
        queueCompletedSentences()
        _state.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == assistantId) {
                        message.copy(
                            text = message.text + event.text,
                            providerName = event.providerName,
                            streaming = true,
                        )
                    } else {
                        message
                    }
                },
                activeProvider = event.providerName,
                toolActivity = null,
            )
        }
    }

    private fun completeTurn(assistantId: String, conversationId: Long?) {
        flushTtsBuffer()
        speechInbox.trySend(SpeechItem.EndOfReply)
        _state.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id == assistantId) {
                        message.copy(text = message.text.trimEnd(), streaming = false)
                    } else {
                        message
                    }
                },
                generating = false,
                toolActivity = null,
            )
        }
        val text = _state.value.messages.firstOrNull { it.id == assistantId }?.text.orEmpty()
        if (conversationId != null) {
            persistAssistant(conversationId, assistantId, _state.value.activeProvider)
            if (text.isNotBlank()) {
                // Phase 4: let the model distill durable facts a few minutes after
                // the conversation goes quiet.
                MemoryExtractionWorker.schedule(appContext)
            }
        }
    }

    private fun persistAssistant(
        conversationId: Long?,
        assistantId: String,
        providerName: String?,
    ) {
        if (conversationId == null) return
        val text = _state.value.messages.firstOrNull { it.id == assistantId }?.text?.trim().orEmpty()
        if (text.isBlank()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                memoryRepository.addMessage(conversationId, "jarvis", text, providerName)
            }
        }
    }

    private fun failTurn(assistantId: String, message: String, conversationId: Long?) {
        val spokeError = message.length <= 220
        _state.update { state ->
            val target = state.messages.firstOrNull { it.id == assistantId }
            val hasText = !target?.text.isNullOrBlank()
            state.copy(
                messages = state.messages.map { chat ->
                    when {
                        chat.id != assistantId -> chat
                        hasText -> chat.copy(streaming = false)
                        else -> chat.copy(text = message, streaming = false, isError = true)
                    }
                },
                generating = false,
                activeProvider = null,
                toolActivity = null,
                error = message,
            )
        }
        interruptSpeech()
        if (spokeError && (uiVisible || serviceActive)) speak(message)
        persistAssistant(conversationId, assistantId, null)
    }

    fun stop() {
        val job = turnJob
        turnJob = null
        job?.cancel()
        rejectConfirmation()
        permissionDeferred?.complete(false)
        permissionDeferred = null
        _state.update {
            it.copy(generating = false, toolActivity = null, pendingPermission = null)
        }
        interruptSpeech()
        stopListening()
    }

    fun retryLast() {
        val snapshot = _state.value
        if (snapshot.generating) return
        val lastUserIndex = snapshot.messages.indexOfLast { it.author == ChatAuthor.USER }
        if (lastUserIndex < 0) return
        val lastUser = snapshot.messages[lastUserIndex]
        _state.update { it.copy(messages = it.messages.take(lastUserIndex), error = null) }
        send(lastUser.text)
    }

    private fun historyForPrompt(): List<LlmMessage> =
        _state.value.messages
            .filter { !it.isError && !it.streaming && it.text.isNotBlank() && !it.id.startsWith("reply") }
            .map { message ->
                if (message.author == ChatAuthor.USER) {
                    LlmMessage.user(message.text)
                } else {
                    LlmMessage.assistant(message.text)
                }
            }
            .takeLast(HISTORY_FOR_PROMPT)

    private fun toolActivityLabel(name: String): String = when (name) {
        "get_current_time" -> "Checking the current time..."
        "get_device_status" -> "Reading the phone status..."
        "open_app" -> "Opening the app..."
        "search_youtube" -> "Searching YouTube..."
        "web_search" -> "Searching the web..."
        "control_media" -> "Adjusting the music..."
        "set_volume" -> "Setting the volume..."
        "set_ringer_mode" -> "Changing the ringer..."
        "control_flashlight" -> "Switching the torch..."
        "set_timer" -> "Setting a timer..."
        "set_alarm" -> "Setting an alarm..."
        "call_contact" -> "Preparing the call..."
        "send_sms" -> "Preparing the message..."
        "navigate_to" -> "Opening navigation..."
        "create_calendar_event" -> "Opening the calendar..."
        "share_text" -> "Opening the share sheet..."
        "open_settings_screen" -> "Opening settings..."
        "remember_about_owner" -> "Committing that to memory..."
        "search_memory" -> "Searching memory..."
        "forget_memory" -> "Forgetting that..."
        "read_screen" -> "Reading the screen..."
        "tap_screen" -> "Tapping..."
        "type_on_screen" -> "Typing..."
        "scroll_screen" -> "Scrolling..."
        "press_system_key" -> "Pressing a system button..."
        else -> "Using ${name.replace('_', ' ')}..."
    }

    // Voice output ------------------------------------------------------------

    private fun startSpeechPipeline() {
        synchronized(speechLock) { startSpeechPipelineLocked() }
    }

    private fun startSpeechPipelineLocked() {
        if (speechJob?.isActive == true) return
        val inbox = speechInbox
        val outbox = speechOutbox
        speechJob = scope.launch {
            coroutineScope {
                launch { synthesisLoop(inbox, outbox) }
                launch { playbackLoop(outbox) }
            }
        }
    }

    /** One synthesis coroutine, so speech is always emitted in sentence order. */
    private suspend fun synthesisLoop(inbox: Channel<SpeechItem>, outbox: Channel<VoiceSink>) {
        for (item in inbox) {
            when (item) {
                is SpeechItem.Sentence -> synthesizeSentence(item.text, outbox)
                SpeechItem.EndOfReply -> outbox.send(VoiceSink.EndOfReply)
            }
        }
    }

    private suspend fun synthesizeSentence(text: String, outbox: Channel<VoiceSink>) {
        if (text.isBlank()) return
        val voice = settings.value.voice
        val config = ttsConfig(voice)
        val engine: TtsEngine? = when (voice.provider) {
            VoiceProvider.SYSTEM -> null
            VoiceProvider.FISH_AUDIO -> fishTts
            VoiceProvider.ELEVENLABS -> elevenTts
        }

        if (engine == null) {
            outbox.send(VoiceSink.Text(text))
            return
        }

        var pending: AudioChunk? = null
        var failure: String? = null
        try {
            engine.synthesize(text, config).collect { chunk ->
                pending?.let { previous ->
                    outbox.send(VoiceSink.Pcm(previous.bytes, previous.sampleRate))
                }
                pending = chunk
            }
            failure = engine.lastError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e.message ?: e::class.simpleName ?: "unknown error"
        }

        val last = pending
        if (last == null) {
            val error = failure ?: "The ${voice.provider.label} voice returned no audio."
            if (voice.fallbackToSystem) {
                outbox.send(VoiceSink.Text(text))
                reportVoice("$error Falling back to the Android system voice.")
            } else {
                reportVoice(error)
            }
            return
        }

        // Only the final chunk of an utterance is faded, so speech inside a
        // sentence is never dipped.
        outbox.send(VoiceSink.Pcm(last.bytes, last.sampleRate, fade = true))
        failure?.let { reportVoice(it) }
    }

    private suspend fun playbackLoop(outbox: Channel<VoiceSink>) {
        for (payload in outbox) {
            when (payload) {
                is VoiceSink.Pcm -> audioPlayer.write(payload.bytes, payload.sampleRate, payload.fade)
                is VoiceSink.Text -> {
                    // Release any cloud track first: system TTS owns the speaker
                    // while it speaks.
                    audioPlayer.finish()
                    systemTts.speak(payload.text, settings.value.voice.language)
                    audioPlayer.lastError?.let { reportVoice(it) }
                }

                VoiceSink.EndOfReply -> {
                    audioPlayer.finish()
                    onReplySpoken()
                }
            }
        }
    }

    private fun onReplySpoken() {
        bargeJob?.cancel()
        bargeJob = null
        abandonAudioFocus()
        _state.update { it.copy(speaking = false) }
        if (voiceTestPending) {
            voiceTestPending = false
            _state.update { it.copy(voiceStatus = "The configured voice is working.") }
        }
        val voice = settings.value.voice
        if (!autoListenAllowed) return
        if (!voice.continuousListening || serviceActive || !uiVisible) return
        if (_state.value.generating) return
        if (isGranted(Manifest.permission.RECORD_AUDIO)) startListeningInternal(interrupt = false)
    }

    private fun beginSpeaking() {
        if (_state.value.speaking) return
        manualListenJob?.cancel()
        manualListenJob = null
        requestAudioFocus()
        bargeJob = bargeInDetector.start { onBargeIn() }
        _state.update { it.copy(speaking = true) }
    }

    private fun onBargeIn() {
        if (!_state.value.speaking) return
        interruptSpeech()
        // The owner interrupted to speak. In the service the continuous loop
        // resumes on its own; in the app, listen immediately.
        if (!serviceActive && uiVisible) startListeningInternal(interrupt = false)
    }

    /** Speaks a line in the configured voice. Safe to call from any thread. */
    fun speak(text: String) {
        if (text.isBlank()) return
        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return
        beginSpeaking()
        sentences.forEach { speechInbox.trySend(SpeechItem.Sentence(it)) }
        speechInbox.trySend(SpeechItem.EndOfReply)
    }

    fun speakGreeting() {
        maybeGreet()
    }

    fun testVoice() {
        voiceTestPending = true
        _state.update { it.copy(voiceStatus = "Testing the configured voice...") }
        speak("Good evening. This is Jarvis. My voice is configured and ready.")
    }

    private fun reportVoice(message: String) {
        _state.update { it.copy(voiceStatus = message, notice = message) }
        scope.launch {
            delay(9_000)
            _state.update { if (it.notice == message) it.copy(notice = null) else it }
        }
    }

    private fun interruptSpeech() {
        synchronized(speechLock) {
            speechJob?.cancel()
            speechJob = null
            while (true) {
                if (!speechInbox.tryReceive().isSuccess) break
            }
            while (true) {
                if (!speechOutbox.tryReceive().isSuccess) break
            }
            speechInbox = Channel(Channel.UNLIMITED)
            speechOutbox = Channel(Channel.UNLIMITED)
            audioPlayer.stop()
            systemTts.stop()
            bargeJob?.cancel()
            bargeJob = null
            bargeInDetector.stop()
            abandonAudioFocus()
            _state.update { it.copy(speaking = false) }
            startSpeechPipelineLocked()
        }
    }

    private fun queueCompletedSentences() {
        val text = ttsBuffer.toString()
        if (text.isEmpty()) return
        var boundary = -1
        text.forEachIndexed { index, character ->
            if (character == '.' || character == '!' || character == '?' || character == '\n') {
                boundary = index
            }
        }
        if (boundary < 0) {
            // A model that streams a long clause without punctuation should still
            // start speaking rather than wait for the full paragraph.
            if (text.length > 240) {
                val space = text.lastIndexOf(' ')
                if (space > 80) boundary = space
            }
        }
        if (boundary < 0) return
        val ready = text.substring(0, boundary + 1)
        ttsBuffer.delete(0, boundary + 1)
        splitSentences(ready).forEach { speechInbox.trySend(SpeechItem.Sentence(it)) }
    }

    private fun flushTtsBuffer() {
        val remaining = ttsBuffer.toString().trim()
        ttsBuffer.setLength(0)
        if (remaining.isNotEmpty()) {
            splitSentences(remaining).forEach { speechInbox.trySend(SpeechItem.Sentence(it)) }
        }
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?…])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.any { character -> character.isLetterOrDigit() } }

    private fun ttsConfig(voice: VoiceSettings): TtsConfig {
        val key = runCatching { settingsRepository.decryptVoiceApiKey(voice) }.getOrDefault("")
        return TtsConfig(
            provider = voice.provider,
            apiKey = key,
            voiceId = voice.voiceId,
            model = voice.model,
            speed = voice.speed,
            fallbackToSystem = voice.fallbackToSystem,
            language = voice.language.ifBlank { "en" },
        )
    }

    // Voice input -------------------------------------------------------------

    fun startListening() = startListeningInternal(interrupt = true)

    /**
     * Opens the microphone once for the owner's next sentence.
     *
     * [interrupt] is false when this is called from the end of a reply: the
     * speech pipeline has already finished, and interrupting it there would
     * cancel the very coroutine that is asking to listen.
     */
    private fun startListeningInternal(interrupt: Boolean) {
        val snapshot = _state.value
        if (snapshot.generating) {
            _state.update { it.copy(error = "One moment. I am still working on the last request.") }
            return
        }
        if (!isGranted(Manifest.permission.RECORD_AUDIO)) {
            _state.update {
                it.copy(error = "Microphone permission is required. Grant it in Settings, Permissions.")
            }
            return
        }
        autoListenAllowed = true
        if (interrupt) interruptSpeech()
        manualListenJob?.cancel()
        manualListenJob = scope.launch {
            val text = listenOnce(localeFor(settings.value.voice.language), reportErrors = true)
            if (text.isNullOrBlank()) {
                _state.update { it.copy(listening = false) }
                return@launch
            }
            send(text)
        }
    }

    fun stopListening() {
        autoListenAllowed = false
        manualListenJob?.cancel()
        manualListenJob = null
        _state.update { it.copy(listening = false, partialTranscript = "") }
    }

    fun toggleListening() {
        if (_state.value.listening) stopListening() else startListening()
    }

    private suspend fun listenOnce(locale: Locale, reportErrors: Boolean): String? {
        if (!isGranted(Manifest.permission.RECORD_AUDIO)) {
            if (reportErrors) {
                _state.update { it.copy(error = "Microphone permission is required.") }
            }
            return null
        }
        _state.update { it.copy(listening = true, partialTranscript = "", error = null) }
        var result: String? = null
        var errorText: String? = null
        try {
            withTimeoutOrNull(LISTEN_WINDOW_MS) {
                sttEngine.listen(locale).collect { event ->
                    when (event) {
                        is SttEvent.Partial -> _state.update { it.copy(partialTranscript = event.text) }
                        is SttEvent.Final -> result = event.text
                        is SttEvent.Error -> errorText = event.message
                        SttEvent.End -> Unit
                    }
                }
            }
        } catch (e: CancellationException) {
            _state.update { it.copy(listening = false, partialTranscript = "") }
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: "Speech recognition failed."
        } finally {
            _state.update { it.copy(listening = false, partialTranscript = "") }
        }
        if (result == null && reportErrors && errorText != null) {
            _state.update { it.copy(error = errorText) }
        }
        return result
    }

    private fun localeFor(tag: String): Locale {
        val cleaned = tag.trim().ifBlank { "en" }
        return runCatching { Locale.forLanguageTag(cleaned) }
            .getOrDefault(Locale.UK)
    }

    // Always-on listening -----------------------------------------------------

    fun startBackgroundListening(): Boolean {
        if (!isGranted(Manifest.permission.RECORD_AUDIO)) {
            _state.update {
                it.copy(error = "Microphone permission is required for background listening.")
            }
            return false
        }
        if (!serviceActive) {
            val started = ListeningServiceController.start(appContext)
            if (!started) {
                _state.update {
                    it.copy(
                        error = "Android blocked the background service. Open Jarvis and try again.",
                    )
                }
                return false
            }
        }
        scope.launch {
            settingsRepository.update { it.copy(voice = it.voice.copy(alwaysListening = true)) }
        }
        return true
    }

    fun stopBackgroundListening() {
        ListeningServiceController.stop(appContext)
        onListeningServiceStopped()
        scope.launch {
            settingsRepository.update { it.copy(voice = it.voice.copy(alwaysListening = false)) }
        }
    }

    fun onListeningServiceStarted() {
        if (serviceActive) return
        serviceActive = true
        _state.update { it.copy(serviceActive = true) }
        if (serviceJob?.isActive == true) return
        serviceJob = scope.launch {
            delay(STARTUP_GRACE_MS)
            while (isActive) {
                if (_state.value.speaking || _state.value.generating) {
                    delay(250)
                    continue
                }
                if (manualListenJob?.isActive == true) {
                    // The owner opened the app and tapped the microphone. One
                    // recogniser at a time, or Android returns RECOGNIZER_BUSY.
                    delay(250)
                    continue
                }
                if (!isGranted(Manifest.permission.RECORD_AUDIO)) {
                    _state.update {
                        it.copy(
                            notice = "Microphone permission is missing, so background listening is paused.",
                        )
                    }
                    delay(5_000)
                    continue
                }
                val text = listenOnce(
                    localeFor(settings.value.voice.language),
                    reportErrors = false,
                )
                if (text.isNullOrBlank()) {
                    delay(LISTEN_RETRY_MS)
                    continue
                }
                handleServiceUtterance(text)
            }
        }
    }

    fun onListeningServiceStopped() {
        serviceActive = false
        serviceJob?.cancel()
        serviceJob = null
        _state.update {
            it.copy(serviceActive = false, listening = false, partialTranscript = "")
        }
    }

    private fun handleServiceUtterance(text: String) {
        val voice = settings.value.voice
        val parsed = WakeWord.parse(text, voice.wakeWord, voice.requireWakeWord)
        if (!parsed.addressed) return
        if (parsed.summonsOnly) {
            summon()
            return
        }
        send(parsed.command)
    }

    // Audio focus -------------------------------------------------------------

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    interruptSpeech()
                }
            }
            .build()
            .also { focusRequest = it }
        val granted = runCatching { manager.requestAudioFocus(request) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            reportVoice("Audio focus was refused. Playing through the system speaker anyway.")
        }
        return granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        val request = focusRequest ?: return
        runCatching { manager.abandonAudioFocusRequest(request) }
    }

    // Gates -------------------------------------------------------------------

    private suspend fun requestConfirmation(title: String, detail: String): Boolean {
        if (!uiVisible) {
            speak("$title I need you to open Jarvis to confirm that.")
            return false
        }
        if (confirmationDeferred != null) return false
        val deferred = CompletableDeferred<Boolean>()
        confirmationDeferred = deferred
        _state.update {
            it.copy(
                pendingConfirmation = PendingConfirmation(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    detail = detail,
                ),
            )
        }
        return try {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() } ?: false
        } finally {
            if (confirmationDeferred === deferred) confirmationDeferred = null
            _state.update { it.copy(pendingConfirmation = null) }
        }
    }

    fun approveConfirmation() {
        val deferred = confirmationDeferred
        confirmationDeferred = null
        _state.update { it.copy(pendingConfirmation = null) }
        deferred?.complete(true)
    }

    fun rejectConfirmation() {
        val deferred = confirmationDeferred
        confirmationDeferred = null
        _state.update { it.copy(pendingConfirmation = null) }
        deferred?.complete(false)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun ensurePermission(permission: String, rationale: String): Boolean {
        if (isGranted(permission)) return true
        if (!uiVisible) {
            _state.update { it.copy(notice = "Open Jarvis to allow: $rationale") }
            return false
        }
        if (permissionDeferred != null) return false
        val deferred = CompletableDeferred<Boolean>()
        permissionDeferred = deferred
        _state.update { it.copy(pendingPermission = PendingPermission(permission, rationale)) }
        return try {
            val answer = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() } ?: false
            answer || isGranted(permission)
        } finally {
            if (permissionDeferred === deferred) permissionDeferred = null
            _state.update { it.copy(pendingPermission = null) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val deferred = permissionDeferred
        permissionDeferred = null
        _state.update { it.copy(pendingPermission = null) }
        deferred?.complete(granted)
    }

    // Memory ------------------------------------------------------------------

    override suspend fun remember(text: String, type: String, importance: Int): String {
        val created = memoryRepository.remember(text, type, importance, source = "tool")
        return if (created) {
            "Saved to memory: $text"
        } else {
            "That was already in memory. I refreshed the entry."
        }
    }

    override suspend fun search(query: String, limit: Int): List<String> =
        memoryRepository.search(query, limit).map { it.text }

    override suspend fun forget(query: String): String {
        val matches = memoryRepository.search(query, 20)
        matches.forEach { memoryRepository.deleteMemory(it.id) }
        return if (matches.isEmpty()) {
            "Nothing in memory matched \"$query\"."
        } else {
            "Forgot ${matches.size} ${if (matches.size == 1) "memory" else "memories"}."
        }
    }

    fun deleteMemory(id: Long) {
        scope.launch {
            memoryRepository.deleteMemory(id)
            setNotice("Memory deleted.")
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        scope.launch {
            memoryRepository.updateMemory(memory)
            setNotice("Memory updated.")
        }
    }

    fun addMemory(text: String, type: String = MemoryType.FACT, importance: Int = 3) {
        if (text.trim().length < 3) {
            setError("A memory needs at least a few words.")
            return
        }
        scope.launch {
            memoryRepository.remember(text.trim(), type, importance, source = "manual")
            setNotice("Saved to memory.")
        }
    }

    fun forgetEverything() {
        scope.launch {
            memoryRepository.forgetEverything()
            // The transcript on screen is part of the same memory, so clear it
            // too rather than leaving the deleted conversation visible.
            _state.update {
                it.copy(
                    messages = emptyList(),
                    memoryCount = 0,
                    notice = "All memories and conversations are gone.",
                )
            }
        }
    }

    fun exportMemories(uri: Uri) {
        scope.launch {
            val result = runCatching {
                val json = memoryRepository.exportJson()
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    } ?: error("The file could not be opened.")
                }
            }
            _state.update {
                it.copy(
                    notice = if (result.isSuccess) "Memory exported." else null,
                    error = result.exceptionOrNull()?.message?.let { message ->
                        "Export failed: $message"
                    },
                )
            }
        }
    }

    fun importMemories(uri: Uri) {
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("The file could not be opened.")
                }
                memoryRepository.importJson(text)
            }
            result.fold(
                onSuccess = { added ->
                    setNotice("Import complete. $added new ${if (added == 1) "memory" else "memories"}.")
                },
                onFailure = { error ->
                    setError("Import failed: ${error.message ?: "invalid file"}")
                },
            )
        }
    }

    suspend fun extractMemoriesFromLatestConversation(): Int {
        val extractor = MemoryExtractor(
            context = appContext,
            registry = providerRegistry,
            memory = memoryRepository,
            settingsRepository = settingsRepository,
        )
        val saved = extractor.extractFromLatestConversation()
        if (saved > 0) {
            _state.update {
                it.copy(notice = "Remembered $saved new ${if (saved == 1) "detail" else "details"}.")
            }
        }
        return saved
    }

    // Screen control ----------------------------------------------------------

    override fun ready(): Boolean =
        JarvisAccessibilityService.isConnected() && settings.value.safety.screenControlEnabled

    override fun blockedApp(): String? {
        if (!settings.value.safety.screenControlEnabled) return "Screen control"
        val controller = JarvisAccessibilityService.controller() ?: return null
        val packageName = controller.foregroundPackage()
        if (packageName.isBlank() || packageName == appContext.packageName) return null
        val label = controller.appLabel(packageName)
        val haystack = "$packageName $label".lowercase(Locale.UK)
        val hit = settings.value.safety.blocklistKeywords.firstOrNull { keyword ->
            keyword.isNotBlank() && haystack.contains(keyword.trim().lowercase(Locale.UK))
        } ?: return null
        return label.ifBlank { hit }
    }

    override suspend fun readScreen(): ScreenResult = withScreen { controller ->
        val snapshot = controller.snapshot()
        if (snapshot.packageName.isBlank()) {
            ScreenResult(false, "I cannot see the screen yet.")
        } else {
            val header = "On ${snapshot.appLabel.ifBlank { snapshot.packageName }} " +
                "(${snapshot.packageName}), ${snapshot.nodeCount} visible elements:"
            ScreenResult(true, "$header\n${snapshot.text.take(3500)}")
        }
    }

    override suspend fun tap(text: String): ScreenResult = withScreen { it.tap(text) }

    override suspend fun type(text: String): ScreenResult = withScreen { it.type(text) }

    override suspend fun scroll(forward: Boolean): ScreenResult = withScreen { it.scroll(forward) }

    override suspend fun press(key: String): ScreenResult = withScreen { it.press(key) }

    private suspend fun withScreen(
        action: (ScreenController) -> ScreenResult,
    ): ScreenResult = withContext(Dispatchers.Main) {
        val controller = JarvisAccessibilityService.controller()
            ?: return@withContext ScreenResult(
                false,
                "Screen control is not enabled. Enable Jarvis in accessibility settings.",
            )
        runCatching { action(controller) }
            .getOrElse { ScreenResult(false, "That screen action failed: ${it.message}") }
    }

    // Settings and small helpers ---------------------------------------------

    fun locale(): Locale = localeFor(settings.value.voice.language)

    fun defaultPersonaPrompt(): String = personaBuilder.defaultPrompt()

    fun refreshToolCatalogue(): List<com.jarvis.assistant.tools.ToolSummary> =
        toolRegistry.catalogue()

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun setError(message: String) = _state.update { it.copy(error = message, notice = null) }

    fun setNotice(message: String) = _state.update { it.copy(notice = message, error = null) }

    fun clearVoiceStatus() = _state.update { it.copy(voiceStatus = null) }

    fun saveProfile(profile: OwnerProfile): Boolean {
        val name = profile.name.trim()
        if (name.isEmpty()) {
            setError("Please enter your name before we begin.")
            return false
        }
        val clean = profile.copy(
            name = name,
            title = profile.title.trim().ifBlank { "sir" },
            occupation = profile.occupation.trim(),
            preferences = profile.preferences.trim(),
        )
        scope.launch {
            runCatching {
                settingsRepository.saveProfile(clean)
                memoryRepository.syncProfile(clean)
                _state.update { it.copy(notice = "Profile saved.") }
                if (uiVisible) maybeGreet()
            }.onFailure { error ->
                setError(error.message?.take(400) ?: "Could not save your profile.")
            }
        }
        return true
    }

    fun savePersona(tone: String, customPrompt: String?) {
        scope.launch {
            runCatching {
                settingsRepository.savePersona(
                    PersonaSettings(tone = tone, customPrompt = customPrompt),
                )
                setNotice("Persona saved. It applies to the next message.")
            }.onFailure { error ->
                setError(error.message?.take(400) ?: "Could not save the persona.")
            }
        }
    }

    fun isValidProviderUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value.trim())
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    suspend fun resolveProviders(current: AppSettings): List<ProviderConfig> =
        withContext(Dispatchers.IO) {
            val default = current.defaultProvider
            val ordered = buildList {
                if (default != null) add(default)
                addAll(current.providers.filter { it.id != default?.id })
            }
            var keyError: String? = null
            val configs = mutableListOf<ProviderConfig>()
            for (provider in ordered) {
                if (!provider.enabled || !provider.hasSavedKey) continue
                try {
                    val key = settingsRepository.decryptApiKey(provider)
                    if (key.isBlank()) {
                        keyError = "The saved API key for ${provider.name} is empty. Re-enter it."
                    } else {
                        configs += ProviderConfig(
                            id = provider.id,
                            name = provider.name,
                            kind = provider.kind,
                            baseUrl = provider.baseUrl.trim().trimEnd('/'),
                            model = provider.model.trim(),
                            apiKey = key,
                        )
                    }
                } catch (e: Exception) {
                    keyError = e.message?.take(400) ?: "A saved API key could not be decrypted."
                }
            }
            if (configs.isEmpty() && keyError != null) throw ProviderException(keyError)
            configs
        }

    fun providerConfig(provider: ProviderSettings, apiKey: String): ProviderConfig = ProviderConfig(
        id = provider.id,
        name = provider.name,
        kind = provider.kind,
        baseUrl = provider.baseUrl.trim().trimEnd('/'),
        model = provider.model.trim(),
        apiKey = apiKey,
    )

    private fun newId(prefix: String): String {
        nextId += 1
        return "${prefix}_${System.currentTimeMillis()}_$nextId"
    }

    companion object {
        private const val HISTORY_LIMIT = 30
        private const val HISTORY_FOR_PROMPT = 30
        private const val GREETING_GAP_MS = 3 * 60 * 60 * 1000L
        private const val SUMMON_GREETING_GAP_MS = 15 * 60 * 1000L
        private const val CONFIRM_TIMEOUT_MS = 120_000L
        private const val LISTEN_WINDOW_MS = 25_000L
        private const val LISTEN_RETRY_MS = 900L
        private const val STARTUP_GRACE_MS = 600L

        @Volatile
        private var instance: JarvisCore? = null

        fun get(context: Context): JarvisCore =
            instance ?: synchronized(this) {
                instance ?: JarvisCore(context).also { instance = it }
            }
    }
}
