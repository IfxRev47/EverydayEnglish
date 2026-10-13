package com.oxford.a1.ui.wordlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxford.a1.audio.AudioState
import com.oxford.a1.data.model.*
import com.oxford.a1.viewmodel.WordUiState

@Composable
fun WordListScreen(
    state: WordUiState, listState: LazyListState, audio: AudioState,
    onQuery: (String) -> Unit, onPos: (String?) -> Unit, onLevel: (String?) -> Unit,
    onLearned: (LearnedFilter) -> Unit, onSort: () -> Unit, onReset: () -> Unit,
    onOpen: (Long) -> Unit, onPlay: (Word) -> Unit, onAbout: () -> Unit,
    onOffline: () -> Unit = {},
    today: (@Composable () -> Unit)? = null
) {
    var posMenu by remember { mutableStateOf(false) }
    var levelMenu by remember { mutableStateOf(false) }
    val levels = state.allWords.flatMap { it.meanings }.map { it.cefrLevel }.distinct().sorted()
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxHeight < 520.dp
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("每日英语", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (!compact) Text("从熟悉，到真正记住", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onOffline) { Text("AI 助手") }
            TextButton(onClick = onAbout) { Text("说明") }
        }
        today?.invoke()
        if (!compact) Surface(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("OXFORD 3000 / 5000 · ${levels.joinToString(" / ")}", style = MaterialTheme.typography.labelLarge)
                Text("${state.allWords.size} 个基础词汇", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("已掌握 ${state.allWords.count { it.learned }} 个 · 按自己的节奏学习",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(value = state.filter.query, onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("搜索英文或中文") }, placeholder = { Text("例如 apple、苹果") },
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                if (state.filter.query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("清除") }
            })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LearnedFilter.entries.forEach { filter ->
                FilterChip(selected = filter == state.filter.learned, onClick = { onLearned(filter) }, label = { Text(filter.label) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { levelMenu = true }) { Text("${state.filter.cefrLevel ?: "全部等级"} ▾") }
                DropdownMenu(expanded = levelMenu, onDismissRequest = { levelMenu = false }) {
                    DropdownMenuItem(text = { Text("全部等级") }, onClick = { onLevel(null); levelMenu = false })
                    levels.forEach { level ->
                        DropdownMenuItem(text = { Text(level) }, onClick = { onLevel(level); levelMenu = false })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { posMenu = true }) {
                    Text((state.filter.partOfSpeech?.let(::posLabel) ?: "全部词性") + " ▾",
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = posMenu, onDismissRequest = { posMenu = false }) {
                    DropdownMenuItem(text = { Text("全部词性") }, onClick = { onPos(null); posMenu = false })
                    state.allWords.flatMap { it.meanings }.map { it.partOfSpeech }.distinct().sorted().forEach { pos ->
                        DropdownMenuItem(text = { Text(posLabel(pos)) }, onClick = { onPos(pos); posMenu = false })
                    }
                }
            }
            TextButton(onClick = onSort, modifier = Modifier.semantics { contentDescription = "切换字母排序" }) {
                Text(if (state.filter.ascending) "A → Z" else "Z → A")
            }
            Text("${state.visibleWords.size} 个", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.visibleWords.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("没有找到匹配的单词", style = MaterialTheme.typography.titleMedium)
                Text("试试其他关键词，或清除筛选。", modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onReset) { Text("清除搜索与筛选") }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.visibleWords, key = { it.id }) { word ->
                    WordCard(word, audio, { onOpen(word.id) }, { onPlay(word) })
                }
            }
        }
    }
    }
}

@Composable
private fun WordCard(word: Word, audio: AudioState, onOpen: () -> Unit, onPlay: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(word.word, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("/${word.phonetic}/", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (word.learned) Text("已掌握", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = onPlay, enabled = audio.ready,
                    modifier = Modifier.semantics { contentDescription = "播放 ${word.word} 发音" }) {
                    Text(if (audio.speakingWordId == word.id) "再听" else "播放")
                }
            }
            Spacer(Modifier.height(8.dp))
            word.meanings.forEach { meaning ->
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${posLabel(meaning.partOfSpeech)} · ${meaning.cefrLevel}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(max = 100.dp))
                    Text(meaning.chineseMeaning, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
