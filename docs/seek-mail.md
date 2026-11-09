# NodeSeek Mail — phase one

The signed-in entry is **My → NodeSeek Mail**, in the community tile group. Guests
also have a direct mail tile and can reach it through **My → Community tools → NodeSeek Mail**. The native screens connect
an account, list owned mailboxes, switch inboxes, paginate, read a message, and
mark it read or unread. Opening a message marks it read; a failed update rolls
back in both the detail and the inbox.

**Read all**, beside the inbox heading, marks the selected inbox across every
page. It captures existing message IDs, skips already-read mail, and sends
sequential read updates in either connection mode. Mail arriving during the
operation is excluded. Repeated taps are blocked while it runs; a permission,
network, or rate-limit failure stops further updates, keeps completed changes,
and reports the incomplete result. Switching accounts invalidates the operation.

## Connect

On Android, the primary action exchanges the existing forum session through the
same native OkHttp DNS/ECH and proxy configuration used by the app. It does not
open a WebView:

1. `GET https://seek.li/api/auth/providers` must advertise the enabled `nodeseek`
   popup provider with the expected NodeSeek origin, `ns-connect` source and
   `NodeMailDev` target.
2. `GET https://www.nodeseek.com/api/cAuth?target=NodeMailDev` uses the existing
   forum cookie bridge. Its signed `data`, `wtf` and `sign` JSON values are
   preserved, and its receiver list must include `https://seek.li`.
3. `POST https://seek.li/api/auth/provider/nodeseek/callback` submits only those
   three fields, using mail-scoped cookies and the website's CSRF header/retry.
4. The returned token is verified against the mailbox endpoint before it is
   encrypted and stored. Cancellation, disconnect and account switching invalidate
   each stage, so an old reply cannot restore a previous account's credential.

The provider GET is not automatically retried: it grants access despite its HTTP
method. Both authorization clients disable redirects, preventing a 307/308 from
forwarding the signed callback body to another origin. Forum and mail requests
use separate cookie bridges; forum cookies are not mail API credentials.

Website authorization remains an explicit fallback for security challenges,
two-factor verification, onboarding, or an unsupported provider configuration.
There is no automatic fallback from a failed native request to WebView. The
fallback uses WebView's own network, which does not inherit the app's native
DoH/ECH and may still require a proxy. Apple retains website authorization as its
primary action until a native provider transport is configured there.

The fallback opens `https://seek.li/login` and captures the website session token
from the main frame's local storage after the official provider callback. Each
explicit connection clears the old website token first. It has its own HTTPS
origin policy for `seek.li` and NodeSeek; the forum browser policy is unchanged.
Capture checks both the native main-frame URL and the script's `location.origin`,
with no JavaScript interface exposed to email frames.

Manual API key entry remains available as a fallback. The developer page is
`https://seek.li/manage/support?tab=developer`. A key needs `mailbox:read`,
`mail:read`, and `mail:manage`; the app verifies mailbox access before storing it.

## Data and authentication

The primary connection uses the website's session API at `https://seek.li/api`:

| Action | Endpoint | Response |
| --- | --- | --- |
| Owned mailboxes | `GET /mailboxes` | `owned` array; shared mailboxes are excluded |
| Inbox | `GET /mails?mailbox_id=…&folder=inbox&limit=20&offset=…` | `mails`, `total`, `limit`, `offset` |
| Message | `GET /mails/{id}` | Mail object directly |
| Read state | `PUT /mails/{id}/read` with `{"read":true/false}` | Success status |

Requests send the website token as `Authorization: Bearer …`, with origin-scoped
cookies through the existing platform cookie bridge. The CSRF header is decoded
from the `csrf_token` cookie. For the website's three specific CSRF rejection
messages, a write renews the cookie through `GET /auth/csrf` and retries once.
Other permission failures are not retried. A 401 erases the saved credential and
live mail content; a 468 offers explicit website authorization.

This website API is not a documented third-party contract and may change.
The fallback uses the published specification at `https://seek.li/api/openapi.json`,
version `1.0.0`, base URL `https://seek.li/openapi/v1`, with the same endpoint paths.
It uses a cookie-free transport and the published `data`/`pagination` envelopes.
The deprecated `/mailboxes/{id}/mails` ignores its path ID, so both connection modes
use an explicit `mailbox_id` filter and reject mail from another mailbox.

The `seek-mail` preferences store contains one encrypted credential: a website
token prefixed with `web:`, or a manually supplied API key. Android uses the
existing Keystore cipher; iOS uses the existing backup-excluded secret store
policy. Encryption failure refuses persistence. Credentials never enter a
navigation key, saved UI state, diagnostic exception, or committed fixture.
Mail content stays in memory without a database cache.

Disconnect and forum sign-out clear the credential, read overrides, and visible
content. Each connection has a generation; pending replies from an earlier
connection cannot publish data or restore a credential after disconnect.

## Reading and limits

Plain-text bodies are preferred. HTML-only bodies are converted off the UI thread
with the pinned Ksoup parser, preserving paragraphs, table text, and safe link
targets. Email HTML never enters a WebView. Scripts, embedded documents, and remote
images are not executed or fetched. Web/mail links open only on an explicit tap.

Attachments have an explanatory website action. Sending, attachment downloads,
folders, and search remain outside phase one.

## New mail monitoring

The navigation root owns one foreground monitor across all tabs, independently of
forum sign-in. It checks immediately on resume, then about every 30 seconds with
an API key, or every 15 seconds while an inbox or message is open. Website-session
connections use 60 seconds. Pausing the app cancels this fast loop. Requests are
sequential; errors back off up to five minutes, and changing screens cannot bypass
a rate-limit cooldown.

The owned-mailbox catalog is cached in memory for five minutes; normal ticks need
only the first inbox page. Manual refresh reloads the catalog, and a removed inbox
invalidates it. Unchanged pages do not reparse message previews. This reduces
request count but does not establish a measured battery or data-usage figure.

Foreground checks and the Android notification worker share a serialized,
persisted per-inbox newest-ID cursor. The first successful check establishes a
baseline without announcing historical mail; an empty baseline still detects the
first later message. Newly arrived mail already read on the website is silent.
Credential replacement and default-inbox changes cannot accept a previous poll's
cursor or snapshot. No email content is persisted by this monitor.

The profile mail tile shows a dot when the current default inbox's fetched first
page contains unread mail, with optimistic read changes and rollback applied.
This is a detected-unread indicator, not an unread total over every page. A global
in-app snackbar announces new arrivals and opens the inbox; the inbox consumes the
same first-page snapshot without starting another polling loop.

Historical unread mail also keeps the dot visible after a newer message is read.
The detail response now publishes its read state to the same repository used by
the list and dot, even when no extra read write is needed. Successful local read
changes remain authoritative until an inbox response acknowledges them, so a
lagging response cannot immediately restore the unread dot. Later website read
changes still synchronize after that acknowledgement. Failed writes roll back.

Read all walks every page of the selected inbox and marks messages sequentially,
bounded by the newest ID seen when the operation starts so later arrivals stay
unread. An error stops the batch while keeping successful writes. Replacing the
account invalidates the operation. The disabled action shows a compact 16dp
indicator with a 2dp stroke while work is in progress.

Foreground detection and in-app alerts work while connected, regardless of the
background notification switch. Android system notifications require both the
notification master switch and the mail switch, plus OS permission. They follow
the configured WorkManager interval, network constraints, and quiet hours. Android
periodic work has a minimum interval of 15 minutes and can run later; the app does
not promise second-level delivery while backgrounded. An API key supplies polling
access and is not itself a push subscription.

On 2026-11-08, browser research observed successful authenticated website mailbox
and inbox requests and checked the shipped website client for detail, read-state,
and CSRF behavior. The live inbox was empty, so a real message detail was not
opened. No real API key was created. Tests use synthetic replies and a committed
synthetic HTML fixture. The native provider protocol is covered by synthetic tests.
Android device authorization and Apple compilation still require device/Mac
verification; browser research alone does not establish those results.

## Local validation

The session implementation passed Android and desktop JVM compilation, focused
mail tests on JVM and Android host, sign-out cleanup, formatting, Android lint,
debug assembly, and the R8 release bundle build. The full UI host run completed
863 tests with two existing `PreferencesViewModelTest` failures involving Windows
file storage; all five mail ViewModel tests and three mail renders passed.
An earlier full repository run also encountered existing shared DataStore rename
and app Room migration path failures on Windows. These unrelated failures remain
unresolved. There are no dependency, Room schema, or application version changes.

For Android testing, the mail changes were also integrated onto `4165e252`, the
source of the preceding `1.3.10-test.2` snapshot. This retains the released account
manager and the invite purchase fixes. Both shells clear mail through
`AccountLocalData.clear`, so adding, switching, and signing out cannot leave a
previous account's connection active. A regression test exercises an actual mail
repository through an account switch. Focused mail/account/navigation tests,
formatting, Android lint, and release compilation passed on this base.

The native authorization change passes 56 focused Android shared tests (mail,
account switching and sign-out), nine focused UI/state-boundary tests, and 26 JVM
mail tests, including eight new provider protocol tests. The seven mail ViewModel
tests include native challenge recovery and cancellation before callback. The
full run reports the same Windows failures as the preceding snapshot: 21 app
Room migration tests, 11 shared file-store tests and two UI preferences tests.
There are no dependency or schema changes.

The `1.3.10-test.10` foreground-monitor update passes the mail and profile state
regressions on Android host, common mail tests on desktop JVM, architecture and
string boundaries, render inspection, standalone formatting, and Android lint
(0 errors, 0 warnings). Debug, signed R8 Release APK, and Release bundle assembly
pass. The full test run retains exactly the same 34 failed test names as test.9
(21 app Room path tests, 11 shared file-store tests, two UI preferences tests).
The APK is version code 144, uses the existing certificate and application ID,
and contains only ARM64/ARMv7 native libraries. No connected Android device was
available; battery and data consumption have not been measured on a device.

The `1.3.10-test.11` read-state and Read all update passes mail/profile regressions,
including both connection modes, unloaded pages, arrivals shifting pagination,
permission/rate-limit interruption, cancellation rollback, and account replacement.
Formatting, mail render inspection, Android lint (0 errors, 0 warnings), Debug,
signed R8 Release APK, and Release bundle assembly pass. The full run executes
2,621 tests and retains exactly the same 34 failed test names as test.10, with
214 skipped tests. The APK is version code 145, 12,156,758 bytes, and retains the
existing application ID, signing certificate, and ARM64/ARMv7 targets. No connected
Android device was available for installation or live service verification.

On 2026-11-09 the user reported that test.11 worked on their device. The remaining
visual feedback was the oversized Read all indicator; its size now follows the
app's other inline button indicators, with before/after Robolectric renders.

The dev integration selects only the mail commits from those combined test
snapshots. Invite-purchase changes stay on their own branch. The integration keeps
dev's stable version, 1.3.9 (134), without importing a test-version bump.
