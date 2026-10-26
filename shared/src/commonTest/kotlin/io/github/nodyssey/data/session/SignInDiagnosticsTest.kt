package io.github.nodyssey.data.session

import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.dns.DohServer
import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.NativeNetworkError
import io.github.plaza.core.net.NativeNetworkErrorDomain
import io.github.plaza.core.net.TransportFailureDiagnostics
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInDiagnosticsTest {
    @Test
    fun reportIncludesRoutingChoicesButRedactsPrivateEndpointsAndSecrets() {
        val context = SignInDiagnosticContext(
            appVersion = AppVersion("1.3.4-test", 85),
            doh = DohConfig(
                enabled = true,
                servers = listOf(
                    DohServer.preset(DohProvider.WEISHIAIR, checked = true),
                    DohServer(
                        id = "private-server-id",
                        customUrl = "https://private-resolver.test/query?key=private-api-key",
                        bootstrapOverride = "192.0.2.44",
                        checked = true,
                    ),
                ),
            ),
            proxy = ProxyConfig(
                enabled = true,
                host = "private-proxy.test",
                port = 8443,
                username = "private-user",
                password = "private-password",
            ),
        )
        val report = signInDiagnosticReport(
            failure = TransportFailureDiagnostics("iOS test", listOf(NativeNetworkError(NativeNetworkErrorDomain.URL, -1200))),
            context = context,
            twoFactor = true,
            verificationPassed = true,
            timeMillis = 1234,
        )
        assertTrue(report.contains("1.3.4-test (85)"))
        assertTrue(report.contains("selected=WEISHIAIR,CUSTOM"))
        assertTrue(report.contains("Step: two-factor"))
        assertTrue(report.contains("Verification ready: true"))
        assertTrue(report.contains("NSURLErrorDomain (-1200)"))
        listOf("private-", "192.0.2.44", "8443", "https://").forEach { assertFalse(report.contains(it), it) }
    }
}
