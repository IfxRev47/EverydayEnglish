package com.oxford.a1

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oxford.a1.audio.TtsAudioProvider
import com.oxford.a1.ui.theme.A1Theme
import com.oxford.a1.ui.worddetail.WordDetailScreen
import com.oxford.a1.ui.wordlist.WordListScreen
import com.oxford.a1.viewmodel.WordViewModel
import com.oxford.a1.ai.AiViewModel
import com.oxford.a1.ai.OfflineLearningScreen
import com.oxford.a1.ai.ChatScreen
import com.oxford.a1.ai.AiSettingsScreen
import com.oxford.a1.practice.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var audio: TtsAudioProvider
    private var refreshSpeechOnResume = false
    private var offlineModel: AiViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audio = TtsAudioProvider(this)
        val repository = (application as VocabularyApplication).repository
        setContent {
            var transferMessage by remember { mutableStateOf("") }
            var transferBusy by remember { mutableStateOf(false) }
            val exportProgress = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) lifecycleScope.launch {
                    transferBusy = true
                    try {
                        val json = repository.exportLearned()
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                                ?: error("无法写入文件")
                        }
                        transferMessage = "学习记录已导出。在新应用中导入此文件即可合并已掌握标记。"
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        transferMessage = "导出失败：${e.message}"
                    } finally { transferBusy = false }
                }
            }
            val importProgress = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) lifecycleScope.launch {
                    transferBusy = true
                    try {
                        val json = withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                                val buffer = CharArray(4096)
                                val text = StringBuilder()
                                while (true) {
                                    val count = reader.read(buffer)
                                    if (count < 0) break
                                    require(text.length + count <= 4 * 1024 * 1024) { "学习记录文件过大" }
                                    text.append(buffer, 0, count)
                                }
                                text.toString()
                            } ?: error("无法读取文件")
                        }
                        val count = repository.importLearned(json)
                        transferMessage = "已匹配并标记 $count 个词；原有已掌握标记保留。"
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        transferMessage = "导入失败：${e.message}"
                    } finally { transferBusy = false }
                }
            }
            val ai: AiViewModel = viewModel()
            val practiceModel: PracticeViewModel = viewModel()
            val daily by practiceModel.daily.collectAsStateWithLifecycle()
            var practice by rememberSaveable { mutableStateOf(true) }
            val practiceState by practiceModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(practiceModel) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) { practiceModel.refresh(); kotlinx.coroutines.delay(60_000) }
                }
            }
            SideEffect { offlineModel = ai }
            var offline by rememberSaveable { mutableStateOf(false) }
            var chat by rememberSaveable { mutableStateOf(true) }
            var settingsPage by rememberSaveable { mutableStateOf(false) }
            val aiState by ai.state.collectAsStateWithLifecycle()
            val speechRoute by rememberUpdatedState(Triple(offline, chat, settingsPage))
            LaunchedEffect(ai) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    ai.speechRequests.collect { request ->
                        if (speechRoute.first && !speechRoute.third && speechRoute.second == request.fromChat) {
                            val settings = ai.state.value.settings
                            audio.speakEnglish(request.text, settings.speechRate, settings.speechPitch)
                        }
                    }
                }
            }
            LaunchedEffect(aiState.busy) { if (aiState.busy) audio.stop() }
            LaunchedEffect(aiState.skills, aiState.turns.isEmpty()) { if (offline && chat) audio.stop() }
            val model: WordViewModel = viewModel(factory = viewModelFactory {
                initializer { WordViewModel(repository, createSavedStateHandle()) }
            })
            val state by model.state.collectAsStateWithLifecycle()
            val loadProgress by repository.progress.collectAsStateWithLifecycle()
            val audioState by audio.state.collectAsStateWithLifecycle()
            val listState = rememberLazyListState()
            var about by rememberSaveable { mutableStateOf(false) }
            var lastFilter by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(state.filter, state.loading) {
                if (!state.loading) {
                    val signature = state.filter.toString()
                    if (lastFilter != null && lastFilter != signature) listState.scrollToItem(0)
                    lastFilter = signature
                }
            }
            BackHandler(enabled = offline || (practice && practiceState.mode != PracticeMode.MENU) || !practice) {
                if (settingsPage) { audio.stop(); settingsPage = false }
                else if (offline) { audio.stop(); ai.stop(); offline = false }
                else if (practice) {
                    audio.stop(); ai.stop()
                    practiceModel.menu()
                } else if (state.selectedWord != null) { audio.stop(); model.selectWord(null) }
                else { audio.stop(); practiceModel.refresh(); practice = true }
            }
            A1Theme {
                Scaffold { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                            when {
                                offline && settingsPage -> AiSettingsScreen(ai,
                                    onBack = { audio.stop(); settingsPage = false }, onSpeechSettings = ::openSpeechSettings)
                                offline && chat -> ChatScreen(ai, audio,
                                    onBack = { audio.stop(); ai.stop(); offline = false },
                                    onModel = { audio.stop(); ai.stop(); chat = false },
                                    onSettings = { audio.stop(); settingsPage = true })
                                offline -> OfflineLearningScreen(ai, state.allWords, audio,
                                    onSettings = { audio.stop(); settingsPage = true },
                                    onChat = { audio.stop(); ai.stop(); chat = true }, onBack = { audio.stop(); ai.stop(); offline = false },
                                    backLabel = if (practice) "返回练习" else "返回词库")
                                practice && !state.loading && state.error == null -> PracticeScreen(practiceModel, ai, state.allWords, audio,
                                    onBack = { audio.stop(); ai.stop(); if (practiceState.mode == PracticeMode.MENU) practice = false else practiceModel.menu() },
                                    onModel = { audio.stop(); ai.stop(); chat = false; offline = true }, onSpeechSettings = ::openSpeechSettings,
                                    onLearned = model::confirmLearned)
                                state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (loadProgress.total > 0) {
                                        LinearProgressIndicator(progress = { loadProgress.fraction }, modifier = Modifier.width(260.dp))
                                        Text("${(loadProgress.fraction * 100).toInt()}%", Modifier.padding(top = 12.dp))
                                    } else LinearProgressIndicator(Modifier.width(260.dp))
                                    Text(loadProgress.message, Modifier.padding(16.dp))
                                }
                                state.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.error.orEmpty())
                                    Button(onClick = model::initialize) { Text("重试") }
                                }
                                state.selectedWord != null -> {
                                    val word = state.selectedWord!!
                                    WordDetailScreen(word, audioState,
                                        onBack = { audio.stop(); model.selectWord(null) },
                                        onPlay = { audio.play(word) }, onStop = audio::stop,
                                        onToggle = { model.toggleLearned(word.id) }, onSettings = ::openSpeechSettings)
                                }
                                else -> WordListScreen(state, listState, audioState,
                                    model::setQuery, model::setPartOfSpeech, model::setLevel, model::setLearnedFilter,
                                    model::toggleSort, model::resetFilters, model::selectWord, audio::play,
                                    onAbout = { about = true }, onOffline = { audio.stop(); chat = true; offline = true },
                                    today = { TodaySummary(daily, state.allWords) { audio.stop(); practiceModel.refresh(); practice = true } })
                            }
                        }
                    }
                }
                if (about) AlertDialog(onDismissRequest = { about = false },
                    title = { Text("关于这份词表") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("当前应用：$packageName")
                            Text("更换包名需迁移学习记录：先在旧应用导出，再在新应用导入。只合并已掌握标记，不清除原标记；模型、Skill 和 AI 设置不包含在此文件中。")
                            TextButton(onClick = { exportProgress.launch("everyday-learning-progress.json") }, enabled = !transferBusy && !state.loading) { Text("导出学习记录") }
                            TextButton(onClick = { importProgress.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !transferBusy && !state.loading) { Text("导入学习记录") }
                            if (transferBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (transferMessage.isNotEmpty()) Text(transferMessage)
                            Text("学习范围依据 Oxford 3000 / 5000 官方词表。A1：2026-09-19；A2：2026-09-22；B1/B2/C1：2026-10-08。当前共 ${state.allWords.size} 个不同词形。")
                            state.allWords.flatMap { it.meanings }.map { it.cefrLevel }.distinct().sorted().forEach { level ->
                                Text("$level：${state.allWords.count { word -> word.meanings.any { it.cefrLevel == level } }} 个词条")
                            }
                            Text("A1/A2 中文与英式音标为项目自编；B1/B2/C1 使用 ECDICT（上游声明 MIT）和项目补写，不保证全部为英式音标。内容未经专业审校，不是 Oxford 的释义或音标，等级对应单词与词性而非每个中文义项。ECDICT 来源：github.com/skywind3000/ECDICT；许可证随安装包保存。")
                            Text("发音来自 Android 系统 TTS，不是真人录音；优先使用已安装的离线英式语音。当前：${audioState.message}")
                            Text("词库与掌握状态保存在本机。各等级中相同词形共用掌握状态；新增词性不会取消你原来的标记。不需要账号，无广告，无打卡。")
                            Text("范围来源：oxfordlearnersdictionaries.com/wordlists/oxford3000-5000\n本项目并非 Oxford 官方应用。")
                        }
                    }, confirmButton = { TextButton(onClick = { about = false }) { Text("知道了") } },
                    dismissButton = { TextButton(onClick = ::openSpeechSettings) { Text("语音设置") } })
            }
        }
    }

    private fun openSpeechSettings() {
        refreshSpeechOnResume = true
        try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
        catch (_: ActivityNotFoundException) {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            catch (_: ActivityNotFoundException) { Toast.makeText(this, "请手动打开系统的文字转语音设置。", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (refreshSpeechOnResume) {
            refreshSpeechOnResume = false
            audio.reload()
        }
    }

    override fun onStop() {
        audio.stop()
        if (!isChangingConfigurations) offlineModel?.stop()
        super.onStop()
    }
    override fun onDestroy() { audio.close(); super.onDestroy() }
}
