package io.github.nodyssey.ui.tools

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.vps_copy_text
import io.github.nodyssey.ui.resources.vps_due_date
import io.github.nodyssey.ui.resources.vps_generate
import io.github.nodyssey.ui.resources.vps_pick_due
import io.github.nodyssey.ui.viewer.SaveOutcome
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class VpsCalculatorScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var viewModel: VpsCalculatorViewModel

    @Before
    fun openCalculator() {
        compose.setContent {
            viewModel = remember {
                val main = Dispatchers.Main.immediate
                VpsCalculatorViewModel(FakeVpsCalculatorRepository(), AppClock { Instant.parse("2024-02-15T12:00:00Z").toEpochMilliseconds() }, AppDispatchers(main, main, main))
            }
            PlazaTheme { VpsCalculatorRoute(viewModel, saveImage = { SaveOutcome.SAVED }, onBack = {}) }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `blank due date opens on trade month and changes only on confirmation`() {
        val pickDue = compose.onNode(hasText(runBlocking { getString(Res.string.vps_pick_due) }) and hasClickAction())
        val inDialog = hasAnyAncestor(isDialog())
        val title = compose.onNode(hasText(runBlocking { getString(Res.string.vps_due_date) }) and inDialog)
        val cancel = compose.onNode(hasText(runBlocking { getString(Res.string.action_cancel) }) and inDialog)
        val done = compose.onNode(hasText(runBlocking { getString(Res.string.action_done) }) and inDialog)
        val day = compose.onNode(hasText("29", substring = true) and inDialog)

        pickDue.performScrollTo().performTouchInput { click() }
        title.assertIsDisplayed()
        done.assertIsNotEnabled()
        day.performTouchInput { click() }
        cancel.assertIsDisplayed().performTouchInput { click() }
        compose.onNode(isDialog()).assertDoesNotExist()

        pickDue.performScrollTo().performTouchInput { click() }
        done.assertIsNotEnabled()
        day.performTouchInput { click() }
        done.assertIsDisplayed().performTouchInput { click() }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNode(hasText("2024-02-29") and hasClickAction()).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `quotes cannot be copied or exported until the exchange rate is known`() {
        compose.runOnIdle {
            viewModel.setPrice("365")
            viewModel.setDueDate(LocalDate(2024, 3, 15))
            viewModel.setRate("")
        }
        val copy = compose.onNodeWithText(runBlocking { getString(Res.string.vps_copy_text) })
        val image = compose.onNodeWithText(runBlocking { getString(Res.string.vps_generate) })
        copy.performScrollTo().assertIsNotEnabled()
        image.performScrollTo().assertIsNotEnabled()

        compose.runOnIdle { viewModel.setRate("7") }
        copy.assertIsEnabled()
        image.assertIsEnabled()
    }

    private fun focusPrice() = compose.onNodeWithText("续费价格").performScrollTo().performTouchInput { click() }.assertIsFocused()

    @Test
    fun `tapping outside the inputs ends price editing`() {
        val price = focusPrice()
        compose.onNodeWithText("付款周期").performTouchInput { click() }
        price.assertIsNotFocused()
    }

    @Test
    fun `tapping a billing cycle selects it and ends price editing`() {
        val price = focusPrice()
        val quarterly = compose.onNodeWithText("季付").performScrollTo()
        quarterly.performTouchInput { click() }.assertIsSelected()
        price.assertIsNotFocused()
    }

    @Test
    fun `finishing on the keyboard ends price editing`() {
        val price = focusPrice()
        price.performImeAction()
        price.assertIsNotFocused()
    }
}
