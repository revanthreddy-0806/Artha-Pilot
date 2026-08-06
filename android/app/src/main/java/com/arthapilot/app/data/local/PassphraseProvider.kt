package com.arthapilot.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a random SQLCipher passphrase on first run and protects it with an
 * AES key stored in the Android Keystore (hardware-backed where available).
 */
@Singleton
class PassphraseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {

    fun blockingGetOrCreate(): ByteArray = runBlocking { getOrCreate() }

    private val mutex = Mutex()

    private suspend fun getOrCreate(): ByteArray = mutex.withLock {
        val prefs = dataStore.data.first()
        prefs[KEY_ENCRYPTED]?.let { encrypted -> return decrypt(encrypted) }

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encrypted = encrypt(passphrase)
        dataStore.edit { it[KEY_ENCRYPTED] = encrypted }
        passphrase
    }

    private fun secretKey(): SecretKey {
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

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val output = cipher.iv + cipher.doFinal(plain)
        return Base64.getEncoder().encodeToString(output)
    }

    private fun decrypt(data: String): ByteArray {
        val raw = Base64.getDecoder().decode(data)
        val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "artha_pilot_db_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        val KEY_ENCRYPTED = stringPreferencesKey("db_passphrase_encrypted")
    }
}
