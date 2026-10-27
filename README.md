<div align="center">

> **Nodyssey 非官方分支**：[下载本分支](https://github.com/weishiair/nodyssey/releases)。基于 [5151561/nodyssey](https://github.com/5151561/nodyssey)，保留原项目署名与 GPL-3.0 许可证。从 1.3.0 起使用简洁版本号，旧 ECH 版本可直接覆盖升级。
> 内置 `https://resolver.weishiair.de/dns-query`，Android 8 及以上支持 ECH；应用内更新仅跟随本分支。详细说明见 [分支说明](docs/ech-fork.md)。

# Nodyssey

[NodeSeek](https://www.nodeseek.com/) 的非官方开源 Android 客户端，Kotlin + Jetpack Compose，帖子渲染为原生 Compose 内容。

[![Release](https://img.shields.io/github/v/release/weishiair/nodyssey?label=Release&color=2C6E7F)](https://github.com/weishiair/nodyssey/releases)
[![Downloads](https://img.shields.io/github/downloads/weishiair/nodyssey/total?label=Downloads&color=2C6E7F)](https://github.com/weishiair/nodyssey/releases)
[![Stars](https://img.shields.io/github/stars/weishiair/nodyssey?style=flat&logo=github&label=Stars)](https://github.com/weishiair/nodyssey/stargazers)
[![CI](https://img.shields.io/github/actions/workflow/status/weishiair/nodyssey/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white)](https://github.com/weishiair/nodyssey/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/weishiair/nodyssey?label=License)](LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-交流群-26A5E4?logo=telegram&logoColor=white)](https://t.me/+JGCIMJmPrj0wZGVh)

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/weishiair/nodyssey/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-M3_Expressive-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)

[![Website](https://img.shields.io/badge/宣传页-nodyssey-2C6E7F?logo=githubpages&logoColor=white)](https://weishiair.github.io/nodyssey/)

<img src="docs/screenshots/post-list-light.png" width="200" alt="帖子列表" />
<img src="docs/screenshots/post-detail-dark.png" width="200" alt="帖子详情" />
<img src="docs/screenshots/notifications-light.png" width="200" alt="消息通知" />
<img src="docs/screenshots/profile-light.png" width="200" alt="我的" />

</div>

## 下载

到 [Releases](https://github.com/weishiair/nodyssey/releases) 下载最新 APK，应用内也可以检查更新。各版本变化见 [CHANGELOG.md](CHANGELOG.md)。

> **关于 iOS**：本分支同时提供未签名 IPA，需要自行签名后安装；以 Android 优化为主，iOS 功能和网络支持可能不同，尚未进行真机验证。

## 功能

- **浏览与互动**：发帖、评论、编辑；点赞 / 反对 / 投喂鸡腿、收藏、签到；投票帖阅读与投票
- **消息与社交**：通知与未读数、私信、关注 / 粉丝、用户空间
- **账号**：鸡腿与星辰流水、星辰转账与收款码、等级进度、账号设置
- **阅读体验**：离线缓存、已读标记、浏览历史与阅读位置、图片预览 / 保存 / 分享、六种图床上传
- **帖子排版**：通栏与轻卡片可选，默认轻卡片；保留签名档，作者等级与注册天数默认显示，可在「设置 → 内容」关闭
- **界面**：Material 3 Expressive，宽屏自动换侧边栏；简体 / 繁體 / English 三语
- **默认配色**：浅色模式使用纯白，深色模式使用中性黑灰，引用块和悬浮控件分层显示；已有的自选配色继续保留

## 构建

需要 JDK 21 和 Android SDK 37。

```bash
./gradlew :app:assembleDebug
```

模块划分和架构约定见 [docs/architecture.md](docs/architecture.md) 与 [docs/README.md](docs/README.md)。

## 参与

- 交流群：[Telegram](https://t.me/+JGCIMJmPrj0wZGVh)，交流 Nodyssey 使用体验、反馈问题与讨论更新；App 内可从「设置 → 关于 Nodyssey → Telegram 交流群」进入。
- 反馈问题：[Issues](https://github.com/weishiair/nodyssey/issues/new/choose)，请带上 App 版本、机型和 Android 版本
- 使用帮助：应用内「设置 → 关于 Nodyssey → 使用帮助」；仍有疑问可在本分支 Issues 提问。
- 提 PR 前请先读 [CONTRIBUTING.md](CONTRIBUTING.md)

## 致谢

- [5151561/nodyssey](https://github.com/5151561/nodyssey)（GPL-3.0）—— 本分支基于此项目开发，保留原项目署名与许可证。
- [tyrad/nodeseek](https://github.com/tyrad/nodeseek)（iOS 客户端，MIT）—— 站点结构的逆向参考
- [mrzhiin/seekmate](https://github.com/mrzhiin/seekmate)（React Native 客户端，MIT）—— 部分 JSON 端点的发现来源
- [Woodll / Nodeseek Pro](https://greasyfork.org/zh-CN/scripts/567109-nodeseek-pro)（GPL-3.0）—— 作者等级与注册天数标签的灵感和等级渐变配色参考。

本分支继承原项目的实现，并在 GPL-3.0 许可下继续改进。

## 说明

本项目与 NodeSeek 官方无关，是社区自制客户端。请求频率保持克制，不做自动化刷分；仓库中不保存任何 Cookie 或凭据。

## 许可

[GPL-3.0](LICENSE)
