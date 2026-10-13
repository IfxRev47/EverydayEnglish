package com.oxford.a1.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.oxford.a1.audio.TtsAudioProvider

@Composable
fun AiSpeechControls(audio: TtsAudioProvider, settings: AiSettings, text: String? = null, enabled: Boolean = true) {
    val state by audio.state.collectAsState()
    Column {
        Row {
            if (!text.isNullOrBlank()) TextButton(onClick = { audio.speakEnglish(text, settings.speechRate, settings.speechPitch) },
                enabled = enabled && state.ready) { Text("朗读英文") }
            if (state.speakingText) TextButton(onClick = audio::stop) { Text("停止朗读") }
        }
        Text(state.message, style = MaterialTheme.typography.labelSmall)
    }
}
