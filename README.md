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

![BeeGent · Your agents. Within reach.](docs/assets/brand/hero.jpg)

电脑上的 Agent 执行任务时，你可以在手机上查看进展、补充要求、处理审批，或接收它生成的文件。BeeGent 通过局域网连接 WorkSwarm / JiuwenSwarm，提供 Android 和 HarmonyOS 原生客户端。

> 当前为 **v0.2.0 预览版**。下载包已通过编译检查，完整业务流程仍待真机验收。上方是品牌插画，非应用截图。

[💬 功能](#features) · [📦 下载](#download) · [🔌 连接](#connect) · [❓ 常见问题](#faq) · [🛠️ 开发文档](#development) · [🎨 品牌素材](#brand)

<a id="features"></a>

## 💬 用手机跟进工作

### 看任务做到哪一步

流式回复会随任务推进逐步显示。你也可以查看 Markdown 正文、工具调用、子代理动态和任务清单。

### 继续安排任务

创建会话时，可以选择 Agent / Team、Work / Code、Normal / Plan。任务执行中，新消息先进入队列，你可以编辑内容、调整顺序、暂停或恢复发送。单 Agent 任务还支持直接补充当前要求。

### 回应问题与审批

在手机上回答 Agent 的提问，处理权限申请和计划审批。历史记录中的审批仅供查看，不能重复提交。

### 查看历史和工作结果

打开已有会话，继续读取服务端的历史消息。收到附件后，可以预览图片和 HTML，保存文件，或通过系统分享给其他应用。

<a id="开始使用"></a>
<a id="download"></a>

## 📦 下载 BeeGent

Android 用户可以下载带调试签名的 APK 安装体验。未签名的 APK 和 HAP 则适合需要自行签名的开发者。

| 你的设备或用途 | 下载 v0.2.0 | 安装前需要知道 |
| --- | --- | --- |
| Android 8.0 及以上 | [下载 Debug APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-debug.apk) | 可手动安装，使用调试签名 |
| Android，自行签名 | [下载 Unsigned APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-unsigned.apk) | Release 构建，签名后才能安装 |
| HarmonyOS | [下载 Unsigned HAP](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-harmonyos-unsigned.hap) | 需要有效的 HarmonyOS 签名与设备授权 |

[查看全部发布文件、构建信息与 SHA-256 校验值](https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0)

<a id="connect"></a>

## 🔌 连接你的电脑

安装后，需要连接运行 WorkSwarm / JiuwenSwarm 的电脑，由桌面后端执行任务和调用模型。

### 1. 启动桌面服务

打开电脑上的 WorkSwarm / JiuwenSwarm。若它只接受本机连接，将 [局域网转发 Skill](swarm/beegent-lan-relay.md) 交给 WorkSwarm，按文档启动转发。

转发工具支持 macOS、Windows 和 Linux，需要 Node.js 18+，无需安装 npm 依赖。

### 2. 填入电脑地址

让手机和电脑接入同一可信局域网。在 BeeGent 的连接面板填入电脑的 IPv4 地址；使用默认转发时，消息和下载端口都可以留空。

### 3. 打开会话

连接后，可以选择已有会话，也可以选好工作模式后发送第一条消息，创建新会话。

<details>
<summary>需要手动填写端口？查看默认配置</summary>

| 用途 | 手机连接的地址 | 转发到电脑上的服务 |
| --- | --- | --- |
| 消息 | `电脑 IP:29000` | `127.0.0.1:19000`，WebChannel |
| 附件 | `电脑 IP:25173` | `127.0.0.1:5173`，下载服务 |

桌面端可能使用其他端口，请以实际启动信息为准，并相应调整转发配置。

</details>

> 当前连接使用明文 WS / HTTP，没有独立登录或 token 配置。请只在可信局域网内使用，不要把这些端口直接开放到公网。

<a id="faq"></a>

## ❓ 使用中可能遇到的问题

<details>
<summary>手机断开连接，电脑上的任务会停吗？</summary>

不会。任务由电脑端执行，手机断线不会终止任务。若要停止任务，需要向后端发出停止请求；结果不确定时，请在电脑端确认。

</details>

<details>
<summary>重新打开 App，会恢复连接地址和草稿吗？</summary>

不会。地址、会话状态、草稿和队列只保存在当前运行内存中。断线或切换会话会清空未发送的队列，请留意尚未发出的消息。

重新连接后，仍可从服务端读取已有会话和历史。

通信诊断日志是单独的本地记录，可以导出或清空。

</details>

<details>
<summary>历史会话会和其他客户端实时同步吗？</summary>

BeeGent 从服务端读取会话与历史消息，但历史读取不等于跨客户端实时同步。客户端兼容游标和页码两种分页协议，也不会把已读取的历史上下文重复回传。

</details>

<details>
<summary>能把手机里的文件发给 Agent 吗？</summary>

目前发送入口只支持文本。附件功能用于接收电脑端生成的文件，尚不支持从手机上传文件。

</details>

<details>
<summary>这个预览版验证到了哪一步？</summary>

v0.2.0 发布包经过源码、编译和文件校验，连接、聊天、队列、审批及附件等完整业务流程仍待真机验收。本次发布没有运行自动测试或真机 UI 测试。

具体记录见 [Android 状态](android/migration.md) 和 [HarmonyOS 验证记录](hos/docs/validation.md)。

</details>

<a id="development"></a>

## 🛠️ 开发与贡献

| 目录 | 内容 | 用什么打开 |
| --- | --- | --- |
| [`android/`](android/) | Kotlin + Jetpack Compose 客户端 | Android Studio |
| [`hos/`](hos/) | ArkTS + ArkUI 客户端 | DevEco Studio |
| [`swarm/`](swarm/) | 局域网转发 Skill 与单文件文档 | 文本编辑器 |
| [`docs/`](docs/) | 构建、发布与品牌素材 | 文本编辑器 |

默认 `android` 分支包含两端代码，`hos` 分支保留鸿蒙开发基线。请用对应 IDE 打开平台目录。

| 你想做什么 | 从这里开始 |
| --- | --- |
| 自己编译安装包 | [构建指南](docs/BUILDING.md) |
| 提交代码或文档改进 | [贡献约定](CONTRIBUTING.md) |
| 报告问题 | [创建 Issue](https://github.com/XiaoLuoLYG/beegent/issues/new/choose) |
| 提问或分享使用方式 | [Discussions](https://github.com/XiaoLuoLYG/beegent/discussions) |
| 报告安全问题 | [安全说明与私密报告入口](SECURITY.md) |
| 查看版本变化 | [更新记录](CHANGELOG.md) |

<a id="brand"></a>

## 🎨 图标与品牌素材

BeeGent 的图标使用蜂蜜黄和淡紫色，蜜蜂身上的纹路藏着一个字母 B。

[App 图标](docs/assets/brand/app-icon.png) · [主视觉](docs/assets/brand/hero.jpg) · [分享封面](docs/assets/brand/social-preview.jpg) · [下载素材包](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-brand-assets.zip)

<details>
<summary>展开查看品牌板</summary>

![BeeGent 品牌系统](docs/assets/brand/brand-board.jpg)

这些图片由 AI 辅助生成，设备画面是设计示意，不是应用运行截图。[查看生成说明](docs/assets/brand/README.md)。

</details>

## 🙌 来源与许可

BeeGent 基于 [kevinCsir/beegent](https://github.com/kevinCsir/beegent) 的 HarmonyOS 客户端和转发工具，继续开发 Android 客户端及品牌素材。感谢原作者和 WorkSwarm / JiuwenSwarm 项目的工作。

本仓库尚未声明开源许可证。公开访问不代表额外授予代码或上游素材的使用许可，来源与署名见 [NOTICE](NOTICE)。

如果 BeeGent 帮上了忙，欢迎留一个 Star；遇到问题或有新的使用想法，可以到 [Discussions](https://github.com/XiaoLuoLYG/beegent/discussions) 聊聊。
