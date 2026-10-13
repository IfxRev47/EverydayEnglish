# 每日英语 · A1—C1

个人英语学习 Android App，Kotlin / Jetpack Compose / Material 3 / Room / MVVM / Coroutines / Flow，最低 Android 8.0（API 26）。

当前版本 **1.9**，安装包名为 `com.oxford.everyday`。已启用 A1、A2、B1、B2、C1 词库。支持 GGUF 离线造句、短上下文 AI 聊天、自定义 Skill、模型参数调节和 AI 回复英语朗读；启动跳过未变化词库的解析，词库导入显示进度。模型加载已恢复原来的后台直接加载方式，显示等待提示，不再轮询权重百分比。

新增[背诵历史记录](docs/背诵历史记录.md)：在今日计划中按日期查看实际背过的词，关闭应用、跨日和同包名更新后保留。

## 安装更新

已经使用新包名 `com.oxford.everyday` 的用户，直接安装 `build/deliverables/everyday-1.9.apk` 覆盖更新即可，无需再次迁移。1.8 以今日背诵词表为默认主页，支持手动选词、按等级 AI 推荐和自选听写；随机回顾移入辅助入口，沿用 1.6.1 的模型加载方式，保留词库加载优化和已存数据。使用及实现说明见[今日目标与练习](docs/今日选词与自定义听写.md)。

从旧包名 `com.oxford.a1` 迁移时，两个应用的数据空间独立，必须先转移学习记录：

1. 安装 `build/deliverables/everyday-migration-old-package-1.6.apk` 覆盖旧版，在「关于」中选择「导出学习记录」。该应用显示为「每日英语（旧版迁移）」。
2. 安装 `build/deliverables/everyday-1.9.apk`，打开新应用「每日英语」，在「关于」中选择「导入学习记录」。只合并已掌握标记，不清除新应用已有标记。
3. 核对标记；模型需要从原 GGUF 文件重新导入，Skill 和 AI 设置需要手动转移。确认完成前保留旧应用。

**不要先卸载旧版，不要清除数据。** 若旧包名迁移版提示签名冲突，应使用旧版签名重新构建，不要通过卸载解决。此后相同新包名的更新可以直接覆盖安装，词库更新沿用掌握状态。详见[加载优化与包名迁移](docs/加载优化与包名迁移.md)。

## 已实现

- A1：898 个词条、1,076 组词性释义。
- A2：867 个词条、990 组词性释义。
- B1：805 个词条；B2：1,429 个词条；C1：1,314 个词条。
- 五档合计 **4,953 个不同词形、5,970 组词性释义**。各等级共用词形沿用原掌握状态。
- 中英文搜索、等级筛选、词性筛选、掌握状态筛选、字母排序。
- 详情页、分词性 IPA 和中文、掌握状态持久化。
- Android 离线英语 TTS，可反复播放；优先英式语音。
- 自定义导入一个 GGUF 指令模型，手机 CPU 离线造句，随机练习最多 5 个已掌握词；支持停止、更换模型和释放内存。
- 首页「AI 助手」进入聊天，最多携带最近 2 轮完整问答；可添加、编辑、切换和删除本地 Skill。
- 「参数 / 语音」调整采样、长度、上下文、线程数及记忆轮数；AI 输出支持手动/自动英语 TTS 朗读、语速与音调设置。
- 今日目标可调；已掌握随机回顾、TTS 单词听写、AI 句子听写与造句练习，每日按词去重统计。
- 无账号、无广告、无打卡；未实现长期错词本、熟练度或语音识别评分。

## 构建与验证

用 Android Studio 打开 `E:\english`。SDK 为 `D:\Android\SDK`，compileSdk 36.1、targetSdk 36。原项目 Gradle daemon 要求 Java 21，命令行使用 Android Studio 的 JBR：

```powershell
$env:JAVA_HOME = 'D:\Program Files\Android\Android Studio\jbr'
python tools/build_word_assets.py --check
python tools/build_advanced_packs.py --check --bundle
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

连接设备后更新安装：

```powershell
& D:\Android\SDK\platform-tools\adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```

首次 Gradle 构建/测试需要联网下载依赖；App 查询词库不需要联网，英语语音包由系统另行安装。

原生推理依赖 NDK `28.2.13676358`、CMake `3.22.1`，APK 仅支持 ARM64 设备。模型需自行准备，不包含在 APK 中。3～4B 量化模型是否可用取决于架构兼容性和手机可用内存，尚未真机验证。

## 文档和数据

- [B1 / B2 / C1 预生成 JSON](data/prepared/README.md)：805 / 1429 / 1314 个词条，已在 1.5 版启用；来源和待审校状态见说明。

- [程序结构、数据库升级与词库扩展详解](docs/程序结构与词库扩展.md)：文件职责、数据流、表结构、三个版本号、添加 B1/B2、修正释义、保留学习记录的安装方式。
- [离线模型与造句](docs/离线模型与造句.md)：模型导入、使用步骤、JNI 结构、推理参数、存储安全和真机验收。
- [AI 聊天与自定义 Skill](docs/AI聊天与自定义Skill.md)：聊天入口、短上下文规则、Skill 示例、存储与扩展位置。
- [模型参数与 AI 语音](docs/模型参数与AI语音.md)：参数范围、生效方式、自动朗读、停止和实现位置。
- [数据来源与使用边界](DATA_SOURCES.md)。
- [实际验证结果](VALIDATION.md)。

`data/packs.json` 管理数据包元信息；`data/oxford_*_scope.json` 保留官方范围快照；`data/editorial_*.txt` 保存自编内容；`tools/build_word_assets.py` 生成 `app/src/main/assets/words_*.json`。

```powershell
python tools/build_word_assets.py --level a2
python tools/build_word_assets.py --check
```

词库内容更新需要增加对应数据包的 `version`，APK 更新需要增加 `versionCode`。新增等级不用重建数据库。数据库结构变化才增加 Room 版本并编写 Migration。

## 数据说明

范围依据 [Oxford 官方词表](https://www.oxfordlearnersdictionaries.com/wordlists/oxford3000-5000)。只核对词形、词性、等级，没有批量抓取 Oxford 词典释义、音标或音频。

中文释义与英式 IPA 为自编学习内容，未经专业词典编辑审校。TTS 不是真人录音，也不按 IPA 控制同形异音词的读音。项目不是 Oxford 官方应用。

新增高级词库的可复现打包命令：`python tools/build_advanced_packs.py --bundle`。上游 ECDICT MIT 许可证位于 APK 的 `assets/licenses/ECDICT-MIT.txt`；B1/B2/C1 的中文和音标仍待编辑审校。
