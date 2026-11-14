# Codemagic iOS 手动备用构建

## 备用出包只生成手机安装包

在 Codemagic 手动构建时选择 **Nodyssey iPhone IPA only**（`ios-ipa`）。它运行与原有
`ios-unsigned` 相同的 Rust 和 macOS Native 回归，生成并核对 Release 真机 IPA，不再构建
模拟器 App Preview。IPA 未签名，需要自行签名后安装；这个入口不发布 GitHub Release。

需要模拟器 `.app` 或 App Preview 时，继续选择 **Nodyssey iOS unsigned IPA**
（`ios-unsigned`），仍会生成真机和模拟器两种产物。

## 保持 GitHub 主力，Codemagic 手动备用

GitHub Actions 负责日常出包与标签发布；`codemagic.yaml` 没有 push/tag 自动触发器。
为避免旧分支或旧标签中的配置继续自动出包，仓库指向 Codemagic 的 Webhook 也应保持停用。
手动构建仍可正常选择分支，不需要 Webhook，也不会因 GitHub 出包而消耗 Codemagic 分钟。

`ios-checks` 保留为手动原生回归入口。`ios-release` 保留为手动发布备用：需要提供与源码版本
匹配的 `CM_TAG`，并在 Codemagic 的 `github_release` 环境组中配置保密的 `GH_TOKEN`。
该 token 只允许访问本仓库，权限为 **Contents: read and write** 与 **Actions: read**。
普通手动 IPA 构建不需要 GitHub 发布凭据。

旧版 GitHub Release 工作流仍读取 `CODEMAGIC_IOS_RELEASE`，该仓库变量应设为 `false`；
新版 Release 工作流直接运行 GitHub iOS job，不再根据它跳过打包。切换前先停用 Codemagic
Webhook，再恢复 GitHub，避免同一标签在两边重复出包。

工作流使用 M2 Mac、JDK 21、Xcode 26.6，最长运行 120 分钟。它会验证 ARM 宿主、版本、应用
标识和所构建二进制的 SDK；iOS SDK 必须至少为 26。Rust 工具链由
`native/ios-network/rust-toolchain.toml` 固定。多平台项目的 Android 插件在配置时需要 SDK
目录，因此使用 Codemagic 预装的 SDK，但不构建 Android 目标。

完整模拟器回归使用 `.github/scripts/boot-ios-test-simulator.sh` 启动 iOS 系统服务；测试结束后
关闭模拟器，再进行需要较多内存的优化链接。原有 `ios-unsigned` 和新的 `ios-ipa` 都保留 Rust
与 macOS Native 测试；需要完整模拟器回归时可手动运行 `ios-checks`。

## 产物与恢复

在 Codemagic 构建页的 Artifacts 可以下载工作流生成的 iPhone arm64 IPA；`ios-unsigned`
另提供模拟器 `.app` 和 `*-simulator-arm64.zip`。产物也包含测试报告、源码提交号和构建日志。
IPA **没有签名**，需要使用自己的签名工具签名后安装；模拟器包只能用于 Apple Silicon Mac
的 iOS Simulator。

对于普通版本标签，Codemagic 最多等待五分钟，直到相同标签与提交的 Android Release job
成功结束；GitHub 的 iOS Mac job 是否排队不影响等待。若 Android job 失败或尚未完成，iOS
发布步骤会失败，已生成的 IPA 仍可在构建产物中下载；应先修复 Android 发布，
再使用 GitHub 手动 iOS 工作流，或下载并核对产物后附加到同一 Release。`ios-ipa` 与 `ios-unsigned`
只保留产物；只有手动发布备用 `ios-release` 会上传 Release。

Codemagic 各手动工作流分别缓存 Gradle 依赖、Gradle 本地任务输出（`$HOME/.gradle/caches/build-cache-1`）、
Kotlin/Native 工具链和 Rust 编译产物。Gradle 按任务输入判断能否复用输出；源文件或工具链
变化后仍需执行受影响任务。第一次构建负责填充缓存，可能较慢。
个人账户的免费构建分钟数和按量计费以 Codemagic **Billing** 页面为准，超过免费额度后可能产生
费用。App Preview 是独立服务，不包含在本手动构建流程里。

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

- https://docs.codemagic.io/yaml-running-builds/webhooks/
- https://docs.codemagic.io/yaml-distributing/github-releases/
- https://docs.codemagic.io/yaml-basic-configuration/configuring-environment-variables/
- https://docs.codemagic.io/specs-macos/xcode-26-6/
- https://docs.codemagic.io/knowledge-codemagic/caching/
- https://kotlinlang.org/docs/native-improving-compilation-time.html
