# iPad 窗口适配

选择性移植自 `eddielee214/nodyssey-wa`（GPL-3.0），参考提交：

- `639eb9f0506453fa873f84811a438d7dd6298737`：原生分栏、选中态、宽屏页面排布。
- `57c1f1ca6edd9b7aa263a75811d46e0b3e94b7a9`：左栏返回关闭对应列表及其详情。
- `0c49e9b61aab5f918ca27171b9fbe26292644be7`：主题帖、评论、收藏、历史成为列表栏。
- `63a1a311f9cceb31d44b94b8ba1f7058a886d377`：宽窗口居中回复卡片。

不引入热榜、站点切换或对方的主题配置；保留本项目的帖子预览、共享元素和预测性返回策略。

## 导航和窗口

iOS 仅在 iPad 且应用窗口宽度至少 840pt 时启用双栏，左栏宽 360pt。按标签控制器的
`view.bounds` 判断，支持旋转、分屏和台前调度，不读取整块屏幕的尺寸。

`PaneLayout` 把导航栈分成独立页面和列表／详情组合；每个组合在外层导航控制器中占一页，
右栏另持有一个原生导航栈。UIKit 返回手势按组合所代表的条目数回写同一份 `NavBackStack`。
改变窗口宽度只重新安放 `StackMirror` 持有的控制器，保留它们的 ViewModel。

原生布局回调只提交窗口更新，不在回调内更换导航容器。有旋转或导航动画时，
通过 UIKit transition coordinator 等待结束，再投递到主队列，按当时的真实 bounds 切换单／双栏。
这避免在 Compose 旋转动画仍使用原视图时重新挂载页面；动画期间的请求会合并，保留重新分栏的需求。
各标签记录已安装的宽度模式，返回回写仍按当前实际显示的页面计数。

列表选中另一项替换右栏，从正文内继续导航则压入右栏。首篇详情不显示返回箭头，后续详情
保留箭头。左栏自己的返回关闭该列表及其上方页面。再次选择同一帖子不会因为标题预览更新
而重建正在阅读的页面；同帖不同楼层仍会导航。

返回动画期间的新选择或新链接会等待动画结束再同步。UIKit 回写返回时只移除仍与旧页面
匹配的栈条目，保留等待中的新导航；已由按钮移除的页面不会再被手势回调重复删除。

双栏右侧的嵌套 `UINavigationController` 在交给外层导航控制器前就以占位页为根；
收起分栏时也先换回占位页再释放正文，不能把仍在显示的嵌套导航栈清空。
页面离开导航栈后，`parentViewController` 已为空时，视图仍可能留在旧导航容器中；
UIKit 的层级检查会因此拒绝把页面加入双栏。迁移前先从旧视图层级移除页面，详情页在释放和
重新挂载时同样清理。`PaneSplitControllerTest` 在 iOS 模拟器上覆盖根页面初始化、释放后
重新挂载，以及父指针已清空而视图仍在旧容器中的迁移。

这里保留小型 `PaneSplitController`，使用 UIKit 公开的子控制器容纳 API。
Apple 的 `UISplitViewController` 文档明确禁止把它推入 `UINavigationController`，而本项目
的列表／详情组合仍需作为原生导航栈中的一页。若以后改成 split root，应移除这个容器。

与参考分支不同，本实现保留 UIKit 提供的安全区，不遍历系统视图查找浮动标签栏，也不抵消
推测出来的顶部留白。支持时通过公开的 `setTabBarHidden` 隐藏全屏二级页上的 iPad 标签栏。

官方依据：<https://developer.apple.com/documentation/uikit/uisplitviewcontroller>

## 共享界面

- 主题帖、评论、收藏、历史与首页、搜索、通知、个人空间共享列表／详情规则，Android
  平板也会使用这些共享导航改进。
- 「我的」可用宽度至少 840dp 时使用两列，整体最多 1040dp；较窄窗口沿用单列。
- 回复编辑器可用窗口宽高均至少 600dp 时使用最宽 640dp 的 Dialog，正文及草稿状态仍由
  同一个编辑器持有；发布期间各关闭路径继续受保护。手机横屏保留底部弹层。
- 顶栏通过 Material `windowInsets` 与阅读列对齐；分栏中的顶栏按自身可用宽度计算。
- iPad 不显示单手模式设置、引导和通知页提示，不修改已保存的手机偏好。

## 验证

`PaneRoleTest` 覆盖右栏替换、重复选择、不同楼层、嵌套列表关闭和返回箭头的导航条件；
`PaneLayoutTest` 覆盖分组及返回计数；`StackMirrorTest` 覆盖控制器保留与释放，以及返回
动画期间的替换、追加、重复返回回调和取消手势。
`WindowLayoutDispatcherTest` 覆盖布局回调退出、旋转完成、取消和请求合并的执行顺序。

宽屏共享界面可通过现有渲染入口检查：

```sh
./gradlew :ui:testAndroidHostTest -PrenderUi --tests '*ProfileScreenRenderTest' --tests '*ComposerRenderTest'
```

新增输出为 `ui/build/outputs/renders/ipad-profile-light.png` 和 `ipad-reply-light.png`。
它们是 Android/Robolectric 对共享界面的渲染，不代表 iPad 原生壳已经验收。

Windows 不能编译或运行 UIKit 部分。合入发布前需要 macOS 的 iOS 编译及以下设备验收：

1. 在窄／宽窗口间来回调整，核对列表滚动位置、正在阅读的楼层和回复草稿。
   先在首页竖屏启动，反复切换横竖屏，确认旋转结束后分栏且进程仍存活（iOS 27.2 报告的闪退路径）。
2. 连续选择多个帖子，再从正文打开第二篇；检查右栏替换、按钮返回、完成及取消的滑动返回。
3. 从帖子打开用户空间再打开帖子，返回左侧空间页，核对回到原先那篇帖子。
4. 检查通知／私信、收藏、历史和我的主题帖／评论，确认不同标签栈互不影响。
5. 打开图片、网页、登录和设置再返回，核对顶部标签栏的显隐及安全区。
6. 弹出／收起键盘、打开表情和预览、发布过程中尝试关闭回复框，确认编辑器可用且草稿保留。
7. 在 iPhone 横竖屏回归单栏导航；分别在 iOS 26 以上和较早系统检查标签栏。

## 本地结果（2026-11-02）

- 分栏导航、分组和 StackMirror 定向测试通过；宽屏及手机编辑器／个人页渲染通过，并已目视检查宽屏图片。
- `spotlessCheck`、`:app:lintDebug`、`:app:assembleDebug`、`:app:bundleRelease` 和发布脚本的 16 项测试通过。
- `jvmTest` 通过；设计系统与富文本的 Android host 测试通过。
- 完整 Android host／应用单元测试未全绿：`:shared` 809 项中 14 项因 Windows DataStore
  文件重命名失败；`:ui` 812 项中 2 项偏好同步测试失败（188 项默认跳过）；`:app` 169 项中
  21 项 Room 迁移测试因 Windows 数据库路径匹配失败。
- 在干净的 `dev@381f84f4` 工作树单独运行 `SearchAvatarSettingTest`、
  `PreferencesViewModelTest` 和 `NodeSeekDatabaseMigrationTest`，分别复现 1、2 和 21 项
  同类失败。这些数据层、偏好和迁移实现均未在本次修改。
- 未配置 Android 模拟器，未运行设备测试；UIKit 编译和 iPad 真机验收仍待 macOS／iPad。

## 首轮旋转修复验证（2026-11-02）

- iOS 27.2 的录像显示首页在竖屏转横屏的动画中退回桌面。本次修复控制器在布局／旋转回调中
  即时换父容器的时序；没有崩溃堆栈，仍需原设备确认是否消除闪退。
- `WindowLayoutDispatcherTest`、`PaneLayoutTest`、`StackMirrorTest` 和 `PaneRoleTest` 共 42 项通过，
  包括 6 项新的布局调度时序测试。
- `spotlessCheck`、`jvmTest`、`:app:lintDebug`、`:app:assembleDebug`、`:app:bundleRelease` 通过；
  发布脚本的 16 项测试通过，Room schema 无改动，Android 资源无跨模块重复。
- 完整门禁以 `--continue` 执行：`:app` 169 项中 21 项 Room 迁移测试因 Windows 数据库路径失败；
  `:shared` 831 项中 15 项因 DataStore 文件重命名失败；`:ui` 831 项中 2 项偏好同步测试失败（跳过 194 项）。
  失败类型与上述 Windows 基线一致；本次未修改这些数据层和偏好实现。
- Windows 上未执行 UIKit 编译、iOS 模拟器或 iPad 真机验证。

## 最终旋转修复验证（2026-11-03）

- 后续设备日志与可见窗口探针定位到 `UIViewControllerHierarchyInconsistency`：父指针清空后，
  页面视图仍属于旧导航容器。旧实现复现异常，迁移前移除旧视图后，普通迁移和布局回调内迁移均通过。
- `1.3.8-ios-test.4`（123）包含最终修复，用户已在原 iOS 27.2 设备确认测试正常。
- 完整 Linux CI 通过，包括单元测试、lint、Debug／Release 构建及设备测试。
- macOS 共享 Kotlin/Native 测试、iOS UIKit 迁移测试及真机／模拟器构建通过。
- iOS 26.5 的 11 英寸和 13 英寸 iPad 模拟器通过连续横竖屏切换及横屏冷启动检查。
  自动旋转测试副本启用全屏兼容模式，以允许系统接受程序发起的方向请求；交付 IPA 保持原有多任务配置。
