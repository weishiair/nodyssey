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

服务基于 [edge-smart-doh](https://github.com/1molchuan/edge-smart-doh)，提供优选 Cloudflare 地址并补充 ECH 配置；还需要客户端支持 ECH，且目标地址在当前网络中可达。登录 WebView 使用独立的网络栈，不使用 App 的 DoH/ECH；Android 的网页登录、人机验证和登录弹窗会使用已保存的 App 内代理。无认证代理直接通过 AndroidX `ProxyController` 配置；带认证的 HTTP/SOCKS5 通过仅监听本机的 CONNECT 适配层转发，目标域名由代理解析，HTTPS 证书校验仍由 WebView 完成。关闭内置代理后恢复系统网络设置，外部浏览器不受影响。

原生登录收到成功响应后，会等系统 Cookie 写入回调再检查登录状态。如果仍没有可读会话，登录页提供脱敏诊断，包含响应中的会话 Cookie 名称、最近写入结果和 WebView 版本，不含账号、密码或 Cookie 值。普通网络自检的 HTTP 200 只能说明探测请求成功，不能说明登录响应已经发出或保存了会话 Cookie。

密码登录或两步验证返回 `success` 并跳转邮箱验证页时，原生登录会继续到邮箱验证码表单；这一步不再被当成已经登录，也不会误报 Cookie 保存失败。密码页和两步验证页也提供独立的邮箱登录入口。发码使用站点的 `/api/email`（`mode=totp`），邮箱验证码提交到 `/api/account/emailSignIn`；支持发码冷却、验证失败重试，以及使用已经从网页收到的验证码。

## 分支更新

应用只从 `https://weishiair.github.io/nodyssey/` 的静态清单获取更新和更新日志，下载地址仅允许 `weishiair/nodyssey` 的 GitHub Release，下载后验证 SHA-256。

升级到此功能的首版时，旧上游更新检查缓存会失效，避免继续提醒安装官方版。旧版 `1.2.22-ech.5` 等 ECH 修订版仍按数字比较；`1.3.0` 会正常替换旧正式版及测试版。正式版 `1.3.6` 使用 Android versionCode 113，先前测试构建已分配至 112；下一次测试使用 `1.3.7-test.1`（114）。后续测试递增 `test.N` 和 versionCode，正式版使用 `1.3.6` 这样的格式；旧 `-dev.N` 版本继续兼容。

新版本发布包含签名 APK 的 `v1.3.6` GitHub Release，APK 使用 `nodyssey-v1.3.6.apk` 格式。发布工作流兼容新命名和旧 `ech-*` 历史，生成并发布本分支的 `updates` 清单；不读取上游 Release。GitHub Pages 使用 `updates` 分支根目录，需在仓库设置中保持启用。

## Android 打包约定

日常测试同样交付 Release APK，开启 R8 与资源压缩，只包含 `arm64-v8a` 和 `armeabi-v7a`，不包含 LeakCanary，体积通常约 10–12 MB。版本号统一取自 `gradle.properties`，不要沿用已发布的正式版号或仅改 APK 文件名。

```powershell
.\gradlew.bat :app:assembleRelease '-Pnodyssey.echRelease=true'
```

签名通过 `NODYSSEY_KEYSTORE_FILE`、`NODYSSEY_KEYSTORE_PASSWORD`、`NODYSSEY_KEY_ALIAS`、`NODYSSEY_KEY_PASSWORD` 注入，沿用本分支既有证书和 `io.github.nodyssey.debug` 包名，以便覆盖安装并保留数据。缺少密钥时上述命令生成未签名包，不能作为可安装包交付，也不自动改成临时包名或其他签名。

交付前用 SDK 的 `aapt2 dump badging` 与 `apksigner verify --print-certs` 核对版本号、versionCode、包名、ARM 架构、非调试状态和签名，再核对大小；文件名使用 `nodyssey-v1.3.7-test.1.apk` 这样的格式。构建后执行 `.\gradlew.bat --stop`。正式发布才创建 `v1.3.6`；测试发布创建 `v1.3.7-test.1` 并标为 prerelease。

本机 Windows 签名库固定存放在用户目录的 `.nodyssey/signing/release.keystore`，与仓库、桌面临时文件分开。四个签名变量保存在当前 Windows 用户的环境中。已经运行的终端或 AI 进程需要先加载用户环境，再执行打包命令：

```powershell
. "$env:USERPROFILE\.nodyssey\signing\load-signing.ps1"
.\gradlew.bat :app:assembleRelease '-Pnodyssey.echRelease=true'
```

既有发布证书 SHA-256 为 `71218866231eae5bf61a1192b2df0180d6d65d55e6aa08f0e52176dbcad197f8`。证书显示名称为 `Android Debug`，辨认时以指纹为准。签名库和密码应另外保存到受保护的备份位置，不能提交到 Git。
