package io.github.nodyssey.ui.login

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.session.EmailCodeOutcome
import io.github.nodyssey.data.session.EmailSignInChallenge
import io.github.nodyssey.data.session.FakeSessionCookieStore
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.session.SignInCredentials
import io.github.nodyssey.data.session.SignInOutcome
import io.github.nodyssey.data.session.SignInRefusal
import io.github.nodyssey.data.session.SignInRepository
import io.github.nodyssey.data.session.TwoFactorChallenge
import io.github.nodyssey.ui.ViewModels
import io.github.nodyssey.ui.typeText
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.NativeNetworkError
import io.github.plaza.core.net.NativeNetworkErrorDomain
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.TransportFailureDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 登录 · 原生表单 (h1)'s state machine.
 *
 * What is held here is card 2's promise — that pressing 登录 can cost the user a refusal but never
 * the text they typed — and the two transitions the endpoint's shape forces: a second factor is a
 * different page, and an expired `otpSession` is the one refusal that has to walk back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModels()
    private val cookies = FakeSessionCookieStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        viewModels.clear(dispatcher.scheduler)
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeSignInRepository, clock: AppClock = AppClock { FIXED_MILLIS }) =
        viewModels.track(
            SignInViewModel(
                signIn = repository,
                session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies)),
                clock = clock,
            ),
        )

    /** Filled in and verified — the only state from which 登录 is pressable. */
    private fun TestScope.readyViewModel(repository: FakeSignInRepository): SignInViewModel {
        val vm = viewModel(repository)
        vm.accountState.typeText("nssk")
        vm.passwordState.typeText("hunter2hunter2")
        runCurrent()
        vm.onVerified("turnstile-token")
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `the button stays down until the verification block answers`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(jar = cookies)
            val vm = viewModel(repository)
            vm.accountState.typeText("nssk")
            vm.passwordState.typeText("hunter2hunter2")
            advanceUntilIdle()

            // The widget is up and waiting, which is not the same as answered.
            assertEquals(VerificationState.Pending, vm.uiState.value.verification)
            assertFalse("a filled form with no token must not be submittable", vm.uiState.value.canSubmitCredentials)

            vm.submitCredentials()
            advanceUntilIdle()

            assertNull("pressing a disabled button must not reach the network", repository.sent)

            // And the same when there is no widget at all — the desktop target, or one that failed
            // to start. Nothing about that state makes the form sendable.
            vm.onVerificationUnavailable()
            advanceUntilIdle()

            assertEquals(VerificationState.NotWired, vm.uiState.value.verification)
            assertFalse(vm.uiState.value.canSubmitCredentials)
        }

    @Test
    fun `the token travels with the credentials`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(jar = cookies)
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            assertEquals(
                SignInCredentials("nssk", "hunter2hunter2", "turnstile-token"),
                repository.sent,
            )
            assertTrue(vm.uiState.value.signedIn)
        }

    @Test
    fun `a refusal keeps both fields and spends the verification`() =
        runTest(dispatcher) {
            val repository =
                FakeSignInRepository(
                    outcome = SignInOutcome.Refused(SignInRefusal.Credentials, "用户名或密码不正确"),
                    jar = cookies,
                )
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("nssk", vm.accountState.text.toString())
            assertEquals("hunter2hunter2", vm.passwordState.text.toString())
            assertTrue(state.hasCredentialRefusal)
            assertEquals("用户名或密码不正确", state.refusal?.detail)
            // The site resets its own widget on every refusal; a spent token must not look reusable.
            assertEquals(VerificationState.Expired, state.verification)
            assertFalse(state.canSubmitCredentials)
            // …and the widget has to be told, or the next press reuses a token that is already gone.
            assertEquals(1, state.verificationGeneration)
        }

    @Test
    fun `a second factor opens its own step with an empty box`() =
        runTest(dispatcher) {
            val challenge = TwoFactorChallenge(account = "nssk", otpSession = "otp-1")
            val repository = FakeSignInRepository(outcome = SignInOutcome.TwoFactorRequired(challenge), jar = cookies)
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SignInStep.TwoFactor, state.step)
            assertEquals(challenge, state.challenge)
            assertEquals("", vm.codeState.text.toString())
            assertFalse("an empty box cannot be submitted", state.canSubmitCode)
            // 30-second window, and the clock sits 12s into one — see FIXED_MILLIS. A reader, not a
            // loop: a `while (true) { delay(…) }` in the ViewModel would make this very
            // `advanceUntilIdle()` spin through virtual time and never come back.
            assertEquals(18, vm.secondsUntilNextCode())
        }

    @Test
    fun `six digits arm the verify button and reach the endpoint`() =
        runTest(dispatcher) {
            val challenge = TwoFactorChallenge(account = "nssk", otpSession = "otp-1")
            val repository = FakeSignInRepository(outcome = SignInOutcome.TwoFactorRequired(challenge), jar = cookies)
            val vm = readyViewModel(repository)
            vm.submitCredentials()
            advanceUntilIdle()

            repository.outcome = SignInOutcome.Signed
            vm.codeState.typeText("49172")
            runCurrent()
            assertFalse("five digits is not a code", vm.uiState.value.canSubmitCode)

            vm.codeState.typeText("491723")
            runCurrent()
            assertTrue(vm.uiState.value.canSubmitCode)

            vm.submitTwoFactor()
            advanceUntilIdle()

            assertEquals(challenge to "491723", repository.verified)
            assertTrue(vm.uiState.value.signedIn)
        }

    /**
     * The endpoint said yes and the jar came away empty.
     *
     * `jar = null` is the whole of the setup: the fake answers [SignInOutcome.Signed] and writes no
     * cookie, which is what a sign-in that stores nothing looks like from in here. What is asserted
     * is that the screen does *not* leave — the bug this covers had the user returned to a
     * signed-out screen with nothing said, because the endpoint's word was taken for the session.
     */
    @Test
    fun `a success that stores no session does not sign in`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(jar = null)
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse("nothing was stored, so nothing was signed in", state.signedIn)
            assertTrue(state.sessionNotStored)
            val report = requireNotNull(state.failureReport)
            listOf("nssk", "hunter2hunter2", "turnstile-token").forEach { assertFalse(report.contains(it)) }
            assertNull("the site did not refuse — it accepted", state.refusal)
            assertEquals("the password was right; it is still there", "hunter2hunter2", vm.passwordState.text.toString())
            // Spent with the attempt the site accepted, so a second press asks the widget for a new one.
            assertEquals(1, state.verificationGeneration)
        }

    /** The same, arrived at from card 3 — which has nothing left to post, so it walks back. */
    @Test
    fun `a second factor that stores no session walks back to the password`() =
        runTest(dispatcher) {
            val challenge = TwoFactorChallenge(account = "nssk", otpSession = "otp-1")
            val repository = FakeSignInRepository(outcome = SignInOutcome.TwoFactorRequired(challenge), jar = null)
            val vm = readyViewModel(repository)
            vm.submitCredentials()
            advanceUntilIdle()

            repository.outcome = SignInOutcome.Signed
            vm.codeState.typeText("491723")
            runCurrent()
            vm.submitTwoFactor()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.signedIn)
            assertTrue(state.sessionNotStored)
            assertEquals(SignInStep.Credentials, state.step)
            assertNull(state.challenge)
        }

    /**
     * The cookie was on its way in, not missing.
     *
     * The store behind the jar writes on its own schedule, so the read taken the instant the endpoint
     * answers can miss a session that arrives a beat later. Accusing that sign-in of not having
     * worked is the one wrong answer this check must not give.
     */
    @Test
    fun `a session that lands a beat late is not reported as missing`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(jar = cookies, writeDelayMillis = 200)
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.signedIn)
            assertFalse("the cookie was late, not absent", state.sessionNotStored)
        }

    /** Pressing 登录 again clears the last verdict, this one included. */
    @Test
    fun `a fresh submit clears the stored-nothing banner`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(jar = null)
            val vm = readyViewModel(repository)
            vm.submitCredentials()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.sessionNotStored)

            repository.jar = cookies
            vm.onVerified("turnstile-token-2")
            advanceUntilIdle()
            vm.submitCredentials()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.sessionNotStored)
            assertTrue(vm.uiState.value.signedIn)
        }

    @Test
    fun `an expired otp session walks back to the password`() =
        runTest(dispatcher) {
            val challenge = TwoFactorChallenge(account = "nssk", otpSession = "otp-1")
            val repository = FakeSignInRepository(outcome = SignInOutcome.TwoFactorRequired(challenge), jar = cookies)
            val vm = readyViewModel(repository)
            vm.submitCredentials()
            advanceUntilIdle()

            repository.outcome =
                SignInOutcome.Refused(SignInRefusal.TwoFactorSessionExpired, "OTP_EXPIRED_OR_NOT_EXIST")
            vm.codeState.typeText("491723")
            runCurrent()
            vm.submitTwoFactor()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SignInStep.Credentials, state.step)
            assertNull("nothing is left to post the code with", state.challenge)
            assertEquals("the account is still there to try again", "nssk", vm.accountState.text.toString())
            assertEquals(SignInRefusal.TwoFactorSessionExpired, state.refusal?.reason)
        }

    @Test
    fun `a transport failure is a snackbar, and retry sends the same thing again`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(failure = SiteException(SiteError.Network), jar = cookies)
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            assertEquals(SiteError.Network, vm.uiState.value.failure)
            assertNull("a failed request is not a refusal", vm.uiState.value.refusal)
            assertEquals("hunter2hunter2", vm.passwordState.text.toString())

            repository.failure = null
            vm.retry()
            advanceUntilIdle()

            assertEquals(SignInCredentials("nssk", "hunter2hunter2", "turnstile-token"), repository.sent)
            assertTrue(vm.uiState.value.signedIn)
        }

    @Test
    fun `native error codes remain exportable after the snackbar without exporting the form`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(
                failure = SiteException(
                    SiteError.Network,
                    // Exception messages are deliberately excluded even if a platform puts a URL
                    // or request context in one. Only the separate numeric diagnostic is exported.
                    detail = "private-message with nssk and hunter2hunter2",
                    diagnostics = TransportFailureDiagnostics(
                        "iOS test",
                        listOf(NativeNetworkError(NativeNetworkErrorDomain.URL, -1200)),
                    ),
                ),
                jar = cookies,
            )
            val vm = readyViewModel(repository)
            vm.submitCredentials()
            advanceUntilIdle()

            val report = requireNotNull(vm.uiState.value.failureReport)
            assertTrue(report.contains("NSURLErrorDomain (-1200)"))
            assertTrue(report.contains("Verification ready: true"))
            listOf("private-message", "nssk", "hunter2hunter2", "turnstile-token").forEach {
                assertFalse(it, report.contains(it))
            }
            vm.consumeFailure()
            assertNull(vm.uiState.value.failure)
            assertEquals(report, vm.uiState.value.failureReport)

            repository.failure = null
            vm.retry()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.signedIn)
            assertNull(vm.uiState.value.failureReport)
        }

    @Test
    fun `an unresponsive diagnostic context keeps the version and does not prevent retry`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(
                failure = SiteException(
                    SiteError.Network,
                    diagnostics = TransportFailureDiagnostics(
                        "iOS test",
                        listOf(NativeNetworkError(NativeNetworkErrorDomain.URL, -1200)),
                    ),
                ),
                jar = cookies,
            )
            val vm = viewModels.track(
                SignInViewModel(
                    signIn = repository,
                    session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies)),
                    clock = AppClock { FIXED_MILLIS },
                    appVersion = AppVersion("1.3.4-test", 85),
                    diagnosticContext = { awaitCancellation() },
                ),
            )
            vm.accountState.typeText("nssk")
            vm.passwordState.typeText("hunter2hunter2")
            runCurrent()
            vm.onVerified("turnstile-token")
            vm.submitCredentials()
            advanceUntilIdle()
            assertTrue(requireNotNull(vm.uiState.value.failureReport).contains("App: 1.3.4-test (85)"))

            repository.failure = null
            vm.retry()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.signedIn)
        }

    private companion object {
        /** 1970-01-01T00:00:12Z — twelve seconds into a 30-second TOTP window, so 18 are left. */
        const val FIXED_MILLIS = 12_000L
    }

    @Test
    fun `an email redirect asks for email verification instead of blaming cookie storage`() = runTest(dispatcher) {
        val repository = FakeSignInRepository(outcome = SignInOutcome.EmailRequired(EmailSignInChallenge("reader@example.com")))
        val vm = readyViewModel(repository)
        vm.submitCredentials()
        advanceUntilIdle()
        assertEquals(SignInStep.Email, vm.uiState.value.step)
        assertEquals("reader@example.com", vm.emailState.text.toString())
        assertFalse(vm.uiState.value.signedIn)
        assertFalse(vm.uiState.value.sessionNotStored)
        assertFalse("The password attempt spent its Turnstile token", vm.uiState.value.isVerified)
    }

    @Test
    fun `email sends are deduplicated and cannot bypass the cooldown with another proof`() = runTest(dispatcher) {
        val repository = FakeSignInRepository(jar = cookies)
        val vm = viewModel(repository, AppClock { FIXED_MILLIS + testScheduler.currentTime })
        vm.useEmailSignIn()
        vm.emailState.typeText("reader@example.com")
        runCurrent()
        vm.onVerified("proof-1")
        vm.requestEmailCode()
        vm.requestEmailCode()
        advanceUntilIdle()
        assertEquals(listOf("reader@example.com" to "proof-1"), repository.emailRequests)
        vm.onVerified("proof-2")
        vm.requestEmailCode()
        advanceUntilIdle()
        assertEquals(1, repository.emailRequests.size)
        advanceTimeBy(60_000)
        vm.requestEmailCode()
        advanceUntilIdle()
        assertEquals(listOf("reader@example.com" to "proof-1", "reader@example.com" to "proof-2"), repository.emailRequests)
    }

    @Test
    fun `changing the email clears the previous address code before it can be submitted`() = runTest(dispatcher) {
        val repository = FakeSignInRepository(jar = cookies)
        val vm = viewModel(repository)
        vm.useEmailSignIn()
        vm.emailState.typeText("reader@example.com")
        runCurrent()
        vm.onVerified("proof")
        vm.requestEmailCode()
        advanceUntilIdle()
        vm.emailCodeState.typeText("123456")
        vm.emailState.typeText("other@example.com")
        runCurrent()
        vm.submitEmailCode()
        advanceUntilIdle()
        assertNull(repository.emailVerification)
        assertFalse(vm.uiState.value.canSubmitEmailCode)
    }

    @Test
    fun `a code already received on the website can be submitted without sending another email`() = runTest(dispatcher) {
        val repository = FakeSignInRepository(jar = cookies)
        val vm = viewModel(repository)
        vm.useEmailSignIn()
        vm.emailState.typeText("reader@example.com")
        runCurrent()
        vm.emailCodeState.typeText("0123456789abcdef01234567")
        runCurrent()
        vm.submitEmailCode()
        advanceUntilIdle()
        assertTrue(repository.emailRequests.isEmpty())
        assertEquals("reader@example.com" to "0123456789abcdef01234567", repository.emailVerification)
        assertTrue(vm.uiState.value.signedIn)
    }

    @Test
    fun `a wrong email code stays on the email step and a retry can establish the session`() = runTest(dispatcher) {
        val repository = FakeSignInRepository(outcome = SignInOutcome.Refused(SignInRefusal.EmailCode, "invalid code"), jar = cookies)
        val vm = viewModel(repository)
        vm.useEmailSignIn()
        vm.emailState.typeText("reader@example.com")
        runCurrent()
        vm.onVerified("proof")
        vm.requestEmailCode()
        advanceUntilIdle()
        vm.emailCodeState.typeText("123456")
        runCurrent()
        vm.submitEmailCode()
        advanceUntilIdle()
        assertEquals(SignInStep.Email, vm.uiState.value.step)
        assertEquals(SignInRefusal.EmailCode, vm.uiState.value.refusal?.reason)
        assertFalse(vm.uiState.value.signedIn)
        repository.outcome = SignInOutcome.Signed
        vm.retry()
        advanceUntilIdle()
        assertEquals("reader@example.com" to "123456", repository.emailVerification)
        assertTrue(vm.uiState.value.signedIn)
    }
}

/**
 * @param jar the store the endpoint writes its `Set-Cookie` into, which is the same one the
 *   [SessionRepository] under test reads. Passing it is what makes [SignInOutcome.Signed] mean what
 *   it means on the wire — the endpoint's word *plus* a session in the shared jar — and leaving it
 *   null is the case the ViewModel now has to notice: a `success` that stored nothing.
 */
private class FakeSignInRepository(
    var outcome: SignInOutcome = SignInOutcome.Signed,
    var failure: Throwable? = null,
    var jar: FakeSessionCookieStore? = null,
    private val writeDelayMillis: Long = 0L,
) : SignInRepository {

    var sent: SignInCredentials? = null
        private set
    var verified: Pair<TwoFactorChallenge, String>? = null
        private set
    val emailRequests = mutableListOf<Pair<String, String>>()
    var emailVerification: Pair<String, String>? = null

    override suspend fun requestEmailCode(email: String, verificationToken: String): EmailCodeOutcome {
        emailRequests += email to verificationToken
        failure?.let { throw it }
        return EmailCodeOutcome.Sent
    }

    override suspend fun verifyEmail(email: String, code: String): SignInOutcome {
        emailVerification = email to code
        failure?.let { throw it }
        return outcome.alsoStoreSession()
    }

    override suspend fun signIn(credentials: SignInCredentials): SignInOutcome {
        sent = credentials
        failure?.let { throw it }
        return outcome.alsoStoreSession()
    }

    override suspend fun verifyTwoFactor(challenge: TwoFactorChallenge, code: String): SignInOutcome {
        verified = challenge to code
        failure?.let { throw it }
        return outcome.alsoStoreSession()
    }

    private suspend fun SignInOutcome.alsoStoreSession(): SignInOutcome = also {
        if (it != SignInOutcome.Signed) return@also
        val jar = jar ?: return@also
        // A store that takes its time, which is what Android's does: the write is handed over here
        // and becomes readable later. Zero means "already readable", which is every other test.
        if (writeDelayMillis == 0L) {
            jar.setCookie(NodeSeekSite.BASE_URL, "session=granted; Path=/")
        } else {
            // Launched, not awaited: the endpoint answers and the write finishes on its own, which
            // is the whole shape of the race. A child of the caller's job, so the test's scheduler
            // runs it.
            CoroutineScope(currentCoroutineContext()).launch {
                delay(writeDelayMillis)
                jar.setCookie(NodeSeekSite.BASE_URL, "session=granted; Path=/")
            }
        }
    }
}
