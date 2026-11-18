package io.github.nodyssey.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.TrackedThread
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.post_reply_count
import io.github.nodyssey.ui.resources.tracked_threads_untrack
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
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
class TrackedThreadsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `row click opens thread without untracking and untrack button removes only target without opening`() {
        val opened = mutableListOf<Long>()
        val untracked = mutableListOf<Long>()
        var untrackSecondLabel = ""

        composeRule.setContent {
            PlazaTheme {
                untrackSecondLabel = stringResource(Res.string.tracked_threads_untrack, "Second Thread")
                TrackedThreadsScreen(
                    threads = listOf(
                        TrackedThread(
                            postId = 101L,
                            title = "First Thread",
                            lastKnownCount = 12,
                            authorName = "alice",
                            categoryTitle = "技术",
                            categorySlug = "tech",
                        ),
                        TrackedThread(
                            postId = 202L,
                            title = "Second Thread",
                            lastKnownCount = TrackedThreadStore.UNKNOWN_COUNT,
                            authorName = "bob",
                            categoryTitle = "日常",
                            categorySlug = "daily",
                        ),
                    ),
                    onBack = {},
                    onOpenThread = { opened += it },
                    onUntrack = { untracked += it },
                )
            }
        }

        composeRule.onNodeWithText("First Thread").performClick()
        assertEquals(listOf(101L), opened)
        assertTrue(untracked.isEmpty())

        composeRule.onNodeWithContentDescription(untrackSecondLabel).performClick()
        assertEquals(listOf(101L), opened)
        assertEquals(listOf(202L), untracked)
    }

    @Test
    fun `negative lastKnownCount does not fabricate zero reply count`() {
        var zeroReplies = ""
        var fiveReplies = ""

        composeRule.setContent {
            PlazaTheme {
                zeroReplies = stringResource(Res.string.post_reply_count, 0)
                fiveReplies = stringResource(Res.string.post_reply_count, 5)
                TrackedThreadsScreen(
                    threads = listOf(
                        TrackedThread(
                            postId = 1L,
                            title = "Unknown Count Post",
                            lastKnownCount = TrackedThreadStore.UNKNOWN_COUNT,
                            authorName = "alice",
                        ),
                        TrackedThread(
                            postId = 2L,
                            title = "Known Count Post",
                            lastKnownCount = 5,
                            authorName = "bob",
                        ),
                    ),
                    onBack = {},
                    onOpenThread = {},
                    onUntrack = {},
                )
            }
        }

        composeRule.onNodeWithText("alice · $zeroReplies").assertDoesNotExist()
        composeRule.onNodeWithText("bob · $fiveReplies").assertExists()
    }
}
