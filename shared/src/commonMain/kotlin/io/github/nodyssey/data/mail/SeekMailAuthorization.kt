package io.github.nodyssey.data.mail

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.SeekMailSite
import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.isChallengeAnswer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The official provider exchange, observed in the site's shipped login/Connect clients. */
internal class SeekMailAuthorization(private val forum: HttpTransport, private val mail: SeekMailClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun token(checkCurrent: () -> Unit): String {
        val providers = decode<Providers>(mail.authorizationRequest("/auth/providers"))
        checkCurrent()
        val provider = providers.providers.singleOrNull { it.id == "nodeseek" && it.enabled }
        if (provider?.type != "oauth_popup" || provider.providerType != "nodeseek" ||
            provider.config != ProviderConfig(FORUM_ORIGIN, CONNECT_URL, "ns-connect", TARGET)
        ) {
            throw MailException(MailError.WEB_AUTHORIZATION)
        }
        val response = forum.execute(
            HttpRequest(
                url = SeekMailSite.NODESEEK_AUTHORIZATION_CHALLENGE,
                headers = mapOf("Accept" to "application/json", "Referer" to CONNECT_URL),
            ),
        )
        checkCurrent()
        if (response.code == 468 || isChallengeAnswer(response.body, response.header("cf-mitigated"), NodeSeekSite.CONFIG.markers)) {
            throw MailException(MailError.CHALLENGE)
        }
        if (response.code == 401 || response.code in 300..399 || response.url != SeekMailSite.NODESEEK_AUTHORIZATION_CHALLENGE) {
            throw MailException(MailError.FORUM_SIGN_IN)
        }
        if (!response.isSuccessful) {
            throw MailException(if (response.code == 429) MailError.RATE_LIMITED else MailError.WEB_AUTHORIZATION)
        }
        val signature = decode<Signature>(response.body)
        if (!signature.success) throw MailException(MailError.FORUM_SIGN_IN)
        if (signature.links.none { it == SeekMailSite.ORIGIN || it == "${SeekMailSite.ORIGIN}/" } ||
            listOf(signature.data, signature.wtf, signature.sign).any { it == JsonNull || (it is JsonPrimitive && it.content.isBlank()) }
        ) {
            throw MailException(MailError.INVALID_RESPONSE)
        }
        // Preserve the signed JSON values exactly. In particular, data need not be a string.
        val payload = JsonObject(mapOf("data" to signature.data, "wtf" to signature.wtf, "sign" to signature.sign))
        val callback = decode<Callback>(
            mail.authorizationRequest("/auth/provider/nodeseek/callback", "POST", HttpBody.Text(payload.toString())),
        )
        checkCurrent()
        if (callback.requires2fa || callback.isNewUser || callback.initialPassword != null) {
            throw MailException(MailError.WEB_AUTHORIZATION)
        }
        return callback.token?.takeIf(String::isNotBlank) ?: throw MailException(MailError.INVALID_RESPONSE)
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (_: SerializationException) {
        throw MailException(MailError.INVALID_RESPONSE)
    } catch (_: IllegalArgumentException) {
        throw MailException(MailError.INVALID_RESPONSE)
    }

    private companion object {
        const val FORUM_ORIGIN = "https://www.nodeseek.com"
        const val TARGET = "NodeMailDev"
        const val CONNECT_URL = "$FORUM_ORIGIN/connect?target=$TARGET"
    }
}

@Serializable
private data class Providers(val providers: List<Provider>)

@Serializable
private data class Provider(
    val id: String,
    val enabled: Boolean = false,
    val type: String,
    val providerType: String? = null,
    val config: ProviderConfig? = null,
)

@Serializable
private data class ProviderConfig(val origin: String, @SerialName("popup_url") val popupUrl: String, val source: String, val target: String)

@Serializable
private data class Signature(
    val success: Boolean,
    val data: JsonElement = JsonNull,
    val wtf: JsonElement = JsonNull,
    val sign: JsonElement = JsonNull,
    val links: List<String> = emptyList(),
)

@Serializable
private data class Callback(
    val token: String? = null,
    @SerialName("requires_2fa") val requires2fa: Boolean = false,
    val isNewUser: Boolean = false,
    val initialPassword: String? = null,
)
