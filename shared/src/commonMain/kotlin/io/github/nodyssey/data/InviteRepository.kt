package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/**
 * Which balance an invite code is paid from.
 *
 * The names are the site's own: `/api/invite/config` keys its price table by them and `buyOne` takes
 * `{"payment": …}` in the same words, so a currency read out of one cannot be sent to the other under
 * a different name.
 */
enum class InviteCurrency(val wireValue: String) {
    /** 鸡腿 — the currency posting and commenting pay in. */
    COIN("coin"),

    /** 星辰 — the rare one. */
    STARDUST("stardust"),
}

/**
 * What one code costs in one currency, and whether the site is selling it that way at all.
 *
 * [enabled] is a separate statement from the price: the site kept the number and closed the door,
 * which is not the same as a currency it never offered.
 */
data class InvitePrice(val cost: Int, val enabled: Boolean)

/**
 * Both prices, as of the last read.
 *
 * Read rather than hard-coded because both move: this app shipped 1000/鸡腿 as a constant for a year
 * while the site was already selling the same code for 100 星辰 beside it, and a price the app states
 * on the site's behalf has to be the site's.
 */
data class InvitePricing(
    val coin: InvitePrice? = null,
    val stardust: InvitePrice? = null,
) {
    fun priceFor(currency: InviteCurrency): InvitePrice? =
        when (currency) {
            InviteCurrency.COIN -> coin
            InviteCurrency.STARDUST -> stardust
        }

    /** The currencies the site would sell in right now, in the order the screen draws them. */
    val sellable: List<InviteCurrency>
        get() = InviteCurrency.entries.filter { priceFor(it)?.enabled == true }
}

/**
 * One code this account has bought.
 *
 * [applied] is the site's 已使用 — a code that has registered somebody — and [code] is what that
 * person types, so a row without one is dropped rather than shown blank.
 */
data class InviteCode(
    val code: String,
    val applied: Boolean,
    val createdAtMillis: Long?,
)

data class InviteCodePage(
    val codes: List<InviteCode>,
    /** Every code this account has bought; [codes] is one page of them. */
    val total: Int,
)

/** A response the site explicitly confirmed; thrown failures leave the purchase result unknown. */
sealed interface InvitePurchaseResult {
    data class Bought(val balance: Int?) : InvitePurchaseResult

    data class Refused(val error: SiteError, val detail: String? = null) : InvitePurchaseResult
}

interface InviteRepository {
    /**
     * The site's current prices.
     *
     * Throws rather than answering a remembered or invented price: the confirmation step is about to
     * state what a code costs, and a number that has drifted from the site's is worse than none.
     */
    suspend fun pricing(): InvitePricing

    /** Newest first; [page] is 1-based, [NodeSeekJsonClient.INVITE_PAGE_SIZE] rows a page. */
    suspend fun codes(page: Int = 1): InviteCodePage

    /**
     * Buys one code, paid for with [currency].
     *
     * A confirmed purchase carries the remaining balance, when supplied. The code itself is not in
     * the answer: see [codes], which is where the site reads it back too.
     *
     * Only an explicit refusal means nothing was bought. An exception can also mean the response was
     * lost after the server charged the account, so callers must check before offering another spend.
     */
    suspend fun buy(currency: InviteCurrency): InvitePurchaseResult
}

/**
 * 邀请码 over the three endpoints the site's own invite page uses.
 *
 * Until this existed the screen bought nothing: it confirmed the spend, then handed the reader to the
 * same page in a WebView, where they confirmed it a second time. The contract came out of that page's
 * chunk on 2026-11-06, so the second confirmation is now the only one.
 */
class NetworkInviteRepository(
    private val api: JsonApi,
    private val dispatchers: AppDispatchers,
) : InviteRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pricing(): InvitePricing {
        val body = api.getJson(path = NodeSeekJsonClient.PATH_INVITE_CONFIG, referer = INVITE_REFERER)
        return withContext(dispatchers.default) {
            val root = body.asJsonObject()
            root.refusal()?.let { throw it }
            val payment = root.obj("payment") ?: throw SiteException(SiteError.Unparsable)
            InvitePricing(
                coin = payment.price(InviteCurrency.COIN),
                stardust = payment.price(InviteCurrency.STARDUST),
            )
        }
    }

    override suspend fun codes(page: Int): InviteCodePage {
        val body = api.getJson(path = NodeSeekJsonClient.inviteListPath(page), referer = INVITE_REFERER)
        return withContext(dispatchers.default) {
            val root = body.asJsonObject()
            root.refusal()?.let { throw it }
            val rows = root.findObjectArray("data") ?: throw SiteException(SiteError.Unparsable)
            val codes = rows.mapNotNull(JsonObject::toInviteCode)
            InviteCodePage(codes = codes, total = root.int("total") ?: codes.size)
        }
    }

    override suspend fun buy(currency: InviteCurrency): InvitePurchaseResult {
        val answer =
            api.sendJson(
                method = "POST",
                path = NodeSeekJsonClient.PATH_INVITE_BUY,
                body = """{"payment":"${currency.wireValue}"}""",
                referer = INVITE_REFERER,
                extraHeaders = CSRF_HEADERS + ("Csrf-Token" to freshCsrfToken()),
            )
        return withContext(dispatchers.default) {
            val root = answer.body.asJsonObject()
            // Read before the status, unlike every other write: the refusal this call is most likely
            // to meet — 鸡腿/星辰不足 — arrives as a 200 with a sentence, and a 403 carrying one is the
            // site saying why, which is worth more to the reader than the sign-in prompt it would
            // otherwise become.
            root.refusal()?.let { return@withContext InvitePurchaseResult.Refused(it.error, it.detail) }
            if (answer.code == 401 || answer.code == 403) {
                return@withContext InvitePurchaseResult.Refused(SiteError.LoginRequired)
            }
            if (answer.code !in 200..299) throw SiteException(SiteError.Http(answer.code))
            if (root.bool("success") != true) throw SiteException(SiteError.Unparsable)
            InvitePurchaseResult.Bought(root.int(currency.wireValue))
        }
    }

    private fun String.asJsonObject(): JsonObject =
        runCatching { json.parseToJsonElement(this) as? JsonObject }
            .getOrElse { throw SiteException(SiteError.Unparsable, it) }
            ?: throw SiteException(SiteError.Unparsable)

    private fun JsonObject.refusal(): SiteException? =
        if (bool("success") == false) SiteException(SiteError.Unknown, detail = text("message")) else null

    private companion object {
        /** All three are issued against the site's own invite screen, so that is the referer. */
        val INVITE_REFERER get() = NodeSeekSite.BASE_URL + NodeSeekSite.INVITE_PATH

        /**
         * `x-csrf-challenge` names the challenge the site should make this request pass, and
         * `simple-token` is the one its own invite page sends.
         *
         * The token beside it is random for the reason 发帖 and 评论 already rely on: the server checks
         * that one *arrived*, not that it is the token it handed out — see
         * [io.github.nodyssey.data.composer.PostComposerRepository]. Only a live refusal can prove the
         * same holds for this endpoint, and a 星辰 purchase too expensive to make is the probe: it
         * answers 不足 rather than 403 when the header pair is accepted.
         */
        val CSRF_HEADERS = mapOf("x-csrf-challenge" to "simple-token")
        const val CSRF_TOKEN_LENGTH = 16

        fun freshCsrfToken(): String = Uuid.random().toString().replace("-", "").take(CSRF_TOKEN_LENGTH)
    }
}

/**
 * One price out of `/api/invite/config`'s `payment` object.
 *
 * A missing `enabled` reads as offered rather than withdrawn: the flag is the site's way of closing a
 * door it once opened, and absent means the door was never mentioned, not that it is shut.
 */
private fun JsonObject.price(currency: InviteCurrency): InvitePrice? {
    val entry = obj(currency.wireValue) ?: return null
    return InvitePrice(
        cost = entry.int("cost") ?: return null,
        enabled = entry.bool("enabled") ?: true,
    )
}

private fun JsonObject.toInviteCode(): InviteCode? {
    val code = text("code") ?: return null
    return InviteCode(
        code = code,
        applied = bool("applied") ?: false,
        createdAtMillis = TimeFormat.parseTimestamp(text("created_at", "createdAt")),
    )
}
