package io.github.plaza.core.net

import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSUnderlyingErrorKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppleNetworkFailureTest {
    @Test
    fun underlyingCodesSurviveWithoutCopyingNSErrorContext() {
        val underlying = NSError(
            domain = "NSOSStatusErrorDomain",
            code = -9843,
            userInfo = mapOf(NSLocalizedDescriptionKey to "private-account and private-password"),
        )
        val error = NSError(
            domain = "NSURLErrorDomain",
            code = -1200,
            userInfo = mapOf(
                NSUnderlyingErrorKey to underlying,
                "_kCFStreamErrorCodeKey" to NSNumber(long = -9843),
                "_kCFStreamErrorDomainKey" to NSNumber(long = 3),
                "failingURL" to "https://private-proxy.test/?token=private-token",
                NSLocalizedDescriptionKey to "private-description",
            ),
        )
        val failure = error.asNetworkFailure()
        val diagnostics = requireNotNull(failure.diagnostics)
        assertEquals(SiteError.Network, failure.error)
        assertEquals("private-description", failure.detail)
        assertEquals(listOf(-1200L, -9843L), diagnostics.errors.map { it.code })
        assertEquals(-9843L, diagnostics.errors.first().streamCode)
        assertEquals(3L, diagnostics.errors.first().streamDomain)
        assertFalse(diagnostics.asText().contains("private-"))
    }

    @Test
    fun deeplyNestedUnderlyingErrorsAreBounded() {
        var error = NSError(domain = "NSURLErrorDomain", code = -1200, userInfo = null)
        repeat(20) { error = NSError(domain = "NSOSStatusErrorDomain", code = 1, userInfo = mapOf(NSUnderlyingErrorKey to error)) }
        assertEquals(TransportFailureDiagnostics.MAX_ERRORS, error.asNetworkFailure().diagnostics?.errors?.size)
    }
}
