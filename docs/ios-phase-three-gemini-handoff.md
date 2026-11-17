# Nodyssey iOS 阶段三：交给 Gemini 的开发任务书

更新：2026-11-17。Mac 已就绪，3A 设置候选及本轮修复已进入远端 iOS 集成线。下一批 3B 的完整实施说明见 [我的页开发任务书](ios-phase-three-profile-gemini-handoff.md)，开发分支为 `codex/ios-phase-three-profile`，目前仅准备任务书，尚未实施页面改造。下文保留原 3A 开发范围与验收要求。

## 当前从 iOS 集成线接续

统一入口：`origin/codex/ios-experience-integration`。已通过 PR 合入：

| 改动 | 功能分支提交 | PR |
| --- | --- | --- |
| 3A 设置与 Mac 修复、滑块收窄及滚动层级 | `daec81a5` | [#78](https://github.com/weishiair/nodyssey/pull/78) |
| 阅读底栏与首页导航对齐 | `c4080b68` | [#79](https://github.com/weishiair/nodyssey/pull/79) |
| 首页横屏发帖按钮裁切 | `7fd1a9c8` | [#80](https://github.com/weishiair/nodyssey/pull/80) |

三项合并后的代码提交为 `3f05536f`。本次不合入 dev/main，不改版本或发布测试包。上述分支保留，后续开发从最新集成线创建各自子分支。

Mac 已完成双 iOS 目标编译、原生及定向回归，以及设置、阅读和横屏的实际操作检查；完整设备与辅助功能验收仍有缺口。先读 [3A Mac 验证记录](ios-phase-three-mac-validation.md) 和 [首页横屏验证记录](ios-home-landscape-validation.md)。本次允许在补验 3A 可执行项目、修复已复现基线回归后推进 3B 候选；缺设备的验收项目仍保留为整体验收缺口，不据此宣称 3A 完整通过。3C 另行安排。三个 PR 的完整 GitHub CI 已启动，交付时仍在运行，结果在各 PR 的 Checks 查看。

Mac 新 shell 先执行 `source ~/.nodyssey/env.sh`。桌面 `Nodyssey 3A Validation` 模拟器已安装三项组合 App：`/Users/a092/Developer/nodyssey-ios/build/ios-home-landscape/Nodyssey.app`；保留设备与 Device Hub 窗口。具体本机路径及旧工作区状态见 `~/.nodyssey/MAC_HANDOFF.md`。

新克隆可执行：

```bash
git fetch origin
git switch -c codex/ios-experience-integration --track origin/codex/ios-experience-integration
```

已有同名本地分支时，先检查 `git status`，再切换并 `git pull --ff-only`；不要覆盖尚未整理的工作区。

## 1. 本轮先完成设置路径，再改「我的」

目标：保留 Compose 页面、共享业务和 UIKit 导航，让「我的 → 设置 → 主题／关于」的导航、选择和开关操作与已经完成的帖子阅读、回复路径保持连贯。

工作按下面的顺序串行推进，每批都交付可运行、可检查的结果：

1. **3A：设置体验统一。实现与 Mac 核心验证已完成，先补齐剩余验收。**
2. **3B：「我的」页的信息层级和视觉重量整理。下一批候选开发，先检查 3A 基线并单列剩余验收。**
3. **3C：把已验证的设置交互推广到其他设置二级页。**

三批都完成，才称为「阶段三完成」。第一天完成 3A，不等于整个阶段三完成。

原任务按 Gemini 暂无 Mac 安排了 Windows／Linux 开发和 Mac 接续两步。Mac 现已可用，下文 W1–W3 与租机准备属于原执行顺序；继续工作时使用本节的当前集成线及验证记录，不重复环境安装或已经有效完成的检查。

### 已有成果与不能倒退的部分

- 阶段一已补齐离线图片读取、图片网络限制、平台能力入口，并实现 iOS 新版检查与提醒。
- 阶段二已统一帖子阅读、更多操作、回复／发帖、草稿保护和发布错误恢复，并修复审核发现的问题。
- 用户已安装 `1.3.12-ios-test.1`（168），反馈没有发现明显问题。这份包作为体验对照。
- 该包 Codemagic 真机 Release 归档、包检查、Rust 和共享 macOS Native 测试成功；快速工作流没有运行 UIKit 模拟器回归。开发时补这一项，不把它当成尚未开发的功能。
- 不重做帖子、回复、底栏、iPad 分栏、网络核心或更新算法；若发现由本轮改动引起的回归，在本轮修复。

## 2. 基线、分支与执行权限

仓库为 `weishiair/nodyssey`，Windows 当前检出目录是 `D:/nodyssey`。Mac 上使用实际克隆目录；下文的 Windows 绝对路径用于定位同一仓库文件。

### 已建立的 iOS 集成线

统一使用 **`codex/ios-experience-integration`** 汇总本轮 iOS 改造。后续功能分支从它创建，PR 的目标也是它；本轮整体通过验收后，才经单独 PR 合入 dev。

```text
当前 dev + 阶段一 + 阶段二 + 已验证的编译修复／构建入口
                         │
            codex/ios-experience-integration
                         │
       ┌─────────────────┼──────────────────┐
       │                 │                  │
  3A 设置子分支     3B 我的页子分支     3C 二级页子分支
       │                 │                  │
       └───── 各自评审后合回 iOS 集成分支 ─────┘
                         │
            整体 iOS／Android 验收后 PR → dev
                         │
                   原有发布流程 → main
```

这条集成线是用户为本轮 iOS 大改指定的流程，替代本轮各功能直接 PR 到 dev 的默认路径；不改变其他功能的日常 dev 流程。集成线可汇总已明确标注「待 Mac 验证」的开发候选，不能把它当作已验收的稳定版本。

### 已确认的源码基线

| 对象 | 提交／说明 |
| --- | --- |
| 当前 origin/dev | `8b0746e9dd9dae5dc7a45f8672bc8a8f798e586b` |
| 阶段一 | `57b1b5c36c820338c8796f725146d11f40b4fcaf` |
| 合并测试包 | `91d42ade038d3730dd175e4666bb59a9476022f6` |
| 不含测试版本号的准备提交 | `8d09fab8e65157342850e5fe1d35be11b76b6175` |
| 阶段二 | `a71e58dbe23c46b1d66a25703aa521649c8d8b97` |
| iOS 协作触控编译修复 | `34425ae4`，已包含在准备提交中 |

集成分支从 **8d09fab8** 建立，已检查它包含当前 origin/dev、阶段一和阶段二的完整提交历史，无须重放这些提交或制造额外合并。准备提交与测试包之间仅有 `gradle.properties` 的测试版本变更；集成线保持 `1.3.11 / 165`，包含协作触控修复和快速 IPA 工作流。

开工先读 `AGENTS.md`、`git status`、分支关系和现有 diff：

- 从最新 `origin/codex/ios-experience-integration` 创建 `codex/ios-phase-three-settings`。不要再自行选择旧 dev、阶段一、阶段二或测试快照作为开发起点。
- 3A 评审通过后合回集成线。3B 从届时最新集成线创建 `codex/ios-phase-three-profile`；3C 再从最新集成线创建独立子分支。不要在集成分支直接堆多批未审代码。
- 子分支向 **`codex/ios-experience-integration`** 提 PR，不向 dev/main 提 PR。本轮已经包含一、二阶段，不再重复合并或 cherry-pick。
- 开始下一批、准备测试快照或最终合入 dev 前，维护者核对 dev 的新进展；需要同步时将最新 dev 合入集成线并检查冲突，不 rebase／force-push 共享集成线。旧的两阶段分支保留，不删除或改写。
- 不从测试版本提交继续堆功能，不把测试版本 bump 合回 dev。若名称已存在，先检查已有工作，不覆盖它。
- 不重置、清理或夹带其他人的未提交文件。当前工作区中的 `.ai_memory/` 和未跟踪 APK 不属于本任务。

新机器或新克隆开始 3A 时，在干净的仓库根目录执行：

```bash
git fetch origin
git switch -c codex/ios-phase-three-settings origin/codex/ios-experience-integration
```

若本地已有该子分支，先检查它的修改和基线，再继续已有工作，不重复执行创建命令。首次推送子分支时，PR base 仍应选择 iOS 集成分支。

本次已获用户授权创建并推送 iOS 集成分支及任务书。Gemini 收到实施任务后负责本批调查、实现、必要相邻修复和验证；其后续推送、PR 与合并按用户对该任务的实际授权执行。本次不将集成线合入 dev/main，不打标签或发布。软件安装与付费操作也需有相应授权。

## 3. 技术方向已确定

### 保留共享布局，用原生控件解决平台交互

- 页面内容继续使用现有 Compose 和 UDF：Repository → ViewModel → UiState → 页面。
- 开关、滑块和选项交互优先使用 UIKit 官方控件／菜单，通过现有 Kotlin/Native 与 `UIKitView` 路径接入。
- 分组背景、分隔线、文字和预览继续使用共享布局与主题 token；不为三个页面再建一套 UITableView／SwiftUI 页面和状态系统。
- 通用的无业务组件在 `:designsys`；设置含义、资源、选项映射和导航在 `:ui`。`:designsys` 不导入论坛业务类型。
- Android／JVM 沿用原 Material 实现与既有行为。不要为了这次 iOS 改造修改它们的视觉风格。

`AdaptiveGlassTopBar`、`AdaptiveNativeMenu`、`AdaptiveConfirmDialog` 已存在，应先复用。新接口只覆盖实际需要，不建立「全应用 UI 平台化框架」。

### 原生交互与玻璃材质必须分开

当前 `nativeChrome` 表示支持普通原生交互，`nativeGlass` 表示满足玻璃主题及系统条件。不要用「玻璃打开且 iOS 26+」作为所有新控件的入口。

- iOS 16+ 普通模式仍有一致的原生交互；关闭玻璃，不应全部退回 Material。
- 墨水屏沿用已有的不透明、低动态回退，不为了本轮覆盖其主题规则。
- 新系统材质继续走现有 Objective-C availability 桥和弱符号检查；不要在 Kotlin 中直接强引用新系统类。

### 不改这些契约

- 不新增一份 iOS 设置存储，不改设置键、默认值、枚举含义或数据模型。
- 不改变 iOS 语言下次启动生效的现有约定。
- 不改变更新通道、六小时缓存、「稍后」处理和外部签名安装路径。
- 不让已隐藏的后台轮询、网络诊断等未接入能力重新出现。
- 不删除仍被其他页面使用的单手模式偏好；iPad 已有的隐藏规则保持。
- 不升级 Kotlin、Compose、Material、导航或其他依赖来顺便实现本任务。API 以仓库固定版本及其实际源码为准。

## 4. 3A 的开发清单与验收

### A1. 统一三个页面的导航

范围：`SettingsScreen`、`ThemeSettingsScreen`、`AboutAppScreen`。

实施：

- 复用阶段二的原生顶栏，统一标题、返回按钮、工具操作和内容安全区。
- 使用普通、清楚的导航标题；首日不开发滚动折叠大标题。
- `NativeShell` 的系统导航栏当前是隐藏的，页面自行绘制顶栏。沿用这一结构，不能直接全局打开系统导航栏造成双顶栏。
- 按钮返回和系统侧滑继续操作同一份导航栈；不增加第二套页面状态或绕过现有 pop guard。
- 内容不能穿过标题；底部保留必要留白，不用固定整机尺寸推算安全区。

验收：三页往返、侧滑完成／取消、切换标签后返回均正常；iPad 不产生双标题、分栏错位或重复返回。

### A2. 先做一个开关行，再推广

先以「仅 Wi-Fi 加载图片」完成原生开关的完整接线，确认后推广到首批页面的开关。

**重要的原有契约：**`GroupedListItemSwitch` 当前只是展示，`onCheckedChange = null`，真正的切换由外层整行承担。不要直接替换成一个独立响应的 UISwitch，同时保留外层切换，造成一次点击改两次、最后看似没改。

新组件需要明确行与开关的事件归属：

- 点击行文字或直接操作开关，都只产生一次有效状态变更。
- VoiceOver 不重复朗读两个独立开关；保持清楚的名称、开关值及可用状态。
- checked/enabled、主题与回调更新后，复用的控件读取最新值和动作。
- 程序同步状态不反向触发业务写入；disabled 后已排队的事件不能继续修改设置。
- 控件在滚动列表中不能截断正常滚动，也不能把一次滚动误判成切换。

设计上保留系统开关的造型，必要时使用现有主题强调色；不叠加 Material 勾、阴影或额外玻璃背景。

### A3. 选择器和菜单按用途适配

- 明暗模式、报告展示、回复样式等少量短选项可使用原生分段控件。
- 语言、已读标记、较长的选项列表等，使用显示当前值的设置行和原生菜单；复用 `AdaptiveNativeMenu`，不强塞成横排按钮。
- 英文或大字体放不下时，用同一设置行／菜单方案；不能缩小字号或截掉选项意义。
- 菜单保留当前选中值、单项禁用和整体禁用；取消不修改设置，选择后返回同一位置。
- 现有 `isChoiceEnabled`、枚举与索引映射不能在适配后丢失。不要把显示顺序当成持久化值。

`ChoiceSegments` 被设置以外的页面使用。不要全局替换它导致搜索、代理等页面意外变化；采用有范围的设置适配入口。

### A4. 原生滑块必须保留「拖动预览，结束才提交」

目前字号、表情大小、搜索头像大小都先更新页面局部预览，结束拖动后才写设置，不能改成每帧写入。

当前数值语义：

| 设置 | 范围和间隔 | 必须保留 |
| --- | --- | --- |
| 正文字号 | 14–24sp，1sp 一档 | 通过原有换算提交 fontScale，保留正文预览 |
| 统一表情大小 | 20–90sp，5sp 一档 | 沿用 SettingsRepository 常量与预览 |
| 搜索头像大小 | 20–44dp，1dp 一档 | 沿用 SettingsRepository 常量与预览 |

实施时重新对照源码常量，不复制出第二份业务范围。

- 保留拖动中的本地值、离散吸附、边界和值转换。
- 接入原生连续变更与交互结束事件，分开「预览」和「提交」。仅把原生 slider 设成结束才发事件，会丢失实时预览。
- 手势取消、控件离开页面、外部状态更新后不能留下卡住的拖动状态或产生晚到的错误提交；保持现有设置落盘语义。
- VoiceOver 的增减也必须能真正提交，不能只处理手指抬起事件。
- 不用新 Material Slider overload 改写 Android 回退。当前代码已有 Android 与 CMP Material API 不一致的说明，必须保留该兼容条件。

### A5. 分组外观和主题页

- 用现有页面／卡片／分隔线 token 调整 iOS 设置分组，减少多层描边、嵌套卡片、无用途投影。
- 内容层实底优先；不把整页设置行都玻璃化。
- 标题、当前值、必要说明建立清楚层级，不为塞满一屏而缩小说明文字。
- 「主题」页保留预设、自定义配色、已保存主题、色彩风格和预览。适配顶栏、分组、开关与常规选择，不重写颜色生成或主题保存逻辑。
- 颜色选择器和重命名表单是有内容的编辑界面，继续保留原能力；`AdaptiveConfirmDialog` 仅用于适合的简单确认，不用它替换表单。

### A6. 关于页与反馈

- 关于页沿用已有版本检查状态、更新说明、重试和发布页入口，只统一呈现和操作风格。
- 项目、反馈、日志、许可等入口保留；未实现的崩溃捕获不因 UI 重做而被画成可用。
- 清缓存、删除主题等动作保持原有业务含义和确认条件；清普通缓存不能删除离线下载。
- 简单确认和菜单复用阶段二组件；需要保留详细信息的错误区域、更新说明和编辑表单不强行改成短暂提示。

### 首日范围明确排除

「我的」页重排、代理／DoH 表单重写、邮箱提醒、后台推送、崩溃采集、网络诊断、导航进程恢复、分享扩展、Universal Links、应用内 Safari、全应用图标替换、全应用性能专项。

这些不是第一、二阶段未完成项，也不能混入 3A 以扩充工作量。

## 5. 先开发，再集中使用 Mac

### 现在：Gemini 在 Windows／Linux 先完成 W1–W3

#### W1. 建立子分支，先做实际接入

- 按第 2 节从 iOS 集成线拉 `codex/ios-phase-three-settings`，检查原有改动。
- 读直接调用者后，先将 A1 的顶栏和 A3 的菜单接入实际设置页面，复用已有适配组件，不先搭一套空框架。
- 按 A2／A4 定义开关行、滑块的状态与事件接口，并保留现有 Android／JVM 行为；新接口同时有实际页面调用。
- UIKit actual 可同步编写，但要基于固定版本源码／官方 API，不用空实现、常量返回或无声禁用冒充接入。无 Mac 时把它列为「已编写，待 Apple 编译和运行验证」。

产出：可编译的共享入口、真实页面接入、保持可用的 Android／JVM 实现；不修改原有业务和存储。

#### W2. 完成 3A 共享页面与可在本机执行的回归

- 完成设置首页、主题和关于的页面改造与资源接入；iOS 样式通过有范围的适配入口实现，不改 Android 全局设计 token。
- 保留所有选项、禁用关系、实时预览、错误恢复和更新能力；按 A1–A6 检查完整页面。
- 执行格式、定向逻辑测试、Android／JVM 及适用构建；使用现有渲染入口查看共享布局和 Android 回退。
- 对共享代码中可独立验证的映射、状态处理补必要回归；原生控件事件测试可以写入 iosTest，执行状态明确留到 Mac。

产出：共享部分可运行且已检查的 3A 候选；截图注明 Android／共享渲染，不能称为 iOS 截图。

#### W3. 做一次可接续的交接，不用等待租机

- 按逻辑提交，列出 W1／W2 已完成项和 Mac 待验证项，给出分支、提交、日志与截图。
- 若已获推送／PR 授权，可提交面向 iOS 集成线的 PR。审核认可后可作为「待 Mac 验证候选」合回该实验集成线，但不能因此宣布 3A 完成，也不能将其作为稳定变更合入 dev。
- 暂未具备推送／合并授权时，保留可用的本地提交和交接信息；不停止独立可做的实现与检查。
- 不提前开展 3B／3C，也不反复在 Windows 尝试只有 Mac 能运行的编译任务。

### Mac 到位前：准备租赁环境

- 准备 Git 访问、远程桌面和实际源码目录；凭据使用已有安全机制，不写进脚本或聊天。
- 确认机器为 Apple Silicon；优先 32GB 内存，具备完整 Xcode、模拟器、JDK 21、Android SDK 37 和固定 Rust 工具链所需环境。
- 当前 Codemagic 成功配置为 Xcode 26.6，要求 iOS SDK 至少 26；本地优先同一配置。查看实际安装路径，必要时仅对当前命令设置 DEVELOPER_DIR，不硬编码不存在的 Xcode。
- 缺少大型组件时按实际安装授权处理，不默默升级系统或项目依赖。
- 普通设置界面开发不要求复制真实用户数据、Cookie 或签名密钥到租赁机器。

### Mac 首日：只补 Apple 依赖和实际体验验证

时间为约 8–10 个有效工时的预算，不含服务商排队。以 W3 的明确提交接续，不重新从旧 dev 开始；冷编译变慢时停止扩展范围，保留回归和交付时间。

| 时段 | 工作 | 产出 |
| --- | --- | --- |
| 0–1.5 小时 | 拉取候选提交，核对环境，跑通 Debug 模拟器；需要比较时运行 8d09fab8 对照版 | 可运行候选与环境记录 |
| 1.5–4 小时 | 编译双 iOS 目标，修复 UIKit actual，跑原生控件和导航定向回归 | 真实原生开关、滑块、菜单和顶栏可用 |
| 4–6 小时 | 按第 7 节实际操作三页、主题回退与 iPad，查看真实画面并修复 | iOS 截图／短录屏及修复 |
| 6–10 小时 | 完成候选的必要回归、构建和交接；有出包授权再制作测试快照 | 可评审提交、验证记录及适用测试包 |

若时间不足，写明哪一项实现或验收待续，不以跳过 UIKit 回归换取“完成”。如果同一提交的共享检查已经有效通过，不为了安心重复运行；Mac 修复改变共享代码后，再补受影响检查。

## 6. 已核对的代码入口

这里只列直接入口，Gemini 仍需读完整受影响函数／组件和相邻调用者。

| 职责 | 当前文件 |
| --- | --- |
| 设置首页、字号和局部预览 | `D:/nodyssey/ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/SettingsScreen.kt` |
| 分组、菜单行、字段与保存按钮 | `D:/nodyssey/ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/SettingsListItems.kt` |
| 设置状态与提交入口 | `D:/nodyssey/ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/SettingsViewModel.kt` |
| 主题页面 | `D:/nodyssey/ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/theme/ThemeSettingsScreen.kt` |
| 关于与更新显示 | `D:/nodyssey/ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/AboutAppScreen.kt` |
| 设置导航注册 | `D:/nodyssey/ui/src/commonMain/kotlin/io/github/nodyssey/SettingsEntries.kt` |
| iOS 导航壳 | `D:/nodyssey/ui/src/iosMain/kotlin/io/github/nodyssey/NativeShell.kt` |
| 通用行及展示用开关 | `D:/nodyssey/designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/GroupedListItem.kt` |
| 原有共享分段控件 | `D:/nodyssey/designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/ChoiceSegments.kt` |
| 现有适配接口／UIKit 实现 | `D:/nodyssey/designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/AdaptiveGlass.kt`；`D:/nodyssey/designsys/src/iosMain/kotlin/io/github/plaza/designsys/component/AdaptiveGlass.ios.kt` |
| 确认对话框 | `D:/nodyssey/designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/AdaptiveConfirmDialog.kt`；`D:/nodyssey/designsys/src/iosMain/kotlin/io/github/plaza/designsys/component/AdaptiveConfirmDialog.ios.kt` |
| 原生交互与材质条件 | `D:/nodyssey/designsys/src/commonMain/kotlin/io/github/plaza/designsys/theme/GlassRenderer.kt` |
| iOS 能力声明 | `D:/nodyssey/ui/src/iosMain/kotlin/io/github/nodyssey/ui/settings/PlatformFeatures.ios.kt` |
| UIKit 事件复用测试参考 | `D:/nodyssey/designsys/src/iosTest/kotlin/io/github/plaza/designsys/component/NativeGlassButtonHostTest.kt` |
| 设置现有回归 | `D:/nodyssey/ui/src/androidHostTest/kotlin/io/github/nodyssey/ui/settings/SettingsScreenTest.kt` |
| 当前编译依赖 | `D:/nodyssey/gradle/libs.versions.toml` |

新文字进 Compose Resources 的简中、英文、繁中三份；繁中继续用 `values-zh-rTW`。不要硬编码中文或从 UIKit 单独生成一套翻译。

官方实现依据如下；查阅 API 时同时对照仓库固定版本的实际接口，不直接复制最新文档中未进入当前版本的调用：

```text
Compose 与 UIKit 互操作：
https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html
Apple 开关设计：
https://developer.apple.com/design/human-interface-guidelines/toggles
UISwitch：
https://developer.apple.com/documentation/uikit/uiswitch
UISlider：
https://developer.apple.com/documentation/uikit/uislider
```

## 7. 验证：区分业务回归、平台事件和实际画面

### 自动化只覆盖会让用户操作出错的行为

复用现有测试；新测试必须对应本次引入或修复的真实风险，例如：

- 复用控件后调用旧回调／旧账号动作。
- 状态同步触发业务写入，或行与开关重复提交。
- 禁用后排队事件仍生效。
- 滑块离散值映射出错、拖动中提前落盘、一次结束重复提交、VoiceOver 改了预览却没提交。
- 菜单重建后选中了另一枚举值，或被禁用的选项仍能写入。

不要新增尺寸、颜色、文案、标签显示、截图 golden 或纯 callback 透传测试。视觉效果靠实际查看；已有渲染入口可用于生成对照图片，不把它当成 iOS 渲染证据。

### 开发中先跑定向检查

**当前机器先执行共享检查。**在仓库根目录执行；Windows 用 `gradlew.bat`，Mac／Linux 用 `./gradlew`。测试过滤按实际新增类补充，不照抄不存在的测试名称。

```bash
./gradlew spotlessCheck
./gradlew :ui:testAndroidHostTest --tests '*SettingsScreenTest' --tests '*ThemeSettingsScreenTest' --tests '*UiImportBoundaryTest' --tests '*HardcodedChineseLiteralTest'
```

**Mac 到位后才执行 Apple 编译和原生回归：**

```bash
./gradlew :iosapp:compileKotlinIosArm64 :iosapp:compileKotlinIosSimulatorArm64 --max-workers=2

device=$(bash .github/scripts/boot-ios-test-simulator.sh)
./gradlew :designsys:iosSimulatorArm64Test :ui:iosSimulatorArm64Test "-Pnodyssey.iosTestDevice=$device" --max-workers=1
xcrun simctl shutdown "$device"
```

实际运行时为模拟器加失败退出清理，确保测试失败也关闭本次启动的设备；不要关闭用户另开的模拟器。优化 Release 链接前关闭测试设备。

需要构建可运行模拟器 App 时，复用已有入口：

```bash
bash .github/scripts/build-ios-simulator.sh "$PWD/build/ios-phase-three-simulator"
```

脚本完成构建与包检查后，仍要在实际模拟器启动和操作。

### 当前机器交接与最终验收分别满足什么

W3 交接时，先按 AGENTS.md 和当前 CI 完成当前机器可执行的共享门禁，不用仅 `:app:testDebugUnitTest` 冒充全仓测试：

```bash
./gradlew spotlessCheck testDebugUnitTest testAndroidHostTest jvmTest :app:lintDebug :app:assembleDebug :app:bundleRelease
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
git diff --check
./gradlew --stop
```

Windows 使用本机已配置的 Python 命令；保留既有环境问题的实际失败，不把无法运行写成通过。

3A 最终验收和整体合入 dev 前，还需在 Mac 执行 `./gradlew :shared:macosArm64Test`，保留上述 UIKit 模拟器回归、双目标编译与实际操作证据，并在构建结束后 `./gradlew --stop`。当前没有 Mac 只影响这部分验收，不阻塞 W1–W3。

CI 的 Android 设备／压缩构建设备门禁按原工作流执行；若需要推送触发 CI，先取得相应授权，不新增替代流水线。

既有问题只在影响本候选时核对基线并记录，不能改断言、删测试或静默跳过来变绿。前两阶段已有证据可复用；不单独重跑无变化的 dev。

### 实际交互验收表

| 场景 | 需要看到的结果 |
| --- | --- |
| 首批三页往返 | 单一顶栏；按钮返回与侧滑一致；取消侧滑不丢页面状态 |
| 开关与滚动 | 点击行、操作开关各只切换一次；上下滑动不会误切换 |
| 三种滑块 | 实时预览，正常结束才落盘；重进显示已提交值；VoiceOver 可修改 |
| 单选菜单 | 当前值正确；取消不改；禁用条件有效；返回设置位置合理 |
| 主题变化 | 浅／深、玻璃开／关、墨水屏切换后可读且可操作；不残留原生视图或遮罩穿透 |
| iOS 较早版本 | 至少一个可用的 iOS 16–18 运行时，冷启动并进入三页；不能只在 iOS 26+ 验证 |
| iPhone 与 iPad | 手机窄屏和 iPad 窗口／分栏中无双顶栏、安全区或菜单锚点问题 |
| 语言与较大字体 | 实际查看简中、英文、繁中及较大字体；不截掉关键选项；iOS 语言重启生效提示保留 |
| 前两阶段哨兵回归 | 打开帖子、回复并保存草稿、返回阅读；离线图片仍可读；更新入口仍可用 |
| Android | 设置主要操作仍正常，无新控件替换或运行期 Material API 错配 |

主题与设备条件按风险挑选代表组合，不要求无意义地做全部笛卡尔积。缺少较早运行时或 iPad 等条件时，写明哪一项未验收，不宣称全平台验收完成。

## 8. 提交、出包和交接

### 提交建议

每个提交包含能实际调用的新能力和对应验证，不提交只有壳的组件库：

1. 原生设置控件及设置首页接入。
2. 主题／关于路径的导航、分组和反馈统一。
3. 实机／模拟器发现的问题及文档收尾，按逻辑拆分。

必要时合并紧密关联的变更，不为了凑提交数量拆文件。显式暂存本任务文件，不夹带用户工作。

### 如果用户要求新的 IPA

- 从 **iOS 集成分支上已核对的候选提交**创建独立 `test/<versionName>` 快照，再单独提交版本号。单项排障包若需从子分支创建，先明确它不是整体集成包。
- 查最新发布与已有测试版本后分配号码；`1.3.12-ios-test.1 / 168` 已被使用，不能重复或直接认定下一号仍空闲。
- 复用现有 Codemagic／Xcode 归档与包核对，不另建发布平台。快速 `ios-ipa` 不自动替代本轮必须完成的 UIKit 回归。
- 产物写清源码提交、版本、bundle ID、arm64、实际 SDK、最低 iOS 和签名状态。当前交付方式是未签名 IPA，用户自行签名。
- 不将测试 bump 合回功能分支、iOS 集成分支或 dev；未经授权不打标签、发布 Release 或发公告。

### Gemini 的最终报告必须能供审核直接使用

1. **完成范围：**按 A1–A6 逐项区分「共享部分完成」「iOS 已编写待验证」「iOS 已验收」「未实现」，不能只写「已原生化」。无 Mac 时先交付 W3，不等待所有平台可用。
2. **源码：**分支、提交、基线及主要文件；注明是否仍有未提交改动。
3. **证据：**实际执行命令及结果、日志路径、模拟器系统和设备、改造前后截图／短录屏。
4. **行为：**明确如何避免双重切换、如何保留滑块预览／提交和状态复用，列出实际改变的交互。
5. **缺口：**失败或未跑的必要检查、影响的验收项、下一位负责人的动作。
6. **可运行结果：**模拟器 App 位置；如已获出包授权，再提供对应 IPA 和准确签名状态。

交付后由审核者核对范围、源码和证据。待 Mac 验证候选经授权和评审可汇总到 iOS 集成线；正式验收时补齐必要 Mac 证据，并由用户在 iPhone 体验复测。本次 3B 候选开发的前置检查与允许范围以新的我的页任务书为准；所有批次完成后，经用户确认单独 PR 合入 dev；缺少必要 Apple 证据时，继续留在实验集成线上。

## 9. 3B／3C 的后续安排

### 3B：「我的」页，单独评审

3A 候选及本轮修复已合回 iOS 集成线。`codex/ios-phase-three-profile` 已从集成线 `75edf0ee` 建立并准备任务书，后续接手该分支；完整范围、入口清单、3A 前置检查与 Mac 验收见 [我的页开发任务书](ios-phase-three-profile-gemini-handoff.md)。主要入口：`ui/src/commonMain/kotlin/io/github/nodyssey/ui/profile/ProfileScreen.kt`。

- 首层突出账号、签到状态和常用入口；资产信息减轻色块，不删鸡腿／星辰、等级、额度等数据。
- 常用入口与低频工具分开；减少重复卡片和密集宫格，保留全部现有导航目的地。
- 已登录、游客、多账号切换、未读邮件／抽奖结果／更新提示继续有效。
- iPad 保留已实现的宽屏布局和可用宽度判断，不写死整屏尺寸。
- 图标优先统一该页面的导航与工具语义，品牌、等级和内容标记保留特色。
- 不改会话、资产、签到和邮件业务；若发现这些独立功能缺口，另行记录。

验收看「用户能否更快找到账号、签到和常用入口」，以及两端原行为是否保留，而不是以少了多少卡片计完成度。

### 3C：推广到设置二级页

从最新 iOS 集成线创建独立子分支。先通知设置，再代理／DoH、图床等复杂表单；一次闭环一条路径，PR 目标仍为 iOS 集成分支。

- 复用已验证的导航、分组、开关、菜单和状态反馈。
- 保留测试／保存、校验、密码隐藏、代理与 DoH 的启用和禁用关系。
- 不因控件适配改变网络配置、请求路由或 Cookie／WebView 安全边界。
- 分享扩展、后台通知、诊断和崩溃采集继续作为独立目标，不加入阶段三。

## 10. 可直接附给 Gemini 的启动指令

> 请阅读 `docs/ios-phase-three-profile-gemini-handoff.md`，使用已准备的 `codex/ios-phase-three-profile` 分支，先完成 3A 基线检查与可执行的剩余验收，再开发 3B「我的」页候选。Mac 已就绪，读取 `~/.nodyssey/MAC_HANDOFF.md` 后接续现有环境。保留全部入口、条件与业务恢复路径，Android／JVM 保持原状。按新任务书开发、验证、提交并推送同名功能分支，PR 目标为 `codex/ios-experience-integration`，交给 Codex 审核；不自行合并、出包或开展 3C。
