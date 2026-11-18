@file:OptIn(coil3.annotation.ExperimentalCoilApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.plaza.designsys.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Size
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeBase64
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Exercises iOS's real Path mapper and image decoder without an ordinary image cache. */
class IosImageLoadingPolicyTest {
    private val url = "https://example.invalid/downloaded.png"
    private var networkCalls = 0

    @Test
    fun `a downloaded image is readable after restarting a loader with ordinary caches disabled`() = runTest {
        val directory = NSTemporaryDirectory().toPath() / "nodyssey-images-${NSUUID().UUIDString()}"
        val stored = directory / "downloaded.png"
        val files = FileSystem.SYSTEM
        files.createDirectories(directory)
        files.write(stored) { write(requireNotNull(PNG.decodeBase64())) }
        try {
            repeat(2) {
                val loader = loader(stored)
                try {
                    val result = success(loader.execute(request()))
                    assertEquals(DataSource.DISK, result.dataSource)
                    assertEquals(2, result.image.width)
                    assertEquals(3, result.image.height)
                } finally {
                    loader.shutdown()
                }
            }
            assertEquals(0, networkCalls)
        } finally {
            files.delete(stored)
            files.delete(directory)
        }
    }

    @Test
    fun `a cache-only miss never reaches the iOS byte transport`() = runTest {
        val loader = loader()
        try {
            val result = assertIs<ErrorResult>(loader.execute(request()))
            assertIs<ImagesDeferredException>(result.throwable)
            assertEquals(0, networkCalls)
        } finally {
            loader.shutdown()
        }
    }

    private fun loader(stored: Path? = null): ImageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
        .memoryCache(null)
        .diskCache(null)
        .components {
            add(StoredImageInterceptor { candidate -> stored?.takeIf { candidate == url } })
            add(ImageNetworkPolicyInterceptor(flowOf(true)) { false })
            add(NetworkFetcher.Factory(networkClient = { NetworkCachePolicyClient(network) }))
        }
        .build()

    private val network = object : NetworkClient {
        override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
            networkCalls++
            throw IOException("Network access is forbidden in this test")
        }
    }

    private fun request(): ImageRequest = ImageRequest.Builder(PlatformContext.INSTANCE).data(url).size(Size.ORIGINAL).build()

    private fun success(result: ImageResult): SuccessResult {
        if (result is ErrorResult) throw AssertionError("Image loading failed", result.throwable)
        return assertIs<SuccessResult>(result)
    }

    private companion object {
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAADCAYAAAC56t6BAAAAEUlEQVR4nGP4z8DwH4QZMBgAoXkL9U3EmgcAAAAASUVORK5CYII="
    }
}
