# BeeGent · HarmonyOS

鸿蒙原生的 JiuwenSwarm 局域网聊天客户端。ArkTS + ArkUI，Stage 模型。

## 当前功能

- beegent 图标与白底／炭黑文字／蜂蜜黄按钮主题，禁用按钮为浅奶油色；首页不展示 Logo。
- 顶部连接状态打开地址浮窗；圆形菜单打开历史会话侧边栏。上下栏透明并收紧留白，会话内使用单行输入栏。
- 连接后通过 `session.list` 分页获取后端会话；选择会话读取元数据和 `history.get`，支持向前加载与大消息分片重组。
- 不缓存本地会话、历史、草稿或地址供重放／恢复使用。通信日志是用户明确要求的独立本地诊断记录。只保留当前页面所需的临时状态；切换会话或断线立即释放，重开从服务器读取。侧边栏关闭即释放列表。
- 首页三个按钮直接切换 Agent / Team、Work / Code、Normal / Plan，默认 Agent / Work / Normal；发送首条消息时按所选模式创建会话。支持多轮流式正文、Markdown、工具、子代理活动、附件操作、问答与审批。
- 执行中输入先进入手机队列，可显式补充当前单 Agent 任务；未知投递结果不自动重发。
- 历史审批只读，不能重复提交。usage（包括 metadata 和 summary）不进入聊天状态或 UI；通信诊断日志保留其收包记录。
- 历史快照读取不等于跨客户端实时同步。另一端正在执行的会话显示状态待确认；断线后可重新打开会话获取历史。

服务器是会话事实源。客户端只发送新输入，不把历史上下文重复回传。
历史读取兼容游标和页码两套分页协议，详见 [历史分页兼容](docs/history-pagination.md)。
当前继续沿用包名 `com.kevincsir.jiuwenbridge`，保留调试签名与覆盖安装能力；鸿蒙工程已迁移至仓库的 `hos/` 目录。

- 通信日志：默认记录消息往返、请求关联、耗时及附件处理；侧边栏可导出和清空。自动脱敏、轮换保留最近约 12 MB，详见 [日志说明](docs/communication-logs.md)。

- 任务清单原位更新；图片自动预览／全屏缩放；HTML 全屏 WebView 渲染。详情见 [消息预览](docs/message-previews.md)。

## 打开和编译

本机工程：`C:\kevin\code\JiuWenBridge\hos`。已验证 DevEco Studio 6.1.1.290、
SDK API 24、Hvigor 6.24.3；兼容版本设为 API 12，较低版本真机兼容性尚待验证。

以下命令均在 `hos/` 目录执行。首次克隆先执行：

```powershell
Copy-Item .\build-profile.template.json5 .\build-profile.json5
```

用 DevEco 打开工程并同步。真机调试需要为 `com.kevincsir.jiuwenbridge` 配置自己的
自动签名。`build-profile.json5` 是本地配置，已忽略；分享构建配置时更新不含签名的
`build-profile.template.json5`。不要覆盖本地签名，也不要提交证书或密码。

命令行编译：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

脚本使用 DevEco 自带的 Node/JBR/Hvigor，在 `%LOCALAPPDATA%\JiuWenBridge\hvigor-deps`
创建到 bundled 工具的目录联接，不依赖 Appless 工程。首次缺少 build-profile 时从模板
创建。产物位于 `entry\build\default\outputs\default`；没有签名时仅产生 unsigned HAP。

设备操作与有超时保护的构建流程由仓库外的个人 skill `jiuwenbridge-device-testing`
管理。该 skill 不属于本工程依赖，普通开发者也可以直接在 DevEco 构建和运行。

## 连接 Swarm

默认通过 [WorkSwarm 局域网转发 Skill](../swarm/beegent-lan-relay.md) 连接桌面版：消息端口 `29000`，下载端口 `25173`。
手机填写电脑局域网 IPv4 地址，后端端口留空默认 29000，下载端口留空默认 25173。
转发进程默认监听 0.0.0.0，并将这两个端口分别转发到本机后端 19000 和下载服务 5173；需要允许局域网访问转发端口。
连接参数仅保留在本次运行内存中，不写入本地配置；测试默认 IP 在
`entry/src/main/ets/config/AppConfig.ets`。

如果直接连接源码后端、不使用转发，则需在手机手动填写实际端口（以下示例为 19000 / 5173），并让两个服务都监听 0.0.0.0。启动示例（先在原终端用 Ctrl+C 停止旧的 app 服务，避免端口占用）：

```powershell
Set-Location 'C:\kevin\code\OpenJiuWen\jiuwenswarm'
$env:WEB_HOST = '0.0.0.0'
$env:WEB_PORT = '19000'
$env:FRONTEND_HOST = '0.0.0.0'
$env:FRONTEND_PORT = '5173'
$env:JIUWENSWARM_CLI_PORTS = '1'
uv run --python 3.11 jiuwenswarm-start all
```

聊天连接为 `ws://IP:后端端口/ws`；附件相对链接使用
`http://IP:下载端口` 作为源地址，包括从历史会话读取的附件。
服务端提供的完整 HTTP/HTTPS 链接保持原样，Base64 附件不依赖下载端口。
无需打开电脑浏览器；5173 的 Python 服务负责网页静态资源和文件下载。

执行中继续输入并点击「排队」，消息加入当前会话的本地队列，当前任务结束后按顺序发送。队列可编辑、移除、上移排序、暂停与恢复；单 Agent 执行中可点「补充当前任务」，使用 `steer` 注入当前执行。Team 暂不支持这条补充通道。按「停止」会暂停队列，并发送
`chat.interrupt` / `intent=cancel`，等待执行确认；请求 ACK 不等于任务停止。
队列和模式保存在本次内存会话中；补充必须等运行时确认，结果未知不自动重发。补充失败保留消息，确认失败后可重新排队；停止后可手动恢复队列；切换会话或断线会清空未发送队列。支持问答、权限和计划审批，以及子代理状态展示。`work_mode` 使用服务端对应默认项目，当前没有项目选择器。
应用连接中断不会终止电脑上的任务。结果待确认时应在电脑核实，或重连后发停止请求；
首版不自动对齐历史，因此可能缺少断线期间的内容。

当前连接浮窗面向 IPv4 局域网，使用明文 WS/HTTP，只用于可信局域网。
当前版本没有独立登录/token 配置界面。

## 代码结构

- `entry/src/main/ets/swarm/Protocol.ets`：类型、地址规范化与帧验证。
- `SwarmConnection.ets`：连接生命周期、请求关联、超时与错误。
- `HarmonyTransport.ets`：鸿蒙 WebSocket API 适配。
- `SwarmManager.ets`：会话创建与路由、发送和停止业务操作。
- `SwarmSession.ets`：内存消息与执行状态、流式合并。
- `entry/src/main/ets/viewmodel/MessageSource.ets`：ArkUI 消息观察对象与懒加载数据源。
- `entry/src/main/ets/pages/Index.ets`：连接、会话、对话 UI。

设计与协议参考 Appless 的 SystemSwarmClient 和 JiuwenSwarm WebChannel，但没有搬入
Appless 产品代码、模型 Key 或签名材料。对接时检查的 Swarm 提交：
`e829cd8bf86bbe7a3b0a4004709677f272c2e5e1`。

## 测试

核心行为测试使用 DevEco 自带 TypeScript 转译同一份 ArkTS 领域代码，在 Node 上注入
内存传输，不等于 ArkTS 编译检查或真机验证：

```powershell
node .\tests\core.test.cjs
node .\tests\queue-mode.test.cjs
node .\tests\transport.test.cjs
```

真实后端测试需要 Node 22+ 的内置 WebSocket，会在指定服务创建 QA 会话，执行
两会话、多轮上下文和停止操作，产生少量模型调用费用，不执行工具操作：

```powershell
node .\tests\live-smoke.cjs ws://127.0.0.1:19000/ws
node .\tests\queue-mode-live.cjs ws://127.0.0.1:19000/ws
```

真机验证仍需签名后安装，检查局域网连接、键盘布局、流式显示和滚动；桌面协议测试
通过不能代替这些检查。具体测试记录见 `docs/validation.md`。


## 下载预览版

[GitHub Release v0.2.0](https://github.com/XiaoLuoLYG/beegent/releases/tag/v0.2.0) 提供 unsigned HAP。该产物使用 API 23 本地构建配置，仓库模板仍为 API 24；复现方式与安装前提见 [构建指南](../docs/BUILDING.md)。
