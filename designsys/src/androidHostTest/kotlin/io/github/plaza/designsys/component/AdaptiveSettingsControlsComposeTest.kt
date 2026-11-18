package io.github.plaza.designsys.component

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AdaptiveSettingsControlsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `rememberFitsSegmentedControl falls back for long translations or large accessibility font scale`() {
        var shortFits = false
        var longTranslationFits = true
        var scaledFontFits = true

        composeRule.setContent {
            PlazaTheme {
                val baseDensity = LocalDensity.current
                shortFits =
                    rememberFitsSegmentedControl(
                        labels = listOf("Auto", "Light", "Dark"),
                        availableWidth = 304.dp,
                        density = Density(baseDensity.density, fontScale = 1f),
                    )
                longTranslationFits =
                    rememberFitsSegmentedControl(
                        labels = listOf("Follow the system", "Light", "Dark"),
                        availableWidth = 304.dp,
                        density = Density(baseDensity.density, fontScale = 1f),
                    )
                scaledFontFits =
                    rememberFitsSegmentedControl(
                        labels = listOf("Auto", "Light", "Dark"),
                        availableWidth = 304.dp,
                        density = Density(baseDensity.density, fontScale = 2.5f),
                    )
            }
        }

        assertTrue(shortFits)
        assertFalse(longTranslationFits)
        assertFalse(scaledFontFits)
    }

    @Test
    fun `rememberFitsSegmentedControl respects app reading scale from PlazaTheme`() {
        var fitsAtDefaultScale = false
        var fitsAtMaxAppScale = true
        val labels = listOf("Balanced", "Light", "Dark")

        composeRule.setContent {
            val density = LocalDensity.current
            PlazaTheme(fontScale = 1f) {
                fitsAtDefaultScale =
                    rememberFitsSegmentedControl(
                        labels = labels,
                        availableWidth = 304.dp,
                        density = density,
                    )
            }
            PlazaTheme(fontScale = 1.5f) {
                fitsAtMaxAppScale =
                    rememberFitsSegmentedControl(
                        labels = labels,
                        availableWidth = 304.dp,
                        density = density,
                    )
            }
        }

        assertTrue(fitsAtDefaultScale)
        assertFalse(fitsAtMaxAppScale)
    }

    @Test
    fun `rememberFitsSegmentedControl falls back for short two-segment labels when large scale overflows height`() {
        var fitsAtLarge = false
        var fitsAtAccessibilityMax = true
        val labels = listOf("Preset", "Custom")

        composeRule.setContent {
            PlazaTheme {
                val baseDensity = LocalDensity.current
                fitsAtLarge =
                    rememberFitsSegmentedControl(
                        labels = labels,
                        availableWidth = 360.dp,
                        density = Density(baseDensity.density, fontScale = 1f),
                    )
                fitsAtAccessibilityMax =
                    rememberFitsSegmentedControl(
                        labels = labels,
                        availableWidth = 360.dp,
                        density = Density(baseDensity.density, fontScale = 1.8f),
                    )
            }
        }

        assertTrue(fitsAtLarge)
        assertFalse(fitsAtAccessibilityMax)
    }
}
