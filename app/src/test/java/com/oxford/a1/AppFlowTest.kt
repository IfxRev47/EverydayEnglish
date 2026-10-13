package com.oxford.a1

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = VocabularyApplication::class, qualifiers = "w411dp-h891dp")
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetOnlyTestPracticeProgress() {
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[com.oxford.a1.practice.PracticeViewModel::class.java]
        model.getApplication<android.app.Application>().getSharedPreferences("study-progress", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        model.refresh()
        model.menu()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("返回词库").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("返回词库").performClick()
    }

    @Test fun todayGoalsCanBeAdjustedAndEmptyPracticeShowsGuidance() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("今日目标 · 开始练习").performClick()
        compose.onNodeWithText("更多练习（随机回顾）").performScrollTo().performClick()
        compose.onNodeWithText("调整辅助练习目标").performScrollTo().performClick()
        compose.onNodeWithText("随机回顾目标").performTextReplacement("12")
        compose.onNodeWithText("听写目标").performTextReplacement("4")
        compose.onNodeWithText("造句练习目标").performTextReplacement("2")
        compose.onNodeWithText("保存目标").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("随机回顾 0/12 · 听写 0/4 · 造句练习 0/2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("随机回顾 0/12 · 听写 0/4 · 造句练习 0/2").assertExists()
        compose.onNodeWithText("已掌握随机回顾").performScrollTo().performClick()
        compose.onNodeWithText("这个范围内还没有已掌握单词，请先标记单词，或切换等级。").assertExists()
        compose.onNodeWithText("返回今日练习").performClick()
        compose.onNodeWithText("返回词库").performScrollTo().performClick()
        compose.onNodeWithText("今日背诵 0/0 个 · 点击选词或开始背诵").assertExists()
    }

    @Test fun learnedWordReviewAndDictationCountWithoutChangingMastery() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索英文或中文").performTextInput("apple")
        val card = hasText("apple") and hasSetTextAction().not()
        compose.waitUntil(10_000) { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(card).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("标记为已掌握").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("标记为已掌握").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("已掌握 · 点击改为未掌握").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("‹ 返回词表").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("今日目标 · 开始练习").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("今日目标 · 开始练习").performClick()
        chooseWords("选择 / 编辑今日单词", "apple")
        compose.onNodeWithText("更多练习（随机回顾）").performScrollTo().performClick()
        compose.onNodeWithText("已掌握随机回顾").performScrollTo().performClick()
        compose.onNodeWithText("apple").assertExists()
        compose.onNodeWithText("查看释义").performClick()
        compose.onNodeWithText("记得 · 完成回顾").performScrollTo().performClick()
        compose.onNodeWithText("返回今日练习").performScrollTo().performClick()
        compose.onNodeWithText("随机回顾 1/20 · 听写 0/5 · 造句练习 0/3").assertExists()
        compose.onNodeWithText("单词听写").performScrollTo().performClick()
        compose.onAllNodesWithText("apple").assertCountEquals(0)
        compose.onNodeWithText("听到的英文").performTextInput("APPLE")
        compose.onNodeWithText("提交并核对").performScrollTo().performClick()
        compose.onNodeWithText("听写正确").assertExists()
        compose.onNodeWithText("返回今日练习").performScrollTo().performClick()
        compose.onNodeWithText("随机回顾 1/20 · 听写 1/5 · 造句练习 0/3").assertExists()
        compose.onNodeWithText("返回词库").performScrollTo().performClick()
        compose.onNode(card and hasText("已掌握")).assertExists()
    }

    private fun chooseWords(button: String, word: String) {
        compose.onNodeWithText(button).performScrollTo().performClick()
        compose.onNodeWithText("搜索待选英文或中文").performTextInput(word)
        val item = hasText(word) and hasSetTextAction().not()
        compose.waitUntil(10_000) { compose.onAllNodes(item).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(item).performClick()
        compose.onNodeWithText("保存词表").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("搜索待选英文或中文").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun historyShowsCompletedWordsEvenAfterRemovingThemFromTodaysPlan() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("今日目标 · 开始练习").performClick()
        compose.onNodeWithText("查看背诵记录").performClick()
        compose.onNodeWithText("还没有背诵记录。完成今日背诵并确认“今日已背过”后，会在这里显示。").assertExists()
        compose.onNodeWithText("关闭记录").performClick()
        chooseWords("选择 / 编辑今日单词", "book")
        compose.onNodeWithText("开始今日背诵").performScrollTo().performClick()
        compose.onNodeWithText("查看释义").performClick()
        compose.onNodeWithText("今日已背过").performScrollTo().performClick()
        compose.onNodeWithText("返回今日练习").performScrollTo().performClick()
        chooseWords("选择 / 编辑今日单词", "book")
        compose.onNodeWithText("查看背诵记录").performScrollTo().performClick()
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[com.oxford.a1.practice.PracticeViewModel::class.java]
        val dateLabel = "${model.daily.value.date} · 背过 1 个词"
        compose.onNodeWithText(dateLabel).performClick()
        compose.onNodeWithText("book").assertExists()
        compose.onNodeWithText("播放发音").assertExists()
        compose.onNodeWithText("返回日期列表").performClick()
        compose.onNodeWithText(dateLabel).assertExists()
        compose.onNodeWithText("关闭记录").performClick()
        compose.onNodeWithText("开始今日背诵").performScrollTo().assertIsNotEnabled()
    }

    @Test fun todayPlanAndCustomDictationUseSeparatelySelectedUnlearnedWords() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("今日目标 · 开始练习").performClick()
        compose.onNodeWithText("开始今日背诵").assertIsNotEnabled()
        chooseWords("选择 / 编辑今日单词", "book")
        compose.onNodeWithText("开始今日背诵").performScrollTo().performClick()
        compose.onNodeWithText("book").assertExists()
        compose.onNodeWithText("查看释义").performClick()
        compose.onNodeWithText("今日已背过").performScrollTo().performClick()
        compose.onNodeWithText("确认已掌握此词").assertExists()
        compose.onNodeWithText("返回今日练习").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("今日已背 1/1 个", substring = true).fetchSemanticsNodes().isNotEmpty() }
        chooseWords("选择 / 编辑听写单词", "banana")
        compose.onNodeWithText("单词听写").performScrollTo().performClick()
        compose.onAllNodesWithText("banana").assertCountEquals(0)
        compose.onNodeWithText("听到的英文").performTextInput("banana")
        compose.onNodeWithText("提交并核对").performScrollTo().performClick()
        compose.onNodeWithText("听写正确").assertExists()
        compose.onNodeWithText("正确答案：banana").assertExists()
        compose.onNodeWithText("返回今日练习").performScrollTo().performClick()
        compose.onNodeWithText("今日已背 1/1 个", substring = true).assertExists()
        compose.onNodeWithText("返回词库").performScrollTo().performClick()
        compose.onNodeWithText("今日背诵 1/1 个 · 点击选词或开始背诵").assertExists()
    }

    @Test fun newLevelsCanBeFilteredAndC1MeaningOpened() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("全部等级 ▾").performClick()
        compose.onNodeWithText("B1").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("805 个").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("B1 ▾").performClick()
        compose.onNodeWithText("B2").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1429 个").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("B2 ▾").performClick()
        compose.onNodeWithText("C1").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1314 个").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索英文或中文").performTextInput("cryptocurrency")
        val card = hasText("cryptocurrency") and hasSetTextAction().not()
        compose.waitUntil(10_000) { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(card).performClick()
        compose.onNodeWithText("加密货币").assertIsDisplayed()
    }

    @Test fun modelAndSpeechSettingsCanBeSavedWithoutLoadingModel() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("AI 助手").performClick()
        compose.onNodeWithText("参数 / 语音").performClick()
        compose.onNodeWithTag("随机性 Temperature").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithText("0 轮").performScrollTo().performClick()
        compose.onNodeWithTag("autoSpeak").performScrollTo().performClick()
        compose.onNodeWithText("保存参数").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("参数已保存，下次生成使用新参数").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("返回 AI").performClick()
        compose.onNodeWithText("记忆 0 轮 · 2048 token").assertIsDisplayed()
        compose.onNodeWithText("参数 / 语音").performClick()
        compose.onNodeWithText("随机性 Temperature：0.50").assertIsDisplayed()
        compose.onNodeWithTag("autoSpeak").performScrollTo().assertIsOn()
    }

    @Test fun chatSkillCanBeCreatedEditedAndDeletedWithoutAModel() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("AI 助手").performClick()
        compose.onNodeWithText("发送").assertIsNotEnabled()
        compose.onNodeWithText("添加 Skill").performClick()
        compose.onNodeWithText("Skill 名称").performTextInput("口语教练")
        compose.onNodeWithText("Skill 内容").performTextInput("用简单英语提问。")
        compose.onNodeWithText("保存并使用").performClick()
        compose.onNodeWithText("Skill：口语教练 ▾").assertIsDisplayed()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("Skill 内容").performTextReplacement("先纠正语法，再简短回答。")
        compose.onNodeWithText("保存并使用").performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("先纠正语法，再简短回答。").assertIsDisplayed()
        compose.onNodeWithText("删除此 Skill").performScrollTo().performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.onNodeWithText("Skill：英语陪练 ▾").assertIsDisplayed()
        compose.onNodeWithText("返回词库").performClick()
        compose.onNodeWithText("4953 个基础词汇").assertIsDisplayed()
    }

    @Test fun offlinePageWorksWithoutLoadingNativeLibraryAndReturnsToWords() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("AI 助手").performClick()
        compose.onNodeWithText("模型 / 造句").performClick()
        compose.onNodeWithText("尚未导入模型").assertIsDisplayed()
        compose.onNodeWithText("加载模型").assertIsNotEnabled()
        compose.onNodeWithText("用已掌握单词造句").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("返回词库").performScrollTo().performClick()
        compose.onNodeWithText("4953 个基础词汇").assertIsDisplayed()
    }

    @Test fun firstLaunchSearchDetailAndLearnedFilter() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("4953 个基础词汇").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索英文或中文").performTextInput("苹果")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("apple").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("apple").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("词性与释义").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("词性与释义").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("标记为已掌握").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("已掌握 · 点击改为未掌握").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("‹ 返回词表").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("清除", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("清除", useUnmergedTree = true).performClick()
        compose.onNodeWithText("未掌握").performClick()
        compose.onNodeWithText("搜索英文或中文").performTextInput("apple")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("没有找到匹配的单词").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("已掌握", useUnmergedTree = true).performClick()
        val wordCardText = hasText("apple") and hasSetTextAction().not()
        compose.waitUntil(10_000) { compose.onAllNodes(wordCardText).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(wordCardText).assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("清除", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("清除", useUnmergedTree = true).performClick()
        compose.onNodeWithText("全部").performClick()
        compose.onNodeWithText("全部等级 ▾").performClick()
        compose.onNodeWithText("A2").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("867 个").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索英文或中文").performTextInput("close")
        val closeCard = hasText("close") and hasSetTextAction().not()
        compose.waitUntil(10_000) { compose.onAllNodes(closeCard).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(closeCard).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("近的；亲密的").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("近的；亲密的").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("/kləʊs/").assertIsDisplayed()
    }
}
