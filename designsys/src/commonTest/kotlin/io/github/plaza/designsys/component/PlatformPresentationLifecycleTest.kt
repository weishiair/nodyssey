package io.github.plaza.designsys.component

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformPresentationLifecycleTest {
    @Test
    fun `closing during presentation waits for UIKit before dismissing`() {
        var finishPresentation: () -> Unit = {}
        var dismissals = 0
        var callbacks = 0
        val lifecycle = PlatformPresentationLifecycle(
            present = { finishPresentation = it },
            dismiss = { done ->
                dismissals++
                done()
            },
            onSystemDismissed = { callbacks++ },
        )
        lifecycle.show()
        lifecycle.dispose()
        assertEquals(0, dismissals)
        finishPresentation()
        lifecycle.dispose()
        lifecycle.systemDismissed()
        assertEquals(1, dismissals)
        assertEquals(0, callbacks)
    }

    @Test
    fun `interactive dismissal reports once and does not dismiss twice on disposal`() {
        var callbacks = 0
        var dismissals = 0
        val lifecycle = PlatformPresentationLifecycle(
            present = { it() },
            dismiss = {
                dismissals++
                it()
            },
            onSystemDismissed = { callbacks++ },
        )
        lifecycle.show()
        lifecycle.systemDismissed()
        lifecycle.dispose()
        lifecycle.systemDismissed()
        assertEquals(1, callbacks)
        assertEquals(0, dismissals)
    }
}
