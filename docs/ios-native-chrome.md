# iOS 原生界面控件

2026-11-12：iOS 使用 UIKit 的导航栏、搜索与导航菜单；复杂编辑器、表单和内容浮层保留共享
Compose 实现。`1.3.11-ios-test.2` 真机反馈暴露了顶栏遮挡、页码换行、悬浮控件重叠及弹层留白，
因此撤回按钮、工具条、sheet 和 dialog 的整批替换，按控件适配能力决定迁移范围。

## 迁移范围

| 入口 | iOS 实现 |
|---|---|
| 手机页面与 iPad 两栏顶栏 | `UINavigationController`、`UINavigationItem`、`UIBarButtonItem` |
| 首页排序、站点、帖子操作、编辑器 APP 菜单 | `UIMenu`、`UIAction` |
| 搜索页与收藏搜索 | `UISearchController`，输入和提交仍调用共享状态接口 |
| 收藏与表情多选 | 同一原生导航栏切换操作集合；退出多选后恢复普通栏 |
| 简单下拉选择与表情长按 | 系统 action sheet；iPad 配置 popover 锚点与关闭回调 |
| 回复、跳页、资料、工具栏定制及确认表单 | 共享 `ModalBottomSheet`、`AlertDialog`、`Dialog`，保留内容测量、键盘处理和关闭条件 |
| Markdown 格式操作 | 共享编辑工具条，保留横向滚动、链接长按的两种模板、APP 菜单与自定义入口 |
| 发帖、回复和阅读浮动操作 | 共享 FAB、页码与阅读控件，保留原有尺寸、展开收起及排布 |

`PlatformTopAppBarHost` 由每个页面控制器持有，有序登记当前栏。回复预览临时覆盖帖子栏，
底层帖子重组不会抢回导航栏，预览退出时恢复帖子操作。搜索、多选和预览模式禁止 UIKit
直接弹出整页；使用栏上的关闭／返回操作退出该模式。发布期间沿用页面及编辑器的返回、关闭保护。

`NativeChromeViewController` 使用公开的 child-controller containment；iPad 左右栏各自持有
导航控制器，在单双栏切换时继续使用原有页面及 ViewModel。两条嵌套导航栈释放页面时保留占位根，
避免将正在显示的栈清空。

发布原生顶栏的页面，将 Compose 子控制器顶边约束到 UIKit 的 `safeAreaLayoutGuide.topAnchor`，
让分类栏和正文从导航栏下面开始；保留共享顶栏的页面仍从完整视图顶边布局。底边不裁切，
继续由共享内容处理标签栏及键盘 inset。通过 Auto Layout 跟随旋转、搜索栏与 iPad 分栏的安全区变化。

## 系统版本与外观

- iOS 26 及以上：导航栏和原生菜单沿用系统材质，badge API 有系统版本保护。
- iOS 16–25：导航栏和菜单显示该系统支持的原生样式，更新提示保留文字标记。
- iOS 26 新增的 badge 类按名称动态解析，避免 Kotlin/Native 在旧系统启动时强链接该类；已有类上的新方法另以系统版本保护。
- iOS 隐藏模拟玻璃和单手模式的设置入口，不改写用户已保存的偏好。Android 与桌面继续使用原有共享组件。
- UIKit 导航栏沿用 `NativeShell` 对根控制器的 appearance 设置，跟随系统时保持 unspecified；共享弹层沿用所在 composition 的主题与业务依赖。

原生选择菜单通过 `PlatformPresentationLifecycle` 串行处理异步展示与关闭。展示动画中收到关闭请求时
先等展示完成；系统完成关闭或菜单自动关闭后，移除 composition 不会再次调用 dismiss。
呈现选择菜单前等待当前 UIKit transition。复杂共享弹层不再经过 UIKit sheet 适配器，
避免把短表单强制扩为半屏或全屏，或给回复编辑器叠加新的安全区和键盘处理。

## 集中真机验收

借用一台设备时可按以下顺序集中检查，发帖和转账只在有合适测试内容时实际提交：

1. 首页切换排序／站点，打开搜索输入中文，提交后进入帖子；返回时检查查询和滚动位置。
2. 收藏搜索、取消搜索、长按进入多选、全选和退出；表情组也检查多选及长按菜单。
3. 帖子打开标题、更多菜单、收藏、分享；使用楼层浮动控件和跳页面板返回正文。
4. 回复输入文字，使用格式、两种链接模板、表情及 APP 菜单；打开工具栏定制／投票等嵌套弹层再返回。
5. 回复切换预览和编辑数次，确认内容和帖子菜单恢复；提交期间尝试关闭与返回，失败后检查草稿保留和重试。
6. 发帖检查对照／预览、图片入口及返回；私信检查菜单、消息详情、资料弹层与键盘。
7. 设置切换浅色／深色／跟随系统，检查导航栏、菜单、弹层；进入主题页确认没有失效的模拟玻璃入口。
8. iPad 或可调整窗口设备检查左右栏顶栏、旋转、分屏及嵌套详情返回；旧版 iOS 另检查系统样式回退。

## 构建与验证边界

Windows 可以编译共享 JVM 界面、Android 和不依赖 cinterop 的 `:designsys:compileKotlinIosArm64`。
它不能完成 `:shared` 的 Apple 网络 cinterop 与整应用链接。UIKit API 的局部编译检查也不能替代
模拟器运行、真机材质、键盘与手势验收。

在已安装完整 Xcode 的 Mac 上，沿用项目的 Xcode 26 工具链选择后运行：

```sh
./gradlew :shared:macosArm64Test :ui:iosSimulatorArm64Test
./gradlew :iosapp:compileKotlinIosArm64 :iosapp:compileKotlinIosSimulatorArm64
./gradlew --stop
```

完整模拟器和安装包流程见 [`ios-build.md`](ios-build.md)。需要远程验证时，可在迁移分支运行
`ios-test.yml` 的 `platform=ios`、`checks_only=true`。整应用链接及原生运行验证需要 Mac；
Windows 上的局部 UIKit API 编译不代表已经通过真机验收。

### Windows 回归快照（2026-11-12）

最终格式检查、Android lint、Debug APK 与开启 R8 优化的 Release bundle 构建均通过。
设计系统的 iOS Arm64 编译通过；导航容器与 iPad 回归代码也通过了固定版本 UIKit API 的
局部 Kotlin/Native 编译检查，但尚未执行 iOS 测试或完成整应用链接。

全量 JVM／Robolectric／桌面回归共发现 2,643 个用例，215 个渲染用例按配置跳过。
其中 34 个存储相关用例失败：21 个 Room migration、11 个设置存储、2 个远程偏好同步。
另取未修改的 `839dfc99` 源码重跑对应的 50 个用例，失败集合与迁移分支完全一致。
Room 报告数据库名称与 Windows 绝对路径不匹配，DataStore 报告临时文件无法重命名；
本次未修改这些存储实现、测试或 Room schema，也未将这组失败计作通过。

新增回归针对导航栏 owner 的替换／预览恢复，以及 UIKit 展示中关闭和系统关闭后的去重；
不以外观、尺寸或控件文案作为断言。iPad 容器迁移的现有回归已按两个嵌套导航控制器更新。

### 真机反馈后的收缩迁移验证（2026-11-12）

撤回复杂控件适配并修正导航安全区后，再次执行 CI 的格式、全量 JVM／Robolectric／桌面测试、
Android lint、Debug APK 与 R8 Release bundle 构建，以及 16 项发布脚本测试。
格式、lint、构建与脚本测试通过；2,643 个回归用例中仍有相同的 34 项 Windows 存储失败、
215 项按配置跳过，与上述基线逐项比对无新增失败。依赖锁、数据库 schema 与资源重名检查无变化。

设计系统的 iOS Arm64 编译通过；本轮修改的导航容器、Auto Layout 安全区约束与保留的
UIKit 菜单操作也通过固定 Kotlin/Native 2.4.20 工具链的局部 API 编译。完整 iOS 包由
macOS 流程构建，截图对应的视觉结果、键盘与手势仍需在设备上确认。
