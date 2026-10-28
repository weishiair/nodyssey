# GitHub Actions iOS 测试包

手动运行 `.github/workflows/ios-test.yml` 的 **Build Android and iOS test apps**，选择测试分支，
将 `platform` 设为 `ios`。版本仍取自该测试分支的 `gradle.properties`。

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

官方依据：

- Apple：<https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass>
- Actions runner：<https://github.com/actions/runner-images/blob/main/images/macos/macos-15-arm64-Readme.md>
