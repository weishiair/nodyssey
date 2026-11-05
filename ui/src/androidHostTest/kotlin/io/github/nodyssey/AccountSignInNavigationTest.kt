package io.github.nodyssey

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.NoOpPostRepository
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.security.SecretCipher
import io.github.nodyssey.data.session.AccountCookieStore
import io.github.nodyssey.data.session.AccountLocalData
import io.github.nodyssey.data.session.AccountRepository
import io.github.nodyssey.data.session.AccountRequestGate
import io.github.nodyssey.data.session.FakeSessionCookieStore
import io.github.nodyssey.data.session.SavedAccountStore
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.ui.account.AccountsRoute
import io.github.nodyssey.ui.account.AccountsViewModel
import io.github.plaza.core.net.SessionCookies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountSignInNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val jar = FakeSessionCookieStore()
    private val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, jar))
    private val cookies = object : AccountCookieStore {
        override suspend fun capture() = jar.cookieHeader(NodeSeekSite.BASE_URL).orEmpty()
        override suspend fun replace(snapshot: String) {
            jar.removeAll()
            if (snapshot.isNotEmpty()) jar.setCookie(NodeSeekSite.BASE_URL, snapshot)
        }
    }
    private val profile = UserProfile(1, "original", "")
    private val profiles = object : ProfileRepository {
        override suspend fun profile(refresh: Boolean) = profile
        override suspend fun profile(uid: Long) = profile
    }
    private val offline = object : OfflineLibrary {
        override val isAvailable = true
        override val states = flowOf(emptyMap<Long, OfflineState>())
        override val usage = flowOf(OfflineUsage())
        override val settings = flowOf(OfflineSettings())
        override suspend fun download(postIds: Collection<Long>) = Unit
        override suspend fun noteReplyCounts(counts: Map<Long, Int>) = Unit
        override suspend fun estimateBytes(postIds: Collection<Long>): Long? = null
        override suspend fun cancel(postId: Long) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun updateSettings(settings: OfflineSettings) = Unit
    }
    private val cipher = object : SecretCipher {
        override fun encrypt(plaintext: String) = plaintext
        override fun decrypt(stored: String) = stored
    }
    private val accounts = AccountRepository(
        SavedAccountStore(MemoryPreferences(), cipher, "test"),
        cookies,
        session,
        AccountRequestGate(),
        AccountLocalData(MemoryPreferences(), MemoryPreferences(), NoOpPostRepository(), profiles, offline, SettingsRepository(MemoryPreferences())),
        currentProfile = { profile },
        stopPlatformRequests = {},
        scope = scope,
    )

    @After fun close() = scope.cancel()

    // Bug: rebuilding account navigation or a cookie-only verification update abandons an untouched login.
    @Test fun `adding from the picker stays on sign in while verification loads without a back gesture`() {
        runBlocking {
            accounts.awaitReady()
            cookies.replace("session=original")
            session.sync()
            accounts.remember(profile, session.peek().fingerprint)
        }
        var currentStack: NavBackStack<NavKey>? = null
        compose.setContent {
            MaterialTheme {
                AccountBoundary(accounts, placeholder = {}) {
                    val stack = rememberAccountProfileStack(accounts)
                    SideEffect { currentStack = stack }
                    val entries = rememberDecoratedNavEntries(
                        backStack = stack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = { key ->
                            NavEntry(key) {
                                if (key == AccountsKey) {
                                    val model = viewModel { AccountsViewModel(accounts, offline) }
                                    AccountsRoute(model, onBack = {})
                                } else {
                                    Text(key.toString())
                                }
                            }
                        },
                    )
                    val currentSession by session.state.collectAsStateWithLifecycle()
                    androidx.compose.runtime.LaunchedEffect(currentSession.isSignedIn, currentSession.fingerprint) {
                        if (currentSession.isSignedIn) accounts.remember(profile, currentSession.fingerprint)
                    }
                    NodysseyNavDisplay(entries, predictiveBack = true, eink = false, onBack = { stack.removeLastOrNull() })
                }
            }
        }
        compose.runOnIdle { checkNotNull(currentStack).add(AccountsKey) }
        compose.runOnIdle { accounts.add() }
        compose.waitUntil { accounts.state.value.adding && !accounts.state.value.busy }
        compose.runOnIdle {
            assertEquals(SignInKey, currentStack?.last())
            assertFalse(session.peek().isSignedIn)
        }
        // Cloudflare can change its own cookies while the form is untouched.
        compose.runOnIdle {
            jar.setCookie(NodeSeekSite.BASE_URL, "cf_clearance=verified")
            session.sync()
            accounts.refresh()
        }
        compose.mainClock.advanceTimeBy(30_000)
        compose.runOnIdle {
            assertEquals(SignInKey, currentStack?.last())
            assertTrue(accounts.state.value.adding)
            assertFalse(session.peek().isSignedIn)
        }
    }

    // Bug: composing the account picker behind sign-in cancels the pending add and restores old cookies.
    @Test fun `previewing back while verification is pending keeps the additional login alive`() {
        runBlocking {
            accounts.awaitReady()
            cookies.replace("session=original")
            session.sync()
            accounts.remember(profile, session.peek().fingerprint)
            accounts.add()
        }
        assertTrue(accounts.state.value.adding)
        val stack = mutableStateListOf<NavKey>(AccountsKey, SignInKey)
        val input = BackInput()
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher().apply { addInput(input) }
        }
        val model = AccountsViewModel(accounts, offline)
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaterialTheme {
                    val entries = rememberDecoratedNavEntries(
                        backStack = stack,
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        entryProvider = { key ->
                            NavEntry(key) {
                                if (key == AccountsKey) AccountsRoute(model, onBack = {}) else Text("Verification pending")
                            }
                        },
                    )
                    NodysseyNavDisplay(entries, predictiveBack = true, eink = false, onBack = { stack.removeLastOrNull() })
                }
            }
        }
        compose.runOnIdle { assertTrue(accounts.state.value.adding) }
        compose.runOnIdle { input.preview() }
        compose.waitForIdle()
        compose.runOnIdle { input.cancel() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(SignInKey, stack.last())
            assertTrue(accounts.state.value.adding)
            assertFalse(session.peek().isSignedIn)
        }
        // An actual exit still restores the original account.
        compose.runOnIdle { accounts.cancelAdd() }
        compose.runOnIdle {
            assertFalse(accounts.state.value.adding)
            assertEquals(1L, accounts.state.value.activeUid)
            assertTrue(session.peek().isSignedIn)
        }
    }

    private class BackInput : NavigationEventInput() {
        fun preview() {
            dispatchOnBackStarted(NavigationEvent(progress = 0f, swipeEdge = NavigationEvent.EDGE_LEFT))
            dispatchOnBackProgressed(NavigationEvent(progress = 0.5f, swipeEdge = NavigationEvent.EDGE_LEFT))
        }
        fun cancel() = dispatchOnBackCancelled()
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
    }
}
