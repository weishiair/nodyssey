package io.github.nodyssey

import io.github.nodyssey.core.Site
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSwitchControllerTest {
    private data class Graph(val site: Site, var stopped: Boolean = false)

    @Test
    fun `site changes only after screens release viewmodels and old work stops`() = runTest {
        var active = Site.NODESEEK
        val events = mutableListOf<String>()
        val initial = Graph(active)
        val controller = SiteSwitchController(
            initial,
            siteOf = { it.site },
            install = {
                events += "install"
                active = it
            },
            build = {
                events += "build"
                Graph(active)
            },
            retire = {
                events += "stop"
                it.stopped = true
            },
            store = {
                events += "store"
                true
            },
        )
        val detach = controller.attachUi(initial) { events += "clear viewmodels" }
        val switch = async { controller.switch(Site.DEEPFLOOD) }
        runCurrent()
        assertTrue(controller.state.value.switching)
        assertEquals(Site.NODESEEK, active)
        assertFalse(initial.stopped)
        // A second tap while disposal is pending must not build a second graph.
        controller.switch(Site.DEEPFLOOD)
        detach()
        switch.await()
        assertEquals(listOf("clear viewmodels", "stop", "install", "build", "store"), events)
        assertEquals(Site.DEEPFLOOD, controller.state.value.graph.site)
        assertFalse(controller.state.value.switching)
        assertTrue(initial.stopped)
    }

    @Test
    fun `returning to the original site creates a live graph instead of reviving its cancelled scope`() = runTest {
        var active = Site.NODESEEK
        val initial = Graph(active)
        val controller = SiteSwitchController(initial, { it.site }, { active = it }, { Graph(active) }, { it.stopped = true }, { true })
        controller.switch(Site.NODESEEK)
        assertFalse(initial.stopped)
        controller.switch(Site.DEEPFLOOD)
        val deepFlood = controller.state.value.graph
        controller.switch(Site.NODESEEK)
        assertNotSame(initial, controller.state.value.graph)
        assertFalse(controller.state.value.graph.stopped)
        assertTrue(deepFlood.stopped)
        assertEquals(Site.NODESEEK, active)
    }

    @Test
    fun `a failed new graph rebuilds the original site and restores its stored choice`() = runTest {
        var active = Site.NODESEEK
        var stored = active
        val initial = Graph(active)
        val controller = SiteSwitchController(
            initial,
            { it.site },
            { active = it },
            {
                check(active != Site.DEEPFLOOD)
                Graph(active)
            },
            { it.stopped = true },
            {
                stored = it
                true
            },
        )
        controller.switch(Site.DEEPFLOOD)
        assertEquals(Site.NODESEEK, active)
        assertEquals(Site.NODESEEK, stored)
        assertEquals(Site.NODESEEK, controller.state.value.graph.site)
        assertFalse(controller.state.value.graph.stopped)
        assertTrue(controller.state.value.failed)
        assertFalse(controller.state.value.switching)
    }

    @Test
    fun `persistence failure retires the uncommitted graph and returns to the old site`() = runTest {
        var active = Site.NODESEEK
        val graphs = mutableListOf<Graph>()
        val controller = SiteSwitchController(
            Graph(active),
            { it.site },
            { active = it },
            { Graph(active).also { graphs += it } },
            { it.stopped = true },
            { it != Site.DEEPFLOOD },
        )
        controller.switch(Site.DEEPFLOOD)
        assertTrue(graphs.first().stopped)
        assertEquals(Site.NODESEEK, controller.state.value.graph.site)
        assertTrue(controller.state.value.failed)
    }

    @Test
    fun `cancelling during screen disposal finishes teardown and recovery without swallowing cancellation`() = runTest {
        var active = Site.NODESEEK
        val initial = Graph(active)
        val controller = SiteSwitchController(initial, { it.site }, { active = it }, { Graph(active) }, { it.stopped = true }, { true })
        val detach = controller.attachUi(initial) {}
        var continued = false
        val switch = launch {
            controller.switch(Site.DEEPFLOOD)
            continued = true
        }
        runCurrent()
        switch.cancel()
        runCurrent()
        assertTrue(controller.state.value.switching)
        assertEquals(Site.NODESEEK, active)
        assertFalse(initial.stopped)
        detach()
        switch.join()
        assertTrue(initial.stopped)
        assertNotSame(initial, controller.state.value.graph)
        assertFalse(controller.state.value.switching)
        assertTrue(controller.state.value.failed)
        assertFalse(continued)
    }

    @Test
    fun `ordinary activity recreation does not clear retained viewmodels`() {
        val initial = Graph(Site.NODESEEK)
        val controller = SiteSwitchController(initial, { it.site }, {}, { initial }, {}, { true })
        var cleared = false
        controller.attachUi(initial) { cleared = true }.invoke()
        assertFalse(cleared)
        assertFalse(initial.stopped)
    }
}
