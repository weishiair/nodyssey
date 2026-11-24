package io.github.nodyssey.data.mail

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.net.BoundedDownload
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun BoundedDownload.loadMailImages(urls: List<String>): Map<String, MailRemoteImage> = coroutineScope {
    val parallel = Semaphore(4)
    urls.asSequence().filter(::isMailImageUrl).distinct().take(MAX_REMOTE_IMAGES).map { url ->
        async {
            parallel.withPermit {
                withTimeoutOrNull(12_000) {
                    runCatchingExceptCancellation { downloadMailImage(url) }.getOrNull()?.let { url to it }
                }
            }
        }
    }.toList().awaitAll().filterNotNull().toMap()
}

private suspend fun BoundedDownload.downloadMailImage(source: String): MailRemoteImage? {
    var url = source
    repeat(4) {
        val response = download(
            HttpRequest(url = url, headers = mapOf("Accept" to "image/*", "Cache-Control" to "no-store")),
            maxBytes = 1024 * 1024,
        )
        if (response.code in 200..299) {
            val mediaType = response.headers["content-type"]?.substringBefore(';')?.trim()?.lowercase()
            val bytes = response.bytes
            return if (mediaType != null && mediaType in mailImageTypes && bytes != null && bytes.isNotEmpty()) {
                MailRemoteImage(mediaType, bytes)
            } else {
                null
            }
        }
        val location = response.headers["location"]?.takeIf { response.code in IMAGE_REDIRECTS } ?: return null
        url = Ksoup.parseBodyFragment("", response.url).body().appendElement("a").attr("href", location).absUrl("href")
        if (!isMailImageUrl(url)) return null
    }
    return null
}

internal const val MAX_REMOTE_IMAGES = 20
private val IMAGE_REDIRECTS = setOf(301, 302, 303, 307, 308)
