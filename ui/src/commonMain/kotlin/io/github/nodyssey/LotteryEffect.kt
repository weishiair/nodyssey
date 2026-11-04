package io.github.nodyssey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Each iOS screen has its own composition; the repository serializes overlapping checks. */
@Composable
internal fun LotteryEffect(container: AppContainer) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(container, lifecycle) {
        val repository = container.lotteryRepository ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch {
                // A screen ViewModel reports store errors; this scheduler must not crash the app.
                repository.entries.catch { }.collect {
                    runCatchingExceptCancellation { repository.synchronize() }
                }
            }
            while (true) {
                runCatchingExceptCancellation { repository.refreshDue() }
                delay(60_000)
            }
        }
    }
}
