package io.github.nodyssey

import io.github.nodyssey.core.Site
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

data class SiteSwitchState<T>(val graph: T, val switching: Boolean = false, val failed: Boolean = false)

/** Main-thread coordinator: remove old screens before retiring their graph and changing the site. */
class SiteSwitchController<T : Any>(
    initial: T,
    private val siteOf: (T) -> Site,
    private val install: (Site) -> Unit,
    private val build: suspend () -> T,
    private val retire: suspend (T) -> Unit,
    private val store: (Site) -> Boolean,
) {
    private val mutableState = MutableStateFlow(SiteSwitchState(initial))
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val attached = mutableMapOf<Any, T>()
    private var detached: CompletableDeferred<Unit>? = null

    /** The release callback clears Activity-owned ViewModels only during a site switch, not rotation. */
    fun attachUi(graph: T, release: () -> Unit): () -> Unit {
        val token = Any()
        attached[token] = graph
        return {
            if (mutableState.value.switching && mutableState.value.graph === graph) release()
            attached.remove(token)
            if (attached.values.none { it === graph }) detached?.complete(Unit)
        }
    }

    suspend fun switch(site: Site) {
        if (!mutex.tryLock()) return
        val old = mutableState.value.graph
        val oldSite = siteOf(old)
        var next: T? = null
        var retired = false
        try {
            if (site == oldSite) return
            val disposed = CompletableDeferred<Unit>()
            detached = disposed
            mutableState.value = SiteSwitchState(old, switching = true)
            if (attached.values.any { it === old }) disposed.await()
            retire(old)
            retired = true
            install(site)
            next = build()
            check(store(site)) { "Cannot persist site choice" }
            mutableState.value = SiteSwitchState(next)
        } catch (failure: Exception) {
            // Rebuild over the same files instead of reviving a stopped graph. Complete recovery
            // even on cancellation so the screen cannot be left with a dead graph.
            withContext(NonCancellable) {
                // Cancellation can arrive while the old screens are still detaching. Complete
                // their teardown before replacing a graph they could otherwise keep using.
                if (attached.values.any { it === old }) detached?.await()
                if (!retired) retire(old)
                next?.let { retire(it) }
                install(oldSite)
                val restored = build()
                store(oldSite)
                mutableState.value = SiteSwitchState(restored, failed = true)
            }
            if (failure is CancellationException) throw failure
        } finally {
            detached = null
            mutex.unlock()
        }
    }
}
