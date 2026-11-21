# iOS 图标 → SF Symbols 对照

PR-9 / PR-10 用。映射表本体在 `designsys/src/commonMain/kotlin/io/github/plaza/designsys/component/SfSymbols.kt`，本文是它的盘点依据。

## 盘点方法

```bash
rg -o --no-filename "(Icons\.(AutoMirrored\.)?(Default|Filled|Outlined|Rounded)\.[A-Za-z0-9]+|PlazaIcons\.[A-Za-z0-9]+|NodeSeekIcons\.[A-Za-z0-9]+)" \
  ui/src/commonMain designsys/src/commonMain | sort | uniq -c | sort -rn
```

- 引用写法 137 种，对应 `ImageVector.name` 135 个；有 SF Symbol 的 131 种，保留原图标的 6 种。
- `Icons.Default.X` 与 `Icons.Filled.X` 是同一个图标（`name` 都是 `"Filled.X"`）。
- 全部 SF Symbol 名已在 macOS 26 上用 `NSImage(systemSymbolName:)` 核对存在；iOS 上由 PR-9 的模拟器测试逐个核对。最低支持 iOS 16，个别符号在旧系统上不存在时运行时退回原图标。

## 对照表

| 引用 | 次数 | `ImageVector.name` | SF Symbol |
|---|---|---|---|
| `Icons.Default.Close` | 28 | `Filled.Close` | `xmark` |
| `Icons.Default.Check` | 18 | `Filled.Check` | `checkmark` |
| `PlazaIcons.OpenInNew` | 14 | `OpenInNew` | `safari` |
| `PlazaIcons.ErrorCircle` | 14 | `ErrorCircle` | `exclamationmark.circle` |
| `Icons.AutoMirrored.Filled.ArrowBack` | 14 | `AutoMirrored.Filled.ArrowBack` | `chevron.backward` |
| `Icons.Default.Add` | 13 | `Filled.Add` | `plus` |
| `PlazaIcons.Link` | 12 | `Link` | `link` |
| `PlazaIcons.ContentCopy` | 12 | `ContentCopy` | `doc.on.doc` |
| `Icons.Default.Lock` | 12 | `Filled.Lock` | `lock.fill` |
| `Icons.Default.Info` | 11 | `Filled.Info` | `info.circle.fill` |
| `Icons.Default.Delete` | 11 | `Filled.Delete` | `trash` |
| `Icons.Default.Edit` | 10 | `Filled.Edit` | `pencil` |
| `PlazaIcons.Group` | 9 | `Group` | `person.2` |
| `PlazaIcons.Download` | 9 | `Download` | `square.and.arrow.down` |
| `Icons.Default.KeyboardArrowDown` | 9 | `Filled.KeyboardArrowDown` | `chevron.down` |
| `Icons.Default.Email` | 9 | `Filled.Email` | `envelope.fill` |
| `Icons.Default.CheckCircle` | 9 | `Filled.CheckCircle` | `checkmark.circle.fill` |
| `PlazaIcons.Image` | 8 | `Image` | `photo` |
| `Icons.Default.Refresh` | 8 | `Filled.Refresh` | `arrow.clockwise` |
| `Icons.Default.MoreVert` | 8 | `Filled.MoreVert` | `ellipsis` |
| `Icons.AutoMirrored.Filled.KeyboardArrowRight` | 8 | `AutoMirrored.Filled.KeyboardArrowRight` | `chevron.forward` |
| `PlazaIcons.Schedule` | 7 | `Schedule` | `clock` |
| `PlazaIcons.PushPin` | 7 | `PushPin` | `pin` |
| `PlazaIcons.History` | 7 | `History` | `clock.arrow.circlepath` |
| `PlazaIcons.Campaign` | 7 | `Campaign` | `megaphone` |
| `Icons.Default.Warning` | 7 | `Filled.Warning` | `exclamationmark.triangle.fill` |
| `Icons.AutoMirrored.Filled.Send` | 7 | `AutoMirrored.Filled.Send` | `paperplane` |
| `PlazaIcons.VisibilityOff` | 6 | `VisibilityOff` | `eye.slash` |
| `PlazaIcons.Visibility` | 6 | `Visibility` | `eye` |
| `PlazaIcons.VerticalAlignTop` | 6 | `VerticalAlignTop` | `arrow.up.to.line` |
| `PlazaIcons.Poll` | 6 | `Poll` | `chart.bar` |
| `PlazaIcons.Gavel` | 6 | `Gavel` | `hammer` |
| `PlazaIcons.EventAvailable` | 6 | `EventAvailable` | `calendar.badge.checkmark` |
| `PlazaIcons.ConfirmationNumber` | 6 | `ConfirmationNumber` | `ticket` |
| `PlazaIcons.Code` | 6 | `Code` | `chevron.left.forwardslash.chevron.right` |
| `PlazaIcons.Alarm` | 6 | `Alarm` | `alarm` |
| `PlazaIcons.Casino` | 5 | `Casino` | `dice` |
| `PlazaIcons.BrokenImage` | 5 | `BrokenImage` | 无对应，保留原图标 |
| `PlazaIcons.Bookmark` | 5 | `Bookmark` | `bookmark.fill` |
| `Icons.Default.KeyboardArrowUp` | 5 | `Filled.KeyboardArrowUp` | `chevron.up` |
| `Icons.Default.ArrowDropDown` | 5 | `Filled.ArrowDropDown` | `chevron.down` |
| `PlazaIcons.Reply` | 4 | `Reply` | `arrowshape.turn.up.left` |
| `PlazaIcons.Mood` | 4 | `Mood` | `face.smiling` |
| `PlazaIcons.FormatQuote` | 4 | `FormatQuote` | `text.quote` |
| `PlazaIcons.CloudUpload` | 4 | `CloudUpload` | `icloud.and.arrow.up` |
| `PlazaIcons.Article` | 4 | `Article` | `doc.text` |
| `Icons.Default.Settings` | 4 | `Filled.Settings` | `gearshape.fill` |
| `Icons.Default.Search` | 4 | `Filled.Search` | `magnifyingglass` |
| `Icons.Default.Build` | 4 | `Filled.Build` | `wrench.and.screwdriver` |
| `PlazaIcons.Wifi` | 3 | `Wifi` | `wifi` |
| `PlazaIcons.Shield` | 3 | `Shield` | `shield` |
| `PlazaIcons.QrCode` | 3 | `QrCode` | `qrcode` |
| `PlazaIcons.Palette` | 3 | `Palette` | `paintpalette` |
| `PlazaIcons.MenuBook` | 3 | `MenuBook` | `book` |
| `PlazaIcons.LinkOff` | 3 | `LinkOff` | 无对应，保留原图标 |
| `PlazaIcons.Forum` | 3 | `Forum` | `bubble.left.and.bubble.right` |
| `PlazaIcons.FolderZip` | 3 | `FolderZip` | `doc.zipper` |
| `PlazaIcons.DragHandle` | 3 | `DragHandle` | `line.3.horizontal` |
| `PlazaIcons.Block` | 3 | `Block` | `nosign` |
| `PlazaIcons.Badge` | 3 | `Badge` | `person.text.rectangle` |
| `NodeSeekIcons.ChickenLeg` | 3 | `ChickenLeg` | 无对应，保留原图标 |
| `Icons.Outlined.Person` | 3 | `Outlined.Person` | `person` |
| `Icons.Default.ShoppingCart` | 3 | `Filled.ShoppingCart` | `cart.fill` |
| `Icons.Default.Person` | 3 | `Filled.Person` | `person.fill` |
| `Icons.Default.FavoriteBorder` | 3 | `Filled.FavoriteBorder` | `heart` |
| `PlazaIcons.WavingHand` | 2 | `WavingHand` | `hand.wave` |
| `PlazaIcons.Wallet` | 2 | `Wallet` | `creditcard` |
| `PlazaIcons.VpnLock` | 2 | `VpnLock` | `lock.shield` |
| `PlazaIcons.VerticalSplit` | 2 | `VerticalSplit` | `rectangle.split.2x1` |
| `PlazaIcons.Title` | 2 | `Title` | `textformat` |
| `PlazaIcons.ThumbDown` | 2 | `ThumbDown` | `hand.thumbsdown` |
| `PlazaIcons.Sync` | 2 | `Sync` | `arrow.triangle.2.circlepath` |
| `PlazaIcons.SwapVert` | 2 | `SwapVert` | `arrow.up.arrow.down` |
| `PlazaIcons.Public` | 2 | `Public` | `globe` |
| `PlazaIcons.PhotoCamera` | 2 | `PhotoCamera` | `camera` |
| `PlazaIcons.PersonSearch` | 2 | `PersonSearch` | 无对应，保留原图标 |
| `PlazaIcons.ModeComment` | 2 | `ModeComment` | `bubble.left` |
| `PlazaIcons.DoneAll` | 2 | `DoneAll` | `checkmark.circle` |
| `PlazaIcons.Dns` | 2 | `Dns` | `server.rack` |
| `PlazaIcons.Contrast` | 2 | `Contrast` | `circle.lefthalf.filled` |
| `PlazaIcons.ContentPaste` | 2 | `ContentPaste` | `doc.on.clipboard` |
| `PlazaIcons.Checklist` | 2 | `Checklist` | `checklist` |
| `PlazaIcons.ChatBubble` | 2 | `ChatBubble` | `bubble.left.fill` |
| `PlazaIcons.BookmarkBorder` | 2 | `BookmarkBorder` | `bookmark` |
| `PlazaIcons.AlternateEmail` | 2 | `AlternateEmail` | `at` |
| `NodeSeekIcons.Award` | 2 | `Award` | 无对应，保留原图标 |
| `Icons.Default.ThumbUp` | 2 | `Filled.ThumbUp` | `hand.thumbsup.fill` |
| `Icons.Default.Share` | 2 | `Filled.Share` | `square.and.arrow.up` |
| `Icons.Default.Notifications` | 2 | `Filled.Notifications` | `bell.fill` |
| `Icons.Default.MailOutline` | 2 | `Filled.MailOutline` | `envelope` |
| `Icons.Default.Favorite` | 2 | `Filled.Favorite` | `heart.fill` |
| `Icons.AutoMirrored.Filled.ExitToApp` | 2 | `AutoMirrored.Filled.ExitToApp` | `rectangle.portrait.and.arrow.right` |
| `PlazaIcons.WifiOff` | 1 | `WifiOff` | `wifi.slash` |
| `PlazaIcons.VerticalAlignBottom` | 1 | `VerticalAlignBottom` | `arrow.down.to.line` |
| `PlazaIcons.Update` | 1 | `Update` | `arrow.clockwise.circle` |
| `PlazaIcons.Tune` | 1 | `Tune` | `slider.horizontal.3` |
| `PlazaIcons.Translate` | 1 | `Translate` | `translate` |
| `PlazaIcons.Tonality` | 1 | `Tonality` | `circle.righthalf.filled` |
| `PlazaIcons.SwapHoriz` | 1 | `SwapHoriz` | `arrow.left.arrow.right` |
| `PlazaIcons.StrikethroughS` | 1 | `StrikethroughS` | `strikethrough` |
| `PlazaIcons.Speed` | 1 | `Speed` | `speedometer` |
| `PlazaIcons.Sort` | 1 | `Sort` | `arrow.up.arrow.down` |
| `PlazaIcons.Sms` | 1 | `Sms` | `message` |
| `PlazaIcons.Science` | 1 | `Science` | `flask` |
| `PlazaIcons.PhotoLibrary` | 1 | `PhotoLibrary` | `photo.on.rectangle` |
| `PlazaIcons.PanTool` | 1 | `PanTool` | `hand.raised` |
| `PlazaIcons.OfflinePin` | 1 | `OfflinePin` | `checkmark.icloud` |
| `PlazaIcons.NetworkCheck` | 1 | `NetworkCheck` | `network` |
| `PlazaIcons.MoreHoriz` | 1 | `MoreHoriz` | `ellipsis` |
| `PlazaIcons.Login` | 1 | `Login` | 无对应，保留原图标 |
| `PlazaIcons.LastPage` | 1 | `LastPage` | `arrow.right.to.line` |
| `PlazaIcons.Fullscreen` | 1 | `Fullscreen` | `arrow.up.left.and.arrow.down.right` |
| `PlazaIcons.FormatListBulleted` | 1 | `FormatListBulleted` | `list.bullet` |
| `PlazaIcons.FormatItalic` | 1 | `FormatItalic` | `italic` |
| `PlazaIcons.FormatBold` | 1 | `FormatBold` | `bold` |
| `PlazaIcons.FolderOff` | 1 | `FolderOff` | `folder.badge.minus` |
| `PlazaIcons.Drafts` | 1 | `Drafts` | `envelope.open` |
| `PlazaIcons.DownloadForOffline` | 1 | `DownloadForOffline` | `arrow.down.circle` |
| `PlazaIcons.DashboardCustomize` | 1 | `DashboardCustomize` | `square.grid.2x2` |
| `PlazaIcons.Colorize` | 1 | `Colorize` | `eyedropper` |
| `PlazaIcons.CloudDone` | 1 | `CloudDone` | `checkmark.icloud` |
| `PlazaIcons.BugReport` | 1 | `BugReport` | `ladybug` |
| `PlazaIcons.BookmarkRemove` | 1 | `BookmarkRemove` | `bookmark.slash` |
| `PlazaIcons.Bolt` | 1 | `Bolt` | `bolt` |
| `PlazaIcons.Bedtime` | 1 | `Bedtime` | `moon` |
| `PlazaIcons.Backspace` | 1 | `Backspace` | `delete.left` |
| `PlazaIcons.Apps` | 1 | `Apps` | `square.grid.3x3` |
| `Icons.Outlined.ThumbUp` | 1 | `Outlined.ThumbUp` | `hand.thumbsup` |
| `Icons.Outlined.Notifications` | 1 | `Outlined.Notifications` | `bell` |
| `Icons.Outlined.Home` | 1 | `Outlined.Home` | `house` |
| `Icons.Filled.Person` | 1 | `Filled.Person` | `person.fill` |
| `Icons.Filled.Notifications` | 1 | `Filled.Notifications` | `bell.fill` |
| `Icons.Filled.Home` | 1 | `Filled.Home` | `house.fill` |
| `Icons.Default.Face` | 1 | `Filled.Face` | `face.smiling` |
| `Icons.Default.DateRange` | 1 | `Filled.DateRange` | `calendar` |
| `Icons.Default.AccountCircle` | 1 | `Filled.AccountCircle` | `person.crop.circle` |
| `Icons.AutoMirrored.Filled.ArrowForward` | 1 | `AutoMirrored.Filled.ArrowForward` | `arrow.forward` |

## 保留原图标的原因

| 图标 | 原因 |
|---|---|
| `NodeSeekIcons.ChickenLeg`、`NodeSeekIcons.Award` | 站点专属标识（鸡腿、奖章），SF Symbols 没有 |
| `PlazaIcons.BrokenImage` | 图片加载失败的占位；SF Symbols 没有"损坏的图片"，用感叹号会和错误提示混淆 |
| `PlazaIcons.LinkOff` | 没有"断开链接"的符号 |
| `PlazaIcons.PersonSearch` | 没有"搜索用户"的符号，拼凑的组合语义不清 |
| `PlazaIcons.Login` | iOS 的登录 / 登出箭头符号方向约定和 Material 相反，容易误读 |
