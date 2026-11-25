package io.github.nodyssey.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.Site
import io.github.nodyssey.core.VpsCalculatorDraft
import io.github.nodyssey.core.VpsCurrency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VpsCalculatorContainerTest {
    @Test
    fun `switching from NodeSeek to DeepFlood restores the saved valuation draft`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val draft = VpsCalculatorDraft(priceText = "42.50", dueDateIso = "2030-06-15")
        val originalSite = ActiveSite.current
        try {
            ActiveSite.install(Site.NODESEEK)
            val first = DefaultAppContainer(context)
            try {
                first.vpsCalculatorRepository.saveDraft(draft)
            } finally {
                first.shutdown()
            }

            ActiveSite.install(Site.DEEPFLOOD)
            val recreated = DefaultAppContainer(context)
            try {
                assertEquals(draft, recreated.vpsCalculatorRepository.draft.first())
            } finally {
                recreated.shutdown()
            }
        } finally {
            ActiveSite.install(originalSite)
        }
    }

    @Test
    fun `retired graph cancels a forced exchange rate refresh`() = runTest {
        val container = DefaultAppContainer(ApplicationProvider.getApplicationContext<Context>())
        val repository = try {
            container.vpsCalculatorRepository
        } finally {
            container.shutdown()
        }
        try {
            repository.getExchangeRate(VpsCurrency.USD, forceRefresh = true)
            fail("Expected the retired graph to cancel the refresh")
        } catch (expected: CancellationException) {
            assertEquals("Site graph retired", expected.message)
        }
    }
}
