package com.oxford.a1.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxford.a1.audio.TtsAudioProvider

@Composable
fun ChatScreen(model: AiViewModel, audio: TtsAudioProvider, onBack: () -> Unit, onModel: () -> Unit, onSettings: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var instruction by rememberSaveable { mutableStateOf("") }
    var delete by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val followBottom by remember { derivedStateOf { !list.canScrollForward } }
    LaunchedEffect(state.turns.size, state.turns.lastOrNull()?.assistant) {
        if (state.turns.isNotEmpty() && followBottom) list.scrollToItem(state.turns.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回词库") }
            Text("AI 聊天", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onModel) { Text("模型 / 造句") }
        }
        Row {
            Text("记忆 ${state.settings.historyRounds} 轮 · ${state.settings.contextSize} token", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onSettings, enabled = !state.busy) { Text("参数 / 语音") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { menu = true }, enabled = !state.busy) { Text("Skill：${state.skills.selected.name} ▾") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    (listOf(SkillStore.DEFAULT) + state.skills.skills).forEach { skill ->
                        DropdownMenuItem(text = { Text(skill.name) }, onClick = { model.selectSkill(skill.id); menu = false })
                    }
                }
            }
            TextButton(onClick = { editId = null; name = ""; instruction = ""; editor = true },
                enabled = !state.busy && state.skills.skills.size < SkillStore.MAX_SKILLS) { Text("添加 Skill") }
            TextButton(onClick = {
                editId = state.skills.selected.id; name = state.skills.selected.name
                instruction = state.skills.selected.instruction; editor = true
            }, enabled = !state.busy && state.skills.selected.id != "default") { Text("编辑") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.loaded) "模型已加载" else "请先在「模型 / 造句」导入并加载模型", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = model::clearChat, enabled = !state.busy && state.turns.isNotEmpty()) { Text("新对话") }
        }
        HorizontalDivider()
        if (state.turns.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("试着说：Hello! 帮我练习日常英语。\n\nSkill 是角色与回答规则，保存后下次打开仍可使用。", style = MaterialTheme.typography.bodyMedium)
            }
        } else LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(state.turns) { _, turn ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.align(Alignment.End).widthIn(max = 540.dp)) {
                        SelectionContainer { Text("你\n${turn.user}", Modifier.padding(12.dp)) }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.align(Alignment.Start).fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("AI", style = MaterialTheme.typography.labelSmall)
                            SelectionContainer { Text(turn.assistant.ifBlank {
                                if (turn.status == ReplyStatus.PENDING) "正在思考…" else "未生成回复"
                            }) }
                            if (turn.status == ReplyStatus.INTERRUPTED || turn.status == ReplyStatus.FAILED)
                                Text("${if (turn.status == ReplyStatus.INTERRUPTED) "已停止" else "失败"} · 此轮不会带入后续上下文", style = MaterialTheme.typography.labelSmall)
                            if (turn.assistant.isNotBlank() && turn.status != ReplyStatus.PENDING) {
                                TextButton(onClick = { audio.speakEnglish(turn.assistant, state.settings.speechRate, state.settings.speechPitch) },
                                    enabled = !state.busy) { Text("朗读英文") }
                            }
                        }
                    }
                }
            }
        }
        Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        AiSpeechControls(audio, state.settings)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                maxLines = 4, label = { Text("发送消息") }, isError = input.length > ChatContext.MAX_INPUT,
                supportingText = { Text("${input.length} / ${ChatContext.MAX_INPUT}") })
            if (state.busy) Button(onClick = model::stop) { Text("停止") }
            else Button(onClick = { audio.stop(); if (model.sendChat(input)) input = "" },
                enabled = state.loaded && input.isNotBlank() && input.length <= ChatContext.MAX_INPUT) { Text("发送") }
        }
    }
    if (editor) Dialog(onDismissRequest = { editor = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp), shape = MaterialTheme.shapes.large) {
          Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (editId == null) "添加自定义 Skill" else "编辑 Skill", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("填写角色、任务、语言和回答格式。保存或切换 Skill 会开始新对话。这里只支持提示文本，不执行脚本。")
                if (state.status.startsWith("Skill 保存失败")) Text(state.status, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Skill 名称") }, singleLine = true,
                    supportingText = { Text("${name.length} / 30") }, isError = name.length > 30)
                OutlinedTextField(value = instruction, onValueChange = { instruction = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Skill 内容") }, minLines = 4, maxLines = 8,
                    placeholder = { Text("你是我的英语口语教练。使用 A1 英语，每次只问一个问题，先纠正我的错误，再继续对话。") },
                    supportingText = { Text("${instruction.length} / ${SkillStore.MAX_INSTRUCTION}") }, isError = instruction.length > SkillStore.MAX_INSTRUCTION)
                if (editId != null) TextButton(onClick = { delete = true }) { Text("删除此 Skill") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { editor = false }) { Text("取消") }
            TextButton(onClick = { if (model.saveSkill(editId, name, instruction)) editor = false },
                enabled = !state.busy && name.isNotBlank() && name.length <= 30 && instruction.isNotBlank() && instruction.length <= SkillStore.MAX_INSTRUCTION) { Text("保存并使用") }
            }
          }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("删除这个 Skill？") },
        text = { Text("将恢复内置英语陪练，并清空当前对话。") },
        confirmButton = { TextButton(onClick = {
            if (editId?.let(model::deleteSkill) == true) { delete = false; editor = false }
        }) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("取消删除") } })
}
