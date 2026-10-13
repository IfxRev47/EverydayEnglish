package com.oxford.a1.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AiSettingsScreen(model: AiViewModel, onBack: () -> Unit, onSpeechSettings: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var draft by remember(state.settings) { mutableStateOf(state.settings) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            TextButton(onClick = onBack) { Text("返回 AI") }
            Text("模型参数与语音", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("这些参数直接用于手机内的 llama.cpp 推理，聊天和造句共用。")
            SettingSlider("随机性 Temperature", draft.temperature, 0f..1.5f, 29, "越低越稳定；0 使用贪心采样，忽略 Top-P / Top-K") { draft = draft.copy(temperature = it) }
            SettingSlider("Top-P", draft.topP, 0.1f..1f, 17, "保留累计概率内的候选词") { draft = draft.copy(topP = it) }
            SettingSlider("Top-K", draft.topK.toFloat(), 1f..100f, 98, "最多考虑多少个候选词", true) { draft = draft.copy(topK = it.roundToInt()) }
            SettingSlider("重复惩罚", draft.repeatPenalty, 1f..1.5f, 9, "1 表示不惩罚；过高可能影响句子自然度") { draft = draft.copy(repeatPenalty = it) }
            SettingSlider("最大回复 token", draft.maxTokens.toFloat(), 64f..768f, 10, "越长生成越久，并占用更多上下文预算", true) { draft = draft.copy(maxTokens = it.roundToInt()) }
            Text("上下文大小（更改后需重新加载模型）")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1024, 2048, 4096).forEach { size -> FilterChip(selected = draft.contextSize == size,
                    onClick = { draft = draft.copy(contextSize = size) }, label = { Text("$size") }) }
            }
            Text("上下文越大，占用内存越多。默认 2048；选择 1024 时较长 Skill 可能超出预算。", style = MaterialTheme.typography.bodySmall)
            SettingSlider("CPU 线程数", draft.threads.toFloat(), 1f..8f, 6, "更改后需重新加载；线程越多不一定越快", true) { draft = draft.copy(threads = it.roundToInt()) }
            Text("记忆轮数")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..2).forEach { rounds -> FilterChip(selected = draft.historyRounds == rounds,
                    onClick = { draft = draft.copy(historyRounds = rounds) }, label = { Text("$rounds 轮") }) }
            }
            HorizontalDivider()
            Text("AI 回复朗读", style = MaterialTheme.typography.titleMedium)
            Row {
                Text("生成完成后自动朗读英文", Modifier.weight(1f).padding(top = 12.dp))
                Switch(checked = draft.autoSpeak, onCheckedChange = { draft = draft.copy(autoSpeak = it) }, modifier = Modifier.testTag("autoSpeak"))
            }
            Text("使用已安装的离线英语 TTS，跳过中文、代码块与思考标签。停止或失败的回复不会自动朗读。", style = MaterialTheme.typography.bodySmall)
            SettingSlider("AI 朗读语速", draft.speechRate, 0.5f..1.5f, 19, "0.85 适合较慢地跟读") { draft = draft.copy(speechRate = it) }
            SettingSlider("AI 朗读音调", draft.speechPitch, 0.8f..1.2f, 7, "1.0 为正常音调；是否支持取决于语音引擎") { draft = draft.copy(speechPitch = it) }
            TextButton(onClick = onSpeechSettings) { Text("系统语音设置 / 下载英语语音包") }
            TextButton(onClick = { draft = AiSettings() }) { Text("恢复默认值（保存后生效）") }
        }
        Text(state.status, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { model.saveSettings(draft) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("保存参数") }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    hint: String, integer: Boolean = false, onChange: (Float) -> Unit) {
    Text("$label：${if (integer) value.roundToInt().toString() else String.format(Locale.ROOT, "%.2f", value)}")
    Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.testTag(label))
    Text(hint, style = MaterialTheme.typography.bodySmall)
}
