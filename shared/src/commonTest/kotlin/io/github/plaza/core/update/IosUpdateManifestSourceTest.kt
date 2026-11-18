package io.github.plaza.core.update

import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosUpdateManifestSourceTest {
    @Test
    fun `numeric versions determine the newest IPA independently of manifest ordering`() = runTest {
        val source = source(release("1.3.9"), release("1.3.10"))
        assertEquals(listOf("1.3.10", "1.3.9"), source.releases(false).map { it.versionName })
    }

    @Test
    fun `test channels include IPA-only test releases without leaking them into stable`() = runTest {
        val source = source(release("1.3.12-ios-test.10"), release("1.3.12-ios-test.9"), release("1.3.11"))
        assertEquals(listOf("1.3.11"), source.releases(false).map { it.versionName })
        assertEquals("1.3.12-ios-test.10", source.releases(true).first().versionName)
    }

    @Test
    fun `a manifest cannot substitute APKs or direct downloads to another host or repository`() = runTest {
        for (bad in listOf(
            release("1.3.12", host = "github.com.evil.example"),
            release("1.3.12", assetRepository = "someone/else"),
            release("1.3.12", extension = "apk"),
        )) {
            assertEquals(UpdateFailure.Unreadable, assertFailsWith<AppUpdateException> { source(bad).releases(true) }.failure)
        }
    }

    @Test
    fun `missing malformed and unsupported feeds are failures rather than latest-version answers`() = runTest {
        assertEquals(UpdateFailure.Server(404), assertFailsWith<AppUpdateException> { source(body = "missing", code = 404).releases(false) }.failure)
        for (body in listOf("not json", "{\"schema\":2,\"versions\":[]}")) {
            assertEquals(UpdateFailure.Unreadable, assertFailsWith<AppUpdateException> { source(body = body).releases(false) }.failure)
        }
    }

    @Test
    fun `transport failure is classified and cancellation still propagates`() = runTest {
        fun failing(error: Exception) = IosUpdateManifestSource(
            object : HttpTransport {
                override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse = throw error
            },
            "https://owner.github.io/app/",
            "owner/app",
        )
        assertEquals(UpdateFailure.Network, assertFailsWith<AppUpdateException> { failing(SiteException(SiteError.Network)).releases(false) }.failure)
        assertFailsWith<CancellationException> { failing(CancellationException()).releases(false) }
    }

    private fun source(
        vararg releases: JsonObject,
        body: String = buildJsonObject {
            put("schema", 1)
            put("versions", buildJsonArray { releases.forEach(::add) })
        }.toString(),
        code: Int = 200,
    ) = IosUpdateManifestSource(
        object : HttpTransport {
            override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse =
                HttpResponse(code, request.url, emptyMap(), body)
        },
        "https://owner.github.io/app/",
        "owner/app",
    )

    private fun release(version: String, host: String = "github.com", assetRepository: String = "owner/app", extension: String = "ipa") = buildJsonObject {
        put("versionName", version)
        put("tag", "v$version")
        put("releaseUrl", "https://github.com/owner/app/releases/tag/v$version")
        put("preRelease", isPreReleaseVersionName(version))
        put("notes", "Release notes")
        put(
            "ipa",
            buildJsonObject {
                put("name", "nodyssey-v$version.$extension")
                put("url", "https://$host/$assetRepository/releases/download/v$version/nodyssey-v$version.$extension")
                put("sizeBytes", 12345)
            },
        )
    }
}
