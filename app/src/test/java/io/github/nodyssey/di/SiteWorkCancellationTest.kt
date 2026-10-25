package io.github.nodyssey.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SiteWorkCancellationTest {
    @Test
    fun `retiring a graph waits for background work cleanup and rejects work scheduled afterward`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val graph = DefaultAppContainer(context, AppDispatchers(io = dispatcher, default = dispatcher))
        val started = CompletableDeferred<Unit>()
        var cleaned = false
        val work = async {
            runCatching {
                graph.runSiteWork {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cleaned = true
                    }
                }
            }
        }
        runCurrent()
        started.await()
        graph.shutdown()
        assertTrue(cleaned)
        assertFalse(graph.isActive)
        assertTrue(work.await().exceptionOrNull() is CancellationException)
        var invoked = false
        val late = runCatching { graph.runSiteWork { invoked = true } }
        assertTrue(late.exceptionOrNull() is CancellationException)
        assertFalse(invoked)
    }
}
