package io.github.plaza.core.update

import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.WebUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reads the IPA inventory published to Pages after an IPA is attached to a release. */
class IosUpdateManifestSource(
    private val transport: HttpTransport,
    private val manifestBaseUrl: String,
    private val repository: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun releases(includePreRelease: Boolean): List<AppRelease> {
        val response = try {
            transport.execute(
                HttpRequest(manifestBaseUrl + MANIFEST, headers = mapOf("Accept" to "application/json", "Cache-Control" to "no-cache")),
            )
        } catch (e: SiteException) {
            throw AppUpdateException(UpdateFailure.Network, e)
        }
        if (!response.isSuccessful) throw AppUpdateException(UpdateFailure.Server(response.code))
        val manifest = try {
            json.decodeFromString<IosUpdateManifest>(response.body)
        } catch (e: IllegalArgumentException) {
            throw AppUpdateException(UpdateFailure.Unreadable, e)
        }
        if (manifest.schema != 1) throw AppUpdateException(UpdateFailure.Unreadable)
        return manifest.versions.mapNotNull { release ->
            val preRelease = release.preRelease || isPreReleaseVersionName(release.versionName)
            if (!includePreRelease && preRelease) return@mapNotNull null
            if (release.versionName.isBlank() || versionNameOfTag(release.tag) != release.versionName ||
                release.ipa.name != "nodyssey-${release.tag}.ipa" || release.ipa.sizeBytes <= 0L ||
                !trustedUrl(release.ipa.url, "/$repository/releases/download/${release.tag}/${release.ipa.name}") ||
                !trustedUrl(release.releaseUrl, "/$repository/releases/tag/${release.tag}")
            ) {
                throw AppUpdateException(UpdateFailure.Unreadable)
            }
            AppRelease(
                versionName = release.versionName,
                tag = release.tag,
                notes = release.notes.trim(),
                downloadUrl = release.ipa.url,
                assetName = release.ipa.name,
                sizeBytes = release.ipa.sizeBytes,
                htmlUrl = release.releaseUrl,
                preRelease = preRelease,
            )
        }.sortedWith { left, right -> compareVersionNames(right.versionName, left.versionName) }
    }

    private fun trustedUrl(value: String, expectedPath: String): Boolean {
        val url = WebUrl.parse(value) ?: return false
        return url.scheme == "https" && url.host == "github.com" && url.userInfo == null &&
            (url.port == -1 || url.port == 443) && url.path == expectedPath && url.rawQuery == null && url.fragment == null
    }

    companion object {
        const val MANIFEST = "ios.json"
    }
}

@Serializable
private data class IosUpdateManifest(val schema: Int, val versions: List<IosReleaseEntry>)

@Serializable
private data class IosReleaseEntry(
    val versionName: String,
    val tag: String,
    val notes: String = "",
    val preRelease: Boolean = false,
    val releaseUrl: String,
    val ipa: IpaAsset,
)

@Serializable
private data class IpaAsset(val name: String, val url: String, val sizeBytes: Long)
