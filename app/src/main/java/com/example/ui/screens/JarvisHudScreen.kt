package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.ActiveDrawer
import com.example.model.ChatMessage
import com.example.model.JarvisMode
import com.example.model.VoiceState
import com.example.ui.desktop.MaimDesktopOverlay
import com.example.ui.hud.BootSequenceOverlay
import com.example.ui.hud.CameraHudBackground
import com.example.ui.hud.GlitchTitleText
import com.example.ui.hud.HolographicAvatarCanvas
import com.example.ui.hud.MatrixGridBackground
import com.example.ui.hud.ScanlineOverlay
import com.example.ui.hud.VoiceRadarWidget
import com.example.ui.hud.hudGlassPanel
import com.example.ui.panels.CodingDrawer
import com.example.ui.panels.GitHubDrawer
import com.example.ui.panels.SettingsDrawer
import com.example.ui.panels.UtilitiesDrawer
import com.example.ui.theme.JarvisColors
import com.example.viewmodel.JarvisUiState
import com.example.viewmodel.JarvisViewModel

@Composable
fun JarvisHudScreen(
    uiState: JarvisUiState,
    viewModel: JarvisViewModel,
    onBiometricUnlockClick: () -> Unit,
    onBiometricEnrollClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemReducedMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val reducedMotion = systemReducedMotion || uiState.settings.reducedMotionOverride

    var inputDraft by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    LaunchedEffect(uiState.chatHistory.size) {
        if (uiState.chatHistory.isNotEmpty()) {
            chatListState.animateScrollToItem(uiState.chatHistory.lastIndex)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setCameraHudEnabled(true)
        } else {
            viewModel.setCameraHudEnabled(false)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPhoneStatus()
        if (granted) {
            viewModel.voiceManager.startListening()
        }
    }

    val phoneAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPhoneStatus()
    }

    if (uiState.activeDrawer != ActiveDrawer.NONE) {
        BackHandler { viewModel.openDrawer(ActiveDrawer.NONE) }
    } else if (uiState.showPanicConfirm) {
        BackHandler { viewModel.requestPanicWipeModal(false) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 0: Optional on-device CameraX SurfaceView AR background
        if (uiState.isUnlocked && uiState.cameraHudEnabled) {
            CameraHudBackground(
                useFrontCamera = uiState.useFrontCamera,
                onError = { viewModel.setCameraHudEnabled(false) }
            )
        }

        // Layer 1: Matrix rain + perspective grid
        MatrixGridBackground(
            cameraActive = uiState.cameraHudEnabled,
            reducedMotion = reducedMotion
        )

        // Layer 2: Interactive 4D real-human avatar with fingertip void-tap launcher
        HolographicAvatarCanvas(
            isSpeaking = uiState.voiceState == VoiceState.SPEAKING,
            isListening = uiState.voiceState == VoiceState.LISTENING,
            speechEnergy = uiState.rmsEnergy,
            reducedMotion = reducedMotion,
            desktopOpen = uiState.desktopOpen,
            onVoidFingerTap = {
                if (uiState.isUnlocked) {
                    viewModel.openMaimDesktop()
                }
            }
        )

        // Layer 3: Scanline HUD overlay
        ScanlineOverlay(reducedMotion = reducedMotion)

        // Layer 4: Foreground HUD or Lock Screen
        if (!uiState.isUnlocked) {
            LockScreen(
                subtitle = uiState.lockSubtitle,
                pinLength = uiState.pinInputLength,
                errorMessage = uiState.lockError,
                biometricEnabled = uiState.biometricEnrolled,
                isBusy = uiState.isAuthBusy,
                reducedMotion = reducedMotion,
                onDigitClick = viewModel::onPinDigit,
                onBackspaceClick = viewModel::onPinBackspace,
                onBiometricClick = onBiometricUnlockClick,
                onPanicWipeClick = { viewModel.requestPanicWipeModal(true) }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .align(Alignment.Center)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Telemetry & Controls Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hudGlassPanel()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(JarvisColors.MatrixEmerald)
                            )
                            GlitchTitleText(
                                text = "MAIM",
                                style = MaterialTheme.typography.titleMedium,
                                reducedMotion = reducedMotion
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SIMULATED",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisColors.MatrixEmerald,
                                modifier = Modifier
                                    .border(1.dp, JarvisColors.MatrixEmerald.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            Text(
                                text = "PRIVATE // ${uiState.telemetryText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisColors.MutedCyan
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = uiState.clockText,
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisColors.MatrixEmerald
                        )
                        IconButton(
                            onClick = {
                                if (uiState.cameraHudEnabled) {
                                    viewModel.setCameraHudEnabled(false)
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.setCameraHudEnabled(true)
                                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .border(
                                    1.dp,
                                    if (uiState.cameraHudEnabled) JarvisColors.MatrixEmerald else JarvisColors.CyanBorder,
                                    CircleShape
                                )
                                .background(
                                    if (uiState.cameraHudEnabled) JarvisColors.EmeraldTint else JarvisColors.CyanTint,
                                    CircleShape
                                )
                                .testTag("ar_camera_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Toggle AR Camera HUD",
                                tint = if (uiState.cameraHudEnabled) JarvisColors.MatrixEmerald else JarvisColors.NeonCyan
                            )
                        }
                        if (uiState.cameraHudEnabled) {
                            IconButton(
                                onClick = { viewModel.toggleCameraLens() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .border(1.dp, JarvisColors.MatrixEmerald, CircleShape)
                                    .background(JarvisColors.EmeraldTint, CircleShape)
                                    .testTag("ar_camera_flip")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Flip AR Camera Lens",
                                    tint = JarvisColors.MatrixEmerald
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.lockVault() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .border(1.dp, JarvisColors.CyanBorder, CircleShape)
                                .background(JarvisColors.CyanTint, CircleShape)
                                .testTag("lock_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Vault",
                                tint = JarvisColors.NeonCyan
                            )
                        }
                    }
                }

                // Middle Zone: Radar + Active Timers + Transparent Chat Stream
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        VoiceRadarWidget(
                            voiceState = uiState.voiceState,
                            rmsEnergy = uiState.rmsEnergy,
                            reducedMotion = reducedMotion
                        )

                        if (uiState.timers.isNotEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                uiState.timers.take(3).forEach { timer ->
                                    val remSec = ((timer.endsAtEpochMs - uiState.nowEpochMs) / 1000L).coerceAtLeast(0L)
                                    val mm = (remSec / 60).toString().padStart(2, '0')
                                    val ss = (remSec % 60).toString().padStart(2, '0')
                                    Text(
                                        text = "${timer.label} $mm:$ss",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisColors.MatrixEmerald,
                                        modifier = Modifier
                                            .background(JarvisColors.EmeraldTint, RoundedCornerShape(99.dp))
                                            .border(1.dp, JarvisColors.MatrixEmerald, RoundedCornerShape(99.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 84.dp, max = 230.dp)
                            .hudGlassPanel()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.chatHistory.isEmpty()) {
                            item {
                                Text(
                                    text = "Tap MIC or type to talk with MAIM. Everything stays AES-256-GCM encrypted on-device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisColors.MutedCyan
                                )
                            }
                        }
                        items(uiState.chatHistory, key = { it.id }) { msg ->
                            ChatBubble(
                                msg = msg,
                                ownerLabel = "YOU",
                                onViewCode = { snippet -> viewModel.openSnippet(snippet) }
                            )
                        }
                    }
                }

                // Bottom Command Dock
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hudGlassPanel()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modeLabel = when (uiState.mode) {
                            JarvisMode.FLIRT -> "MOOD: FLIRT"
                            JarvisMode.FRIEND -> "MOOD: FRIEND"
                            JarvisMode.CODING -> "MOOD: CODING"
                        }
                        HudChipButton(
                            label = modeLabel,
                            active = uiState.mode == JarvisMode.FLIRT || uiState.mode == JarvisMode.CODING,
                            onClick = { viewModel.toggleMode() },
                            testTag = "mode_toggle_chip"
                        )
                        HudChipButton(
                            label = if (uiState.settings.spontaneousFlirtEnabled) "AUTO-TALK: ON" else "AUTO-TALK: OFF",
                            active = uiState.settings.spontaneousFlirtEnabled,
                            onClick = { viewModel.toggleSpontaneousFlirt(!uiState.settings.spontaneousFlirtEnabled) },
                            testTag = "auto_flirt_chip"
                        )
                        HudChipButton(
                            label = if (uiState.handsFreeEnabled) "HANDS-FREE: ON" else "HANDS-FREE: OFF",
                            active = uiState.handsFreeEnabled,
                            onClick = { viewModel.toggleHandsFree() },
                            testTag = "handsfree_toggle_chip"
                        )
                        val langBadge = when (uiState.settings.sttLang) {
                            "pa-IN" -> "LANG: PUNJABI"
                            "en-IN" -> "LANG: EN-IN"
                            "en-US" -> "LANG: EN-US"
                            else -> "LANG: HI+EN"
                        }
                        HudChipButton(
                            label = langBadge,
                            active = true,
                            onClick = { viewModel.cycleLanguage() },
                            testTag = "lang_cycle_chip"
                        )
                        HudChipButton(
                            label = if (uiState.cameraHudEnabled) "AR HUD: LIVE" else "AR HUD: OFF",
                            active = uiState.cameraHudEnabled,
                            onClick = {
                                if (uiState.cameraHudEnabled) {
                                    viewModel.setCameraHudEnabled(false)
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.setCameraHudEnabled(true)
                                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            testTag = "ar_hud_chip"
                        )
                        HudChipButton(
                            label = if (uiState.desktopOpen) "MAIM OS: ON" else "MAIM OS",
                            active = uiState.desktopOpen,
                            onClick = {
                                if (uiState.desktopOpen) viewModel.closeMaimDesktop()
                                else viewModel.openMaimDesktop()
                            },
                            testTag = "maim_os_chip"
                        )
                        HudChipButton(
                            label = "CODE",
                            active = uiState.activeDrawer == ActiveDrawer.CODING,
                            onClick = { viewModel.openDrawer(ActiveDrawer.CODING) },
                            testTag = "open_code_chip"
                        )
                        HudChipButton(
                            label = "TOOLS",
                            active = uiState.activeDrawer == ActiveDrawer.UTILITIES,
                            onClick = { viewModel.openDrawer(ActiveDrawer.UTILITIES) },
                            testTag = "open_utils_chip"
                        )
                        HudChipButton(
                            label = "GITHUB",
                            active = uiState.activeDrawer == ActiveDrawer.GITHUB,
                            onClick = { viewModel.openDrawer(ActiveDrawer.GITHUB) },
                            testTag = "open_github_chip"
                        )
                        HudChipButton(
                            label = "SYS",
                            active = uiState.activeDrawer == ActiveDrawer.SETTINGS,
                            onClick = { viewModel.openDrawer(ActiveDrawer.SETTINGS) },
                            testTag = "open_settings_chip"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isMicBusy = uiState.voiceState == VoiceState.LISTENING || uiState.voiceState == VoiceState.SPEAKING
                        IconButton(
                            onClick = {
                                viewModel.touchActivity()
                                when (uiState.voiceState) {
                                    VoiceState.SPEAKING -> viewModel.voiceManager.stopSpeaking()
                                    VoiceState.LISTENING -> viewModel.voiceManager.stopListening()
                                    else -> {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) viewModel.voiceManager.startListening()
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = if (uiState.voiceState == VoiceState.LISTENING) JarvisColors.MatrixEmerald else JarvisColors.NeonCyan,
                                    shape = CircleShape
                                )
                                .background(
                                    if (isMicBusy) JarvisColors.EmeraldTint else JarvisColors.CyanTint,
                                    CircleShape
                                )
                                .testTag("voice_mic_button")
                        ) {
                            Icon(
                                imageVector = if (isMicBusy) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isMicBusy) "Stop Voice" else "Start Voice Input",
                                tint = if (uiState.voiceState == VoiceState.LISTENING) JarvisColors.MatrixEmerald else JarvisColors.NeonCyan
                            )
                        }

                        OutlinedTextField(
                            value = inputDraft,
                            onValueChange = {
                                viewModel.touchActivity()
                                inputDraft = it
                            },
                            placeholder = {
                                Text(
                                    if (uiState.mode == JarvisMode.CODING) "Ask MAIM to write code..." else "Bol yaar... or ask for code",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisColors.MutedCyan
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (inputDraft.isNotBlank()) {
                                    viewModel.sendUserMessage(inputDraft)
                                    inputDraft = ""
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisColors.NeonCyan,
                                unfocusedBorderColor = JarvisColors.CyanBorder,
                                focusedTextColor = JarvisColors.FrostWhite,
                                unfocusedTextColor = JarvisColors.FrostWhite,
                                cursorColor = JarvisColors.NeonCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_text_input")
                        )

                        IconButton(
                            onClick = {
                                if (inputDraft.isNotBlank()) {
                                    viewModel.sendUserMessage(inputDraft)
                                    inputDraft = ""
                                }
                            },
                            enabled = inputDraft.isNotBlank() && uiState.voiceState != VoiceState.THINKING,
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.dp, JarvisColors.NeonCyan, RoundedCornerShape(10.dp))
                                .background(JarvisColors.CyanTint, RoundedCornerShape(10.dp))
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                tint = JarvisColors.NeonCyan
                            )
                        }
                    }
                }
            }
        }

        // Layer 5: Drawers
        when (uiState.activeDrawer) {
            ActiveDrawer.CODING -> CodingDrawer(
                snippet = uiState.activeSnippet,
                onClose = { viewModel.openDrawer(ActiveDrawer.NONE) }
            )
            ActiveDrawer.UTILITIES -> UtilitiesDrawer(
                timers = uiState.timers,
                reminders = uiState.reminders,
                todos = uiState.todos,
                notes = uiState.notes,
                nowEpochMs = uiState.nowEpochMs,
                onAddTimer = viewModel::addTimerMinutes,
                onDeleteTimer = viewModel::deleteTimer,
                onAddReminder = viewModel::addReminder,
                onDeleteReminder = viewModel::deleteReminder,
                onAddTodo = viewModel::addTodo,
                onToggleTodo = viewModel::toggleTodo,
                onDeleteTodo = viewModel::deleteTodo,
                onAddNote = viewModel::addNote,
                onDeleteNote = viewModel::deleteNote,
                onClose = { viewModel.openDrawer(ActiveDrawer.NONE) }
            )
            ActiveDrawer.GITHUB -> GitHubDrawer(
                savedUsername = uiState.settings.githubUsername,
                savedToken = uiState.settings.githubToken,
                repos = uiState.githubRepos,
                files = uiState.githubFiles,
                selectedRepoFullName = uiState.selectedRepoFullName,
                statusMessage = uiState.githubStatus,
                isLoading = uiState.githubLoading,
                onLinkAndLoad = viewModel::loadGitHubRepos,
                onInspectFiles = viewModel::inspectGitHubRepoFiles,
                onClose = { viewModel.openDrawer(ActiveDrawer.NONE) }
            )
            ActiveDrawer.SETTINGS -> SettingsDrawer(
                ownerName = uiState.settings.ownerName,
                ownerEmail = uiState.settings.ownerEmail,
                sttLang = uiState.settings.sttLang,
                selectedVoiceName = uiState.settings.ttsVoiceName,
                availableVoices = uiState.availableVoices,
                biometricAvailable = uiState.biometricAvailable,
                biometricEnrolled = uiState.biometricEnrolled,
                reducedMotionOverride = uiState.settings.reducedMotionOverride,
                spontaneousFlirtEnabled = uiState.settings.spontaneousFlirtEnabled,
                phoneStatus = uiState.phoneStatus,
                statusMessage = uiState.settingsNotice,
                onSaveOwnerProfile = viewModel::updateOwnerProfile,
                onSelectSttLang = viewModel::updateSttLang,
                onSelectTtsVoice = viewModel::updateTtsVoice,
                onTestVoice = viewModel::testVoice,
                onEnableBiometric = onBiometricEnrollClick,
                onToggleReducedMotion = viewModel::toggleReducedMotion,
                onToggleSpontaneousFlirt = viewModel::toggleSpontaneousFlirt,
                onGrantPhonePermissions = {
                    phoneAccessLauncher.launch(viewModel.phoneHelper.requiredRuntimePermissions())
                },
                onOpenAppPermissions = viewModel::openAppPermissionsSettings,
                onToggleFlashlight = { viewModel.setFlashlight(null) },
                onOpenDialer = viewModel::openDialer,
                onOpenWifi = viewModel::openWifiSettings,
                onOpenBluetooth = viewModel::openBluetoothSettings,
                onPanicWipe = { viewModel.requestPanicWipeModal(true) },
                onClose = { viewModel.openDrawer(ActiveDrawer.NONE) }
            )
            ActiveDrawer.NONE -> Unit
        }

        // Layer 5.5: MAIM's Computer Windows Overlay (opened via fingertip tap in the void)
        if (uiState.isUnlocked) {
            MaimDesktopOverlay(
                visible = uiState.desktopOpen,
                uiState = uiState,
                windows = uiState.desktopWindows,
                onOpenOrFocusApp = viewModel::openOrFocusDesktopApp,
                onFocusWindow = viewModel::focusDesktopWindow,
                onDragWindow = viewModel::dragDesktopWindow,
                onToggleMinimize = viewModel::toggleMinimizeDesktopWindow,
                onToggleMaximize = viewModel::toggleMaximizeDesktopWindow,
                onCloseWindow = viewModel::closeDesktopWindow,
                onRunShellCommand = viewModel::runDesktopShellCommand,
                onSendToMaim = viewModel::sendUserMessage,
                onAddNote = viewModel::addNote,
                onAddTodo = viewModel::addTodo,
                onToggleTodo = viewModel::toggleTodo,
                onOpenSnippet = { snippet ->
                    viewModel.closeMaimDesktop()
                    viewModel.openSnippet(snippet)
                },
                onToggleFlashlight = { viewModel.setFlashlight(null) },
                onOpenDialer = viewModel::openDialer,
                onOpenWifi = viewModel::openWifiSettings,
                onOpenBluetooth = viewModel::openBluetoothSettings,
                onToggleArHud = {
                    if (uiState.cameraHudEnabled) {
                        viewModel.setCameraHudEnabled(false)
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.setCameraHudEnabled(true)
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onCloseDesktop = viewModel::closeMaimDesktop
            )
        }

        // Layer 6: Boot sequence & Panic wipe modal
        if (uiState.showBootOverlay) {
            BootSequenceOverlay(
                lines = uiState.bootLines,
                progress = uiState.bootProgress
            )
        }

        if (uiState.showPanicConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.requestPanicWipeModal(false) },
                containerColor = JarvisColors.DeepHudSurface,
                title = {
                    Text(
                        "CONFIRM PANIC WIPE",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisColors.DangerCrimson
                    )
                },
                text = {
                    Text(
                        "This permanently erases your PIN vault, encrypted notes, to-dos, reminders, GitHub PAT, chat history, and hardware biometric binding from this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisColors.FrostWhite
                    )
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = { viewModel.confirmPanicWipe() },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = JarvisColors.DangerCrimson),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_panic_wipe_button")
                    ) {
                        Text("WIPE EVERYTHING", style = MaterialTheme.typography.labelLarge, color = JarvisColors.FrostWhite)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.requestPanicWipeModal(false) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("CANCEL", style = MaterialTheme.typography.labelLarge, color = JarvisColors.NeonCyan)
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    ownerLabel: String,
    onViewCode: (com.example.model.CodeSnippet) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (msg.isUser) JarvisColors.CyanTint else JarvisColors.EmeraldTint)
                .border(
                    1.dp,
                    if (msg.isUser) JarvisColors.CyanBorder else JarvisColors.MatrixEmerald.copy(alpha = 0.5f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (msg.isUser) ownerLabel else "MAIM",
                style = MaterialTheme.typography.labelSmall,
                color = if (msg.isUser) JarvisColors.NeonCyan else JarvisColors.MatrixEmerald
            )
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisColors.FrostWhite
            )
            msg.codeSnippet?.let { snippet ->
                OutlinedButton(
                    onClick = { onViewCode(snippet) },
                    border = BorderStroke(1.dp, JarvisColors.NeonCyan),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = JarvisColors.NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VIEW ${snippet.language.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisColors.NeonCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun HudChipButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, if (active) JarvisColors.NeonCyan else JarvisColors.CyanBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) JarvisColors.CyanTint else JarvisColors.VoidBlack.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) JarvisColors.FrostWhite else JarvisColors.MutedCyan
        )
    }
}
