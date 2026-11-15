package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonWriteSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The OP's reply pin, verified in NodeSeek's `index.54a64c51385d4d94674d.js` on 2026-11-15.
 * The site asks before spending five chicken legs on `add`; `remove` has its own confirmation.
 * Neither operation is retried automatically: a lost response may follow an accepted spend.
 */
class PostPinWriter(private val api: JsonWriteSource) {
    suspend fun setPinned(postId: Long, commentId: Long, pinned: Boolean): Boolean {
        val answer = api.postJson(
            path = NodeSeekJsonClient.PATH_PIN_COMMENT,
            body = """{"commentId":$commentId,"action":"${if (pinned) "add" else "remove"}"}""",
            referer = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId),
        )
        val root = try {
            Json.parseToJsonElement(answer).jsonObject
        } catch (exception: IllegalArgumentException) {
            throw SiteException(SiteError.Unparsable, exception)
        }
        if (root["success"]?.jsonPrimitive?.booleanOrNull != true) {
            throw SiteException(SiteError.Unknown, detail = root["message"]?.jsonPrimitive?.contentOrNull?.ifBlank { null })
        }
        return when (root["message"]?.jsonPrimitive?.contentOrNull) {
            "added" -> true

            "removed" -> false

            // Success already acknowledges the write; do not turn an absent echo into a retry.
            else -> pinned
        }
    }
}
