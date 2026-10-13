package com.oxford.a1.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oxford.a1.data.model.Word
import com.oxford.a1.audio.TtsAudioProvider

@Composable
fun OfflineLearningScreen(model: AiViewModel, words: List<Word>, audio: TtsAudioProvider, onSettings: () -> Unit,
    onChat: () -> Unit = {}, onBack: () -> Unit, backLabel: String = "返回词库") {
    val state by model.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.import(uri)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(backLabel) }
        OutlinedButton(onClick = onChat) { Text("进入 AI 聊天") }
        TextButton(onClick = onSettings, enabled = !state.busy) { Text("参数 / 语音") }
        Text("离线造句", style = MaterialTheme.typography.headlineMedium)
        Text("使用已掌握的单词，在手机本地生成简单例句。已掌握 ${words.count { it.learned }} 个词。")
        Text("本地模型", style = MaterialTheme.typography.titleLarge)
        Text(state.modelName ?: "尚未导入模型")
        Text("支持 llama.cpp 可加载的单文件 GGUF 指令模型。3～4B 量化模型能否运行取决于手机可用内存；也可以导入更小的模型。模型不会随 APK 提供。",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.busy) {
            Text(if (state.modelName == null) "导入 GGUF 模型" else "更换模型文件")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.loaded) OutlinedButton(onClick = { model.unload() }, enabled = !state.busy) { Text("释放内存") }
            else OutlinedButton(onClick = model::load, enabled = state.modelName != null && !state.busy) { Text("加载模型") }
            TextButton(onClick = { confirmDelete = true }, enabled = state.modelName != null && !state.busy) { Text("删除模型") }
        }
        Text(state.status, color = MaterialTheme.colorScheme.primary)
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = model::stop) { Text("停止当前操作") }
        }
        HorizontalDivider()
        Button(onClick = { audio.stop(); model.generate(words) }, enabled = state.loaded && !state.busy && words.any { it.learned }) { Text("用已掌握单词造句") }
        if (words.none { it.learned }) Text("先在词库中标记几个已掌握的单词，再回来练习。")
        if (state.targets.isNotEmpty()) Text("本轮目标词：${state.targets.joinToString(" · ")}")
        if (state.output.isNotEmpty()) SelectionContainer { Text(state.output, style = MaterialTheme.typography.bodyLarge) }
        AiSpeechControls(audio, state.settings, state.output, enabled = !state.busy)
        Text("模型可能使用少量未学词、词形变化，也可能生成错误的例句或翻译。输出仅作练习参考，不会自动更改掌握状态。离开此页面会停止当前任务；模型可手动释放。",
            style = MaterialTheme.typography.bodySmall)
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("删除本地模型？") }, text = { Text("仅删除 App 内的模型副本；单词与学习记录不受影响。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.unload(delete = true) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}
