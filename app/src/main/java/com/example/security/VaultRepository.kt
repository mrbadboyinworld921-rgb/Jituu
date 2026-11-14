package com.example.security

import android.content.Context
import com.example.model.ChatMessage
import com.example.model.CodeSnippet
import com.example.model.NoteItem
import com.example.model.ReminderItem
import com.example.model.TodoItem
import com.example.model.VaultData
import com.example.model.VaultSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

sealed interface PinVerifyResult {
    data class Unlocked(val vaultData: VaultData, val isFirstSetup: Boolean) : PinVerifyResult
    data class Error(val message: String) : PinVerifyResult
}

class VaultRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
    private val vaultFile: File get() = File(context.filesDir, VAULT_FILENAME)

    private var activeAesKey: SecretKey? = null
    private var activeKeyBytes: ByteArray? = null

    fun hasPinConfigured(): Boolean =
        prefs.contains(KEY_SALT) && prefs.contains(KEY_VERIFIER)

    fun hasBiometricWrappedKey(): Boolean =
        prefs.contains(KEY_BIO_IV) && prefs.contains(KEY_BIO_WRAPPED)

    fun getLockoutRemainingSeconds(): Long {
        val lockUntil = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        val remaining = lockUntil - System.currentTimeMillis()
        return if (remaining > 0) (remaining + 999L) / 1000L else 0L
    }

    fun isUnlocked(): Boolean = activeAesKey != null

    fun lock() {
        activeAesKey = null
        activeKeyBytes?.fill(0)
        activeKeyBytes = null
    }

    fun getBiometricBlob(): Pair<ByteArray, ByteArray>? {
        val ivStr = prefs.getString(KEY_BIO_IV, null) ?: return null
        val wrappedStr = prefs.getString(KEY_BIO_WRAPPED, null) ?: return null
        return VaultCrypto.fromBase64(ivStr) to VaultCrypto.fromBase64(wrappedStr)
    }

    fun getActiveKeyBytesCopy(): ByteArray? = activeKeyBytes?.copyOf()

    fun saveBiometricBlob(iv: ByteArray, wrappedKey: ByteArray) {
        prefs.edit()
            .putString(KEY_BIO_IV, VaultCrypto.toBase64(iv))
            .putString(KEY_BIO_WRAPPED, VaultCrypto.toBase64(wrappedKey))
            .apply()
    }

    suspend fun createInitialPin(pin: String): PinVerifyResult = withContext(Dispatchers.Default) {
        val salt = VaultCrypto.randomBytes(16)
        val derived = VaultCrypto.derivePinKeys(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, VaultCrypto.toBase64(salt))
            .putString(KEY_VERIFIER, VaultCrypto.toBase64(derived.verifier))
            .putInt(KEY_FAIL_COUNT, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()

        val initialVault = VaultData()
        val key = derived.aesSecretKey()
        activeAesKey = key
        activeKeyBytes = derived.aesKeyBytes
        saveVaultInternal(key, initialVault)
        PinVerifyResult.Unlocked(initialVault, isFirstSetup = true)
    }

    suspend fun verifyPinAndUnlock(pin: String): PinVerifyResult = withContext(Dispatchers.Default) {
        val remSecs = getLockoutRemainingSeconds()
        if (remSecs > 0) {
            return@withContext PinVerifyResult.Error("Locked out for ${remSecs}s after 5 wrong attempts.")
        }

        val saltStr = prefs.getString(KEY_SALT, null)
        val verifierStr = prefs.getString(KEY_VERIFIER, null)
        if (saltStr == null || verifierStr == null) {
            return@withContext createInitialPin(pin)
        }

        val salt = VaultCrypto.fromBase64(saltStr)
        val expectedVerifier = VaultCrypto.fromBase64(verifierStr)
        val derived = VaultCrypto.derivePinKeys(pin, salt)

        if (!VaultCrypto.constantTimeEquals(derived.verifier, expectedVerifier)) {
            derived.aesKeyBytes.fill(0)
            val fails = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
            if (fails >= MAX_FAILED_ATTEMPTS) {
                prefs.edit()
                    .putInt(KEY_FAIL_COUNT, 0)
                    .putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + LOCKOUT_MS)
                    .apply()
                return@withContext PinVerifyResult.Error("5 wrong attempts. Vault locked for 5 minutes.")
            } else {
                prefs.edit().putInt(KEY_FAIL_COUNT, fails).apply()
                return@withContext PinVerifyResult.Error("Invalid PIN ($fails/$MAX_FAILED_ATTEMPTS attempts).")
            }
        }

        prefs.edit()
            .putInt(KEY_FAIL_COUNT, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()

        val key = derived.aesSecretKey()
        val vault = loadVaultInternal(key)
        activeAesKey = key
        activeKeyBytes = derived.aesKeyBytes
        PinVerifyResult.Unlocked(vault, isFirstSetup = false)
    }

    suspend fun unlockWithBiometricBytes(keyBytes: ByteArray): PinVerifyResult = withContext(Dispatchers.Default) {
        val remSecs = getLockoutRemainingSeconds()
        if (remSecs > 0) {
            keyBytes.fill(0)
            return@withContext PinVerifyResult.Error("Locked out for ${remSecs}s.")
        }
        val key = SecretKeySpec(keyBytes, "AES")
        runCatching {
            val vault = loadVaultInternal(key)
            activeAesKey = key
            activeKeyBytes = keyBytes
            PinVerifyResult.Unlocked(vault, isFirstSetup = false)
        }.getOrElse {
            keyBytes.fill(0)
            PinVerifyResult.Error("Biometric vault decryption failed. Enter PIN.")
        }
    }

    suspend fun saveVault(vault: VaultData) = withContext(Dispatchers.IO) {
        val key = activeAesKey ?: return@withContext
        saveVaultInternal(key, vault)
    }

    suspend fun panicWipe() = withContext(Dispatchers.IO) {
        lock()
        prefs.edit().clear().commit()
        if (vaultFile.exists()) {
            vaultFile.delete()
        }
        BiometricVaultHelper.deleteKeystoreKey()
    }

    private fun saveVaultInternal(key: SecretKey, vault: VaultData) {
        val json = serializeVault(vault).toString().toByteArray(Charsets.UTF_8)
        val (iv, ct) = VaultCrypto.encryptAesGcm(key, json)
        val wrapper = JSONObject().apply {
            put("iv", VaultCrypto.toBase64(iv))
            put("ct", VaultCrypto.toBase64(ct))
        }
        vaultFile.writeText(wrapper.toString(), Charsets.UTF_8)
    }

    private fun loadVaultInternal(key: SecretKey): VaultData {
        if (!vaultFile.exists()) return VaultData()
        val raw = vaultFile.readText(Charsets.UTF_8)
        val wrapper = JSONObject(raw)
        val iv = VaultCrypto.fromBase64(wrapper.getString("iv"))
        val ct = VaultCrypto.fromBase64(wrapper.getString("ct"))
        val decrypted = VaultCrypto.decryptAesGcm(key, iv, ct)
        return deserializeVault(JSONObject(String(decrypted, Charsets.UTF_8)))
    }

    private fun serializeVault(vault: VaultData): JSONObject {
        return JSONObject().apply {
            put("chatHistory", JSONArray().apply {
                vault.chatHistory.takeLast(40).forEach { msg ->
                    put(JSONObject().apply {
                        put("id", msg.id)
                        put("isUser", msg.isUser)
                        put("text", msg.text)
                        put("timestampMs", msg.timestampMs)
                        msg.codeSnippet?.let { snippet ->
                            put("code", snippet.code)
                            put("codeLang", snippet.language)
                        }
                    })
                }
            })
            put("notes", JSONArray().apply {
                vault.notes.forEach { n ->
                    put(JSONObject().apply {
                        put("id", n.id)
                        put("text", n.text)
                        put("createdAt", n.createdAt)
                    })
                }
            })
            put("todos", JSONArray().apply {
                vault.todos.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("text", t.text)
                        put("done", t.done)
                    })
                }
            })
            put("reminders", JSONArray().apply {
                vault.reminders.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("text", r.text)
                        put("timeHHmm", r.timeHHmm)
                        put("fired", r.fired)
                    })
                }
            })
            put("settings", JSONObject().apply {
                put("ownerName", vault.settings.ownerName)
                put("ownerEmail", vault.settings.ownerEmail)
                put("sttLang", vault.settings.sttLang)
                put("ttsVoiceName", vault.settings.ttsVoiceName)
                put("githubUsername", vault.settings.githubUsername)
                put("githubToken", vault.settings.githubToken)
                put("reducedMotionOverride", vault.settings.reducedMotionOverride)
                put("spontaneousFlirtEnabled", vault.settings.spontaneousFlirtEnabled)
            })
        }
    }

    private fun deserializeVault(json: JSONObject): VaultData {
        val chatArr = json.optJSONArray("chatHistory") ?: JSONArray()
        val chatList = buildList {
            for (i in 0 until chatArr.length()) {
                val o = chatArr.getJSONObject(i)
                val code = o.optString("code", "")
                val codeLang = o.optString("codeLang", "")
                add(
                    ChatMessage(
                        id = o.optString("id"),
                        isUser = o.optBoolean("isUser"),
                        text = o.optString("text"),
                        codeSnippet = if (code.isNotBlank()) CodeSnippet(code, codeLang) else null,
                        timestampMs = o.optLong("timestampMs", System.currentTimeMillis())
                    )
                )
            }
        }

        val notesArr = json.optJSONArray("notes") ?: JSONArray()
        val notes = buildList {
            for (i in 0 until notesArr.length()) {
                val o = notesArr.getJSONObject(i)
                add(NoteItem(o.optString("id"), o.optString("text"), o.optString("createdAt")))
            }
        }

        val todosArr = json.optJSONArray("todos") ?: JSONArray()
        val todos = buildList {
            for (i in 0 until todosArr.length()) {
                val o = todosArr.getJSONObject(i)
                add(TodoItem(o.optString("id"), o.optString("text"), o.optBoolean("done")))
            }
        }

        val remArr = json.optJSONArray("reminders") ?: JSONArray()
        val reminders = buildList {
            for (i in 0 until remArr.length()) {
                val o = remArr.getJSONObject(i)
                add(
                    ReminderItem(
                        id = o.optString("id"),
                        text = o.optString("text"),
                        timeHHmm = o.optString("timeHHmm"),
                        fired = o.optBoolean("fired")
                    )
                )
            }
        }

        val s = json.optJSONObject("settings") ?: JSONObject()
        val settings = VaultSettings(
            ownerName = s.optString("ownerName", "Jitendra Maurya").ifBlank { "Jitendra Maurya" },
            ownerEmail = s.optString("ownerEmail", "jitendermourya921@gmail.com").ifBlank { "jitendermourya921@gmail.com" },
            sttLang = s.optString("sttLang", "hi-IN"),
            ttsVoiceName = s.optString("ttsVoiceName", ""),
            githubUsername = s.optString("githubUsername", "Crosas27"),
            githubToken = s.optString("githubToken", ""),
            reducedMotionOverride = s.optBoolean("reducedMotionOverride", false),
            spontaneousFlirtEnabled = s.optBoolean("spontaneousFlirtEnabled", true)
        )

        return VaultData(
            chatHistory = chatList,
            notes = notes,
            todos = todos,
            reminders = reminders,
            settings = settings
        )
    }

    companion object {
        private const val META_PREFS = "jarvis_vault_meta_v1"
        private const val VAULT_FILENAME = "jarvis_vault_v1.enc"
        private const val KEY_SALT = "salt"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_BIO_IV = "bio_iv"
        private const val KEY_BIO_WRAPPED = "bio_wrapped"
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_MS = 5 * 60 * 1000L
    }
}
