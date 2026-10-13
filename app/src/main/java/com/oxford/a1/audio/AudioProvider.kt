package com.oxford.a1.audio

import com.oxford.a1.data.model.Word
import kotlinx.coroutines.flow.StateFlow

data class AudioState(
    val ready: Boolean = false,
    val message: String = "正在准备英语发音…",
    val speakingWordId: Long? = null,
    val speakingText: Boolean = false
)

interface AudioProvider : AutoCloseable {
    val state: StateFlow<AudioState>
    fun play(word: Word)
    fun stop()
}
