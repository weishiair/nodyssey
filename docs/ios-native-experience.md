# iOS 原生体验改造：开发文档

> 状态：试点为 PR #93；一期于 2026-11-20 经 PR #107 合入 `dev`。第二批和 SF Symbols 的上一轮真机验收见 §5.4。
> 2026-11-21，二期按实际交付范围收尾。原生面板、代理表单、设置视觉统一和主界面打磨已合入 `ios/native-experience`，见 §5.2 和 §5.5。
> 集成分支等待本轮真机和模拟器验收。用户确认后，再跑全量门禁并另开合入 `dev` 的 PR。2026-11-22 的追加改动见 §5.5 末尾。
> 旧分支 `codex/ios-experience-integration` 的做法已放弃，本文不以它为基线；其中仍有价值的个别修复见 §10。

本文写给两类读者：

- **Devin**：负责基础设施、iOS 原生代码、需要 Mac 验证的 PR，以及所有 PR 的验收。
- **协作 AI**：负责可以只在 Windows / Linux 上完成、能用 Android 渲染图逐像素核对的迁移和替换类 PR。

协作 AI 开工前必须完整读完 §1–§3、§7 和自己那张任务卡；遇到 §7.6 列出的情况必须停下报告，不要自行绕过。

---

## 1. 开发目的

### 1.1 要达成什么

**让 iOS 用户用起来像一个 iOS App，同时不让维护成本随页面数增长。**

具体说是三件事：

1. **体验**：iOS 上用户直接接触的系统级交互——设置列表、开关、选项菜单、确认框、底部面板、图标、点击反馈——
   用 iOS 系统自己的实现，而不是 Compose 画出来的 Material 或"仿 iOS"控件。
2. **一份定义**：每个页面、每段业务逻辑仍然只写一次。平台差异集中在 `:designsys` 的少数渲染器和组件里，
   页面代码里不出现 `if (iOS)`，也不出现 `IosXxxLayout` 这类按平台分叉的页面。
3. **Android 不受影响**：Android 的外观和行为保持不变，迁移页面的 Android 渲染图必须改动前后逐像素一致。

### 1.2 明确不做

- 不把 App 重写成 SwiftUI / UIKit；内容页（首页、帖子、编辑器、私信、搜索等）继续用 Compose。
- 不改 Android 视觉，不改业务逻辑、Repository、Room、网络层。
- 不在 Compose 行里逐个嵌入原生控件（旧分支的做法，裁切、黑底、大字号回退等问题都出自这里）。
- 不追求"像素级仿 iOS"：要么交给系统原生实现，要么保持现状，不做中间态的仿制品。
- 不加新依赖（需要时单独提出，经用户确认）。

### 1.3 怎样算成功

见 §9「最终验收目标」。一句话：iOS 上的设置类页面、确认框、面板、图标和点击反馈都是系统原生的；
Android 渲染图不变、交付门禁全绿；页面代码没有平台分叉。

---

## 2. 现状与盘点

### 2.1 已完成：PR-0 试点（分支 `ios/native-settings-spike`）

把"通知"页改成**描述驱动**：页面先构建一份与平台无关的 `SettingsPage` 描述，再由各平台渲染。

| 文件 | 作用 |
|---|---|
| `designsys/src/commonMain/.../settings/SettingsPage.kt` | 描述模型：`SettingsPage` / `SettingsSection` / `SettingsItem`（`Toggle`、`Choice`、`Link`）/ `SettingsSectionStyle`；以及 `shape()`（决定 iOS 表格是否整表重载） |
| `designsys/src/commonMain/.../settings/AdaptiveSettingsPage.kt` | `expect fun AdaptiveSettingsPage(page, onBack, modifier, fallback)` |
| `designsys/src/jvmCommonMain/.../settings/AdaptiveSettingsPage.jvm.kt` | Android / 桌面：直接调用 `fallback`（即 Compose 渲染） |
| `designsys/src/iosMain/.../settings/AdaptiveSettingsPage.ios.kt` | iOS 原生渲染器（见 §4.3） |
| `ui/src/commonMain/.../ui/settings/SettingsPageContent.kt` | `SettingsPageScreen`：入口；以及 Compose 渲染器（复刻现有卡片和行） |
| `ui/src/commonMain/.../ui/settings/NotificationSettingsScreen.kt` | 第一个迁移的页面，**迁移时照这个写** |
| `designsys/src/commonTest/.../settings/SettingsPageShapeTest.kt` | `shape()` 判定测试 |
| `ui/src/androidHostTest/.../ui/settings/NotificationSettingsScreenTest.kt` | 主开关联动、频率选择回调值测试 |

已验证：

- Android：`NotificationSettingsScreenRenderTest` 的 5 张图改动前后逐像素一致。
- iOS（Xcode 27.2，iOS 27.2 模拟器）：真机路径 我的 → 设置 → 通知、行跳转、边缘返回、原生返回；
  夹具中验证开关、菜单选择、主开关禁用联动、深色、Accessibility XL 字号。

已知问题：Compose 宿主内打开原生弹出菜单时会带出软键盘（§10 R1）。已在 D2 定位并修复，见任务 D2 的结论。

### 2.2 盘点数据（2026-11-19，`origin/dev` = `aab7303a`）

`:ui` 共约 69,400 行。按处理方式分三类：

| 类别 | 页面 | 处理 |
|---|---|---|
| **A 设置/表单类** | 设置首页、通知（已完成）、代理、加密 DNS、账号设置、开源许可 | **迁到描述驱动**，iOS 整页原生 |
| **A' 列表管理类** | 屏蔽列表、标题关键词、追踪的帖子、账号列表 | 第四阶段再评估（需要可编辑列表能力） |
| **B 视觉自定义重** | 图床、网络自检、关于、社区、更新日志、联系方式、资料字段、安全、主题设置、隐私、偏好设置（存储标记、板块色标）、动态取色（壁纸色块、调色板色阶） | 不迁，保留 Compose，只吃 C 类的组件级改造 |
| **C 内容页** | 帖子、编辑器、首页、私信、搜索、收藏、通知、我的、表情、工具等 | 不重写，在组件层换掉安卓惯用法 |

C 类涉及的组件用量：

| 组件 | 用量 | 说明 |
|---|---|---|
| `AlertDialog(` | 35 处 / 26 个文件 | 其中 15 处只有文字和按钮（清单见附录 B），可直接换成原生确认框 |
| `PlazaSheet(` | 14 个调用文件 | 已是统一入口；另有 2 处直接用 `ModalBottomSheet`（`ReplyComposer.kt`、`MessageThreadScreen.kt`） |
| `Icon(` | 295 处 | Material 图标直接引用 256 处 / 77 个文件；`PlazaIcons.` 273 处 / 70 个文件 |
| `DropdownMenu(` | 23 处 / 18 个文件 | 第四阶段再评估（锚定原生菜单风险高） |
| Snackbar / FAB | — | `PlazaSnackbarHost`、`PlazaExtendedFab` 在开启毛玻璃时已有 iOS 原生玻璃实现，本轮不动 |

### 2.3 内容页盘点（2026-11-20，`origin/dev` = `4f0cf1fa`）

一期合入后，在模拟器上逐屏检查了首页、帖子详情、通知、私信、我的（已登录，浅色 / 深色），并对照代码。结论：

- **现有的原生控件默认不生效。** 液态玻璃标签栏、帖子详情的原生顶栏和"…"菜单、发帖 / 回复 / 翻页玻璃按钮、提示条，
  都由 `PlazaLayers.nativeGlass`（= 「毛玻璃质感」开启 && iOS 26+）控制。该开关默认关闭，关闭时全部退回 Compose 的 Material 版本。
- **D2 的菜单键盘修复只覆盖设置页**（`AdaptiveSettingsPage.ios.kt`）；`AdaptiveGlass.ios.kt` 里的原生菜单（帖子详情"…"等）没有。
- **约 50 个页面用 Material `TopAppBar`**（安卓返回箭头、竖三点），私信会话最明显。`AdaptiveGlassTopBar` 只有帖子详情在用，
  且只支持一行纯文字标题。
- 首页排序、切换站点等按钮菜单是 Material `DropdownMenu`；通知页的切换是下划线标签；图标全部是 Material。
- 长按帖子卡片没有菜单；楼层操作、私信附件是 Material 底部面板。这几项属于 §5.2 的二期范围，第二批不做。

---

## 3. 技术方向与硬性规则

### 3.1 分层原则：交给原生的部分，必须整块由原生管理

| 层 | 归属 | 例子 |
|---|---|---|
| 外壳 | 原生（dev 已完成） | 标签栏、每个标签一个 `UINavigationController`、系统返回手势 |
| 设置/表单页 | **整页原生**，由描述驱动 | 一个 `UIKitView` 承载整页 `UINavigationBar` + `UITableView` |
| 模态界面 | **整块原生** | `UIAlertController`、`UISheetPresentationController`、`UIMenu` |
| 内容区 | Compose | 列表、帖子、编辑器；只在组件层替换图标、点击反馈等 |

### 3.2 硬性规则（所有 PR）

1. **页面只定义一次。** `ui/src/commonMain` 里不允许新增按平台分叉的页面布局；平台差异只能放在
   `:designsys` 的渲染器 / `Adaptive*` 组件里，或 `NativeShell`。
2. **不在 Compose 内容里逐个嵌入原生控件。** 原生只以"整页"或"整块模态"出现。
3. **Android 渲染图逐像素一致。** 任何迁移/替换 PR，受影响页面的渲染图必须和基线逐像素相同；
   有意的视觉变化必须单独提出并经用户确认。
4. **`:designsys` 不得引用论坛业务类型**（编译期保证，不要绕）。文案、选项映射、导航都在 `:ui`。
5. **文案只走 Compose Resources**，三种语言各自更新；禁止新增中文字面量（`HardcodedChineseLiteralTest` 会拦）。
6. **不加依赖、不动 Room、不改 `gradle.properties` 版本号、不改 lockfile。**
7. 遵守 `AGENTS.md` 的代码风格、测试准则和提交规则；本文与 `AGENTS.md` 冲突时以 `AGENTS.md` 为准。

---

## 4. 架构说明

### 4.1 数据流

```
ViewModel ── UiState ──▶ XxxScreen（@Composable，commonMain）
                           │  用 stringResource 构建
                           ▼
                     SettingsPage（纯描述：分组、行、文案、当前值、回调）
                           │
                SettingsPageScreen（ui/settings/SettingsPageContent.kt）
                           │
                 AdaptiveSettingsPage（designsys，expect/actual）
                    ├─ Android / 桌面：fallback → ComposeSettingsPage（现有卡片和行）
                    └─ iOS：NativeSettingsPageHost（整页 UIKit）
```

- Route 和 ViewModel **不变**；Screen 的公开签名**不变**（渲染测试、预览依赖它）。
- 回调是普通 Kotlin lambda，原生控件触发时在主线程直接调用，和 Compose 点击完全一样。
- 文案在 Compose 里解析，所以 Android 应用内切换语言照常生效；iOS 本来就是重启后生效。

### 4.2 描述模型（D1 合入后的实际 API）

模型在 `designsys/.../settings/SettingsPage.kt`，Compose 渲染器在 `ui/.../ui/settings/SettingsPageContent.kt`。

```kotlin
SettingsPage(title, sections, intro = null, showRowIconsOnIos = false)
SettingsSection(key, items = emptyList(), header = null, footer = null, style = Grouped)
SettingsSectionStyle: Grouped | Prominent（主开关卡片）| Callout（指向页外的卡片）
SettingsOption(label, swatch: Color? = null, enabled = true)   // 单选的一个答案；swatch 是已读标记那种色块
SettingsChoicePresentation: Sheet（底部面板单选）| Menu（行尾菜单）| Segmented（标题下的分段）
SettingsSample(text, fontSizeSp)                        // 滑块下跟着字号变的示例文字

// 每种行都有：key、title、icon?、enabled、testTag?（Compose testTag / iOS accessibilityIdentifier）
SettingsItem.Toggle(key, title, checked, onCheckedChange, description?)
SettingsItem.Text(key, title, value, onValueChange, placeholder?, keyboard = Text, secure = false, errorMessage?)
SettingsItem.Choice(key, title, options: List<SettingsOption>, selectedIndex, onSelect(Int),
                    prompt?, presentation = Sheet, description?)
SettingsItem.Slider(key, title, value, range, steps, onValueChange(Float), onValueChangeFinished(),
                    valueLabel?, sample?, description?)
SettingsItem.Link(key, title, onClick, description?, value?, badge = false, external = false)
SettingsItem.Action(key, title, onClick, description?, value?, destructive = false, loading = false)
SettingsItem.Info(key, title, value?, description?)                  // 只读：不可点、无箭头（签到状态）
SettingsItem.Custom(native: SettingsItem, compose: @Composable (SettingsRowPosition) -> Unit)

// 页面入口（:ui 内部）
SettingsPageScreen(page, onBack, modifier, layout = SettingsPageLayout.Standard, snackbarHostState = null)
SettingsPageLayout.Standard | Account | Licenses       // 只影响 Compose 的页边距、组间距、脚注内边距、小号图标
```

- `key`：页面内唯一、跨重组稳定，用英文 kebab-case（如 `"wifi-only"`），**不要用下标或文案**。
- `intro` 是首个分组前的说明。设置首页用 `showRowIconsOnIos = true` 将 `icon` 解析为行首 SF Symbol，设置子页沿用默认值。
- `value` 是"目的地当前的状态"（如「3 个」「4.3 MB」）：Android 画在副标题，iOS 画在右侧。
- `description` 是解释性文字：两端都画在标题下方。`Choice(Sheet)` 没有 `description` 时，Android 副标题显示当前答案。
- 区块 `footer`：有行时是区块下方的小字；没有行时（只有 header + footer）就是一段说明。
- `Slider`：`value` 是滑块当前显示的值（拖动中由页面自己保存），`onValueChange` 每次移动都调用，
  `onValueChangeFinished` 松手时调用一次，页面在这里写入设置。`steps` 的含义和 Compose `Slider` 一样（两端之间的停靠点数，0 = 连续），
  iOS 端用 `snapToSteps` 吸附到同样的停靠点。
- `Choice(Segmented)`：只给 2～4 个短答案用。iOS 在辅助功能字号或超过 4 项时自动退回菜单。
- `Choice(Menu)` 在 iOS 的辅助功能字号下，答案按钮移到标题下一行（和系统设置一致）。
- `Action.loading = true` 时两端都显示转圈，并且不再响应点击；`destructive` 两端都画成红色。
- `Info`：只展示当前状态，没有点击。Android 是不带点击的 `GroupedRow`；iOS 值在右侧，过长时系统自动移到标题下。
- `Link.external = true`：外链图标、无箭头；`Link.badge = true`：更新红点。
- `Custom`：只给 Compose 端现有组件无法复刻的行用（如设置首页的搜索头像预览卡）。`native` 是同一个设置的普通行，
  拿着同样的状态和回调，iOS 画它。每个 `Custom` 都是"一行两份画法"，能不用就不用，用了要在 PR 里说明。
- `layout`：只为让既有页面迁移后 Android 逐像素不变，**不是**给新页面挑样式用的。新页面一律 `Standard`。
- `snackbarHostState`：页面有提示消息时传入。Android 放在 `Scaffold` 里；iOS 叠在原生页上方。
- `Text` 的 `SettingsKeyboard` 支持 `Text`、`Uri`、`Number`。`secure` 隐藏输入，`errorMessage` 在字段下显示校验错误。代理页的测试和保存分别使用 `Action`。
- 需要带标题的多段脚注、独立横幅或可拖拽行时，按 §7.6 报告描述模型的缺口。

### 4.3 iOS 渲染器要点（`AdaptiveSettingsPage.ios.kt`）

- **整页一个 `UIKitView`**，`NonCooperative`、开启原生无障碍，**不用** `placedAsOverlay`
  （这样 shell 叠在页面上的 Compose 提示仍在最上层）。
- **独立的 `UINavigationBar`**：shell 隐藏了导航控制器自带的栏。放两个 item 得到系统返回按钮；
  `navigationBar(_:shouldPop:)` 把返回交给 back stack 并返回 `false`；`positionForBar` 返回 `TopAttached`。
  列表延伸到栏下，通过顶部 inset 留出导航栏高度。页面在顶部时栏透明，滚动后使用系统模糊材质。键盘避让只更新底部 inset。
- **`UITableView(.insetGrouped)`**，单元格按 `kind:key` 常驻、不复用；
  `shape()` 不变时原地更新（保留开关动画和已打开的菜单），变化时才 `reloadData()`。
- 开关 `UISwitch`（强调色），单选是弹出按钮 + `UIMenu`（菜单标题为 `prompt`），跳转行是系统箭头。
- 页面和卡片用 `PlazaLayers.page` 与 `PlazaLayers.card`。浅色纯白模式下两色相同时，页面增加轻微灰阶差以区分分组。
  文字使用 UIKit 的 `label` 系列，开关使用 App 强调色，字号由系统 Dynamic Type 和 App 内字号共同决定。
- `LocalEinkMode` 开启时直接渲染实色 Compose 页面。
- UIAction 只捕获不持有视图的状态对象，避免 ARC 循环（照现有写法）。

### 4.4 何时**不**迁移一个页面

满足任一条就保留 Compose：页面有两处以上无法用描述表达的自定义块（预览卡、色板网格、报告图表等）；
主体是可编辑列表或富文本；交互不是"设置项"而是"内容"。拿不准时问 Devin。

### 4.5 统一顶栏 `AdaptiveTopBar`（D7）

文件：`designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/AdaptiveTopBar.kt`。纯 common 代码，没有平台分叉。

```kotlin
AdaptiveTopBar(
    title: String,
    state: OneHandAppBarState,          // 照旧 rememberOneHandAppBarState()，Scaffold 的 nestedScroll 不变
    onBack: (() -> Unit)?,              // null = 没有返回按钮
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    closeInsteadOfBack: Boolean = false, // 模态页用叉号（Android Close / iOS xmark）
    actions: List<TopBarAction> = emptyList(),
)

// 页面原来没有单手留白（TopAppBar 或 ReadableTopBar { TopAppBar }）时用不带 state 的重载：
AdaptiveTopBar(
    title: String,                       // "" = 无标题，Android 的标题槽留空
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    closeInsteadOfBack: Boolean = false,
    actions: List<TopBarAction> = emptyList(),
    containerColor: Color? = null,       // 照抄原 colors 的 containerColor；null = Material 默认
    maxContentWidth: Dp? = null,         // 原来包了 ReadableTopBar 的传它（默认宽度即 Sizes.readableContentWidth）
)

data class TopBarAction(
    val glass: GlassAction,                       // iOS：文案、SF Symbol、enabled、menu
    val icon: ImageVector? = null,                // Android：生成 IconButton(onClick = glass.onClick, enabled = glass.enabled)
    val compose: (@Composable () -> Unit)? = null, // Android：整段原样使用，代替生成的按钮
)
```

**各平台画什么**

| 情况 | 画法 |
|---|---|
| Android / 桌面 | 原来的 `OneHandTopAppBar`：`ArrowBack`（或 `Close`）`IconButton` + 标题 / 副标题 + 每个动作一个 `IconButton` 或 `compose`。迁移后渲染逐像素不变 |
| iOS，系统控件可用 | 系统 `UINavigationBar`：返回 `chevron.backward`，按钮为 SF Symbol，菜单为 `UIMenu`，副标题用系统的 `UINavigationItem.subtitle` |
| iOS，单手模式开 | 仍是 Compose 的 `OneHandTopAppBar`（单手模式的意义就在顶部留白） |
| Android，原来是普通 `TopAppBar` | 不带 `state` 的重载：画原来的 `TopAppBar` / `ReadableTopBar`，逐像素不变；iOS 一律系统导航栏（不受单手模式影响） |

返回按钮的文案（返回 / 关闭）由组件自带，页面不用传。

**用哪个重载**：原来是 `OneHandTopAppBar`（带单手留白）的页用带 `state` 的；原来是 `TopAppBar` 或
`ReadableTopBar { TopAppBar }` 的页用不带 `state` 的——`containerColor` 照抄原 `colors` 的 `containerColor`，
原来包了 `ReadableTopBar` 的传 `maxContentWidth`（默认宽度即 `Sizes.readableContentWidth`）；
图标改过尺寸的（如 `Modifier.size(20.dp)`）用 `compose` 原样保留。

**什么时候用 `icon`，什么时候用 `compose`**

- 按钮在 Android 上就是一个 `IconButton(onClick) { Icon(图标, 文案) }`：只填 `icon`，删掉原来的按钮代码。
- 按钮在 Android 上带自己的下拉菜单、角标或其他内容：填 `compose`，把原来那段整个放进去，**一行不改**；
  iOS 的菜单另写在 `glass.menu` 里。两边的菜单项必须一一对应，所以 `glass` 的构造要抽成函数并写测试（见示范页）。

**菜单项 `GlassMenuAction`**

| 字段 | 用途 |
|---|---|
| `label` / `onClick` / `symbol` | 文案、动作、SF Symbol |
| `subtitle` | 菜单项下的小字（例如当前取值） |
| `selected` | 打勾 |
| `enabled` | 灰掉 |
| `destructive` | 系统红色，用于删除、清空这类操作（对应 Android 里用 `error` 色的菜单项） |

Android 菜单在列表上方或下方有一行说明文字的，把它放进 `GlassAction.menuTitle`，iOS 显示在菜单顶部（例：首页切换站点菜单的"两个站点分开登录……"）。

**示范页**：`ui/.../history/ReadHistoryScreen.kt`。

- 标题 + 副标题 + 返回；
- 溢出菜单用 `compose` 保留 Android 原来的 `HistoryMenu`，iOS 菜单由 `historyMenuAction()` 构造；
- 测试 `ReadHistoryScreenTest.the native overflow runs each entry's own action and offers clearing only with history`。

**Material 图标 → SF Symbol 常用对照**

| Material | SF Symbol | Material | SF Symbol |
|---|---|---|---|
| `ArrowBack` | `chevron.backward`（组件自带） | `Close` | `xmark`（组件自带） |
| `MoreVert` / `MoreHoriz` | `ellipsis` | `Refresh` | `arrow.clockwise` |
| `OpenInNew` / 在浏览器打开 | `safari` | `Share` | `square.and.arrow.up` |
| `Search` | `magnifyingglass` | `Settings` | `gearshape` |
| `Add` | `plus` | `Delete` | `trash` |
| `Edit` | `pencil` | `Check` / `Done` | `checkmark` |
| `DoneAll`（全部已读） | `checkmark.circle` | `Sort` / `SwapVert` | `arrow.up.arrow.down` |
| `FilterList` | `line.3.horizontal.decrease` | `History` | `clock.arrow.circlepath` |
| `Bookmark` | `bookmark` | `Notifications` | `bell` |
| `ContentCopy` | `doc.on.doc` | `Link` | `link` |
| `Person` | `person` | `Mail` | `envelope` |
| `Info` | `info.circle` | `Help` | `questionmark.circle` |
| `PlazaIcons.Badge`（备注） | `person.text.rectangle` | | |

对照表里没有的，挑 SF Symbols 里意思最接近的，并在 PR 描述里列出，由 Devin 在 Mac 上核对。

**不适用**：标题区有纯文字以外内容的（头像、标签页、搜索框），顶栏有编辑模式切换、单手留白以外的
滚动折叠等特殊行为的。这类页面保持现状，记入跳过清单。
（单一 `containerColor` 的普通 `TopAppBar` 页面不在此列——用不带 `state` 的重载。）

---

## 5. PR 总表与依赖

**2026-11-19 定：先做一期。** 一期只做用户最常接触、风险可控的部分——设置类页面和确认框；做完出一个 iOS 测试包，
真机验收、收集反馈后，再决定二期做不做、做哪些。二期的任务卡（§6）保留原样，届时直接启用，不必重写本文。

### 5.1 一期分工（已完成，2026-11-20 经 PR #107 合入 `dev`）

**每个任务都是"开发 + 测试 + 自验"一起交付**：任务卡里的测试和验收清单就是任务本身的一部分，不单独拆测试 PR。
一个任务一个分支、一个 PR，目标分支都是集成分支 `ios/native-experience`（§7.1）。

**交接只需要一句话**：

> 请按仓库 `weishiair/nodyssey` 中 `docs/ios-native-experience.md` 的任务 **C1**（或 C2、C3）执行。先读该文档 §1–§3、§7 和任务卡，遵守仓库根目录的 `AGENTS.md`。

任务卡里写全了分支、步骤、测试、验收清单和交付方式，不需要另外补充说明。

#### Devin 的任务

| 任务 | 对应 | 内容 | 依赖 | 顺序 |
|---|---|---|---|---|
| D1 | PR-2 | 描述模型扩展 A：C1、C3 需要的全部能力（含 Compose 页面布局预设，保证 Android 逐像素不变） | — | **1**，先做，解除 C1 第 2 段和 C3 的等待 |
| D2 | PR-1 | iOS 原生菜单不再带出软键盘 | — | 2 |
| D3 | PR-7 | 原生确认框 `AdaptiveAlertDialog` | — | 3，解除 C2 的等待 |
| D5 | — | 验收协作 AI 的 PR；一期整体验收（§9）；出 iOS 测试包；开 `ios/native-experience → dev` 的 PR | 全部 | 持续 |

（原 D4「迁移设置首页」改为协作 AI 的 C3。）

#### 协作 AI 的任务

| 任务 | 内容 | 依赖 | 什么时候能做 |
|---|---|---|---|
| C1 | 迁移账号设置、开源许可（补渲染测试 → 迁移 → 逐像素对比 → 行为测试） | 第 1 段无；第 2 段等 D1 | **第 1 段现在就能做**；D1 合入集成分支后做第 2 段 |
| C2 | 把只有文字和按钮的确认框换成 `AdaptiveAlertDialog`（含测试） | D3 | D3 合入集成分支后 |
| C3 | 迁移设置首页（含测试） | D1（iOS 验收另需 D2） | D1 合入集成分支后 |

```
D1 ──┬── C1 第 2 段
     └── C3 ──（iOS 验收等 D2）
D2
D3 ── C2
C1 第 1 段（现在）
```

一期结束的标志：上表全部合入集成分支 `ios/native-experience`，§9 中标为「一期」的目标全部通过，
并从集成分支出一个 iOS 测试包给用户真机验收；通过后再由单独 PR 把集成分支合入 `dev`。

### 5.2 二期交付状态

| 任务 | 内容 | 合入状态 |
|---|---|---|
| PR-3 | 描述模型扩展 B：表单 | **已交付（缩减范围）** #128。文本输入、行内校验、键盘避让和回车切换 |
| PR-6 | 代理与加密 DNS | **已交付（缩减范围）** #128。代理页使用原生表单，加密 DNS 保留 Compose |
| PR-9 / PR-10a / PR-10 | SF Symbols 基础设施、清单与调用点迁移 | **已完成** #123，见 [图标清单](ios-sf-symbols.md) |
| PR-11 | iOS 按压样式与触感 | **已交付 Part B** #126。开关与刷新接入系统触感，按压样式见 §10 R7 |
| PR-12 | 原生底部面板 | **已完成** #127。iOS 26+ 的共享面板和工具栏自定义使用系统 sheet |
| PR-13 | 直接使用 `ModalBottomSheet` 的两处面板 | **已交付（缩减范围）** #127。回帖面板接入，私信会话信息面板保留现有行为 |

### 5.3 第二批分工（2026-11-20 定）

一期已全部合入 `dev`（PR #107）。第二批针对 §2.3 的发现，只用现成组件或小幅扩展，不新增整页原生实现。
目标分支仍是集成分支 `ios/native-experience`（§7.1）。交接方式同 §5.1：

> 请按仓库 `weishiair/nodyssey` 中 `docs/ios-native-experience.md` 的任务 **C4**（或 C5）执行。先读该文档 §1–§3、§7 和任务卡，遵守仓库根目录的 `AGENTS.md`。

**用户已定（2026-11-20）**：iOS 26 及以上一律使用系统控件，不再受「毛玻璃质感」控制，iOS 26+ 上隐藏该开关；
墨水屏模式例外，仍用 Compose 实色控件；iOS 26 以下和 Android 不变。理由：

- iOS 26 的系统控件本身就是液态玻璃，没有"实色原生"可选，自绘实色版就是 §1.2 说的仿制品；
- 系统控件自动遵循"降低透明度""增强对比度"，Compose 版不会；
- iOS 26+ 只剩一条渲染路径，验收量减半；
- 该开关默认关闭，多数 iOS 用户目前看到的是 Material 版本。

**用户已定（2026-11-23）**：iOS 26 以下同样使用系统控件，只是不带液态玻璃。门槛拆成两层：
`nativeControls`（所有 iOS，墨水屏除外）决定是否由 UIKit 绘制顶栏、浮动按钮、工具栏、提示条、菜单和底部面板；
`nativeGlassAvailable`（iOS 26+）只决定这些控件用玻璃材质，还是用同一控件在旧系统上的样式：
系统灰色 / 填充胶囊按钮、`systemMaterial` 模糊胶囊、带分隔线的磨砂导航栏、两行标题视图（`UINavigationItem.subtitle` 是 iOS 26 的 API）、
圆角矩形分段条和磨砂标签栏。`PlazaLayers.nativeGlass` 已删除。Android / JVM 上 `nativeControls` 恒为 false，渲染不变。

#### Devin 的任务

| 任务 | 内容 | 依赖 | 顺序 |
|---|---|---|---|
| D6 | 原生按钮与菜单补全：菜单键盘修复覆盖 `AdaptiveGlass` 的菜单；`GlassMenuAction` 支持副标题；`GlassAction` 支持提醒红点 | — | **1**，解除 C4 第 2 段的等待 |
| D7 | 统一顶栏 `AdaptiveTopBar`：一份描述，iOS 画系统导航栏，Android 画现在的 Material 顶栏（逐像素不变） | — | 2，解除 C5 的等待 |
| D8 | iOS 26+ 的系统控件与「毛玻璃质感」脱钩（上面的决定） | D6 | 3 |
| D9 | 验收 C4、C5；第二批整体验收；出 iOS 测试包；开 `ios/native-experience → dev` 的 PR | 全部 | 持续 |
| D10 | 私信会话：返回、⋯菜单、工具、发送换成原生玻璃按钮；顶栏卡片（头像、等级/注册天数警示）保留 | — | 4 |

#### 协作 AI 的任务

| 任务 | 内容 | 依赖 | 什么时候能做 |
|---|---|---|---|
| C4 | 首页、通知、我的三个标签页页头的按钮换成 `AdaptiveGlassButton`（带菜单的用原生菜单） | 第 1 段无；第 2 段等 D6 | **第 1 段现在就能做** |
| C5 | 二级页面的顶栏分批换成 D7 的 `AdaptiveTopBar` | D7 | D7 合入集成分支后 |

```
D6 ──┬── C4 第 2 段
     └── D8
D7 ── C5（分批）
C4 第 1 段（现在）
```

第二批结束的标志：上表全部合入集成分支，iOS 测试包经用户真机确认，再由单独 PR 把集成分支合入 `dev`。

### 5.4 第二批收尾（2026-11-21）

**合入集成分支的 PR**

| PR | 内容 | 负责 |
|---|---|---|
| #108 | 第二批任务卡 | Devin |
| #109 | D6 原生菜单不弹键盘、菜单副标题、按钮红点 | Devin |
| #110、#111 | C4 首页、通知、我的三个标签页页头按钮 | 协作 AI |
| #112 | 原生菜单开着时点菜单外面，不再连带打开下面的内容 | Devin |
| #113 | D7 统一顶栏 `AdaptiveTopBar` | Devin |
| #114 | D8 iOS 26+ 一律用系统控件（墨水屏除外），隐藏毛玻璃开关 | Devin |
| #116、#117、#119 | C5 第 1–3 批二级页面顶栏 | 协作 AI |
| #118 | 原生玻璃控件随页头收起被裁掉；单独按钮图标缩到 17pt | Devin |
| #120 | `AdaptiveTopBar` 不带单手留白的重载；用户空间迁移 | Devin |
| #121 | C5 收尾：账号列表、抽奖、表情管理 / 来源 / 添加 | Devin |
| #122 | D10 私信会话：返回、⋯菜单、工具、发送换成原生玻璃按钮 | Devin |
| #123 | 二期 PR-9 / PR-10a / PR-10：SF Symbols | Devin |

**测试包**：`nodyssey-1.3.12-ios-native-1cfc93d6.ipa`（集成分支 `1cfc93d6`，未签名，版本仍为 1.3.12 / 172），2026-11-21 用户真机确认无问题。

**仍保持原样的页面**（统一顶栏不适用）：表情分组（多选编辑态）、私信会话的顶栏卡片（头像与警示标签，仅按钮换成原生）、账号保存失败页（文字"关闭"键）。

**已知遗留**（都不是本批引入，留待后续）：

- 隐私协议页的大字号来源排版、追踪帖子空状态间距及繁体标签边界的后续进展见 §5.5。
- 渲染测试 `lottery-filter-won-phone`、`sticker-group-mine`、`sticker-manage`、`sticker-sources` 偶尔两次运行结果不同（未改动的提交上也会出现）。
- Windows 上 `PreferencesViewModelTest` 与部分 DataStore、Room 迁移测试因临时文件重命名、路径格式失败；Linux CI 通过。
- iOS 上未实测的页面：社区工具（NodeSeek 无入口）、邮件详情（打开会标记已读）、添加表情（只能从编辑器表情面板进入），由 Android 渲染逐像素一致和代码审查保证。

### 5.5 二期与集成分支收尾（2026-11-21）

第二批记录由 #124 补入本文。此后集成分支交付以下改动，链接包含各 PR 的验证记录。

| PR / 合并提交 | 交付内容 | 验证记录 |
|---|---|---|
| [#126](https://github.com/weishiair/nodyssey/pull/126) · `785407fc` | `PlatformHaptics` 接入 Compose 设置开关与下拉刷新触发点。iOS 使用 UIKit 选中和冲击反馈，Android / JVM 使用空实现 | 模拟器复核开关与刷新流程，触感强度由真机验收 |
| [#127](https://github.com/weishiair/nodyssey/pull/127) · `26bbfbfb` | `AdaptiveModalSheet` 在 iOS 26+ 使用 `UISheetPresentationController` 承载 Compose 内容，传入外层主题与 `CompositionLocal`，支持中 / 大高度和交互关闭。覆盖 `PlazaSheet`、回帖面板和工具栏自定义 | 模拟器覆盖关闭、键盘、嵌套面板、楼层操作和深色旋转 |
| [#128](https://github.com/weishiair/nodyssey/pull/128) · `239d73be` | `SettingsItem.Text` 用 `UITextField` 支持文本、地址和数字、密码遮蔽、行内校验、Next / Done 及键盘避让。代理页接入表单，测试和保存复用 `Action` | `NetworkSettingsRenderTest` 4 张 Android 图逐像素一致。#129 复测代理页焦点与避让 |
| [#129](https://github.com/weishiair/nodyssey/pull/129) · `c28cdb3a` | 原生设置页跟随 `PlazaLayers`，纯白分组增加轻微灰阶差，设置首页显示 SF Symbols。列表在导航栏后滚动，墨水屏切回实色 Compose | 37 张 Android 图逐像素一致。iOS 覆盖浅色、深色、纯白、纯黑、辅助功能 XL 和墨水屏，代理页输入框与保存可在键盘上方看到，菜单开关 20 次软键盘出现 0 次 |
| [#130](https://github.com/weishiair/nodyssey/pull/130) · `98e1bb92` | 通知页「互动 / 私信」采用跟随 pager 偏移的胶囊分段条。首页、通知、收藏、信箱、搜索和帖子详情共用自适应刷新指示器，iOS 26+ 显示无底盘的 12 刻度菊花 | 100 张 Android 图逐像素一致。iOS 验证浅色、深色和辅助功能 XL 下的点选和双向滑动，逐帧核对首页与通知页的下拉及旋转动画 |

**2026-11-22 追加**

`a7026f1f` 之后有 4 个提交直接推到了集成分支，没有经过 PR 和 CI（违反 §7.1），由后续 PR 补验证：

| 提交 | 交付内容 | 验证记录 |
|---|---|---|
| `cee1f4ee` | 回帖发布按钮放回弹层标题栏，回复预览时隐藏帖子顶栏，原生弹层为拖动条留出空间，标题和正文获得焦点时收起表情面板 | 新增 `ComposerEmojiFocusTest`；模拟器验证 |
| `e0bd471b` | iOS 原生弹层里回复编辑器填满剩余高度，格式工具栏停在键盘上方 | 聚焦测试与模拟器验证 |
| `eac5b19c` | 跳页与楼层定位弹层按内容定高，保留键盘避让和下滑关闭 | 模拟器验证数字输入与键盘 |
| `6c48f98d` | 标签栏使用未着色的系统玻璃；普通顶栏与状态栏共用页面底色，帖子详情在滚动内容上方使用磨砂 | 模拟器对照邮箱顶栏与帖子滚动 |
| `ios/native-sheet-cleanup` | 按内容定高的逻辑从 `ui/src/iosMain` 移入 `AdaptiveModalSheet(fitContent)`；`:ui` 的 local 转发集中到 `nativeSheetLocals()`，由 `NativeSheetLocalsTest` 守护（见 §10 R3） | 全量渲染 306 张 Android 图与 `a7026f1f` 逐像素一致；iOS 编译与面板复测待 Mac |

**保留现有实现的页面和交互**

- 加密 DNS 保留 Compose。服务器拖拽重排和预设选择需要可重排描述项或混合渲染，现有原生设置模型按整页接管。
- 主题、动态取色、偏好设置，以及 §2.2 B 类页面保留自定义内容布局。列表管理页沿用现有编辑行为，内容页通过共享组件获得原生控件。
- 私信会话信息面板保留 `ModalBottomSheet`。它只允许全展开，关闭按钮先等待 `SheetState.hide()`；共享原生面板提供中 / 大两档。私信的头像与警示卡片、表情分组编辑态、账号保存失败页文字关闭键沿用 §5.4 的顶栏例外。
- 复杂确认框保留 Compose，继续支持富文本、输入、自定义按钮或异步确认，具体清单见附录 B。无对应 SF Symbol 的站点图标继续使用原矢量。
- iOS 26 以下及墨水屏沿用自适应面板、通知分段条和下拉刷新组件的 Compose 回退。墨水屏的原生设置页也回退到实色 Compose。
- 全局按压高亮保留现有实现，原因见 §10 R7。首页帖子卡片长按菜单留待独立验证纵向列表、横向板块切换、轻点进帖与菜单关闭的触摸分发。

**验收状态**

- #129 与 #130 已通过 GitHub `build`、`device-test`。Windows 上已知 DataStore 和 Room 基线问题与本轮 iOS 改动分开记录，见 §5.4。
- 截至 `98e1bb92`，[#131](https://github.com/weishiair/nodyssey/pull/131) 的隐私协议大字号、追踪帖子空状态与系统繁体标签边界修复仍在独立 PR。
- 本轮模拟器 Debug 与未签名真机 Release 从文档合入后的同一集成分支提交构建，版本保持 `1.3.12 (172)`。真机包名包含提交短 SHA。用户确认后再执行合入 `dev` 的门禁。

---

## 6. 任务卡

每张卡的「验收目标」就是验收时逐条核对的清单；**全部满足才算完成**。

### 任务 D6 · 原生按钮与菜单补全（Devin）

- 把 D2 的"菜单弹出时 Compose 交出第一响应者"从 `AdaptiveSettingsPage.ios.kt` 抽到 `:designsys` iosMain 的共用位置；
  `AdaptiveGlass.ios.kt` 里所有带 `UIMenu` 的控件（`AdaptiveGlassButton`、`AdaptiveGlassToolbar`、`AdaptiveGlassTopBar`）都走它。
- `GlassMenuAction` 增加 `subtitle: String? = null`，iOS 映射到 `UIAction.subtitle`。
- `GlassAction` 增加 `badge: Boolean = false`，iOS 原生按钮右上角画系统红点。
- 两个新字段都带默认值，Compose 回退不读，Android 不变。
- **验收目标**
  - [ ] 远程桌面下手动打开帖子详情"…"菜单和设置页菜单各若干次，软键盘不出现；自动化 20 次循环 0 次。
  - [ ] 普通输入框仍能弹出键盘。
  - [ ] 副标题、红点在浅色 / 深色、大字号下截图正常。
  - [ ] Android 渲染图不变。

### 任务 D7 · 统一顶栏 `AdaptiveTopBar`（Devin）

**目标**：普通二级页的顶栏只定义一次。

- 描述：标题、可选副标题、可选返回、右侧按钮列表（每个按钮：文案、Material 图标、SF Symbol 名、点击、可选菜单项、可选红点、是否可用）。
- Android / JVM：按描述画出现有 Material `TopAppBar` 的样子；页面间常见的差异（颜色、`OneHandTopAppBar`、`ReadableTopBar` 宽度）
  作为参数提供，保证迁移后逐像素一致。
- iOS 26+：系统 `UINavigationBar`，返回为 `chevron.backward`，溢出菜单为系统 `UIMenu`；iOS 26 以下画和 Android 相同的 Compose 版本。
- 标题区有纯文字以外内容（头像、标签页、搜索框）的页面不在此组件范围。
- 在 §4.5 写明 API、迁移示例和 Material 图标 → SF Symbol 常用对照（C5 照用）。
- **验收目标**
  - [ ] 用它迁移一个示范页（`ReadHistoryScreen`），Android 渲染逐像素一致，iOS 截图为系统导航栏。
  - [ ] 返回按钮、边缘返回、菜单（20 次循环 + 远程桌面手动检查）、深色、大字号、iPad 横屏正常。
  - [ ] §4.5 已写好。

### 任务 D8 · 系统控件与「毛玻璃质感」脱钩（Devin）

- iOS 26+：`nativeGlass` 不再取决于 `frostedGlass`（墨水屏模式除外）；`frostedGlass` 参与的配色和布局间距在 iOS 26+ 按"开"处理，
  和系统控件保持一致；主题设置里隐藏「毛玻璃质感」开关。
  规则只在 designsys 的 frostedGlassInForce / frostedGlassConfigurable（GlassRenderer.kt）。
- iOS 26 以下、Android：行为和外观不变。设置的存储键不改，Android 上开关照常读写。
- **验收目标**
  - [x] 开关为默认值（关）的 iPhone iOS 26+ 上：标签栏、帖子详情顶栏、发帖 / 回复 / 翻页按钮、提示条都是系统控件。
  - [x] 墨水屏模式下仍是 Compose 实色控件。
  - [x] iOS 26+ 主题设置页不显示该开关；Android 主题设置渲染图逐像素一致。
  - [x] 打开系统"降低透明度"后，系统控件随之变为不透明。

### 任务 C4：标签页页头按钮换成原生按钮（协作 AI）

**目标**：首页、通知、我的三个标签页页头的按钮改用现成的 `AdaptiveGlassButton`
（`designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/AdaptiveGlass.kt`）。
iOS 上变成系统液态玻璃按钮，带菜单的变成系统菜单；Android 外观和行为不变——`AdaptiveGlassButton` 的 Android / JVM 实现
就是直接调用 `fallback`，把原来的 Compose 按钮整段放进 `fallback` 即可。开发和测试在同一个分支、同一个 PR 里完成。

**分支**：`git fetch origin` 后从 `origin/ios/native-experience` 切 `ios/tab-header-native-buttons`（第 2 段另切 `ios/tab-header-native-buttons-2`）。

**写法**（参照 `ui/.../postdetail/ThreadReadingControls.kt` 里的回复按钮）：

```kotlin
AdaptiveGlassButton(
    action = GlassAction(
        label = stringResource(Res.string.action_sort), // VoiceOver 读的文字
        onClick = {},                                   // 有 menu 时点按直接弹菜单，不调用 onClick
        symbol = "arrow.up.arrow.down",                 // SF Symbol 名
        menu = sortMenuActions(sort, onSortChange),     // List<GlassMenuAction>，抽成可测的函数
    ),
    modifier = …,                                       // 和原按钮同尺寸
) {
    // 原来的 FilledIconButton + DropdownMenu，一行不改
}
```

#### 第 1 段（现在就能做）

| 位置 | 原控件 | 换成 |
|---|---|---|
| 首页 `ui/.../postlist/PostListScreen.kt` 的 `SortButton` | `FilledIconButton` + `DropdownMenu` | `GlassAction(symbol = "arrow.up.arrow.down", menu = 各排序项)`，当前排序 `selected = true` |
| 通知 `ui/.../notifications/NotificationsScreen.kt` 的 `markAllRead` | 带图标的 `TextButton` | `GlassAction(title = 「全部已读」文案, symbol = "checkmark.circle", enabled = state.hasUnread)` |

#### 第 2 段（D6 合入后）

| 位置 | 原控件 | 换成 |
|---|---|---|
| 首页 `SiteSwitcher` | `TextButton` + `DropdownMenu`（每项带域名、当前登录账号、"下次启动生效"） | `GlassAction(title = 当前站点名, symbol = "chevron.down", menu = 各站点)`；每项的附加说明用「 · 」连成一行，放进 D6 新增的 `GlassMenuAction.subtitle` |
| 我的 `ui/.../profile/ProfileScreen.kt` 的 `SettingsAction` | `IconButton` + 更新红点 | `GlassAction(symbol = "gearshape", badge = hasAppUpdate)` |

#### 不许做

- 首页右上角的账号按钮不换：登录后它显示头像，原生按钮画不了远程图片。
- 不改 `AdaptiveGlass*` 本身、`**/iosMain/**`、ViewModel；能力不够就按 §7.6 停下报告。
- 不改页头的布局、间距和其他按钮。

#### 测试

- 原生菜单只在 iOS 上执行，Android 测试走不到，所以菜单项的构造要抽成普通函数单独测：
  排序菜单、站点菜单各一个测试，逐项调用 `onClick` 传出的是对应的业务值（排序枚举、站点），不是下标；且只有当前项 `selected`。
  放在 `ui/src/androidHostTest/kotlin/io/github/nodyssey/ui/<包>/`。
- `PostListScreenRenderTest`、`NotificationsScreenRenderTest`、`ProfileScreenRenderTest`：改前存基线，改后逐像素一致（§7.4）。

#### 验收清单（全部满足才算完成）

- [ ] 本段的位置已替换；账号按钮未动。
- [ ] 三个页面的 Android 渲染图逐像素一致（附 §7.4 输出）。
- [ ] 菜单映射测试：逐项点选传出正确的业务值，且只有当前项 `selected`。
- [ ] 用到的 SF Symbol 名列在 PR 描述里，供 Devin 在 Mac 上核对。
- [ ] `spotlessCheck` 通过，`git diff --check` 无输出；改动只涉及上述位置及其测试。

#### 交付

- 第 1 段、第 2 段各开一个 PR：`gh pr create -R weishiair/nodyssey --base ios/native-experience`，描述用 §7.7 的模板。
- Devin 验收：全量门禁；Mac 上逐个按钮截图；菜单 20 次循环 + 远程桌面手动检查（菜单相关的验收在 D6 合入后进行）。

### 任务 C5：二级页面顶栏换成 `AdaptiveTopBar`（协作 AI）

**目标**：普通二级页面的 Material `TopAppBar` 换成 D7 的 `AdaptiveTopBar`。iOS 上成为系统导航栏（返回 `chevron.backward`、
溢出菜单为系统菜单），Android 逐像素不变。

**前提**：D7 已合入集成分支。先读 §4.5 的 API、示范页（`ReadHistoryScreen`）和 SF Symbol 对照表。

**分支**：每批一个分支、一个 PR：`ios/adaptive-top-bar-1`、`ios/adaptive-top-bar-2`……，从最新 `origin/ios/native-experience` 切。

| 批 | 页面 |
|---|---|
| 1 | 私信会话 `messages/MessageThreadScreen.kt`（标题 + 副标题，溢出菜单 `ThreadMenu`）、用户空间 `space/UserSpaceScreen.kt`、关注 `space/FollowScreen.kt`、收藏 `bookmarks/BookmarksScreen.kt`、我的内容 `mycontent/MyContentScreen.kt` |
| 2 | 设置下的二级页：关于、社区、更新日志、隐私、网络自检、标题关键词、追踪的帖子、主题、动态取色 |
| 3 | 账号与工具：账号列表、屏蔽、联系方式、图床、偏好、资料字段、安全、资产、积分、星尘、抽奖、社区工具、奖励、邀请、幸运、管理记录、邮箱、表情相关页 |
| 不在本任务 | 发帖 / 回复编辑器、登录流程、`SettingsPageContent.kt`（iOS 已整页原生）、帖子详情（已有原生顶栏）、三个标签页根页面（C4） |

#### 每页步骤

1. 改前存该页渲染基线；没有渲染测试的先补，单独提交（做法见 C1 第 1 段）。
2. 把 `TopAppBar` 换成 `AdaptiveTopBar`：原 `IconButton` 的图标、文案、点击搬进按钮描述，溢出 `DropdownMenu` 的各项搬进菜单项。一个页面一个提交。
3. 逐像素对比。不一致且原因在 `AdaptiveTopBar` 的 Compose 实现时，按 §7.6 停下报告，不要在页面里补偿。
4. 标题区有纯文字以外的内容，或顶栏有编辑模式切换、单手留白以外的滚动折叠等没覆盖的行为：跳过，记入跳过清单。
   单一 `containerColor` 的普通 `TopAppBar` 页面不算——用不带 `state` 的重载（§4.5「用哪个重载」）；
   用户空间 `space/UserSpaceScreen.kt` 已按此重载迁移，是本重载的示范页。

#### 不许做

- 不改 `AdaptiveTopBar` 本身、`**/iosMain/**`、ViewModel、Route。
- 不改页面内容区。

#### 测试

- 有溢出菜单的页面：菜单项执行的是对应动作（例如"在浏览器打开"打开的是本页地址）。已有测试覆盖的写明测试名，没有的新增。
- 每页渲染图逐像素一致（§7.4）。

#### 验收清单（全部满足才算完成）

- [ ] 本批页面逐一处理：已替换，或在跳过清单里写明原因。
- [ ] 每页 Android 渲染图逐像素一致（附 §7.4 输出）。
- [ ] 溢出菜单动作的测试（新增或指明已有）。
- [ ] 用到对照表以外的 SF Symbol 时列在 PR 描述里。
- [ ] `spotlessCheck` 通过，`git diff --check` 无输出。

#### 交付

- 每批一个 PR：`gh pr create -R weishiair/nodyssey --base ios/native-experience`，描述用 §7.7 的模板。
- Devin 验收：全量门禁；Mac 上逐页截图（浅色 / 深色 / 大字号）、返回和边缘返回、菜单 20 次循环。

### 任务 D2 · PR-1 iOS 原生菜单不再带出软键盘（Devin）

- **问题**：在 Compose 宿主里打开 `UIMenu`，iOS 26+ 的菜单键入筛选会把软键盘带出来。纯 UIKit 对照组不弹。
  已确认菜单打开时持有第一响应者的是 Compose 的 `OverlayInputView` / `ComposeContainerView`；
  试过"原生根视图先接过焦点"，无效（Compose 会抢回焦点），已撤回。
- **方向**：查锁定版本 Compose Multiplatform 的第一响应者逻辑和官方 issue；如无可靠修法，
  iOS 上 `Choice` 改为"推入一个系统勾选列表页"（系统设置 App 的做法），不再用弹出菜单。
- **验收目标**
  - [ ] 在夹具里连续打开 / 关闭频率菜单（或选项页）20 次，软键盘一次都不出现。
  - [ ] App 内真实文本框（如「提醒关键词」输入框）点击后键盘照常弹出、可输入，返回后键盘收起。
  - [ ] 若采用推入列表页：选中后自动返回、当前项打勾、边缘右滑可返回、VoiceOver 读出"已选定"。
  - [ ] 若改动了 Compose 侧焦点：注释写明尝试过的官方 API、它缺什么、何时可以删掉这段绕过。
- **结论（2026-11-20，已修复）**：
  - **触发条件**：模拟器或设备认为接着硬件键盘。用远程桌面操作 Mac 时，Mac 键盘会被模拟器当作硬件键盘，并且随按键反复连接、断开（日志 `BKSHIDKeyboardDevice … transport: CoreDevice`）。自动化测试不经过 Mac 键盘，所以一直复现不了；`typeKey` 模拟按键也复现不了。
  - **机制**：有硬件键盘时，菜单会开启键入筛选 `_UITypeSelectKeyInput`，挂在当前第一响应者上。Compose 为了接收硬件按键，一直让 `OverlayInputView` 当第一响应者（`canBecomeFirstResponder = true`），于是 UIKit 判定它需要键盘（`requiresKB:Y`）。硬件键盘这时恰好处于断开状态，软键盘就弹了出来。纯 UIKit 页面没有第一响应者，所以不弹。
  - **修复**：`NativeMenuFocus`（`designsys/src/iosMain/.../component/NativeMenuFocus.kt`，D6 从设置页抽出共用）在菜单触发时（`UIControlEventMenuActionTriggered`）让非文本输入的第一响应者交出焦点，菜单所属的控件离开组合时再交还给 Compose。Compose 只在窗口或浮层变化时才会把焦点抢回去，菜单不会触发这两种情况。`UIBarButtonItem` 打开菜单时没有事件，原生顶栏改在触摸到达时交出（`FocusReleasingNavigationBar`）。设置页、`AdaptiveGlassButton` / `Toolbar` / `TopBar` 都走它；**以后新增的原生菜单也必须接上**。
  - **验证**：用户在远程桌面下原本每次打开菜单都会弹出键盘，修复后不再弹出。
- **决定**：保留弹出菜单。§8 检查表保留"菜单连续开关 20 次"，作为回归检查。但它测不出这个问题，涉及菜单的改动还要在远程桌面（或连着硬件键盘的真机）上手动开几次菜单。

### 任务 D1 · PR-2 描述模型扩展 A（Devin）

为 C1（账号设置、开源许可）和 C3（设置首页）准备能力。**硬性要求：这两项迁移后 Android 必须逐像素不变**，所以 Compose 渲染器要能复刻它们现在的样子。计划新增（命名以合入代码为准）：

| 能力 | Android 复刻的现有组件 | iOS 实现 |
|---|---|---|
| `Slider`（实时值、松手提交、步进、值标签） | `SettingsBlock` + `SettingsSlider` | 单元格内 `UISlider` + 右侧值 |
| `Choice` 呈现方式：`Sheet` / `Menu` / `Segmented` | `PlazaSheet`+`ChoiceRow` / `SettingsMenuRow` / `SettingsBlock`+`ChoiceSegments` | 菜单（或 PR-1 的选项页）/ 单元格内 `UISegmentedControl`，大字号或超过 4 项时退回菜单 |
| 选项色块（`swatch`） | `ReadMarkColorRow` | 菜单项 / 选项页带色块图像 |
| `Action`（原地操作，不跳页；`loading`、`destructive`） | `GroupedRow(showChevron = false)` + `PlazaSpinner`；红色退出登录行 | 普通行；loading 时右侧 `UIActivityIndicatorView`；destructive 用系统红 |
| `Link.badge`（更新红点） | `UpdateDot` | 右侧系统红点 |
| `SampleText`（随设置变化的示例文字） | 字号滑块下的预览 `Text` | 单元格内 `UILabel`，字号跟随 |
| 区块脚注带标题（`SectionNotes`） | `SectionNotes` | 区块 footer（标题 + 多段） |
| 页面提示消息 | `PlazaSnackbarHost` | 同一个 Compose 提示叠在原生页上方 |
| 页首说明文字 | 开源许可页顶部的 `bodyMedium` 说明 | 首个区块的 header 位置显示说明文字 |
| 外链行（右侧外链图标、无箭头） | `GroupedRow(showChevron = false, trailing = OpenInNew)` | 右侧 `arrow.up.forward` 图标 |
| Compose 页面布局预设（页边距、组间距、说明文字内边距） | 账号设置、开源许可与标准设置页不同的间距 | 不适用（iOS 用系统列表间距） |
| 小号弱化行首图标、破坏性操作行带图标 | 账号设置的 `RowIcon`（20dp、`onSurfaceVariant`）与红色退出登录行 | 行首不画图标（系统设置子页惯例）；破坏性行用系统红 |
| 预览块（仅 Compose 绘制的示意） | 设置首页的搜索头像预览卡 | 省略，或以示例文字代替；在 PR 中说明 |

- **验收目标**
  - [ ] 每种新能力在 iOS 夹具"全组件页"上可见、可操作，截图存档；深色、AX XL 字号下不裁切。
  - [ ] 滑块拖动时值标签实时更新、松手才回调提交；拖动中不触发整表重载。
  - [ ] `shape()` 覆盖新种类；新增判定写进 `SettingsPageShapeTest`。
  - [ ] PR-0 的通知页渲染图仍逐像素一致；iOS 通知页行为不回退（§8 真机路径脚本通过）。
  - [ ] `docs/ios-native-experience.md` §4.2 更新为实际 API。

### 【二期】PR-3 描述模型扩展 B：表单（Devin）

**已交付（缩减范围）**：仅 `SettingsItem.Text`（`value/onValueChange/placeholder/keyboard:SettingsKeyboard(Text/Uri/Number)/secure/errorMessage/enabled`）+ `SettingsKeyboard` 枚举。
iOS 侧为单元格内 `UITextField`（keyboardType 映射、clearButton、secureTextEntry），`errorMessage` 渲染为单元格内红色错误行（出现/消失触发行高重算）；键盘避让走 `UIKeyboardWillChangeFrameNotification`→`contentInset/scrollIndicatorInsets`+滚动编辑行可见；回车链 Next→页内下一个 Text 字段、末字段 Done；焦点互斥复用 `NativeMenuFocus`（beginEditing→releaseFor、endEditing→restore）。未做 `RadioGroup`/独立按钮组项——测试/保存复用 `SettingsItem.Action`（loading），内联错误不新增 `InlineBanner` 项（`errorMessage` 已在单元格内）。

- **验收目标**
  - [ ] iOS：点任一输入框键盘弹出且该行滚到键盘上方；回车依次跳到下一个；最后一个收起键盘。
  - [ ] iOS：输入中切走再回来，内容不丢；外部状态变化（如校验错误）不打断正在输入的光标。
  - [ ] iOS：页面里其他 Compose 部分（提示消息）不会抢走焦点或让键盘闪烁。
  - [ ] Android：复用 `SettingsTextField` / `ProxyField` / `SettingsTestSaveButtons`，代理与 DNS 页迁移后渲染逐像素一致（在 PR-6 核对）。

### 任务 C1：迁移账号设置、开源许可（协作 AI）

**目标**：账号设置、开源许可两个页面改成描述驱动（§4），**Android 渲染图逐像素不变**，iOS 自动成为系统原生列表。
开发和测试在同一个分支、同一个 PR 里完成。

> 范围说明（2026-11-19 修订）：原计划的偏好设置、动态取色已移出——前者有存储标记、板块色标、等宽图例等定制布局，
> 后者是壁纸色块和调色板色阶，都属于 §4.4「不迁」的情况，归入 B 类保留 Compose。

**分支**：`git fetch origin` 后从 `origin/ios/native-experience` 切 `ios/migrate-simple-settings`。
（如果已经按旧任务卡建了这个分支并补了四个页面的渲染测试：保留偏好设置、动态取色的渲染测试提交，它们无害；只是不迁移这两页。）

#### 第 1 段：补渲染测试、存基线图（现在就能做）

这两个页面目前没有渲染测试，迁移前必须先有"改之前"的图。

1. 在 `ui/src/androidHostTest/kotlin/io/github/nodyssey/render/` 为每个页面新增一个渲染测试类，
   完全照 `NotificationSettingsScreenRenderTest.kt` 的写法（`RobolectricTestRunner`、`GraphicsMode.NATIVE`、
   `@Config(qualifiers = "w360dp-h…dp")`、`@Before fun onlyWhenRendering() = assumeRendering()`、最后 `captureRender("名字")`）。
   - 每个页面浅色、深色各一张；高度要放得下整页。
   - 账号设置另加一张"屏蔽人数未知"（`blockedCount = null`）的状态。
2. 每个页面一个提交，例如「补充开源许可页渲染测试」。
3. 生成基线图并保存到工作区外（例如 `..\renders-before\`）：
   ```powershell
   .\gradlew.bat :ui:testAndroidHostTest -PrenderUi --tests '*OpenSourceLicenses*RenderTest' --tests '*AccountSettings*RenderTest'
   # 把 ui\build\outputs\renders\ 下的 PNG 复制到 ..\renders-before\
   ```
4. 逐张打开检查：必须是真实页面，不是空白或报错页。

第 1 段做完先推送分支（PR 可以先开成 Draft），**等 D1 合入集成分支**后再做第 2 段。

#### 第 2 段：迁移、对比、补行为测试（D1 合入后）

1. 把最新集成分支合进来：`git fetch origin; git merge origin/ios/native-experience`。
2. 先读 D1 合入后的实际代码和 §4.2（D1 会把它更新成实际 API），命名以代码为准。按 §7.3 逐页迁移，**一个页面一个提交**。各页注意：
   - 两页的页边距和组间距都和标准设置页不同：用 D1 提供的 Compose 页面布局预设，**不要**改成标准间距。
   - **账号设置**：左侧小号弱化图标用 D1 的对应样式；「退出登录」用破坏性操作行（红色、带图标、无箭头），点击仍走原来的 `onSignOut`。
   - **开源许可**：页首说明文字、外链行（右侧外链图标、无箭头）、底部说明都用 D1 提供的能力；依赖列表每项一行，`key` 用依赖名，不要用下标。
3. 每迁完一页，重新生成该页渲染图，用 §7.4 的脚本和基线逐像素对比。**必须完全一致**；不一致就修到一致，原因在渲染器的停下报告（§7.6）。
4. 每页至少一个行为测试，放在 `ui/src/androidHostTest/kotlin/io/github/nodyssey/ui/<包>/`，抓真实逻辑。例如：
   - 账号设置：五个入口各自触发对应回调（不是同一个）；点「退出登录」调用的是 `onSignOut`。
   - 开源许可：点某个依赖行，`onOpenUri` 收到的是该依赖的源码地址；点本应用行收到的是许可证地址。
5. 跑 §7.2 的命令。

#### 不许做

- 不改 `designsys/**/settings/**`、`ui/.../settings/SettingsPageContent.kt`、`**/iosMain/**`、`NativeShell.kt`、ViewModel、Route、Repository。
- 不往描述模型里加字段，不在页面里写平台判断；模型不够用就停下报告。
- 不加依赖，不改版本号、lockfile、Room。

#### 验收清单（全部满足才算完成）

- [ ] 两个页面都有渲染测试；默认测试运行时全部跳过、不产生文件。
- [ ] 两个页面迁移后渲染图与基线逐像素一致（附 §7.4 脚本输出）。
- [ ] 每页至少一个行为测试，且能在对应逻辑被改坏时失败。
- [ ] 页面文件里不再有 `SettingsGroup`、`GroupedRow`、`OneHandTopAppBar` 等布局代码。
- [ ] `git diff --stat origin/ios/native-experience` 只涉及这两个页面、它们的测试和渲染测试（以及第 1 段已有的渲染测试）。
- [ ] `spotlessCheck` 通过，`git diff --check` 无输出。

#### 交付

- 推送分支，开 PR（或把 Draft 转为正式）：`gh pr create -R weishiair/nodyssey --base ios/native-experience`。
- PR 描述用 §7.7 的模板，附逐像素对比输出和新增测试清单。
- Devin 验收：全量门禁、复核对比结果、在 Mac 上做 §8 的 iOS 检查，通过后合入集成分支。

### 任务 C3：迁移设置首页（协作 AI）

**目标**：设置首页（`ui/.../ui/settings/SettingsScreen.kt` 里的 `SettingsScreen`）改成描述驱动，**Android 渲染图逐像素不变**。
这是一期里最大的页面迁移：开关、3 个滑块（含实时示例文字和搜索头像预览）、3 组分段、4 个菜单、已读标记色块、
清除缓存（转圈）、更新红点、自动签到区块。D1 会提供全部需要的能力。

**前提**：D1 已合入集成分支。开工前先读 D1 合入后的 §4.2 和实际代码。

**分支**：`git fetch origin` 后从 `origin/ios/native-experience` 切 `ios/migrate-settings-home`。

#### 步骤

1. **基线**：`SettingsScreenRenderTest` 已存在。先在未改动的代码上跑一遍，PNG 存到工作区外：
   ```powershell
   .\gradlew.bat :ui:testAndroidHostTest -PrenderUi --tests '*SettingsScreenRenderTest'
   ```
   如果它没覆盖下面这些状态，**先补渲染测试并单独提交**，再存基线：墨水屏开（明暗分段禁用）、表情统一大小开（多出表情大小滑块）、
   已读标记为整块染色（多出标记颜色）、清除缓存进行中（转圈）、有新版本（红点）、自动签到开 / 关。
2. **迁移**：按 §7.3 改写 `SettingsScreen`，保留函数签名。建议按区块分提交：外观、内容、自动签到、通知 / 网络 / 关于。
   - `AutoAttendanceSettings`、`ReadMarkColorRow`、`AppLanguageRow` 等私有或内部组件，改成返回描述项 / 区块的函数，原组件不再使用后删除。
   - 滑块拖动中只更新显示、松手才提交（现有 `SettingsScreenTest` 锁着这条），用 D1 滑块的"实时值 / 松手提交"两个回调。
   - 原代码里解释"为什么"的注释跟着对应的行搬过去。
3. **对比**：每个区块迁完都和基线逐像素对比（§7.4），**必须完全一致**。
4. **测试**（和迁移一起交付）：
   - 现有 `SettingsScreenTest` 全部通过，不许改它的断言。
   - 新增行为测试，至少覆盖：墨水屏开时明暗分段禁用；表情统一大小关时没有表情大小滑块；已读标记切到整块染色后出现标记颜色；
     分段 / 菜单的回调传出的是业务枚举值，不是下标；清除缓存进行中不能重复触发。
5. 跑 §7.2 的命令。

#### 不许做

- 同 C1。另外不改 `SettingsViewModel` 和 `SettingsUiState`。

#### 验收清单（全部满足才算完成）

- [ ] `SettingsScreenRenderTest` 的全部渲染图与基线逐像素一致（附 §7.4 输出）。
- [ ] 现有 `SettingsScreenTest` 全部通过且断言未改；新增行为测试覆盖上面列出的 5 条，且各自能在对应逻辑被改坏时失败。
- [ ] `SettingsScreen.kt` 里不再有 `SettingsGroup`、`GroupedRow`、`SettingsBlock`、`OneHandTopAppBar` 等布局代码。
- [ ] `spotlessCheck` 通过，`git diff --check` 无输出；改动只涉及设置首页及其测试。

#### 交付

- 推送分支，开 PR：`gh pr create -R weishiair/nodyssey --base ios/native-experience`。
- PR 描述用 §7.7 的模板。
- Devin 验收：全量门禁、复核对比；D2（菜单键盘）合入后在 Mac 上做 §8 的 iOS 检查（开关、滑块、分段、菜单、每个子页面进出），通过后合入集成分支。

### 【二期】PR-6 迁移代理、加密 DNS（Devin）

**已交付（缩减范围）**：仅代理页——`ProxySettingsScreen` 包 `AdaptiveSettingsPage(page=proxySettingsPage(), fallback={原 Compose 体逐字})`，Android 渲染逐像素不变（`NetworkSettingsRenderTest` 4/4 identical）。
**加密 DNS 不迁移**：`DohSettingsScreen` 的服务器拖拽排序（`detectDragGestures`）+ preset 单选列表需要 reorder 模型项或原生/Compose 混排——模型与渲染器都不支持 `Custom` 行到达原生页（会 `error`），整页必须纯描述或整页留 Compose；缩减决策整页保留 Compose。

- **验收目标**
  - [ ] `NetworkSettingsRenderTest` 逐像素一致；`ProxySettingsViewModelTest`、`DohSettingsViewModelTest` 通过。
  - [ ] iOS：开启代理 → 填写 → 测试连接（loading、成功 / 失败提示）→ 保存，全流程可用，键盘不遮挡。
  - [ ] iOS：校验错误（缺地址、端口非法）显示在对应输入框下方。

### 任务 D3 · PR-7 原生确认框 `AdaptiveAlertDialog`（Devin）

在 `:designsys` 新增 `AdaptiveAlertDialog(title, message, confirmLabel, onConfirm, dismissLabel?, onDismiss, destructive = false)`：
Android / 桌面画现有 Material `AlertDialog`（与现有调用的样式一致），iOS 弹 `UIAlertController`。
由组合状态驱动：在组合里就显示、离开组合就收起。

- **验收目标**
  - [ ] iOS：标题、正文、按钮文字正确；`destructive` 按钮为系统红；取消按钮位置由系统决定。
  - [ ] iOS：点外部不关闭（与系统一致）；对话框显示中页面被弹出时不崩溃、不残留。
  - [ ] Android：替换一处真实调用作为样例，渲染逐像素一致。
  - [ ] 附带一份"调用点替换指南"写进本文附录 B（见 B.0）。

### 任务 C2 · PR-8 替换简单确认框（协作 AI）

**目标**：只有文字和按钮的确认框改用 D3 提供的 `AdaptiveAlertDialog`，iOS 上变成系统确认框，Android 外观和行为不变。
开发和测试在同一个分支、同一个 PR 里完成。

**前提**：D3 已合入集成分支。开工前先读附录 B.0 的替换指南（含签名、逐项对照、必须跳过的情况）。

**分支**：`git fetch origin` 后从 `origin/ios/native-experience` 切 `ios/native-alert-dialogs`。

#### 步骤

1. 用 `rg -n "AlertDialog\(" ui/src/commonMain` 列出当前全部调用点，对照附录 B 的三份清单逐个确认。
   只替换"正文只有文字（可以多段）+ 至多两个按钮（可带一个破坏性按钮）"的；其余一律不动。
2. 替换前，为每个要改的对话框准备"改之前"的证据：
   - 所在页面有渲染测试、且能渲染出对话框的：保存基线图；
   - 没有的：在该页面现有的 Robolectric 测试或新写的行为测试里，能把对话框打开即可（见第 4 步）。
3. 逐个替换，**一个文件一个提交**。按钮文案、点击后的行为、`onDismiss` 的时机保持原样。
4. 测试（和替换一起交付）：
   - 每个被替换的对话框至少有一个行为测试覆盖"点确认才执行、点取消不执行"。已有覆盖的写明是哪个测试，没有的新增，
     放在对应包的 `ui/src/androidHostTest/kotlin/io/github/nodyssey/ui/<包>/`。
   - 能渲染的对话框，替换后和基线逐像素对比（§7.4）。
5. 跑 §7.2 的命令。

#### 不许做

- 带输入框、单选、复选、列表、图片的对话框不换，记入"跳过清单"并写明原因。
- 不改 `AdaptiveAlertDialog` 本身、`**/iosMain/**`、ViewModel；它不够用就停下报告。

#### 验收清单（全部满足才算完成）

- [ ] 附录 B「可直接替换」中除 D3 已完成的 `HighRiskDialog` 外的 14 处逐一处理：要么已替换，要么在跳过清单里写明原因。
- [ ] 「需逐个判断」的 14 处逐一检查并归类（替换 / 跳过 + 原因）。
- [ ] 每个被替换的对话框都有"确认才执行、取消不执行"的行为测试（新增或指明已有测试）。
- [ ] 能渲染的对话框逐像素一致（附 §7.4 输出）。
- [ ] `spotlessCheck` 通过，`git diff --check` 无输出；除被替换的调用点和测试外没有其他改动。

#### 交付

- 推送分支，开 PR：`gh pr create -R weishiair/nodyssey --base ios/native-experience`。
- PR 描述用 §7.7 的模板，附"已替换清单""跳过清单（含原因）"和测试清单。
- Devin 验收：全量门禁，在 Mac 上逐个触发被替换的对话框截图（§8），通过后合入集成分支。

### 【二期】PR-9 SF Symbols 图标基础设施（Devin）

在 `:designsys` 新增 `PlazaIcon(imageVector, contentDescription, modifier, tint)`：
Android / 桌面等同 Material `Icon`；iOS 按 `ImageVector.name` 查映射表，命中就渲染对应 SF Symbol（按尺寸、字重缓存，
用 `tint` 着色），没命中回退到原矢量图。映射表放在 `:designsys`，只含通用符号。

- **验收目标**
  - [ ] iOS 测试：映射表里每个 SF Symbol 名都能被 `UIImage.systemImageNamed` 解析（`iosSimulatorArm64Test`）。
  - [ ] iOS：图标在 1×/2×/3× 和 AX 字号下清晰，不糊、不错位；深色下着色正确。
  - [ ] 列表滚动时没有可感知的卡顿（快速滑动首页 10 屏，对比替换前）。
  - [ ] Android：所有现有渲染图逐像素一致。

### 【二期】PR-10a 图标清单与 SF Symbols 候选表（协作 AI）

- **范围**：列出 `ui/src/commonMain` 和 `designsys/src/commonMain` 里用到的每一个图标（`Icons.*` 和 `PlazaIcons.*`），
  每个给出：`ImageVector.name`、用途（取一个典型调用点）、建议的 SF Symbol 名、备选名。产出为 PR-9 映射表可直接粘贴的格式。
- **不许做**：不改生产代码。
- **验收目标**
  - [ ] 覆盖全部图标，无遗漏（附生成清单的命令和统计数字）。
  - [ ] 语义对应合理（如返回 → `chevron.backward`，删除 → `trash`），站点专属概念标注"无对应，保留原图标"。

### 【二期】PR-10 图标调用点替换（协作 AI）

- **范围**：把 `:ui` 里的 `Icon(` 替换为 `PlazaIcon(`（参数不变），并按 PR-10a 补全映射表。
- **验收目标**
  - [ ] Android：全部渲染测试逐像素一致（跑全量 `-PrenderUi`，附对比结果）。
  - [ ] `:ui` 中不再直接调用 Material `Icon(`（附 `rg` 检查结果）；例外需写明原因。
  - [ ] Devin 在 Mac 上跑 PR-9 的 SF Symbol 解析测试通过，并抽查首页、帖子、设置、我的四页截图。

### 【二期】PR-11 iOS 点击反馈与触感（Devin）

iOS 上把默认点击反馈从水波纹换成按下变暗（主题层一处）；开关切换、下拉刷新触发、长按菜单出现时给系统触感。

- **验收目标**
  - [ ] iOS：列表行、按钮、卡片按下时无水波纹，有按下高亮；Android 不变（渲染逐像素一致）。
  - [ ] iOS 真机：三种场景各有一次触感，且不重复触发。

### 【二期】PR-12 原生底部面板（Devin）

`PlazaSheet` 在 iOS 上改为以 `UISheetPresentationController` 弹出一个承载 Compose 内容的控制器：
系统拖动条、中 / 大两档、下拉关闭。要解决主题和 `CompositionLocal` 跨组合传递、面板内状态与页面同步。

- **验收目标**
  - [ ] iOS：14 个调用 `PlazaSheet` 的文件里的每个面板逐一打开、操作、关闭，无白屏、无主题错乱、无崩溃。
  - [ ] iOS：下拉关闭与点按钮关闭都正确回调 `onDismiss`；旋转、iPad 分栏下位置正确。
  - [ ] Android：不变。

### 【二期】PR-13 直接 `ModalBottomSheet` 改走 `PlazaSheet`（协作 AI）

- **范围**：`composer/ReplyComposer.kt`、`messages/MessageThreadScreen.kt` 各一处。评估能否改用 `PlazaSheet`；
  能改就改，不能改（依赖 `PlazaSheet` 不提供的参数或行为）就不改，在 PR 描述里写明缺什么。
- **验收目标**
  - [ ] 改动的面板外观、拖动、关闭行为与改前一致（附前后截图）；相关测试通过。

---

## 7. 通用流程（协作 AI 必读）

### 7.1 环境与分支

- JDK 21、Android SDK 37；Windows 用 `.\gradlew.bat`。`local.properties` 指向本机 SDK，**不要提交**。
- **集成分支 `ios/native-experience`**（2026-11-19 基于 `dev` 建立）。本轮所有 PR 的目标分支都是它，**不是 `dev`**。
  ```
  dev ──▶ ios/native-experience（集成分支）
              ▲      ▲      ▲
         ios/<slug> ios/<slug> …   各 PR 合回集成分支
  每一批整体验收、真机测试包通过后，由 Devin 开单独 PR：ios/native-experience → dev
  ```
- 在干净的工作区从最新 `origin/ios/native-experience` 切分支，命名 `ios/<slug>`（例：`ios/migrate-account-settings`）。
  工作区里已有的、不属于你的改动不要碰；暂存时写明路径，不要 `git add -A`。
- 开 PR 必须显式指定仓库和目标分支：
  `gh pr create -R weishiair/nodyssey --base ios/native-experience`。
  这个仓库的 `gh` 默认指向上游 `5151561/nodyssey`，不写 `-R` 会开到别的仓库去。
- 不在集成分支上直接提交；不对集成分支 rebase 或 force-push。`dev` 有新进展时，由 Devin 把 `dev` 合入集成分支（merge）。
- 一个 PR 一个目的；页面迁移一个页面一个提交。提交信息用中文短句，正文写原因和验证结果。
- **推送和开 PR 只在用户授权后做。**

### 7.2 每个 PR 都要跑的命令

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck :designsys:jvmTest :designsys:testAndroidHostTest :ui:compileKotlinJvm `
  :ui:testAndroidHostTest --tests '*guard*' --tests '<你改动相关的测试>'
git diff --check
```

交付前（Devin 验收时会跑全量）：

```powershell
.\gradlew.bat spotlessCheck testDebugUnitTest testAndroidHostTest jvmTest :app:lintDebug :app:assembleDebug
.\gradlew.bat --stop
```

### 7.3 页面迁移步骤

1. **先拿基线图**：在未改动的提交上运行该页渲染测试，把 PNG 复制到工作区外保存：
   ```powershell
   .\gradlew.bat :ui:testAndroidHostTest -PrenderUi --tests '*XxxRenderTest'
   # 产物在 ui/build/outputs/renders/，复制到例如 ..\renders-before\
   ```
   没有渲染测试的页面，先补渲染测试并单独提交（做法见任务 C1 第 1 段）。
2. **改写 Screen**：保留函数签名；把布局换成构建 `SettingsPage` 并调用 `SettingsPageScreen(page, onBack, modifier)`（需要时再传 `layout`、`snackbarHostState`，见 §4.2）。
   对照附录 A 的组件映射表。照 `NotificationSettingsScreen.kt` 的写法：可复用的行先建成变量，复杂分支抽成私有 `@Composable` 函数返回 `List<SettingsSection>`。
3. **保留原注释的意图**：原代码里解释"为什么"的注释，跟着对应的行或区块搬过去，不要丢。
4. **清理**：删掉不再使用的私有 Composable 和 import。
5. **出新图并逐像素对比**（§7.4）。不一致就修到一致；如果原因在渲染器，停下报告（§7.6）。
6. **补行为测试**（§7.5），跑 §7.2 的命令。

### 7.4 逐像素对比脚本

```python
# compare_renders.py <before_dir> <after_dir>   需要 Pillow
import os, sys
from PIL import Image, ImageChops
before, after = sys.argv[1], sys.argv[2]
failed = False
for name in sorted(os.listdir(before)):
    a = Image.open(os.path.join(before, name)).convert("RGBA")
    path = os.path.join(after, name)
    if not os.path.exists(path):
        print(name, "MISSING"); failed = True; continue
    b = Image.open(path).convert("RGBA")
    box = None if a.size != b.size else ImageChops.difference(a, b).getbbox()
    status = "size differs" if a.size != b.size else ("identical" if box is None else f"DIFF {box}")
    failed |= status != "identical"
    print(name, status)
sys.exit(1 if failed else 0)
```

把脚本输出原样贴进 PR 描述。

### 7.5 测试要求

遵守 `AGENTS.md` 的测试准则：测试要能抓到真实的逻辑错误，不测外观、不测原样透传。迁移页面时典型的好测试：

- 主开关关闭后依赖它的行禁用，而管理入口仍可用（见 `NotificationSettingsScreenTest`）。
- 选择回调传出的是业务值（分钟数、枚举），不是下标。
- 未登录 / 无数据时的行为（禁用、隐藏、跳转登录）。

### 7.6 必须停下报告、不要自己绕过的情况

- 页面需要描述模型没有的能力（不要往模型里加字段，不要在页面里写平台判断）。
- 逐像素对比不一致，且原因在 Compose 渲染器或 `:designsys` 组件。
- 需要改 `**/iosMain/**`、`designsys/**/settings/**`、`AdaptiveGlass*` / `AdaptiveTopBar` 本身、`NativeShell.kt`、ViewModel、Route、Repository。
- 需要加依赖、改 lockfile、改版本号、改 Room。
- 测试在你改动之前就是失败的（报告，不要顺手修）。

报告写清：哪个页面、哪一行、缺什么能力或差在哪里、你建议怎么做。

### 7.7 PR 描述模板

```markdown
## 做了什么
- …

## 逐像素对比
<compare_renders.py 输出>

## 测试
- 命令：…
- 新增 / 修改的测试：…（各自抓的是什么错误）

## 跳过 / 未完成
- …（原因）

## 需要 Devin 在 iOS 上验收的点
- …
```

---

## 8. iOS 验收流程（Devin）

- **环境**：Tailscale 上的 Mac mini（`092mac-mini`，Xcode 27.2，iOS 27.2 模拟器）。
  独立工作区 `~/Developer/nodyssey-native-settings`（由 `git worktree` 建立，补丁同步），
  专用模拟器 "Nodyssey Native Settings"，不动其他工作区和设备。
- **工具**（仓库外，`~/.nodyssey/native-settings/`）：`rebuild-all.sh`（模拟器 App + 夹具）、`run.sh`（单个 XCUITest）、
  夹具 App（以合成状态展示任意页面）、XCUITest 探针（截图 + 无障碍树）。
  注意：在 Compose 列表里滚动要用"按住慢拖"，用 `swipeUp` 甩动后点击会落空。
- **每个 iOS 相关 PR 的检查表**
  - [ ] `:designsys` / `:ui` 的 iOS 编译、模拟器 App 构建通过（含弱链接检查脚本）。
  - [ ] 真实路径能进入目标页面，返回按钮、边缘返回都正确。
  - [ ] 浅色 / 深色、默认 / Accessibility XL 字号截图，无裁切、无重叠。
  - [ ] iPad 横屏分栏下页面可用。
  - [ ] VoiceOver：每行可聚焦，开关读出状态，标题读作标题。
  - [ ] 变更涉及的交互逐项操作一次（开关、菜单、输入、跳转）。
  - [ ] 页面有弹出菜单时：每个菜单连续开关 20 次（探针 `test10MenuLoop` 的做法），软键盘出现 0 次。
    同时点一次纯 UIKit 对照输入框（`test11SoftwareKeyboardVisible`），确认软键盘能弹出，否则上一条的结果无效。
  - [ ] 改动涉及菜单或第一响应者时：在远程桌面里（Mac 键盘被当作硬件键盘）手动打开每个菜单几次，软键盘不出现。自动化测试测不出这一条，见任务 D2。
  - [ ] Compose 页面上的原生菜单：菜单开着时点菜单外面的内容（帖子卡片、列表行），只关菜单，不能打开下面的内容。
    Compose 读的是原始触摸，会收到这一下；`NativeMenuShield`（`designsys/src/iosMain/.../component/NativeMenuShield.kt`）在菜单弹出时垫一层透明视图吃掉它。以后新加的原生菜单也必须接上。
  - [ ] 截图与结论写进 PR 评论。

---

## 9. 最终验收目标

每期结束按下表验收：一期只验「期」为一期的行；§9.2、§9.3 每期都要满足。**每一条都要有可复现的检查方法。**

### 9.1 iOS 体验

| 编号 | 期 | 目标 | 检查方法 |
|---|---|---|---|
| G1 | 一期及二期 | 设置首页、通知、账号设置、开源许可、代理使用系统原生列表。墨水屏使用实色 Compose | 逐页截图；无障碍树里行是 `Cell`、开关是 `Switch` |
| G2 | 一期 | 打开任何选项都不会带出软键盘 | 模拟器上每个菜单连续开关 20 次，并在远程桌面下手动打开（§8）；真机测试包上每个带选项的页面各打开 5 次 |
| G3 | 二期 | 表单页输入流畅：键盘不遮挡、回车切换、校验错误就近显示 | 代理页全流程 |
| G4 | 一期 | 附录 B 中可替换的确认框全部是系统 `UIAlertController` | 逐个触发截图 |
| G5 | 二期 | iOS 26+ 的共享面板使用系统拖动条、中 / 大两档和下拉关闭 | 逐一打开共享面板，按 §5.5 核对私信会话信息面板 |
| G6 | 二期 | 图标为 SF Symbols；没有对应的保留原图标且有清单 | 首页、帖子、设置、我的、编辑器截图；映射解析测试通过 |
| G7 | 二期 | 设置开关、刷新和通知分段切换提供系统触感 | 真机分别检查一次反馈是否适度，重复点选当前分段时检查无额外反馈 |
| G8 | 一期 | 返回手势、深色、AX XL 字号、iPad 分栏、横屏、VoiceOver 在本期改动的页面都可用 | §8 检查表 |
| G9 | 一期 | 原有功能不回退：登录、浏览、回帖、发帖、通知、签到、切换账号 | 冒烟走一遍 |
| G10 | 二期 | 通知胶囊分段条同步点击与双向滑动，首页与通知页的刷新指示器逐格点亮、旋转 | 浅色、深色、辅助功能 XL 的截图与录屏 |

### 9.2 Android 不变

| 编号 | 目标 | 检查方法 |
|---|---|---|
| A1 | 所有改动页面的渲染图与基线逐像素一致（有意变化须经用户确认并单列） | `compare_renders.py` |
| A2 | 交付门禁全绿 | `spotlessCheck testDebugUnitTest testAndroidHostTest jvmTest :app:lintDebug :app:assembleDebug` |
| A3 | 没有新依赖、没改 Room、没改版本号 | `git diff origin/dev -- gradle/ '**/gradle.lockfile' '**/schemas/' gradle.properties` 为空 |

### 9.3 可维护性

| 编号 | 目标 | 检查方法 |
|---|---|---|
| M1 | 页面只有一份定义：`ui/src/commonMain` 没有按平台分叉的页面布局 | `rg -n "Ios[A-Z][A-Za-z]*Layout\|isIos\|Platform.iOS" ui/src/commonMain` 无新增 |
| M2 | iOS 专属 UI 代码集中在 `designsys/src/iosMain` 的 `settings/`、`component/` 与 `NativeShell.kt` | `git diff --stat origin/dev -- '**/iosMain/**'` 只落在这些位置 |
| M3 | 新增一个设置项只需改一个 Screen 文件，两个平台同时生效 | 抽一个已迁移页面加一行开关验证 |
| M4 | 每个 PR 可单独回滚 | 每个 PR 独立合入，互不夹带 |

---

## 10. 风险与待决事项

| 编号 | 风险 / 问题 | 处理 |
|---|---|---|
| R1 | Compose 宿主内打开 `UIMenu` 带出软键盘 | **已修复**。D2 和 D6 通过 `NativeMenuFocus` 释放 Compose 第一响应者。#129 的设置菜单 20 次开关出现 0 次软键盘，§8 继续保留硬件键盘检查 |
| R2 | 原生 `UITextField` 与 Compose 焦点冲突，或键盘遮挡表单 | **已处理**。#128 在编辑开始和结束时复用 `NativeMenuFocus`，强引用 UIKit 的弱委托；#129 验证编辑行露出和滚动到底部操作，避让保留顶部 inset |
| R3 | 原生 sheet 内 Compose 内容拿不到页面的主题和 `CompositionLocal` | **已处理**。原生 sheet 是独立组合，不能整体恢复 `currentCompositionLocalContext`（会覆盖 sheet 自己的 `LocalUIViewController`、焦点和 UIKit 互操作宿主）。`AdaptiveModalSheet.ios.kt` 逐个转发主题与 `:designsys` 的 local，`:ui` 的 local 由 `nativeSheetLocals()` 转发；`NativeSheetLocalsTest` 要求每个 iOS 可见的 local 要么转发、要么写明不转发的原因。私信面板例外见 §5.5 |
| R4 | SF Symbols 栅格化的清晰度、性能、着色 | **已交付**。#123 验证映射解析并抽查实际页面。缓存按符号名和像素尺寸复用，空缺映射保留矢量，见 [图标清单](ios-sf-symbols.md) |
| R5 | Compose Multiplatform alpha 升级可能改变 UIKit 互操作 | **持续复查**。升级时在 Mac 跑 §8 |
| R6 | 模拟器与真机在触感、键盘、性能上的差异 | **待本轮真机验收**。用同一集成提交的真机包和模拟器安装验证 |
| R7 | iOS 按压高亮替代 ripple（PR-11 Part A） | **保留现状**。固定的 Material 3 `1.5.0-alpha27` 中，`Surface` 直接调用 `ripple()`，`RippleConfiguration` 只调外观参数。全局换形态缺少公开主题 API。Part B 的 `PlatformHaptics` 已由 #126 交付 |
| R8 | 点原生菜单外部的触摸透传给 Compose | **已修复**。#112 的 `NativeMenuShield` 拦截关闭菜单的触摸，新接入的原生菜单按 §8 复查 |

**待用户确认**

- iOS 设置页底色已由 #129 统一到 App 主题。浅色纯白模式保留分组灰阶差。
- iOS 开关目前沿用 App 强调色。本轮真机验收时一并确认。
- iOS 单选保留弹出菜单，见任务 D2 的结论。
- iOS 启动时将港澳繁体系统语言收窄到 `zh-TW` 已由 #101 处理。其余标签边界见 §5.5。

---

## 附录 A：现有组件 → 描述模型对照

| 现有写法 | 描述 |
|---|---|
| `SectionLabel(x)` + `SettingsGroup { … }` | `SettingsSection(key, header = x, items = …)` |
| `GroupedRow(checked, onCheckedChange, trailing = { GroupedListItemSwitch(…) })` | `SettingsItem.Toggle` |
| `GroupedRow(onClick)`（有箭头，进入子页面） | `SettingsItem.Link` |
| `GroupedRow(onClick, showChevron = false)`（原地操作）；带 `PlazaSpinner` 的 | `SettingsItem.Action`（`loading`） |
| 红色的退出登录行 | `SettingsItem.Action(destructive = true)` |
| `GroupedRow(showChevron = false, trailing = OpenInNew)` 外链行 | `SettingsItem.Link(external = true)` |
| `GroupedRow(showChevron = false)` 且没有 `onClick`（只展示状态） | `SettingsItem.Info` |
| 行尾 `UpdateDot` | `SettingsItem.Link(badge = true)` |
| 页顶 `bodyMedium` 说明文字 | `SettingsPage(intro = …)` |
| `ReadMarkColorRow` 这类带色块的单选 | `SettingsOption(swatch = …)` |
| 字号滑块下的预览 `Text` | `SettingsItem.Slider(sample = SettingsSample(…))` |
| 现有组件复刻不了的预览块 | `SettingsItem.Custom`（慎用，见 §4.2） |
| 行 + `PlazaSheet` 里的 `ChoiceRow` 单选 | `SettingsItem.Choice`（`Sheet`） |
| `SettingsMenuRow` 下拉菜单 | `SettingsItem.Choice`（`Menu`） |
| `SettingsBlock` + `ChoiceSegments` | `SettingsItem.Choice`（`Segmented`） |
| `SettingsBlock` + `SettingsSlider` | `SettingsItem.Slider` |
| 主开关的着色卡片 `GroupedListItem(colors = primaryContainer…)` | 只含一个 `Toggle` 的 `SettingsSection(style = Prominent)` |
| `LayerCard` 指向页外的卡片 | 只含一个 `Link` 的 `SettingsSection(style = Callout)` |
| 区块下方 `Text(labelSmall)` | 有行的区块的 `footer` |
| `SectionLabel` + `SectionNote`（没有卡片） | 没有行的区块：`header` + `footer` |
| `SettingsTextField` | `SettingsItem.Text`，校验文案传 `errorMessage` |
| `SettingsTestSaveButtons` | 分别使用 `SettingsItem.Action`，测试时用 `loading` |
| 独立 `InlineBanner` | 沿用原有 Compose 布局。字段错误可用 `Text.errorMessage` |

## 附录 B：确认框替换指南与清单

### B.0 替换指南（D3 合入后）

`AdaptiveAlertDialog` 在 `io.github.plaza.designsys.component`：

```kotlin
AdaptiveAlertDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,      // null = 只有一个按钮
    destructive: Boolean = false,      // 确认按钮：Android error 色 / iOS 系统红
    prominentConfirm: Boolean = false, // 确认按钮：Android 用实心 Button
    icon: ImageVector? = null,         // 只有 Android 画，iOS 系统确认框没有图标
)
```

**逐项对照**（原写法 → 新参数）：

| 原 `AlertDialog` | `AdaptiveAlertDialog` |
|---|---|
| `onDismissRequest = X` | `onDismiss = X` |
| `title = { Text(t) }` | `title = t` |
| `text = { Text(b) }`，或带 `style = MaterialTheme.typography.bodyMedium` | `message = b`（这个样式就是默认样式）；没有 `text` 就传 `null` |
| `icon = { Icon(i, contentDescription = null) }` | `icon = i` |
| `confirmButton = { TextButton(onClick = A) { Text(l) } }` | `confirmLabel = l`，`onConfirm = A` |
| 确认按钮文字 `color = MaterialTheme.colorScheme.error` | 再加 `destructive = true` |
| 确认按钮是 `Button(...)`（实心） | 再加 `prominentConfirm = true` |
| `dismissButton = { TextButton(onClick = Y) { Text(d) } }` | `dismissLabel = d` |
| 没有 `dismissButton` | 不传 `dismissLabel` |

`destructive` 只在 Android 原来就是 error 色时才加。没有红色的确认按钮，即使动作是删除，也不要加，否则 Android 会变色。

**遇到以下情况一律跳过**，记进跳过清单并写明原因：
- 正文是 `Column` 里的多个 `Text`，或者带样式片段、链接、按钮：合并成一个字符串会改变 Android 的排版。
- `dismissButton` 的 `onClick` 和 `onDismissRequest` 不是同一个动作。iOS 上取消按钮调用的是 `onDismiss`，行为会变。
- 点确认后确认框并不消失，比如留在原地转圈：iOS 系统确认框点任何按钮都会自己关闭。
- 设置了 `properties`（如 `DialogProperties(dismissOnClickOutside = false)`）、`modifier`、自定义 `shape` / 颜色，或按钮带 `enabled`、图标、三个及以上按钮。

**行为约定**：
- 两个回调都必须让确认框离开组合，也就是把控制它显示的状态置为 false。原来的写法本来就是这样，照搬即可。
- iOS 上点外部不会关闭，这是系统行为，不用处理。
- 页面被返回时确认框离开组合，iOS 会自动收起，调用方不用处理。

**样例**：D3 已经把 `account/AccountComponents.kt` 的 `HighRiskDialog` 换掉了（6 处调用一起生效），C2 不用再处理这一处：

```kotlin
AdaptiveAlertDialog(
    title = title,
    message = body,
    confirmLabel = confirmLabel,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    dismissLabel = stringResource(Res.string.action_cancel),
    destructive = destructive,
    icon = icon,
)
```

**验证**：
- Android：有渲染测试的对话框，用 `composeRule.onNode(isDialog()).captureRender(...)` 截图，前后逐像素对比。写法见 `HighRiskDialogRenderTest`。
- 行为：用 `onNode(isDialog())` 下按文字点按钮，断言回调只调了一次。写法见 `designsys` 的 `AdaptiveAlertDialogTest`。

### B.1 清单（2026-11-19）

**可直接替换（只有文字和按钮）**，15 处（第一处 `AccountComponents.kt:197` 的 `HighRiskDialog` 已由 D3 完成）：

```
account/AccountComponents.kt:197        bookmarks/OfflineManageSheet.kt:125
composer/PostComposerScreen.kt:919      history/ReadHistoryScreen.kt:257
lottery/LotteryScreen.kt:373            lottery/LotteryScreen.kt:594
mail/MailInboxScreen.kt:490             postdetail/PostDetailScreen.kt:3054
postdetail/PostDetailScreen.kt:3103     sticker/StickerGroupScreen.kt:269
sticker/StickerSourcesScreen.kt:170     tools/InviteScreen.kt:499
tools/InviteScreen.kt:514               vote/VoteCard.kt:766
vote/VoteComposeDialog.kt:226
```

**需逐个判断**（正文里有 `Column`，可能只是多段文字，也可能有自定义内容），PR-8 中逐一检查：

```
account/AccountsScreen.kt:233           account/ContactScreen.kt:440
account/SecurityScreen.kt:289           assets/StardustScreen.kt:549
common/AttendanceModeDialog.kt:50       common/SpendConfirmDialog.kt:82
login/SignInDiagnosticsAction.kt:41     mail/MailInboxScreen.kt:499
postdetail/PostTitleDialog.kt:32        settings/UpdateReminderDialog.kt:54
stardust/StardustReceiveComposeDialog.kt:88   sticker/StickerManageScreen.kt:876
tools/InviteScreen.kt:451               vote/VoteComposeDialog.kt:100
```

**不替换**（含输入框或单选）：`bookmarks/OfflineManageSheet.kt:341`、`common/UserNotes.kt:118`、
`history/ReadHistoryScreen.kt:507`、`settings/theme/ThemeSettingsScreen.kt:594`、`sticker/StickerManageScreen.kt:778`、
`sticker/StickerPanel.kt:285`。

行号是起草时的快照；执行时以 `rg -n "AlertDialog\(" ui/src/commonMain` 的实际结果为准。
