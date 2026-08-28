package com.fincore.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreTokenManager(
    private val context: Context,
    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
) : TokenManager {

    companion object {
        private const val PREF_NAME = "fincore_secure_tokens"
        private const val KEY_ALIAS = "fincore_auth_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12

        private const val KEY_ACCESS_TOKEN = "enc_access_token"
        private const val KEY_REFRESH_TOKEN = "enc_refresh_token"
    }

    private val isKeyStoreAvailable: Boolean by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    // In-memory fallback if AndroidKeyStore is unavailable (e.g. standard JVM unit tests)
    private var memoryAccessToken: String? = null
    private var memoryRefreshToken: String? = null

    override suspend fun saveAccessToken(token: String) {
        if (!isKeyStoreAvailable) {
            memoryAccessToken = token
            return
        }
        val encrypted = encrypt(token)
        preferences.edit().putString(KEY_ACCESS_TOKEN, encrypted).apply()
    }

    override suspend fun getAccessToken(): String? {
        if (!isKeyStoreAvailable) {
            return memoryAccessToken
        }
        val encrypted = preferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return decrypt(encrypted)
    }

    override suspend fun saveRefreshToken(token: String) {
        if (!isKeyStoreAvailable) {
            memoryRefreshToken = token
            return
        }
        val encrypted = encrypt(token)
        preferences.edit().putString(KEY_REFRESH_TOKEN, encrypted).apply()
    }

    override suspend fun getRefreshToken(): String? {
        if (!isKeyStoreAvailable) {
            return memoryRefreshToken
        }
        val encrypted = preferences.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return decrypt(encrypted)
    }

    override suspend fun clearTokens() {
        memoryAccessToken = null
        memoryRefreshToken = null
        if (isKeyStoreAvailable) {
            preferences.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).apply()
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(encryptedBase64: String): String? {
        return runCatching {
            val combined = Base64.getDecoder().decode(encryptedBase64)
            val iv = ByteArray(IV_LENGTH)
            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        }.getOrNull()
    }
}
