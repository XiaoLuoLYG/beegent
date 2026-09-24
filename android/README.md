# beegent Android

JiuwenSwarm 的原生 Kotlin / Jetpack Compose 客户端。Android Studio 打开本目录。

与 `hos/` 共用 WebChannel 协议，不依赖鸿蒙工程、浏览器或电脑端改动。连接设置、聊天、队列和历史只保存在当前运行的内存中；服务端是会话事实源。通信诊断日志是单独的本地记录。

## 构建

需要 Android SDK 36、JDK 17+。执行 `./gradlew :app:assembleDebug`。首次运行需在连接面板输入电脑的局域网 IPv4 地址；消息端口留空为 29000，下载端口留空为 25173。手机与电脑必须能访问同一局域网，电脑端可用仓库中的 WorkSwarm 转发 Skill。

Android 工程使用独立包名 `com.xiaoluolyg.beegent`。调试签名使用 Android 默认 debug keystore，仓库不包含发布签名、密钥、缓存或 APK。

## 迁移状态

功能对照和验证边界见 [migration.md](migration.md)。
