package com.jarvis.assistant.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Encrypts small secrets with an AES-256-GCM key held by the Android Keystore.
 *
 * The key never leaves the Keystore. The encoded value is
 * [version=1][iv length][iv][ciphertext], Base64 encoded for storage.
 */
class SecretCipher {

    @Synchronized
    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(ASSOCIATED_DATA)
            val iv = cipher.iv
            require(iv.size in 1..255) { "Unexpected IV length" }
            val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val packed = ByteBuffer.allocate(2 + iv.size + ciphertext.size)
                .put(VERSION)
                .put(iv.size.toByte())
                .put(iv)
                .put(ciphertext)
                .array()
            Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw SecretCipherException("Could not encrypt the API key on this device.", e)
        }
    }

    @Synchronized
    fun decrypt(encoded: String): String {
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size >= 4) { "Encrypted value is too short" }
            require(packed[0] == VERSION) { "Unknown encrypted value version" }
            val ivLength = packed[1].toInt() and 0xFF
            require(ivLength in 1..255) { "Invalid IV length" }
            require(packed.size > 2 + ivLength) { "Encrypted value is truncated" }
            val iv = packed.copyOfRange(2, 2 + ivLength)
            val ciphertext = packed.copyOfRange(2 + ivLength, packed.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(ASSOCIATED_DATA)
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw SecretCipherException(
                "The saved API key could not be decrypted. Re-enter it in Settings.",
                e,
            )
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "jarvis_settings_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        val ASSOCIATED_DATA: ByteArray = "jarvis.settings.v1".toByteArray(StandardCharsets.UTF_8)
    }
}
