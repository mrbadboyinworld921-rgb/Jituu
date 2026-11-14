package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {
    private const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    private val secureRandom = SecureRandom()

    data class DerivedPinKeys(
        val verifier: ByteArray,
        val aesKeyBytes: ByteArray
    ) {
        fun aesSecretKey(): SecretKey = SecretKeySpec(aesKeyBytes, "AES")
    }

    fun randomBytes(length: Int): ByteArray = ByteArray(length).also { secureRandom.nextBytes(it) }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun fromBase64(encoded: String): ByteArray = Base64.decode(encoded, Base64.NO_WRAP)

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun derivePinKeys(pin: String, salt: ByteArray): DerivedPinKeys {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val masterKey = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }

        val prk = hkdfExtract(salt, masterKey)
        val verifier = hkdfExpand(prk, "jarvis-pin-verifier-v1".toByteArray(Charsets.UTF_8), 32)
        val aesKeyBytes = hkdfExpand(prk, "jarvis-vault-aes-gcm-v1".toByteArray(Charsets.UTF_8), 32)
        masterKey.fill(0)
        prk.fill(0)
        return DerivedPinKeys(verifier = verifier, aesKeyBytes = aesKeyBytes)
    }

    fun encryptAesGcm(aesKey: SecretKey, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv to ciphertext
    }

    fun decryptAesGcm(aesKey: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, out, offset, copyLen)
            offset += copyLen
            counter++
        }
        return out
    }
}
