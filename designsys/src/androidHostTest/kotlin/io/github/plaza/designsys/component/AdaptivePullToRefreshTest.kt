package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AdaptivePullToRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `adaptive pull to refresh box renders content with Android fallback even when frostedGlass is set`() {
        composeRule.setContent {
            PlazaTheme(frostedGlass = true) {
                val frostedLayers = LocalPlazaLayers.current.copy(frostedGlass = true)
                CompositionLocalProvider(LocalPlazaLayers provides frostedLayers) {
                    AdaptivePullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = {},
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Text("Feed Content")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Feed Content").assertExists()
    }

    @Test
    fun `ios indicator reports no progress while idle and determinate progress while pulling`() {
        lateinit var ptrState: PullToRefreshState
        composeRule.setContent {
            PlazaTheme {
                ptrState = rememberPullToRefreshState()
                AdaptivePullToRefreshIndicatorContent(
                    state = ptrState,
                    isRefreshing = false,
                    useIosStyle = true,
                )
            }
        }

        composeRule.onAllNodes(hasAnyProgress()).fetchSemanticsNodes().let { nodes ->
            check(nodes.isEmpty()) { "Expected no progress semantics while idle, found ${nodes.size}" }
        }

        runBlocking { ptrState.snapTo(0.6f) }
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(current = 0.6f, range = 0f..1f),
                ),
            )
            .assertExists()
    }

    @Test
    fun `ios indicator reports indeterminate progress and steps cleanly while refreshing`() {
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                PlazaTheme(darkTheme = true) {
                    val state = rememberPullToRefreshState()
                    AdaptivePullToRefreshIndicatorContent(
                        state = state,
                        isRefreshing = true,
                        useIosStyle = true,
                    )
                }
            }

            composeRule.mainClock.advanceTimeBy(160)
            composeRule
                .onNode(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.ProgressBarRangeInfo,
                        ProgressBarRangeInfo.Indeterminate,
                    ),
                )
                .assertExists()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun hasAnyProgress(): SemanticsMatcher =
        SemanticsMatcher("has ProgressBarRangeInfo") { node ->
            SemanticsProperties.ProgressBarRangeInfo in node.config
        }
}
