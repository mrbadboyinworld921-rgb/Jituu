package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.model.TtsVoiceOption
import com.example.model.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sin

class JarvisVoiceManager(
    private val context: Context,
    private val onTranscript: (String) -> Unit,
    private val onStateChanged: (VoiceState) -> Unit,
    private val onSpeakComplete: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var currentLang = "hi-IN"
    private var selectedVoiceName = ""
    private var isListening = false
    private var isSpeaking = false
    private var speechEnvelopePhase = 0f

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _rmsEnergy = MutableStateFlow(0f)
    val rmsEnergy: StateFlow<Float> = _rmsEnergy.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private val speechEnvelopeRunnable = object : Runnable {
        override fun run() {
            if (!isSpeaking) return
            speechEnvelopePhase += 0.34f
            val syllable = abs(sin(speechEnvelopePhase * 1.4f)) * 0.55f +
                abs(sin(speechEnvelopePhase * 0.55f)) * 0.35f
            _rmsEnergy.value = (0.22f + syllable).coerceIn(0.18f, 0.96f)
            mainHandler.postDelayed(this, 60L)
        }
    }

    init {
        initTts()
    }

    private fun scoreNaturalGirlfriendVoice(voice: Voice): Int {
        val nameLower = voice.name.lowercase(Locale.US)
        val tagLower = voice.locale.toLanguageTag().lowercase(Locale.US)
        val langPrefix = currentLang.substringBefore("-").lowercase(Locale.US)
        var score = 0

        if (tagLower == currentLang.lowercase(Locale.US)) score += 60
        else if (voice.locale.language.equals(langPrefix, ignoreCase = true)) score += 45
        else if (tagLower == "hi-in") score += 32
        else if (tagLower == "pa-in") score += 30
        else if (tagLower == "en-in") score += 26
        else if (voice.locale.language in setOf("hi", "pa", "en")) score += 16

        when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> score += 30
            voice.quality >= Voice.QUALITY_HIGH -> score += 22
            voice.quality >= Voice.QUALITY_NORMAL -> score += 8
        }

        // Prefer natural female neural voices on Android / Google TTS
        val femaleHints = listOf(
            "female", "woman", "hia", "hic", "hie", "paa", "pac", "pag",
            "ena", "enc", "sfg", "tpf", "iob", "gba", "gbc"
        )
        val maleHints = listOf("male", "man", "hib", "hid", "pab", "pad", "enb", "ene", "iom", "tpd", "gbb", "gbd")
        if (femaleHints.any { nameLower.contains(it) }) score += 45
        if (maleHints.any { nameLower.contains(it) && !nameLower.contains("female") }) score -= 50

        if (!voice.isNetworkConnectionRequired) score += 8
        return score
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val voices = tts?.voices.orEmpty()
                    .filter { it.locale.language in setOf("hi", "pa", "en") }
                    .sortedWith(
                        compareByDescending<Voice> { scoreNaturalGirlfriendVoice(it) }
                            .thenBy { it.name }
                    )
                    .map { v ->
                        val qualityBadge = when {
                            v.quality >= Voice.QUALITY_VERY_HIGH -> "HD Neural"
                            v.quality >= Voice.QUALITY_HIGH -> "Natural"
                            else -> "Standard"
                        }
                        TtsVoiceOption(
                            name = v.name,
                            localeTag = v.locale.toLanguageTag(),
                            displayLabel = "${v.locale.displayLanguage} (${v.locale.toLanguageTag()}) • $qualityBadge"
                        )
                    }
                _availableVoices.value = voices
                if (selectedVoiceName.isBlank()) {
                    selectedVoiceName = voices.firstOrNull()?.name.orEmpty()
                }
                applySelectedVoice()
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status=$status")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    isSpeaking = true
                    speechEnvelopePhase = 0f
                    mainHandler.removeCallbacks(speechEnvelopeRunnable)
                    mainHandler.post(speechEnvelopeRunnable)
                    onStateChanged(VoiceState.SPEAKING)
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    isSpeaking = false
                    mainHandler.removeCallbacks(speechEnvelopeRunnable)
                    _rmsEnergy.value = 0f
                    onStateChanged(VoiceState.STANDBY)
                    onSpeakComplete()
                }
            }

            @Deprecated("Deprecated in API 21")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    isSpeaking = false
                    mainHandler.removeCallbacks(speechEnvelopeRunnable)
                    _rmsEnergy.value = 0f
                    onStateChanged(VoiceState.STANDBY)
                    onSpeakComplete()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    isSpeaking = false
                    mainHandler.removeCallbacks(speechEnvelopeRunnable)
                    _rmsEnergy.value = 0f
                    onStateChanged(VoiceState.STANDBY)
                    onSpeakComplete()
                }
            }
        })
    }

    fun setLanguage(langTag: String) {
        currentLang = langTag
        applySelectedVoice()
    }

    fun setSelectedVoice(voiceName: String) {
        selectedVoiceName = voiceName
        applySelectedVoice()
    }

    private fun applySelectedVoice() {
        val engine = tts ?: return
        if (!ttsReady) return
        val targetLangPrefix = currentLang.substringBefore("-").lowercase(Locale.US)
        val manualMatch = engine.voices?.firstOrNull {
            it.name == selectedVoiceName && it.locale.language.equals(targetLangPrefix, ignoreCase = true)
        }
        val bestLangMatch = engine.voices
            ?.filter { it.locale.language in setOf("hi", "pa", "en") }
            ?.maxByOrNull { scoreNaturalGirlfriendVoice(it) }
        val match = manualMatch ?: bestLangMatch
        if (match != null) {
            engine.voice = match
        } else {
            engine.language = Locale.forLanguageTag(currentLang)
        }
        // Warm, natural female girlfriend pitch and relaxed conversational pacing
        engine.setPitch(1.07f)
        engine.setSpeechRate(0.94f)
    }

    fun startListening() {
        mainHandler.post {
            if (!isRecognitionAvailable) {
                onStateChanged(VoiceState.UNAVAILABLE)
                return@post
            }
            if (isSpeaking || isListening) return@post

            stopListeningInternal()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onStateChanged(VoiceState.LISTENING)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    _rmsEnergy.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    _rmsEnergy.value = 0f
                }

                override fun onError(error: Int) {
                    isListening = false
                    _rmsEnergy.value = 0f
                    if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                    ) {
                        when (currentLang) {
                            "pa-IN" -> {
                                currentLang = "hi-IN"
                                startListening()
                                return
                            }
                            "hi-IN" -> {
                                currentLang = "en-IN"
                                startListening()
                                return
                            }
                        }
                    }
                    onStateChanged(VoiceState.STANDBY)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    _rmsEnergy.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim().orEmpty()
                    if (transcript.isNotEmpty()) {
                        onTranscript(transcript)
                    } else {
                        onStateChanged(VoiceState.STANDBY)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching {
                recognizer.startListening(intent)
                isListening = true
                onStateChanged(VoiceState.LISTENING)
            }.onFailure {
                isListening = false
                onStateChanged(VoiceState.UNAVAILABLE)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            stopListeningInternal()
            if (!isSpeaking) onStateChanged(VoiceState.STANDBY)
        }
    }

    private fun stopListeningInternal() {
        isListening = false
        _rmsEnergy.value = 0f
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    fun speak(rawText: String) {
        val clean = stripForSpeech(rawText)
        if (clean.isBlank() || !ttsReady) {
            onSpeakComplete()
            return
        }
        mainHandler.post {
            stopListeningInternal()
            applySelectedVoice()
            val id = UUID.randomUUID().toString()
            isSpeaking = true
            speechEnvelopePhase = 0f
            mainHandler.removeCallbacks(speechEnvelopeRunnable)
            mainHandler.post(speechEnvelopeRunnable)
            onStateChanged(VoiceState.SPEAKING)
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            tts?.stop()
            isSpeaking = false
            mainHandler.removeCallbacks(speechEnvelopeRunnable)
            _rmsEnergy.value = 0f
            onStateChanged(VoiceState.STANDBY)
        }
    }

    fun release() {
        mainHandler.post {
            mainHandler.removeCallbacks(speechEnvelopeRunnable)
            stopListeningInternal()
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    companion object {
        private const val TAG = "JarvisVoiceManager"

        fun stripForSpeech(text: String): String {
            return text
                .replace(Regex("```[\\s\\S]*?```"), " ")
                .replace(Regex("[*_`~#>\\[\\]()]"), " ")
                .replace(Regex("[\\p{So}\\p{Cn}]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
