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

BeeGent 把 WorkSwarm / JiuwenSwarm 的会话带到手机上。电脑继续跑任务，你用手机看进度、补充要求、处理审批，完成后查看生成的文件。

支持 Android 和 HarmonyOS，通过局域网连接你的电脑。

[💬 功能](#features) · [📦 下载](#download) · [🔌 开始使用](#connect) · [🛠️ 开发](#development) · [🎨 品牌素材](#brand)

<a id="features"></a>

## 💬 手机上能做什么

- 查看流式回复、Markdown、工具调用和子代理进度，跟进任务清单。
- 任务执行中继续发消息，编辑和调整队列，暂停或恢复发送。单 Agent 任务支持直接补充要求。
- 回答 Agent 的提问，处理权限申请和计划审批。
- 打开历史会话，接收电脑端生成的附件，预览图片和 HTML，保存或分享文件。

创建会话时，可以选择 **Agent / Team**、**Work / Code** 和 **Normal / Plan**。

<a id="开始使用"></a>
<a id="download"></a>

## 📦 下载

| 平台 | v0.2.0 | 安装方式 |
| --- | --- | --- |
| Android 8.0 及以上 | [下载 APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-debug.apk) | 可直接安装，使用 Debug 签名 |
| Android，自行签名 | [下载 Unsigned APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-unsigned.apk) | 签名后安装 |
| HarmonyOS | [下载 Unsigned HAP](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-harmonyos-unsigned.hap) | 需要配置 HarmonyOS 签名与设备授权 |

[全部下载与版本说明](https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0) · [更新记录](CHANGELOG.md)

<a id="connect"></a>

## 🔌 开始使用

1. 在电脑上启动 **WorkSwarm / JiuwenSwarm**。若服务只接受本机连接，将 [局域网转发 Skill](swarm/beegent-lan-relay.md) 交给 WorkSwarm，按说明开启手机连接。
2. 手机和电脑接入同一局域网，在 BeeGent 中填写电脑的 **IPv4 地址**。使用默认转发时，消息和下载端口可以留空。
3. 选择已有会话，或选好模式后发出第一条消息。

任务由电脑上的 Agent 执行，模型调用也由桌面后端处理。请保持电脑和桌面服务运行。

<details>
<summary>端口与连接说明</summary>

转发工具支持 macOS、Windows 和 Linux，需要 Node.js 18+，无需安装 npm 依赖。

| 用途 | 手机连接 | 电脑端服务 |
| --- | --- | --- |
| 消息 | `电脑 IP:29000` | `127.0.0.1:19000` |
| 附件下载 | `电脑 IP:25173` | `127.0.0.1:5173` |

如果桌面服务使用了其他端口，请相应调整转发配置。

手机断线后，电脑上的任务仍会继续。连接地址、草稿和待发送队列仅保留在本次运行中；断线或切换会话会清空未发送队列，已有历史可在重新连接后从服务端读取。

当前发送入口支持文本，附件用于接收电脑端生成的文件。更多说明见 [Android](android/README.md) 和 [HarmonyOS](hos/README.md) 文档。

</details>

连接采用 WS / HTTP，暂未提供登录鉴权。请在可信局域网内使用，不要将端口直接开放到公网。

<a id="development"></a>

## 🛠️ 开发与贡献

Android 客户端使用 Kotlin / Jetpack Compose，HarmonyOS 客户端使用 ArkTS / ArkUI。分别用 Android Studio 打开 `android/`、DevEco Studio 打开 `hos/`。

[构建指南](docs/BUILDING.md) · [贡献约定](CONTRIBUTING.md) · [反馈问题](https://github.com/XiaoLuoLYG/beegent/issues/new/choose) · [讨论交流](https://github.com/XiaoLuoLYG/beegent/discussions) · [安全说明](SECURITY.md)

<details>
<summary>源码目录</summary>

```text
android/    Android 客户端
hos/        HarmonyOS 客户端
swarm/      局域网转发 Skill
docs/       构建文档与品牌素材
```

默认 `android` 分支包含两端代码；`hos` 分支保留鸿蒙开发基线。

</details>

<a id="brand"></a>

## 🎨 品牌素材

[App 图标](docs/assets/brand/app-icon.png) · [主视觉](docs/assets/brand/hero.jpg) · [分享封面](docs/assets/brand/social-preview.jpg) · [下载素材包](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-brand-assets.zip)

<details>
<summary>查看品牌板</summary>

![BeeGent 品牌设计](docs/assets/brand/brand-board.jpg)

[素材说明](docs/assets/brand/README.md)

</details>

## 🙌 致谢

BeeGent 基于 [kevinCsir/beegent](https://github.com/kevinCsir/beegent) 的 HarmonyOS 客户端和转发工具开发。感谢原作者和 WorkSwarm / JiuwenSwarm 项目。

本仓库尚未声明开源许可证，来源与署名见 [NOTICE](NOTICE)。
