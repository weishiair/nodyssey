package io.github.plaza.designsys.image

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Exercises Coil's real file fetcher and network cache, shared by the Android and iOS loaders. */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageLoadingPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val wifiOnly = MutableStateFlow(true)
    private val url = "https://example.invalid/offline.png"
    private var networkCalls = 0
    private var networkAvailable = true

    @Test
    fun `downloaded image remains readable after normal caches are cleared and the loader restarts`() = runTest {
        val stored = temporary.newFile("downloaded.png").apply { writeBytes(png()) }
        val cache = temporary.newFolder("cache")
        networkAvailable = false
        val first = loader(cache, stored)
        try {
            success(first.execute(request()))
            first.memoryCache?.clear()
            first.diskCache?.clear()
        } finally {
            first.shutdown()
        }

        val restarted = loader(cache, stored)
        try {
            val result = success(restarted.execute(request()))
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals(url, result.memoryCacheKey?.key)
            assertEquals(2, result.image.width)
            assertEquals(0, networkCalls)
        } finally {
            restarted.shutdown()
        }
    }

    @Test
    fun `a remote cache miss on cellular is deferred without sending a network request`() = runTest {
        val loader = loader(temporary.newFolder("cache"))
        try {
            val result = loader.execute(request()) as ErrorResult
            assertTrue(result.throwable.stackTraceToString(), result.throwable is ImagesDeferredException)
            assertEquals(0, networkCalls)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun `explicit loading on cellular fetches the image while the Wi-Fi preference stays enabled`() = runTest {
        val loader = loader(temporary.newFolder("cache"))
        try {
            assertTrue(loader.execute(request()) is ErrorResult)
            val result = loader.execute(request().newBuilder().allowMeteredImage(true).build())
            success(result)
            assertEquals(1, networkCalls)
            assertTrue(wifiOnly.value)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun `enabling Wi-Fi only still allows an image from the ordinary disk cache`() = runTest {
        val loader = loader(temporary.newFolder("cache"))
        try {
            wifiOnly.value = false
            success(loader.execute(request()))
            loader.memoryCache?.clear()
            wifiOnly.value = true
            networkAvailable = false

            val result = success(loader.execute(request()))
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals(1, networkCalls)
        } finally {
            loader.shutdown()
        }
    }

    private fun loader(cache: File, stored: File? = null): ImageLoader = ImageLoader.Builder(context)
        .components {
            // ImageDecoder's file input is Android-only in Robolectric on Windows.
            add(BitmapFactoryDecoder.Factory())
            add(StoredImageInterceptor { candidate -> stored?.toOkioPath()?.takeIf { candidate == url } })
            add(ImageNetworkPolicyInterceptor(wifiOnly) { false })
            add(NetworkFetcher.Factory(networkClient = { NetworkCachePolicyClient(network) }, cacheStrategy = { CacheControlCacheStrategy() }))
        }
        .diskCache { DiskCache.Builder().directory(cache.toOkioPath()).build() }
        .build()

    private val network = object : NetworkClient {
        override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
            networkCalls++
            if (!networkAvailable) throw IOException("offline")
            return block(
                NetworkResponse(
                    code = 200,
                    headers = NetworkHeaders.Builder().set("Cache-Control", "public, max-age=86400").set("Content-Type", "image/png").build(),
                    body = NetworkResponseBody(Buffer().write(png())),
                ),
            )
        }
    }

    private fun request(): ImageRequest = ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).build()

    private fun success(result: ImageResult): SuccessResult {
        if (result is ErrorResult) throw AssertionError("Image loading failed", result.throwable)
        return result as SuccessResult
    }

    private fun png(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
