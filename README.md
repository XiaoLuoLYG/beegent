# beegent

JiuwenSwarm 的原生移动客户端。

```text
hos/       HarmonyOS 客户端（ArkTS + ArkUI）
android/   Android 客户端（Kotlin + Jetpack Compose）
swarm/     给 WorkSwarm 使用的局域网转发 Skill 与单文件文档
```

## HarmonyOS

使用 DevEco Studio 打开 `hos/`，不要将仓库根目录作为鸿蒙工程打开。
签名、构建、连接配置及功能说明见 [鸿蒙工程说明](hos/README.md)。

在仓库根目录编译：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\hos\scripts\build.ps1
```

历史会话读取兼容页码和游标两套协议，详见 [历史分页兼容](hos/docs/history-pagination.md)。

## WorkSwarm 桌面版局域网连接

桌面版后端仅监听本机时，可让 WorkSwarm 根据本仓库提供的 Skill 启动 TCP 转发，再用手机连接电脑的局域网 IP。无需修改 Swarm 代码或模型配置。

- [单文件 Markdown](swarm/beegent-lan-relay.md)：包含说明和全部脚本，可直接发给 WorkSwarm 按说明保存、启动。
- [多文件 Skill](swarm/beegent-lan-relay/)：`SKILL.md` 与 `scripts/lan_relay.cjs`，适合整体复制或打包分发。

两种交付形式使用同一个 Node 脚本，支持 Windows、Linux、macOS。优先使用桌面版随附的 Node，需要 Node.js 18+；不依赖 Python 或 npm 包。

| 手机配置 | 默认转发目标 |
| --- | --- |
| 电脑局域网 IP，消息端口 `29000` | `127.0.0.1:19000` |
| 电脑局域网 IP，下载端口 `25173` | `127.0.0.1:5173` |

鸿蒙客户端默认消息端口为 `29000`、下载端口为 `25173`，端口输入留空时也使用这两个默认值。转发默认监听 `0.0.0.0`，手机应填写电脑实际 IP。桌面版可能自动更换后端端口，启动前需确认；转发脚本提供 `start`、`status`、`stop`，详情见 Skill。转发就绪不代表手机连通或后端业务正常。

### 维护与生成

以 `swarm/beegent-lan-relay/SKILL.md` 和 `swarm/beegent-lan-relay/scripts/lan_relay.cjs` 为维护源。修改后，在仓库根目录运行：

```shell
node swarm/build-single-file.cjs
```

该命令仅生成 `swarm/beegent-lan-relay.md`，不会启动转发或执行测试。提交时将维护源和生成的单文件文档一起提交，避免两种版本不同步。运行状态及连接日志保存到用户目录，不纳入仓库。

## Android

Android Studio 打开 `android/`；迁移范围与验证边界见 [Android 说明](android/README.md) 和 [迁移清单](android/migration.md)。

本仓库不包含本机调试签名、模型密钥、依赖目录或构建产物。
