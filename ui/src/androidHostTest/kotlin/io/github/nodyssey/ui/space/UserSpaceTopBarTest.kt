package io.github.nodyssey.ui.space

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_open_in_browser
import io.github.nodyssey.ui.resources.profile_edit
import io.github.nodyssey.ui.resources.user_note_action
import io.github.plaza.designsys.component.TopBarAction
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * iOS draws the bar from the [TopBarAction.glass] descriptions, which no Android path runs: the wrong
 * action on the wrong space, or a browser button opening some other address, would ship without a
 * screen showing it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class UserSpaceTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the native bar offers editing on your own space, a note on someone else's, and always this space's own address`() {
        val url = "https://www.nodeseek.com/space/18842"
        var edits = 0
        var notes = 0
        val opened = mutableListOf<String>()
        lateinit var self: List<TopBarAction>
        lateinit var other: List<TopBarAction>
        lateinit var noProfile: List<TopBarAction>
        lateinit var editLabel: String
        lateinit var noteLabel: String
        lateinit var browserLabel: String
        composeRule.setContent {
            editLabel = stringResource(Res.string.profile_edit)
            noteLabel = stringResource(Res.string.user_note_action)
            browserLabel = stringResource(Res.string.action_open_in_browser)
            val onOpen = { u: String -> opened += u }
            self = spaceTopBarActions(true, true, url, { edits++ }, { notes++ }, onOpen)
            other = spaceTopBarActions(false, true, url, { edits++ }, { notes++ }, onOpen)
            noProfile = spaceTopBarActions(false, false, url, { edits++ }, { notes++ }, onOpen)
        }
        composeRule.waitForIdle()

        assertEquals(listOf(editLabel, browserLabel), self.map { it.glass.label })
        assertEquals(listOf(noteLabel, browserLabel), other.map { it.glass.label })
        assertEquals(listOf(browserLabel), noProfile.map { it.glass.label })

        self[0].glass.onClick()
        assertEquals(1, edits)
        other[0].glass.onClick()
        assertEquals(1, notes)
        self[1].glass.onClick()
        assertEquals(listOf(url), opened)
    }
}
