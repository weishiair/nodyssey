package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.JsonPostResponse
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the three invite endpoints against the shape the site's own invite page uses, read out of its
 * `index-*.js` chunk on 2026-11-06.
 *
 * Four things here are worth breaking a build over. The prices are the site's and either currency can
 * be withdrawn, so an `enabled:false` must not become a button. The purchase names its currency in the
 * body under the same word the config uses as its key. The answer carries the balance that paid and
 * *not* the code. And `success:false` arrives with HTTP 200 — read as a completed purchase, it would
 * report a spend of real 鸡腿 or 星辰 that never happened.
 */
class InviteRepositoryTest {
    private val dispatchers = AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    @Test
    fun `reads both prices and keeps the withdrawn one withdrawn`() =
        runTest {
            val source =
                FakeInviteJsonSource(
                    config =
                    """
                    {"success":true,"payment":{
                      "coin":{"cost":1000,"enabled":true},
                      "stardust":{"cost":100,"enabled":false}}}
                    """.trimIndent(),
                )

            val pricing = NetworkInviteRepository(source, dispatchers).pricing()

            assertEquals("/api/invite/config", source.requestedPath)
            assertEquals(1_000, pricing.coin?.cost)
            assertEquals(true, pricing.coin?.enabled)
            assertEquals(100, pricing.stardust?.cost)
            assertEquals(false, pricing.stardust?.enabled)
            // The screen draws one button per sellable currency, so this list is the whole decision.
            assertEquals(listOf(InviteCurrency.COIN), pricing.sellable)
        }

    /** A currency the config stopped pricing is not a currency to offer at zero. */
    @Test
    fun `drops a currency the config does not price`() =
        runTest {
            val source =
                FakeInviteJsonSource(
                    config = """{"success":true,"payment":{"stardust":{"enabled":true}}}""",
                )

            val pricing = NetworkInviteRepository(source, dispatchers).pricing()

            assertNull(pricing.coin)
            assertNull(pricing.stardust)
            assertTrue(pricing.sellable.isEmpty())
        }

    @Test
    fun `reads the codes and drops a row without one`() =
        runTest {
            val source =
                FakeInviteJsonSource(
                    list =
                    """
                    {"success":true,"total":7,"data":[
                      {"code":"8F3K2Q9M","applied":false,"created_at":"2026-11-06T03:34:13.000Z"},
                      {"applied":true,"created_at":"2026-11-05T03:34:13.000Z"}]}
                    """.trimIndent(),
                )

            val page = NetworkInviteRepository(source, dispatchers).codes()

            assertEquals("/api/invite/list?page=1", source.requestedPath)
            assertEquals(7, page.total)
            val code = page.codes.single()
            assertEquals("8F3K2Q9M", code.code)
            assertFalse(code.applied)
            assertEquals(1_793_936_053_000L, code.createdAtMillis)
        }

    /**
     * The body's one field is the currency, and the balance comes back under that currency's own name.
     *
     * Reading the wrong key would leave the screen showing the pre-purchase balance next to a code that
     * has already been paid for.
     */
    @Test
    fun `buys in the named currency and answers the balance that paid`() =
        runTest {
            val source =
                FakeInviteJsonSource(buy = JsonPostResponse(code = 200, body = """{"success":true,"stardust":1}"""))

            val balance = NetworkInviteRepository(source, dispatchers).buy(InviteCurrency.STARDUST)

            assertEquals(InvitePurchaseResult.Bought(1), balance)
            val sent = source.sent ?: error("nothing was sent")
            assertEquals("POST", sent.method)
            assertEquals("/api/invite/buyOne", sent.path)
            assertEquals("""{"payment":"stardust"}""", sent.body)
            // Both headers the site's own page sends: the challenge name, and a token it checks only
            // for having arrived. A live 星辰 refusal is what proves the pair is accepted.
            assertEquals("simple-token", sent.headers["x-csrf-challenge"])
            assertTrue(sent.headers["Csrf-Token"]?.isNotEmpty() == true)
        }

    @Test
    fun `treats a success false answer as a refusal carrying the site's sentence`() =
        runTest {
            val source =
                FakeInviteJsonSource(
                    buy = JsonPostResponse(code = 200, body = """{"success":false,"message":"鸡腿数目不足"}"""),
                )

            val result = NetworkInviteRepository(source, dispatchers).buy(InviteCurrency.COIN)

            assertEquals(InvitePurchaseResult.Refused(SiteError.Unknown, "鸡腿数目不足"), result)
        }

    /** A 401/403 with no sentence of its own is a session the site no longer honours. */
    @Test
    fun `maps an unexplained 403 to a sign-in requirement`() =
        runTest {
            val source = FakeInviteJsonSource(buy = JsonPostResponse(code = 403, body = """{"message":"forbidden"}"""))

            val result = NetworkInviteRepository(source, dispatchers).buy(InviteCurrency.COIN)

            assertEquals(InvitePurchaseResult.Refused(SiteError.LoginRequired), result)
        }

    // Bug: any 200 JSON object used to count as a completed purchase, even without confirmation.
    @Test
    fun `an unrecognized success body does not confirm a purchase`() =
        runTest {
            val source = FakeInviteJsonSource(buy = JsonPostResponse(code = 200, body = """{"message":"unexpected"}"""))

            val exception = runCatching { NetworkInviteRepository(source, dispatchers).buy(InviteCurrency.COIN) }.exceptionOrNull()

            assertEquals(SiteError.Unparsable, (exception as? SiteException)?.error)
        }

    // A failed server response may follow a committed transaction, unlike an explicit refusal.
    @Test
    fun `a server failure without an explicit refusal leaves the result unknown`() =
        runTest {
            val source = FakeInviteJsonSource(buy = JsonPostResponse(code = 500, body = """{"message":"server error"}"""))

            val exception = runCatching { NetworkInviteRepository(source, dispatchers).buy(InviteCurrency.COIN) }.exceptionOrNull()

            assertEquals(SiteError.Http(500), (exception as? SiteException)?.error)
        }
}

private class FakeInviteJsonSource(
    private val config: String = """{"success":true,"payment":{}}""",
    private val list: String = """{"success":true,"data":[],"total":0}""",
    private val buy: JsonPostResponse = JsonPostResponse(code = 200, body = """{"success":true}"""),
) : JsonApi {
    var requestedPath: String? = null
        private set
    var sent: Sent? = null
        private set

    override suspend fun getJson(
        path: String,
        referer: String,
    ): String {
        requestedPath = path
        return when {
            path == "/api/invite/config" -> config
            path.startsWith("/api/invite/list") -> list
            else -> error("unexpected read of $path")
        }
    }

    override suspend fun postJson(
        path: String,
        body: String,
        referer: String,
    ): String = error("unexpected post to $path")

    override suspend fun sendJson(
        method: String,
        path: String,
        body: String,
        referer: String,
        extraHeaders: Map<String, String>,
    ): JsonPostResponse {
        sent = Sent(method = method, path = path, body = body, headers = extraHeaders)
        return buy
    }
}

private data class Sent(
    val method: String,
    val path: String,
    val body: String,
    val headers: Map<String, String>,
)
