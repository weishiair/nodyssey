# Nodyssey iOS：Mac Codex 独立验收任务书

更新：2026-11-17。Gemini 负责开发、自检和修复，Mac 上的 Codex 负责独立审核与平台验收。先验收 3A 当前基线，收到 Gemini 的明确候选提交后再验收 3B。

## 1. 当前验收对象和交接入口

开发要求与完整入口清单见 [3B 我的页开发任务书](ios-phase-three-profile-gemini-handoff.md)。3A 既有证据见 [Mac 验证记录](ios-phase-three-mac-validation.md) 和 [首页横屏记录](ios-home-landscape-validation.md)。

| 对象 | 本次输入 |
| --- | --- |
| 3A 基线 | `323b7dc96be49ed72fadb281afde784772726b45`，来自 `origin/codex/ios-experience-integration` |
| 基线的新变化 | 在 `75edf0ee` 后追加设置开关右边缘裁切及滑块宽度修复；旧 CI／截图不自动覆盖这笔改动 |
| 3B 功能分支 | `origin/codex/ios-phase-three-profile`，等待 Gemini 交付实现、自检结果、PR 和完整候选 SHA |
| 集成目标 | `codex/ios-experience-integration`，此次不进入 dev/main，不发布安装包 |

当前尚未收到 3B 实现提交，因此现在执行 3A 验收即可；不要将任务书提交当作 3B 功能完成，也不持续追随功能分支的 HEAD 运行测试。3B 到达时由用户下发候选 SHA／PR，再开始该候选的验收。

## 2. 独立目录、固定提交和资源使用

- Gemini 开发目录：`/Users/a092/Developer/nodyssey-ios-profile`，保留其分支、文件、构建和正在进行的操作。
- Mac Codex 的 3A 验收目录：`/Users/a092/Developer/nodyssey-ios-acceptance`，detached HEAD 固定到上面的 3A SHA。
- 3B 验收使用新的独立 worktree，固定到 Gemini 交付的完整 SHA；不要切换 Gemini 的目录或在测试中更新候选代码。
- 本任务书位于开发目录的 `docs/ios-mac-codex-acceptance.md`。3A 旧基线中没有这份新文档，先从开发目录读取，再进入验收目录工作。
- 先读 `AGENTS.md`、`~/.nodyssey/MAC_HANDOFF.md`，新 shell 执行 `source ~/.nodyssey/env.sh`。工具链已就绪，不重复安装，不改变全局 xcode-select。

开始时记录 `git status --short`、`git rev-parse HEAD`、候选基线、diff 范围和系统／设备。验收源码必须与记录的提交一致；临时驱动、合成数据、日志和图片放在仓库外。若必须修改生产源码才能复现或继续，先提交发现，由 Gemini 修复并交付新候选。

使用自己的验收模拟器，名称可为 `Nodyssey Acceptance`。从本机现有 runtime 和设备类型中选择，不硬编码不存在的系统。保留用户正在观看的 `Nodyssey 3A Validation` 与 Device Hub 窗口，不在该设备上安装、旋转、切主题或执行测试。

同一验收模拟器一次只有一个操作者。与 Gemini 在同一 Mac 上运行时，避免同时进行重型 Native 链接；只等待或协调相关构建，不终止其他人的进程。构建结束按仓库要求停止本轮 Gradle daemons，不干扰仍在运行的他人任务。

## 3. 第一段：先完成 3A 的剩余验收

核对固定候选已包含 `323b7dc9`。先审阅新增设置修复及其直接调用和测试，再补齐以下证据：

1. 开关右边缘完整，行内触控和滚动正常；滑块宽度、居中、预览和结束提交正常，原生控件不残留在固定标题上。
2. 设置／主题／关于在普通模式、玻璃模式、浅／深色下的导航和控件操作正常；墨水屏按已有回退工作。
3. iPad 与较窄窗口的顶栏、安全区、控件和菜单锚点正确，不能只用 iPhone 横屏代替 iPad 验收。
4. 简中／繁中／英文、较大字体下关键选项和数值可读；分段控件放不下时使用现有菜单回退。
5. VoiceOver 实际操作开关、滑块和选择器，检查状态读出、重复响应和落盘。只有语义树检查不等于实际 VoiceOver 验收。
6. 本机具备 iOS 16–18 环境时检查旧系统回退；没有相应 runtime／设备则记录未执行项及所缺资源。
7. 按受影响范围补阅读工具栏、横屏首页、回复／草稿、离线图片与更新入口的哨兵检查，复用同候选仍有效的旧证据。

将新增基线修复的编译、原生逻辑测试和实际控件画面作为优先项。已有 469 项 macOS、54 项 UIKit、88 项阅读／导航及 25 项首页测试记录各有适用范围；查记录后复用，不能把旧数量当作新候选的全量通过结果。

3A 发现的可复现回归交给 Gemini 或原修复负责人，给出文件、重现步骤、预期／实际结果和证据。缺设备项目保留为整体验收缺口；不以当前系统重复运行替代旧系统、真机或辅助功能证据。

## 4. 第二段：收到候选后验收 3B

Gemini 需交付：完整候选 SHA、基线 SHA、PR、B1–B6 实施说明、完整入口清单、实际自检命令及日志、已知问题。缺少候选 SHA 时，请用户提供；这只影响 3B 验收，3A 与已有资料审核可以继续。

按候选建立独立 worktree，记录基线差异。审核代码时重点核对：

- iOS 顶栏、身份／资产、签到、分组列表和游客路径是否真实接入；Android／JVM 和墨水屏回退是否保留。
- `ProfileDestinations` 的全部目的地、`accounts == null`、游客工具、未读和更新提示是否仍符合原合同。
- 后台刷新、账号切换、资料缓存、签到请求中／已签／失败、验证／登录／重试是否保持原含义。
- 新 API 是否来自固定版本；原生事件生命周期、回调更新和释放是否正确；是否越界修改业务、存储或网络。

然后执行开发任务书第 8 节 M3 的实际操作表，并独立查看 iOS 已登录、游客、横屏／宽屏、主题与语言代表画面。对照第 5 节 B1–B6 评估最终页面：账号与签到是否容易发现、资产是否可读、下方入口是否容易找到、分组和状态是否清楚。源码正确和测试通过不能代替实际画面。

关键分支使用既有 fixture、专用测试账号或仓库外的合成数据。注明合成画面的条件；它不能证明真实登录、网络请求或持久化已经通过。外部写入和真实账户操作沿用用户对该操作的授权。

## 5. 编译、测试和截图

首先核对 Gemini／CI 的证据是否对应同一候选和输入。有效共享检查可复用；执行仍缺少、被新变化影响或发现反证的必要检查。

平台入口：

```bash
source ~/.nodyssey/env.sh
./gradlew :iosapp:compileKotlinIosArm64 :iosapp:compileKotlinIosSimulatorArm64
```

根据修改范围执行原生控件测试。仓库入口为 `.github/scripts/boot-ios-test-simulator.sh` 和 `:designsys:iosSimulatorArm64Test`、`:ui:iosSimulatorArm64Test`，通过 `-Pnodyssey.iosTestDevice=<本轮测试设备UDID>` 指定自己的设备；先读脚本的设备选择逻辑，避免复用用户正在操作的设备。测试结束只关闭自己启动且不再使用的测试设备。

模拟器 App 使用已有脚本和新的输出目录构建：

```bash
bash .github/scripts/build-ios-simulator.sh "build/ios-acceptance-$(date +%Y%m%d-%H%M%S)"
```

安装到自己的设备并实际启动，检查的是这份固定候选生成的 App，不能沿用未知来源的已安装 App。记录构建目录、候选 SHA、版本、SDK 和模拟器条件。

实际操作可复用既有 XCTest 驱动或创建仓库外的临时驱动。横屏截图使用 `XCUIScreen.main.screenshot()`，实际打开完整屏幕图检查。自动化退出码、AX 树和截图各自提供不同证据；没有查看图片就不写视觉验收通过。

新增测试仍遵守仓库规则：不写尺寸、颜色、间距、文案或截图金图断言；不增加回调透传测试。必要 CI 门禁沿用现有工作流，按候选记录结果。权限或工具不可用时保留对应项目及影响，不绕过权限或把未执行写成成功。

## 6. 报告与修复闭环

输出到 `~/.nodyssey/ios-acceptance/<3a或3b>/<完整候选SHA>/`，包含 `report.md`、日志、实际截图及操作结果。报告至少包含：

| 内容 | 要求 |
| --- | --- |
| 验收对象 | 候选／基线完整 SHA、工作区、PR、实际 App 路径、系统与设备 |
| 覆盖情况 | 按 3A 项目或 B1–B6 与操作表列出通过、失败、未执行及依据 |
| 发现的问题 | 严重程度、文件与行号、可复现步骤、预期／实际结果、日志或截图 |
| 验收缺口 | 缺什么设备、权限、账号或工具，影响哪一项结论，谁补齐及恢复条件 |
| 修复流转 | 将问题清单交给用户，由用户转给 Gemini；不擅自跨聊天发消息 |
| 结论 | 可提交集成评审、需修复后复验、或证据不足；整体阶段三仍含未开展的 3C |

默认只写仓库外报告和临时验收驱动，不修改应用源码、合并 PR、推送功能分支或发布安装包。修复由 Gemini 交付新 SHA；Mac Codex 核对修复差异后，只复验受影响项目和相邻关键路径，旧证据仍有效的部分继续引用。

最终由用户确认 iPhone 上的实际手感与整体体验；Codex 的模拟器结果和用户真机体验分别记录。

## 7. 可直接下发给 Mac Codex 的指令

> 请先读取 `/Users/a092/Developer/nodyssey-ios-profile/docs/ios-mac-codex-acceptance.md`、相关开发任务书、`AGENTS.md` 和 `~/.nodyssey/MAC_HANDOFF.md`。你负责独立验收，Gemini 负责开发与修复。先在 `/Users/a092/Developer/nodyssey-ios-acceptance` 验收固定的 3A 提交 `323b7dc96be49ed72fadb281afde784772726b45`，补齐当前环境可执行的项目并记录真实缺口，输出仓库外报告。使用自己的模拟器，保留用户的 Validation 设备，不修改 Gemini 的工作区或应用源码。3B 尚无实现候选，收到用户下发的 Gemini 完整 SHA／PR 后，再按同一任务书使用独立 worktree 验收；不要自动追随分支或自行合并、推送、发布。测试任务优先使用 `gpt-6.1-terra / xhigh`，不能实际切换时说明当前模型并请用户选择，不能假称已切换。
