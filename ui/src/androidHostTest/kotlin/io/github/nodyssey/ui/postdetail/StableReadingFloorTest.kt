package io.github.nodyssey.ui.postdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StableReadingFloorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the capsule keeps its floor until loading has completed`() {
        var currentFloor by mutableIntStateOf(20)
        var loading by mutableStateOf(false)
        var displayedFloor = 0
        compose.setContent {
            displayedFloor = rememberStableReadingFloor(currentFloor, loading)
        }
        compose.waitForIdle()
        assertEquals(20, displayedFloor)

        compose.runOnIdle {
            loading = true
            currentFloor = 34
        }
        compose.waitForIdle()
        assertEquals(20, displayedFloor)

        compose.runOnIdle { currentFloor = 40 }
        compose.waitForIdle()
        assertEquals(20, displayedFloor)

        compose.runOnIdle { loading = false }
        compose.waitForIdle()
        assertEquals(40, displayedFloor)
    }
}
