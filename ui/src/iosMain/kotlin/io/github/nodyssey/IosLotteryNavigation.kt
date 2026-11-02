package io.github.nodyssey

import io.github.nodyssey.core.NodeSeekSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retains a notification tap until the asynchronous native shell has finished starting. */
object IosLotteryNavigation {
    private val mutableRequest = MutableStateFlow<Request?>(null)
    val requests = mutableRequest.asStateFlow()

    fun open(postId: Long, origin: String) {
        if (postId <= 0 || origin != NodeSeekSite.BASE_URL) return
        mutableRequest.value = Request(postId)
    }

    fun handled(request: Request) {
        mutableRequest.compareAndSet(request, null)
    }

    data class Request(val postId: Long)
}
