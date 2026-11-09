package io.github.nodyssey.data.session

import io.github.plaza.core.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountDiagnosticsTest {
    // Bug: printing Throwable or NSError descriptions exports cookies embedded in their messages.
    @Test
    fun `failure report retains step and native code without secret messages`() = runTest {
        val diagnostics = AccountDiagnostics(AppClock { 123 })
        assertFailsWith<IllegalStateException> {
            diagnostics.step(AccountStep.CAPTURE_COOKIES) {
                throw IllegalStateException("secret-password session=secret-cookie", AccountNativeFailure("NSCocoaErrorDomain", 3840))
            }
        }
        val report = diagnostics.report.value
        assertTrue(report.contains("CAPTURE_COOKIES failed"))
        assertTrue(report.contains("IllegalStateException -> AccountNativeFailure"))
        assertTrue(report.contains("NSCocoaErrorDomain/3840"))
        assertFalse(report.contains("secret-"))
        assertFailsWith<AccountNativeFailure> {
            diagnostics.step(AccountStep.READ_VAULT) { throw AccountNativeFailure("secret-domain", 7) }
        }
        assertTrue(diagnostics.report.value.contains("Other/7"))
        assertFalse(diagnostics.report.value.contains("secret-domain"))
    }

    // Bug: a cancelled screen is reported as storage corruption or hides the original failure.
    @Test
    fun `cancellation propagates without becoming a storage failure`() = runTest {
        val diagnostics = AccountDiagnostics()
        assertFailsWith<CancellationException> {
            diagnostics.step(AccountStep.FETCH_PROFILE) { throw CancellationException("secret-message") }
        }
        assertTrue(diagnostics.report.value.contains("FETCH_PROFILE cancelled"))
        assertFalse(diagnostics.report.value.contains("failed"))
        assertFalse(diagnostics.report.value.contains("secret-message"))
    }
}
