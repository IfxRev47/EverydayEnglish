package com.oxford.a1.ui.worddetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxford.a1.audio.AudioState
import com.oxford.a1.data.model.Word
import com.oxford.a1.data.model.posLabel

@Composable
fun WordDetailScreen(word: Word, audio: AudioState, onBack: () -> Unit,
                     onPlay: () -> Unit, onStop: () -> Unit, onToggle: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ 返回词表") }
        Text(word.meanings.map { it.cefrLevel }.distinct().joinToString(" · ") + "  /  基础词汇",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word.word, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
            Text("/${word.phonetic}/", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("英式音标 · 自编学习数据", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlay, enabled = audio.ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text(if (audio.speakingWordId == word.id) "再听一次" else "播放发音")
            }
            Text(audio.message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("可以反复点击播放，跟着读一遍。", style = MaterialTheme.typography.bodySmall)
            Text("多读音词的 TTS 读音由系统决定，请对照词性音标。", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onSettings) { Text("语音设置") }
                if (audio.speakingWordId != null) TextButton(onClick = onStop) { Text("停止播放") }
            }
        }
        HorizontalDivider()
        Text("词性与释义", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        word.meanings.forEach { meaning ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${posLabel(meaning.partOfSpeech)} · ${meaning.cefrLevel}",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(meaning.chineseMeaning, style = MaterialTheme.typography.bodyLarge)
                    Text("${meaning.headword.ifBlank { word.word }} /${meaning.phonetic.ifBlank { word.phonetic }}/",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp)) {
            Text(if (word.learned) "已掌握 · 点击改为未掌握" else "标记为已掌握")
        }
        Text("这里记录的是整个词形的掌握状态，A1 / A2 共用同一个标记。想不起来时，随时改回未掌握。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
