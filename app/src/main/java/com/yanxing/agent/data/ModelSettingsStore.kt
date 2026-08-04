package com.yanxing.agent.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("model_settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = preferences.getString(KEY_BASE_URL, "") ?: ""
        set(value) = preferences.edit().putString(KEY_BASE_URL, value.trim()).apply()

    var model: String
        get() = preferences.getString(KEY_MODEL, "") ?: ""
        set(value) = preferences.edit().putString(KEY_MODEL, value.trim()).apply()

    fun saveApiKey(value: String) {
        val encrypted = encrypt(value)
        preferences.edit()
            .putString(KEY_API_KEY, encrypted.ciphertext)
            .putString(KEY_API_IV, encrypted.iv)
            .apply()
    }

    fun readApiKey(): String = runCatching {
        val ciphertext = preferences.getString(KEY_API_KEY, null) ?: return ""
        val iv = preferences.getString(KEY_API_IV, null) ?: return ""
        decrypt(ciphertext, iv)
    }.getOrDefault("")

    private fun encrypt(value: String): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return EncryptedValue(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    private fun decrypt(ciphertext: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        return String(
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
            StandardCharsets.UTF_8,
        )
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private data class EncryptedValue(val ciphertext: String, val iv: String)

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
        const val KEY_API_IV = "api_iv"
        const val KEY_ALIAS = "yanxing_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
