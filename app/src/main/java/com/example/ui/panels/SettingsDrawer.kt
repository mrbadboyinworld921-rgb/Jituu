package com.example.ui.panels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.device.PhoneStatusSnapshot
import com.example.model.TtsVoiceOption
import com.example.ui.hud.hudGlassPanel
import com.example.ui.theme.JarvisColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDrawer(
    ownerName: String,
    ownerEmail: String,
    sttLang: String,
    selectedVoiceName: String,
    availableVoices: List<TtsVoiceOption>,
    biometricAvailable: Boolean,
    biometricEnrolled: Boolean,
    reducedMotionOverride: Boolean,
    spontaneousFlirtEnabled: Boolean,
    phoneStatus: PhoneStatusSnapshot,
    statusMessage: String,
    onSaveOwnerProfile: (name: String, email: String) -> Unit,
    onSelectSttLang: (String) -> Unit,
    onSelectTtsVoice: (String) -> Unit,
    onTestVoice: () -> Unit,
    onEnableBiometric: () -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onToggleSpontaneousFlirt: (Boolean) -> Unit,
    onGrantPhonePermissions: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onOpenDialer: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onPanicWipe: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showVoiceDropdown by remember { mutableStateOf(false) }
    val resolvedOwnerName = ownerName.ifBlank { "Jitendra Maurya" }
    val resolvedOwnerEmail = ownerEmail.ifBlank { "jitendermourya921@gmail.com" }
    var draftOwnerName by remember(resolvedOwnerName) { mutableStateOf(resolvedOwnerName) }
    var draftOwnerEmail by remember(resolvedOwnerEmail) { mutableStateOf(resolvedOwnerEmail) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = JarvisColors.NeonCyan,
        unfocusedBorderColor = JarvisColors.CyanBorder,
        focusedTextColor = JarvisColors.FrostWhite,
        unfocusedTextColor = JarvisColors.FrostWhite,
        cursorColor = JarvisColors.NeonCyan
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack.copy(alpha = 0.55f))
            .navigationBarsPadding()
            .padding(10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .heightIn(max = 560.dp)
                .hudGlassPanel(strong = true)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NEURAL SYSTEM & VOICE", style = MaterialTheme.typography.titleMedium, color = JarvisColors.NeonCyan)
                OutlinedButton(
                    onClick = onClose,
                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("close_settings_drawer")
                ) {
                    Text("CLOSE", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(JarvisColors.CyanTint)
                    .border(1.dp, JarvisColors.CyanBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .testTag("profile_about_section"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "PROFILE & ABOUT",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisColors.MatrixEmerald
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Owner",
                        tint = JarvisColors.NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Owner: $resolvedOwnerName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisColors.FrostWhite,
                        modifier = Modifier.testTag("settings_owner_display")
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email",
                        tint = JarvisColors.NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Email: $resolvedOwnerEmail",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisColors.FrostWhite,
                        modifier = Modifier.testTag("settings_email_display")
                    )
                }
                Text(
                    text = "MAIM Holographic Neural HUD • Private AES-256-GCM Encrypted Local Vault",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisColors.MutedCyan
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Edit Owner Profile (AES-GCM Encrypted)", style = MaterialTheme.typography.bodySmall, color = JarvisColors.MatrixEmerald)
                OutlinedTextField(
                    value = draftOwnerName,
                    onValueChange = { draftOwnerName = it },
                    label = { Text("Owner Name", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("owner_name_input")
                )
                OutlinedTextField(
                    value = draftOwnerEmail,
                    onValueChange = { draftOwnerEmail = it },
                    label = { Text("Owner Email", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("owner_email_input")
                )
                OutlinedButton(
                    onClick = { onSaveOwnerProfile(draftOwnerName.trim(), draftOwnerEmail.trim()) },
                    border = BorderStroke(1.dp, JarvisColors.NeonCyan),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("save_owner_button")
                ) {
                    Text("SAVE OWNER PROFILE", style = MaterialTheme.typography.labelLarge, color = JarvisColors.NeonCyan)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Conversation & Voice Language", style = MaterialTheme.typography.bodySmall, color = JarvisColors.MutedCyan)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "hi-IN" to "Hindi-English Mix",
                        "pa-IN" to "Punjabi (ਪੰਜਾਬੀ)",
                        "en-IN" to "English (IN)",
                        "en-US" to "English (US)"
                    ).forEach { (tag, label) ->
                        val active = sttLang == tag
                        OutlinedButton(
                            onClick = { onSelectSttLang(tag) },
                            border = BorderStroke(1.dp, if (active) JarvisColors.NeonCyan else JarvisColors.CyanBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) JarvisColors.CyanTint else JarvisColors.VoidBlack.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("stt_lang_$tag")
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = JarvisColors.FrostWhite)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Voice Picker (TTS)", style = MaterialTheme.typography.bodySmall, color = JarvisColors.MutedCyan)
                val currentLabel = availableVoices.firstOrNull { it.name == selectedVoiceName }?.displayLabel
                    ?: availableVoices.firstOrNull()?.displayLabel
                    ?: "System Default Voice"
                Box {
                    OutlinedButton(
                        onClick = { showVoiceDropdown = true },
                        border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("voice_picker_button")
                    ) {
                        Text(currentLabel, style = MaterialTheme.typography.bodySmall, color = JarvisColors.FrostWhite)
                    }
                    DropdownMenu(
                        expanded = showVoiceDropdown,
                        onDismissRequest = { showVoiceDropdown = false }
                    ) {
                        availableVoices.take(25).forEach { voice ->
                            DropdownMenuItem(
                                text = { Text(voice.displayLabel, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    onSelectTtsVoice(voice.name)
                                    showVoiceDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTestVoice,
                    border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("test_voice_button")
                ) {
                    Text("TEST VOICE", style = MaterialTheme.typography.labelLarge, color = JarvisColors.MatrixEmerald)
                }

                if (biometricAvailable) {
                    OutlinedButton(
                        onClick = onEnableBiometric,
                        border = BorderStroke(1.dp, JarvisColors.NeonCyan),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("enable_biometric_button")
                    ) {
                        Text(
                            if (biometricEnrolled) "REBIND BIOMETRIC" else "ENABLE BIOMETRIC",
                            style = MaterialTheme.typography.labelLarge,
                            color = JarvisColors.NeonCyan
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(JarvisColors.EmeraldTint)
                    .border(1.dp, JarvisColors.MatrixEmerald.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .testTag("phone_access_section"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PHONE ACCESS & DEVICE CONTROLS",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisColors.MatrixEmerald
                )
                val chargeTag = if (phoneStatus.isCharging) "Charging" else "On Battery"
                Text(
                    text = "Battery: ${phoneStatus.batteryPercent}% ($chargeTag) • Mic: ${if (phoneStatus.micGranted) "ON" else "OFF"} • Cam: ${if (phoneStatus.cameraGranted) "ON" else "OFF"} • Alerts: ${if (phoneStatus.notificationsGranted) "ON" else "OFF"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisColors.FrostWhite
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onGrantPhonePermissions,
                        border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("grant_phone_access_button")
                    ) {
                        Text("GRANT ACCESS", style = MaterialTheme.typography.labelLarge, color = JarvisColors.MatrixEmerald)
                    }
                    OutlinedButton(
                        onClick = onOpenAppPermissions,
                        border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("open_app_permissions_button")
                    ) {
                        Text("SYSTEM PERMS", style = MaterialTheme.typography.labelLarge, color = JarvisColors.FrostWhite)
                    }
                    OutlinedButton(
                        onClick = onToggleFlashlight,
                        border = BorderStroke(1.dp, if (phoneStatus.flashlightOn) JarvisColors.MatrixEmerald else JarvisColors.CyanBorder),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_torch_button")
                    ) {
                        Text(
                            if (phoneStatus.flashlightOn) "TORCH: ON" else "TORCH: OFF",
                            style = MaterialTheme.typography.labelLarge,
                            color = JarvisColors.NeonCyan
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenDialer,
                        border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("open_dialer_button")
                    ) {
                        Text("DIALER", style = MaterialTheme.typography.labelLarge, color = JarvisColors.FrostWhite)
                    }
                    OutlinedButton(
                        onClick = onOpenWifi,
                        border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("open_wifi_button")
                    ) {
                        Text("WI-FI", style = MaterialTheme.typography.labelLarge, color = JarvisColors.FrostWhite)
                    }
                    OutlinedButton(
                        onClick = onOpenBluetooth,
                        border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("open_bluetooth_button")
                    ) {
                        Text("BLUETOOTH", style = MaterialTheme.typography.labelLarge, color = JarvisColors.FrostWhite)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Spontaneous Flirty Talk", style = MaterialTheme.typography.bodyMedium, color = JarvisColors.FrostWhite)
                    Text(
                        "MAIM randomly starts flirty conversations with you unprompted",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisColors.MutedCyan
                    )
                }
                Switch(
                    checked = spontaneousFlirtEnabled,
                    onCheckedChange = onToggleSpontaneousFlirt,
                    modifier = Modifier.testTag("spontaneous_flirt_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reduce HUD Motion", style = MaterialTheme.typography.bodyMedium, color = JarvisColors.FrostWhite)
                Switch(
                    checked = reducedMotionOverride,
                    onCheckedChange = onToggleReducedMotion,
                    modifier = Modifier.testTag("reduced_motion_switch")
                )
            }

            if (statusMessage.isNotBlank()) {
                Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = JarvisColors.MatrixEmerald)
            }

            OutlinedButton(
                onClick = onPanicWipe,
                border = BorderStroke(1.dp, JarvisColors.DangerCrimson),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = JarvisColors.DangerTint),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_panic_wipe_button")
            ) {
                Text("PANIC WIPE EVERYTHING", style = MaterialTheme.typography.labelLarge, color = JarvisColors.DangerCrimson)
            }

            Text(
                text = "AR Camera & Microphone stay strictly on-device. Notes, To-Dos, Reminders, GitHub PAT, and Chat History are AES-256-GCM encrypted from your PIN. Telemetry metrics are simulated.",
                style = MaterialTheme.typography.bodySmall,
                color = JarvisColors.MutedCyan
            )
        }
    }
}
