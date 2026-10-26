# iOS DoH and ECH test transport

The native forum client explicitly resolves A, optional AAAA and HTTPS records from the selected DoH provider. Its IP answers and ECHConfigList travel together into rustls (AWS-LC HPKE). TLS keeps the original server name, WebPKI roots and normal certificate validation. HTTP/1.1 and HTTP/2 share a bounded connection pool. Resolver settings are read before the first request; switching the selected resolver uses a different pool.

This follows the explicit DNS-to-ECH architecture investigated in FluxDO / fluxdo_doh, with an independently implemented C ABI and Kotlin transport. No app source or certificate-trust shortcuts were copied.

Native password login, two-factor login, posts, forum writes, avatars and image downloads use this route when DoH is enabled and the app's forum proxy is disabled. URLSession remains the route when DoH is off or the app's proxy is selected. Native requests and WebKit share the existing Apple cookie storage through the cookie bridge. Every redirect stores individual Set-Cookie headers before the next request; cross-origin redirects strip authorization and cannot replay a request body. HTTPS cannot downgrade to HTTP. Non-idempotent requests are not automatically retried.

WebKit still renders the Turnstile widget and optional browser login through Apple's own networking and existing encrypted DNS privacy context. Native transport changes cannot guarantee WebKit ECH negotiation. There is no local TLS interception, installed root CA or remote plaintext exception.

Failures expose allowlisted connection stages and TLS/ECH outcomes in the login diagnostic action. They do not export credentials, cookies, request bodies, arbitrary native errors or custom resolver URLs. A failed handshake reports NotEstablished rather than claiming ECH acceptance. The read-only probe verifies actual ECH acceptance against the configured production resolver from CI; iPhone behavior must still be verified on the affected network.

Build prerequisites: JDK 21, Xcode, Rust 1.99.1 and the repository's Android SDK configuration. Gradle invokes `.github/scripts/build-ios-network.sh` per Apple target and embeds the static library through cinterop. Cargo.lock pins the native dependency graph. Run `cargo test --locked` and `cargo fmt --check` in `native/ios-network`, the normal shared gates, and `:shared:macosArm64Test` before packaging an unsigned device IPA.
