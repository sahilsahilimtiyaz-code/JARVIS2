package com.jarvis.assistant.ui

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.brain.AgentEvent
import com.jarvis.assistant.brain.AgentLoop
import com.jarvis.assistant.brain.AgentRequest
import com.jarvis.assistant.brain.HttpClientFactory
import com.jarvis.assistant.brain.LlmMessage
import com.jarvis.assistant.brain.ProviderConfig
import com.jarvis.assistant.brain.ProviderException
import com.jarvis.assistant.brain.ProviderKind
import com.jarvis.assistant.brain.ProviderRegistry
import com.jarvis.assistant.brain.ProviderTester
import com.jarvis.assistant.persona.GreetingEngine
import com.jarvis.assistant.persona.PersonaBuilder
import com.jarvis.assistant.settings.AppSettings
import com.jarvis.assistant.settings.OwnerProfile
import com.jarvis.assistant.settings.PersonaSettings
import com.jarvis.assistant.settings.ProviderSettings
import com.jarvis.assistant.settings.SettingsRepository
import com.jarvis.assistant.settings.VoiceProvider
import com.jarvis.assistant.settings.VoiceSettings
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.voice.AndroidSttEngine
import com.jarvis.assistant.voice.AudioPlayer
import com.jarvis.assistant.voice.BargeInDetector
import com.jarvis.assistant.voice.ElevenLabsTtsEngine
import com.jarvis.assistant.voice.FishAudioTtsEngine
import com.jarvis.assistant.voice.SpeechPayload
import com.jarvis.assistant.voice.SttEvent
import com.jarvis.assistant.voice.SystemTtsEngine
import com.jarvis.assistant.voice.TtsConfig
import com.jarvis.assistant.voice.TtsEngine
import java.net.URI
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One unit of speech to be synthesised and played in order. */
sealed interface SpeechItem {
    data class Sentence(val text: String) : SpeechItem
    data object EndOfReply : SpeechItem
}

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val personaBuilder = PersonaBuilder(appContext)
    private val greetingEngine = GreetingEngine()
    private val toolRegistry = ToolRegistry(appContext)
    private val providerRegistry = ProviderRegistry(HttpClientFactory.create())
    private val providerTester = ProviderTester(providerRegistry)
    private val agentLoop = AgentLoop(providerRegistry, toolRegistry)

    private val audioPlayer = AudioPlayer(appContext)
    private val sttEngine = AndroidSttEngine(appContext)
    private val systemTtsEngine = SystemTtsEngine(appContext)
    private val fishTtsEngine = FishAudioTtsEngine(HttpClientFactory.create())
    private val elevenTtsEngine = ElevenLabsTtsEngine(HttpClientFactory.create())
    private val bargeInDetector = BargeInDetector(appContext)

    private var sttJob: kotlinx.coroutines.Job? = null
    private val speechInbox = Channel<SpeechItem>(Channel.UNLIMITED)
    private val speechOutbox = Channel<SpeechPayload>(Channel.UNLIMITED)
    private var speechPipeline: kotlinx.coroutines.Job? = null
    private val ttsBuffer = StringBuilder()

    private val _ui = MutableStateFlow(JarvisUiState())
    val ui: StateFlow<JarvisUiState> = _ui.asStateFlow()

    private var sendJob: Job? = null
    private var greetingShown = false
    private var nextMessageNumber = 0L
    private var speaking = false

    init {
        startSpeechPipeline()

        viewModelScope.launch {
            val prompt = withContext(Dispatchers.IO) { personaBuilder.defaultPrompt() }
            _ui.update { it.copy(defaultPersonaPrompt = prompt) }
        }

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _ui.update { state ->
                    state.copy(
                        settings = settings,
                        loading = false,
                        onboardingComplete = settings.profile.name.isNotBlank(),
                    )
                }
                if (
                    settings.profile.name.isNotBlank() &&
                    !greetingShown &&
                    _ui.value.messages.isEmpty()
                ) {
                    addGreeting(settings)
                }
            }
        }
    }

    /**
     * Runs the speech producer and consumer for one reply.
     *
     * The producer starts synthesis as soon as a sentence arrives and the
     * consumer plays payloads in order, so by the time sentence N finishes
     * playing, sentence N+1 is usually already waiting. A mutex keeps the sends
     * ordered even though several sentences are being synthesised at once.
     */
    private fun startSpeechPipeline() {
        speechPipeline?.cancel()
        val order = Mutex()
        speechPipeline = viewModelScope.launch {
            coroutineScope {
                launch {
                    for (item in speechInbox) {
                        when (item) {
                            is SpeechItem.Sentence -> launch {
                                val config = ttsConfig()
                                val engine = engineFor(config.provider)
                                val payload = runCatching {
                                    engine.synthesize(item.text, config)
                                }.getOrNull().let { result ->
                                    result ?: if (config.fallbackToSystem) {
                                        SpeechPayload.SystemText(item.text)
                                    } else {
                                        null
                                    }
                                }
                                if (payload != null) {
                                    order.withLock { speechOutbox.send(payload) }
                                } else {
                                    engine.lastError?.let { reportSpeechError(it) }
                                }
                            }
                            SpeechItem.EndOfReply -> {
                                speechOutbox.send(SpeechPayload.EndOfReply)
                                return@launch
                            }
                        }
                    }
                }
                launch {
                    for (payload in speechOutbox) {
                        playPayload(payload)
                    }
                }
            }
        }
    }

    private fun engineFor(provider: VoiceProvider): TtsEngine = when (provider) {
        VoiceProvider.FISH_AUDIO -> fishTtsEngine
        VoiceProvider.ELEVENLABS -> elevenTtsEngine
        VoiceProvider.SYSTEM -> systemTtsEngine
    }

    fun onInputChange(value: String) {
        _ui.update { it.copy(input = value) }
    }

    fun send(textOverride: String? = null) {
        val snapshot = _ui.value
        val text = (textOverride ?: snapshot.input).trim()
        if (text.isEmpty() || snapshot.generating) return

        ttsBuffer.setLength(0)
        interruptSpeech()

        val userMessage = ChatMessageUi(
            id = newMessageId(),
            author = ChatAuthor.USER,
            text = text,
        )
        val assistantId = newMessageId()
        val assistantMessage = ChatMessageUi(
            id = assistantId,
            author = ChatAuthor.JARVIS,
            text = "",
            streaming = true,
        )

        val history = snapshot.messages
            .filter { !it.isError && !it.streaming && it.text.isNotBlank() }
            .map { message ->
                if (message.author == ChatAuthor.USER) {
                    LlmMessage.user(message.text)
                } else {
                    LlmMessage.assistant(message.text)
                }
            }
            .takeLast(30)

        _ui.update { state ->
            state.copy(
                messages = state.messages + userMessage + assistantMessage,
                input = "",
                generating = true,
                listening = false,
                speaking = false,
                activeProvider = null,
                toolActivity = null,
                error = null,
                notice = null,
            )
        }

        sendJob = viewModelScope.launch {
            try {
                val providers = resolveProviders(snapshot.settings)
                if (providers.isEmpty()) {
                    fail(assistantId, NO_PROVIDER_MESSAGE)
                    return@launch
                }
                val systemPrompt = withContext(Dispatchers.IO) {
                    personaBuilder.build(snapshot.settings)
                }
                agentLoop.run(
                    AgentRequest(
                        systemPrompt = systemPrompt,
                        history = history,
                        userMessage = text,
                        providers = providers,
                        tools = toolRegistry.definitions(),
                    ),
                ).collect { event ->
                    handleAgentEvent(assistantId, event)
                }
            } catch (e: CancellationException) {
                markStopped(assistantId)
                throw e
            } catch (e: Exception) {
                fail(
                    assistantId,
                    e.message?.take(400) ?: "Jarvis could not complete that request.",
                )
            }
        }
    }

    fun stop() {
        val job = sendJob
        sendJob = null
        if (job != null) {
            job.cancel()
        } else {
            _ui.update { it.copy(generating = false, toolActivity = null) }
        }
        interruptSpeech()
    }

    fun retryLast() {
        val state = _ui.value
        if (state.generating) return
        val lastUserIndex = state.messages.indexOfLast { it.author == ChatAuthor.USER }
        if (lastUserIndex < 0) return
        val lastUser = state.messages[lastUserIndex]
        val remaining = state.messages.take(lastUserIndex)
        _ui.update {
            it.copy(
                messages = remaining,
                input = lastUser.text,
                error = null,
                notice = null,
            )
        }
        send()
    }

    fun openSettings() {
        _ui.update { it.copy(showSettings = true, notice = null) }
    }

    fun closeSettings() {
        _ui.update { it.copy(showSettings = false, notice = null) }
    }

    fun dismissError() {
        _ui.update { it.copy(error = null) }
    }

    fun dismissNotice() {
        _ui.update { it.copy(notice = null) }
    }

    fun saveProfile(profile: OwnerProfile) {
        val name = profile.name.trim()
        if (name.isEmpty()) {
            setError("Please enter your name before we begin.")
            return
        }
        val clean = profile.copy(
            name = name,
            title = profile.title.trim().ifBlank { "sir" },
            occupation = profile.occupation.trim(),
            preferences = profile.preferences.trim(),
        )
        _ui.update { it.copy(onboardingBusy = true, error = null) }
        viewModelScope.launch {
            try {
                settingsRepository.saveProfile(clean)
                _ui.update {
                    it.copy(
                        onboardingBusy = false,
                        notice = "Profile saved.",
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        onboardingBusy = false,
                        error = e.message?.take(400) ?: "Could not save your profile.",
                    )
                }
            }
        }
    }

    fun savePersona(tone: String, customPrompt: String?) {
        viewModelScope.launch {
            try {
                settingsRepository.savePersona(
                    PersonaSettings(
                        tone = tone,
                        customPrompt = customPrompt,
                    ),
                )
                setNotice("Persona saved. It applies to the next message.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(e.message?.take(400) ?: "Could not save the persona.")
            }
        }
    }

    fun resetPersonaPrompt(tone: String) {
        savePersona(tone = tone, customPrompt = null)
    }

    fun saveProvider(provider: ProviderSettings, newApiKey: String = "") {
        if (provider.id.isBlank()) {
            setError("This provider has not been saved yet.")
            return
        }
        validateProvider(provider)?.let {
            setError(it)
            return
        }
        viewModelScope.launch {
            try {
                val encrypted = withContext(Dispatchers.IO) {
                    if (newApiKey.isNotBlank()) {
                        settingsRepository.encryptApiKey(newApiKey.trim())
                    } else {
                        provider.encryptedApiKey
                    }
                }
                settingsRepository.update { current ->
                    val updatedProvider = provider.copy(encryptedApiKey = encrypted)
                    val exists = current.providers.any { it.id == provider.id }
                    current.copy(
                        providers = if (exists) {
                            current.providers.map {
                                if (it.id == provider.id) updatedProvider else it
                            }
                        } else {
                            current.providers + updatedProvider
                        },
                        defaultProviderId = current.defaultProviderId.ifBlank {
                            updatedProvider.id
                        },
                    )
                }
                setNotice("Provider saved.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(e.message?.take(400) ?: "Could not save the provider.")
            }
        }
    }

    fun addProvider(
        kind: ProviderKind,
        name: String,
        baseUrl: String,
        model: String,
        apiKey: String,
    ) {
        val provisional = ProviderSettings(
            id = "pending",
            name = name.trim(),
            kind = kind,
            baseUrl = baseUrl.trim(),
            model = model.trim(),
        )
        validateProvider(provisional)?.let {
            setError(it)
            return
        }
        viewModelScope.launch {
            try {
                val encrypted = withContext(Dispatchers.IO) {
                    if (apiKey.isNotBlank()) {
                        settingsRepository.encryptApiKey(apiKey.trim())
                    } else {
                        ""
                    }
                }
                val id = "${kind.name.lowercase(Locale.US)}_${UUID.randomUUID().toString().take(8)}"
                settingsRepository.update { current ->
                    val newProvider = provisional.copy(
                        id = id,
                        encryptedApiKey = encrypted,
                    )
                    current.copy(
                        providers = current.providers + newProvider,
                        defaultProviderId = current.defaultProviderId.ifBlank { id },
                    )
                }
                setNotice("Provider added.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(e.message?.take(400) ?: "Could not add the provider.")
            }
        }
    }

    fun removeProvider(providerId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.update { current ->
                    val remaining = current.providers.filterNot { it.id == providerId }
                    current.copy(
                        providers = remaining,
                        defaultProviderId = if (current.defaultProviderId == providerId) {
                            remaining.firstOrNull()?.id.orEmpty()
                        } else {
                            current.defaultProviderId
                        },
                    )
                }
                _ui.update {
                    it.copy(
                        providerTests = it.providerTests - providerId,
                        notice = "Provider removed.",
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(e.message?.take(400) ?: "Could not remove the provider.")
            }
        }
    }

    fun setDefaultProvider(providerId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.update { current ->
                    current.copy(defaultProviderId = providerId)
                }
                setNotice("Default provider updated.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(e.message?.take(400) ?: "Could not change the default provider.")
            }
        }
    }

    fun moveProvider(providerId: String, delta: Int) {
        viewModelScope.launch {
            try {
                settingsRepository.update { current ->
                    val providers = current.providers.toMutableList()
                    val index = providers.indexOfFirst { it.id == providerId }
                    if (index < 0) return@update current
                    val target = index + delta
                    if (target !in providers.indices) return@update current
                    val item = providers.removeAt(index)
                    providers.add(target, item)
                    current.copy(providers = providers)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(e.message?.take(400) ?: "Could not reorder the providers.")
            }
        }
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.update { current ->
                    val providers = current.providers.map {
                        if (it.id == providerId) it.copy(enabled = enabled) else it
                    }
                    val defaultId = if (!enabled && current.defaultProviderId == providerId) {
                        providers.firstOrNull { it.enabled }?.id ?: current.defaultProviderId
                    } else {
                        current.defaultProviderId
                    }
                    current.copy(providers = providers, defaultProviderId = defaultId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(e.message?.take(400) ?: "Could not update the provider.")
            }
        }
    }

    fun testProvider(provider: ProviderSettings, draftKey: String = "") {
        if (provider.id.isBlank()) {
            setProviderTest(provider.id, ProviderTestState.Failure("Save the provider first."))
            return
        }
        if (_ui.value.providerTests[provider.id] is ProviderTestState.Testing) return
        validateProvider(provider)?.let {
            setProviderTest(provider.id, ProviderTestState.Failure(it))
            return
        }
        setProviderTest(provider.id, ProviderTestState.Testing)
        viewModelScope.launch {
            try {
                val key = withContext(Dispatchers.IO) {
                    if (draftKey.isNotBlank()) {
                        draftKey.trim()
                    } else {
                        settingsRepository.decryptApiKey(provider)
                    }
                }
                if (key.isBlank()) {
                    setProviderTest(
                        provider.id,
                        ProviderTestState.Failure(
                            "Enter an API key first, or save one with the provider.",
                        ),
                    )
                    return@launch
                }
                val result = providerTester.test(provider.toConfig(key))
                setProviderTest(
                    provider.id,
                    if (result.success) {
                        ProviderTestState.Success(result.message)
                    } else {
                        ProviderTestState.Failure(result.message)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setProviderTest(
                    provider.id,
                    ProviderTestState.Failure(
                        e.message?.take(400) ?: "The connection test failed.",
                    ),
                )
            }
        }
    }

    private fun handleAgentEvent(assistantId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.TextDelta -> {
                ttsBuffer.append(event.text)
                queueCompletedSentences()
                _ui.update { state ->
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

            is AgentEvent.ProviderFallback -> {
                _ui.update {
                    it.copy(
                        toolActivity = "${event.from} is unavailable. Trying ${event.to}...",
                    )
                }
            }

            is AgentEvent.ToolStarted -> {
                _ui.update { state ->
                    state.copy(
                        // Tool rounds restart the visible answer. A blank line keeps
                        // "let me check" and the final answer from running together.
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

            is AgentEvent.ToolFinished -> {
                _ui.update {
                    it.copy(
                        toolActivity = if (event.success) {
                            "Done."
                        } else {
                            "A tool reported a problem. Adjusting..."
                        },
                    )
                }
            }

            is AgentEvent.Completed -> {
                if (ttsBuffer.isNotBlank()) {
                    speechInbox.trySend(SpeechItem.Sentence(ttsBuffer.toString().trim()))
                    ttsBuffer.setLength(0)
                }
                speechInbox.trySend(SpeechItem.EndOfReply)
                _ui.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id == assistantId) {
                                message.copy(
                                    text = message.text.trimEnd(),
                                    streaming = false,
                                    providerName = event.providerName,
                                )
                            } else {
                                message
                            }
                        },
                        generating = false,
                        activeProvider = event.providerName,
                        toolActivity = null,
                    )
                }
            }

            is AgentEvent.Error -> fail(assistantId, event.message)
        }
    }

    private fun fail(assistantId: String, message: String) {
        _ui.update { state ->
            val target = state.messages.firstOrNull { it.id == assistantId }
            val hasText = !target?.text.isNullOrBlank()
            state.copy(
                messages = state.messages.map { chat ->
                    if (chat.id != assistantId) {
                        chat
                    } else if (hasText) {
                        chat.copy(streaming = false)
                    } else {
                        chat.copy(
                            text = message,
                            streaming = false,
                            isError = true,
                        )
                    }
                },
                generating = false,
                activeProvider = null,
                toolActivity = null,
                error = message,
            )
        }
        interruptSpeech()
    }

    private fun markStopped(assistantId: String) {
        _ui.update { state ->
            state.copy(
                messages = state.messages
                    .filterNot { it.id == assistantId && it.text.isBlank() }
                    .map { chat ->
                        if (chat.id == assistantId) chat.copy(streaming = false) else chat
                    },
                generating = false,
                activeProvider = null,
                toolActivity = null,
            )
        }
        interruptSpeech()
    }

    private fun addGreeting(settings: AppSettings) {
        greetingShown = true
        val greeting = greetingEngine.next(
            profile = settings.profile,
            lastGreetingId = settings.persona.lastGreetingId,
        )
        _ui.update { state ->
            state.copy(
                messages = state.messages + ChatMessageUi(
                    id = newMessageId(),
                    author = ChatAuthor.JARVIS,
                    text = greeting.text,
                ),
            )
        }
        viewModelScope.launch {
            runCatching {
                settingsRepository.update { current ->
                    current.copy(
                        persona = current.persona.copy(lastGreetingId = greeting.id),
                    )
                }
            }
        }
        speakText(greeting.text)
    }

    private suspend fun resolveProviders(settings: AppSettings): List<ProviderConfig> =
        withContext(Dispatchers.IO) {
            val default = settings.defaultProvider
            val ordered = buildList {
                if (default != null) add(default)
                addAll(settings.providers.filter { it.id != default?.id })
            }

            var keyError: String? = null
            val configs = mutableListOf<ProviderConfig>()
            for (provider in ordered) {
                if (!provider.enabled || !provider.hasSavedKey) continue
                try {
                    val key = settingsRepository.decryptApiKey(provider)
                    if (key.isBlank()) {
                        keyError = "The saved API key for ${provider.name} is empty. Re-enter it in Settings."
                    } else {
                        configs += provider.toConfig(key)
                    }
                } catch (e: Exception) {
                    keyError = e.message?.take(400)
                        ?: "A saved API key could not be decrypted."
                }
            }
            if (configs.isEmpty() && keyError != null) {
                throw ProviderException(keyError)
            }
            configs
        }

    private fun validateProvider(provider: ProviderSettings): String? {
        if (provider.name.isBlank()) return "Give the provider a name."
        if (!isHttpUrl(provider.baseUrl)) {
            return "Base URL must be a full http:// or https:// address."
        }
        if (provider.model.isBlank()) return "Enter a model name."
        return null
    }

    private fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun ProviderSettings.toConfig(apiKey: String): ProviderConfig = ProviderConfig(
        id = id,
        name = name,
        kind = kind,
        baseUrl = baseUrl.trim().trimEnd('/'),
        model = model.trim(),
        apiKey = apiKey,
    )

    private fun setProviderTest(providerId: String, state: ProviderTestState) {
        if (providerId.isBlank()) return
        _ui.update {
            it.copy(providerTests = it.providerTests + (providerId to state))
        }
    }

    private fun setError(message: String) {
        _ui.update {
            it.copy(
                error = message,
                notice = null,
                onboardingBusy = false,
            )
        }
    }

    private fun setNotice(message: String) {
        _ui.update {
            it.copy(
                notice = message,
                error = null,
            )
        }
    }

    private fun toolActivityLabel(name: String): String = when (name) {
        "get_current_time" -> "Checking the current time..."
        else -> "Using ${name.replace('_', ' ')}..."
    }

    private fun newMessageId(): String {
        nextMessageNumber += 1
        return "message_$nextMessageNumber"
    }

    fun startListening() {
        if (_ui.value.generating) return
        _ui.update { it.copy(listening = true, error = null) }
        interruptSpeech()
        sttJob?.cancel()
        sttJob = viewModelScope.launch(Dispatchers.Main) {
            val locale = runCatching { Locale.forLanguageTag(_ui.value.settings.voice.language) }
                .getOrDefault(Locale.US)
            sttEngine.listen(locale).collect { event ->
                when (event) {
                    is SttEvent.Partial -> Unit
                    is SttEvent.Final -> {
                        _ui.update { it.copy(listening = false) }
                        if (event.text.isNotBlank()) {
                            send(event.text)
                        }
                    }
                    is SttEvent.Error -> {
                        _ui.update { it.copy(listening = false, error = event.message) }
                    }
                    SttEvent.End -> {
                        _ui.update { it.copy(listening = false) }
                    }
                }
            }
            _ui.update { it.copy(listening = false) }
        }
    }

    fun toggleListening() {
        if (_ui.value.listening) stopListening() else startListening()
    }

    fun stopListening() {
        sttJob?.cancel()
        sttJob = null
        _ui.update { it.copy(listening = false) }
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        if (!speaking) {
            if (!requestAudioFocus()) {
                // If we can't get audio focus, fall back to system TTS directly
                _ui.update { it.copy(notice = "Could not get audio focus. Playing through system.") }
            }
            bargeInDetector.start { interruptSpeech() }
            speaking = true
            _ui.update { it.copy(speaking = true) }
        }
        speechInbox.trySend(SpeechItem.Sentence(text.trim()))
    }

    private suspend fun speakOne(sentence: String) {
        if (sentence.isBlank()) return
        speechInbox.trySend(SpeechItem.Sentence(sentence.trim()))
    }

    private suspend fun ttsConfig(): TtsConfig {
        val voice = _ui.value.settings.voice
        val key = runCatching { settingsRepository.decryptVoiceApiKey(voice) }.getOrDefault("")
        return TtsConfig(
            provider = voice.provider,
            apiKey = key,
            voiceId = voice.voiceId,
            speed = voice.speed,
            fallbackToSystem = voice.fallbackToSystem,
            language = voice.language,
        )
    }

    private fun interruptSpeech() {
        // Cancel the pipeline to stop both synthesis and playback
        speechPipeline?.cancel()
        // Drain the queues
        runCatching {
            while (true) {
                speechInbox.tryReceive()
            }
        }
        runCatching {
            while (true) {
                speechOutbox.tryReceive()
            }
        }
        audioPlayer.stop()
        systemTtsEngine.stop()
        bargeInDetector.stop()
        speaking = false
        _ui.update { it.copy(speaking = false) }
        abandonAudioFocus()
    }

    private suspend fun playPayload(payload: SpeechPayload) {
        when (payload) {
            is SpeechPayload.Pcm -> {
                audioPlayer.writePcm(payload.bytes, payload.sampleRate)
            }
            is SpeechPayload.SystemText -> {
                audioPlayer.finish()
                systemTtsEngine.speak(payload.text, _ui.value.settings.voice.language)
            }
            SpeechPayload.EndOfReply -> {
                audioPlayer.finish()
                _ui.update { it.copy(speaking = false) }
                if (_ui.value.settings.voice.continuousListening) {
                    startListening()
                }
            }
        }
    }

    private fun queueCompletedSentences() {
        val regex = Regex("""[^.!?]*[.!?]""")
        val text = ttsBuffer.toString()
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return
        val consumed = matches.last().range.last + 1
        matches.forEach { match ->
            val sentence = match.value.trim()
            if (sentence.isNotBlank()) {
                speechInbox.trySend(SpeechItem.Sentence(sentence))
            }
        }
        ttsBuffer.delete(0, consumed)
    }

    private fun reportSpeechError(message: String) {
        val notice = "Voice error: $message"
        _ui.update { it.copy(notice = notice) }
        // Clear the notice after a few seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            _ui.update { it.copy(notice = null) }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { audioManager.abandonAudioFocusRequest(
            android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build(),
        ) }
    }

    fun saveVoiceSettings(
        provider: VoiceProvider,
        voiceId: String,
        speed: Float,
        fallbackToSystem: Boolean,
        language: String,
        apiKey: String,
        continuousListening: Boolean = true,
    ) {
        viewModelScope.launch {
            try {
                settingsRepository.saveVoice(
                    VoiceSettings(
                        provider = provider,
                        voiceId = voiceId,
                        speed = speed,
                        fallbackToSystem = fallbackToSystem,
                        language = language,
                        continuousListening = continuousListening,
                    ),
                    apiKey,
                )
                setNotice("Voice settings saved.")
            } catch (e: Exception) {
                setError(e.message?.take(400) ?: "Could not save voice settings.")
            }
        }
    }

    fun testVoice() {
        viewModelScope.launch {
            speakText("Hello. I am Jarvis, and my voice is now configured.")
        }
    }

    override fun onCleared() {
        sendJob?.cancel()
        sendJob = null
        speechPipeline?.cancel()
        interruptSpeech()
        systemTtsEngine.shutdown()
        super.onCleared()
    }

    private companion object {
        const val NO_PROVIDER_MESSAGE =
            "No AI provider is configured. Open Settings, add an API key for OpenRouter, Gemini or Anthropic, then try again."
    }
}
