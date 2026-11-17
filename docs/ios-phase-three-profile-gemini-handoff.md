# Nodyssey iOS 3B：我的页开发任务书

日期：2026-11-17。交给 Gemini 执行。先完成 3A 的基线检查与当前环境可执行的验收，再开发 3B「我的」页；3C 二级设置另开一批。

## 1. 交付目标：账号、签到和常用入口更容易找到

保留现有 Compose 页面、UIKit 导航和共享业务，在 iOS 上将「我的」页整理成清楚的账号首页：上方是身份、资产和签到，下方是分组入口。减少相互争夺注意力的彩色色块、嵌套卡片和四列宫格，让文字、状态和操作建立层级。

本次交付包括已登录、游客、首次加载、加载失败、后台刷新及宽屏状态。所有现有目的地、条件入口、未读提示和错误恢复都要保留。Android／JVM 保持现有呈现与行为。

用户验收时应能直接完成：查看账号和资产、签到或查看今日签到结果、切换账号、进入收藏／历史／邮件，以及找到其他现有工具。

## 2. 当前基线与已完成的部分

统一集成线：`origin/codex/ios-experience-integration`。编写任务书时最新提交为 `75edf0ee`，三项组合代码为 `3f05536f`。

| 已有内容 | 证据与保留要求 |
| --- | --- |
| 阶段一：平台能力、离线图片、图片网络限制、版本检查与提醒 | 已开发并进入集成线；沿用现有能力入口与更新协议 |
| 阶段二：阅读、菜单、回复／发帖、草稿与发布失败恢复 | 用户测试过合并包；保留导航、草稿和错误恢复 |
| 3A：设置首页、主题、关于 | PR #78；设置原生控件、滑块收窄和滚动层级已修复，Apple 编译和核心操作通过 |
| 阅读底栏对齐 | PR #79；原生玻璃模式使用系统 UIToolbar，保留显隐、返回与内容留白 |
| 首页横屏裁切 | PR #80；高度不足时横向排列，左右横屏和转回竖屏已验证 |

先读 `AGENTS.md`、`docs/ios-phase-three-mac-validation.md`、`docs/ios-home-landscape-validation.md`。已有验证只适用于记录中的候选与范围，不能代替后续改动的检查。

3A 尚缺 iPad／窗口尺寸、iOS 16–18、多语言／大字体、VoiceOver 和其余前两阶段哨兵验收。2026-11-17 收尾时，三个 PR 的完整 GitHub CI 均已成功：#78 为 `44054927487`，#79 为 `44054966930`，#80 为 `44054975209`。这些结果对应各 PR 的候选；3B 新改动仍需执行受影响检查。开工时若有新的失败，读日志区分本轮回归、既有失败和环境失败。

## 3. 分支、工作区和交付权限

开发分支统一为 **`codex/ios-phase-three-profile`**，从上述 iOS 集成线建立。Codex 已在此分支准备任务书；接手已有分支，不重新从旧 dev、设置分支或测试快照建一条同名分支。

| 用途 | 路径 |
| --- | --- |
| Windows 本任务工作区 | `C:/Users/weishi/.codex/worktrees/ios-phase-three-profile/nodyssey` |
| Mac 本任务工作区 | `/Users/a092/Developer/nodyssey-ios-profile` |
| Mac 已有集成线工作区 | `/Users/a092/Developer/nodyssey-ios-integration` |
| Mac 环境与当前运行设备说明 | `~/.nodyssey/MAC_HANDOFF.md` |

开工执行 `git status`、`git branch --show-current`、`git log -5 --oneline`，确认实际目录与分支。Mac 新 shell 先 `source ~/.nodyssey/env.sh`。已有工具链继续使用，不重复安装。

新克隆接手：

```bash
git fetch origin
git switch --track -c codex/ios-phase-three-profile origin/codex/ios-phase-three-profile
```

同名本地分支已存在时先检查工作区，再切换并 `git pull --ff-only`。Mac 上本任务工作区若已准备好，直接进入它；不要在其他功能工作区切换分支。集成线有后续变化时先检查关系，再将其正常 merge 到功能分支，保留本任务书，不 rebase 或 force-push 共享分支。

将本任务书交给 Gemini 作为实施指令时，交付范围为：完成本节限定的开发与验证、按逻辑提交、推送同名功能分支，并创建目标为 **`codex/ios-experience-integration`** 的 PR，供 Codex 审核。PR 合并、进入 dev/main、改版本、发布 IPA／APK 另行处理。

3A 收尾中发现的设置／阅读／首页回归，继续用各自修复分支或新的单一目的修复分支提交到 iOS 集成线；不要夹带进 3B 的页面提交。保留 `.ai_memory/`、安装包、其他人的改动和正在使用的模拟器。

## 4. 第一步：确认 3A 基线与剩余验收

先在当前组合 App 上执行尚未完成且环境可执行的项目，已有同候选的有效记录可以复用：

1. 玻璃开启与关闭、浅色与深色下，设置／主题／关于往返、开关、滑块预览和结束提交正常；滚出视口的原生控件不盖住标题。
2. iPad 及较窄窗口下无双顶栏、返回错位或控件裁切；用实际可用宽度检查，不能仅运行手机横屏替代。
3. 英文、繁中、大字体下选项和数值可读；放不下的分段控件按现有策略回退菜单，不能靠缩字解决。
4. VoiceOver 实际调节开关、滑块和选择器，确认可用状态与设置落盘；原生控件不能与整行重复响应。
5. 旧系统环境可用时执行 iOS 16–18 回退；不具备 runtime／设备时，列明系统、缺少的资源和待执行项目，不宣称这项已通过。
6. 保留阅读楼层跳转、回复开关、草稿取消／保留、横竖屏及返回首页等现有路径；只补未覆盖或受新改动影响的操作。

先解决已复现的基线回归，再开展 3B。缺设备的项目保留为整体验收缺口，不以重复当前系统测试替代；可继续独立的入口盘点和 3B 候选开发，最终验收仍需补齐。此次允许推进 3B 候选，不代表 3A 的全部验收已完成。

输出一份简短的 3A 收尾记录，列出本次补验、修复及真实缺口，链接原验证记录。

## 5. 3B 的布局方向已选定

### B1. 顶栏沿用已有导航体系

复用 3A 使用的自适应原生顶栏与现有根页标题策略，保留设置动作和 `hasAppUpdate` 的可见提示及无障碍含义。游客与登录状态使用一致的位置和入口。

`NativeShell` 隐藏系统导航栏、页面绘制适配顶栏的结构继续保留，避免出现两套标题栏。根页不增加返回按钮，不做滚动折叠大标题，不重写整个导航壳。

核对 `AdaptiveGlassTopBar` 等现有组件的实际参数。若原生 action 当前无法承载更新标记，做范围最小的适配并说明组件限制；不能静默丢掉更新提醒。新增桥接仍沿用现有 availability 与弱符号机制。

### B2. 身份和资产：信息完整，视觉收敛

上方保留头像、昵称、UID／注册时间、等级与等级进度。身份区域仍进入自己的个人主页，账号切换是独立操作，点击切换不能同时打开个人主页。

鸡腿、星辰、等级采用同一块安静的资产区域，通过排版和必要分隔建立关系，降低三个高饱和彩色格子的重量。每个值保留名称及对应入口：

- 鸡腿 → 鸡腿流水。
- 星辰 → 星辰流水；转账仍在该页面。
- 等级 → 账户与成长。

未知值继续表达未知，不能将 `null` 写成零；大数不截掉数位或改成未经产品约定的缩写。英文和大字体放不下时允许换行或改排，保持数值可读。注册年月不完整时沿用现有 UID 回退，不捏造日期。

### B3. 签到：突出当前动作，完成后显示结果

签到放在账号区域之后、内容入口之前。未签到时有明确的签到动作；已签到后呈现今天的结果与记录入口，降低主按钮的视觉重量。

保留 `AttendanceModeDialog`、签到榜以及现有失败恢复。当前一次签到在「我的」页直接完成，不新增必须先进入「账户与成长」的中间步骤。

严格保留 `isAttendanceUnknown`、`isSigningIn`、`attendanceKnown`、`hasSignedInToday` 等状态含义：后台复查已知结果时不退回首次加载；请求中不能重复提交；失败后仍能重试、登录或处理验证挑战。

### B4. 入口使用分组列表，全部保留直达

iPhone 主方案为可滚动的分组列表，每行用图标、名称和必要状态表达用途，减少四列彩色宫格。优先使用已有 `GroupedListItem`／`GroupedRow` 及主题 token；不建立新的全应用组件框架。

分组及顺序建议直接采用：

| 分组 | 入口顺序 |
| --- | --- |
| 我的内容 | 收藏、浏览历史、主题帖、我的评论、我的关注、我的粉丝 |
| 消息与活动 | NodeSeek 邮箱、抽奖助手 |
| 社区 | 推荐阅读、幸运抽奖、管理记录、合作商家、友站、屏蔽、邀请码、关于社区 |
| 设置与支持 | 账号设置、通知提醒、主题外观、关于反馈 |

标题名称优先复用现有资源；确需新增时补齐简中、繁中、英文。每个入口仍一次点击到原目的地，不新增「更多」中转页、默认折叠或必须展开才能看到的工具。邮件和抽奖的未读标记放在各自入口旁，沿用现有数据来源。

分组边界使用背景、间距和分隔表达；一行不再套一张独立卡片。使用足够可读的正文与状态文字，避免把说明和英文缩成宫格小字。

### B5. 游客、加载和失败同样完整

游客页用简洁的账号占位说明和清楚的登录动作，缩减现有欢迎装饰与重复权益卡片。保留账号管理的条件入口、设置入口、游客可用工具、邮件和抽奖提示。

首次登录尚无资料时保留加载和可恢复错误；已有缓存资料的刷新继续显示已有信息。验证挑战继续使用 `onVerify` 的专用验证路径，访问网站继续使用 `onOpenWebsite`，不能合并两个回调。

`accounts == null` 表示此平台没有该能力，沿用隐藏规则，不能绘制一个无效按钮。退出账号或切换账号后不能残留上一账号的资产、签到或未读状态。

### B6. iPad、普通模式和平台边界

iPad 保留账号／签到与目的地两区域的宽屏能力，列表使用合理可读宽度；窄窗口可回到单列。依据当前容器宽度布局，沿用已有尺寸 token 与宽屏规则，不用整机型号判断。

页面适配可使用 `LocalPlazaLayers.current.nativeChrome` 的现有能力边界；它覆盖普通 iOS 原生交互，并保留墨水屏回退。不要只用 `nativeGlass` 判断是否走 iOS 方案。玻璃关闭、旧系统和墨水屏分别检查各自既有策略。

共享 Route、ViewModel、`ProfileDestinations` 和弹窗状态继续复用。把 iOS 呈现限制在明确分支或局部组件中，Android／JVM 保留现有账号卡、宫格、游客页、入口和交互；不要复制整套业务状态或全局修改主题 token。

## 6. 逐项核对现有入口和合同

实现前后各核对一次下表，记录入口所在区域、显示条件和原目的地。以实际代码为准，下面是编写任务书时已核实的清单。

| 对象 | 需要保留 |
| --- | --- |
| 顶栏 | `settings`，登录／游客均可达，保留应用更新提示 |
| 账号身份与资产 | `space`、`credit`、`stardust`、`assets`、可空的 `accounts` |
| 我的内容 | `topics`、`comments`、`collections`、`history`、`following`、`followers` |
| 消息与活动 | `mail`、`lotteryAssistant`，各自未读数据和无障碍说明 |
| 社区 | `award`、`lucky`、`ruling`、`providers`、`friends`、`blockList`、`invite`、`aboutCommunity` |
| 设置与支持 | `accountSettings`、`notificationSettings`、`themeSettings`、`about` |
| 游客工具 | 推荐阅读、管理记录、抽奖助手、幸运抽奖、合作商家、`tools`、邮件；社区工具内的原有入口继续可达 |
| 游客登录 | `onSignIn`，可用时保留账号管理入口 |
| 签到与失败恢复 | 模式选择、签到请求、签到榜加载／重试、失败消费、验证／登录／重试动作 |

合作商家和友站仍走原网站 WebView；不能创建空壳原生页替换。收藏中的离线管理、编辑器自动恢复的单份草稿沿用原路径，不新增不存在的「下载中心」或「草稿箱」入口。

## 7. 直接代码入口和允许范围

以下路径相对仓库根目录，先读完整相关函数和调用者，再改代码：

| 文件 | 用途 |
| --- | --- |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/profile/ProfileScreen.kt` | Route、生命周期刷新、登录／游客、身份资产、签到与现有入口布局 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/profile/ProfileDestinations.kt` | 全部导航回调与账号能力条件 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/profile/ProfileViewModel.kt` | 资料、签到、账号切换、邮件和抽奖未读状态；原则上保留业务实现 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/TabEntries.kt` | ProfileKey 的真实目的地、WebView 和验证接线 |
| `ui/src/commonMain/kotlin/io/github/nodyssey/ui/settings/SettingsListItems.kt` | 3A 的页面分组与适配用法；按模块边界复用适合的部分 |
| `designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/AdaptiveGlass.kt` | 已有自适应顶栏、按钮、菜单和提示 API |
| `designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/GroupedListItem.kt` | 已有分组行 |
| `designsys/src/commonMain/kotlin/io/github/plaza/designsys/theme/GlassRenderer.kt` | nativeChrome／nativeGlass 能力含义 |
| `ui/src/iosMain/kotlin/io/github/nodyssey/NativeShell.kt` | UIKit 导航与工具栏；仅必要相邻适配，不重写壳 |
| `ui/src/androidHostTest/kotlin/io/github/nodyssey/ui/profile/ProfileScreenTest.kt` | 签到分支、错误恢复与前台刷新回归 |
| `ui/src/androidHostTest/kotlin/io/github/nodyssey/ui/profile/ProfileViewModelTest.kt` | 缓存刷新、失效会话、签到及邮件未读回滚／账号隔离 |
| `ui/src/androidHostTest/kotlin/io/github/nodyssey/render/ProfileScreenRenderTest.kt` | Android／共享布局的既有渲染入口 |

允许为本页面新增小范围的 `ui/profile` 组件、真实需要的 iOS actual，以及必要资源和逻辑回归。站点相关内容不进入 `:designsys`。依赖固定版本，先查本仓库已解析源码和实际组件 API。

本批不改签到策略、资产计算、会话存储、邮件算法、网络配置、数据库、更新协议或版本号；不开展 3C、分享扩展、后台推送、诊断、崩溃采集、全应用图标替换或全局性能工程。由本批引起的回归应在本批修复。

## 8. 开发顺序与检查

### M0：基线与原貌

核对分支与 CI，按第 4 节补验 3A。记录改造前的真实 iOS 已登录／游客画面及 Android 对照，列出第 6 节入口位置与条件。无登录资料时先用仓库外的合成数据驱动画面，不索取 Cookie 或修改生产账号状态。

### M1：完成一条可用的 iOS 我的页

按 B1–B6 完成账号、资产、签到与分组入口，随后完成游客和加载／错误状态。先做可运行页面和真实接线，保留 Android 分支；不交付空组件或静态假状态页面。

### M2：编译、回归与查看实际效果

共享检查：

```bash
./gradlew spotlessCheck :ui:compileKotlinJvm
./gradlew :ui:testAndroidHostTest --tests '*ProfileScreenTest' --tests '*ProfileViewModelTest'
./gradlew :ui:testAndroidHostTest -PrenderUi --tests '*ProfileScreenRenderTest'
git diff --check
```

渲染位于 `ui/build/outputs/renders/`，实际打开检查 Android 对照。渲染只是查看工具，不新增像素、颜色、尺寸、间距、文案或截图金图断言；新测试只覆盖真实的行为分支和逻辑错误，不写回调透传测试。

Mac 检查：

```bash
source ~/.nodyssey/env.sh
./gradlew :iosapp:compileKotlinIosArm64 :iosapp:compileKotlinIosSimulatorArm64
bash .github/scripts/build-ios-simulator.sh "build/ios-profile-$(date +%Y%m%d-%H%M%S)"
```

使用新输出目录，避免已有 App 被嵌套复制。新增或修改 UIKit host 时跑受影响原生测试；用仓库脚本启动专用测试设备，不关闭用户正在观看的 `Nodyssey 3A Validation`。其他 CI 门禁按 `AGENTS.md` 和 `.github/workflows/ci.yml` 执行，现有同候选的有效证据不重复跑；结束停止本轮 Gradle daemons。

### M3：实际操作和提交评审

在真实模拟器里完成下表，检查画面和可操作性；共享渲染不能作为 iOS 验收。

| 场景 | 验收动作 |
| --- | --- |
| 已登录／游客 | 设置、登录和所有当时可用入口可达；账号切换条件正确；没有装饰性假按钮 |
| 账号与资产 | 身份及三项资产进入原页面；大数、长昵称、缺失值可读；切换后不残留上一账号数据 |
| 签到 | 未签、检查中、请求中、已签、失败均正确；取消选择不请求；完成后返回不重新打开选择器 |
| 错误恢复 | 验证挑战、需要登录、网络失败分别提供原恢复动作；错误提示不被底栏盖住 |
| 未读与更新 | 邮件、抽奖和更新提示仍对应原入口；刷新和切换账号后正确更新 |
| 导航 | 每个入口打开并返回；浏览历史等下方入口可滚动到达，返回不意外重置页面；设置侧滑完成／取消正常 |
| 横竖屏与宽屏 | iPhone 左右横屏、转回竖屏、iPad 宽窄窗口；不裁切，不出现双顶栏或重复底部留白 |
| 主题与语言 | 浅／深色、玻璃开／关、墨水屏回退；简中／繁中／英文及较大字体 |
| 辅助功能 | VoiceOver 的阅读顺序、名称、当前状态与操作清楚，独立操作不重复触发 |
| 前两阶段哨兵 | 首页进入帖子、楼层跳转、回复打开关闭、草稿取消／恢复、返回首页；本次未改动的有效证据可复用 |

需要签到、发布、转账、购买、切换真实账号等有状态操作时，优先使用合成数据、现有测试 fixture 或专用测试账号。实际对外写入和真实账户操作遵循用户给该操作的授权，截图与日志不提交认证数据。横屏截图使用 `XCUIScreen.main.screenshot()`，避免此前应用截图产生的错误裁切。

## 9. 完成标准和交付内容

3B 完成需要：真实页面接线、完整入口清单、Android 回退保留、必要编译与回归通过、实际 iOS 画面和关键操作已检查。缺少必要证据时交付候选并写明缺口，不把整个阶段三写成完成。

交付时提供：

1. 功能分支、基线、提交与 PR；说明实际完成的 B1–B6。
2. 改造前后的 iOS 已登录／游客截图，至少一个宽屏画面，及 Android 对照；附系统、设备和主题条件。输出放仓库外，不提交构建产物或认证数据。
3. 逐项入口清单及检查结论，特别列出可空 accounts、游客 tools、邮件／抽奖未读和更新提示。
4. 实际执行命令、结果和日志路径；给出可运行模拟器 App 的路径及来源提交，有工作区 diff 时明确写出。
5. 3A 收尾记录与尚缺设备的验收条件，3B 的真实失败／缺口及下一步。3C 尚未开发，另开任务。

提交按逻辑拆分，例如 iOS 已登录布局、游客与边界状态、必要相邻修复；测试和资源随对应功能提交。显式 stage 本次文件，推送到 `origin/codex/ios-phase-three-profile`，PR base 为 `codex/ios-experience-integration`，不自行合并或出测试包。

## 10. 可直接交给 Gemini 的指令

> 请按 `docs/ios-phase-three-profile-gemini-handoff.md` 执行 Nodyssey iOS 3B。使用已准备的 `codex/ios-phase-three-profile` 分支，先检查 3A 基线并补验当前环境能执行的项目，修复已复现回归，缺设备的项目准确保留。随后完成「我的」页的 iOS 账号／资产、签到、分组入口、游客及边界状态，逐项保留原目的地、显示条件和恢复路径，Android／JVM 保持原状。Mac 环境已就绪，先读 `~/.nodyssey/MAC_HANDOFF.md`；实际编译、运行并查看 iOS 画面，不能只交共享截图。按任务书开发、验证、提交，推送同名功能分支并提交面向 `codex/ios-experience-integration` 的 PR，交给 Codex 审核；不要合并、发布或开展 3C。
