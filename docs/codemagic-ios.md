# Codemagic iOS 构建

## 日常只给手机出包

在 Codemagic 手动构建时选择 **Nodyssey iPhone IPA only**（`ios-ipa`）。它运行与原有
`ios-unsigned` 相同的 Rust 和 macOS Native 回归，生成并核对 Release 真机 IPA，不再构建
模拟器 App Preview。IPA 未签名，需要自行签名后安装；这个入口不发布 GitHub Release。

需要模拟器 `.app` 或 App Preview 时，继续选择 **Nodyssey iOS unsigned IPA**
（`ios-unsigned`），仍会生成真机和模拟器两种产物。自动回归和标签发布的测试门禁不变。

## 自动回归与发布

功能合入 `dev` 后，Codemagic 自动运行 Rust 网络核心、Kotlin/Native 和 iOS 模拟器回归；推送
`v` 版本标签后，它自动构建未签名的 iPhone IPA 和模拟器包，并将 IPA 附加到对应的 GitHub
Release。普通功能分支不会自动占用 Mac，仍可在 Codemagic 手动运行 **Nodyssey iOS unsigned
IPA**，无需 GitHub 发布凭据；该手动工作流保留原有的 Rust 和 macOS Native 测试，不额外要求
模拟器回归通过。自动回归与标签发布则运行完整的 iOS 模拟器测试。

工作流使用 M2 Mac、JDK 21、Xcode 26.6，最长运行 120 分钟。它会验证 ARM 宿主、版本、应用
标识和真机及模拟器二进制的 SDK；iOS SDK 必须至少为 26，才能使用系统液态玻璃底栏。Rust
工具链由 `native/ios-network/rust-toolchain.toml` 固定。多平台项目的 Android 插件在配置时
需要 SDK 目录，因此构建使用 Codemagic 预装的 SDK，但不构建 Android 目标。
模拟器回归使用 `.github/scripts/boot-ios-test-simulator.sh` 启动完整的 iOS 系统服务；测试结束后
关闭模拟器，再进行需要较多内存的优化链接。

## 首次接入和切换

1. 在 Codemagic 添加 `weishiair/nodyssey`，在应用设置的 **Webhooks** 点击 **Update webhook**。
   自动触发读取事件对应提交中的 `codemagic.yaml`，所以该文件需先合入相关分支；旧标签不会
   自动获得新配置。
2. 为自动上传 IPA，创建只允许访问该仓库的 GitHub fine-grained personal access token，授予
   **Contents: read and write** 和 **Actions: read**。Contents 写权限也能修改仓库内容，应设置
   到期时间并妥善保管。在
   Codemagic 应用设置的 **Environment variables** 中，将 token 作为勾选 **Secret** 的
   `GH_TOKEN` 保存到 `github_release` 组。不要把 token 写进仓库或聊天。
3. 将配置合入 `dev` 后，检查自动运行的 **Nodyssey iOS native regression**。从包含新配置的
   提交创建一个 iOS 测试版本标签，确认 **Nodyssey iOS tag release** 通过，并在 GitHub
   prerelease 上核对 IPA 与版本信息。iOS-only 标签会创建 prerelease；其他标签会等待 Android
   工作流创建同名 Release，不会代它创建正式版。
4. Codemagic 的标签发布验证成功后，在 GitHub 仓库 **Settings → Secrets and variables →
   Actions → Variables** 中设置 `CODEMAGIC_IOS_RELEASE` 为 `true`。此后标签触发的 GitHub
   Release 工作流不再启动 Mac job，避免重复构建和覆盖附件。手动运行 Release 工作流仍可
   使用 GitHub Mac、`ios_ref` 修复和 Telegram 通知；删除该变量或改成其他值即可恢复原行为。

未完成第 4 步时 GitHub 的 iOS job 保持原样。正式发布前也必须将此配置合入 `main`，让正式
标签所指向的提交包含它。

## 产物与恢复

在 Codemagic 构建页的 Artifacts 可以下载 iPhone arm64 IPA、模拟器 `.app` 和
`*-simulator-arm64.zip`，以及测试报告、源码提交号和构建日志。IPA **没有签名**，需要使用
自己的签名工具签名后安装；模拟器包只能用于 Apple Silicon Mac 的 iOS Simulator。

对于普通版本标签，Codemagic 最多等待五分钟，直到相同标签与提交的 Android Release job
成功结束；GitHub 的 iOS Mac job 是否排队不影响等待。若 Android job 失败或尚未完成，iOS 发布步骤会失败，已生成的 IPA 仍可在构建产物中下载；应先修复 Android 发布，
再使用 GitHub 手动 iOS 工作流，或下载并核对产物后附加到同一 Release。Codemagic 的手动构建
不会自动上传 Release。

自动回归只监听 `dev`，新提交会取消同分支尚未完成的旧回归。标签构建不会互相取消。Codemagic
各工作流分别缓存 Gradle 依赖、Gradle 本地任务输出（`$HOME/.gradle/caches/build-cache-1`）、
Kotlin/Native 工具链和 Rust 编译产物。Gradle 按任务输入判断能否复用输出；源文件或工具链
变化后仍需执行受影响任务。第一次构建负责填充缓存，可能较慢。
个人账户的免费构建分钟数和按量计费以 Codemagic **Billing** 页面为准，超过免费额度后可能产生
费用。App Preview 是独立服务，不包含在本自动构建流程里。

## 比较实际出包耗时

在同一提交、同一工作流和同一 M2 / Xcode 环境中连续手动构建两次。记录构建页的缓存恢复、
原生测试、真机 Archive、产物收集和缓存保存耗时；第二次检查 Gradle 日志中的 `FROM-CACHE`。
核对缓存保存和恢复日志是否成功：Codemagic 每工作流缓存上限为个人账号 3 GB、团队 10 GB；
超限或保存失败时，先调整缓存范围，再比较热缓存构建。
Archive 和模拟器构建的 `xcodebuild.log` 都带有 `Build Timing Summary`，可查看 Kotlin
framework 脚本阶段的耗时。Release 全程序优化链接不保证能复用，不能只凭缓存恢复成功判断提速。

比较 `ios-ipa` 与 `ios-unsigned` 时，单独列出被省略的模拟器 App 构建时间，不能把减少产物
算作编译器变快。两个手动入口的缓存分别填充；切换入口后的首次构建不作为热缓存基准。

参考：

- https://docs.codemagic.io/yaml-running-builds/starting-builds-automatically/
- https://docs.codemagic.io/yaml-distributing/github-releases/
- https://docs.codemagic.io/yaml-basic-configuration/configuring-environment-variables/
- https://docs.codemagic.io/specs-macos/xcode-26-6/
- https://docs.codemagic.io/knowledge-codemagic/caching/
- https://kotlinlang.org/docs/native-improving-compilation-time.html
