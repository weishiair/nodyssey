package io.github.nodyssey.render

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.tools.AwardRow
import io.github.nodyssey.ui.tools.AwardScreen
import io.github.nodyssey.ui.tools.AwardUiState
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AwardScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Test
    fun `recommended reading in light`() = render("award-light", darkTheme = false)

    @Test
    fun `recommended reading in dark`() = render("award-dark", darkTheme = true)

    private fun render(name: String, darkTheme: Boolean) {
        composeRule.setContent {
            val paging = remember {
                val loaded = LoadState.NotLoading(endOfPaginationReached = false)
                flowOf(PagingData.from(listOf(ROW), LoadStates(loaded, loaded, loaded)))
            }
            PlazaTheme(darkTheme = darkTheme) {
                AwardScreen(
                    state = AwardUiState(totalPages = 18, pageSize = 20),
                    posts = paging.collectAsLazyPagingItems(),
                    onBack = {},
                    onPostClick = {},
                    onGoToPage = {},
                    onOpenBrowser = {},
                    onSignIn = {},
                )
            }
        }
        composeRule.onRoot().captureRender(name)
    }

    private companion object {
        val ROW = AwardRow(
            summary = PostSummary(
                postId = 1,
                title = "从零搭一套自用的探针与告警",
                authorName = "羽落无声",
                authorUid = 11203,
                avatarUrl = null,
                categoryTitle = "技术",
                categorySlug = "tech",
                viewCount = 6204,
                commentCount = 88,
                lastActiveText = "3天前",
                lastActiveTitle = null,
            ),
            page = 1,
        )
    }
}
