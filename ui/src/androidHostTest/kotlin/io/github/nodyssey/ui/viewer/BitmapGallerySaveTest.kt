package io.github.nodyssey.ui.viewer

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.MediaStore.Images.Media
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapGallerySaveTest {
    @get:Rule val compose = createComposeRule()

    private val resolver = shadowOf(ApplicationProvider.getApplicationContext<Context>().contentResolver)
    private val uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, 1)

    private fun saveTo(output: ByteArrayOutputStream): SaveOutcome {
        resolver.setNextDatabaseIdForInserts(0)
        resolver.registerOutputStream(uri, output)
        lateinit var saver: ImageGallerySaver
        compose.setContent { saver = rememberImageGallerySaver(AppDispatchers()) }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return runBlocking { saver.save(bitmap.asImageBitmap(), "calculator.png") }
    }

    @Test
    fun `bitmap is published as a readable PNG in the requested gallery directory`() {
        val output = ByteArrayOutputStream()
        assertEquals(SaveOutcome.SAVED, saveTo(output))
        val inserted = resolver.insertStatements.single().contentValues
        assertEquals("calculator.png", inserted.getAsString(Media.DISPLAY_NAME))
        assertEquals("Pictures/NodeSeek", inserted.getAsString(Media.RELATIVE_PATH))
        assertEquals("image/png", inserted.getAsString(Media.MIME_TYPE))
        assertEquals(1, inserted.getAsInteger(Media.IS_PENDING))
        assertEquals(0, resolver.updateStatements.single().contentValues.getAsInteger(Media.IS_PENDING))
        val bytes = output.toByteArray()
        assertEquals("image/png", sniffImageMime(bytes)?.value)
        assertEquals(Color.RED, BitmapFactory.decodeByteArray(bytes, 0, bytes.size).getPixel(0, 0))
    }

    @Test
    fun `stream close failure removes pending image without publishing it`() {
        val output = object : ByteArrayOutputStream() {
            override fun close() = throw IOException("close failed")
        }
        assertEquals(SaveOutcome.FAILED, saveTo(output))
        assertTrue(resolver.updateStatements.isEmpty())
        assertEquals(listOf(uri), resolver.deletedUris)
    }
}
