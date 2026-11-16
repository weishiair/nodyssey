package io.github.plaza.designsys.image

import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult

/** Resolves downloaded images before the network policy or Coil's reclaimable caches are consulted. */
class StoredImageInterceptor(
    private val storedImage: (String) -> Any?,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (!chain.request.isRemoteImage()) return chain.proceed()
        val url = chain.request.data.toString()
        val stored = storedImage(url) ?: return chain.proceed()
        val request = chain.request.newBuilder()
            .data(stored)
            .memoryCacheKey(chain.request.memoryCacheKey ?: url)
            .build()
        return chain.withRequest(request).proceed()
    }
}

internal fun ImageRequest.isRemoteImage(): Boolean = data.toString().let { it.startsWith("https://") || it.startsWith("http://") }
