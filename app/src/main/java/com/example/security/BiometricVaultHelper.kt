package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricVaultHelper {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "jarvis_biometric_wrap_key_v1"

    fun canUseBiometrics(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun deleteKeystoreKey() {
        runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) {
                ks.deleteEntry(KEY_ALIAS)
            }
        }
    }

    fun authenticateAndWrapVaultKey(
        activity: FragmentActivity,
        vaultKeyBytes: ByteArray,
        onSuccess: (iv: ByteArray, wrappedKey: ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        val cipher = runCatching {
            val key = getOrCreateKeystoreKey()
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        }.getOrElse { err ->
            onError(err.localizedMessage ?: "Biometric hardware unavailable.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher
                    if (authedCipher == null) {
                        onError("Missing cryptographic session.")
                        return
                    }
                    runCatching {
                        val encrypted = authedCipher.doFinal(vaultKeyBytes)
                        val iv = authedCipher.iv
                        onSuccess(iv, encrypted)
                    }.onFailure { err ->
                        onError(err.localizedMessage ?: "Failed to bind biometric key.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable MAIM Biometric Unlock")
            .setSubtitle("Authenticate to seal your AES-256 vault key in hardware")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    fun authenticateAndUnwrapVaultKey(
        activity: FragmentActivity,
        iv: ByteArray,
        wrappedKey: ByteArray,
        onSuccess: (vaultKeyBytes: ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        val cipher = runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val key = ks.getKey(KEY_ALIAS, null) as? SecretKey
                ?: throw IllegalStateException("Biometric key invalidated. Enter 6-digit PIN.")
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }
        }.getOrElse { err ->
            onError(err.localizedMessage ?: "Biometric key unavailable. Enter PIN.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher
                    if (authedCipher == null) {
                        onError("Missing biometric crypto object.")
                        return
                    }
                    runCatching {
                        val unwrapped = authedCipher.doFinal(wrappedKey)
                        onSuccess(unwrapped)
                    }.onFailure { err ->
                        onError(err.localizedMessage ?: "Biometric decryption failed. Use PIN.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MAIM Neural Vault")
            .setSubtitle("Verify biometric identity to decrypt local vault")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
