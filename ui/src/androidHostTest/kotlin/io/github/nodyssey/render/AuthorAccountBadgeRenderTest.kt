package io.github.nodyssey.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.postdetail.AuthorAccountBadge
import io.github.nodyssey.ui.postdetail.ReplyAuthorInfo
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Manual colour and motion previews only; ordinary test runs skip this class. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class AuthorAccountBadgeRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Test
    fun `rank colours in light`() = capture(darkTheme = false)

    @Test
    fun `rank colours in dark`() = capture(darkTheme = true)

    @Test
    fun `rank colours in neutral dark`() = capture(darkTheme = true, pureWhite = true)

    @Test
    fun `account facts reveal after lookup`() {
        composeRule.mainClock.autoAdvance = false
        var info by mutableStateOf<ReplyAuthorInfo?>(null)
        composeRule.setContent {
            PlazaTheme(darkTheme = false, pureWhite = true) {
                Surface(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("轻舟", style = MaterialTheme.typography.bodySmall)
                        AuthorAccountBadge(info?.level, info?.joinedDays)
                    }
                }
            }
        }
        composeRule.onRoot().captureRender("author-account-reveal-pending")
        composeRule.runOnIdle { info = ReplyAuthorInfo(level = 4, joinedDays = 459) }
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.onRoot().captureRender("author-account-reveal-start")
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.onRoot().captureRender("author-account-reveal-middle")
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onRoot().captureRender("author-account-reveal-finished")
    }

    private fun capture(darkTheme: Boolean, pureWhite: Boolean = false) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme, pureWhite = pureWhite) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Text("作者账号信息", style = MaterialTheme.typography.titleLarge)
                        (0..7).forEach { level ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("论坛用户 ${level + 1}", style = MaterialTheme.typography.bodyMedium)
                                AuthorAccountBadge(level, listOf(2, 30, 94, 143, 459, 880, 1024, 2000)[level])
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("资料不全", style = MaterialTheme.typography.bodyMedium)
                            AuthorAccountBadge(null, 82)
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRender("author-account-badges-${if (darkTheme) "dark" else "light"}${if (pureWhite) "-neutral" else ""}")
    }
}
