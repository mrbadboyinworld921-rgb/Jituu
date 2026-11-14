package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NoteItem
import com.example.model.VaultData
import com.example.security.PinVerifyResult
import com.example.security.VaultCrypto
import com.example.security.VaultRepository
import com.example.voice.JarvisVoiceManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MAIM", appName)
    }

    @Test
    fun `stripForSpeech removes markdown and emojis`() {
        val cleaned = JarvisVoiceManager.stripForSpeech("Arre **waah** yaar! `code` 😎 #cool")
        assertEquals("Arre waah yaar! code cool", cleaned)
    }

    @Test
    fun `vault encrypts with PIN preserves owner and wipes on panic`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = VaultRepository(context)
        repo.panicWipe()

        assertFalse(repo.hasPinConfigured())
        val created = repo.createInitialPin("123456")
        assertTrue(created is PinVerifyResult.Unlocked)

        val note = NoteItem(id = "n1", text = "Secret Hinglish Note", createdAt = "12:00")
        repo.saveVault(VaultData(notes = listOf(note)))
        repo.lock()

        val wrong = repo.verifyPinAndUnlock("000000")
        assertTrue(wrong is PinVerifyResult.Error)

        val unlocked = repo.verifyPinAndUnlock("123456")
        assertTrue(unlocked is PinVerifyResult.Unlocked)
        val vault = (unlocked as PinVerifyResult.Unlocked).vaultData
        assertEquals("Secret Hinglish Note", vault.notes.first().text)
        assertEquals("Jitendra Maurya", vault.settings.ownerName)
        assertEquals("jitendermourya921@gmail.com", vault.settings.ownerEmail)

        repo.panicWipe()
        assertFalse(repo.hasPinConfigured())
    }

    @Test
    fun `aes gcm detects tampered ciphertext`() {
        val salt = VaultCrypto.randomBytes(16)
        val keys = VaultCrypto.derivePinKeys("654321", salt)
        val (iv, ct) = VaultCrypto.encryptAesGcm(keys.aesSecretKey(), "hello".toByteArray())
        ct[0] = (ct[0].toInt() xor 0xFF).toByte()
        var threw = false
        try {
            VaultCrypto.decryptAesGcm(keys.aesSecretKey(), iv, ct)
        } catch (_: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `phone access helper and spontaneous flirt work across languages`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = com.example.device.PhoneAccessHelper(context)
        val snap = helper.getStatusSnapshot()
        assertTrue(snap.batteryPercent in 0..100)

        val aiRepo = com.example.ai.JarvisAiRepository()
        val hiLine = aiRepo.fallbackSpontaneousFlirt("hi-IN", 80)
        val paLine = aiRepo.fallbackSpontaneousFlirt("pa-IN", 80)
        assertTrue(hiLine.isNotBlank())
        assertTrue(paLine.isNotBlank())
    }

    @Test
    fun `maim os desktop windows and shell commands work`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.JarvisViewModel(app)
        assertFalse(vm.uiState.value.desktopOpen)

        vm.openMaimDesktop()
        assertTrue(vm.uiState.value.desktopOpen)
        assertTrue(vm.uiState.value.desktopWindows.isNotEmpty())

        val helpOut = vm.runDesktopShellCommand("help")
        assertTrue(helpOut.any { it.contains("Commands:") })

        vm.openOrFocusDesktopApp(com.example.model.DesktopAppId.CALCULATOR)
        assertTrue(vm.uiState.value.desktopWindows.any { it.appId == com.example.model.DesktopAppId.CALCULATOR })

        vm.closeMaimDesktop()
        assertFalse(vm.uiState.value.desktopOpen)
    }
}
