package io.github.nodyssey.ui.lottery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.LuckyDraw
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryLimitException
import io.github.nodyssey.data.lottery.LotteryOutcome
import io.github.nodyssey.data.lottery.LotteryRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LotteryViewModel(
    private val repository: LotteryRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val mutableError = MutableStateFlow<SiteError?>(null)
    val error = mutableError.asStateFlow()
    val entries = repository.entries.catch { mutableError.value = it.toSiteError() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutablePending = MutableStateFlow<Set<String>>(emptySet())
    val pending = mutablePending.asStateFlow()
    private val mutableLimitReached = MutableStateFlow(false)
    val limitReached = mutableLimitReached.asStateFlow()
    private val mutableNow = MutableStateFlow(clock.nowMillis())
    val now = mutableNow.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                mutableNow.value = clock.nowMillis()
                delay(30_000)
            }
        }
    }

    fun draws(body: PostContent, postId: Long): List<LuckyDrawParams> = LuckyDraw.find(body.nodes, postId)

    fun act(draw: LuckyDrawParams, title: String) {
        perform(draw.key) {
            val entry = entries.value.firstOrNull { it.key == draw.key }
            when {
                entry != null && clock.nowMillis() < draw.drawAtMillis -> repository.remove(draw.key)
                entry != null -> repository.refreshDue(draw.key, force = true)
                else -> repository.watch(draw, title)
            }
        }
    }

    fun remove(entry: LotteryEntry) = perform(entry.key) { repository.remove(entry.key) }

    fun refresh() = perform("refresh") { repository.refreshDue(force = true) }

    fun markAllResultsRead() {
        val unread = entries.value.filter { it.outcome != null && !it.resultRead }
        if (unread.isEmpty()) return
        perform("readAll") { repository.markResultsRead(unread) }
    }

    fun markResultsRead(entries: List<LotteryEntry>) {
        val unread = entries.filter { it.outcome != null && !it.resultRead }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.markResultsRead(unread) }.onFailure {
                mutableError.value = it.toSiteError()
            }
        }
    }

    fun clearError() {
        mutableError.value = null
    }
    fun clearLimit() {
        mutableLimitReached.value = false
    }

    private fun perform(key: String, action: suspend () -> Unit) {
        if (key in mutablePending.value) return
        mutablePending.update { it + key }
        viewModelScope.launch {
            try {
                runCatchingExceptCancellation { action() }.onFailure {
                    if (it is LotteryLimitException) mutableLimitReached.value = true else mutableError.value = it.toSiteError()
                }
            } finally {
                mutablePending.update { it - key }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LotteryViewModel(requireNotNull(container.lotteryRepository), container.clock) }
        }
    }
}

internal fun lotteryResultsInDisplayOrder(entries: List<LotteryEntry>, outcome: LotteryOutcome? = null): List<LotteryEntry> =
    entries.filter { it.outcome != null && (outcome == null || it.outcome == outcome) }.sortedWith(
        compareBy<LotteryEntry> {
            when {
                !it.resultRead && it.outcome == LotteryOutcome.WON -> 0
                !it.resultRead -> 1
                else -> 2
            }
        }.thenByDescending { it.checkedAtMillis },
    )
