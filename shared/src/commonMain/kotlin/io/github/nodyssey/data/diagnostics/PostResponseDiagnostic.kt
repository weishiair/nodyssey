package io.github.nodyssey.data.diagnostics

import kotlinx.serialization.Serializable

/** Fixed response features only: never a response body, arbitrary header or cookie value. */
@Serializable
data class PostResponseDiagnostic(
    val recordedAtMillis: Long,
    val requestedUrl: String,
    val finalUrl: String,
    val statusCode: Int,
    val server: String,
    val cfRay: String?,
    val cfMitigated: Boolean,
    val contentType: String,
    val sampledBytes: Int,
    val bodyFeatures: List<String>,
    val sentCookieNames: List<String>,
    val receivedCookieNames: List<String>,
    val remoteAddress: String?,
    val protocol: String?,
    val tlsVersion: String?,
    val routeEchBytes: Int?,
)
