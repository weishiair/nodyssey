# GitHub Actions iOS 测试包

手动运行 `.github/workflows/ios-test.yml` 的 **Build Android and iOS test apps**，选择测试分支，
将 `platform` 设为 `ios`。版本仍取自该测试分支的 `gradle.properties`。

排查 Apple 平台的测试失败时，可在 `platform = ios` 下启用 `checks_only`。该模式运行
Native 与 iOS 模拟器回归，并编译真机和模拟器的 iOS 外壳，跳过版本检查与安装包构建；因此可以先在修复分支验证，
通过后再创建带版本号的测试快照。

`v版本号-ios-test.N` 标签对应 iOS 专用测试快照，使用本工作流构建后将 IPA 附加到
GitHub prerelease。常规 Release 工作流的标签过滤器排除该命名空间。

`ios_runner` 默认是 `macos-15`，也可选 `macos-26`。GitHub 的 ARM runner 容量不足、
长时间排队时，可选 `macos-15-intel` 交叉编译同样的 iPhone / 模拟器 arm64 产物。
Intel 无法执行 `macosArm64Test`，该模式明确跳过此项并运行 `:shared:jvmTest`；
不能将其视为通过了 Kotlin/Native 运行测试。常规 ARM runner 仍保留原生测试门禁。

## 底栏与构建 SDK

iOS 26 及以上使用 UIKit 的原生液态玻璃底栏，不设置自定义背景或关闭半透明。
低于 iOS 26 使用普通不透明底栏，统一 `standardAppearance` 与 `scrollEdgeAppearance`，
防止列表文字穿过控件。运行系统版本和构建 SDK 是两个条件：只有新系统并不足以启用新设计。

`1.3.5-ios-test.1` 的成功构建（Actions run `40686456366`）实际使用
`/Applications/Xcode_16.4.app`、`SDKROOT = iphoneos18.5`。`macos-15` 镜像虽安装了
Xcode 26.3，默认仍为 16.4，因此该包在 iOS 27 上仍采用旧设计。把底栏改成透明不会启用液态玻璃，
把所有系统的底栏改成不透明也无法恢复它。

已下载核对该次构建的 IPA：`DTXcode = 1640`、`DTSDKName = iphoneos18.5`，
可执行文件的 `LC_BUILD_VERSION` 同样记录 `sdk = 18.5.0`，并未设置 `UIDesignRequiresCompatibility`。

工作流通过 job 级 `DEVELOPER_DIR` 显式选择镜像中的 Xcode 26.3，让 Kotlin/Native、真机包
和模拟器包使用同一套工具链；开始构建前检查 iOS SDK 至少为 26。打包时再次检查真机包的
`DTSDKName` 和 Mach-O 的 SDK，模拟器脚本也检查最终二进制的 SDK，避免构建成功却退回旧设计。
产物中的 `build-info.txt` 记录 SDK，`binary-platform.txt` 保留二进制检查结果。

若镜像移除了固定的 Xcode，构建应直接失败。更新 `DEVELOPER_DIR` 前先核对 runner 的已安装
软件清单，不能回退到镜像默认版本。

## 真机验收

- iOS 26 / 27：三个主标签应显示系统悬浮胶囊、玻璃材质和选中态；滚动时检查背景折射与文字对比。
- 低于 iOS 26：显示普通有背景的底栏，列表文字不能直接穿过图标。
- 切换浅色、深色和跟随系统后，切换标签并进入帖子再返回，确认材质和显隐正常。

Windows 上可检查配置、脚本和共享代码；Swift 编译和以上视觉验收需要 macOS / iOS。

## 正式版

`dev` 通过发布 PR 合入 `main` 后，推送 `v版本号` 标签触发 `release.yml`。
Android 签名包发布后，iOS job 使用同一标签编译并附加未签名 IPA。
正式版使用 `macos-15` ARM Mac 与 Xcode 26.3，运行 Kotlin/Native 和 iOS 模拟器回归后再打包。
工作流核对宿主确为 arm64；正式版不能选择 Intel，也不会跳过原生运行测试。手动重跑时可通过
`ios_runner` 选择 `macos-26` ARM Mac。

发布前可在准备分支运行 `ios-test.yml`，选择 `platform = ios`、`checks_only = false`。
该工作流同时接受稳定版本号，先完成原生测试、Release 真机 IPA 和模拟器包验证，将产物保存在
Actions artifacts 中，不创建 GitHub Release。稳定版本的预检同样要求 ARM Mac。

`1.3.10-test.12` 的 ARM Release 链接在 5 GB 堆中耗尽内存，因此打包前停止测试 daemon，
用单 worker、5 GB 堆和 Serial GC 启动独立构建。6 GB 的预检仍在优化链接中运行了 64 分钟，随后主动停止。
`:iosapp` 的 Release framework 通过 Kotlin/Native 的 `-Xdisable-phases` 暂时跳过
`RemoveRedundantCallsToStaticInitializersPhase`，保留初始化调用，其余 Release 优化继续执行。
这可能增加初始化检查的运行开销；待上游约束该分析在完整应用调用图中的内存占用后移除。关闭 Gradle 构建缓存后仍复现 Swift 库路径错配，说明它来自已发布原生依赖的链接参数。
`:ui` 与 `:iosapp` 通过 Kotlin/Native 的 `linkerOpts` 补充 `DEVELOPER_DIR` 中所选 Xcode 的
Swift compatibility 搜索路径，覆盖真机和模拟器。依赖修复绝对路径后可移除此兼容配置。
IPA 旁同时提供 SHA-256 和 `ios-build-v版本号.txt`，记录版本、SDK 与源提交。

推送版本标签会发布安装包和更新清单。需要向 Telegram 频道发送公告和安装包时，
手动运行 Release 工作流并勾选 `notify_telegram`；该选项默认关闭。

官方依据：

- Apple：<https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass>
- Actions runner：<https://github.com/actions/runner-images/blob/main/images/macos/macos-15-arm64-Readme.md>
