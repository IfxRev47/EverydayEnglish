package com.oxford.a1.practice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxford.a1.audio.TtsAudioProvider
import com.oxford.a1.data.model.Word

@Composable
fun MemorizationHistory(history: List<MemorizationDay>, words: List<Word>, audio: TtsAudioProvider, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    val day = history.find { it.date == selected }
    val audioState by audio.state.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = { audio.stop(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("背诵记录", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { audio.stop(); onDismiss() }) { Text("关闭记录") }
                }
                if (day == null) {
                    Text("按日期查看确认“今日已背过”的单词，同一天同一词只记录一次。")
                    if (history.isEmpty()) Text("还没有背诵记录。完成今日背诵并确认“今日已背过”后，会在这里显示。")
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { it.date }) { record ->
                            Card(onClick = { selected = record.date }, modifier = Modifier.fillMaxWidth()) {
                                Text("${record.date} · 背过 ${record.wordIds.size} 个词", Modifier.padding(16.dp))
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { audio.stop(); selected = null }) { Text("返回日期列表") }
                    Text("${day.date} · 背过 ${day.wordIds.size} 个词", style = MaterialTheme.typography.titleMedium)
                    val available = remember(words) { words.associateBy { it.id } }
                    val ids = remember(day, words) { day.wordIds.sortedBy { available[it]?.word.orEmpty() } }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ids, key = { it }) { id ->
                            val word = available[id]
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (word == null) Text("词条 #$id 已不在本地词库中，背诵记录保留。")
                                    else {
                                        Text(word.word, style = MaterialTheme.typography.titleLarge)
                                        Text("/${word.phonetic}/")
                                        Text(word.meanings.map { it.chineseMeaning }.distinct().joinToString("；"))
                                        TextButton(onClick = { audio.play(word) }, enabled = audioState.ready,
                                            modifier = Modifier.semantics { contentDescription = "播放记录中的 ${word.word}" }) { Text("播放发音") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
