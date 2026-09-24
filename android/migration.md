# Android 迁移清单

基线：`hos` 分支的 HOS 客户端。Android 实现提交于 `android` 分支；功能是否可用仍需 Android 编译与真机连接验证。源代码覆盖不等于运行验收。

| HOS 功能 | Android 源码位置 | 当前证据 |
| --- | --- | --- |
| IPv4 地址、消息／下载端口与内存连接设置 | `Wire.kt`、`MainActivity.kt` | 已编写，待编译与真机验证 |
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
- 根据仓库 `AGENTS.md`，未运行自动测试或截图检查。尚未有 Android 真机功能验收结果。
