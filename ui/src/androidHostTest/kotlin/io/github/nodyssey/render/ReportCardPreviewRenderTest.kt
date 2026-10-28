package io.github.nodyssey.render

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.settings.ReportFormat
import io.github.nodyssey.ui.richtext.LocalReportFormat
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.ansi.AnsiDecoder
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** On-demand previews of the report card redesign; not a gate. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h2600dp-mdpi")
class ReportCardPreviewRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    private fun render(dark: Boolean) {
        val raw =
            checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/reports/hardware-quality.txt"))
                .bufferedReader()
                .readText()
        val decoded = AnsiDecoder.decode(raw)
        compose.setContent {
            PlazaTheme(darkTheme = dark, pureWhite = true) {
                Surface {
                    CompositionLocalProvider(LocalReportFormat provides ReportFormat.ADAPTED) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            PostRichContent(
                                nodes = listOf(
                                    RichNode.CodeBlock(
                                        code = decoded.text,
                                        language = "ansi",
                                        spans = decoded.spans,
                                        columns = decoded.columns,
                                    ),
                                ),
                                onLinkClick = {},
                                onImageClick = {},
                            )
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRender("report-card-preview-${if (dark) "dark" else "light"}")
    }

    @Test
    fun `report card preview in light`() = render(dark = false)

    @Test
    fun `report card preview in dark`() = render(dark = true)
}
