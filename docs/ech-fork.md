# Nodyssey 非官方分支

基于 [Nodyssey](https://github.com/5151561/nodyssey) 的非官方 Android 分支，用于在支持的网络中配合 DoH 与 ECH 直连 NodeSeek。协议继续使用 GPL-3.0，感谢上游作者。

- 下载：[weishiair/nodyssey Releases](https://github.com/weishiair/nodyssey/releases)。
- 交流与反馈：[Telegram 交流群](https://t.me/+JGCIMJmPrj0wZGVh)，也可从 App「设置 → 关于 Nodyssey → Telegram 交流群」加入。
- 从 1.3.0 起，应用名统一为 `Nodyssey`，版本号使用 `1.3.0` 这样的常规格式，不再附加 ECH；ECH 继续作为加密 DNS 与 TLS 功能提供。
- 包名 `io.github.nodyssey.debug`，可与官方版共存。沿用旧 ECH 版本的同一包名和签名，可直接覆盖升级并保留数据。
- Android 8–16 使用内置 Conscrypt，Android 17 及以上使用系统 ECH。
- 提供纯白及清爽蓝、薄荷绿、暖米色、柔雾紫主题，纯白为默认配色，深色模式对应中性黑灰；帖子支持通栏、轻卡片与官方原版，默认轻卡片，引用块和悬浮控件单独分层，保留签名档与连续阅读。已有的自选配色继续保留。Release 包不包含 LeakCanary。
- 「设置 → 内容」可切换回复样式、关闭作者等级与注册天数；账号信息默认显示，读取时有加载动画，关闭后不再补查作者资料。

## 内置 DoH

`https://resolver.weishiair.de/dns-query` 在加密 DNS 列表中显示为 **Weishiair DoH（ECH）**。此预设只使用域名，不内置或展示固定服务器 IP；旧版本为此预设保存的引导 IP 覆盖也不再使用，以便连接跟随域名解析的变化。

从 `1.2.22-ech.7` 起，内置预设使用新的 resolver 域名，避开原域名在部分直连网络上的 TLS 重置。已选择此预设的用户升级后自动使用新入口，自定义 DoH 地址保持原设置。

首次启用加密 DNS 时默认选择此服务。覆盖安装会保留已保存的开关、服务器选择和排序，新服务补入列表后不自动改动选择。

服务基于 [edge-smart-doh](https://github.com/1molchuan/edge-smart-doh)，提供优选 Cloudflare 地址并补充 ECH 配置；还需要客户端支持 ECH，且目标地址在当前网络中可达。登录 WebView 使用独立的网络栈。

## 分支更新

应用只从 `https://weishiair.github.io/nodyssey/` 的静态清单获取更新和更新日志，下载地址仅允许 `weishiair/nodyssey` 的 GitHub Release，下载后验证 SHA-256。

升级到此功能的首版时，旧上游更新检查缓存会失效，避免继续提醒安装官方版。旧版 `1.2.22-ech.5` 等 ECH 修订版仍按数字比较；`1.3.0` 会正常替换旧正式版及测试版。测试版使用 `1.3.3-dev.1` 这样的格式，正式版使用 `1.3.2` 这样的格式。

新版本发布包含签名 APK 的 `v1.3.2` GitHub Release，APK 使用 `nodyssey-v1.3.2.apk` 格式。发布工作流兼容新命名和旧 `ech-*` 历史，生成并发布本分支的 `updates` 清单；不读取上游 Release。GitHub Pages 使用 `updates` 分支根目录，需在仓库设置中保持启用。
