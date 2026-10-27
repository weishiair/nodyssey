# Codemagic iOS 构建

仓库根目录的 `codemagic.yaml` 提供 `ios-unsigned` 工作流，使用 macOS M2、JDK 21 和 Xcode 26.6。没有自动触发、自动发布或消息通知；在 Codemagic 页面手动开始构建。

多平台项目的 Android 插件在配置时需要 SDK 目录，因此准备步骤会将 Codemagic 预装 SDK 的路径写入 `local.properties`。此工作流只运行原生测试并编译 iOS，不安装 Android 平台包或 Build Tools，也不修改 Android 的 `compileSdk`。

构建会使用 `native/ios-network/rust-toolchain.toml` 固定的 Rust 工具链，先运行网络核心测试，再通过 Gradle 编译并链接 DoH/ECH 静态库。原生依赖许可随 IPA 一同打包；不需要额外的代理或签名凭据。

## 操作

1. 在 Codemagic 应用页面选中包含 `codemagic.yaml` 的分支，然后点击 **Check for configuration files**。
2. 选择 **Nodyssey iOS unsigned IPA**，手动开始构建。
3. 构建完成后，从 Artifacts 下载 `.ipa`。这是 iPhone arm64 设备包，需要使用自己的签名工具签名后安装。
4. 同一次构建还会生成 `Nodyssey.app` 模拟器产物，供 Codemagic App Preview 识别，以及 `*-simulator-arm64.zip` 下载包。解压后可通过 `xcrun simctl install booted Nodyssey.app` 安装到 Apple Silicon Mac 的 iOS Simulator。设备 IPA 与模拟器包不可互换。

若 Codemagic 账号已经启用 App Preview，成功构建的 `.app` 旁会出现 **Quick Launch**。此功能是团队的独立服务；官方文档目前说明 Pay as you go 团队启用后有 100 分钟试用，之后按预览分钟计费，不能与免费构建分钟混为一谈。本配置只生成产物，不启用套餐或启动计费预览会话。

配置修复推送后，请在分支上启动新的构建，并检查页面显示的新提交号；重建旧提交仍会使用旧配置。

不需要上传 Apple 证书、账号密码或 App Store Connect 密钥。此流程不提交 App Store，也不会改动 GitHub Release。

## 版本和验证

`gradle.properties` 的 `nodyssey.versionName`、`nodyssey.versionCode` 是唯一版本来源。构建将其传给 Xcode，并检查包内 Info.plist 版本、应用标识、arm64 架构和 ZIP 完整性。

构建先运行 `:shared:macosArm64Test`，再分别编译 Release 真机包和 Debug 模拟器包，后者同样包含 DoH/ECH 原生网络库。构建产物包括 IPA、模拟器 `.app` 和 ZIP、SHA-256、源代码提交号、版本信息、Xcode 日志和原生测试报告，方便核对实际拿到的包。

工作流默认最长 90 分钟，并缓存 Gradle 下载依赖、Kotlin/Native 工具链和 Rust 编译产物。缓存和手动触发用于减少重复下载及不必要的构建；首次运行仍需实际验证 Codemagic 环境。GitHub 的既有构建流程继续保留。

参考官方配置与环境说明：

- https://docs.codemagic.io/yaml-basic-configuration/yaml-getting-started/
- https://docs.codemagic.io/yaml-quick-start/building-a-native-ios-app/
- https://docs.codemagic.io/specs-macos/xcode-26-6/
- https://docs.codemagic.io/yaml-distributing/app-preview/
