package io.github.nodyssey.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.data.local.TrackedThreadDao
import io.github.nodyssey.data.local.TrackedThreadEntity
import io.github.nodyssey.model.TermsBlock
import io.github.nodyssey.model.TermsDocument
import io.github.plaza.core.AppClock
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class PrivacyAndTrackedThreadsLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `privacy source line wraps onto a full-width row at Accessibility XL scale`() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.35f)) {
                PlazaTheme(fontScale = 1.5f) {
                    PrivacyScreen(
                        state =
                        PrivacyUiState.Content(
                            document =
                            TermsDocument(
                                title = "服务条款与隐私说明",
                                effectiveDate = "2026-01-01",
                                blocks = listOf(TermsBlock.Paragraph("正文")),
                            ),
                        ),
                        onBack = {},
                        onOpenOriginal = {},
                        onRetry = {},
                        onOpenWebFallback = {},
                    )
                }
            }
        }

        val dateBounds = composeRule.onNodeWithText("最新版本生效日期 2026-01-01").getUnclippedBoundsInRoot()
        val sourceBounds = composeRule.onNodeWithText("来自 nodeseek.com/termsofservice").getUnclippedBoundsInRoot()

        val sourceWidth = sourceBounds.right - sourceBounds.left
        assertTrue("Expected source label width > 180dp when wrapped, was $sourceWidth", sourceWidth > 180.dp)
        assertTrue(
            "Expected source label below effective date pill (${dateBounds.bottom}), was ${sourceBounds.top}",
            sourceBounds.top >= dateBounds.bottom,
        )
    }

    @Test
    fun `empty tracked threads message stays clear of the compact top bar`() {
        val viewModel = TrackedThreadsViewModel(TrackedThreadStore(EmptyTrackedThreadDao, AppClock.System))
        composeRule.setContent {
            PlazaTheme(oneHandMode = false) {
                TrackedThreadsRoute(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenThread = {},
                )
            }
        }
        composeRule.waitForIdle()

        val topBarTitleBounds = composeRule.onNodeWithText("追踪的帖子").getUnclippedBoundsInRoot()
        val emptyTextBounds = composeRule
            .onNodeWithText("还没有追踪的帖子。在帖子右上角的菜单里点「追踪新回复」。")
            .getUnclippedBoundsInRoot()

        val gap = emptyTextBounds.top - topBarTitleBounds.bottom
        assertTrue("Expected generous clearance below compact top bar, was $gap", gap > 48.dp)
    }

    private object EmptyTrackedThreadDao : TrackedThreadDao {
        override fun observeAll(): Flow<List<TrackedThreadEntity>> = flowOf(emptyList())

        override suspend fun all(): List<TrackedThreadEntity> = emptyList()

        override suspend fun count(): Int = 0

        override fun observeIsTracked(postId: Long): Flow<Boolean> = flowOf(false)

        override suspend fun upsert(thread: TrackedThreadEntity) = Unit

        override suspend fun delete(postId: Long) = Unit

        override suspend fun fillSnapshot(
            postId: Long,
            authorName: String?,
            categoryTitle: String?,
            categorySlug: String?,
        ) = Unit

        override suspend fun advance(
            postId: Long,
            count: Int,
        ) = Unit
    }
}
