package io.github.nodyssey.core

import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LotteryFloor(
    @SerialName("member_id") val uid: Long,
    @SerialName("floor_id") val floor: Int,
    @SerialName("member_name") val name: String = "",
)

/**
 * Port of NodeSeek's lucky.f7803609f35861b4ec4d.js, measured 2026-11-02.
 * The site has no winners endpoint: floor-data + drand + cyrb128/sfc32 are its public algorithm.
 * Keep the 32-bit overflow, pre-shuffle duplicate marking and original array length intact.
 */
object LotteryResults {
    const val BEACON_BASE = "https://api.drand.sh/8990e7a9aaed2ffed73dbd7092123d6f289930540d7651336225dc172e51b2ce/public/"
    private val json = Json { ignoreUnknownKeys = true }

    fun round(drawAtMillis: Long): Long = (drawAtMillis - 1_595_431_050_000L) / 30_000L

    fun floors(body: String): List<LotteryFloor> {
        val response = try {
            json.decodeFromString<FloorResponse>(body)
        } catch (_: IllegalArgumentException) {
            throw SiteException(SiteError.Unparsable)
        }
        if (!response.success || response.data == null) throw SiteException(SiteError.Unparsable)
        val floors = response.data
        if (floors.any { it.uid <= 0 || it.floor < 0 } || floors.map { it.floor }.distinct().size != floors.size) {
            throw SiteException(SiteError.Unparsable)
        }
        return floors
    }

    fun randomness(body: String, expectedRound: Long): String {
        val beacon = try {
            json.decodeFromString<Beacon>(body)
        } catch (_: IllegalArgumentException) {
            throw SiteException(SiteError.Unparsable)
        }
        if (beacon.round != expectedRound || !beacon.randomness.matches(Regex("[0-9a-f]{64}"))) {
            throw SiteException(SiteError.Unparsable)
        }
        return beacon.randomness
    }

    fun winners(params: LuckyDrawParams, floors: List<LotteryFloor>, randomness: String): List<LotteryFloor> {
        val seen = mutableSetOf<Long>()
        // The official page tests the ARRAY INDEX here, and the floor number during selection.
        // Filtering out ineligible floors before shuffling changes every subsequent winner.
        val duplicate = floors.mapIndexed { index, floor -> index >= params.startFloor && !seen.add(floor.uid) }
        return order(randomness, floors.size).asSequence()
            .filter { floors[it].floor >= params.startFloor && (!params.dedupeFloors || !duplicate[it]) }
            .take(params.prizeCount)
            .map { floors[it] }
            .sortedBy { it.floor }
            .toList()
    }

    internal fun order(seed: String, size: Int): List<Int> {
        var a = 1779033703
        var b = 3144134277L.toInt()
        var c = 1013904242
        var d = 2773480762L.toInt()
        for (char in seed) {
            val value = char.code
            a = b xor ((a xor value) * 597399067)
            b = c xor ((b xor value) * 2869860233L.toInt())
            c = d xor ((c xor value) * 951274213)
            d = a xor ((d xor value) * 2716044179L.toInt())
        }
        a = (c xor (a ushr 18)) * 597399067
        b = (d xor (b ushr 22)) * 2869860233L.toInt()
        c = (a xor (c ushr 17)) * 951274213
        d = (b xor (d ushr 19)) * 2716044179L.toInt()
        val first = a xor b xor c xor d
        b = b xor a
        c = c xor a
        d = d xor a
        a = first
        val values = LongArray(size) {
            var sum = a + b
            a = b xor (b ushr 9)
            b = c + (c shl 3)
            c = (c shl 21) or (c ushr 11)
            d += 1
            sum += d
            c += sum
            sum.toLong() and 0xffffffffL
        }
        return values.indices.sortedWith(compareBy<Int> { values[it] }.thenBy { it })
    }

    @Serializable
    private data class FloorResponse(val success: Boolean, val data: List<LotteryFloor>? = null)

    @Serializable
    private data class Beacon(val round: Long, val randomness: String)
}
