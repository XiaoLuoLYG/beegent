# 构建与发布

编译不等于运行验收。以下命令只生成安装包，不执行单元、协议、端到端或真机 UI 测试，也不会安装到设备。

## Android

需要 JDK 17+、Android SDK 36 和可用的 Android SDK 路径。仓库包含 Gradle Wrapper。

```sh
git clone --branch android https://github.com/XiaoLuoLYG/beegent.git
cd beegent/android
# 用 Android Studio 配置 SDK，或设置 ANDROID_HOME 为你的 SDK 目录
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon
```

Windows 可在 Android Studio 中构建这两个变体。输出：

- `app/build/outputs/apk/debug/app-debug.apk`：调试签名，可安装。
- `app/build/outputs/apk/release/app-release-unsigned.apk`：未签名，需自行签名。

包名为 `com.xiaoluolyg.beegent`，最低 Android 8.0 / API 26，目标 API 35，编译 API 36。Debug 签名可能随构建环境变化；旧调试包遇到签名冲突时应先确认原签名，卸载会删除本地诊断记录。

[Android compile](https://github.com/XiaoLuoLYG/beegent/actions/workflows/android-build.yml) 工作流在 Android 源码更新或手动触发时构建上述两个 APK，不运行测试。未签名版本不适合作为普通用户的直接安装入口。

## HarmonyOS

使用 DevEco Studio 打开 `hos/`。首次构建先从不含签名的模板创建本地配置：

```sh
cd hos
cp build-profile.template.json5 build-profile.json5
```

Windows：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\hos\scripts\build.ps1
```

上面的 PowerShell 命令在仓库根目录运行。脚本自动定位 DevEco 自带工具；若安装目录不同，使用 `-DevEco` 参数。没有签名配置时只生成 unsigned HAP。

macOS 可直接使用 DevEco Studio 的构建功能。命令行使用以下命令，在 `hos/` 目录运行；`DEVECO_ROOT` 按实际安装目录填写：

```sh
export DEVECO_ROOT=/Applications/DevEco-Studio.app/Contents
export JAVA_HOME="$DEVECO_ROOT/jbr/Contents/Home"
export DEVECO_SDK_HOME="$DEVECO_ROOT/sdk"
export PATH="$DEVECO_ROOT/tools/node/bin:$DEVECO_ROOT/tools/ohpm/bin:$PATH"
export NODE_PATH="$HOME/.cache/beegent/hvigor-deps/node_modules"
mkdir -p "$NODE_PATH/@ohos"
ln -sfn "$DEVECO_ROOT/tools/hvigor/hvigor" "$NODE_PATH/@ohos/hvigor"
ln -sfn "$DEVECO_ROOT/tools/hvigor/hvigor-ohos-plugin" "$NODE_PATH/@ohos/hvigor-ohos-plugin"
node "$DEVECO_ROOT/tools/hvigor/hvigor/bin/hvigor.js" \
  --mode module -p module=entry@default -p product=default \
  -p buildMode=release assembleHap --no-daemon
```

输出位于 `hos/entry/build/default/outputs/default/`。包名保留 `com.kevincsir.jiuwenbridge`。本地签名配置不进入版本控制；安装 unsigned HAP 前需要使用自己的合法签名与设备授权。

### v0.2.0 的实际 HAP 构建环境

仓库模板指定 **HarmonyOS 6.1.1 / API 24**。本次发布机器装有 **HarmonyOS 6.1.0.105 / API 23**，因此发布 HAP 使用本地 `build-profile.json5` 将 `compileSdkVersion` 与 `targetSdkVersion` 设为 `6.1.0(23)`，兼容版本仍为 `5.0.0(12)`，签名配置为空。仓库的 API 24 模板保持原样。

Release 同时提供这份不含签名的构建配置和构建信息，可替换本地副本以复现该产物。API 12 是配置下限，不代表已在全部低版本设备上验证。编译器会报告现有 ArkTS 异常处理、废弃 API 等警告；本次不修改这些业务代码。

## 发布内容

代码标签 `v0.2.0` 对应两个客户端的 `versionName = 0.2.0`。Release 标记为预览版，提供：

- Android Debug APK 与 unsigned Release APK。
- HarmonyOS unsigned Release HAP 与对应构建配置。
- `SHA256SUMS.txt`、`BUILD-INFO.txt`。
- 品牌素材 ZIP，以及可直接交给 WorkSwarm 的局域网转发 Markdown。

安装包只上传 GitHub Releases，不提交到 Git 历史。GitHub 的 source archives 是源码，不能直接安装。

macOS / Linux 可在下载目录校验：

```sh
shasum -a 256 -c SHA256SUMS.txt
```

该命令用于文件完整性校验，不运行应用。未下载清单内全部文件时会提示对应文件缺失；可只对所需文件运行 `shasum -a 256 文件名` 与清单逐项比较。

## 转发工具文档

维护源位于 `swarm/beegent-lan-relay/`。改动后在仓库根目录生成单文件版本：

```sh
node swarm/build-single-file.cjs
```

该命令不启动转发；使用方式及端口说明见 [转发 Skill](../swarm/beegent-lan-relay.md)。
