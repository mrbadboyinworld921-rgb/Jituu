package com.example.ui.desktop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.model.CodeSnippet
import com.example.ui.theme.JarvisColors
import com.example.viewmodel.JarvisUiState
import kotlin.math.roundToInt

@Composable
fun TerminalAppContent(
    uiState: JarvisUiState,
    onRunShellAction: (cmd: String) -> List<String>,
    onSendToMaim: (String) -> Unit
) {
    val lines = remember {
        mutableStateListOf(
            "MAIM-OS v4.2 // Holographic Neural Shell",
            "Encrypted AES-256-GCM Vault Mounted.",
            "Type 'help' for commands or 'ask <msg>' to talk to MAIM."
        )
    }
    var cmdInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    fun executeCommand(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        lines.add("$ $trimmed")
        val lower = trimmed.lowercase()
        when {
            lower == "clear" -> lines.clear()
            lower.startsWith("ask ") -> {
                val prompt = trimmed.drop(4).trim()
                if (prompt.isNotBlank()) {
                    onSendToMaim(prompt)
                    lines.add("[MAIM]: Processing via live neural link...")
                }
            }
            else -> lines.addAll(onRunShellAction(trimmed))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(JarvisColors.VoidBlack.copy(alpha = 0.78f))
                .border(1.dp, JarvisColors.CyanBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.startsWith("$")) JarvisColors.NeonCyan else JarvisColors.MatrixEmerald
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("help", "battery", "ls", "torch on", "torch off", "clear").forEach { quick ->
                OutlinedButton(
                    onClick = { executeCommand(quick) },
                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(quick, style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = cmdInput,
                onValueChange = { cmdInput = it },
                placeholder = { Text("Enter shell command...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    executeCommand(cmdInput)
                    cmdInput = ""
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisColors.NeonCyan,
                    unfocusedBorderColor = JarvisColors.CyanBorder,
                    focusedTextColor = JarvisColors.FrostWhite,
                    unfocusedTextColor = JarvisColors.FrostWhite
                ),
                modifier = Modifier.weight(1f).testTag("desktop_terminal_input")
            )
            OutlinedButton(
                onClick = {
                    executeCommand(cmdInput)
                    cmdInput = ""
                },
                border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("desktop_terminal_run")
            ) {
                Text("RUN", style = MaterialTheme.typography.labelLarge, color = JarvisColors.MatrixEmerald)
            }
        }
    }
}

@Composable
fun VaultFilesAppContent(
    uiState: JarvisUiState,
    onAddNote: (String) -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onOpenSnippet: (CodeSnippet) -> Unit
) {
    var quickNote by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "/vault/encrypted (AES-256-GCM)",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisColors.MatrixEmerald
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = quickNote,
                onValueChange = { quickNote = it },
                placeholder = { Text("New encrypted file/note...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisColors.NeonCyan,
                    unfocusedBorderColor = JarvisColors.CyanBorder,
                    focusedTextColor = JarvisColors.FrostWhite,
                    unfocusedTextColor = JarvisColors.FrostWhite
                ),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    if (quickNote.isNotBlank()) {
                        onAddNote(quickNote.trim())
                        quickNote = ""
                    }
                },
                border = BorderStroke(1.dp, JarvisColors.NeonCyan),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("SAVE", style = MaterialTheme.typography.labelSmall, color = JarvisColors.NeonCyan)
            }
        }

        uiState.activeSnippet?.let { snippet ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisColors.CyanTint)
                    .border(1.dp, JarvisColors.NeonCyan, RoundedCornerShape(8.dp))
                    .clickable { onOpenSnippet(snippet) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "snippet.${snippet.language} (${snippet.code.length} chars)",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisColors.FrostWhite
                )
                Text("OPEN", style = MaterialTheme.typography.labelSmall, color = JarvisColors.MatrixEmerald)
            }
        }

        Text("NOTES (${uiState.notes.size})", style = MaterialTheme.typography.labelSmall, color = JarvisColors.NeonCyan)
        if (uiState.notes.isEmpty()) {
            Text("No notes yet.", style = MaterialTheme.typography.bodySmall, color = JarvisColors.MutedCyan)
        } else {
            uiState.notes.take(8).forEach { note ->
                Text(
                    text = "• [${note.createdAt}] ${note.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisColors.FrostWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(JarvisColors.VoidBlack.copy(alpha = 0.4f))
                        .padding(6.dp)
                )
            }
        }

        Text("TO-DOS (${uiState.todos.size})", style = MaterialTheme.typography.labelSmall, color = JarvisColors.NeonCyan)
        if (uiState.todos.isEmpty()) {
            OutlinedButton(
                onClick = { onAddTodo("Review MAIM Workspace") },
                border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("+ SAMPLE TASK", style = MaterialTheme.typography.labelSmall, color = JarvisColors.MutedCyan)
            }
        } else {
            uiState.todos.take(8).forEach { todo ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = todo.done, onCheckedChange = { onToggleTodo(todo.id) })
                    Text(
                        text = todo.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (todo.done) JarvisColors.MutedCyan else JarvisColors.FrostWhite
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemPhoneAppContent(
    uiState: JarvisUiState,
    onToggleFlashlight: () -> Unit,
    onOpenDialer: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onToggleArHud: () -> Unit
) {
    val ps = uiState.phoneStatus
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "REAL DEVICE TELEMETRY",
            style = MaterialTheme.typography.labelLarge,
            color = JarvisColors.MatrixEmerald
        )
        Text(
            "Battery: ${ps.batteryPercent}% (${if (ps.isCharging) "Charging" else "Discharging"})",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisColors.FrostWhite
        )
        LinearProgressIndicator(
            progress = { (ps.batteryPercent / 100f).coerceIn(0f, 1f) },
            color = if (ps.batteryPercent <= 20) JarvisColors.DangerCrimson else JarvisColors.MatrixEmerald,
            trackColor = JarvisColors.VoidBlack,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Text(
            "Mic: ${if (ps.micGranted) "GRANTED" else "DENIED"} • Cam: ${if (ps.cameraGranted) "GRANTED" else "DENIED"} • Alerts: ${if (ps.notificationsGranted) "GRANTED" else "DENIED"}",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisColors.MutedCyan
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onToggleFlashlight,
                border = BorderStroke(1.dp, if (ps.flashlightOn) JarvisColors.MatrixEmerald else JarvisColors.CyanBorder),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(if (ps.flashlightOn) "TORCH: ON" else "TORCH: OFF", style = MaterialTheme.typography.labelSmall, color = JarvisColors.NeonCyan)
            }
            OutlinedButton(
                onClick = onToggleArHud,
                border = BorderStroke(1.dp, if (uiState.cameraHudEnabled) JarvisColors.MatrixEmerald else JarvisColors.CyanBorder),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(if (uiState.cameraHudEnabled) "AR CAM: ON" else "AR CAM: OFF", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
            }
            OutlinedButton(onClick = onOpenDialer, border = BorderStroke(1.dp, JarvisColors.CyanBorder), modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("DIALER", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
            }
            OutlinedButton(onClick = onOpenWifi, border = BorderStroke(1.dp, JarvisColors.CyanBorder), modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("WI-FI", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
            }
            OutlinedButton(onClick = onOpenBluetooth, border = BorderStroke(1.dp, JarvisColors.CyanBorder), modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("BLUETOOTH", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
            }
        }
    }
}

@Composable
fun CodeStudioMiniAppContent(
    snippet: CodeSnippet?,
    onOpenFullCode: (CodeSnippet) -> Unit
) {
    val context = LocalContext.current
    val active = snippet ?: CodeSnippet(
        language = "html",
        code = "<!DOCTYPE html><html><body style='background:#03080d;color:#00f3ff;font-family:monospace;padding:24px'><h2>MAIM Holographic OS</h2><p>Ready to build.</p></body></html>"
    )
    var editedCode by remember(active.code) { mutableStateOf(active.code) }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(active.language.uppercase(), style = MaterialTheme.typography.labelSmall, color = JarvisColors.MatrixEmerald)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("MAIM Code", editedCode))
                    },
                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("COPY", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
                }
                OutlinedButton(
                    onClick = { onOpenFullCode(active.copy(code = editedCode)) },
                    border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("RUN / FULL", style = MaterialTheme.typography.labelSmall, color = JarvisColors.MatrixEmerald)
                }
            }
        }
        OutlinedTextField(
            value = editedCode,
            onValueChange = { editedCode = it },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.NeonCyan,
                unfocusedBorderColor = JarvisColors.CyanBorder,
                focusedTextColor = JarvisColors.FrostWhite,
                unfocusedTextColor = JarvisColors.FrostWhite
            ),
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@Composable
fun CalculatorAppContent() {
    var display by remember { mutableStateOf("0") }
    var firstOperand by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }

    fun pressDigit(d: String) {
        if (resetOnNextDigit || display == "0") {
            display = if (d == ".") "0." else d
            resetOnNextDigit = false
        } else if (d != "." || !display.contains('.')) {
            display += d
        }
    }

    fun evaluate() {
        val a = firstOperand ?: return
        val b = display.toDoubleOrNull() ?: return
        val result = when (pendingOp) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b == 0.0) Double.NaN else a / b
            else -> b
        }
        display = when {
            result.isNaN() -> "Err"
            result % 1.0 == 0.0 && kotlin.math.abs(result) < 1e12 -> result.roundToInt().toString()
            else -> "%.4f".format(result).trimEnd('0').trimEnd('.')
        }
        firstOperand = null
        pendingOp = null
        resetOnNextDigit = true
    }

    fun pressOp(op: String) {
        if (firstOperand != null && !resetOnNextDigit) evaluate()
        firstOperand = display.toDoubleOrNull()
        pendingOp = op
        resetOnNextDigit = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(JarvisColors.VoidBlack.copy(alpha = 0.85f))
                .border(1.dp, JarvisColors.NeonCyan, RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(display, style = MaterialTheme.typography.titleLarge, color = JarvisColors.MatrixEmerald)
        }

        val grid = listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("C", "0", ".", "+"),
            listOf("DEL", "=")
        )
        grid.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { key ->
                    val isOp = key in listOf("/", "*", "-", "+", "=")
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                "C" -> {
                                    display = "0"
                                    firstOperand = null
                                    pendingOp = null
                                    resetOnNextDigit = false
                                }
                                "DEL" -> display = if (display.length > 1) display.dropLast(1) else "0"
                                "=" -> evaluate()
                                "+", "-", "*", "/" -> pressOp(key)
                                else -> pressDigit(key)
                            }
                        },
                        border = BorderStroke(1.dp, if (isOp) JarvisColors.MatrixEmerald else JarvisColors.CyanBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (key == "=") JarvisColors.EmeraldTint else JarvisColors.CyanTint
                        ),
                        modifier = Modifier.weight(1f).fillMaxSize()
                    ) {
                        Text(key, style = MaterialTheme.typography.labelLarge, color = JarvisColors.FrostWhite)
                    }
                }
            }
        }
    }
}
