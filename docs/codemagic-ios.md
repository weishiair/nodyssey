# Codemagic iOS 构建

仓库根目录的 `codemagic.yaml` 提供 `ios-unsigned` 工作流，使用 macOS M2、JDK 21 和 Xcode 26.6。没有自动触发、自动发布或消息通知；在 Codemagic 页面手动开始构建。

多平台项目的 Android 插件在配置时需要 SDK 目录，因此准备步骤会将 Codemagic 预装 SDK 的路径写入 `local.properties`。此工作流只运行原生测试并编译 iOS，不安装 Android 平台包或 Build Tools，也不修改 Android 的 `compileSdk`。

## 操作

1. 在 Codemagic 应用页面选中包含 `codemagic.yaml` 的分支，然后点击 **Check for configuration files**。
2. 选择 **Nodyssey iOS unsigned IPA**，手动开始构建。
3. 构建完成后，从 Artifacts 下载 `.ipa`。这是 iPhone arm64 设备包，需要使用自己的签名工具签名后安装。

配置修复推送后，请在分支上启动新的构建，并检查页面显示的新提交号；重建旧提交仍会使用旧配置。

不需要上传 Apple 证书、账号密码或 App Store Connect 密钥。此流程不提交 App Store，也不会改动 GitHub Release。

## 版本和验证

`gradle.properties` 的 `nodyssey.versionName`、`nodyssey.versionCode` 是唯一版本来源。构建将其传给 Xcode，并检查包内 Info.plist 版本、应用标识、arm64 架构和 ZIP 完整性。

构建先运行 `:shared:macosArm64Test`。构建产物包括 IPA、SHA-256、源代码提交号、版本信息、Xcode 日志和原生测试报告，方便核对实际拿到的包。

工作流默认最长 60 分钟，并缓存 Gradle 下载依赖和 Kotlin/Native 工具链。缓存和手动触发用于减少重复下载及不必要的构建；首次运行仍需实际验证 Codemagic 环境。GitHub 的既有构建流程继续保留。

参考官方配置与环境说明：

- https://docs.codemagic.io/yaml-basic-configuration/yaml-getting-started/
- https://docs.codemagic.io/yaml-quick-start/building-a-native-ios-app/
- https://docs.codemagic.io/specs-macos/xcode-26-6/
