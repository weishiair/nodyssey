# Nodyssey iOS 3C-1：通知设置路径开发任务书

更新：2026-11-18。Gemini 负责开发、自检及本批问题修复；Codex 负责独立审核和 Apple 平台验收。现在可以在 Windows 推进本批开发，Mac 的构建和模拟器操作继续按 3A → 3B → 3C-1 排队。

## 1. 交付目标：通知入口和管理页连贯、清楚、可操作

将「通知提醒 → 提醒关键词／追踪的帖子 → 返回」整理为与 3A 设置首页一致的 iOS 路径：单一顶栏、安静的分组列表、容易发现的编辑操作、正确的键盘和底部安全区。继续使用 Compose 页面、现有 UIKit 导航和共享业务，不重写整套设置系统。

本批只做三页及其必要相邻接线：

1. **通知提醒：**说明当前平台能做什么，保留全部现有目的地和数量摘要。
2. **标题关键词管理：**以提醒关键词为主路径，兼容同一页面的屏蔽关键词用途；新增、删除和持久化语义不变。
3. **追踪的帖子：**保留帖子信息、进入帖子及取消追踪，空列表状态同样完整。

用户应能从设置进入通知页，理解 iOS 当前提醒能力，管理关键词和追踪列表，进入 Telegram 绑定或可用的抽奖入口，再返回原路径。Android／JVM 保持原有布局和行为，墨水屏保留既有回退。

后续 3C-2 代理／DoH、3C-3 图床设置另开批次；本次不顺带改造。

## 2. 基线、独立分支和交付权限

编写任务书时核对的远端状态：

| 对象 | 当前记录及用途 |
| --- | --- |
| iOS 集成线 | `origin/codex/ios-experience-integration`，`323b7dc96be49ed72fadb281afde784772726b45` |
| 3B 我的页候选 | PR #81，`a0decc529aa3f93a1d165fe63114761ce5b5eec2`；已交付开发候选，Apple 平台独立验收待完成 |
| 本批新分支 | **`ios/phase-three-notifications`**，从开工时最新的 iOS 集成线创建，不从 3B 功能分支创建 |
| PR 目标 | **`codex/ios-experience-integration`**，不直接进入 dev/main |

开工读取 `AGENTS.md`、本任务书及 3A 组件实现，执行 `git status --short --branch`、`git worktree list` 和 `git fetch origin`，记录实际基线完整 SHA。集成线若已更新，审阅新增差异后以实际集成线为准；不追随未合入的其他功能分支。

使用独立工作区。Windows 建议目录为 `C:/Users/weishi/.codex/worktrees/ios-phase-three-notifications/nodyssey`。确认同名分支和目录不存在时，可从已有 checkout 执行：

```powershell
git worktree add -b ios/phase-three-notifications `
  C:/Users/weishi/.codex/worktrees/ios-phase-three-notifications/nodyssey `
  origin/codex/ios-experience-integration
```

已存在同名分支或目录时先检查并复用正确的工作区，不用 `-B`、reset 或删除来覆盖。不要在 3B 工作区切换分支，也不要改动 3A／3B 的验收目录。

本任务书可能先从 3B 的文档目录交到你手中；先读完，再进入本批工作区。新基线中没有此文件时，只复制本任务书到新工作区的同名 `docs/` 路径，不要为取得文档而 merge 整个 3B 分支。

Gemini 的交付范围包括实现、自检、按逻辑提交并推送本批功能分支，以及创建目标为 iOS 集成线的 PR。PR 合并、dev/main 集成、改版本、标签、发布和 IPA／APK 打包不在本批范围内。

## 3. 并行规则：Windows 开发，Mac 验收串行

- Gemini 现在在 Windows 的独立工作区开发；Codex 继续完成 3A、随后 3B 的独立验收，不启动第二套 Mac 验收来抢资源。
- Mac 的重型编译、Native 链接和模拟器操作一次只有一个负责人。3C-1 等前一任务结束当前资源占用、交付报告并交接后再进入 Mac，不因日期变化或 SSH 延迟低就假定机器空闲。
- Codex 可在 Mac 本机或经 SSH 执行验收，两种入口使用的是同一台 Mac，遵守同一资源安排。
- 保留用户正在观看的 `Nodyssey 3A Validation` 及 Device Hub；不在该设备安装、旋转或切换主题。不终止其他人的构建，也不在他人构建期间执行 `gradlew --stop`。
- 如果 3A／3B 验收交来已复现且归属明确的回归，由原负责人在对应修复分支处理；不把其他批次的修复夹进本批。Gemini 负责的问题先修复并交付新 SHA，再继续本批开发。
- 复用 3A 现有组件即可开工。若发现共用组件缺陷影响本批，提交复现及受影响调用清单，协调在独立修复中解决；不改旧候选来让原验收记录失去对应源码。

## 4. 必须保留的入口、状态和数据含义

### C1.1 通知页：呈现真实能力，不画无效开关

`PlatformFeatures.ios.kt` 当前定义 `platformHasBackgroundNotificationPolling = false`。这表示通知设置页不提供 Android 的后台轮询功能，不等于 iOS 所有提醒能力都不存在。

| 对象 | 保留合同 |
| --- | --- |
| iOS 能力说明 | 保留当前后台轮询不可用的说明，不宣称开启关键词后会自动后台推送 |
| 抽奖入口 | 只在 `onOpenLottery != null` 时显示，继续进入原 `LotteryKey`；本批不改抽奖自身的提醒或调度 |
| 提醒关键词 | 原 `TitleKeywordsKey(alerts = true)`，数量来自 `alertCounts.keywords` |
| 追踪的帖子 | 原 `TrackedThreadsKey`，数量来自 `alertCounts.trackedThreads` |
| Telegram | 原 `onOpenTelegram` → `AccountContactKey`，继续进入网站联系方式绑定路径，不替换成一个外部 Telegram 链接 |
| Android 轮询设置 | 总开关、15／30／60 分钟选择、Wi-Fi 限制、静默时段、互动／消息／邮件／关键词／追踪开关及禁用关系全部保留 |

iOS 主布局采用「能力说明 → 可用的抽奖入口 → 已保存提醒管理 → Telegram 绑定入口」的层级。Telegram 可收为普通分组行，减轻独立彩色卡片；不能为统一外观删除用途说明或入口。

平台能力判断仍由现有 `backgroundPollingSupported` 提供，不用 `nativeChrome` 代替能力判断。Android 关闭总开关时，管理关键词、管理追踪列表和 Telegram 仍沿用各自原有可用条件，不能把整页统一禁用。

不因进入通知页自动申请通知权限，不改变设置值与系统授权相互独立的现有协议，也不新增后台推送、轮询器或调度任务。

### C1.2 关键词：编辑即生效，提醒和屏蔽不能混写

复用现有 `TitleKeywordsRoute`／`TitleKeywordsViewModel` 和 `TitleKeywordStore`：

- `ALERT` 与 `BLOCK` 继续由导航 key 决定，保留各自标题、说明及独立数据。即使屏蔽关键词主要由其他页面管理，现有 `TitleKeywordsKey(alerts = false)` 仍可恢复并正确工作。
- 沿用 `MAX_KEYWORD_LENGTH`（当前为 30）、已有裁剪、去首尾空白、小写存储及去重规则，不在 UI 另写一套标准。
- 添加按钮与键盘完成键走同一个现有新增入口；空白不入库、重复词不新增记录，输入清空时机沿用现有实现。普通重组、返回或切换主题不能重复执行新增。
- 删除操作只删除当前种类的目标词；保留清楚、可发现的删除动作，不能只提供隐藏手势。仍为即时编辑，不新增另一个「保存」步骤或「清空全部」能力。
- 保留空列表、较长词、较长列表和键盘弹出时的可用性。进入子页再返回时，不因重新创建无必要状态而丢失原页面位置；不借本批引入新的草稿系统。

输入可继续使用 Compose，优先复用已有字段和样式。原生化不要求将所有输入框替换成 UIKit，也不为三页新建通用表单框架。

### C1.3 追踪列表：打开与取消追踪是两个独立动作

- 行主体继续按正确 `postId` 打开帖子；取消追踪只删除该记录，不能同时打开帖子或操作相邻行。
- 保留标题、已有作者／板块信息和已知回复数。`lastKnownCount < 0` 表示未知，沿用不显示计数的行为，不能补成零。
- 保留空列表和取消最后一项后的可用状态，列表变化后通知页数量仍来自原数据流。
- 取消追踪仍走现有 store，不重算通知基线、不增加服务器操作，不顺带改追踪上限或新回复检测算法。
- 删除动作在触控与辅助功能下均可发现；不把原本直接可用的操作改成必须经过新的中间页。

## 5. 视觉和平台实现方向

三页复用 3A 的 `SettingsTopBar`、`SettingsGroup`、`settingsPageColor()` 和 `settingsScrollPadding()`，采用同一套分组间距与可读宽度。必要时增加本页面局部组件，不复制导航壳或业务状态。

1. **单一顶栏：**iOS 走现有自适应原生顶栏和返回接线；不同时保留可见的 Material 大标题，不重写 `NativeShell`。Android／JVM 维持原来 `OneHandTopAppBar` 与滚动联动。
2. **一组一个容器：**按实际用途分组，不给每行再套一张卡片。正文与说明保持可读，不靠缩小英文和大字体解决排版。
3. **普通 iOS 也适配：**布局边界沿用 `LocalPlazaLayers.current.nativeChrome`，不只判断 `nativeGlass`。玻璃关闭仍用 iOS 布局，墨水屏保持既有回退。
4. **安全区和滚动：**沿用设置页已有的顶部裁切及底部留白策略，检查固定标题、浮动底栏和键盘；不叠加两遍 inset，也不让滚动控件盖住标题。
5. **宽屏：**依据当前容器宽度和已有 `readableWidth()` 限制内容宽度，iPad／窄窗口不拉满整屏，也不使用设备型号判断布局。
6. **辅助功能：**保留名称、状态与独立操作语义。返回、输入、添加、删除、打开帖子不重复响应；VoiceOver 的实际可用性由后续 Mac／真机验收确认。
7. **资源：**优先复用现有文案。新增用户可见字符串补齐简中、繁中 `values-zh-rTW`、英文；不在 Kotlin 新写硬编码中文，不改既有平台能力说明的含义。

## 6. 代码入口和允许修改范围

以下路径均相对仓库根目录。先读完整相关函数、调用者与实际固定版本 API，再编辑。

| 文件 | 用途与边界 |
| --- | --- |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/NotificationSettingsScreen.kt` | 主开发入口；Route、平台分支、全部入口与 Android 频率选择 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/TitleKeywordsScreen.kt` | 关键词输入、列表和删除呈现 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/TrackedThreadsScreen.kt` | 追踪列表和 ViewModel 同文件，主要改呈现，不改 store 行为 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/NotificationSettingsViewModel.kt` | 设置 SSOT、关键词和追踪数量流，原则上保留 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/TitleKeywordsViewModel.kt` | 输入限制、添加与删除，原则上保留 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/SettingsEntries.kt` | 真实导航、条件抽奖入口、ALERT／BLOCK 分离 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/SettingsListItems.kt` | 已有顶栏、分组、背景及留白；优先直接调用 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/SettingsScreen.kt` | 3A 组件的现有用法，作为接入参考，不重做设置首页 |
| `ui/src/iosMain/kotlin/io/github/nodyssey/ui/settings/PlatformFeatures.ios.kt` | iOS 能力事实，读取核对，不把 false 改成 true 来展示控件 |
| `ui/src/iosMain/kotlin/io/github/nodyssey/ui/settings/NotificationPermission.ios.kt` | 现有系统授权协议，本批不扩展 |
| `shared/src/commonMain/kotlin/io/github/nodyssey/data/PersonalStores.kt` | 关键词／追踪持久化及未知数值合同，读取核对，不改算法或 schema |
| `shared/src/commonMain/kotlin/io/github/nodyssey/data/BackgroundAlerts.kt` | 已有关键词／新回复检测，读取核对，不增加 iOS 轮询 |
| `ui/src/androidHostTest/kotlin/io/github/nodyssey/render/NotificationSettingsScreenRenderTest.kt` | Android 与无后台轮询分支的现有查看入口 |
| `ui/src/commonMain/composeResources/values{,-en,-zh-rTW}/strings.xml` | 必需的三语资源 |

允许在 `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings` 增加局部呈现组件及真实回归所需的测试。保持 Repository → ViewModel → UiState → Compose 的现有方向，不把站点业务放进 `:designsys`。

本批不改依赖版本、全局主题 token、账号会话、通知检测算法、网络配置、数据库／迁移、权限协议、更新协议和版本号；不实施代理／DoH、图床、分享扩展、后台推送、网络诊断或崩溃采集。若本批改动造成相邻回归，修复属于本批交付。

## 7. 开发顺序和 Gemini 自检

### M0：核对分支与原貌

记录基线 SHA、三页的入口／条件／动作清单，以及 Android 和无后台轮询分支的现有画面。当前没有 Mac 空档时可用现有 Robolectric 渲染查看共享呈现；这种画面不写成 iOS 原生验收。

### M1：完成通知设置，再闭环两个管理页

先接入顶栏、页面背景、分组与安全区，确认能力分支及四个目的地无变化；再完成关键词和追踪列表。走通进入、操作、返回的闭环后再收敛视觉，不交付静态假状态或未接线按钮。

### M2：执行当前机器可用的检查

Windows 使用仓库 wrapper：

```powershell
.\gradlew.bat spotlessCheck :ui:compileKotlinJvm
.\gradlew.bat :ui:testAndroidHostTest --tests '*SettingsScreenTest' --tests '*UiImportBoundaryTest' --tests '*HardcodedChineseLiteralTest'
.\gradlew.bat :shared:testAndroidHostTest --tests '*BackgroundAlertsTest'
.\gradlew.bat :ui:testAndroidHostTest -PrenderUi --tests '*NotificationSettingsScreenRenderTest'
git diff --check
```

实际打开 `ui/build/outputs/renders/` 中本轮生成的通知页浅／深色、无后台轮询分支及频率选择画面，对照 Android 原貌。关键词和追踪页通过现有查看手段补充代表画面，不新增截图金图断言。

编写任务书时，代码中没有专用的 `NotificationSettingsViewModelTest`、`TitleKeywordsViewModelTest` 或 `TrackedThreadsViewModelTest`，不要在报告中列出未存在、未执行的套件。新增测试前先写明要抓住的真实 bug，例如提醒／屏蔽写错种类或取消追踪误触发导航；测试必须经过有后果的状态或交互分支，不只断言 callback 收到原值。

不测试 dp、边界、颜色、字号、间距、显示文案或像素相似度。不为凑覆盖率测试纯透传、常量或只由裸 `if` 显示的字段；已有渲染是人工查看工具，不是视觉断言。

交付前执行 `.github/workflows/ci.yml` 的共享门禁：

```powershell
.\gradlew.bat spotlessCheck testDebugUnitTest testAndroidHostTest jvmTest :app:lintDebug :app:assembleDebug :app:bundleRelease
python -m unittest discover -s .github/scripts -p 'test_*.py'
git diff --check
```

实际使用本机可用的 Python 命令。已有同候选、同输入的有效证据可以复用。Windows 如遇 Room／DataStore 文件路径问题，保留日志并核对基线；不能仅因 3B 遇到过就认定新失败也是环境问题，更不能删测试或改断言来通过。

有其他构建时先协调；本轮构建结束且不会影响他人时执行 `.\gradlew.bat --stop`。自检后可以交付“待 Mac 验收候选”，不必一直占用开发工作区等待 Mac，但不能把它写成 iOS 验收完成。

## 8. Mac 独立验收：资源交接后执行

Gemini 提供固定候选完整 SHA、实际基线、PR、自检日志、入口清单和已知缺口。Codex 在独立 worktree 固定该 SHA，不在 Gemini 目录运行，不在验收中自动更新候选，也不修改生产代码。

新 shell 执行 `source ~/.nodyssey/env.sh`，读取 `~/.nodyssey/MAC_HANDOFF.md`。复用已有 Xcode／JDK／SDK，不重复安装或修改全局 `xcode-select`。本地 Codex 与 SSH 发起的验收均遵守同一资源交接。

先核对可复用证据，再执行受影响的 Apple 检查：

```bash
./gradlew :iosapp:compileKotlinIosArm64 :iosapp:compileKotlinIosSimulatorArm64
bash .github/scripts/build-ios-simulator.sh "build/ios-notifications-$(date +%Y%m%d-%H%M%S)"
```

原生／UIKit 回归按修改范围执行 `:ui:iosSimulatorArm64Test`；若修改 `:designsys` 再覆盖其受影响测试。使用自己的验收设备并通过 `-Pnodyssey.iosTestDevice=<UDID>` 指定。仓库启动脚本当前会选首个可用 iPhone，不能在共享 Mac 上不核对就直接运行，以免使用他人的设备。

构建安装来源明确的候选 App，实际操作并查看完整屏幕截图；横屏使用 `XCUIScreen.main.screenshot()`，不要沿用此前会错误裁切的 application 截图。临时驱动、合成数据、日志和图片放仓库外，不提交认证数据。

| 场景 | 验收动作与预期 |
| --- | --- |
| 完整路径 | 设置进入通知页，再进关键词／追踪列表并返回；按钮返回和侧滑完成／取消不丢栈、不出现双顶栏 |
| iOS 能力 | 不出现无效的 Android 轮询开关；保留说明、数量、Telegram 及条件抽奖入口；进入页面不自动弹权限 |
| 条件入口 | `onOpenLottery` 有值／空值均合理，不能留下无效占位行 |
| 关键词 | 添加、键盘提交、空白、重复、长度上限和删除；提醒／屏蔽种类互不污染，重进仍有已写入数据 |
| 键盘与列表 | 添加／删除动作不被键盘或底栏遮住；长列表可滚动，返回后页面位置合理 |
| 追踪帖子 | 正确进入帖子并返回；取消目标项不导航、不删错项，取消最后一项呈现空列表；未知回复数不显示零 |
| 数量联动 | 子页修改后返回，通知页摘要随原数据源更新，不另建计数缓存 |
| 主题和尺寸 | 浅／深色、玻璃开／关、墨水屏；iPhone 左右横屏转回竖屏、iPad 宽窄窗口；无标题覆盖、重复留白或不可达动作 |
| 语言和辅助功能 | 简中／繁中／英文、大字体；实际 VoiceOver 输入、添加、删除、打开帖子和返回，独立动作不重复响应 |
| 平台回归 | Android 原总开关和子项启停关系、频率选择及管理入口可用；提醒说明不被误搬到 Android |
| 相邻路径 | Telegram 到原联系方式页，抽奖到原抽奖页；帖子返回和原设置首页受影响部分正常 |

优先使用本地测试数据和专用测试环境。不要为了演示关键词或取消追踪删除用户真实记录；真实账号绑定、对外写入和数据删除遵循用户对该操作的授权。

旧系统、iPad 或真实 VoiceOver 条件不可用时，写明未执行项和补齐条件。合成状态只证明对应 UI／接线，不能替代真实持久化、系统授权或网络结果。未变化的 3A／3B 有效证据继续引用，不为本批重跑全部历史矩阵。

报告输出到 `~/.nodyssey/ios-acceptance/3c-1/<完整候选SHA>/`，包含 `report.md`、命令日志、实际截图及问题清单。测试负责人给出通过／失败／未执行和来源提交；Gemini 修复后提供新 SHA，按受影响范围复验，不自动合并。

## 9. 完成标准与交付清单

**开发候选完成：**三页真实接线、入口和数据含义完整、Android／JVM 回退保留，当前机器的必要自检和画面检查已执行，Apple 缺口明确。

**本批验收完成：**固定候选的必要 Apple 编译、真实画面和关键操作证据齐全，已发现回归修复并复验。缺设备的必需项仍作为验收缺口；3C-1 完成不等于整个 3C 或阶段三完成。

Gemini 交付时提供：

1. 分支、实际基线完整 SHA、候选完整 SHA、PR，注明工作区是否仍有影响候选的未提交差异。
2. 三页前后画面及条件，区分 Android／共享渲染与真实 iOS；Mac 尚未运行时明确缺少 iOS 画面。
3. 第 4 节逐项入口／条件／动作对照，特别是 iOS 轮询不可用、条件抽奖、Telegram、ALERT／BLOCK、未知回复数和数量联动。
4. 实际命令、结果和日志路径；失败与未执行分开列出，不借用其他候选的通过数量。
5. 已知问题、待 Mac 检查项目，以及后续验收所需数据或工具。

显式 stage 本批文件，按逻辑提交；不要提交构建产物、账号资料、密码或本地 SDK 路径。推送 `origin/ios/phase-three-notifications`，创建目标为 `codex/ios-experience-integration` 的 PR，说明实际完成范围并等待评审。

## 10. 可直接下发给 Gemini 的启动指令

> 请先读取 `AGENTS.md` 和 `docs/ios-phase-three-notifications-gemini-handoff.md`，按任务书开发 iOS 3C-1：通知提醒、提醒／屏蔽关键词兼容和追踪帖子管理路径。从最新 `origin/codex/ios-experience-integration` 创建 `ios/phase-three-notifications` 及独立工作区，不沿用 3B 分支。现在在 Windows 完成实现、自检和当前可用画面检查；Mac 仍留给前序验收，等资源交接后再由 Codex 固定候选验收。保留平台能力判断、全部原导航与数据语义、Android／JVM／墨水屏回退；不要把 iOS 不支持的后台轮询画成可用功能。提交并推送完整候选 SHA、基线、日志和入口清单，创建目标为 iOS 集成线的 PR；不自行合并、改版本、出包或开展 3C-2／3C-3。
