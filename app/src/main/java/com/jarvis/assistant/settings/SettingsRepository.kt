package com.jarvis.assistant.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.jarvisDataStore by preferencesDataStore(name = "jarvis_settings")

class SettingsRepository(
    context: Context,
    private val cipher: SecretCipher = SecretCipher(),
) {
    private val appContext = context.applicationContext

    val settings: Flow<AppSettings> = appContext.jarvisDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> decode(preferences[STATE_KEY]) }

    /**
     * Atomically updates the whole settings document. The transform must be
     * quick and must not suspend.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        var updated = AppSettings()
        appContext.jarvisDataStore.edit { preferences ->
            val current = decode(preferences[STATE_KEY])
            updated = transform(current).normalized()
            preferences[STATE_KEY] = json.encodeToString(updated)
        }
        return updated
    }

    suspend fun saveProfile(profile: OwnerProfile): AppSettings =
        update { current -> current.copy(profile = profile.sanitized()) }

    suspend fun savePersona(persona: PersonaSettings): AppSettings =
        update { current ->
            current.copy(
                persona = persona.copy(
                    tone = persona.tone.trim().ifBlank { "dry British wit, calm and understated" },
                    customPrompt = persona.customPrompt?.trim()?.takeIf { it.isNotBlank() },
                    lastGreetingId = current.persona.lastGreetingId,
                ),
            )
        }

    fun encryptApiKey(plainText: String): String = cipher.encrypt(plainText)

    /**
     * Returns the saved key, or an empty string when none is saved.
     *
     * @throws SecretCipherException when a saved key cannot be decrypted.
     */
    fun decryptApiKey(provider: ProviderSettings): String {
        if (!provider.hasSavedKey) return ""
        return cipher.decrypt(provider.encryptedApiKey)
    }

    private fun decode(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return runCatching { json.decodeFromString<AppSettings>(raw) }
            .getOrElse { AppSettings() }
    }

    private fun AppSettings.normalized(): AppSettings {
        val seen = mutableSetOf<String>()
        val cleaned = providers.mapNotNull { provider ->
            val id = provider.id.trim()
            if (id.isEmpty() || !seen.add(id)) {
                null
            } else {
                provider.copy(
                    id = id,
                    name = provider.name.trim().ifBlank { provider.kind.label },
                    baseUrl = provider.baseUrl.trim().trimEnd('/'),
                    model = provider.model.trim(),
                )
            }
        }
        val validDefault = defaultProviderId.takeIf { id -> cleaned.any { it.id == id } }
            ?: cleaned.firstOrNull()?.id
            ?: ""
        return copy(providers = cleaned, defaultProviderId = validDefault)
    }

    private fun OwnerProfile.sanitized(): OwnerProfile = copy(
        name = name.trim(),
        title = title.trim().ifBlank { "sir" },
        occupation = occupation.trim(),
        preferences = preferences.trim(),
    )

    private companion object {
        val STATE_KEY = stringPreferencesKey("settings_v1")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
