package com.github.premnirmal.ticker.network

/**
 * A-share (mainland China) symbol handling.
 *
 * Users type A-share symbols in several shapes, and each data source wants a different one:
 *
 * | Shape            | Example        | Tencent   | East Money | Yahoo     |
 * |------------------|----------------|-----------|------------|-----------|
 * | bare 6-digit     | `600519`       | `sh600519`| `1.600519` | `600519.SS` |
 * | prefixed         | `sh600519`     | `sh600519`| `1.600519` | `600519.SS` |
 * | suffixed         | `600519.SH`    | `sh600519`| `1.600519` | `600519.SS` |
 * | Yahoo style      | `600519.XSHG`  | `sh600519`| `1.600519` | `600519.SS` |
 *
 * [AShareSymbols.parse] normalises all of them into an [AShareSymbol], whose properties produce the
 * per-source form; [AShareSymbols.isAShare] is the cheap predicate the fetch path uses to route a
 * symbol to the A-share source instead of Yahoo.
 */
enum class AShareExchange(
    /** The lower-case market prefix used by Tencent's quote API. */
    val prefix: String,
    /** East Money's numeric market id, as used in a `secid` (`1` = Shanghai, `0` = Shenzhen). */
    val eastMoneyMarket: Int,
    /** The Yahoo Finance suffix for this exchange. */
    val yahooSuffix: String,
    /** The suffix users type (and brokers export) for this exchange. */
    val displaySuffix: String,
) {
    SHANGHAI(prefix = "sh", eastMoneyMarket = 1, yahooSuffix = "SS", displaySuffix = ".SH"),
    SHENZHEN(prefix = "sz", eastMoneyMarket = 0, yahooSuffix = "SZ", displaySuffix = ".SZ"),
    BEIJING(prefix = "bj", eastMoneyMarket = 0, yahooSuffix = "BJ", displaySuffix = ".BJ"),
}

/** A normalised A-share symbol: an [exchange] plus the 6-digit [code]. */
data class AShareSymbol(
    val exchange: AShareExchange,
    val code: String,
) {
    /** `sh600519` — the form Tencent's quote API expects. */
    val tencentSymbol: String get() = "${exchange.prefix}$code"

    /** `1.600519` — the form East Money's `secid` parameter expects. */
    val eastMoneySecId: String get() = "${exchange.eastMoneyMarket}.$code"

    /** `600519.SS` — the Yahoo Finance form (kept for interoperability with exported portfolios). */
    val yahooSymbol: String get() = "$code.${exchange.yahooSuffix}"

    /** The canonical in-app form, with the exchange prefix: `sh600519`. */
    val canonical: String get() = tencentSymbol
}

object AShareSymbols {

    private val BARE_CODE = Regex("""^(\d{6})$""")
    private val PREFIXED_CODE = Regex("""^(sh|sz|bj)(\d{6})$""")
    private val SUFFIXED_CODE = Regex("""^(\d{6})[.\-_](sh|sz|ss|sz|bj|xshg|xshe|bse)$""")

    /**
     * Whether [symbol] names a mainland-China instrument.
     *
     * Anything that is not recognised here (US tickers, `^GSPC`, crypto, …) stays on the Yahoo path,
     * so enabling A-share support never changes how existing symbols are fetched.
     */
    fun isAShare(symbol: String): Boolean = parse(symbol) != null

    /**
     * Normalises [symbol] into an [AShareSymbol], or returns `null` when it is not an A-share
     * instrument. Accepts bare 6-digit codes, `sh`/`sz`/`bj` prefixes, `.SH`/`.SZ`/`.BJ` suffixes and
     * the Yahoo `.XSHG`/`.XSHE` suffixes.
     */
    fun parse(symbol: String): AShareSymbol? {
        val value = symbol.trim().lowercase()
        if (value.isEmpty()) return null

        PREFIXED_CODE.matchEntire(value)?.let { match ->
            val exchange = exchangeForPrefix(match.groupValues[1]) ?: return null
            return AShareSymbol(exchange, match.groupValues[2])
        }

        SUFFIXED_CODE.matchEntire(value)?.let { match ->
            val exchange = when (val suffix = match.groupValues[2]) {
                "sh", "ss", "xshg" -> AShareExchange.SHANGHAI
                "sz", "xshe" -> AShareExchange.SHENZHEN
                "bj", "bse" -> AShareExchange.BEIJING
                else -> return null
            }
            return AShareSymbol(exchange, match.groupValues[1])
        }

        BARE_CODE.matchEntire(value)?.let { match ->
            return AShareSymbol(exchangeForCode(match.groupValues[1]), match.groupValues[1])
        }

        return null
    }

    /**
     * Routes a 6-digit code to its exchange using the numbering plan:
     * `6`/`9` → Shanghai (incl. `688` STAR Market), `0`/`3` → Shenzhen (incl. `300` ChiNext),
     * `4`/`8` → Beijing Stock Exchange, `5`/`1` → Shanghai/Shenzhen funds and ETFs.
     */
    fun exchangeForCode(code: String): AShareExchange = when (code.firstOrNull()) {
        '6', '9', '5' -> AShareExchange.SHANGHAI
        '0', '3', '1' -> AShareExchange.SHENZHEN
        '4', '8' -> AShareExchange.BEIJING
        else -> AShareExchange.SHANGHAI
    }

    private fun exchangeForPrefix(prefix: String): AShareExchange? = when (prefix) {
        "sh" -> AShareExchange.SHANGHAI
        "sz" -> AShareExchange.SHENZHEN
        "bj" -> AShareExchange.BEIJING
        else -> null
    }
}
