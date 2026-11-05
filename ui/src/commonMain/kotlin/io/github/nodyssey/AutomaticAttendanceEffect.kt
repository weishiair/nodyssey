package io.github.nodyssey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

/** Also installed in each iOS native screen's chrome; the graph's runner serializes callers. */
@Composable
internal fun AutomaticAttendanceEffect(container: AppContainer) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(container, lifecycle) {
        val runner = container.automaticAttendance ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            combine(
                runner.enabled,
                container.sessionRepository.state,
                container.accountRepository?.state ?: kotlinx.coroutines.flow.flowOf(null),
            ) { enabled, session, accounts -> Triple(enabled, session, accounts) }
                .collect { runner.request() }
        }
    }
}
