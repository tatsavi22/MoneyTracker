package com.example.moneytracker.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import javax.crypto.Cipher

/**
 * Manages the SQLCipher database passphrase using the Android Keystore.
 *
 * The actual passphrase bytes are randomly generated once, then encrypted with
 * an AES-GCM key that never leaves the hardware-backed Keystore. The encrypted
 * blob is stored in a private SharedPreferences file.
 *
 * This means:
 *  - The passphrase is unique per device install.
 *  - It cannot be extracted without the Keystore key (which is hardware-bound
 *    on devices with a secure element).
 *  - Clearing app data wipes both the encrypted blob and the Keystore key,
 *    making the database unrecoverable — the desired behaviour for a private app.
 */
class DbKeyProvider(private val context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS = "moneytracker_db_key"
        private const val PREFS_NAME = "mt_secure_prefs"
        private const val PREF_ENCRYPTED_KEY = "encrypted_db_passphrase"
        private const val PREF_IV = "db_passphrase_iv"
        private const val PASSPHRASE_LENGTH = 32 // 256 bits
    }

    /**
     * Returns the database passphrase as a [ByteArray].
     * Generates and stores it on first call; retrieves it on subsequent calls.
     * Must be called off the main thread.
     */
    fun getPassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedB64 = prefs.getString(PREF_ENCRYPTED_KEY, null)
        val ivB64 = prefs.getString(PREF_IV, null)

        return if (encryptedB64 != null && ivB64 != null) {
            // Decrypt the stored passphrase
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivB64, Base64.DEFAULT)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(Base64.decode(encryptedB64, Base64.DEFAULT))
        } else {
            // Generate a new random passphrase, encrypt it, and persist the blob
            val passphrase = ByteArray(PASSPHRASE_LENGTH).also {
                java.security.SecureRandom().nextBytes(it)
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(passphrase)
            prefs.edit()
                .putString(PREF_ENCRYPTED_KEY, Base64.encodeToString(encrypted, Base64.DEFAULT))
                .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.DEFAULT))
                .apply()
            passphrase
        }
    }

    /**
     * Clears the stored encrypted passphrase.
     * Called from "Delete all my data" — after this, the database cannot be
     * opened with the old passphrase, so we also delete the DB file.
     */
    fun clearStoredPassphrase() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_ENCRYPTED_KEY)
            .remove(PREF_IV)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        // Key doesn't exist yet — create it
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // passphrase unlocked at app start
                .build()
        )
        return keyGenerator.generateKey()
    }
}
