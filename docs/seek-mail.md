# NodeSeek Mail — phase one

The entry is **My → Community tools → NodeSeek Mail**. The native screens connect
an account, list owned mailboxes, switch inboxes, paginate, read a message, and
mark it read or unread. Opening a message marks it read; a failed update rolls
back in both the detail and the inbox.

## Connect

The primary action opens `https://seek.li/login` inside the app. The official
NodeSeek login opens `https://www.nodeseek.com/connect?target=NodeMailDev` and can
reuse the app's forum cookies. Once the website finishes its provider callback,
the app reads the website session token from the main frame's local storage,
verifies mailbox access, and returns to the native inbox. No API key is created.

Forum login and mail login remain separate sessions. A forum cookie authorizes
the provider step; it is not a mail API credential. Each explicit connection
clears the previous mail token in the website before starting, so switching forum
accounts cannot silently import the previous mail identity from local storage.
The official login and any site verification remain visible to the user.

The mail browser has its own HTTPS origin policy for `seek.li` and NodeSeek. The
forum browser's global host policy is unchanged. Token capture checks both the
native main-frame URL and the script's `location.origin`; no JavaScript interface
is exposed to email frames. Cancelling authorization or signing out invalidates
the attempt, including a verification request that completes late.

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
live mail content; a 468 offers another visible website authorization.

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
synthetic HTML fixture. Native device authorization and Apple compilation still
require device/Mac verification; browser research alone does not establish those
results.

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
