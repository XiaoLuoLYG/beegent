<p align="center">
  <img src="docs/assets/brand/app-icon.png" width="88" alt="BeeGent 蜜蜂图标" />
</p>

<h1 align="center">BeeGent</h1>
<p align="center"><strong>电脑上的 AI 继续工作，手机上的你随时接手。</strong></p>
<p align="center">WorkSwarm / JiuwenSwarm 的原生移动伴侣 · Android + HarmonyOS</p>

<p align="center">
  <a href="https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0"><img alt="下载 v0.2.0 预览版" src="https://img.shields.io/badge/Download-v0.2.0_preview-F6BE27?style=flat-square&amp;labelColor=24221F" /></a>
  <a href="https://github.com/XiaoLuoLYG/beegent/actions/workflows/android-build.yml"><img alt="Android 编译状态" src="https://github.com/XiaoLuoLYG/beegent/actions/workflows/android-build.yml/badge.svg?branch=android" /></a>
  <img alt="原生 Android 和 HarmonyOS" src="https://img.shields.io/badge/Native-Android_%2B_HarmonyOS-B8A2ED?style=flat-square&amp;labelColor=24221F" />
</p>

<p align="center">
  <a href="#开始使用">开始使用</a> ·
  <a href="https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0">下载</a> ·
  <a href="docs/BUILDING.md">构建</a> ·
  <a href="https://github.com/XiaoLuoLYG/beegent/discussions">交流</a> ·
  <a href="README.en.md">English</a>
</p>

![BeeGent — Your agents. Within reach.](docs/assets/brand/hero.jpg)

**把任务交给电脑，把进展带在身边。** BeeGent 通过局域网连接桌面 WorkSwarm / JiuwenSwarm：看流式回复和工具进度、补充下一条指令、回应提问与审批、接收生成的文件。Agent 在电脑上执行，手机提供原生交互。

> 当前为 **v0.2.0 预览版**。下载包经过编译检查，完整业务链路仍待真机验收。上方是品牌插画，非运行界面截图。

## 不只是一扇聊天窗口

| 你要做的事 | BeeGent 提供的入口 |
| --- | --- |
| 跟进正在执行的工作 | 流式消息、Markdown、工具步骤、子代理动态和任务清单 |
| 切换工作方式 | Agent / Team、Work / Code、Normal / Plan |
| 想起一句补充 | 消息排队、调整顺序、暂停与恢复；单 Agent 支持补充当前任务 |
| 做关键决定 | 回答问题、权限审批、计划审批；历史审批只读 |
| 接住工作结果 | 接收附件、图片预览、HTML 预览、保存与系统分享 |
| 回到之前的会话 | 从服务端读取会话与分页历史，兼容游标和页码协议 |

两端采用原生技术：**Kotlin / Jetpack Compose** 与 **ArkTS / ArkUI**。会话以服务端为准，手机不将历史上下文反复回传。

## 开始使用

### 1 · 下载适合你的版本

| 平台 | 下载 | 使用说明 |
| --- | --- | --- |
| Android 8.0+ | [Debug APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-debug.apk) | 带调试签名，可手动安装；不是正式签名版本 |
| Android · 自行签名 | [Unsigned APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-unsigned.apk) | Release 构建，需要先签名才能安装 |
| HarmonyOS · 开发者 | [Unsigned HAP](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-harmonyos-unsigned.hap) | 需要有效的 HarmonyOS 签名与设备授权；不是点击即装包 |

[全部发布文件与 SHA-256 校验值 →](https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0)

### 2 · 让桌面 Agent 可连接

启动桌面 WorkSwarm / JiuwenSwarm。若服务仅监听本机，将 [局域网转发 Skill](swarm/beegent-lan-relay.md) 交给 WorkSwarm，按文档启动转发。它使用 Node.js 18+，支持 macOS、Windows、Linux，无需 npm 依赖。

### 3 · 手机填入电脑地址

手机与电脑连接同一可信局域网。在 BeeGent 的连接面板填入电脑的 **IPv4 地址**；使用默认转发时，消息和下载端口可以留空。

| 手机连接 | 默认桌面目标 |
| --- | --- |
| `电脑 IP:29000` · 消息 | `127.0.0.1:19000` · WebChannel |
| `电脑 IP:25173` · 附件 | `127.0.0.1:5173` · 下载服务 |

桌面端可能改用其他端口，以实际启动信息为准。连接后选择已有会话，或选好模式后发送第一条消息。

## 使用边界，提前说清

- **需要桌面后端。** BeeGent 不在手机本地运行模型或 Agent；断开手机连接也不等于终止电脑任务。
- **仅面向可信局域网。** 当前使用明文 WS / HTTP，没有独立登录与 token 配置，不应直接暴露到公网。
- **会话数据来自服务端。** 地址、会话、草稿和队列仅在当前运行内存中；断线或切换会话会清空未发送队列。通信诊断日志单独存储，可导出、清空。
- **附件方向是电脑到手机。** 当前发送入口是文本，不含手机文件上传；历史读取不等于跨客户端实时同步。
- **预览版不代表已完成验收。** 详见 [Android 状态](android/migration.md) 与 [HarmonyOS 验证记录](hos/docs/validation.md)。

## 参与构建

```text
android/    Kotlin + Jetpack Compose 原生客户端
hos/        ArkTS + ArkUI 原生客户端
swarm/      局域网转发 Skill 与可分享的单文件文档
docs/       构建、发布与品牌素材
```

默认 `android` 分支包含两端代码；`hos` 保留鸿蒙开发基线。Android Studio 打开 `android/`，DevEco Studio 打开 `hos/`。

[构建指南](docs/BUILDING.md) · [贡献约定](CONTRIBUTING.md) · [问题反馈](https://github.com/XiaoLuoLYG/beegent/issues/new/choose) · [安全说明](SECURITY.md) · [更新记录](CHANGELOG.md)

## 认识这只蜜蜂

蜂蜜黄代表行动，淡紫翅膀延续 WorkSwarm 的亲和力，身体上的 **B** 形纹路属于 BeeGent。下载 [App 图标](docs/assets/brand/app-icon.png)、[主视觉](docs/assets/brand/hero.jpg)、[分享封面](docs/assets/brand/social-preview.jpg) 或查看 [品牌板](docs/assets/brand/brand-board.jpg)。

<details>
<summary>展开品牌视觉</summary>

![BeeGent 品牌系统](docs/assets/brand/brand-board.jpg)

品牌图为 AI 辅助生成的设计素材；其中的设备画面是视觉示意。生成说明见 [品牌文件](docs/assets/brand/README.md)。

</details>

## 来源与许可

本项目基于 [kevinCsir/beegent](https://github.com/kevinCsir/beegent) 的 HarmonyOS 客户端与转发工具，继续开发 Android 客户端及 BeeGent 品牌。感谢原作者与 WorkSwarm / JiuwenSwarm 项目。

本仓库目前未声明开源许可证；公开访问不代表额外授予代码或上游素材的使用许可。来源说明见 [NOTICE](NOTICE)。

如果 BeeGent 对你有用，欢迎点一个 Star，或在 Discussions 分享你的手机工作流。
