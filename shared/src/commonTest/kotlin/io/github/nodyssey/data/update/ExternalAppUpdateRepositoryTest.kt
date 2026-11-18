@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.nodyssey.data.update

import io.github.plaza.core.AppClock
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateException
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateCheckRecord
import io.github.plaza.core.update.UpdateFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExternalAppUpdateRepositoryTest {
    private val store = Store()
    private var now = 1_000_000L
    private var calls = 0
    private var latest = release("1.3.12")

    @Test
    fun `switching channels cancels an in-flight answer from the previous channel`() = runTest {
        val oldResponse = CompletableDeferred<List<AppRelease>>()
        val testRelease = release("1.3.13-ios-test.1")
        val updates = repository(load = { dev -> if (dev) listOf(testRelease) else oldResponse.await() })
        updates.check()
        runCurrent()
        store.dev = true
        updates.check(force = true)
        runCurrent()
        assertEquals(testRelease, updates.state.value.available)
        assertEquals(true, store.record.devChannel)
        oldResponse.complete(listOf(latest))
        runCurrent()
        assertEquals(testRelease, updates.state.value.available)
    }

    @Test
    fun `opening about does not prompt but a launch using the fresh record does`() = runTest {
        val updates = repository()
        updates.check()
        runCurrent()
        assertEquals(latest, updates.state.value.available)
        assertNull(updates.launchReminder.value)
        updates.checkOnLaunch()
        runCurrent()
        assertEquals(latest, updates.launchReminder.value)
        assertEquals(1, calls)
    }

    @Test
    fun `postponing survives a new process while manual checking keeps the update available`() = runTest {
        val first = repository()
        first.checkOnLaunch()
        runCurrent()
        first.postponeLaunchReminder()
        runCurrent()
        val next = repository()
        next.checkOnLaunch()
        runCurrent()
        assertNull(next.launchReminder.value)
        next.check(force = true)
        runCurrent()
        assertEquals(latest, next.state.value.available)
        assertNull(next.launchReminder.value)
        latest = release("1.3.13")
        now += ExternalAppUpdateRepository.CHECK_INTERVAL_MILLIS
        next.checkOnLaunch()
        runCurrent()
        assertEquals(latest, next.launchReminder.value)
    }

    @Test
    fun `installing the cached version or a newer test build does not offer a downgrade`() = runTest {
        store.record = UpdateCheckRecord(now, latest, sourceId = "ios")
        for (current in listOf("1.3.12", "1.3.13-ios-test.1")) {
            val updates = repository(current)
            updates.checkOnLaunch()
            runCurrent()
            assertIs<UpdateCheck.UpToDate>(updates.state.value.check)
            assertNull(updates.launchReminder.value)
        }
        assertEquals(0, calls)
    }

    @Test
    fun `manual checks bypass six-hour caching and changing channels invalidates a fresh answer`() = runTest {
        val updates = repository()
        updates.checkOnLaunch()
        runCurrent()
        updates.check()
        runCurrent()
        assertEquals(1, calls)
        updates.check(force = true)
        runCurrent()
        assertEquals(2, calls)
        store.dev = true
        updates.check()
        runCurrent()
        assertEquals(3, calls)
        assertEquals(true, store.record.devChannel)
        now += ExternalAppUpdateRepository.CHECK_INTERVAL_MILLIS
        updates.check()
        runCurrent()
        assertEquals(4, calls)
    }

    @Test
    fun `a cached APK from another source cannot become an iOS reminder`() = runTest {
        store.record = UpdateCheckRecord(now, release("9.0.0"), sourceId = "android")
        val updates = repository()
        updates.checkOnLaunch()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(latest, updates.launchReminder.value)
    }

    @Test
    fun `failed launch checks stay silent and allow manual recovery`() = runTest {
        var failing = true
        val updates = repository(load = {
            if (failing) throw AppUpdateException(UpdateFailure.Network)
            listOf(latest)
        })
        updates.checkOnLaunch()
        runCurrent()
        assertEquals(UpdateCheck.Failed(UpdateFailure.Network), updates.state.value.check)
        assertNull(updates.launchReminder.value)
        failing = false
        updates.check(force = true)
        runCurrent()
        assertEquals(latest, updates.state.value.available)
        assertNull(updates.launchReminder.value)
    }

    @Test
    fun `a launch joining an in-flight manual check still raises its reminder`() = runTest {
        val response = CompletableDeferred<List<AppRelease>>()
        val updates = repository(load = {
            calls++
            response.await()
        })
        updates.check()
        runCurrent()
        updates.checkOnLaunch()
        response.complete(listOf(latest))
        runCurrent()
        assertEquals(1, calls)
        assertEquals(latest, updates.launchReminder.value)
    }

    private fun TestScope.repository(
        current: String = "1.3.11",
        load: suspend (Boolean) -> List<AppRelease> = {
            calls++
            listOf(latest)
        },
    ) = ExternalAppUpdateRepository(load, store, AppClock { now }, backgroundScope, current, "ios")

    private class Store : UpdateCheckStore {
        var record = UpdateCheckRecord()
        var dev = false
        var postponed: String? = null

        override suspend fun updateCheckRecord() = record

        override suspend fun setUpdateCheckRecord(record: UpdateCheckRecord) {
            this.record = record
        }

        override suspend fun devChannelEnabled() = dev

        override suspend fun postponedUpdateVersion() = postponed

        override suspend fun setPostponedUpdateVersion(versionName: String) {
            postponed = versionName
        }
    }

    private companion object {
        fun release(version: String) = AppRelease(version, "v$version", "Notes", "https://example.invalid/$version.ipa", "$version.ipa", 12345, "https://example.invalid/releases/v$version")
    }
}
