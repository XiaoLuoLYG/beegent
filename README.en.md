<p align="center"><img src="docs/assets/brand/app-icon.png" width="88" alt="BeeGent bee icon" /></p>
<h1 align="center">BeeGent</h1>
<p align="center"><strong>Your agents. Within reach.</strong></p>
<p align="center">A native mobile companion for WorkSwarm / JiuwenSwarm.</p>
<p align="center"><a href="README.md">简体中文</a> · <a href="https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0">Download v0.2.0 preview</a> · <a href="docs/BUILDING.md">Build</a> · <a href="https://github.com/XiaoLuoLYG/beegent/discussions">Discuss</a></p>

![BeeGent — Your agents. Within reach.](docs/assets/brand/hero.jpg)

Let your desktop agents work. Stay involved from your phone. BeeGent connects to WorkSwarm / JiuwenSwarm over your local network so you can follow progress, send instructions, respond to approvals and receive files.

**Native Android and HarmonyOS.** Kotlin / Jetpack Compose on Android; ArkTS / ArkUI on HarmonyOS. Agents execute on your desktop. BeeGent is the mobile client.

> v0.2.0 is a preview release. Packages are compile-checked; complete backend workflows still need device acceptance. The hero is a brand illustration, not an app screenshot.

## What you can work with

| Workflow | Client capabilities |
| --- | --- |
| Follow a task | Streaming replies, Markdown, tool steps, subagent activity and task lists |
| Choose your mode | Agent / Team, Work / Code, Normal / Plan |
| Keep work moving | Queue, edit, reorder, pause and resume messages; steer a running single-agent task |
| Make decisions | Questions, permission requests and plan approvals; historical approvals are read-only |
| Receive results | File downloads, image and HTML previews, system save and share |
| Revisit work | Server sessions and paginated history, supporting cursor and page-based protocols |

## Get started

1. Download a package from [Releases](https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0).
2. Start WorkSwarm / JiuwenSwarm on your desktop. If it listens only on localhost, use the included [LAN relay Skill](swarm/beegent-lan-relay.md). It requires Node.js 18+, with no npm dependencies.
3. Connect your phone and desktop to the same trusted LAN. Enter the desktop IPv4 address in BeeGent. With the default relay, leave the ports blank: messages use `29000`, downloads use `25173`.
4. Open an existing session, or choose a mode and send your first message.

| Package | Intended use |
| --- | --- |
| [Android Debug APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-debug.apk) | Android 8.0+; installable with a debug signature |
| [Android unsigned APK](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-android-unsigned.apk) | Release build; sign before installing |
| [HarmonyOS unsigned HAP](https://github.com/XiaoLuoLYG/beegent/releases/download/v0.2.0/BeeGent-v0.2.0-harmonyos-unsigned.hap) | Requires your HarmonyOS signing profile and device authorization |

The relay forwards `29000 → 19000` for WebChannel and `25173 → 5173` for downloads by default. Confirm your desktop's actual ports before starting it. SHA-256 checksums accompany the release.

## Know the boundaries

- A desktop backend is required. This is not an on-device model runtime, and disconnecting the phone does not stop desktop tasks.
- Current connections use plaintext WS / HTTP on a trusted LAN, without a separate login or token setting. Do not expose these ports directly to the internet.
- The server owns session history. Connection settings, drafts and queues live in memory. Disconnecting or switching sessions clears unsent queued messages. Diagnostic logs are separate local files.
- Attachments travel from desktop to phone. Sending is text-only; phone uploads and continuous cross-client synchronization are not implemented.
- Compilation is not runtime acceptance. See [Android status](android/migration.md) and [HarmonyOS validation records](hos/docs/validation.md).

## Build and contribute

The default `android` branch contains both clients; `hos` preserves the HarmonyOS development baseline. Open `android/` in Android Studio or `hos/` in DevEco Studio.

[Build guide](docs/BUILDING.md) · [Contributing](CONTRIBUTING.md) · [Report a problem](https://github.com/XiaoLuoLYG/beegent/issues/new/choose) · [Security](SECURITY.md) · [Changelog](CHANGELOG.md) · [Brand assets](docs/assets/brand/README.md)

## Credits and licensing

Based on [kevinCsir/beegent](https://github.com/kevinCsir/beegent), with continued Android development and a new BeeGent identity. Thanks to its original author and the WorkSwarm / JiuwenSwarm projects.

No open-source license has been declared for this repository. Public availability does not grant additional permissions for the code or upstream assets. See [NOTICE](NOTICE).

If BeeGent helps your workflow, leave a Star or share your experience in Discussions.
