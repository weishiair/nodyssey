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
            displayedFloor = rememberStableReadingFloor(currentFloor, loading, positionReady = true)
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

    @Test
    fun `a completed page request keeps its floor through layout and scroll restoration`() {
        var currentFloor by mutableIntStateOf(20)
        var loading by mutableStateOf(false)
        var positionReady by mutableStateOf(true)
        var displayedFloor = 0
        compose.setContent {
            displayedFloor = rememberStableReadingFloor(currentFloor, loading, positionReady)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            loading = true
            positionReady = false
            currentFloor = 0
        }
        compose.waitForIdle()
        assertEquals(20, displayedFloor)

        // The request returns before the replacement page and its scroll target have been placed.
        compose.runOnIdle { loading = false }
        compose.waitForIdle()
        assertEquals(20, displayedFloor)
        compose.runOnIdle { currentFloor = 121 }
        compose.waitForIdle()
        assertEquals(20, displayedFloor)
        compose.runOnIdle {
            currentFloor = 127
            positionReady = true
        }
        compose.waitForIdle()
        assertEquals(127, displayedFloor)

        // An intentional return to the opening post must still be allowed to display floor zero.
        compose.runOnIdle { currentFloor = 0 }
        compose.waitForIdle()
        assertEquals(0, displayedFloor)
    }
}
