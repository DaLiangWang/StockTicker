package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.components.AppClock
import com.github.premnirmal.ticker.network.data.Chart
import com.github.premnirmal.ticker.network.data.DataQuote
import com.github.premnirmal.ticker.network.data.HistoricalDataResult
import com.github.premnirmal.ticker.network.data.Indicators
import com.github.premnirmal.ticker.network.data.Meta
import com.github.premnirmal.ticker.network.data.Result

/**
 * Market classification for the mainland-China data sources (Tencent / East Money), which together
 * cover A-shares, HK stocks and US stocks — the full watchlist the app typically holds — so the Yahoo
 * path (blocked on the mainland network) is no longer on the hot path.
 *
 * Encoding mirrors the PanBar reference project:
 *  - Tencent : `sh600519`/`sz000001` (A), `hk00700` (HK, zero-padded to 5 digits), `usAAPL` (US).
 *  - EastMoney: `1.600519`/`0.000001` (A), `116.00700` (HK), `105.AAPL` (US, NASDAQ default).
 */
enum class ChinaMarket {
    A, HK, US;

    val currencyCode: String
        get() = when (this) {
            A -> "CNY"
            HK -> "HKD"
            US -> "USD"
        }
}

/**
 * A normalised symbol for one of the Chinese sources: a [market] plus the raw [code]. The source forms
 * ([tencent], [eastMoneySecId]) are derived on demand, and [canonical] is the in-app key the fetch path
 * uses to re-write results back to whatever spelling the user stored.
 */
data class ChinaSymbol(
    val market: ChinaMarket,
    val code: String,
) {

    /** `sh600519` / `hk00700` / `usAAPL` — the form Tencent's quote API expects. */
    val tencent: String
        get() = when (market) {
            ChinaMarket.A -> aShare?.tencentSymbol ?: "sh$code"
            ChinaMarket.HK -> "hk${code.padStart(5, '0')}"
            ChinaMarket.US -> "us${code.uppercase()}"
        }

    /** `1.600519` / `116.00700` / `105.AAPL` — the form East Money's `secid` parameter expects. */
    val eastMoneySecId: String
        get() = when (market) {
            ChinaMarket.A -> aShare?.eastMoneySecId ?: "1.$code"
            ChinaMarket.HK -> "116.${code.padStart(5, '0')}"
            ChinaMarket.US -> "105.${code.uppercase()}"
        }

    /** Canonical in-app form, used as the map key when re-writing results to the caller. */
    val canonical: String
        get() = when (market) {
            ChinaMarket.A -> aShare?.canonical ?: "sh$code"
            ChinaMarket.HK -> "hk${code.padStart(5, '0')}"
            ChinaMarket.US -> code.uppercase()
        }

    private val aShare: AShareSymbol? get() = AShareSymbols.parse(code)
}

object ChinaSymbols {

    /** Whether [symbol] names an A/HK/US instrument the Chinese sources can serve. */
    fun isChinaMarket(symbol: String): Boolean = parse(symbol) != null

    /**
     * Normalises [symbol] into a [ChinaSymbol], or null when it is not a market the Chinese sources
     * cover (indices like `^GSPC`, crypto, … stay on the Yahoo path). Accepts:
     *  - A-shares: `600519`, `sh600519`, `600519.SH`, `600519.XSHG` (delegated to [AShareSymbols]).
     *  - HK stocks: `0700.HK`, `hk00700`, `700` (treated as HK when it looks like a 4–5 digit code).
     *  - US stocks: any 1–6 letter code, e.g. `AAPL`, `VOO`, `BRK.B`.
     */
    fun parse(symbol: String): ChinaSymbol? {
        val s = symbol.trim()
        if (s.isEmpty()) return null

        // A-share (6-digit codes, sh/sz/bj prefixes, .SH/.SZ/.BJ/.XSHG/.XSHE suffixes).
        AShareSymbols.parse(s)?.let { return ChinaSymbol(ChinaMarket.A, it.code) }

        // HK: `.hk` suffix (any case) or `hk` prefix.
        val hkSuffix = Regex("""^(\d{1,5})[.\-_]?[hH][kK]$""")
        hkSuffix.matchEntire(s)?.let { return ChinaSymbol(ChinaMarket.HK, it.groupValues[1]) }
        if (s.startsWith("hk", ignoreCase = true)) {
            val digits = s.substring(2).filter { it.isDigit() }
            if (digits.isNotEmpty()) return ChinaSymbol(ChinaMarket.HK, digits)
        }

        // US: 1–6 letters, optionally with a `.` (e.g. BRK.B) — not digit-only, not `^`-prefixed.
        if (Regex("""^[A-Za-z][A-Za-z.\-]{0,5}$""").matches(s)) {
            return ChinaSymbol(ChinaMarket.US, s.replace(".", "").uppercase())
        }
        return null
    }
}

/**
 * Trading-hours check for the Chinese sources' three markets. Self-contained (derives local time from
 * an epoch millisecond value) because `commonMain` has no timezone database. US uses a fixed UTC-4
 * offset (a DST approximation, summer time); A-share/HK use UTC+8.
 */
object ChinaMarketHours {

    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    private const val MILLIS_PER_MINUTE = 60 * 1000L

    fun isOpenNow(market: ChinaMarket, nowMs: Long = AppClock.AppClockImpl.currentTimeMillis()): Boolean {
        val offset = when (market) {
            ChinaMarket.A -> 8 * 60 * 60 * 1000L
            ChinaMarket.HK -> 8 * 60 * 60 * 1000L
            ChinaMarket.US -> -4 * 60 * 60 * 1000L
        }
        val localMs = nowMs + offset
        // 1970-01-01 (epoch day 0) was a Thursday → day 0 maps to 4 with Monday = 1 … Sunday = 7 (mod 7).
        val dayOfWeek = ((localMs / MILLIS_PER_DAY).toInt() + 4) % 7
        if (dayOfWeek == 0 || dayOfWeek == 6) return false
        val minuteOfDay = ((localMs % MILLIS_PER_DAY) / MILLIS_PER_MINUTE).toInt()
        return when (market) {
            ChinaMarket.A -> minuteOfDay in (9 * 60 + 30..11 * 60 + 30) ||
                minuteOfDay in (13 * 60..15 * 60)

            ChinaMarket.HK -> minuteOfDay in (9 * 60 + 30..12 * 60) ||
                minuteOfDay in (13 * 60..16 * 60)

            ChinaMarket.US -> minuteOfDay in (9 * 60 + 30..16 * 60)
        }
    }
}

/**
 * Parses a `yyyy-MM-dd` date string into epoch milliseconds (UTC midnight). `commonMain` has no
 * `java.time`/`NSDateFormatter`, so this uses the standard Julian Day Number conversion.
 */
internal fun dateToEpochMillis(date: String): Long {
    val parts = date.split("-")
    if (parts.size < 3) return 0L
    val y = parts[0].toIntOrNull() ?: return 0L
    val m = parts[1].toIntOrNull() ?: return 0L
    val d = parts[2].toIntOrNull() ?: return 0L
    val a = (14 - m) / 12
    val y2 = y + 4800 - a
    val m2 = m + 12 * a - 3
    val jdn = d + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045
    // 2440588 is the Julian Day Number of 1970-01-01.
    return (jdn - 2440588) * 86400000L
}

/** Maps a Yahoo-style [range] param to a Tencent K-line bar count. */
internal fun rangeToCount(range: String): Int = when (range) {
    "1d", "5d" -> 20
    "14d" -> 20
    "1mo" -> 23
    "3mo" -> 66
    "1y" -> 250
    "5y" -> 1200
    "max" -> 2000
    else -> 320
}

/**
 * Builds a [HistoricalDataResult] (the same shape [ChartApi] returns for Yahoo) from parallel OHLCV
 * lists. Shared by the Tencent and East Money K-line implementations so the chart rendering path
 * stays source-agnostic.
 */
internal fun buildKlineResult(
    china: ChinaSymbol,
    timestamps: List<Long>,
    opens: List<Double?>,
    closes: List<Double?>,
    lows: List<Double?>,
    highs: List<Double?>,
    volumes: List<Long?>,
): HistoricalDataResult {
    val lastClose = closes.lastOrNull() ?: 0.0
    val firstClose = closes.firstOrNull() ?: 0.0
    return HistoricalDataResult(
        Chart(
            result = listOf(
                Result(
                    meta = Meta(
                        currency = china.market.currencyCode,
                        symbol = china.canonical,
                        regularMarketPrice = lastClose,
                        chartPreviousClose = firstClose,
                    ),
                    timestamp = timestamps,
                    indicators = Indicators(
                        quote = listOf(DataQuote(close = closes, open = opens, low = lows, high = highs, volume = volumes))
                    ),
                )
            ),
            error = null,
        )
    )
}
