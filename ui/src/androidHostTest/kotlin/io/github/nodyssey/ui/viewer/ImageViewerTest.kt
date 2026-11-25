package io.github.nodyssey.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.github.panpf.zoomimage.CoilZoomState
import io.github.plaza.designsys.image.StoredImageInterceptor
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageViewerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var zoomState: CoilZoomState
    private var dismissed = false
    private var canPage = false

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `a long screenshot loads original detail and can scroll without closing`() {
        setContent(imageHeight = 8_000)
        waitForImageState("original image for subsampling") {
            zoomState.subsampling.ready
        }

        composeRule.runOnIdle {
            assertEquals(8_000, zoomState.subsampling.imageInfo?.height)
            assertTrue(zoomState.zoomable.contentSize.height < 8_000)
            assertEquals(0, zoomState.zoomable.sourceVisibleRect.top)
            assertTrue(canPage)
        }
        composeRule.onRoot().performTouchInput { swipeUp(durationMillis = 500) }
        composeRule.runOnIdle {
            assertFalse(dismissed)
            assertTrue(zoomState.zoomable.sourceVisibleRect.top > 0)
        }
        composeRule.onRoot().performTouchInput {
            pinch(
                start0 = center - Offset(width * 0.12f, 0f),
                end0 = center - Offset(width * 0.43f, 0f),
                start1 = center + Offset(width * 0.12f, 0f),
                end1 = center + Offset(width * 0.43f, 0f),
            )
        }
        waitForImageState("a tile decoded at original resolution") {
            zoomState.subsampling.foregroundTiles.any { it.sampleSize == 1 && it.tileImage != null }
        }
        composeRule.runOnIdle { assertFalse(canPage) }
    }

    @Test
    fun `a vertical swipe still closes a whole image`() {
        setContent(imageHeight = 640)
        waitForImageState("whole image decoded") { zoomState.zoomable.contentSize.height > 0 }

        composeRule.onRoot().performTouchInput { swipeDown(durationMillis = 500) }
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    private fun waitForImageState(description: String, condition: () -> Boolean) {
        composeRule.waitUntil(description, timeoutMillis = 10_000) {
            // Coil and ZoomImage post results and delayed work to the Android main looper.
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    private fun setContent(imageHeight: Int) {
        val bytes = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(640, imageHeight, Bitmap.Config.ARGB_8888)
            try {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
            } finally {
                bitmap.recycle()
            }
            output.toByteArray()
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components { add(StoredImageInterceptor { bytes }) }
                .build(),
        )
        composeRule.setContent {
            PlazaTheme {
                Box(Modifier.fillMaxSize()) {
                    zoomState = rememberViewerZoomState()
                    ZoomableImage(
                        url = "https://example.test/screenshot.jpg",
                        isCurrentPage = true,
                        zoomState = zoomState,
                        onDismiss = { dismissed = true },
                        onGesturesChanged = { _, pageBySwipe -> canPage = pageBySwipe },
                    )
                }
            }
        }
    }
}
