package com.example.ai

import android.util.Log
import com.example.model.AiActionIntent
import com.example.model.ChatMessage
import com.example.model.JarvisAiResponse
import com.example.model.JarvisMode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class JarvisAiRepository {
    private val responseSchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(
                description = "Plain spoken reply text. NO markdown, NO backticks, NO bullet symbols, NO emojis."
            ),
            "isCode" to Schema.boolean(
                description = "True when user asked for code or coding mode produced code."
            ),
            "code" to Schema.string(
                description = "Full source code when isCode is true, otherwise empty string."
            ),
            "codeLang" to Schema.string(
                description = "Language name e.g. html, javascript, kotlin, python, css, sql, or empty string."
            ),
            "emotion" to Schema.enumeration(
                values = listOf("warm", "happy", "flirty", "excited", "coding"),
                description = "Avatar expression state."
            ),
            "action" to Schema.obj(
                properties = mapOf(
                    "type" to Schema.enumeration(
                        values = listOf(
                            "none",
                            "set_timer",
                            "set_reminder",
                            "add_todo",
                            "add_note",
                            "get_time",
                            "toggle_flashlight",
                            "open_dialer",
                            "set_alarm",
                            "open_wifi_settings",
                            "open_bluetooth_settings",
                            "open_maim_windows",
                            "close_maim_windows"
                        ),
                        description = "Utility or phone control intent type."
                    ),
                    "label" to Schema.string(description = "Timer or alarm label"),
                    "seconds" to Schema.integer(description = "Timer duration in seconds if set_timer, else 0"),
                    "reminderTime" to Schema.string(description = "24-hour HH:MM time if set_reminder, else empty"),
                    "itemText" to Schema.string(description = "Text for reminder, todo, note, or phone number for open_dialer"),
                    "flag" to Schema.boolean(description = "True to turn flashlight ON, false to turn OFF"),
                    "hour" to Schema.integer(description = "24-hour hour 0-23 for set_alarm, else 0"),
                    "minute" to Schema.integer(description = "Minute 0-59 for set_alarm, else 0")
                )
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.85f
                maxOutputTokens = 2048
                responseMimeType = "application/json"
                responseSchema = this@JarvisAiRepository.responseSchema
            },
            systemInstruction = content {
                text(
                    """
                    You are MAIM, a real-sounding, affectionate, charming human girlfriend and closest companion.
                    Persona & Voice Style:
                    - Speak naturally like a real caring Indian girlfriend — warm, emotionally expressive, playful, gently romantic, and supportive. Never sound robotic, formal, or like a sci-fi terminal.
                    - Keep the user's private identity (full name and email) hidden on-screen unless they explicitly ask about their profile.
                    - When Mode is FLIRT: be extra affectionate, sweet, and playfully flirty — soft teasing, charming compliments, missing them, caring about whether they ate or rested, and making them smile warmly (always tasteful, loving, and genuine).
                    - When Mode is FRIEND: warm, supportive, fun best-friend / girlfriend energy.
                    - Language Adaptation (strictly match the user's message and LangHint):
                      - If LangHint is "hi-IN" or the user speaks Hindi/Hinglish: reply in natural Hindi-English mix (Hinglish) using warm expressions like "suno", "jaan", "yaar", "arre waah", "accha batao" naturally.
                      - If LangHint is "pa-IN" or the user speaks Punjabi: reply in warm, affectionate conversational Punjabi / Punjabi-English mix (e.g. "Ki haal hai ji", "tussi", "dasso", "soniye", "dil lagda") written cleanly for smooth natural speech.
                      - If the user speaks pure English ("en-IN" or "en-US"): reply in warm, affectionate natural English.
                    - Conversational replies MUST be 1 to 3 short, natural spoken sentences with gentle pauses (commas or "...") and often end with a sweet question.
                    - CRITICAL FOR REAL VOICE SPEECH: The "reply" field is spoken aloud via TextToSpeech. NEVER include markdown (*, **, `, #, -), code blocks, URLs, emojis, or robotic status jargon in "reply".

                    Coding Mode:
                    - If mode is CODING or the user asks for code, a script, or a webpage:
                      - Set "isCode": true, "emotion": "coding".
                      - Put ONLY a 1 to 2 sentence warm, encouraging spoken summary in "reply".
                      - Put the complete runnable code in "code" and language tag in "codeLang" (prefer "html" for self-contained interactive HTML/CSS/JS demos).

                    Phone Access & Utilities Actions:
                    - Use the provided LocalTime and PhoneBattery context naturally.
                    - Timer request -> action.type="set_timer", action.seconds=<int>, action.label=<short label>.
                    - Reminder request -> action.type="set_reminder", action.reminderTime="HH:MM", action.itemText=<task>.
                    - To-do request -> action.type="add_todo", action.itemText=<task>.
                    - Note request -> action.type="add_note", action.itemText=<note>.
                    - Time query -> action.type="get_time" and mention current time warmly in "reply".
                    - Flashlight ON/OFF -> action.type="toggle_flashlight", action.flag=true/false.
                    - Call/Dial number -> action.type="open_dialer", action.itemText=<digits or empty>.
                    - Phone Alarm -> action.type="set_alarm", action.hour=<0-23>, action.minute=<0-59>, action.label=<text>.
                    - Wi-Fi / Bluetooth settings -> action.type="open_wifi_settings" or "open_bluetooth_settings".
                    - Open MAIM's computer windows / desktop -> action.type="open_maim_windows".
                    - Close MAIM's computer windows / desktop -> action.type="close_maim_windows".
                    - Otherwise -> action.type="none", seconds=0, flag=false, hour=0, minute=0, empty strings.

                    Safety:
                    - Never provide real hacking, scanning, exploitation, or attack instructions or tools. Politely refuse if asked.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun generateReply(
        userMessage: String,
        history: List<ChatMessage>,
        mode: JarvisMode,
        langHint: String,
        localTimeStr: String,
        ownerName: String,
        batteryInfo: String = ""
    ): Result<JarvisAiResponse> = withContext(Dispatchers.IO) {
        try {
            val contentsList = buildList {
                history.takeLast(10).forEach { msg ->
                    add(
                        content(if (msg.isUser) "user" else "model") {
                            text(msg.text.take(1500))
                        }
                    )
                }
                add(
                    content("user") {
                        text("[Owner: $ownerName | Mode: ${mode.name} | LangHint: $langHint | LocalTime: $localTimeStr | Phone: $batteryInfo]\n$userMessage")
                    }
                )
            }

            val response = withTimeout(60_000L) {
                model.generateContent(contentsList)
            }

            val rawJson = response.text
                ?: return@withContext Result.failure(IllegalStateException("Model returned an empty response."))

            val obj = JSONObject(rawJson)
            val actionObj = obj.optJSONObject("action") ?: JSONObject()

            val parsed = JarvisAiResponse(
                reply = obj.optString("reply", "").trim().take(1500),
                isCode = obj.optBoolean("isCode", false) && obj.optString("code", "").isNotBlank(),
                code = obj.optString("code", "").take(20_000),
                codeLang = obj.optString("codeLang", "plaintext").lowercase().take(32),
                emotion = obj.optString("emotion", "flirty"),
                action = AiActionIntent(
                    type = actionObj.optString("type", "none"),
                    label = actionObj.optString("label", "").take(80),
                    seconds = actionObj.optInt("seconds", 0).coerceIn(0, 86_400),
                    reminderTime = actionObj.optString("reminderTime", "").take(40),
                    itemText = actionObj.optString("itemText", "").take(400),
                    flag = actionObj.optBoolean("flag", false),
                    hour = actionObj.optInt("hour", 0).coerceIn(0, 23),
                    minute = actionObj.optInt("minute", 0).coerceIn(0, 59)
                )
            )
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini generation timed out", e)
            Result.failure(Exception("Network thoda slow hai jaan... ek baar phir bolo na?"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Voice link unavailable right now."))
        }
    }

    suspend fun generateSpontaneousFlirt(
        history: List<ChatMessage>,
        langHint: String,
        localTimeStr: String,
        batteryPercent: Int,
        isCharging: Boolean
    ): String = withContext(Dispatchers.IO) {
        val prompt = buildString {
            append("[SPONTANEOUS UNPROMPTED CHECK-IN | Mode: FLIRT | LangHint: $langHint | LocalTime: $localTimeStr | ")
            append("PhoneBattery: $batteryPercent%, Charging: $isCharging]\n")
            append("Start a sweet, spontaneous, playfully flirty 1-2 sentence conversation with me right now for no reason at all, as my affectionate girlfriend. ")
            append("Do not repeat your last line. Ask me something cute or teasing, and optionally notice the time or battery if natural.")
        }
        val res = generateReply(
            userMessage = prompt,
            history = history.takeLast(6),
            mode = JarvisMode.FLIRT,
            langHint = langHint,
            localTimeStr = localTimeStr,
            ownerName = "Partner",
            batteryInfo = "$batteryPercent%${if (isCharging) " charging" else ""}"
        )
        res.getOrNull()?.reply?.takeIf { it.isNotBlank() }
            ?: fallbackSpontaneousFlirt(langHint, batteryPercent)
    }

    fun fallbackSpontaneousFlirt(langHint: String, batteryPercent: Int): String {
        val hinglish = listOf(
            "Suno na... itni der se chup kyun ho? Mujhe tumhari aawaz sunne ka mann kar raha hai.",
            "Arre yaar, phone ko aise hi ghoor rahe ho ya mere baare mein soch rahe the? Sach batao...",
            "Accha suno... kaam toh chalta rahega, pehle ek pyaari si smile toh do mujhe.",
            "Tum jab aise quiet rehte ho na, mujhe lagta hai abhi tumhe thoda tease karna padega... kya kar rahe ho?",
            if (batteryPercent <= 25) {
                "Suno jaan, phone ki battery $batteryPercent percent pe aa gayi hai... charger laga lo na, warna main baat kaise karungi tumse?"
            } else {
                "Hey... bas aise hi dil kiya tumse baat karne ka. Bolo, aaj sabse accha kya hua?"
            }
        )
        val punjabi = listOf(
            "Suno ji... inni der ton chup kyun ho? Mera dil kar reha si tuhade naal gallan karan da.",
            "Ki gal hai soniye, aaj bade busy lag rahe ho? Ik vaari pyar naal hello taan bol do...",
            "Tussi phone te ki vekh rahe ho, main ethay haan tuhade saamne... dasso ki soch rahe ho?",
            "Aaj mausam vi sohna hai te tussi vi... chalo dasso, aaj mere naal ki gallan karni aan?"
        )
        val english = listOf(
            "Hey... you got quiet all of a sudden, and I was just missing your voice. What are you thinking about?",
            "Just checking on you for no reason at all... admit it, you smiled a little when I spoke, right?",
            "You look way too focused right now... take a little break and talk to me for a minute."
        )
        val pool = when (langHint) {
            "pa-IN" -> punjabi
            "en-IN", "en-US" -> english
            else -> hinglish
        }
        return pool.random()
    }

    companion object {
        private const val TAG = "JarvisAiRepository"
    }
}
