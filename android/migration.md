# Android 迁移清单

基线：`hos` 分支的 HOS 客户端。Android 实现提交于 `android` 分支；GitHub Actions 可执行 `assembleDebug` 并保存调试 APK。编译通过不等于真机功能验收。

| HOS 功能 | Android 源码位置 | 当前证据 |
| --- | --- | --- |
| IPv4 地址、消息／下载端口与内存连接设置 | `Wire.kt`、`MainActivity.kt` | 已编写，待真机验证 |
| WebSocket 请求、ACK、超时、运行时受理和断线清理 | `Wire.kt` | 已编写，待验证 |
| 会话列表分页、创建、三组模式、历史会话切换 | `Store.kt`、`MainActivity.kt` | 已编写，待验证 |
| 游标／页码历史兼容、分片、快照、历史回放 | `Store.kt`、`Session.kt` | 已编写，待验证 |
| 多轮流式消息、Markdown、工具步骤、子代理、任务清单 | `Session.kt`、`MainActivity.kt` | 已编写，待验证 |
| 排队／编辑／移动／暂停／补充执行中任务／停止 | `Store.kt`、`MainActivity.kt` | 已编写，待验证 |
| 问答、权限与计划审批 | `Store.kt`、`MainActivity.kt` | 已编写，待验证 |
| 附件打开、系统保存、转发，图片／HTML 预览 | `Attachments.kt`、`MainActivity.kt` | 已编写，待验证 |
| 本地通信日志、脱敏、轮换、导出与清空 | `Wire.kt`、`MainActivity.kt` | 已编写，待验证 |

## 边界

- Android 连接仍为可信局域网明文 WS/HTTP，与 HOS 相同；没有登录/token 配置。
- Android 不缓存或恢复会话、草稿、队列与地址；通信日志为单独的本地诊断文件。
- 原 HOS 工程未修改。Android 使用独立包名、系统文档选择器和 FileProvider。
- 自动测试未运行；下述真机检查仅覆盖安装、启动与基本界面，不代表业务功能验收。

## 2026-09-24 真机冒烟结果

- ALN-AL80（Android 12 / API 31）：Debug APK 经 ADB 安装成功；`MainActivity` 进入前台且进程保持运行。首页、连接设置和会话抽屉在设备上显示正常，未见启动崩溃。
- 当前电脑没有运行中的 WorkSwarm WebChannel、下载服务或局域网转发，因此连接、聊天、历史、队列、审批与附件等业务功能仍待端到端验证。
