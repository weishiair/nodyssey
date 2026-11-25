package io.github.nodyssey.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalLiquidGlassBackdrop
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.rememberLiquidGlassBackdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomNavigationTabsTest {
    @get:Rule
    val compose = createComposeRule()

    private val selections = mutableListOf<TopLevelDestination>()

    private fun showGlassTabs() {
        compose.setContent {
            PlazaTheme(frostedGlass = true) {
                val backdrop = requireNotNull(rememberLiquidGlassBackdrop())
                CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(56.dp).testTag("bottom-tabs")) {
                            BottomNavigationTabs(TopLevelDestination.HOME, { selections.add(it) }, unreadCount = 0)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `tapping the glass lens reselects the current tab once`() {
        showGlassTabs()

        compose.onNodeWithText("首页").performTouchInput {
            down(center)
            moveTo(center + Offset(1f, 1f))
            up()
        }
        compose.onNodeWithText("通知").performTouchInput { click() }

        compose.runOnIdle { assertEquals(listOf(TopLevelDestination.HOME, TopLevelDestination.NOTIFICATIONS), selections) }
    }

    @Test
    fun `dragging the glass lens switches to the landing tab once`() {
        showGlassTabs()

        compose.onNodeWithTag("bottom-tabs").performTouchInput {
            swipe(Offset(width / 6f, center.y), center)
        }

        compose.runOnIdle { assertEquals(listOf(TopLevelDestination.NOTIFICATIONS), selections) }
    }
}
