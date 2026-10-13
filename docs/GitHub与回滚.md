# GitHub 与回滚

仓库：https://github.com/IfxRev47/EverydayEnglish

Android 源码、词库、生成工具与文档纳入版本管理。APK、Gradle/IDE 缓存、本机 SDK 配置、GGUF 模型及签名密钥不提交。llama.cpp 以子模块固定到项目使用的提交版本，不将其完整历史复制到主仓库。

首次获取项目请使用：

```powershell
git clone --recurse-submodules https://github.com/IfxRev47/EverydayEnglish.git
```

已克隆但缺少原生依赖时：

```powershell
git submodule update --init --recursive
```

用 Android Studio 设置自己的 SDK 路径，生成本机 local.properties。Gradle 构建说明见 README。默认构建使用本机 debug 签名，新电脑的 debug 签名可能不同；要覆盖已有手机应用，应继续使用原签名，签名密钥另行安全保存，不上传 GitHub。

1.9 稳定基线标签为 v1.9。查看旧版本可以创建独立工作目录：

```powershell
git worktree add --detach ../EverydayEnglish-v1.9 v1.9
git -C ../EverydayEnglish-v1.9 submodule update --init --recursive
```

回滚代码不会自动回滚手机数据。Android 通常不允许较低 versionCode 覆盖安装；确需用旧代码构建更新包时，应提高 versionCode 并保持原包名与签名，不要通过卸载来保留数据。本项目词库升级与学习记录存储说明见 docs 下相关文档。

这份仓库不包含用户手机上的数据库、模型或学习记录。
