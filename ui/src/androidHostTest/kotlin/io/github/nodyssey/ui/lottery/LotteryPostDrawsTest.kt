package io.github.nodyssey.ui.lottery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.LuckyDraw
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.data.lottery.LotteryEntry
import io.github.nodyssey.data.lottery.LotteryRepository
import io.github.nodyssey.model.PostContent
import io.github.plaza.core.AppClock
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h700dp")
class LotteryPostDrawsTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val models = ViewModelStore()
    private val draw = LuckyDrawParams(1024765, 1793546010000, 10, 1, false)
    private var signInRequests = 0
    private var watchRequests = 0
    private var removeRequests = 0
    private val entry = LotteryEntry(draw, "draw", 7767, "owner", "https://www.nodeseek.com")
    private val body = PostContent(
        commentId = null,
        floor = "#0",
        authorName = "author",
        authorUid = 69240,
        avatarUrl = null,
        isOriginalPoster = true,
        badges = emptyList(),
        createdAtText = null,
        createdAtTitle = null,
        categoryTitle = null,
        nodes = listOf(RichNode.Paragraph(listOf(InlineNode.Link("draw", LuckyDraw.link(draw))))),
    )

    @After
    fun clearModels() = models.clear()

    private fun show(entries: List<LotteryEntry>) {
        val repository = object : LotteryRepository {
            override val entries = flowOf(entries)
            override suspend fun watch(draw: LuckyDrawParams, title: String) {
                watchRequests++
            }
            override suspend fun remove(key: String) {
                removeRequests++
            }
            override suspend fun refreshDue(key: String?, force: Boolean) = Unit
            override suspend fun markResultsRead(entries: List<LotteryEntry>) = Unit
            override suspend fun synchronize() = Unit
        }
        val model = ViewModelProvider.create(
            models,
            viewModelFactory { initializer { LotteryViewModel(repository, AppClock { draw.drawAtMillis - 60_000 }) } },
        )[LotteryViewModel::class]
        composeRule.setContent {
            PlazaTheme {
                LotteryPostDraws(model, body, draw.postId, "draw", false, { signInRequests++ }, {}, {})
            }
        }
    }

    @Test
    fun `a signed-out new subscription goes to sign in before choosing the owning account`() {
        show(emptyList())
        composeRule.onNodeWithText("开启开奖提醒").performClick()
        assertEquals(1, signInRequests)
        assertEquals(0, watchRequests)
    }

    @Test
    fun `signing out does not prevent canceling an existing local reminder`() {
        show(listOf(entry))
        composeRule.onNodeWithText("取消开奖提醒").performClick()
        composeRule.waitForIdle()
        assertEquals(0, signInRequests)
        assertEquals(1, removeRequests)
    }
}
