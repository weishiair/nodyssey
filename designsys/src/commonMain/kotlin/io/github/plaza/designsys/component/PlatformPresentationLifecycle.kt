package io.github.plaza.designsys.component

/** Serializes closing with UIKit's asynchronous presentation transition. */
internal class PlatformPresentationLifecycle(
    private val present: (completed: () -> Unit) -> Unit,
    private val dismiss: (completed: () -> Unit) -> Unit,
    private val onSystemDismissed: () -> Unit,
) {
    private enum class Phase { WAITING, PRESENTING, VISIBLE, DISMISSING, CLOSED }
    private var phase = Phase.WAITING
    private var disposed = false

    fun show() {
        if (phase != Phase.WAITING || disposed) return
        phase = Phase.PRESENTING
        present {
            if (phase == Phase.PRESENTING) {
                phase = Phase.VISIBLE
                if (disposed) close()
            }
        }
    }

    fun dispose() {
        disposed = true
        when (phase) {
            Phase.WAITING -> phase = Phase.CLOSED
            Phase.VISIBLE -> close()
            else -> Unit
        }
    }

    fun systemDismissed() {
        val notify = !disposed && phase != Phase.CLOSED
        phase = Phase.CLOSED
        if (notify) onSystemDismissed()
    }

    private fun close() {
        phase = Phase.DISMISSING
        dismiss { phase = Phase.CLOSED }
    }
}
