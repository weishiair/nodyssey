@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/** One process-lifetime monitor for Coil, matching Android's available, unmetered network policy. */
internal class IosImageNetworkMonitor {
    private val unmetered = MutableStateFlow<Boolean?>(null)
    private val monitor = nw_path_monitor_create()

    init {
        val monitor = monitor
        if (monitor == null) {
            unmetered.value = false
        } else {
            nw_path_monitor_set_update_handler(monitor) { path ->
                unmetered.value = path != null && nw_path_get_status(path) == nw_path_status_satisfied && !nw_path_is_expensive(path)
            }
            nw_path_monitor_set_queue(monitor, dispatch_queue_create("io.github.nodyssey.images.network", null))
            nw_path_monitor_start(monitor)
        }
    }

    suspend fun hasUnmeteredNetwork(): Boolean = unmetered.filterNotNull().first()
}
