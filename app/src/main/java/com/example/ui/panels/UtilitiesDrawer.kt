package com.example.ui.panels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.model.NoteItem
import com.example.model.ReminderItem
import com.example.model.TimerItem
import com.example.model.TodoItem
import com.example.ui.hud.hudGlassPanel
import com.example.ui.theme.JarvisColors

@Composable
fun UtilitiesDrawer(
    timers: List<TimerItem>,
    reminders: List<ReminderItem>,
    todos: List<TodoItem>,
    notes: List<NoteItem>,
    nowEpochMs: Long,
    onAddTimer: (label: String, minutes: Int) -> Unit,
    onDeleteTimer: (String) -> Unit,
    onAddReminder: (text: String, hhmm: String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("TIMERS", "REMINDERS", "TO-DO", "NOTES")

    var timerLabel by remember { mutableStateOf("") }
    var timerMinutes by remember { mutableStateOf("5") }
    var reminderText by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf("09:00") }
    var todoText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ENCRYPTED UTILITIES", style = MaterialTheme.typography.titleMedium, color = JarvisColors.NeonCyan)
                OutlinedButton(
                    onClick = onClose,
                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("close_utils_drawer")
                ) {
                    Text("CLOSE", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEachIndexed { idx, title ->
                    val active = selectedTab == idx
                    OutlinedButton(
                        onClick = { selectedTab = idx },
                        border = BorderStroke(1.dp, if (active) JarvisColors.NeonCyan else JarvisColors.CyanBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (active) JarvisColors.CyanTint else JarvisColors.VoidBlack.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("util_tab_$title")
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) JarvisColors.FrostWhite else JarvisColors.MutedCyan
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = timerLabel,
                            onValueChange = { timerLabel = it },
                            placeholder = { Text("Label (Chai)", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = timerMinutes,
                            onValueChange = { timerMinutes = it.filter { ch -> ch.isDigit() }.take(4) },
                            placeholder = { Text("Min", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.width(74.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                val mins = timerMinutes.toIntOrNull()?.coerceIn(1, 1440) ?: 5
                                onAddTimer(timerLabel.ifBlank { "Timer" }, mins)
                                timerLabel = ""
                            },
                            border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("start_timer_button")
                        ) {
                            Text("GO", style = MaterialTheme.typography.labelLarge, color = JarvisColors.MatrixEmerald)
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (timers.isEmpty()) {
                            item { EmptyUtilText("No active timers. Say 'Set a 5 minute timer' or add one above.") }
                        }
                        items(timers, key = { it.id }) { timer ->
                            val remSec = ((timer.endsAtEpochMs - nowEpochMs) / 1000L).coerceAtLeast(0L)
                            val mm = (remSec / 60).toString().padStart(2, '0')
                            val ss = (remSec % 60).toString().padStart(2, '0')
                            UtilItemRow(
                                text = "${timer.label} — $mm:$ss",
                                done = false,
                                onDelete = { onDeleteTimer(timer.id) }
                            )
                        }
                    }
                }

                1 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = reminderText,
                            onValueChange = { reminderText = it },
                            placeholder = { Text("Remind me...", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reminderTime,
                            onValueChange = { reminderTime = it.take(5) },
                            placeholder = { Text("HH:MM", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.width(86.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                if (reminderText.isNotBlank()) {
                                    onAddReminder(reminderText.trim(), reminderTime.trim())
                                    reminderText = ""
                                }
                            },
                            border = BorderStroke(1.dp, JarvisColors.NeonCyan),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_reminder_button")
                        ) {
                            Text("ADD", style = MaterialTheme.typography.labelLarge, color = JarvisColors.NeonCyan)
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (reminders.isEmpty()) {
                            item { EmptyUtilText("No encrypted reminders yet.") }
                        }
                        items(reminders, key = { it.id }) { rem ->
                            UtilItemRow(
                                text = "[${rem.timeHHmm}] ${rem.text}",
                                done = rem.fired,
                                onDelete = { onDeleteReminder(rem.id) }
                            )
                        }
                    }
                }

                2 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = todoText,
                            onValueChange = { todoText = it },
                            placeholder = { Text("New task...", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                if (todoText.isNotBlank()) {
                                    onAddTodo(todoText.trim())
                                    todoText = ""
                                }
                            },
                            border = BorderStroke(1.dp, JarvisColors.NeonCyan),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_todo_button")
                        ) {
                            Text("ADD", style = MaterialTheme.typography.labelLarge, color = JarvisColors.NeonCyan)
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (todos.isEmpty()) {
                            item { EmptyUtilText("To-do list is clear.") }
                        }
                        items(todos, key = { it.id }) { todo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, JarvisColors.CyanBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .background(JarvisColors.CyanTint, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onToggleTodo(todo.id) },
                                    border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(
                                        if (todo.done) "DONE" else "TODO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisColors.MatrixEmerald
                                    )
                                }
                                Text(
                                    text = todo.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisColors.FrostWhite,
                                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { onDeleteTodo(todo.id) },
                                    border = BorderStroke(1.dp, JarvisColors.DangerCrimson.copy(alpha = 0.6f)),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("DEL", style = MaterialTheme.typography.labelSmall, color = JarvisColors.DangerCrimson)
                                }
                            }
                        }
                    }
                }

                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("Quick encrypted note...", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                if (noteText.isNotBlank()) {
                                    onAddNote(noteText.trim())
                                    noteText = ""
                                }
                            },
                            border = BorderStroke(1.dp, JarvisColors.NeonCyan),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_note_button")
                        ) {
                            Text("SAVE", style = MaterialTheme.typography.labelLarge, color = JarvisColors.NeonCyan)
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (notes.isEmpty()) {
                            item { EmptyUtilText("No encrypted notes saved yet.") }
                        }
                        items(notes, key = { it.id }) { note ->
                            UtilItemRow(
                                text = "${note.text} (${note.createdAt})",
                                done = false,
                                onDelete = { onDeleteNote(note.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyUtilText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = JarvisColors.MutedCyan,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun UtilItemRow(
    text: String,
    done: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JarvisColors.CyanBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .background(JarvisColors.CyanTint, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) JarvisColors.MutedCyan else JarvisColors.FrostWhite,
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onDelete,
            border = BorderStroke(1.dp, JarvisColors.DangerCrimson.copy(alpha = 0.6f)),
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Text("DEL", style = MaterialTheme.typography.labelSmall, color = JarvisColors.DangerCrimson)
        }
    }
}
