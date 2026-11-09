package io.github.nodyssey.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.attendance.AutoAttendanceState
import io.github.nodyssey.data.attendance.AutomaticAttendance
import io.github.nodyssey.data.session.AccountRepository
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AccountsViewModel(private val accounts: AccountRepository, offline: OfflineLibrary, attendance: AutomaticAttendance? = null) : ViewModel() {
    val state = accounts.state
    val usage = offline.usage
    val attendanceStates = attendance?.accountStates ?: MutableStateFlow<Map<Long, AutoAttendanceState>>(emptyMap())
    fun setAttendanceEnabled(uid: Long, enabled: Boolean) {
        viewModelScope.launch { accounts.configureAttendance(uid) { it.copy(enabled = enabled) } }
    }
    fun refresh() = accounts.refresh()
    fun add() = accounts.add()
    fun switch(uid: Long) = accounts.switch(uid)
    fun remove(uid: Long) = accounts.remove(uid)
    fun cancelAdd() = accounts.cancelAdd()
    fun dismissFailure() = accounts.dismissFailure()

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AccountsViewModel(checkNotNull(container.accountRepository), container.offlineLibrary, container.automaticAttendance) }
        }
    }
}
