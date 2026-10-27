package io.github.plaza.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransportFailureDiagnosticsTest {
    @Test
    fun rustConnectionFactsAreBoundedAndClassified() {
        val failure = TransportFailureDiagnostics("test", emptyList(), NativeTransportEngine.RUST_ECH, EncryptedConnectionDiagnostics.fromNative("TLS", "certificate_rejected", "NotEstablished"))
        assertEquals(TransportFailureCategory.CERTIFICATE, failure.category)
        assertTrue(failure.asText().contains("Nodyssey rustls DoH/ECH"))
        assertTrue(failure.asText().contains("Connection stage: TLS"))
        val untrusted = EncryptedConnectionDiagnostics.fromNative("private-url", "password", "cookie")
        assertEquals("UNKNOWN", untrusted.stage)
        assertEquals("other", untrusted.reason)
        assertEquals("Unknown", untrusted.ech)
    }

    @Test
    fun wrapperDoesNotHideUrlLoadingFailure() {
        val failure = TransportFailureDiagnostics(
            "iOS test",
            listOf(
                NativeNetworkError(NativeNetworkErrorDomain.OTHER, 1),
                NativeNetworkError(NativeNetworkErrorDomain.URL, -1200),
                NativeNetworkError(NativeNetworkErrorDomain.CFNETWORK, -1200, streamDomain = 3, streamCode = -9824),
            ),
        )
        assertEquals(TransportFailureCategory.TLS, failure.category)
        assertTrue(failure.asText().contains("CFStream code: -9824"))
        assertTrue(failure.asText().contains("NSURLErrorDomain (-1200)"))
        assertTrue(failure.asText().contains("ECH negotiation: not measured"))
    }

    @Test
    fun dnsAndCertificateFailuresStayDistinctFromHandshakeAndTimeout() {
        mapOf(
            -1003L to TransportFailureCategory.DNS,
            -1006L to TransportFailureCategory.DNS,
            -1001L to TransportFailureCategory.TIMEOUT,
            -1004L to TransportFailureCategory.CONNECTION,
            -1200L to TransportFailureCategory.TLS,
            -1202L to TransportFailureCategory.CERTIFICATE,
            -1206L to TransportFailureCategory.CERTIFICATE,
            -1022L to TransportFailureCategory.ATS,
        ).forEach { (code, expected) ->
            val failure = TransportFailureDiagnostics("test", listOf(NativeNetworkError(NativeNetworkErrorDomain.URL, code)))
            assertEquals(expected, failure.category)
        }
        // The numeric value has meaning only inside the matching error domain.
        val posix = TransportFailureDiagnostics("test", listOf(NativeNetworkError(NativeNetworkErrorDomain.POSIX, -1200)))
        assertEquals(TransportFailureCategory.OTHER, posix.category)
    }

    @Test
    fun unknownDomainsCannotExportArbitraryTextAndTheChainIsBounded() {
        val domain = NativeNetworkErrorDomain.fromNative("private-account@example.test")
        val failure = TransportFailureDiagnostics("test", List(20) { NativeNetworkError(domain, it.toLong()) })
        assertEquals(NativeNetworkErrorDomain.OTHER, domain)
        assertFalse(failure.asText().contains("private-account"))
        assertEquals(TransportFailureDiagnostics.MAX_ERRORS, failure.asText().lineSequence().count { it.startsWith("Error[") })
    }
}
