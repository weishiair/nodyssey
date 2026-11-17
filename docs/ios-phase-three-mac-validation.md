# iOS 3A Mac 验证记录

日期：2026-11-17。设置的 Apple 编译、原生测试和核心操作已通过；任务书要求的全部设备、语言与辅助功能验收尚未完成。

## 源码与工作区

验证时模拟器安装的是设置、阅读底栏和首页横屏三个工作区改动的组合，基线 HEAD 本身不代表完整候选。源码分别提交到下列功能分支，统一汇入 `codex/ios-experience-integration`；不修改版本或发布 IPA。

| 范围 | 分支与基线 | Windows | Mac |
| --- | --- | --- | --- |
| 3A 设置及本轮修复 | `codex/ios-phase-three-settings`，`2dbbffd1` | `D:/nodyssey` | `/Users/a092/Developer/nodyssey-ios` |
| 阅读底栏对齐 | `codex/ios-reading-controls-alignment`，从集成线 `4ea74179` 建立 | `C:/Users/weishi/.codex/worktrees/ios-reading-controls-alignment/nodyssey` | `/Users/a092/Developer/nodyssey-ios-reading-controls` |
| 首页横屏裁切 | `codex/ios-home-landscape-controls`，从集成线 `4ea74179` 建立 | `C:/Users/weishi/.codex/worktrees/ios-home-landscape-controls/nodyssey` | `/Users/a092/Developer/nodyssey-ios-home-landscape-controls` |

三项分别评审，目标均为 `codex/ios-experience-integration`。不要把阅读底栏当作 3A 的一部分提交。Mac 的源码已拆回各自工作区，桌面模拟器保留已验证的组合 App；重新单独构建设置分支，会得到仅含设置改动的 App。

## 设置修复

- 修复三个 UIKit host 初始化时的 `state` 名称遮蔽，显式读取 host 的回调状态；原来的引用被解析成 UIKit 控件的状态值，Apple 编译失败。
- 滑块的三个 cinterop 回调检查可空 sender，保留预览、正常结束提交和取消回滚契约。
- 设置控件使用 Compose 默认的 UIKit 下层渲染，消除开关滚出视口后盖住固定标题栏的问题。浮动玻璃按钮仍保留其原有渲染方式。
- 三个原生滑块居中、左右增加留白，轨道最大宽度为 320pt；Android／JVM 回退不变。
- `NativeGlassButtonHostTest` 的两处 UIKit 对象断言使用 `assertEquals`。原失败中原生指针相同，而 Kotlin wrapper 不同；仍检查导航项／菜单项没有被替换。

## 阅读底栏修复

在 iOS 原生玻璃模式下，通过现有导航控制器的官方 `UIToolbar` 显示楼层和回复操作，替换共享浮动行的手工高度。楼层跳转、回复回调和发布逻辑保持原合同；其他渲染模式继续使用原浮动行。

导航控制器在进入、返回和切换详情时根据页面的 toolbar items 管理工具栏。回复窗口打开时隐藏工具栏，关闭后恢复；释放页面时清空回调。帖子列表、底部刷新提示和 snackbar 不再重复预留旧浮动行的空间。

iPhone 17 竖屏实测：原阅读按钮中心为 Y=795pt，首页导航中心为 Y=822pt；改为系统工具栏后，楼层和回复中心均为 Y=822pt。横屏使用 UIKit 的工具栏布局，已实际操作检查；其按钮与标签栏的 accessibility frame 中心相差 4pt。

## 已取得的验证证据

Mac 使用 Xcode 27.2（27B52）、iOS SDK／runtime 27.2、JDK 21。记录根目录：`~/.nodyssey/ios-phase-three-validation/2dbbffd1`。

| 检查 | 结果／记录 |
| --- | --- |
| `:iosapp:compileKotlinIosArm64`、`:iosapp:compileKotlinIosSimulatorArm64` | 组合候选通过，`recheck/reading-alignment-final-compile.*` |
| `:shared:macosArm64Test` | 469 项通过，`macos-tests.*` |
| `:designsys:iosSimulatorArm64Test`、`:ui:iosSimulatorArm64Test` | 49 + 5 项通过，包含 5 项设置 host 测试，`recheck/uikit-tests.*` |
| `spotlessCheck`、`git diff --check` | 通过；Mac 的最终格式检查为 `recheck/final-spotless.*` |
| 仓库模拟器构建脚本 | 通过，arm64、iPhoneSimulator、SDK 27.2、最低 iOS 16、版本 165；`recheck/reading-alignment-final-app.*` |
| Windows 阅读分支 `:ui:compileKotlinJvm` | 通过，日志位于原 Windows 工作区 `build/ios-phase-three-validation/2dbbffd1/windows-reading-checks.log` |
| Windows `PostDetailScreenTest`、`StackMirrorTest` | 76 + 12 项通过，0 失败／错误／跳过，同上日志 |

523 项原生测试在设置编译／回调修复后通过；后续的滑块排版、视图层级及阅读底栏改动，另以双目标编译和以下实际操作验证，不把旧测试当成这些新界面的外观验收。

临时 XCTest 驱动放在仓库外，不提交外观或尺寸断言。以下最终流程通过：

- `ui-probe/refined-settings-v2.*`：三个滑块调整、恢复原值；Wi-Fi 行与开关区域各点击一次；关于页进入、返回。截图确认标题栏不再残留开关。
- `ui-probe/aligned-reading-final.*`：首页进入帖子、楼层跳转、打开并关闭回复窗口、工具栏恢复、横屏、返回首页；确认首页不残留回复工具栏。没有输入或发布内容。
- `ui-probe/landscape-screen.*`：旋转稳定后通过 `XCUIScreen` 获取完整横屏画面，检查帖子与首页。较早 `XCUIApplication.screenshot()` 产生了错误裁切的横屏截图，不作为外观证据。

较早探查日志包含驱动定位及等待条件导致的失败，最终证据以上述退出码 0 的流程为准。截图保存在 `ui-probe/evidence/`。

### 横屏首页的既有缺陷已修复

完整横屏截图显示首页右侧“发帖”按钮被裁切。已安装不含本轮阅读底栏改动的设置候选包作对照，`ui-probe/landscape-baseline-v2.*` 通过，截图 `81-baseline-home-landscape.png` 同样出现裁切。因此这不是新工具栏引入的问题。后续独立分支 `codex/ios-home-landscape-controls` 已修复：可用高度不足时改为横向排列，左右横屏及转回竖屏、页码入口与发帖入口均已在模拟器验证。详见 [首页横屏验证记录](ios-home-landscape-validation.md)。

## 当前使用与剩余验收

桌面测试设备为 `Nodyssey 3A Validation`，UDID `851D602A-3EE6-4CCA-9832-81AF03B32EA3`。保持设备启动和 Device Hub 窗口运行。组合 App 位于设置工作区的 `build/ios-home-landscape/Nodyssey.app`，已安装。

按任务书仍需完成 iPad／窗口尺寸、iOS 16–18、英文／繁中与较大字体、VoiceOver 实际调节，以及其余前两阶段哨兵操作。整套 Android／lint／Release 门禁尚未针对这个组合候选执行。这些验收缺口仍需补齐；当前模拟器可继续体验设置、阅读底栏和首页横屏三处修复。
