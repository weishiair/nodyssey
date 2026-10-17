# Android ECH compatibility

The app still installs on Android 8.0 (API 26) and later. DoH is available on every supported
Android version. ECH now has two TLS backends:

| Android | TLS backend |
| --- | --- |
| 8–16 (API 26–36) | App-bundled Conscrypt 2.7.0 |
| 17+ (API 37+) | Android's TLS provider and native ECH APIs |

The existing encrypted DNS settings control both paths. `AppDns.newCall()` preserves HTTPS
service records alongside addresses, and OkHttp supplies the ECH configuration belonging to the
selected route. No extra resolver or hostname-to-config cache is introduced. With no DoH service
metadata, a connection has no real ECH configuration. A successful DNS lookup does not establish
that a server supports ECH or that its IP address is reachable.

`BundledEchPlatform` is a compatibility bridge for the pinned OkHttp 5.5.0, whose Conscrypt socket
adapter accepts but does not apply the route's ECH configuration. OkHttp has no public socket
adapter registration API, so the bridge uses its internal platform replacement hook at app startup.
It delegates the original platform's DNS, certificate-chain cleaner, socket connection, logging,
cleartext policy and application context behavior. Remove this bridge when OkHttp directly supports
standalone Conscrypt ECH. No JCA provider is installed globally; the socket factory selects Conscrypt
explicitly. The Android 17 path is left on its native provider.

Standalone Conscrypt 2.7.0 defaults its ECH policy to `UNKNOWN`, so providing ECH bytes alone does
not enable encryption. `EchTrustManager` exposes Conscrypt's policy hook and opts into ECH, while
delegating every certificate check to the original trust manager, including socket/engine overloads.
Its reflected policy method is retained through R8 with `@Keep`. OkHttp still verifies the requested
hostname and applies certificate pinning. The wrapper implements `X509TrustManager` and exposes
socket/engine overloads explicitly: subclassing `X509ExtendedTrustManager` makes Conscrypt replace
it with an anonymous delegate that loses the ECH policy, silently restoring plaintext SNI. The wire
test exercises that failure. No certificate or hostname check is disabled. Conscrypt's
standalone Android API currently does not expose authenticated ECH retry configurations; a rejected
configuration fails the connection rather than silently downgrading it. Android 17 retains its
native ECH retry behavior.

The login WebView has its own networking stack; these settings and the bundled provider apply to
the app's OkHttp clients, including forum requests, images and background work.

Certificate trust still comes from Android's installed CA store. A resolver whose current certificate
chains to a newer root may be rejected by an old Android image, independently of ECH support.
The device tests use the configured custom DoH endpoint on both versions, rather than disabling
certificate checks to accommodate an alternative resolver's certificate chain.

## Verification

`BundledEchTest` uses real desktop Conscrypt 2.7 JNI and local sockets. It reads the actual
ClientHello to verify that DNS ECH metadata changes the visible SNI from `hidden.example` to the
public cover name `example.com`. Separate HTTPS tests check a trusted matching certificate, an
untrusted certificate, and a mismatched hostname. The public ECH fixture is from the Apache-2.0
Conscrypt 2.7.0 test resources; no production keys or sessions are used.

The manual `Build ECH APK` workflow additionally runs `BundledEchDeviceTest` on API 26 and API 36
emulators. This opt-in live test uses the app's actual TLS client and requires Cloudflare's trace
endpoint to report `sni=encrypted`; merely seeing ECH bytes in DNS is insufficient. Routine device
journeys skip this external-server test unless `liveEch=true` is supplied.
