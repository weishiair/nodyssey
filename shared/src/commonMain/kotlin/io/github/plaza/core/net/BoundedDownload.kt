package io.github.plaza.core.net

/** A binary GET that retains the HTTP status and stops buffering at the supplied size limit. */
interface BoundedDownload {
    suspend fun download(request: HttpRequest, maxBytes: Int): DownloadResponse
}

class DownloadResponse(
    val code: Int,
    val url: String,
    val headers: Map<String, String>,
    val bytes: ByteArray?,
)
