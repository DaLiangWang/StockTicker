package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.UserPreferences
import com.github.premnirmal.ticker.components.AppClock
import com.github.premnirmal.ticker.network.data.HistoricalDataResult
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.network.data.SuggestionsNet.SuggestionNet
import org.koin.core.qualifier.named

/** Koin qualifier for the Tencent Finance A-share client. */
val A_SHARE_TENCENT = named("aShareTencent")

/** Koin qualifier for the East Money A-share client. */
val A_SHARE_EAST_MONEY = named("aShareEastMoney")

/**
 * Common contract for a mainland-China (A-share) quote source.
 *
 * [StocksApi] routes every symbol that [AShareSymbols.isAShare] recognises to whichever
 * implementation the user selected, and everything else to Yahoo. Keeping this behind an interface
 * means adding or swapping a source (Tencent, East Money, …) is a DI change rather than a change to
 * the fetch orchestration.
 */
interface AShareQuoteApi {

    /**
     * Fetches quotes for [symbols] (any recognised A-share form — `600519`, `sh600519`, `600519.SH`).
     *
     * Symbols that cannot be resolved are simply absent from the result; the fetch path treats a
     * missing symbol the same way it treats a Yahoo symbol missing from a Yahoo response.
     */
    suspend fun getQuotes(symbols: List<String>): List<Quote>

    /**
     * Searches for A-share instruments matching [query], in the shared suggestion model.
     *
     * Optional: sources that do not expose a usable search endpoint keep the default (no results),
     * and search simply falls back to Yahoo.
     */
    suspend fun getSuggestions(query: String): List<SuggestionNet> = emptyList()

    /**
     * Fetches historical K-line data for [symbol] (any A/HK/US instrument the Chinese sources cover)
     * in the shared [HistoricalDataResult] model — the same shape [ChartApi] returns for Yahoo, so the
     * chart rendering path is source-agnostic.
     */
    suspend fun getChartData(symbol: String, interval: String, range: String): HistoricalDataResult
}

/**
 * Dispatches to whichever A-share source the user picked in Settings.
 *
 * Both implementations are constructed eagerly (they are cheap, engine-backed clients) and the
 * preference is read per call, so switching sources in Settings takes effect on the next refresh
 * without restarting the app or rebuilding the DI graph.
 *
 * Symbol search always goes through East Money: it is the only one of the two sources with a
 * UTF-8 JSON search endpoint, and search results are source-independent (they only need to name a
 * symbol), so there is no value in routing them by the quote-source preference.
 */
class PreferenceAShareQuoteApi(
    private val preferences: UserPreferences,
    private val tencent: AShareQuoteApi,
    private val eastMoney: AShareQuoteApi,
) : AShareQuoteApi {

    override suspend fun getQuotes(symbols: List<String>): List<Quote> =
        when (preferences.aShareDataSourcePref) {
            UserPreferences.A_SHARE_SOURCE_EAST_MONEY -> eastMoney.getQuotes(symbols)
            else -> tencent.getQuotes(symbols)
        }

    override suspend fun getSuggestions(query: String): List<SuggestionNet> =
        eastMoney.getSuggestions(query)

    override suspend fun getChartData(
        symbol: String,
        interval: String,
        range: String
    ): HistoricalDataResult =
        when (preferences.aShareDataSourcePref) {
            UserPreferences.A_SHARE_SOURCE_EAST_MONEY -> eastMoney.getChartData(symbol, interval, range)
            else -> tencent.getChartData(symbol, interval, range)
        }
}

/**
 * A-share (SSE/SZSE/BSE) trading-hours arithmetic.
 *
 * The mainland exchanges trade Monday–Friday 09:30–11:30 and 13:00–15:00 China Standard Time
 * (UTC+8), with no DST. This is deliberately self-contained (it derives Beijing time from an epoch
 * millisecond value) because `commonMain` has no timezone database.
 */
object AShareMarketHours {

    /** China Standard Time offset from UTC, in milliseconds. */
    private const val UTC_OFFSET_MS = 8 * 60 * 60 * 1000L
    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    private const val MILLIS_PER_MINUTE = 60 * 1000L

    /** Morning session 09:30–11:30, in minutes since midnight. */
    private val MORNING_SESSION = 9 * 60 + 30..11 * 60 + 30
    /** Afternoon session 13:00–15:00, in minutes since midnight. */
    private val AFTERNOON_SESSION = 13 * 60..15 * 60

    /**
     * Whether the A-share market is currently in a trading session.
     *
     * @param nowMs epoch milliseconds; defaults to the shared [AppClock]. Exposed as a parameter so
     * it is unit-testable without a platform clock.
     */
    fun isOpenNow(nowMs: Long = AppClock.AppClockImpl.currentTimeMillis()): Boolean {
        val beijingMs = nowMs + UTC_OFFSET_MS
        // 1970-01-01 (epoch day 0) was a Thursday, so day 0 → 4 with Monday = 1 … Sunday = 7 (mod 7).
        val dayOfWeek = ((beijingMs / MILLIS_PER_DAY).toInt() + 4) % 7
        if (dayOfWeek == 0 || dayOfWeek == 6) return false // Sunday = 0, Saturday = 6
        val minuteOfDay = ((beijingMs % MILLIS_PER_DAY) / MILLIS_PER_MINUTE).toInt()
        return minuteOfDay in MORNING_SESSION || minuteOfDay in AFTERNOON_SESSION
    }
}
