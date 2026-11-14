package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.hud.GlitchTitleText
import com.example.ui.hud.hudGlassPanel
import com.example.ui.theme.JarvisColors

@Composable
fun LockScreen(
    subtitle: String,
    pinLength: Int,
    errorMessage: String,
    biometricEnabled: Boolean,
    isBusy: Boolean,
    reducedMotion: Boolean,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: () -> Unit,
    onPanicWipeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack.copy(alpha = 0.72f))
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .hudGlassPanel(strong = true)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(JarvisColors.NeonCyan)
                )
                Text(
                    text = "AES-256-GCM ZERO-TRUST VAULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisColors.NeonCyan
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            GlitchTitleText(
                text = "MAIM",
                style = MaterialTheme.typography.displaySmall,
                reducedMotion = reducedMotion
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "IDENTITY PROTECTED • PRIVATE ACCESS",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisColors.MatrixEmerald,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("lock_privacy_badge")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = JarvisColors.FrostWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(6) { index ->
                    val filled = index < pinLength
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(if (filled) JarvisColors.MatrixEmerald else JarvisColors.VoidBlack.copy(alpha = 0.4f))
                            .border(
                                width = 1.5.dp,
                                color = if (filled) JarvisColors.MatrixEmerald else JarvisColors.NeonCyan,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = errorMessage.ifBlank { " " },
                style = MaterialTheme.typography.bodySmall,
                color = JarvisColors.DangerCrimson,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("lock_error_text")
            )

            Spacer(modifier = Modifier.height(12.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "DEL")
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { label ->
                            val enabled = when (label) {
                                "BIO" -> biometricEnabled && !isBusy
                                else -> !isBusy
                            }
                            KeypadCell(
                                label = label,
                                enabled = enabled,
                                onClick = {
                                    when (label) {
                                        "BIO" -> onBiometricClick()
                                        "DEL" -> onBackspaceClick()
                                        else -> onDigitClick(label.first())
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("keypad_$label")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onPanicWipeClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = JarvisColors.DangerCrimson,
                    containerColor = JarvisColors.DangerTint
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisColors.DangerCrimson.copy(alpha = 0.7f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lock_panic_wipe_button")
            ) {
                Text("PANIC WIPE", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun KeypadCell(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAction = label == "BIO" || label == "DEL"
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(
                if (enabled) JarvisColors.CyanTint else JarvisColors.VoidBlack.copy(alpha = 0.25f)
            )
            .border(
                width = 1.dp,
                color = if (enabled) JarvisColors.CyanBorder else JarvisColors.CyanBorder.copy(alpha = 0.25f),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (isAction) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
            color = when {
                !enabled -> JarvisColors.MutedCyan.copy(alpha = 0.35f)
                isAction -> JarvisColors.NeonCyan
                else -> JarvisColors.FrostWhite
            }
        )
    }
}
