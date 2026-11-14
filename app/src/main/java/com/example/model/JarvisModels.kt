package com.example.model

enum class JarvisMode {
    FLIRT,
    FRIEND,
    CODING
}

enum class VoiceState {
    STANDBY,
    LISTENING,
    THINKING,
    SPEAKING,
    UNAVAILABLE
}

enum class ActiveDrawer {
    NONE,
    CODING,
    UTILITIES,
    GITHUB,
    SETTINGS
}

enum class DesktopAppId(val title: String, val badge: String) {
    TERMINAL("MAIM_Terminal.sh", ">_"),
    FILES("Vault_Explorer", "DIR"),
    SYSTEM("Phone_SysMon", "SYS"),
    CODE("Code_Studio", "{ }"),
    CALCULATOR("Holo_Calc", "=")
}

data class DesktopWindowState(
    val appId: DesktopAppId,
    val offsetX: Float = 16f,
    val offsetY: Float = 24f,
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val zOrder: Int = 1
)

data class CodeSnippet(
    val code: String,
    val language: String
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val codeSnippet: CodeSnippet? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class TimerItem(
    val id: String,
    val label: String,
    val endsAtEpochMs: Long
)

data class ReminderItem(
    val id: String,
    val text: String,
    val timeHHmm: String,
    val fired: Boolean = false
)

data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean = false
)

data class NoteItem(
    val id: String,
    val text: String,
    val createdAt: String
)

data class VaultSettings(
    val ownerName: String = "Jitendra Maurya",
    val ownerEmail: String = "jitendermourya921@gmail.com",
    val sttLang: String = "hi-IN",
    val ttsVoiceName: String = "",
    val githubUsername: String = "Crosas27",
    val githubToken: String = "",
    val reducedMotionOverride: Boolean = false,
    val spontaneousFlirtEnabled: Boolean = true
)

data class VaultData(
    val chatHistory: List<ChatMessage> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val reminders: List<ReminderItem> = emptyList(),
    val settings: VaultSettings = VaultSettings()
)

data class AiActionIntent(
    val type: String = "none",
    val label: String = "",
    val seconds: Int = 0,
    val reminderTime: String = "",
    val itemText: String = "",
    val flag: Boolean = false,
    val hour: Int = 0,
    val minute: Int = 0
)

data class JarvisAiResponse(
    val reply: String,
    val isCode: Boolean,
    val code: String,
    val codeLang: String,
    val emotion: String,
    val action: AiActionIntent
)

data class TtsVoiceOption(
    val name: String,
    val localeTag: String,
    val displayLabel: String
)

data class GitHubRepoItem(
    val name: String,
    val fullName: String,
    val description: String,
    val language: String,
    val isPrivate: Boolean,
    val htmlUrl: String
)

data class GitHubFileItem(
    val name: String,
    val type: String,
    val sizeBytes: Long
)
