package com.oxford.a1.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.oxford.a1.data.model.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsAudioProvider(context: Context) : AudioProvider {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(AudioState())
    override val state = mutableState.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var generation = 0L
    private var initialization = 0L
    private var utterance: String? = null
    private val pending = java.util.ArrayDeque<String>()
    private var engine: TextToSpeech? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }
        .build()

    init {
        reload()
    }

    fun reload() {
        if (closed) return
        stop()
        engine?.shutdown()
        mutableState.value = AudioState()
        val token = ++initialization
        engine = TextToSpeech(appContext) { status ->
            main.post {
                if (!closed && token == initialization) initialize(status)
            }
        }
    }

    private fun initialize(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            mutableState.value = AudioState(message = "语音引擎不可用，请在系统设置中安装或启用 TTS。")
            return
        }
        // Prefer an installed UK English voice; an installed English alternative is acceptable.
        val voice = tts.voices.orEmpty().filter {
            it.locale.language == "en" && !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        }.sortedWith(compareBy({ if (it.locale.country == "GB") 0 else if (it.locale.country == "US") 1 else 2 }, { it.name }))
            .firstOrNull()
        if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            mutableState.value = AudioState(message = "缺少离线英语语音，请在系统 TTS 设置中下载英语语音包。")
            return
        }
        tts.setAudioAttributes(attributes)
        tts.setSpeechRate(0.85f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(utteranceId, false)
            @Deprecated("Android compatibility callback")
            override fun onError(utteranceId: String?) = finish(utteranceId, true)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, true)
        })
        val accent = when (voice.locale.country) { "GB" -> "英式"; "US" -> "美式"; else -> voice.locale.getDisplayName(Locale.SIMPLIFIED_CHINESE) }
        mutableState.value = AudioState(ready = true, message = "$accent · 系统合成发音（TTS）")
    }

    private fun finish(id: String?, failed: Boolean) {
        main.post {
            if (!closed && id == utterance) {
                if (!failed && pending.isNotEmpty()) { speakNext(); return@post }
                pending.clear()
                utterance = null
                audioManager.abandonAudioFocusRequest(focus)
                mutableState.value = mutableState.value.copy(speakingWordId = null, speakingText = false,
                    message = if (failed) "发音失败，请重试或检查系统语音设置。"
                        else if (mutableState.value.speakingText) "朗读完成 · 离线英语 TTS" else mutableState.value.message)
            }
        }
    }

    override fun play(word: Word) {
        if (closed || !state.value.ready) return
        stop()
        engine?.setSpeechRate(0.85f)
        engine?.setPitch(1f)
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mutableState.value = state.value.copy(message = "暂时无法播放，请稍后重试。")
            return
        }
        val id = "${word.id}-${++generation}"
        utterance = id
        mutableState.value = state.value.copy(speakingWordId = word.id)
        if (engine?.speak(word.word, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id, true)
    }

    fun speakEnglish(text: String, rate: Float = 0.85f, pitch: Float = 1f) {
        if (closed || !state.value.ready) return
        stop()
        val parts = SpeechText.chunks(text, minOf(1000, TextToSpeech.getMaxSpeechInputLength()))
        if (parts.isEmpty()) {
            mutableState.value = state.value.copy(message = "没有可朗读的英文内容。")
            return
        }
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mutableState.value = state.value.copy(message = "暂时无法播放，请稍后重试。")
            return
        }
        engine?.setSpeechRate(rate.coerceIn(0.5f, 1.5f))
        engine?.setPitch(pitch.coerceIn(0.8f, 1.2f))
        pending.addAll(parts)
        mutableState.value = state.value.copy(speakingText = true, message = "正在朗读英文 · 离线 TTS")
        speakNext()
    }

    private fun speakNext() {
        val part = pending.pollFirst() ?: return
        val id = "text-${++generation}"
        utterance = id
        if (engine?.speak(part, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id, true)
    }

    override fun stop() {
        utterance = null
        pending.clear()
        engine?.stop()
        audioManager.abandonAudioFocusRequest(focus)
        mutableState.value = state.value.copy(speakingWordId = null, speakingText = false,
            message = if (state.value.speakingText) "朗读已停止 · 离线英语 TTS" else state.value.message)
    }

    override fun close() {
        closed = true
        stop()
        engine?.shutdown()
        engine = null
        main.removeCallbacksAndMessages(null)
    }
}
