# B1 / B2 / C1 预生成词库

**启用更新（2026-10-08）：用户后续要求打包，1.5 版已启用三档。** 本目录保留原预生成副本；运行 `python tools/build_advanced_packs.py --bundle` 生成当前 assets，追加 `--check` 校验。只调整打包状态和许可证相对路径，词条内容与本目录一致。下面关于“提前生成、尚未启用”的描述记录此前阶段，现以本段为准。

生成日期：2026-10-08。三个 JSON 与现有 `SeedParser` / `SeedImporter` 的 schemaVersion 1 兼容，已具备 word、phonetic、分词性 chineseMeaning、cefrLevel、datasetId 与 version。

| 文件 | 单词条目 | 词性释义组合 | 范围 |
| --- | ---: | ---: | --- |
| `words_b1.json` | 805 | 905 | Oxford 5000 B1 |
| `words_b2.json` | 1429 | 1578 | Oxford 5000 B2，包含 3000 核心及扩展词 |
| `words_c1.json` | 1314 | 1421 | Oxford 5000 C1 |

不同等级可能有相同词形，不能把三档计数之和当成独立词形总数。C1 采用 Oxford 5000 范围，不把它称为 Oxford 3000 的 C1。范围按官方词表页的 `data-ox5000` 和词性读取，没有让模型判断等级。

B2 官方原始范围有 1579 行，其中 `tear` 有两行 noun（眼泪 /tɪə/、裂口 /teə/）。现有 schema 每个词形/词性只能有一条记录，因此 JSON 合并为一条 noun，并保留双读音、对应中文和结构化 `readings` 数组；另有 verb「撕」/teə/。不是丢弃其中一个义项。原始范围快照仍保留重复行。

## 为什么放在这里

用户要求提前生成，因此文件放在 `data/prepared/`，没有复制进 `app/src/main/assets/`。现有 App 会自动导入 assets 根目录的 `words_*.json`，直接放那里会立即启用新词库。本次没有修改已启用的 A1/A2 文件、包版本、Room 结构或学习记录，也没有增加 App 版本号。

## 内容来源和质量状态

- 范围：[Oxford 官方词表](https://www.oxfordlearnersdictionaries.com/wordlists/oxford3000-5000)。只提取单词、词性和等级，没有访问词典详情、复制 Oxford 释义/音标或下载音频。
- 中文与音标基础数据：[ECDICT](https://github.com/skywind3000/ECDICT)，固定提交号、原 CSV SHA-256、局部快照和编辑补丁校验和均写入 JSON。保留的输入快照为 `data/sources/ecdict_advanced_subset.json`；无需联网即可重新生成。
- 中文严格按来源词性标签提取，保留至多四个以逗号/分号分隔的释义片段；没有匹配词性时必须通过项目补写解决，不把其他词性的释义强行塞入。
- 缺失释义、缺失音标、AI/IT 大小写和部分同形异音词通过 `data/advanced_overrides.json` 独立补写。每个词性保存其内容来源，同形异音项可以有独立 phonetic。
- ECDICT 的旧式重音、长音和央元音符号转成 Unicode 写法；**不能保证全部为英式音标**。中文释义是通用学习提示，不是 Oxford 的分级义项。
- 全部条目标为 `needs-editorial-review`，包标为 `prepared-not-bundled`。字段完整、范围一致和导入测试通过，不代表数千条中文与读音已逐条经过专业审校。

## 许可边界

ECDICT 上游声明 MIT，许可证副本在 `data/licenses/ECDICT-MIT.txt`；其说明提到多个历史数据来源，底层资料的逐项权利没有由本项目独立核实。因此新内容与 App 解耦，保留来源与待审校标记，不冒充原 A1/A2 的全自编数据，也不宣称已经完成公开发布授权核查。

Oxford 官方范围的公开可见不等于授予开放再分发许可。若要公开发布应用或词库，需要确认范围及字典内容的适用许可；必要时替换相应输入快照。音频仍可使用设备离线 TTS，这些文件不带音频资源。

## 生成和检查

在项目根目录运行：

```powershell
python tools/build_advanced_packs.py
python tools/build_advanced_packs.py --check
python tools/build_word_assets.py --check
```

`build_advanced_packs.py` 只写待启用目录，不修改 `data/packs.json` 或已打包 assets。原脚本继续负责 A1/A2。需要更新范围时，可对合法获取的官方词表页面本地副本运行：

```powershell
python tools/extract_oxford_scope.py path/to/wordlist.html data/oxford_b2_scope.json --level b2 --list ox5000 --keep-homographs
```

随后核对计数、增补字典来源或编辑补丁，再重新生成；不能仅改数量常量来跳过人工确认。

## 将来启用

完成内容与适用许可核对后，将对应 JSON 放进 `app/src/main/assets/`，保持 `datasetId` 稳定（`oxford-b1`、`oxford-b2`、`oxford-c1`）。若之前未导入，版本从 1 开始；同一包以后修正内容时递增其 version。打包时也保留字典许可证，更新来源说明中的自编内容描述，并增加 APK versionCode。

现有 Repository 自动发现新 JSON，现有等级筛选自动显示新等级。通过相同包名和签名更新安装，SeedImporter 会复用已有单词 ID / learned；无需重建数据库。以后若要让一词的不同等级或词义分别记录掌握状态，需要另做学习记录表设计。

回归测试：`PreparedPacksTest` 检查全部词形/词性范围、字段完整性、异音词修正，并模拟在已有 A1/A2 和学习标记上追加全部三包，逐条核对旧 WordEntity 与释义保留、重复导入不产生变化。
