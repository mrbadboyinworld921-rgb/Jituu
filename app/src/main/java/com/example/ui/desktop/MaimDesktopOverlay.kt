package com.example.ui.desktop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.model.CodeSnippet
import com.example.model.DesktopAppId
import com.example.model.DesktopWindowState
import com.example.ui.hud.hudGlassPanel
import com.example.ui.theme.JarvisColors
import com.example.viewmodel.JarvisUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaimDesktopOverlay(
    visible: Boolean,
    uiState: JarvisUiState,
    windows: List<DesktopWindowState>,
    onOpenOrFocusApp: (DesktopAppId) -> Unit,
    onFocusWindow: (DesktopAppId) -> Unit,
    onDragWindow: (DesktopAppId, Float, Float) -> Unit,
    onToggleMinimize: (DesktopAppId) -> Unit,
    onToggleMaximize: (DesktopAppId) -> Unit,
    onCloseWindow: (DesktopAppId) -> Unit,
    onRunShellCommand: (String) -> List<String>,
    onSendToMaim: (String) -> Unit,
    onAddNote: (String) -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onOpenSnippet: (CodeSnippet) -> Unit,
    onToggleFlashlight: () -> Unit,
    onOpenDialer: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onToggleArHud: () -> Unit,
    onCloseDesktop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startMenuOpen by remember { mutableStateOf(false) }

    if (visible) {
        BackHandler {
            if (startMenuOpen) startMenuOpen = false else onCloseDesktop()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(
            initialScale = 0.25f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ),
        exit = fadeOut() + scaleOut(targetScale = 0.25f)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(JarvisColors.VoidBlack.copy(alpha = 0.34f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("maim_desktop_overlay")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Workspace canvas area containing desktop icons + draggable floating windows
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    val maxWidthPx = constraints.maxWidth.toFloat()
                    val maxHeightPx = constraints.maxHeight.toFloat()

                    // Desktop icon grid floating in the void
                    FlowRow(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DesktopAppId.entries.forEach { app ->
                            Column(
                                modifier = Modifier
                                    .size(width = 78.dp, height = 78.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(JarvisColors.DeepHudSurface.copy(alpha = 0.72f))
                                    .border(1.dp, JarvisColors.CyanBorder, RoundedCornerShape(10.dp))
                                    .clickable { onOpenOrFocusApp(app) }
                                    .padding(6.dp)
                                    .testTag("desktop_icon_${app.name.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(app.badge, style = MaterialTheme.typography.titleMedium, color = JarvisColors.MatrixEmerald)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    app.title.substringBefore('.'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisColors.FrostWhite,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Active non-minimized draggable windows sorted by zOrder
                    windows.filter { !it.isMinimized }.sortedBy { it.zOrder }.forEach { win ->
                        val winWidth = if (win.isMaximized) maxWidth else minOf(maxWidth - 16.dp, 340.dp)
                        val winHeight = if (win.isMaximized) maxHeight else minOf(maxHeight - 16.dp, 360.dp)
                        val xPx = if (win.isMaximized) 0 else win.offsetX.coerceIn(0f, (maxWidthPx - 220f).coerceAtLeast(0f)).roundToInt()
                        val yPx = if (win.isMaximized) 0 else win.offsetY.coerceIn(0f, (maxHeightPx - 180f).coerceAtLeast(0f)).roundToInt()

                        Column(
                            modifier = Modifier
                                .offset { IntOffset(xPx, yPx) }
                                .size(width = winWidth, height = winHeight)
                                .zIndex(win.zOrder.toFloat())
                                .hudGlassPanel(strong = true)
                                .clickable { onFocusWindow(win.appId) }
                                .testTag("desktop_window_${win.appId.name.lowercase()}")
                        ) {
                            // Draggable Window Title Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(JarvisColors.CyanTint)
                                    .border(0.5.dp, JarvisColors.CyanBorder)
                                    .pointerInput(win.appId, win.isMaximized) {
                                        if (!win.isMaximized) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                onFocusWindow(win.appId)
                                                onDragWindow(win.appId, dragAmount.x, dragAmount.y)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(JarvisColors.MatrixEmerald)
                                    )
                                    Text(
                                        text = win.appId.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = JarvisColors.NeonCyan
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    WindowControlButton("_", JarvisColors.FrostWhite) { onToggleMinimize(win.appId) }
                                    WindowControlButton("□", JarvisColors.NeonCyan) { onToggleMaximize(win.appId) }
                                    WindowControlButton("×", JarvisColors.DangerCrimson) { onCloseWindow(win.appId) }
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (win.appId) {
                                    DesktopAppId.TERMINAL -> TerminalAppContent(
                                        uiState = uiState,
                                        onRunShellAction = onRunShellCommand,
                                        onSendToMaim = onSendToMaim
                                    )
                                    DesktopAppId.FILES -> VaultFilesAppContent(
                                        uiState = uiState,
                                        onAddNote = onAddNote,
                                        onAddTodo = onAddTodo,
                                        onToggleTodo = onToggleTodo,
                                        onOpenSnippet = onOpenSnippet
                                    )
                                    DesktopAppId.SYSTEM -> SystemPhoneAppContent(
                                        uiState = uiState,
                                        onToggleFlashlight = onToggleFlashlight,
                                        onOpenDialer = onOpenDialer,
                                        onOpenWifi = onOpenWifi,
                                        onOpenBluetooth = onOpenBluetooth,
                                        onToggleArHud = onToggleArHud
                                    )
                                    DesktopAppId.CODE -> CodeStudioMiniAppContent(
                                        snippet = uiState.activeSnippet,
                                        onOpenFullCode = onOpenSnippet
                                    )
                                    DesktopAppId.CALCULATOR -> CalculatorAppContent()
                                }
                            }
                        }
                    }

                    // Start Menu Popup
                    if (startMenuOpen) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .width(230.dp)
                                .zIndex(999f)
                                .hudGlassPanel(strong = true)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("MAIM OS // PROGRAMS", style = MaterialTheme.typography.labelSmall, color = JarvisColors.MatrixEmerald)
                            DesktopAppId.entries.forEach { app ->
                                OutlinedButton(
                                    onClick = {
                                        onOpenOrFocusApp(app)
                                        startMenuOpen = false
                                    },
                                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                ) {
                                    Text("${app.badge}  ${app.title}", style = MaterialTheme.typography.bodySmall, color = JarvisColors.FrostWhite)
                                }
                            }
                        }
                    }
                }

                // MAIM OS Bottom Taskbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hudGlassPanel(strong = true)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { startMenuOpen = !startMenuOpen },
                        border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("desktop_start_button")
                    ) {
                        Text("MAIM", style = MaterialTheme.typography.labelLarge, color = JarvisColors.MatrixEmerald)
                    }

                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        windows.forEach { win ->
                            val isActive = !win.isMinimized
                            OutlinedButton(
                                onClick = {
                                    if (win.isMinimized) onToggleMinimize(win.appId)
                                    onFocusWindow(win.appId)
                                },
                                border = BorderStroke(1.dp, if (isActive) JarvisColors.NeonCyan else JarvisColors.CyanBorder),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    win.appId.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive) JarvisColors.NeonCyan else JarvisColors.MutedCyan
                                )
                            }
                        }
                    }

                    Text(
                        uiState.clockText,
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisColors.MatrixEmerald
                    )
                    OutlinedButton(
                        onClick = onCloseDesktop,
                        border = BorderStroke(1.dp, JarvisColors.DangerCrimson),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("close_desktop_button")
                    ) {
                        Text("EXIT", style = MaterialTheme.typography.labelSmall, color = JarvisColors.DangerCrimson)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowControlButton(
    symbol: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(JarvisColors.VoidBlack.copy(alpha = 0.55f))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
