<p align="center"><img src="docs/assets/brand/app-icon.png" width="88" alt="BeeGent bee icon" /></p>
<h1 align="center">BeeGent</h1>
<p align="center"><strong>Your agents. Within reach.</strong></p>
<p align="center">A native mobile companion for WorkSwarm / JiuwenSwarm.</p>
<p align="center"><a href="README.md">简体中文</a> · <a href="https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0">Download v0.2.0 preview</a> · <a href="docs/BUILDING.md">Build</a> · <a href="https://github.com/XiaoLuoLYG/beegent/discussions">Discuss</a></p>

![BeeGent · Your agents. Within reach.](docs/assets/brand/hero.jpg)

Follow your desktop agents from your phone. BeeGent connects to WorkSwarm / JiuwenSwarm over your local network, so you can check progress, send instructions, respond to approvals and receive files. Both Android and HarmonyOS clients use native UI frameworks.

> **v0.2.0 is a preview release.** Packages have passed compilation checks; full workflows still need device acceptance. The hero is a brand illustration, not an app screenshot.

[💬 Features](#features) · [📦 Downloads](#download) · [🔌 Connect](#connect) · [❓ FAQ](#faq) · [🛠️ Development](#development) · [🎨 Brand assets](#brand)

<a id="features"></a>

## 💬 Follow work from your phone

### See how a task is progressing

Read streaming replies and Markdown, follow tool calls and subagent activity, and check the task list as work continues.

### Keep tasks moving

Choose Agent / Team, Work / Code and Normal / Plan when creating a session. While a task is running, new messages go into a queue you can edit, reorder, pause or resume. You can also send extra instructions directly to a running single-agent task.

### Respond to questions and approvals

Answer questions, review permission requests and approve plans on your phone. Approvals in historical messages are read-only and cannot be submitted again.

### Read history and receive results

Open an existing session to read its history from the server. When files arrive, you can preview images and HTML, save them, or share them with another app.

<a id="download"></a>

## 📦 Download BeeGent

For Android, the debug APK is ready to install. The unsigned APK and HAP are for developers who want to use their own signing setup.

| Device or use | Download v0.2.0 | Before installing |
| --- | --- | --- |
| Android 8.0+ | [Debug APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-debug.apk) | Install manually; uses a debug signature |
| Android, with your own signature | [Unsigned APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-unsigned.apk) | Release build; sign it first |
| HarmonyOS | [Unsigned HAP](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-harmonyos-unsigned.hap) | Requires valid HarmonyOS signing and device authorization |

[All release files, build information and SHA-256 checksums](https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0)

<a id="connect"></a>

## 🔌 Connect to your desktop

BeeGent needs a computer running WorkSwarm / JiuwenSwarm. The desktop backend runs the tasks and calls the model.

### 1. Start the desktop service

Open WorkSwarm / JiuwenSwarm. If it only accepts connections from localhost, use the included [LAN relay Skill](swarm/beegent-lan-relay.md) to make it reachable from your phone.

The relay supports macOS, Windows and Linux. It requires Node.js 18+, with no npm dependencies.

### 2. Enter your computer's address

Connect your phone and computer to the same trusted LAN. Enter the computer's IPv4 address in BeeGent. With the default relay, you can leave both port fields blank.

### 3. Open a session

Choose an existing session, or select your work modes and send a first message to create one.

<details>
<summary>Need to enter ports manually? View the defaults</summary>

| Purpose | Phone connects to | Relay forwards to |
| --- | --- | --- |
| Messages | `Computer IP:29000` | `127.0.0.1:19000`, WebChannel |
| Attachments | `Computer IP:25173` | `127.0.0.1:5173`, download service |

Your desktop may use different ports. Check its startup information and adjust the relay configuration accordingly.

</details>

> Connections currently use plaintext WS / HTTP, without a separate login or token setting. Use a trusted LAN and do not expose these ports directly to the internet.

<a id="faq"></a>

## ❓ Common questions

<details>
<summary>Does disconnecting my phone stop the desktop task?</summary>

No. The desktop runs the task independently of your phone's connection. To stop it, send a stop request to the backend. If the result is unclear, check on the desktop.

</details>

<details>
<summary>Will the app restore my connection address and drafts?</summary>

No. Connection settings, session state, drafts and queues stay in memory for the current run. Disconnecting or switching sessions clears unsent queued messages.

After reconnecting, you can still read existing sessions and history from the server.

Diagnostic logs are separate local files that you can export or clear.

</details>

<details>
<summary>Does history stay in sync with other clients?</summary>

BeeGent reads sessions and history from the server. This does not provide continuous synchronization with other clients. It supports cursor and page-based pagination without sending previously loaded conversation history back to the server.

</details>

<details>
<summary>Can I upload a file from my phone?</summary>

Sending is currently text-only. Attachments travel from the desktop to the phone; phone uploads are not implemented.

</details>

<details>
<summary>What has been verified for this preview?</summary>

The v0.2.0 release went through source review, compilation and file checks. Complete connection, chat, queue, approval and attachment workflows still need device acceptance. No automated or device UI tests were run for this release.

See [Android status](android/migration.md) and [HarmonyOS validation records](hos/docs/validation.md).

</details>

<a id="development"></a>

## 🛠️ Development and contributions

| Directory | Contents | Open with |
| --- | --- | --- |
| [`android/`](android/) | Kotlin + Jetpack Compose client | Android Studio |
| [`hos/`](hos/) | ArkTS + ArkUI client | DevEco Studio |
| [`swarm/`](swarm/) | LAN relay Skill and single-file guide | A text editor |
| [`docs/`](docs/) | Build, release and brand files | A text editor |

The default `android` branch contains both clients. The `hos` branch preserves the HarmonyOS development baseline. Open the relevant platform directory in its IDE.

| What you want to do | Start here |
| --- | --- |
| Compile a package | [Build guide](docs/BUILDING.md) |
| Contribute code or documentation | [Contributing](CONTRIBUTING.md) |
| Report a problem | [Create an issue](https://github.com/XiaoLuoLYG/beegent/issues/new/choose) |
| Ask a question or share a workflow | [Discussions](https://github.com/XiaoLuoLYG/beegent/discussions) |
| Report a security issue | [Security and private reporting](SECURITY.md) |
| Read about version changes | [Changelog](CHANGELOG.md) |

<a id="brand"></a>

## 🎨 Icons and brand assets

The BeeGent bee uses honey yellow and lilac, with a letter B tucked into its stripes.

[App icon](docs/assets/brand/app-icon.png) · [Hero](docs/assets/brand/hero.jpg) · [Social cover](docs/assets/brand/social-preview.jpg) · [Download the asset pack](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-brand-assets.zip)

<details>
<summary>View the brand board</summary>

![BeeGent brand system](docs/assets/brand/brand-board.jpg)

These images were generated with AI assistance. Device scenes are illustrations, not screenshots of the app. See the [generation notes](docs/assets/brand/README.md).

</details>

## 🙌 Credits and licensing

BeeGent builds on the HarmonyOS client and relay tools in [kevinCsir/beegent](https://github.com/kevinCsir/beegent), with continued Android development and a BeeGent identity. Thanks to the original author and the WorkSwarm / JiuwenSwarm projects.

No open-source license has been declared for this repository. Public availability does not grant additional permissions for the code or upstream assets. See [NOTICE](NOTICE) for attribution.

If BeeGent helps your workflow, leave a Star. For questions or ideas, join the [Discussions](https://github.com/XiaoLuoLYG/beegent/discussions).
