package io.github.nodyssey.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.PageJumpRail
import io.github.plaza.designsys.component.PlazaExtendedFab
import io.github.plaza.designsys.component.PlazaSnackbar
import io.github.plaza.designsys.theme.LocalLiquidGlassBackdrop
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.liquidGlassBackdropSource
import io.github.plaza.designsys.theme.rememberLiquidGlassBackdrop
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Review images only, including real layer capture; no appearance assertions or baselines. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class GlassChromeRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Scene(
        darkTheme: Boolean,
        expanded: Boolean = true,
        captureBackdrop: Boolean = true,
        frostedGlass: Boolean = true,
        einkMode: Boolean = false,
    ) {
        PlazaTheme(darkTheme = darkTheme, pureWhite = true, frostedGlass = frostedGlass, einkMode = einkMode) {
            val layers = LocalPlazaLayers.current
            val backdrop = rememberLiquidGlassBackdrop().takeIf { captureBackdrop }
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().liquidGlassBackdropSource(backdrop).background(layers.page).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(8) { index ->
                        Surface(shape = MaterialTheme.shapes.large, color = layers.card, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) {
                                Text("NodeSeek ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                Text("玻璃控件后的列表内容", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
                    Column(
                        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 140.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PageJumpRail(expanded, 1, 100, {}, {}, {})
                        PlazaExtendedFab("发帖", Icons.Default.Edit, {}, expanded = expanded)
                    }
                    PlazaSnackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 80.dp),
                        action = { FilledTonalButton(onClick = {}) { Text("重试") } },
                    ) { Text("网络开小差了") }
                }
            }
        }
    }

    @Test
    fun `glass chrome in light`() {
        composeRule.setContent { Scene(darkTheme = false) }
        composeRule.onRoot().captureRender("glass-chrome-light")
    }

    @Test
    fun `glass chrome in dark`() {
        composeRule.setContent { Scene(darkTheme = true) }
        composeRule.onRoot().captureRender("glass-chrome-dark")
    }

    @Test
    fun `collapsed glass chrome`() {
        composeRule.setContent { Scene(darkTheme = false, expanded = false) }
        composeRule.onRoot().captureRender("glass-chrome-collapsed")
    }

    @Test
    fun `glass chrome without layer capture`() {
        composeRule.setContent { Scene(darkTheme = false, captureBackdrop = false) }
        composeRule.onRoot().captureRender("glass-chrome-fallback")
    }

    @Test
    fun `ordinary chrome in pure white`() {
        composeRule.setContent { Scene(darkTheme = false, frostedGlass = false) }
        composeRule.onRoot().captureRender("glass-audit-ordinary")
    }

    @Test
    fun `ordinary chrome in neutral dark`() {
        composeRule.setContent { Scene(darkTheme = true, frostedGlass = false) }
        composeRule.onRoot().captureRender("glass-audit-ordinary-dark")
    }

    @Test
    fun `eink chrome with glass preference off`() {
        composeRule.setContent { Scene(darkTheme = false, frostedGlass = false, einkMode = true) }
        composeRule.onRoot().captureRender("glass-audit-eink-off")
    }

    @Test
    fun `eink chrome with glass preference on`() {
        composeRule.setContent { Scene(darkTheme = false, frostedGlass = true, einkMode = true) }
        composeRule.onRoot().captureRender("glass-audit-eink-on")
    }
}
