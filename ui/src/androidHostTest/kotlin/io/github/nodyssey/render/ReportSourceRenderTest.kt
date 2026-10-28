package io.github.nodyssey.render

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.settings.ReportFormat
import io.github.nodyssey.guard.repositoryRoot
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
import java.io.File

/** On-demand pictures of the original terminal report, using the committed parser fixture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h1000dp-mdpi")
class ReportSourceRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(dark: Boolean, fullscreen: Boolean = false) {
        val raw = File(repositoryRoot(), "shared/src/commonTest/resources/fixtures/reports/ip-quality.txt").readText()
        val coloured = raw.lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("#") || line.startsWith("=") -> "\u001B[36m$line\u001B[0m"

                line.contains("、") -> "\u001B[33;1m$line\u001B[0m"

                else -> line.replace("低风险", "\u001B[32m低风险\u001B[0m")
                    .replace("较高风险", "\u001B[31m较高风险\u001B[0m")
                    .replace("解锁", "\u001B[42;37m解锁\u001B[0m")
            }
        }
        val decoded = AnsiDecoder.decode(coloured)
        val code = RichNode.CodeBlock(decoded.text, "ansi", decoded.spans, decoded.columns)
        compose.setContent {
            PlazaTheme(darkTheme = dark, pureWhite = true) {
                Surface {
                    CompositionLocalProvider(LocalReportFormat provides ReportFormat.SOURCE) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            PostRichContent(
                                nodes = listOf(RichNode.Tabs(listOf(RichNode.Tabs.Tab("IP质量", listOf(code)), RichNode.Tabs.Tab("基本信息", listOf(code))))),
                                onLinkClick = {},
                                onImageClick = {},
                                modifier = Modifier.testTag("report-preview"),
                            )
                        }
                    }
                }
            }
        }
        if (fullscreen) {
            compose.onNodeWithContentDescription("全屏查看").performClick()
            compose.onAllNodesWithContentDescription("恢复原始字号").onLast().performClick()
            compose.waitForIdle()
            captureScreenRoboImage(filePath = "build/outputs/renders/report-source-fullscreen.png")
        } else {
            compose.onNodeWithTag("report-preview").captureRender("report-source-${if (dark) "dark" else "light"}")
        }
    }

    @Test
    fun `terminal report in light`() = render(dark = false)

    @Test
    fun `terminal report in dark`() = render(dark = true)

    @Test
    fun `terminal report full screen at original text size`() = render(dark = false, fullscreen = true)
}
