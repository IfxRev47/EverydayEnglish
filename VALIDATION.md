# 验证记录

## GitHub 基线与语法入门（App 1.10）

语法开发前已将完整 1.9 代码提交并推送到 https://github.com/IfxRev47/EverydayEnglish，基线提交 `81a88ae1e269449383e61570ef4e637e097155b9`，远端标签 `v1.9`。llama.cpp 是固定提交 `2145525a4081d66ff1a87cf43ef809f95a85ac0c` 的子模块；本机配置、缓存、APK、模型及密钥未提交。

新增独立语法入口及 8 节从需求、状态到否定、提问、第三人称、数量表达的自编课程，按看懂、跟读、换词、拼句、独立表达推进。课程数据为 grammar_beginner.json，进度与个人表达单独保存，不修改词库或背诵历史。

完整测试、Lint 和 assembleDebug：**BUILD SUCCESSFUL**，62 项测试通过，0 失败；Lint 0 错误、21 项警告、3 项提示。最终日志：`build/grammar-final.log`。

- 新增 4 项数据/状态测试：8 课资产与词库一致性、重复课号拒绝、声明的缩写和格式匹配、错误词序及动词形式不匹配、重新学习保留完成与个人表达、拼句与独立表达门槛有效。
- 新增 Compose 流程：无需模型，进入 G01、看懂和跟读、替换、逐词拼句、输入目标句与自己的句子、确认已说出、保存并回目录显示完成，再返回今日计划。修正最初使用 FlowRow 的运行时兼容问题，改为普通 Row 分组显示。
- 原有 57 项词库、Room 迁移、掌握标记、词表、听写、历史、AI 与 UI 测试继续通过。Room 仍为版本 2，五个词库 JSON 和 libollama.so 与 1.9 APK 逐字节一致。
- APK 为 `com.oxford.everyday`、versionCode 12 / versionName 1.10、ARM64；签名 SHA-256 与前版一致：`d2c827eb0d8e37c1f1239fc8c3c0563203d70b5d1df4593df79f415a9db2ba16`。输出 `build/deliverables/everyday-1.10.apk`，可覆盖安装。
- adb 未发现连接设备，未修改手机数据，未真机验证声音或学习效果。自由表达不自动判语法对错，完成状态不代表口语熟练度。

使用与课程资料说明见[语法从词到句子](docs/语法从词到句子.md)。

## 按日期查看背诵历史（2026-10-13，App 1.9）

完整单元测试、Lint 和 assembleDebug：**BUILD SUCCESSFUL**，57 项测试通过，0 失败；Lint 0 错误、19 项警告、2 项提示。最终日志：`build/history-validation.log`。

- 新增历史数据测试：每日去重，不记录未选词；删除今日计划后历史仍保留；同词跨日分别记录，空白日期不生成记录，重开和多日跨越后保留。
- 升级测试模拟 1.8 留下的旧日期、已完成词和既有历史，验证先按原日期合并归档，再清空今日状态，历史不覆盖，自选听写保留。
- 新增 Compose 流程：空历史提示，选择 book 并确认背过，从今日计划移除，再按日期打开历史，book 与播放入口仍存在，关闭后今日计划为空。
- 原有 54 项词库、掌握标记保留、今日计划、听写、AI 和界面测试继续通过。Room 仍为版本 2；五个词库 JSON 和 libollama.so 与 1.8 APK 逐字节一致。
- APK 为 `com.oxford.everyday`、versionCode 11 / versionName 1.9、ARM64；签名证书 SHA-256 与前版一致：`d2c827eb0d8e37c1f1239fc8c3c0563203d70b5d1df4593df79f415a9db2ba16`。输出 `build/deliverables/everyday-1.9.apk`，可同包名覆盖安装。
- 本轮未进行手机安装和真机播放测试。旧版已清除的早期完成集合不能恢复，仅补存仍留在本机的记录。

操作及历史保存规则见[背诵历史记录](docs/背诵历史记录.md)。

## 今日背诵词表、自选听写与 AI 推荐（2026-10-12，App 1.8）

默认主页改为今日背诵计划，随机回顾及次数目标收进辅助区域。完整测试、Lint 和 assembleDebug：**BUILD SUCCESSFUL**，54 项测试通过、0 失败；Lint 0 错误、20 项警告、2 项提示。最终日志：`build/today-plan-home-verified.log`。

- 新增数据测试：今日词表保存重开、增删后完成数按交集计算，日期变化只清空今日计划而保留自选听写；未掌握词可进入计划和听写，两个词表队列独立，随机回顾仍仅选已掌握词；AI 推荐只接受指定等级候选池中的原词，过滤重复、越级、已掌握及虚构词。
- 新增 Compose 流程：未安排词时背诵按钮不可用；自行选择未掌握 book，查看释义并完成今日背诵，仍可明确确认掌握；另外选择 banana 作为听写词，提交前隐藏答案，核对成功后今日 book 计划仍为 1/1。原辅助目标、随机回顾、听写流程适配入口后继续通过。
- 原有词库、Room 升级保留记录、聊天、Skill、TTS、参数等测试通过。数据库版本仍为 2，五个 words JSON 与 1.7 APK 逐字节一致；libollama.so 也与 1.7 一致，模型加载方式没有变动。
- APK 为 `com.oxford.everyday`、versionCode 10 / versionName 1.8、ARM64，签名证书 SHA-256 与前版一致：`d2c827eb0d8e37c1f1239fc8c3c0563203d70b5d1df4593df79f415a9db2ba16`，可覆盖更新。输出：`build/deliverables/everyday-1.8.apk`。
- 没有连接设备，没有修改手机数据。尚未真机验证 AI 推荐和生成效果；推荐过滤保证词库成员及等级范围，不证明推荐难度或语法质量。

操作与持久化规则见[今日选词与自定义听写](docs/今日选词与自定义听写.md)。

## 今日目标、随机回顾与 AI 练习（2026-10-12，App 1.7）

执行全部单元测试、Lint 与 assembleDebug：**BUILD SUCCESSFUL**，50 项测试通过，0 失败；Lint 0 错误、18 项警告。最终日志：`build/practice-release-validation.log`。

- 新增 6 项数据与状态测试：每日计数去重和重开保留、日期变化归零而目标保留、非法目标拒绝且进度不清除、关闭目标无除零、随机队列只选已掌握词且等级匹配不重复、听写大小写/标点/缩写规则、AI 句子格式及目标词校验、未完成和跳过不计数。
- 新增 2 项 Compose 流程测试：设置目标后回到主页显示新目标，无已掌握词时提示；标记 apple 后随机回顾、听写提交并核对，完成计数增加，提交前不显示答案，原词库掌握状态保留。
- 原有 42 项词库导入、Room 升级保留标记、聊天、参数、Skill、TTS 和界面测试全部通过。UI 目标编辑使用带固定可用空间的 Dialog，修正早期 AlertDialog 输入时的布局问题；各 UI 用例独立初始化测试用练习记录，避免互相沿用计数。
- 已核验 APK 为 `com.oxford.everyday`、versionCode 9 / versionName 1.7、ARM64，签名证书 SHA-256 与 1.6.1 一致：`d2c827eb0d8e37c1f1239fc8c3c0563203d70b5d1df4593df79f415a9db2ba16`。可同包名覆盖安装。
- 五个词库 JSON 与 1.6.1 APK 逐字节一致，Room 仍为版本 2；APK 中 libollama.so 与 1.6.1 逐字节一致，模型加载没有重新加入进度轮询。每日练习使用独立 SharedPreferences，不写入 learned 标记。
- 未发现连接设备；没有安装或修改用户手机数据。模型句子生成及 TTS 真机声音未实测，自动化测试不保证小模型生成句子的语法质量。

安装包：`build/deliverables/everyday-1.7.apk`。使用、计数规则和结构说明见[今日目标与练习](docs/今日目标与练习.md)。

## 恢复原模型加载方式（2026-10-11，App 1.6.1）

根据用户模型加载闪退反馈，撤销 1.6 新增的模型进度轮询、对应 JNI 查询接口和原子进度字段，恢复后台直接调用 LlamaNative.load。原生加载回调仅保留原来的停止检查，界面恢复不定进度条。单词索引、批量导入、词库进度条和学习记录迁移功能不变。

全部单元测试、Lint 和 assembleDebug 成功，日志为 `build/model-loading-rollback.log`。APK 已核验为 `com.oxford.everyday`、versionCode 8 / versionName 1.6.1，签名与 1.6 一致，可同包名覆盖安装。输出：`build/deliverables/everyday-1.6.1.apk`。

没有连接 Android 设备，未取得崩溃日志，未确认闪退根因或真机恢复结果；本次完成的是按用户要求回退模型加载方式。

## 加载优化与包名迁移（2026-10-09，App 1.6）

执行全部单元测试、Lint 和 assembleDebug：**BUILD SUCCESSFUL**，42 项测试通过，0 失败；Lint 0 错误、15 项警告。日志：`build/loading-final.log`。之后分别构建旧包名迁移版与默认新包名版本成功，日志为 `build/migration-apk.log`、`build/everyday-apk.log`。

- 新测试验证 600 词分批进度为 250/500/600，导入不再逐词查询；已导入词库的再次启动跳过解析和导入阶段，数据库记录不变。
- 学习记录导出后在另一数据库导入，按 key 合并已掌握标记，原标记保留，重复导入安全，非法备份拒绝。原有数据库升级、追加词库、UI 和 AI 功能测试全部通过。
- APK 实际核验：新版本 `com.oxford.everyday`，迁移版 `com.oxford.a1`；均 versionCode 7 / versionName 1.6、ARM64。入口类仍为 `com.oxford.a1.MainActivity`。两个桌面名称分别为「每日英语」和「每日英语（旧版迁移）」。
- 两个 APK 签名与旧版一致，证书 SHA-256 为 `d2c827eb0d8e37c1f1239fc8c3c0563203d70b5d1df4593df79f415a9db2ba16`。最终默认输出恢复为新包名。
- APK 内包含构建自动生成的 pack_index.json、五个 words JSON 和数据许可文件。Room 表结构及词库内容未修改。模型权重进度通过 llama.cpp 回调与原子变量读取，原生代码已编译。
- adb 未发现已连接设备；没有安装或修改用户手机数据。尚未实测手机启动速度、真实模型加载进度和系统文件选择器的跨应用迁移。自动化验证不代表真机性能结论。

安装与迁移步骤见[加载优化与包名迁移](docs/加载优化与包名迁移.md)。跨包名无法直接覆盖旧应用，必须先在旧包名迁移版导出，再在新版本导入；模型、Skill 和 AI 设置不在学习记录备份中。

## A1—C1 全部打包（2026-10-08，App 1.5）

用户明确要求启用此前预生成包。已加入 B1/B2/C1 assets 和 ECDICT 许可证，更新包内来源说明；原 A1/A2 文件与旧 APK 逐字节相同，Room 仍为版本 2。

执行全部单元测试、Lint 和 assembleDebug：**BUILD SUCCESSFUL**，39 项测试全部通过，Lint 0 错误、33 项提示。日志：`build/advanced-bundled-final.log`。

- 五档合并 4953 个不同词形、5970 组词性释义；APK 内实际检查包含全部五个 JSON 和 `licenses/ECDICT-MIT.txt`。
- 新增等级筛选检查 B1 805、B2 1429、C1 1314，并打开 C1 cryptocurrency 查看中文释义。
- 从实际旧 v1 表结构升级、追加全部词库并重开数据库后，全部原 A1 记录的 ID / learned 保留；另有模拟已有 A1+A2 后追加三包的逐条保留及重复导入测试。
- APK 为 `com.oxford.a1`、versionCode 6 / versionName 1.5、ARM64；与更新前 1.4 APK 的签名证书 SHA-256 一致：`d2c827eb0d8e37c1f1239fc8c3c0563203d70b5d1df4593df79f415a9db2ba16`。
- 没有安装或更改用户手机；需覆盖安装 APK 后才在手机上追加词库。新中文与音标仍待编辑审校，来源和许可边界见 DATA_SOURCES.md。

## B1 / B2 / C1 预生成词库（2026-10-08）

本次只准备 `data/prepared/words_b1.json`、`words_b2.json`、`words_c1.json`，没有启用新 assets 或更改 App / Room 版本。

- 官方 Oxford 5000 范围：B1 805 词 / 905 行，B2 1429 词 / 1579 行，C1 1314 词 / 1421 行。使用独立运行的范围提取脚本重新核对全部快照一致。
- B2 的 tear 有两行 noun，兼容现有 schema 后合为带双读音、双中文及 readings 的一条 noun；因此 JSON 为 1578 个不同词形/词性组合。其余两档数量保持不变。
- `build_advanced_packs.py --check` 通过，所有生成文件与固定输入一致；`build_word_assets.py --check` 通过，原 A1/A2 未改变。
- `:app:testDebugUnitTest --tests com.oxford.a1.PreparedPacksTest :app:assembleDebug` 成功，3 项新增测试通过；日志 `build/prepared-packs-verified.log`。
- 测试涵盖全部范围/词性与字段完整性、异音词修正、追加三包后全部原 A1/A2 WordEntity（含 ID、learned）及释义保留、重复导入不产生变化。只操作隔离的内存数据库，没有读取或修改手机数据。
- 此轮只运行相关新增测试和 APK 编译，没有把旧版的 35 项全套测试计为本轮重新执行。
- 中文/音标采用 ECDICT 固定来源加项目补写，全部标为待编辑审校；结构测试不证明语言内容准确性或历史来源许可。详见 [预生成词库说明](data/prepared/README.md)。

## 参数调节与 AI 语音（2026-09-27，App 1.4）

最终执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`：**BUILD SUCCESSFUL**，35 项测试全部通过，Lint 0 错误、33 项提示。日志：`build/parameters-final.log`。

- 参数测试验证完整读写、非法值拒绝且保留旧设置、上下文/线程重载条件、0/1 轮历史规则。
- 语音测试验证跳过中文、代码和思考段、长英文分段、使用离线语音完整播放队列、停止后迟到回调不续播。使用 Robolectric 的 TTS/音频模拟，不代表已播放真实声音。
- 新增 Compose 操作测试：在未加载模型时调整温度、关闭记忆、开启自动朗读，保存并重新进入检查生效值。
- 原生库实际完成 ARM64 编译。参数传入线程/上下文配置、采样链和生成长度；预算根据实际上下文大小扣除用户指定输出长度。
- 原词库、升级标记保留、模型导入与 Skill 测试继续通过。A1/A2 APK 内 JSON 与更新前 1.3 APK 逐字节相同。
- APK 为 `com.oxford.a1`、versionCode 5 / versionName 1.4，ARM64；新旧 APK 签名证书 SHA-256 相同。Room 结构与模型保存路径均未改变。
- 没有连接设备。尚未验证真实模型的参数效果、速度、内存、真实语音包声音或真机自动朗读；完整使用步骤见 [模型参数与 AI 语音](docs/模型参数与AI语音.md)。

## AI 聊天与 Skill（2026-09-27，App 1.3）

最终执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`：**BUILD SUCCESSFUL**；28 项测试、0 失败；Lint 0 错误、32 项警告。日志：`build/chat-verified.log`。

- 新增 6 项数据测试：只保留最近两轮完整对话、排除失败/中断/空回复、输入长度与空字符限制、Skill 保存/编辑/删除后重读、非法保存保留旧设置、缺失选中项回退及条目数量限制。
- 新增 Compose 全流程：无模型时发送禁用，添加 Skill、编辑规则、删除并恢复内置英语陪练、返回词库。编辑器使用受限高度与滚动内容，修复初版弹窗的布局反复测量问题。
- 原有模型导入、词库与学习标记迁移测试继续通过。A1/A2 APK 内 JSON 与构建前 1.2 APK 逐字节一致，Room 版本仍为 2。
- 多消息 JNI 和模板 token 裁剪代码已完成 ARM64 交叉编译；APK 为 `com.oxford.a1`、versionCode 4 / versionName 1.3，原 debug 签名 SHA-256 不变。
- `adb devices` 无连接设备；没有实际 GGUF 模型，未执行真机多轮推理、模板裁剪运行或回答质量验证。自动化上下文测试验证 Kotlin 选轮规则，不冒充原生 tokenizer 的模型运行测试。

使用与结构详见 [AI 聊天与自定义 Skill](docs/AI聊天与自定义Skill.md)。

## 离线造句更新（2026-09-27，App 1.2）

执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`：**BUILD SUCCESSFUL**，21 项测试全部通过。Lint 0 错误、32 项警告（依赖版本、模板资源、版本目录及当前仅 ARM64、不支持 ChromeOS x86_64 等提示），未屏蔽检查。最终日志：`build/offline-verified.log`。

- ARM64 JNI 与 vendored llama.cpp 完成实际交叉编译。NDK 28.2.13676358、CMake 3.22.1，原生编译命令确认使用 `-O3 -DNDEBUG`。
- 7 项新增数据/提示测试：仅选择已掌握词、禁止空目标、流式复制与进度、无效文件头与空间不足、失败导入保留原文件、取消导入保留原文件、复制完成后原子替换。使用模拟 GGUF 头与测试载荷验证导入机制，并非实际模型权重推理测试。
- 新增 Compose 流程测试：未导入模型时进入离线页面、加载/生成按钮禁用、返回词库，无需加载主机不支持的 ARM64 库。
- 原词库、完整旧数据库迁移与学习标记保留测试继续通过。
- 新 APK 内 A1/A2 JSON 与构建前保存的 1.1 APK 逐字节一致。Room 版本仍为 2，未更改表结构或词库导入逻辑。
- APK：`com.oxford.a1`，versionCode 3、versionName 1.2，API 26 起，ABI `arm64-v8a`，约 22.18 MiB。包含 `libollama.so` 和引擎 MIT 许可证，不包含 GGUF 模型。
- 新旧 APK 的签名证书 SHA-256 均为 `d2c827eb0d8e37c1f1239fc8c3c0563203d70b5d1df4593df79f415a9db2ba16`。
- `adb devices` 无设备；没有更新安装用户手机，也未验证具体 3～4B 模型的加载、中文生成、运行内存、速度或真机取消响应。原生引擎运行需要按 [真机验收步骤](docs/离线模型与造句.md) 继续验证。

## A1 + A2 更新（2026-09-22，App 1.1）

环境：Windows、Android Studio JBR 21、SDK `D:\Android\SDK`、Gradle 9.3.1、AGP 9.1.1。

执行：

```powershell
python tools/build_word_assets.py --check
$env:JAVA_HOME = 'D:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

结果：**BUILD SUCCESSFUL**。13 项测试，0 失败；Lint 0 错误、30 项提示（依赖版本更新、原模板未使用资源、版本目录建议等）。没有隐藏或屏蔽 Lint 检查。

| 验证 | 结果 |
| --- | --- |
| A1 JSON 与编写源一致 | 898 个词，1,076 组释义 |
| A2 JSON 与官方范围快照、编写源一致 | 867 个词，990 组释义 |
| A1 文件与旧 APK 内文件逐字节比较 | 完全一致 |
| A2 与 A1 共用词形 | 75 个；合并 1,690 个词形、2,066 组释义 |
| v1→v2 实际旧结构迁移 | 通过；使用导出的 v1 schema 创建旧库 |
| 旧学习数据保留 | 所有 898 个旧 WordEntity 逐条相同，包括非连续 ID 和交替设置的已掌握／未掌握状态 |
| A1 释义保留 | 全部比较通过 |
| 新增单词 | 792 个；默认未掌握 |
| 重开数据库并重复初始化 | 无重复词条，状态、包版本与总数保持一致 |
| 异音词和大小写 | close 的 A1 / A2 读音、May / may 的展示分别保留 |
| Compose 操作流程 | 中文搜索、详情、掌握标记、筛选、A2 选择及 close 读音展示通过 |
| 数据包更新与错误回滚 | 原有 Room 回归测试通过 |
| APK 内容 | 实际 ZIP 中同时存在完整 words_a1.json 与 words_a2.json；无 mp3 / ogg / wav |
| APK 更新身份 | com.oxford.a1，versionCode 2，versionName 1.1 |
| APK 签名 | 与构建前保存的旧版 APK 证书 SHA-256 一致 |

A1 JSON SHA-256：`92a414faa95358f33edd1af61bbca01ed9920585a4a4240cbcd6aa4dd3ece3c8`。

报告位置：

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `build/a2-validation-pass.log`
- `app/build/outputs/apk/debug/app-debug.apk`

## 原 A1 阶段（2026-09-19）

现有模板、API 26 与 Room/KSP 骨架、Room 数据层、Compose 与 TTS 四个阶段均分别通过 assembleDebug。随后 10 项原版测试及最终构建通过。

## 设备与内容边界

本次 `adb devices` 没有连接设备。未安装到用户手机，没有读取、清空或修改手机数据库。用户直接更新安装新版 APK 后，第一次启动才会对手机旧库执行 Migration 并追加 A2。

Robolectric 不代替真机验收；真实 TTS 声音、设备语音包安装和 OEM 设置界面尚未人工验证。

中文释义与英式 IPA 为项目自编学习内容，尚未经过专业词典编辑审校。官方词表只用于核对词形、词性和 CEFR 范围；代码测试不能证明每个语言义项的准确性。
