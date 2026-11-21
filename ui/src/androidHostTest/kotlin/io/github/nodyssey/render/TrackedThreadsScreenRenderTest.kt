package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.data.local.TrackedThreadDao
import io.github.nodyssey.data.local.TrackedThreadEntity
import io.github.nodyssey.ui.settings.TrackedThreadsRoute
import io.github.nodyssey.ui.settings.TrackedThreadsViewModel
import io.github.plaza.core.AppClock
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 追踪的帖子 in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class TrackedThreadsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        val viewModel = remember { TrackedThreadsViewModel(TrackedThreadStore(FakeTrackedThreadDao, AppClock.System)) }
        PlazaTheme(darkTheme = darkTheme) {
            TrackedThreadsRoute(
                viewModel = viewModel,
                onBack = {},
                onOpenThread = {},
            )
        }
    }

    @Test
    fun `tracked threads in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.waitForIdle()

        composeRule.onRoot().captureRender("tracked-threads-light")
    }

    @Test
    fun `tracked threads in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }
        composeRule.waitForIdle()

        composeRule.onRoot().captureRender("tracked-threads-dark")
    }

    private object FakeTrackedThreadDao : TrackedThreadDao {
        private val rows =
            listOf(
                TrackedThreadEntity(
                    postId = 1001L,
                    title = "香港线路晚上丢包排查记录",
                    lastKnownCount = 24,
                    addedAtMillis = 1_760_000_000_000L,
                    authorName = "轻舟",
                    categoryTitle = "技术",
                    categorySlug = "tech",
                ),
                TrackedThreadEntity(
                    postId = 1002L,
                    title = "四盘位 NAS 静音风扇选型对比",
                    lastKnownCount = 12,
                    addedAtMillis = 1_759_900_000_000L,
                    authorName = "homelab_er",
                    categoryTitle = "日常",
                    categorySlug = "daily",
                ),
            )

        override fun observeAll(): Flow<List<TrackedThreadEntity>> = flowOf(rows)

        override suspend fun all(): List<TrackedThreadEntity> = rows

        override suspend fun count(): Int = rows.size

        override fun observeIsTracked(postId: Long): Flow<Boolean> = flowOf(rows.any { it.postId == postId })

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
