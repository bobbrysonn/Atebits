package dev.bobbrysonn.atebits.network

import android.util.Base64
import dev.bobbrysonn.atebits.Constants
import dev.bobbrysonn.atebits.data.AuthRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates the `x-client-transaction-id` header X requires on its newer
 * GraphQL query ids (older ones are grandfathered; new ones 404 without it).
 *
 * The inputs come from the logged-in home page: a base64 verification key, an
 * animation curve encoded in the loading-spinner SVG paths, and byte indices
 * pulled from the `ondemand.s` JS chunk. Those are fetched once and cached;
 * only the timestamp, hash and a random byte vary per request.
 *
 * Ported from the reference Python implementation (XClientTransaction), which
 * in turn mirrors x.com's own client-side JS.
 */
class TransactionIdGenerator(private val authRepository: AuthRepository) {

    private class Bundle(val keyBytes: List<Int>, val animationKey: String)

    @Volatile
    private var bundle: Bundle? = null

    // Bare client: the authenticated OkHttp stack installs the interceptor that
    // calls into this class, so bootstrapping through it would recurse.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches and caches the key/animation bundle ahead of time. Bootstrapping
     * lazily inside the interceptor makes the triggering request pay for two
     * extra fetches (~280KB), which can blow its timeout.
     */
    fun prewarm() {
        if (bundle != null) return
        runCatching { transactionId("GET", "/") }
    }

    fun transactionId(method: String, path: String): String? {
        val current = bundle ?: synchronized(this) {
            bundle ?: runCatching { bootstrap() }
                .onFailure { println("TransactionIdGenerator: bootstrap failed: ${it.message}") }
                .getOrNull()?.also { bundle = it }
        } ?: return null

        val timeNow = floor((System.currentTimeMillis() - EPOCH_MS) / 1000.0).toLong()
        val timeBytes = (0 until 4).map { ((timeNow shr (it * 8)) and 0xFF).toInt() }
        val payload = "$method!$path!$timeNow$KEYWORD${current.animationKey}"
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        val hashBytes = hash.take(16).map { it.toInt() and 0xFF }

        val randomByte = Random.nextInt(0, 256)
        val values = current.keyBytes + timeBytes + hashBytes + ADDITIONAL_RANDOM_NUMBER
        val out = ByteArray(values.size + 1)
        out[0] = randomByte.toByte()
        values.forEachIndexed { i, value -> out[i + 1] = (value xor randomByte).toByte() }
        return Base64.encodeToString(out, Base64.NO_WRAP).trimEnd('=')
    }

    /** Drops the cached key/animation pair so the next call refetches (X redeploys rotate them). */
    fun invalidate() {
        bundle = null
    }

    private fun bootstrap(): Bundle {
        val html = fetch("https://x.com/home")
        val key = Regex("""name="twitter-site-verification" content="([^"]+)"""")
            .find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("no twitter-site-verification meta")
        val keyBytes = Base64.decode(key, Base64.DEFAULT).map { it.toInt() and 0xFF }

        // Webpack maps the chunk name and its hash under the same numeric id
        val chunkId = Regex(""",(\d+):["']ondemand\.s["']""")
            .find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("no ondemand.s chunk id")
        val hash = Regex(""",$chunkId:"([0-9a-f]+)"""")
            .find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("no ondemand.s chunk hash")
        val js = fetch("https://abs.twimg.com/responsive-web/client-web/ondemand.s.${hash}a.js")

        val indices = Regex("""\(\w\[(\d{1,2})],\s*16\)""")
            .findAll(js).map { it.groupValues[1].toInt() }.toList()
        if (indices.isEmpty()) throw IllegalStateException("no key byte indices")
        val rowIndexKey = indices.first()
        val keyByteIndices = indices.drop(1)

        val animationKey = animationKey(keyBytes, html, rowIndexKey, keyByteIndices)
        return Bundle(keyBytes, animationKey)
    }

    private fun fetch(url: String): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", Constants.USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
        authRepository.getSession()?.let { builder.header("Cookie", it.cookieString) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("GET $url -> ${response.code}")
            return response.body?.string() ?: throw IllegalStateException("empty body for $url")
        }
    }

    private fun animationKey(
        keyBytes: List<Int>,
        html: String,
        rowIndexKey: Int,
        keyByteIndices: List<Int>
    ): String {
        val rowIndex = keyBytes[rowIndexKey] % 16
        val frameTimeRaw = keyByteIndices.fold(1) { acc, index -> acc * (keyBytes[index] % 16) }
        val frameTime = jsRound(frameTimeRaw / 10.0) * 10
        val frameRow = frames2d(keyBytes, html)[rowIndex]
        return animate(frameRow, frameTime.toDouble() / TOTAL_TIME)
    }

    /** The animation curve lives in the second <path> of the chosen spinner SVG. */
    private fun frames2d(keyBytes: List<Int>, html: String): List<List<Int>> {
        val svgs = Regex("""id="loading-x-anim-\d".*?</svg>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { it.value }.toList()
        if (svgs.isEmpty()) throw IllegalStateException("no loading-x-anim frames")
        val svg = svgs[keyBytes[5] % 4]
        val paths = Regex("""<path[^>]*\sd="([^"]*)"""").findAll(svg)
            .map { it.groupValues[1] }.toList()
        if (paths.size < 2) throw IllegalStateException("spinner svg has no curve path")
        // Drops the leading "M 10,30 C" before splitting the curve segments
        return paths[1].substring(9).split("C").map { segment ->
            Regex("""[^\d]+""").replace(segment, " ").trim()
                .split(" ").filter { it.isNotEmpty() }.map { it.toInt() }
        }
    }

    private fun animate(frames: List<Int>, targetTime: Double): String {
        val fromColor = frames.take(3).map { it.toDouble() } + 1.0
        val toColor = frames.subList(3, 6).map { it.toDouble() } + 1.0
        val toRotation = solve(frames[6].toDouble(), 60.0, 360.0, true)
        val curves = frames.drop(7)
            .mapIndexed { i, item -> solve(item.toDouble(), isOdd(i), 1.0, false) }

        val value = Cubic(curves).valueAt(targetTime)
        val color = fromColor.zip(toColor) { from, to -> interpolate(from, to, value) }
            .map { it.coerceIn(0.0, 255.0) }
        val rotation = interpolate(0.0, toRotation, value)

        val parts = mutableListOf<String>()
        color.dropLast(1).forEach { parts.add(jsRound(it).toString(16)) }
        rotationMatrix(rotation).forEach { entry ->
            // -0.0 is not < 0, so it falls through to floatToHex -> "" -> "0"
            var rounded = round2(entry)
            if (rounded < 0) rounded = -rounded
            val hex = floatToHex(rounded)
            parts.add(
                when {
                    hex.startsWith(".") -> "0$hex".lowercase()
                    hex.isEmpty() -> "0"
                    else -> hex
                }
            )
        }
        parts.add("0")
        parts.add("0")
        return Regex("""[.-]""").replace(parts.joinToString(""), "")
    }

    private fun solve(value: Double, minVal: Double, maxVal: Double, rounding: Boolean): Double {
        val result = value * (maxVal - minVal) / 255 + minVal
        return if (rounding) floor(result) else round2(result)
    }

    private fun interpolate(from: Double, to: Double, f: Double) = from * (1 - f) + to * f

    private fun rotationMatrix(degrees: Double): List<Double> {
        val rad = degrees * PI / 180.0
        return listOf(cos(rad), -sin(rad), sin(rad), cos(rad))
    }

    private fun isOdd(num: Int) = if (num % 2 != 0) -1.0 else 0.0

    private fun round2(value: Double) = round(value * 100) / 100.0

    // JS Math.round semantics (ties toward +infinity), which is what the
    // browser client uses — not Kotlin's Math.rint / banker's rounding.
    private fun jsRound(value: Double) = floor(value + 0.5).toInt()

    /** Manual hex expansion of a double, matching the reference byte-for-byte. */
    private fun floatToHex(input: Double): String {
        var x = input
        val result = StringBuilder()
        var quotient = x.toInt()
        var fraction = x - quotient
        while (quotient > 0) {
            quotient = (x / 16).toInt()
            val remainder = (x - quotient * 16).toInt()
            result.insert(0, if (remainder > 9) ('A' + (remainder - 10)) else ('0' + remainder))
            x = quotient.toDouble()
        }
        if (fraction == 0.0) return result.toString()
        result.append('.')
        while (fraction > 0) {
            fraction *= 16
            val integer = fraction.toInt()
            fraction -= integer
            result.append(if (integer > 9) ('A' + (integer - 10)) else ('0' + integer))
        }
        return result.toString()
    }

    private class Cubic(private val curves: List<Double>) {
        fun valueAt(time: Double): Double {
            var start = 0.0
            var end = 1.0
            var mid = 0.0
            if (time <= 0.0) {
                var startGradient = 0.0
                if (curves[0] > 0.0) startGradient = curves[1] / curves[0]
                else if (curves[1] == 0.0 && curves[2] > 0.0) startGradient = curves[3] / curves[2]
                return startGradient * time
            }
            if (time >= 1.0) {
                var endGradient = 0.0
                if (curves[2] < 1.0) endGradient = (curves[3] - 1.0) / (curves[2] - 1.0)
                else if (curves[2] == 1.0 && curves[0] < 1.0) endGradient = (curves[1] - 1.0) / (curves[0] - 1.0)
                return 1.0 + endGradient * (time - 1.0)
            }
            while (start < end) {
                mid = (start + end) / 2
                val estimate = calculate(curves[0], curves[2], mid)
                if (abs(time - estimate) < 0.00001) return calculate(curves[1], curves[3], mid)
                if (estimate < time) start = mid else end = mid
            }
            return calculate(curves[1], curves[3], mid)
        }

        private fun calculate(a: Double, b: Double, m: Double) =
            3.0 * a * (1 - m) * (1 - m) * m + 3.0 * b * (1 - m) * m * m + m * m * m
    }

    companion object {
        @Volatile
        private var instance: TransactionIdGenerator? = null

        /** One generator app-wide: each screen builds its own repository (and
         *  interceptor), and they must share the cached bundle. */
        fun shared(authRepository: AuthRepository): TransactionIdGenerator =
            instance ?: synchronized(this) {
                instance ?: TransactionIdGenerator(authRepository).also { instance = it }
            }

        private const val KEYWORD = "obfiowerehiring"
        private const val ADDITIONAL_RANDOM_NUMBER = 3
        private const val EPOCH_MS = 1682924400L * 1000
        private const val TOTAL_TIME = 4096
    }
}
