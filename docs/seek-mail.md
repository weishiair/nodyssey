# NodeSeek Mail — phase one

The signed-in entry is **My → NodeSeek Mail**, in the community tile group. Guests
can reach it through **My → Community tools → NodeSeek Mail**. The native screens connect
an account, list owned mailboxes, switch inboxes, paginate, read a message, and
mark it read or unread. Opening a message marks it read; a failed update rolls
back in both the detail and the inbox.

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
folders, search, and notification polling remain outside phase one.

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
