package io.github.nodyssey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import io.github.nodyssey.data.session.AccountRepository
import io.github.nodyssey.data.session.AccountsState
import io.github.nodyssey.di.AppContainer

/** The account owns its entire navigation state, including hidden tabs' ViewModels. */
@Composable
internal fun AccountBoundary(container: AppContainer, content: @Composable () -> Unit) {
    val accounts = container.accountRepository
    if (accounts == null) {
        content()
        return
    }
    AccountBoundary(accounts, placeholder = { AccountSwitchPlaceholder() }, content)
}

@Composable
internal fun AccountBoundary(accounts: AccountRepository, placeholder: @Composable () -> Unit, content: @Composable () -> Unit) {
    val state by accounts.state.collectAsStateWithLifecycle()
    val stores: AccountNavigationStore = viewModel { AccountNavigationStore() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The repository waits for detach before replacing cookies. Only animate the overlay.
        if (state.ready && !state.busy) {
            key(state.navigationKey, state.adding, state.revision) {
                val owner = stores.owner(Triple(state.navigationKey, state.adding, state.revision))
                DisposableEffect(accounts, owner) {
                    val detach = accounts.attachUi { owner.viewModelStore.clear() }
                    onDispose { detach() }
                }
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
            }
        }
        AnimatedVisibility(
            visible = !state.ready || state.busy,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) { placeholder() }
    }
}

internal fun accountProfileInitialKeys(state: AccountsState?): List<NavKey> = when {
    state?.adding == true -> listOf(ProfileKey, AccountsKey, SignInKey)
    state?.failure != null || (state != null && state.revision > 0 && state.activeUid == null && state.accounts.isNotEmpty()) -> listOf(ProfileKey, AccountsKey)
    else -> listOf(ProfileKey)
}

internal fun shouldOpenProfileTabOnAccountChange(state: AccountsState?): Boolean =
    state?.adding == true || state?.failure != null || (state?.revision ?: 0) > 0

private class AccountNavigationStore : ViewModel() {
    private var identity: Any? = null
    private var current: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    fun owner(identity: Any): ViewModelStoreOwner {
        if (this.identity != identity) {
            current.viewModelStore.clear()
            current = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
            this.identity = identity
        }
        return current
    }
    override fun onCleared() {
        current.viewModelStore.clear()
    }
}
