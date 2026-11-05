package io.github.nodyssey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nodyssey.data.session.AccountRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.di.AppContainer

/** The account owns its entire navigation state, including hidden tabs' ViewModels. */
@Composable
internal fun AccountBoundary(container: AppContainer, initialSettings: UserSettings?, content: @Composable () -> Unit) {
    val accounts = container.accountRepository
    if (accounts == null) {
        content()
        return
    }
    AccountBoundary(accounts, placeholder = { NodysseySiteSwitchPlaceholder(container, initialSettings) }, content)
}

@Composable
internal fun AccountBoundary(accounts: AccountRepository, placeholder: @Composable () -> Unit, content: @Composable () -> Unit) {
    val state by accounts.state.collectAsStateWithLifecycle()
    val stores: AccountNavigationStore = viewModel { AccountNavigationStore() }
    if (!state.ready || state.busy) {
        placeholder()
    } else {
        key(state.navigationKey, state.adding, state.revision) {
            val owner = stores.owner(Triple(state.navigationKey, state.adding, state.revision))
            DisposableEffect(accounts, owner) {
                val detach = accounts.attachUi { owner.viewModelStore.clear() }
                onDispose { detach() }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
        }
    }
}

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
