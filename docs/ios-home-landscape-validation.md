# iOS 首页横屏裁切修复验证

日期：2026-11-17。分支：`codex/ios-home-landscape-controls`，基于 iOS 集成线 `4ea74179`。

## 修复结果

首页右侧纵向翻页栏在横屏下先占据可用高度，导致后面的发帖按钮被压成窄条。iOS 原生玻璃模式下，现在根据实际可用高度，在完整纵向控件放不下时改为横排；始终按展开状态所需高度判断，滚动收起按钮不会反复切换布局。转回竖屏后恢复纵排。

`PageJumpRail` 新增默认值为 `true` 的 `vertical` 参数，复用现有原生玻璃工具栏及 Material 官方横向／纵向工具栏。页码、回顶部、下一页和发帖操作均保留，原有调用与 Android 行为保持。

## 已通过的检查

- Windows：`:ui:compileKotlinJvm`、`:ui:testAndroidHostTest --tests '*PostListScreenTest'`，25 项测试通过；`spotlessCheck`、`git diff --check` 通过。
- Mac：`spotlessCheck`、`:iosapp:compileKotlinIosArm64`、`:iosapp:compileKotlinIosSimulatorArm64` 通过。
- 仓库 `.github/scripts/build-ios-simulator.sh` 构建成功，原有 arm64、平台、SDK 和弱符号检查通过。
- iPhone 17／iOS 27.2 模拟器实际操作：竖屏 → 左横屏 → 打开并关闭页码面板 → 发帖打开编辑器并取消 → 竖屏 → 右横屏 → 竖屏。XCTest 退出码为 0，没有输入或发布内容。
- 已查看完整屏幕截图：左右横屏的页码、回顶部、下一页和发帖按钮均完整，转回竖屏恢复原布局。截屏使用 `XCUIScreen.main.screenshot()`。

验证候选由设置分支 `2dbbffd1` 加设置修复、阅读底栏分支改动和本分支改动组成；上述基线提交本身不代表完整候选。原生测试与阅读回归的适用范围见 [3A Mac 验证记录](ios-phase-three-mac-validation.md)。

## 日志与正在使用的 App

- Windows 日志：`D:/nodyssey/build/ios-home-landscape-validation/`，`windows-checks.log`、`windows-spotless.log`。
- Mac 日志：`~/.nodyssey/ios-home-landscape-validation/`，`compile.exit`、`app.exit`、`ui-probe/actions.exit` 均为 0；操作结果为 `ui-probe/actions.xcresult`。
- Mac 原图：`ui-probe/evidence/`；Windows 已检查副本：`D:/nodyssey/build/ios-home-landscape-validation/review/90-home-portrait.jpg`、`91-home-landscape-left.jpg`、`92-home-returned-portrait.jpg`、`93-home-landscape-right.jpg`。
- 已安装组合 App：`/Users/a092/Developer/nodyssey-ios/build/ios-home-landscape/Nodyssey.app`，版本仍为 `1.3.11 / 165`。
- 测试设备：`Nodyssey 3A Validation`，`851D602A-3EE6-4CCA-9832-81AF03B32EA3`。保持模拟器和 Device Hub 窗口运行。

本次完成首页横屏修复。完整 3A 的 iPad、旧系统、语言、大字体、VoiceOver 与其余哨兵验收仍按任务书执行；整套 Android、lint 和 Release 门禁尚未针对这个组合候选执行。
