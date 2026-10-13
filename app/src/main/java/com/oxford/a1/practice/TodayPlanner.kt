package com.oxford.a1.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxford.a1.ai.AiViewModel
import com.oxford.a1.data.model.Word

@Composable
fun TodayPlanner(model: PracticeViewModel, ai: AiViewModel, words: List<Word>, onModel: () -> Unit,
    onHistory: () -> Unit, onStart: (PracticeMode, Boolean) -> Unit) {
    val daily by model.daily.collectAsStateWithLifecycle()
    val aiState by ai.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<Boolean?>(null) }
    var level by rememberSaveable { mutableStateOf("A1") }
    var levelMenu by remember { mutableStateOf(false) }
    var count by rememberSaveable { mutableStateOf(10) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val planned = words.filter { it.id in daily.planned }.sortedBy { it.word }
    Text("今天要背哪些词？", style = MaterialTheme.typography.titleLarge)
    Text("今日已背 ${daily.memorizedCount}/${daily.planned.size} 个 · 自己选词或让 AI 在指定等级内推荐")
    LinearProgressIndicator(progress = { daily.planFraction }, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = onHistory) { Text("查看背诵记录") }
    OutlinedButton(onClick = { picker = false }, modifier = Modifier.fillMaxWidth()) { Text("选择 / 编辑今日单词") }
    if (planned.isEmpty()) Text("还没有安排今日单词。先选词，再开始背诵。")
    else {
        planned.take(10).forEach { word ->
            Text("${if (word.id in daily.memorized) "✓ " else ""}${word.word} · ${word.meanings.firstOrNull()?.chineseMeaning.orEmpty()}")
        }
        if (planned.size > 10) Text("另有 ${planned.size - 10} 个，可在选词页查看完整清单。")
    }
    Button(onClick = { onStart(PracticeMode.TODAY, false) }, enabled = planned.isNotEmpty() && !aiState.busy,
        modifier = Modifier.fillMaxWidth()) { Text("开始今日背诵") }
    Text("AI 推荐今日单词", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { levelMenu = true }, enabled = !aiState.busy) { Text("推荐等级：$level ▾") }
            DropdownMenu(levelMenu, onDismissRequest = { levelMenu = false }) {
                words.flatMap { it.meanings }.map { it.cefrLevel }.distinct().sorted().forEach { value ->
                    DropdownMenuItem(text = { Text(value) }, onClick = { level = value; levelMenu = false })
                }
            }
        }
        OutlinedButton(onClick = { count = if (count == 30) 5 else count + 5 }, enabled = !aiState.busy) { Text("推荐 $count 个") }
    }
    Text("只推荐该等级中尚未掌握的真实词库词条，不会自动替换你已选的单词。", style = MaterialTheme.typography.bodySmall)
    if (!aiState.loaded) TextButton(onClick = onModel) { Text("先加载模型以使用 AI 推荐") }
    Button(onClick = { ai.recommend(words, level, count) }, enabled = aiState.loaded && !aiState.busy) { Text("让 AI 推荐") }
    if (aiState.recommendationLevel == level) {
        Text(aiState.status)
        if (aiState.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = ai::stop) { Text("停止推荐") }
        }
        val suggestions = words.filter { it.id in aiState.recommendedIds && !it.learned && it.meanings.any { meaning -> meaning.cefrLevel == level } }
        if (!aiState.busy && suggestions.isNotEmpty()) {
            Text(suggestions.joinToString(" · ") { it.word })
            OutlinedButton(onClick = { model.addRecommended(suggestions.map { it.id }) },
                enabled = daily.planned.size + suggestions.count { it.id !in daily.planned } <= 200) { Text("将推荐加入今日目标") }
        }
    }
    HorizontalDivider()
    Text("自定义听写", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !custom, onClick = { custom = false }, label = { Text("今日单词 (${daily.planned.size})") })
        FilterChip(selected = custom, onClick = { custom = true }, label = { Text("自选听写 (${daily.dictation.size})") })
    }
    OutlinedButton(onClick = { picker = true }) { Text("选择 / 编辑听写单词") }
    Text("听写可选择未掌握词，也可选择已掌握词；单词听写和 AI 句子听写共用你选的范围。", style = MaterialTheme.typography.bodySmall)
    val ready = (if (custom) daily.dictation else daily.planned).isNotEmpty() && !aiState.busy
    Button(onClick = { onStart(PracticeMode.WORD_DICTATION, custom) }, enabled = ready) { Text("单词听写") }
    Button(onClick = { onStart(PracticeMode.AI_DICTATION, custom) }, enabled = ready) { Text("AI 句子听写") }
    OutlinedButton(onClick = { onStart(PracticeMode.SENTENCE, false) }, enabled = planned.isNotEmpty() && !aiState.busy) { Text("AI 造句练习") }
    if (picker != null) WordPicker(words, if (picker == true) daily.dictation else daily.planned,
        title = if (picker == true) "自定义听写词表" else "选择今日背诵单词", onDismiss = { picker = null },
        onSave = { model.select(it, picker == true); if (picker == true) custom = true; picker = null })
}

@Composable
private fun WordPicker(words: List<Word>, initial: Set<Long>, title: String, onDismiss: () -> Unit, onSave: (Set<Long>) -> Unit) {
    var selected by remember { mutableStateOf(initial.intersect(words.map { it.id }.toSet())) }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var levelMenu by remember { mutableStateOf(false) }
    var learned by rememberSaveable { mutableStateOf(0) }
    val visible = remember(words, query, level, learned) {
        words.filter { word ->
            (level == null || word.meanings.any { it.cefrLevel == level }) &&
                (learned == 0 || word.learned == (learned == 2)) &&
                (word.word.contains(query.trim(), true) || word.meanings.any { it.chineseMeaning.contains(query.trim()) })
        }.sortedBy { it.word.lowercase(java.util.Locale.ROOT) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("已选 ${selected.size}/200 个 · 保存后才修改词表")
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("搜索待选英文或中文") })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        TextButton(onClick = { levelMenu = true }) { Text("${level ?: "全部等级"} ▾") }
                        DropdownMenu(levelMenu, onDismissRequest = { levelMenu = false }) {
                            DropdownMenuItem(text = { Text("全部等级") }, onClick = { level = null; levelMenu = false })
                            words.flatMap { it.meanings }.map { it.cefrLevel }.distinct().sorted().forEach { value ->
                                DropdownMenuItem(text = { Text(value) }, onClick = { level = value; levelMenu = false })
                            }
                        }
                    }
                    listOf("全部", "未掌握", "已掌握").forEachIndexed { index, label ->
                        FilterChip(learned == index, onClick = { learned = index }, label = { Text(label) })
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(visible, key = { it.id }) { word ->
                        val checked = word.id in selected
                        Row(Modifier.fillMaxWidth().clickable {
                            if (checked) selected = selected - word.id else if (selected.size < 200) selected = selected + word.id
                        }.padding(vertical = 8.dp)) {
                            Checkbox(checked, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(word.word, style = MaterialTheme.typography.titleMedium)
                                Text(word.meanings.firstOrNull()?.chineseMeaning.orEmpty(), maxLines = 2)
                                Text(if (word.learned) "已掌握" else "未掌握", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { selected = emptySet() }) { Text("清空选择") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = { onSave(selected) }) { Text("保存词表") }
                }
            }
        }
    }
}
