package com.example.viewmodel

import android.app.Application
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.JarvisAiRepository
import com.example.device.PhoneAccessHelper
import com.example.device.PhoneStatusSnapshot
import com.example.model.ActiveDrawer
import com.example.model.AiActionIntent
import com.example.model.ChatMessage
import com.example.model.CodeSnippet
import com.example.model.DesktopAppId
import com.example.model.DesktopWindowState
import com.example.model.GitHubFileItem
import com.example.model.GitHubRepoItem
import com.example.model.JarvisMode
import com.example.model.NoteItem
import com.example.model.ReminderItem
import com.example.model.TimerItem
import com.example.model.TodoItem
import com.example.model.TtsVoiceOption
import com.example.model.VaultData
import com.example.model.VaultSettings
import com.example.model.VoiceState
import com.example.network.GitHubRepository
import com.example.security.BiometricVaultHelper
import com.example.security.PinVerifyResult
import com.example.security.VaultRepository
import com.example.voice.JarvisVoiceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

data class JarvisUiState(
    val isUnlocked: Boolean = false,
    val lockSubtitle: String = "ENTER 6-DIGIT NEURAL PIN",
    val pinInputLength: Int = 0,
    val lockError: String = "",
    val isAuthBusy: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnrolled: Boolean = false,

    val showBootOverlay: Boolean = false,
    val bootLines: List<String> = emptyList(),
    val bootProgress: Float = 0f,

    val mode: JarvisMode = JarvisMode.FLIRT,
    val handsFreeEnabled: Boolean = true,
    val cameraHudEnabled: Boolean = false,
    val useFrontCamera: Boolean = false,
    val voiceState: VoiceState = VoiceState.STANDBY,
    val rmsEnergy: Float = 0f,

    val clockText: String = "--:--",
    val telemetryText: String = "SYNC 98.4% // 14ms",
    val nowEpochMs: Long = System.currentTimeMillis(),

    val chatHistory: List<ChatMessage> = emptyList(),
    val activeSnippet: CodeSnippet? = null,
    val activeDrawer: ActiveDrawer = ActiveDrawer.NONE,
    val showPanicConfirm: Boolean = false,
    val desktopOpen: Boolean = false,
    val desktopWindows: List<DesktopWindowState> = listOf(
        DesktopWindowState(DesktopAppId.TERMINAL, offsetX = 16f, offsetY = 96f, zOrder = 1),
        DesktopWindowState(DesktopAppId.SYSTEM, offsetX = 48f, offsetY = 230f, zOrder = 2)
    ),

    val timers: List<TimerItem> = emptyList(),
    val reminders: List<ReminderItem> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),

    val settings: VaultSettings = VaultSettings(),
    val availableVoices: List<TtsVoiceOption> = emptyList(),
    val settingsNotice: String = "",
    val phoneStatus: PhoneStatusSnapshot = PhoneStatusSnapshot(
        batteryPercent = 85,
        isCharging = false,
        micGranted = false,
        cameraGranted = false,
        notificationsGranted = false,
        flashlightOn = false
    ),

    val githubRepos: List<GitHubRepoItem> = emptyList(),
    val githubFiles: List<GitHubFileItem> = emptyList(),
    val selectedRepoFullName: String = "",
    val githubStatus: String = "",
    val githubLoading: Boolean = false
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {
    private val vaultRepo = VaultRepository(application)
    private val aiRepo = JarvisAiRepository()
    private val githubRepo = GitHubRepository()
    val phoneHelper = PhoneAccessHelper(application)

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var pinBuffer = StringBuilder()
    private var setupFirstPin: String? = null
    private var idleJob: Job? = null
    private var spontaneousJob: Job? = null
    private var lastActivityMs = System.currentTimeMillis()
    private var lastConversationMs = System.currentTimeMillis()
    private var isSendingAi = false

    val voiceManager: JarvisVoiceManager = JarvisVoiceManager(
        context = application,
        onTranscript = { text -> sendUserMessage(text) },
        onStateChanged = { state ->
            val prevState = _uiState.value.voiceState
            _uiState.update { it.copy(voiceState = state) }
            if (state == VoiceState.UNAVAILABLE && prevState != VoiceState.UNAVAILABLE) {
                val infoMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = "Voice recognition is unavailable on this device right now — you can type below to chat with MAIM!"
                )
                _uiState.update { it.copy(chatHistory = (it.chatHistory + infoMsg).takeLast(40)) }
            }
            if (state == VoiceState.LISTENING || state == VoiceState.SPEAKING) {
                touchActivity()
                lastConversationMs = System.currentTimeMillis()
            }
        },
        onSpeakComplete = {
            lastConversationMs = System.currentTimeMillis()
            val s = _uiState.value
            if (s.isUnlocked && s.handsFreeEnabled && s.mode != JarvisMode.CODING && !isSendingAi) {
                voiceManager.startListening()
            }
        }
    )

    init {
        refreshLockScreenState()
        refreshPhoneStatus()
        startClockAndTelemetryLoop()
        viewModelScope.launch {
            voiceManager.availableVoices.collect { voices ->
                _uiState.update { it.copy(availableVoices = voices) }
            }
        }
        viewModelScope.launch {
            voiceManager.rmsEnergy.collect { energy ->
                _uiState.update { it.copy(rmsEnergy = energy) }
            }
        }
    }

    private fun refreshLockScreenState(error: String = "") {
        val hasPin = vaultRepo.hasPinConfigured()
        val bioAvailable = BiometricVaultHelper.canUseBiometrics(getApplication())
        val bioEnrolled = bioAvailable && vaultRepo.hasBiometricWrappedKey()
        _uiState.update {
            it.copy(
                isUnlocked = false,
                lockSubtitle = if (hasPin) "ENTER 6-DIGIT NEURAL PIN" else "CREATE 6-DIGIT NEURAL PIN",
                pinInputLength = 0,
                lockError = error,
                biometricAvailable = bioAvailable,
                biometricEnrolled = bioEnrolled
            )
        }
        pinBuffer.clear()
        setupFirstPin = null
    }

    private fun startClockAndTelemetryLoop() {
        val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        viewModelScope.launch {
            var tickCount = 0
            while (true) {
                val now = System.currentTimeMillis()
                val formatted = clockFmt.format(Date(now))
                val telemetry = if (tickCount % 3 == 0) {
                    val sync = "%.1f".format(Locale.US, 97.4f + Random.nextFloat() * 2.4f)
                    val lat = 11 + Random.nextInt(9)
                    "SYNC $sync% // ${lat}ms"
                } else {
                    _uiState.value.telemetryText
                }

                checkTimersAndReminders(now, formatted)
                _uiState.update {
                    it.copy(
                        clockText = formatted,
                        telemetryText = telemetry,
                        nowEpochMs = now
                    )
                }
                tickCount++
                delay(1_000L)
            }
        }
    }

    private fun checkTimersAndReminders(nowMs: Long, currentHHmm: String) {
        val state = _uiState.value
        if (!state.isUnlocked) return

        val expired = state.timers.filter { nowMs >= it.endsAtEpochMs }
        if (expired.isNotEmpty()) {
            val remaining = state.timers.filter { nowMs < it.endsAtEpochMs }
            _uiState.update { it.copy(timers = remaining) }
            expired.forEach { t ->
                playAlertFeedback()
                val alertMsg = "Yaar, tumhara ${t.label} timer poora ho gaya!"
                appendModelAlert(alertMsg)
            }
        }

        var remChanged = false
        val updatedReminders = state.reminders.map { rem ->
            if (!rem.fired && rem.timeHHmm == currentHHmm) {
                remChanged = true
                playAlertFeedback()
                appendModelAlert("Reminder yaar: ${rem.text}")
                rem.copy(fired = true)
            } else {
                rem
            }
        }
        if (remChanged) {
            _uiState.update { it.copy(reminders = updatedReminders) }
            persistVaultAsync()
        }
    }

    private fun appendModelAlert(text: String) {
        val msg = ChatMessage(id = UUID.randomUUID().toString(), isUser = false, text = text)
        _uiState.update { it.copy(chatHistory = (it.chatHistory + msg).takeLast(40)) }
        phoneHelper.postMaimNotification("MAIM", text)
        voiceManager.speak(text)
    }

    private fun playAlertFeedback() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        }
        runCatching {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = app.getSystemService(Vibrator::class.java)
                @Suppress("DEPRECATION")
                v?.vibrate(220)
            }
        }
    }

    fun onPinDigit(digit: Char) {
        if (_uiState.value.isAuthBusy || _uiState.value.isUnlocked) return
        val lockRem = vaultRepo.getLockoutRemainingSeconds()
        if (lockRem > 0) {
            _uiState.update { it.copy(lockError = "Locked out for ${lockRem}s.") }
            return
        }
        if (pinBuffer.length >= 6) return
        pinBuffer.append(digit)
        _uiState.update { it.copy(pinInputLength = pinBuffer.length, lockError = "") }

        if (pinBuffer.length == 6) {
            val pin = pinBuffer.toString()
            pinBuffer.clear()
            _uiState.update { it.copy(pinInputLength = 0) }
            handlePinSubmitted(pin)
        }
    }

    fun onPinBackspace() {
        if (_uiState.value.isAuthBusy || pinBuffer.isEmpty()) return
        pinBuffer.deleteCharAt(pinBuffer.length - 1)
        _uiState.update { it.copy(pinInputLength = pinBuffer.length) }
    }

    private fun handlePinSubmitted(pin: String) {
        if (!vaultRepo.hasPinConfigured()) {
            val first = setupFirstPin
            if (first == null) {
                setupFirstPin = pin
                _uiState.update { it.copy(lockSubtitle = "CONFIRM 6-DIGIT NEURAL PIN", lockError = "") }
                return
            }
            if (first != pin) {
                setupFirstPin = null
                _uiState.update {
                    it.copy(
                        lockSubtitle = "CREATE 6-DIGIT NEURAL PIN",
                        lockError = "PINs did not match. Try again."
                    )
                }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isAuthBusy = true) }
                val res = vaultRepo.createInitialPin(pin)
                onAuthResult(res)
            }
        } else {
            viewModelScope.launch {
                _uiState.update { it.copy(isAuthBusy = true) }
                val res = vaultRepo.verifyPinAndUnlock(pin)
                onAuthResult(res)
            }
        }
    }

    fun unlockWithBiometricBytes(bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthBusy = true) }
            val res = vaultRepo.unlockWithBiometricBytes(bytes)
            onAuthResult(res)
        }
    }

    fun showLockError(msg: String) {
        _uiState.update { it.copy(lockError = msg, isAuthBusy = false) }
    }

    private suspend fun onAuthResult(result: PinVerifyResult) {
        when (result) {
            is PinVerifyResult.Error -> {
                _uiState.update { it.copy(isAuthBusy = false, lockError = result.message) }
            }
            is PinVerifyResult.Unlocked -> {
                val data = result.vaultData
                voiceManager.setLanguage(data.settings.sttLang)
                if (data.settings.ttsVoiceName.isNotBlank()) {
                    voiceManager.setSelectedVoice(data.settings.ttsVoiceName)
                }

                val initialHistory = if (result.isFirstSetup && data.chatHistory.isEmpty()) {
                    listOf(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            text = "Hey... kaisa raha tumhara din? Main yahin hoon tumhare saath, bolo kya baat karein?"
                        )
                    )
                } else {
                    data.chatHistory
                }

                _uiState.update {
                    it.copy(
                        isAuthBusy = false,
                        isUnlocked = true,
                        lockError = "",
                        chatHistory = initialHistory,
                        notes = data.notes,
                        todos = data.todos,
                        reminders = data.reminders,
                        settings = data.settings,
                        biometricEnrolled = BiometricVaultHelper.canUseBiometrics(getApplication()) &&
                            vaultRepo.hasBiometricWrappedKey(),
                        activeSnippet = initialHistory.lastOrNull { m -> m.codeSnippet != null }?.codeSnippet
                    )
                }
                if (result.isFirstSetup) {
                    persistVaultAsync()
                }
                startIdleWatcher()
                startSpontaneousFlirtLoop()
                refreshPhoneStatus()
                runBootSequence()
            }
        }
    }

    private suspend fun runBootSequence() {
        val steps = listOf(
            "INITIALIZING MAIM CORE...",
            "IDENTITY SHIELDED // PRIVATE SESSION",
            "UNLOCKING AES-256-GCM VAULT...",
            "ENCRYPTED LINK ESTABLISHED"
        )
        _uiState.update { it.copy(showBootOverlay = true, bootLines = emptyList(), bootProgress = 0f) }
        steps.forEachIndexed { idx, line ->
            _uiState.update {
                it.copy(
                    bootLines = it.bootLines + line,
                    bootProgress = (idx + 1).toFloat() / steps.size
                )
            }
            delay(280L)
        }
        delay(220L)
        _uiState.update { it.copy(showBootOverlay = false) }
    }

    fun touchActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    private fun startSpontaneousFlirtLoop() {
        spontaneousJob?.cancel()
        lastConversationMs = System.currentTimeMillis()
        spontaneousJob = viewModelScope.launch {
            while (true) {
                delay(6_000L)
                val state = _uiState.value
                if (!state.isUnlocked) break
                val quietMs = System.currentTimeMillis() - lastConversationMs
                val canSpeakNow = state.settings.spontaneousFlirtEnabled &&
                    !state.showBootOverlay &&
                    !isSendingAi &&
                    state.voiceState == VoiceState.STANDBY &&
                    state.activeDrawer == ActiveDrawer.NONE &&
                    quietMs >= SPONTANEOUS_FLIRT_INTERVAL_MS

                if (canSpeakNow) {
                    triggerSpontaneousFlirtLine()
                }
            }
        }
    }

    private suspend fun triggerSpontaneousFlirtLine() {
        if (isSendingAi || !_uiState.value.isUnlocked) return
        lastConversationMs = System.currentTimeMillis()
        val s = _uiState.value
        val status = phoneHelper.getStatusSnapshot()
        _uiState.update { it.copy(phoneStatus = status) }
        val localTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val line = aiRepo.generateSpontaneousFlirt(
            history = s.chatHistory,
            langHint = s.settings.sttLang,
            localTimeStr = localTimeStr,
            batteryPercent = status.batteryPercent,
            isCharging = status.isCharging
        )
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = line
        )
        _uiState.update { it.copy(chatHistory = (it.chatHistory + msg).takeLast(40)) }
        persistVaultAsync()
        phoneHelper.postMaimNotification("MAIM", line)
        voiceManager.speak(line)
    }

    private fun startIdleWatcher() {
        idleJob?.cancel()
        lastActivityMs = System.currentTimeMillis()
        idleJob = viewModelScope.launch {
            while (true) {
                delay(5_000L)
                val state = _uiState.value
                if (!state.isUnlocked) break
                val busyVoice = state.voiceState == VoiceState.LISTENING || state.voiceState == VoiceState.SPEAKING
                if (busyVoice || isSendingAi) {
                    lastActivityMs = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastActivityMs >= IDLE_LOCK_MS) {
                    lockVault("Auto-locked after 2 minutes idle.")
                    break
                }
            }
        }
    }

    fun lockVault(notice: String = "Locked manually.") {
        idleJob?.cancel()
        spontaneousJob?.cancel()
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        vaultRepo.lock()
        _uiState.update {
            it.copy(
                cameraHudEnabled = false,
                desktopOpen = false,
                activeDrawer = ActiveDrawer.NONE,
                showPanicConfirm = false,
                showBootOverlay = false
            )
        }
        refreshLockScreenState(notice)
    }

    fun getBiometricBlob(): Pair<ByteArray, ByteArray>? = vaultRepo.getBiometricBlob()

    fun getActiveAesKeyBytesForBiometric(): ByteArray? = vaultRepo.getActiveKeyBytesCopy()

    fun onBiometricEnrolled(iv: ByteArray, wrappedKey: ByteArray) {
        vaultRepo.saveBiometricBlob(iv, wrappedKey)
        _uiState.update {
            it.copy(
                biometricEnrolled = true,
                settingsNotice = "Biometric unlock sealed in Android Keystore."
            )
        }
    }

    fun onBiometricEnrollFailed(msg: String) {
        _uiState.update { it.copy(settingsNotice = msg) }
    }

    fun requestPanicWipeModal(show: Boolean) {
        _uiState.update { it.copy(showPanicConfirm = show) }
    }

    fun confirmPanicWipe() {
        viewModelScope.launch {
            idleJob?.cancel()
            spontaneousJob?.cancel()
            voiceManager.stopSpeaking()
            voiceManager.stopListening()
            vaultRepo.panicWipe()
            _uiState.update {
                JarvisUiState(
                    clockText = it.clockText,
                    telemetryText = it.telemetryText,
                    availableVoices = it.availableVoices
                )
            }
            refreshLockScreenState("Vault wiped. Create a new 6-digit PIN.")
        }
    }

    fun sendUserMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank() || isSendingAi || !_uiState.value.isUnlocked) return

        touchActivity()
        lastConversationMs = System.currentTimeMillis()
        isSendingAi = true

        val userMsg = ChatMessage(id = UUID.randomUUID().toString(), isUser = true, text = text)
        _uiState.update {
            it.copy(
                chatHistory = (it.chatHistory + userMsg).takeLast(40),
                voiceState = VoiceState.THINKING
            )
        }

        viewModelScope.launch {
            val state = _uiState.value
            val status = phoneHelper.getStatusSnapshot()
            _uiState.update { it.copy(phoneStatus = status) }
            val localTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val result = aiRepo.generateReply(
                userMessage = text,
                history = state.chatHistory.dropLast(1),
                mode = state.mode,
                langHint = state.settings.sttLang,
                localTimeStr = localTimeStr,
                ownerName = state.settings.ownerName,
                batteryInfo = "${status.batteryPercent}%${if (status.isCharging) " charging" else ""}"
            )

            isSendingAi = false
            lastConversationMs = System.currentTimeMillis()
            result.onSuccess { ai ->
                applyAiAction(ai.action)
                val snippet = if (ai.isCode && ai.code.isNotBlank()) {
                    CodeSnippet(code = ai.code, language = ai.codeLang.ifBlank { "plaintext" })
                } else {
                    null
                }
                val replyText = ai.reply.ifBlank { "Arre, thoda network glitch aa gaya yaar. Ek baar phir bolo?" }
                val botMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = replyText,
                    codeSnippet = snippet
                )

                _uiState.update {
                    it.copy(
                        chatHistory = (it.chatHistory + botMsg).takeLast(40),
                        activeSnippet = snippet ?: it.activeSnippet,
                        activeDrawer = if (snippet != null) ActiveDrawer.CODING else it.activeDrawer
                    )
                }
                persistVaultAsync()
                voiceManager.speak(replyText)
            }.onFailure { err ->
                val errorReply = err.message ?: "Neural link unavailable."
                val errMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = "Error: $errorReply"
                )
                _uiState.update {
                    it.copy(
                        chatHistory = (it.chatHistory + errMsg).takeLast(40),
                        voiceState = VoiceState.STANDBY
                    )
                }
            }
        }
    }

    private fun applyAiAction(action: AiActionIntent) {
        when (action.type) {
            "set_timer" -> if (action.seconds > 0) {
                addTimerSeconds(action.label.ifBlank { "MAIM Timer" }, action.seconds)
            }
            "set_reminder" -> if (action.itemText.isNotBlank() && action.reminderTime.isNotBlank()) {
                addReminder(action.itemText, action.reminderTime)
            }
            "add_todo" -> if (action.itemText.isNotBlank()) {
                addTodo(action.itemText)
            }
            "add_note" -> if (action.itemText.isNotBlank()) {
                addNote(action.itemText)
            }
            "toggle_flashlight" -> setFlashlight(action.flag)
            "open_dialer" -> phoneHelper.openDialer(action.itemText)
            "set_alarm" -> phoneHelper.setPhoneAlarm(action.hour, action.minute, action.label.ifBlank { "MAIM Alarm" })
            "open_wifi_settings" -> phoneHelper.openWifiSettings()
            "open_bluetooth_settings" -> phoneHelper.openBluetoothSettings()
            "open_maim_windows" -> openMaimDesktop()
            "close_maim_windows" -> closeMaimDesktop()
        }
    }

    fun openMaimDesktop() {
        touchActivity()
        refreshPhoneStatus()
        _uiState.update { state ->
            val windows = if (state.desktopWindows.isEmpty()) {
                listOf(DesktopWindowState(DesktopAppId.TERMINAL, offsetX = 16f, offsetY = 96f, zOrder = 1))
            } else {
                state.desktopWindows
            }
            state.copy(desktopOpen = true, desktopWindows = windows)
        }
    }

    fun closeMaimDesktop() {
        touchActivity()
        _uiState.update { it.copy(desktopOpen = false) }
    }

    fun openOrFocusDesktopApp(appId: DesktopAppId) {
        touchActivity()
        _uiState.update { state ->
            val maxZ = (state.desktopWindows.maxOfOrNull { it.zOrder } ?: 0) + 1
            val existing = state.desktopWindows.firstOrNull { it.appId == appId }
            val updated = if (existing != null) {
                state.desktopWindows.map {
                    if (it.appId == appId) it.copy(isMinimized = false, zOrder = maxZ) else it
                }
            } else {
                val shift = (state.desktopWindows.size % 4) * 34f
                state.desktopWindows + DesktopWindowState(
                    appId = appId,
                    offsetX = 18f + shift,
                    offsetY = 96f + shift,
                    zOrder = maxZ
                )
            }
            state.copy(desktopOpen = true, desktopWindows = updated)
        }
    }

    fun focusDesktopWindow(appId: DesktopAppId) {
        _uiState.update { state ->
            val maxZ = (state.desktopWindows.maxOfOrNull { it.zOrder } ?: 0) + 1
            state.copy(
                desktopWindows = state.desktopWindows.map {
                    if (it.appId == appId) it.copy(zOrder = maxZ) else it
                }
            )
        }
    }

    fun dragDesktopWindow(appId: DesktopAppId, dx: Float, dy: Float) {
        touchActivity()
        _uiState.update { state ->
            state.copy(
                desktopWindows = state.desktopWindows.map {
                    if (it.appId == appId) {
                        it.copy(offsetX = it.offsetX + dx, offsetY = it.offsetY + dy)
                    } else {
                        it
                    }
                }
            )
        }
    }

    fun toggleMinimizeDesktopWindow(appId: DesktopAppId) {
        touchActivity()
        _uiState.update { state ->
            state.copy(
                desktopWindows = state.desktopWindows.map {
                    if (it.appId == appId) it.copy(isMinimized = !it.isMinimized) else it
                }
            )
        }
    }

    fun toggleMaximizeDesktopWindow(appId: DesktopAppId) {
        touchActivity()
        _uiState.update { state ->
            val maxZ = (state.desktopWindows.maxOfOrNull { it.zOrder } ?: 0) + 1
            state.copy(
                desktopWindows = state.desktopWindows.map {
                    if (it.appId == appId) it.copy(isMaximized = !it.isMaximized, zOrder = maxZ) else it
                }
            )
        }
    }

    fun closeDesktopWindow(appId: DesktopAppId) {
        touchActivity()
        _uiState.update { state ->
            state.copy(desktopWindows = state.desktopWindows.filterNot { it.appId == appId })
        }
    }

    fun runDesktopShellCommand(cmd: String): List<String> {
        touchActivity()
        val trimmed = cmd.trim()
        val lower = trimmed.lowercase(Locale.US)
        val snap = phoneHelper.getStatusSnapshot()
        _uiState.update { it.copy(phoneStatus = snap) }
        return when {
            lower == "help" -> listOf(
                "Commands: help, time, battery, ls, notes, todos,",
                "torch on, torch off, dial [num], wifi, bt, open <app>, flirt, ask <msg>, clear"
            )
            lower == "time" -> listOf("Local Time: ${_uiState.value.clockText}")
            lower == "battery" -> listOf(
                "Battery: ${snap.batteryPercent}% (${if (snap.isCharging) "Charging" else "On Battery"})"
            )
            lower == "whoami" -> listOf("Session: IDENTITY SHIELDED (AES-256-GCM)")
            lower == "ls" -> listOf(
                "notes(${_uiState.value.notes.size})  todos(${_uiState.value.todos.size})  reminders(${_uiState.value.reminders.size})  snippets(${if (_uiState.value.activeSnippet != null) 1 else 0})"
            )
            lower == "notes" -> _uiState.value.notes.take(5).map { "- [${it.createdAt}] ${it.text}" }
                .ifEmpty { listOf("(no encrypted notes)") }
            lower == "todos" -> _uiState.value.todos.take(5).map { "[${if (it.done) "x" else " "}] ${it.text}" }
                .ifEmpty { listOf("(no tasks)") }
            lower == "torch on" -> {
                setFlashlight(true)
                listOf("Flashlight -> ON")
            }
            lower == "torch off" -> {
                setFlashlight(false)
                listOf("Flashlight -> OFF")
            }
            lower.startsWith("dial") -> {
                val num = trimmed.drop(4).trim()
                phoneHelper.openDialer(num)
                listOf("Opening phone dialer ${if (num.isNotBlank()) "($num)" else ""}...")
            }
            lower == "wifi" -> {
                openWifiSettings()
                listOf("Opening Wi-Fi settings...")
            }
            lower == "bt" || lower == "bluetooth" -> {
                openBluetoothSettings()
                listOf("Opening Bluetooth settings...")
            }
            lower.startsWith("open ") -> {
                val appKey = lower.removePrefix("open ").trim()
                val target = DesktopAppId.entries.firstOrNull {
                    it.name.lowercase(Locale.US).contains(appKey) || it.title.lowercase(Locale.US).contains(appKey)
                }
                if (target != null) {
                    openOrFocusDesktopApp(target)
                    listOf("Launched ${target.title}")
                } else {
                    listOf("Unknown app '$appKey'. Try: terminal, files, system, code, calc")
                }
            }
            lower == "flirt" -> {
                val line = aiRepo.fallbackSpontaneousFlirt(_uiState.value.settings.sttLang, snap.batteryPercent)
                voiceManager.speak(line)
                listOf("MAIM: $line")
            }
            else -> listOf("Unknown command '$trimmed'. Type 'help' or 'ask $trimmed'.")
        }
    }

    fun toggleMode() {
        touchActivity()
        _uiState.update {
            val next = when (it.mode) {
                JarvisMode.FLIRT -> JarvisMode.FRIEND
                JarvisMode.FRIEND -> JarvisMode.CODING
                JarvisMode.CODING -> JarvisMode.FLIRT
            }
            it.copy(mode = next)
        }
    }

    fun toggleHandsFree() {
        touchActivity()
        _uiState.update {
            val next = !it.handsFreeEnabled
            if (!next && it.voiceState == VoiceState.LISTENING) {
                voiceManager.stopListening()
            }
            it.copy(handsFreeEnabled = next)
        }
    }

    fun setCameraHudEnabled(enabled: Boolean) {
        touchActivity()
        _uiState.update { it.copy(cameraHudEnabled = enabled) }
    }

    fun toggleCameraLens() {
        touchActivity()
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    }

    fun openDrawer(drawer: ActiveDrawer) {
        touchActivity()
        _uiState.update { it.copy(activeDrawer = drawer, settingsNotice = "") }
        if (drawer == ActiveDrawer.GITHUB && _uiState.value.githubRepos.isEmpty()) {
            val saved = _uiState.value.settings
            if (saved.githubUsername.isNotBlank() || saved.githubToken.isNotBlank()) {
                loadGitHubRepos(saved.githubUsername, saved.githubToken)
            }
        }
    }

    fun openSnippet(snippet: CodeSnippet) {
        touchActivity()
        _uiState.update { it.copy(activeSnippet = snippet, activeDrawer = ActiveDrawer.CODING) }
    }

    fun addTimerMinutes(label: String, minutes: Int) = addTimerSeconds(label, minutes * 60)

    private fun addTimerSeconds(label: String, seconds: Int) {
        touchActivity()
        val item = TimerItem(
            id = UUID.randomUUID().toString(),
            label = label,
            endsAtEpochMs = System.currentTimeMillis() + seconds.coerceIn(1, 86_400) * 1000L
        )
        _uiState.update { it.copy(timers = it.timers + item) }
    }

    fun deleteTimer(id: String) {
        _uiState.update { it.copy(timers = it.timers.filterNot { t -> t.id == id }) }
    }

    fun addReminder(text: String, hhmm: String) {
        touchActivity()
        val item = ReminderItem(id = UUID.randomUUID().toString(), text = text, timeHHmm = hhmm)
        _uiState.update { it.copy(reminders = listOf(item) + it.reminders) }
        persistVaultAsync()
    }

    fun deleteReminder(id: String) {
        _uiState.update { it.copy(reminders = it.reminders.filterNot { r -> r.id == id }) }
        persistVaultAsync()
    }

    fun addTodo(text: String) {
        touchActivity()
        val item = TodoItem(id = UUID.randomUUID().toString(), text = text)
        _uiState.update { it.copy(todos = listOf(item) + it.todos) }
        persistVaultAsync()
    }

    fun toggleTodo(id: String) {
        _uiState.update {
            it.copy(todos = it.todos.map { t -> if (t.id == id) t.copy(done = !t.done) else t })
        }
        persistVaultAsync()
    }

    fun deleteTodo(id: String) {
        _uiState.update { it.copy(todos = it.todos.filterNot { t -> t.id == id }) }
        persistVaultAsync()
    }

    fun addNote(text: String) {
        touchActivity()
        val created = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val item = NoteItem(id = UUID.randomUUID().toString(), text = text, createdAt = created)
        _uiState.update { it.copy(notes = listOf(item) + it.notes) }
        persistVaultAsync()
    }

    fun deleteNote(id: String) {
        _uiState.update { it.copy(notes = it.notes.filterNot { n -> n.id == id }) }
        persistVaultAsync()
    }

    fun updateSttLang(langTag: String) {
        touchActivity()
        voiceManager.setLanguage(langTag)
        _uiState.update { it.copy(settings = it.settings.copy(sttLang = langTag)) }
        persistVaultAsync()
    }

    fun cycleLanguage() {
        val order = listOf("hi-IN", "pa-IN", "en-IN", "en-US")
        val current = _uiState.value.settings.sttLang
        val next = order[(order.indexOf(current) + 1) % order.size]
        updateSttLang(next)
    }

    fun updateTtsVoice(voiceName: String) {
        touchActivity()
        voiceManager.setSelectedVoice(voiceName)
        _uiState.update { it.copy(settings = it.settings.copy(ttsVoiceName = voiceName)) }
        persistVaultAsync()
    }

    fun toggleReducedMotion(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(reducedMotionOverride = enabled)) }
        persistVaultAsync()
    }

    fun toggleSpontaneousFlirt(enabled: Boolean) {
        touchActivity()
        _uiState.update {
            it.copy(
                settings = it.settings.copy(spontaneousFlirtEnabled = enabled),
                settingsNotice = if (enabled) "Spontaneous flirty talk enabled." else "Spontaneous talk paused."
            )
        }
        persistVaultAsync()
    }

    fun refreshPhoneStatus() {
        _uiState.update { it.copy(phoneStatus = phoneHelper.getStatusSnapshot()) }
    }

    fun setFlashlight(enabled: Boolean? = null) {
        touchActivity()
        val next = enabled ?: !_uiState.value.phoneStatus.flashlightOn
        val ok = phoneHelper.setFlashlight(next)
        val snap = phoneHelper.getStatusSnapshot()
        _uiState.update {
            it.copy(
                phoneStatus = snap,
                settingsNotice = if (ok) "Flashlight ${if (next) "ON" else "OFF"}." else "Flashlight unavailable on this device."
            )
        }
    }

    fun openDialer() {
        touchActivity()
        phoneHelper.openDialer()
    }

    fun openWifiSettings() {
        touchActivity()
        phoneHelper.openWifiSettings()
    }

    fun openBluetoothSettings() {
        touchActivity()
        phoneHelper.openBluetoothSettings()
    }

    fun openAppPermissionsSettings() {
        touchActivity()
        phoneHelper.openAppPermissionsSettings()
    }

    fun updateOwnerProfile(name: String, email: String) {
        touchActivity()
        val safeName = name.ifBlank { "Jitendra Maurya" }
        val safeEmail = email.ifBlank { "jitendermourya921@gmail.com" }
        _uiState.update {
            it.copy(
                settings = it.settings.copy(ownerName = safeName, ownerEmail = safeEmail),
                settingsNotice = "Owner profile saved to encrypted vault."
            )
        }
        persistVaultAsync()
    }

    fun testVoice() {
        touchActivity()
        val sample = when (_uiState.value.settings.sttLang) {
            "pa-IN" -> "Sat sri akal ji... main MAIM haan, tuhadi aawaz sunan layi bilkul ready haan. Dasso ki haal hai?"
            "en-IN", "en-US" -> "Hey there... I am MAIM, right here with you. Tell me, how are you feeling today?"
            else -> "Suno... main MAIM hoon, bilkul ready hoon tumhare saath baat karne ke liye. Bolo aaj kaisa feel kar rahe ho?"
        }
        voiceManager.speak(sample)
    }

    fun loadGitHubRepos(username: String, token: String) {
        touchActivity()
        _uiState.update {
            it.copy(
                settings = it.settings.copy(githubUsername = username, githubToken = token),
                githubLoading = true,
                githubStatus = "Connecting to GitHub uplink...",
                githubFiles = emptyList()
            )
        }
        persistVaultAsync()

        viewModelScope.launch {
            val res = githubRepo.fetchRepos(username, token)
            res.onSuccess { repos ->
                _uiState.update {
                    it.copy(
                        githubLoading = false,
                        githubRepos = repos,
                        githubStatus = if (repos.isEmpty()) {
                            "No repositories found."
                        } else {
                            "Loaded ${repos.size} repositories."
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        githubLoading = false,
                        githubStatus = err.message ?: "Failed to load repositories."
                    )
                }
            }
        }
    }

    fun inspectGitHubRepoFiles(fullName: String) {
        val parts = fullName.split('/')
        if (parts.size != 2) return
        val (owner, repo) = parts
        _uiState.update { it.copy(githubLoading = true, selectedRepoFullName = fullName) }
        viewModelScope.launch {
            val res = githubRepo.fetchRootFiles(owner, repo, _uiState.value.settings.githubToken)
            res.onSuccess { files ->
                _uiState.update { it.copy(githubLoading = false, githubFiles = files) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        githubLoading = false,
                        githubStatus = err.message ?: "Could not load files."
                    )
                }
            }
        }
    }

    private fun persistVaultAsync() {
        val s = _uiState.value
        if (!s.isUnlocked) return
        viewModelScope.launch {
            vaultRepo.saveVault(
                VaultData(
                    chatHistory = s.chatHistory.takeLast(40),
                    notes = s.notes,
                    todos = s.todos,
                    reminders = s.reminders,
                    settings = s.settings
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }

    companion object {
        private const val IDLE_LOCK_MS = 2 * 60 * 1000L
        private const val SPONTANEOUS_FLIRT_INTERVAL_MS = 30_000L
    }
}
