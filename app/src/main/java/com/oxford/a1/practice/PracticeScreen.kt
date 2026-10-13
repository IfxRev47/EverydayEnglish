package com.oxford.a1.practice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxford.a1.ai.AiViewModel
import com.oxford.a1.audio.TtsAudioProvider
import com.oxford.a1.data.model.Word
import com.oxford.a1.data.model.posLabel

@Composable
fun TodaySummary(progress: DailyProgress, words: List<Word> = emptyList(), onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("今日目标 · 开始练习", style = MaterialTheme.typography.titleSmall)
            Text("今日背诵 ${progress.memorizedCount}/${progress.planned.size} 个 · 点击选词或开始背诵",
                style = MaterialTheme.typography.bodySmall)
            val preview = words.filter { it.id in progress.planned }.take(6).joinToString(" · ") { it.word }
            Text(preview.ifEmpty { "还没有选词，自己选择或让 AI 按等级推荐" }, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { progress.planFraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PracticeScreen(model: PracticeViewModel, ai: AiViewModel, words: List<Word>, audio: TtsAudioProvider,
    onBack: () -> Unit, onModel: () -> Unit, onSpeechSettings: () -> Unit, onLearned: (Long) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val daily by model.daily.collectAsStateWithLifecycle()
    val aiState by ai.state.collectAsStateWithLifecycle()
    val audioState by audio.state.collectAsStateWithLifecycle()
    var goals by remember { mutableStateOf(false) }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var levelMenu by remember { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val history by model.history.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    LaunchedEffect(state.mode, state.index) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(if (state.mode == PracticeMode.MENU) "返回词库" else "返回今日练习") }
        Text(state.mode.title, style = MaterialTheme.typography.headlineMedium)
        if (state.mode == PracticeMode.MENU) {
            Text("${daily.date} · 已掌握 ${words.count { it.learned }} 个词")
            TodayPlanner(model, ai, words, onModel, onHistory = { model.refresh(); showHistory = true }) { mode, custom ->
                audio.stop(); ai.clearPractice(); model.start(words, mode, null, custom)
            }
            TextButton(onClick = { more = !more }) { Text("更多练习（随机回顾）") }
            if (more) {
            Text(StudyKind.entries.joinToString(" · ") { "${it.label} ${daily.counts.getValue(it)}/${daily.goals.getValue(it)}" })
            TextButton(onClick = { goals = true }) { Text("调整辅助练习目标") }
            Box {
                OutlinedButton(onClick = { levelMenu = true }) { Text("练习范围：${level ?: "全部等级"} ▾") }
                DropdownMenu(levelMenu, onDismissRequest = { levelMenu = false }) {
                    DropdownMenuItem(text = { Text("全部等级") }, onClick = { level = null; levelMenu = false })
                    words.flatMap { it.meanings }.map { it.cefrLevel }.distinct().sorted().forEach { value ->
                        DropdownMenuItem(text = { Text(value) }, onClick = { level = value; levelMenu = false })
                    }
                }
            }
            Text("每轮随机抽取最多 10 个已掌握词，不重复；练习结果不会自动修改掌握标记。")
            OutlinedButton(onClick = { audio.stop(); ai.clearPractice(); model.start(words, PracticeMode.REVIEW, level) },
                enabled = !aiState.busy) { Text("已掌握随机回顾") }
            }
            OutlinedButton(onClick = onModel, modifier = Modifier.fillMaxWidth()) { Text("模型与参数设置") }
            Text("单词听写无需模型。AI 句子听写和造句需要先加载本地 GGUF 模型，并安装离线英语 TTS。")
        } else {
            val word = words.find { it.id == state.currentId }
            if (state.ids.isEmpty()) {
                Text(if (state.mode == PracticeMode.REVIEW) "这个范围内还没有已掌握单词，请先标记单词，或切换等级。" else "当前词表为空，请返回选择单词。")
            } else if (state.index >= state.ids.size) {
                Text("本轮练习完成！", style = MaterialTheme.typography.titleLarge)
                TodaySummary(daily, words) { model.menu() }
                Button(onClick = { audio.stop(); ai.clearPractice(); model.start(words, state.mode, level, state.custom) }) { Text("再练一轮") }
            } else if (word == null || (state.mode == PracticeMode.REVIEW && !word.learned)) {
                Text("这个词已不在已掌握范围内。")
                Button(onClick = { audio.stop(); ai.clearPractice(); model.next() }, enabled = !aiState.busy) { Text("跳过") }
            } else {
                Text("第 ${state.index + 1} / ${state.ids.size} 个")
                val isAi = state.mode == PracticeMode.AI_DICTATION || state.mode == PracticeMode.SENTENCE
                val sentence = aiState.output.takeIf { aiState.practiceReady && aiState.practiceWordId == word.id && !aiState.busy }
                val dictation = state.mode == PracticeMode.WORD_DICTATION || state.mode == PracticeMode.AI_DICTATION
                if (!dictation || state.revealed) {
                    Text(word.word, style = MaterialTheme.typography.headlineLarge)
                    Text("/${word.phonetic}/")
                }
                if (state.mode == PracticeMode.REVIEW || state.mode == PracticeMode.TODAY) {
                    Text("先回想中文意思，再查看答案。")
                    OutlinedButton(onClick = { audio.play(word) }, enabled = audioState.ready) { Text("播放发音") }
                    if (!state.revealed) Button(onClick = model::reveal) { Text("查看释义") }
                    else {
                        Meanings(word)
                        if (!state.completed) {
                            if (state.mode == PracticeMode.TODAY) {
                                Button(onClick = { model.finish(word) }) { Text("今日已背过") }
                            } else {
                                Button(onClick = { model.finish(word) }) { Text("记得 · 完成回顾") }
                                OutlinedButton(onClick = { model.finish(word) }) { Text("忘了 · 再读一遍后完成") }
                            }
                        }
                        if (state.mode == PracticeMode.TODAY && !word.learned) OutlinedButton(onClick = { onLearned(word.id) }) { Text("确认已掌握此词") }
                        if (state.mode == PracticeMode.TODAY && word.learned) Text("已掌握")
                    }
                }
                if (isAi) {
                    if (!aiState.loaded) OutlinedButton(onClick = onModel) { Text("先加载模型") }
                    Button(onClick = { audio.stop(); ai.generatePractice(word) },
                        enabled = aiState.loaded && !aiState.busy && !state.submitted && !state.completed) {
                        Text(if (sentence == null) "生成练习句" else "重新生成练习句")
                    }
                    Text(aiState.status)
                    if (aiState.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = ai::stop) { Text("停止生成") }
                    }
                    if (sentence != null) {
                        if (state.mode == PracticeMode.SENTENCE || state.revealed) Text(sentence, style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { audio.speakEnglish(sentence, aiState.settings.speechRate, aiState.settings.speechPitch) },
                            enabled = audioState.ready) { Text("播放句子 / 再听") }
                        if (state.mode == PracticeMode.SENTENCE) {
                            Meanings(word)
                            Text("先跟读，再用这个词说一句关于自己的话。模型例句可能有误，可跳过不自然的句子。")
                            if (!state.completed) Button(onClick = { model.finish(word) }) { Text("已跟读并自己造句") }
                        }
                    }
                }
                if (state.mode == PracticeMode.WORD_DICTATION) {
                    Text("听发音，输入英文单词。答案在提交前隐藏。")
                    OutlinedButton(onClick = { audio.play(word) }, enabled = audioState.ready) { Text("播放单词 / 再听") }
                }
                if (dictation) {
                    val expected = if (state.mode == PracticeMode.WORD_DICTATION) word.word else sentence
                    if (state.mode == PracticeMode.AI_DICTATION) Text("听句子并输入。核对忽略大小写和标点，保留单词与缩写差异。")
                    OutlinedTextField(state.answer, model::answer, label = { Text("听到的英文") },
                        enabled = !state.submitted && !aiState.busy, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { model.submit(expected.orEmpty()); model.finish(word) },
                        enabled = expected != null && state.answer.isNotBlank() && !state.submitted && !aiState.busy) { Text("提交并核对") }
                    if (state.submitted) {
                        Text(if (state.correct) "听写正确" else "再听一次，注意下面的正确写法。")
                        Text("正确答案：$expected")
                        Meanings(word)
                    }
                }
                Text(audioState.message, style = MaterialTheme.typography.bodySmall)
                if (!audioState.ready) TextButton(onClick = onSpeechSettings) { Text("打开系统语音设置") }
                if (audioState.speakingWordId != null || audioState.speakingText) TextButton(onClick = audio::stop) { Text("停止朗读") }
                if (state.completed) {
                    Text("本词练习已完成，同一天重复不会增加计数。")
                    Button(onClick = { audio.stop(); ai.clearPractice(); model.next() }, enabled = !aiState.busy) { Text("下一个") }
                } else TextButton(onClick = { audio.stop(); ai.clearPractice(); model.next() }, enabled = !aiState.busy) { Text("跳过，不计入目标") }
            }
        }
    }
    if (goals) GoalDialog(daily, onDismiss = { goals = false }, onSave = { model.goals(it); goals = false })
    if (showHistory) MemorizationHistory(history, words, audio) { showHistory = false }
}

@Composable private fun Meanings(word: Word) {
    word.meanings.forEach { Text("${posLabel(it.partOfSpeech)} · ${it.chineseMeaning}") }
}

@Composable private fun GoalDialog(progress: DailyProgress, onDismiss: () -> Unit, onSave: (Map<StudyKind, Int>) -> Unit) {
    var values by remember { mutableStateOf(progress.goals.mapValues { it.value.toString() }) }
    val valid = StudyKind.entries.all { values[it]?.toIntOrNull() in 0..100 }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("今日目标数量", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("每项 0–100；0 表示关闭。调整目标不会清除今日已完成记录。")
                    StudyKind.entries.forEach { kind ->
                        OutlinedTextField(values.getValue(kind), { input -> values = values + (kind to input.take(3)) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("${kind.label}目标") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onSave(values.mapValues { it.value.toInt() }) }, enabled = valid) { Text("保存目标") }
                }
            }
        }
    }
}
