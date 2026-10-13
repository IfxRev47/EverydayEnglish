package com.oxford.a1.grammar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxford.a1.audio.TtsAudioProvider

@Composable
fun GrammarScreen(model: GrammarViewModel, audio: TtsAudioProvider, onExit: () -> Unit, onSpeechSettings: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val voice by audio.state.collectAsStateWithLifecycle()
    val lesson = state.lesson
    fun back() { audio.stop(); if (lesson == null) onExit() else model.back() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = ::back) { Text(if (lesson == null) "返回今日计划" else "返回语法目录") }
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在读取本地课程…")
        } else if (state.error != null) {
            Text(state.error.orEmpty())
            Button(onClick = model::open) { Text("重试读取课程") }
        } else if (lesson == null) {
            Text("语法入门 · 从词到句子", style = MaterialTheme.typography.headlineSmall)
            Text("看懂 → 跟读 → 换词 → 点词拼句 → 独立表达。每天练一小课，不需要先背语法术语。")
            Text("已完成 ${state.progress.values.count { it.completedDate != null }}/${state.lessons.size} 课 · 完成记录不是熟练度评估")
            state.lessons.find { it.id == state.lastId }?.let { last ->
                OutlinedButton(onClick = { model.select(last.id) }, modifier = Modifier.fillMaxWidth()) { Text("继续上次学习：${last.title}") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.lessons, key = { it.id }) { item ->
                    val progress = state.progress[item.id] ?: GrammarProgress()
                    Card(onClick = { model.select(item.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${item.id.uppercase()} · ${item.title}", style = MaterialTheme.typography.titleMedium)
                            Text(item.goal)
                            Text(if (progress.completedDate != null) "已完成 · ${progress.completedDate}"
                                else if (item.id == state.lastId || progress.step > 0) "学习中 · 第 ${progress.step + 1}/5 步" else "未开始")
                            if (progress.completedDate != null) TextButton(onClick = { model.select(item.id, true) }) { Text("从头再练") }
                        }
                    }
                }
                item { Text("讲解、例句和练习为项目自编入门内容。来源与教学方案见项目文档。自由表达由你自查，本版不使用 AI 判分。", style = MaterialTheme.typography.bodySmall) }
            }
        } else {
            val scroll = rememberScrollState()
            LaunchedEffect(lesson.id, state.step) { scroll.scrollTo(0) }
            val labels = listOf("看懂一句", "听读跟读", "换词表达", "点词拼句", "独立表达")
            Text(lesson.title, style = MaterialTheme.typography.titleLarge)
            Text("第 ${state.step + 1}/5 步 · ${labels[state.step]}")
            LinearProgressIndicator(progress = { (state.step + 1) / 5f }, modifier = Modifier.fillMaxWidth())
            Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.step) {
                    0 -> {
                        Text(lesson.goal, style = MaterialTheme.typography.titleMedium)
                        Text(lesson.rule)
                        lesson.examples.forEach { example ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(example.english, style = MaterialTheme.typography.titleLarge)
                                    Text(example.chinese)
                                    Text(example.parts, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    1 -> {
                        Text("听一句，暂停后跟读一句。最后再不看文字说一次；没有语音包时也可以自行朗读。")
                        var translation by rememberSaveable(lesson.id) { mutableStateOf(false) }
                        TextButton(onClick = { translation = !translation }) { Text(if (translation) "隐藏中文" else "需要时查看中文") }
                        lesson.examples.forEach { example ->
                            Text(example.english, style = MaterialTheme.typography.titleLarge)
                            if (translation) Text(example.chinese)
                            OutlinedButton(onClick = { audio.speakEnglish(example.english, 0.8f, 1f) }, enabled = voice.ready) { Text("听：${example.english}") }
                        }
                        Text(voice.message)
                        if (!voice.ready) TextButton(onClick = onSpeechSettings) { Text("安装 / 设置英语语音") }
                        if (voice.speakingText) TextButton(onClick = audio::stop) { Text("停止朗读") }
                    }
                    2 -> {
                        var index by rememberSaveable(lesson.id) { mutableStateOf(0) }
                        var reveal by rememberSaveable(lesson.id) { mutableStateOf(false) }
                        Text("保持句子骨架，换一个意思。先尝试说，再展开英文核对。")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            lesson.swaps.indices.toList().chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { i -> FilterChip(index == i, onClick = { index = i; reveal = false }, label = { Text(lesson.swaps[i].chinese) }) }
                                }
                            }
                        }
                        Text("想表达：${lesson.swaps[index].chinese}", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { reveal = true }) { Text("我说完了，查看英文") }
                        if (reveal) {
                            Text(lesson.swaps[index].english, style = MaterialTheme.typography.titleLarge)
                            Text(lesson.swaps[index].parts)
                            OutlinedButton(onClick = { audio.speakEnglish(lesson.swaps[index].english, 0.8f, 1f) }, enabled = voice.ready) { Text("听替换后的句子") }
                        }
                    }
                    3 -> {
                        Text(lesson.guided.prompt, style = MaterialTheme.typography.titleMedium)
                        Text(state.assembled.ifEmpty { "点击下面的词，组成句子" }, style = MaterialTheme.typography.titleLarge)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.bank.indices.toList().chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { index -> AssistChip(onClick = { model.token(index) }, enabled = index !in state.selected, label = { Text(state.bank[index]) }) }
                                }
                            }
                        }
                        TextButton(onClick = model::undo, enabled = state.selected.isNotEmpty()) { Text("撤回最后一个词") }
                        Button(onClick = model::checkGuided, enabled = state.selected.size == state.bank.size) { Text("核对拼句") }
                        if (state.guidedCorrect != null) {
                            Text(if (state.guidedCorrect == true) "拼句正确，现在试着自己表达。" else "还没有匹配目标句型，撤回后再试。")
                            Text(lesson.guided.explanation)
                        }
                    }
                    4 -> {
                        Text(lesson.independent.prompt, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(state.independent, model::independent, modifier = Modifier.fillMaxWidth(), label = { Text("不看提示，写出目标句子") })
                        Button(onClick = model::checkIndependent, enabled = state.independent.isNotBlank()) { Text("核对目标句型") }
                        if (state.independentCorrect != null) {
                            Text(if (state.independentCorrect == true) "目标句型匹配成功" else "未匹配本课目标句型；其他写法也可能正确。请对照提示再试。")
                            if (state.independentCorrect == false) Text(lesson.independent.explanation)
                        }
                        HorizontalDivider()
                        Text(lesson.ownTask)
                        OutlinedTextField(state.own, model::own, modifier = Modifier.fillMaxWidth(), label = { Text("写一句自己的话") }, maxLines = 4)
                        Row {
                            Checkbox(state.spoken, onCheckedChange = model::spoken)
                            Text("我已不看原句，把自己的句子说出来", Modifier.padding(top = 12.dp))
                        }
                        Text("自己的句子不会自动判语法对错；这是主动表达练习，可对照句型自查。", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = model::complete, enabled = state.independentCorrect == true && state.spoken &&
                            state.own.any { it in 'a'..'z' || it in 'A'..'Z' } && !state.saved) { Text("保存表达并完成本课") }
                        if (state.saved) {
                            Text("本课练习已完成，自己的句子已保存。")
                            Button(onClick = { audio.stop(); model.back() }) { Text("回目录选下一课") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.step > 0) OutlinedButton(onClick = { audio.stop(); model.previous() }) { Text("上一步") }
                else TextButton(onClick = model::back) { Text("课程目录") }
                if (state.step < 4) Button(onClick = { audio.stop(); model.advance() }, enabled = state.step != 3 || state.guidedCorrect == true) { Text("下一步") }
            }
        }
    }
}
