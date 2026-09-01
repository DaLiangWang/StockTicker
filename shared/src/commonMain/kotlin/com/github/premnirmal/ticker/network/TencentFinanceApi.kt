package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.network.data.HistoricalDataResult
import com.github.premnirmal.ticker.network.data.Quote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Multiplatform client for the Tencent Finance (腾讯财经) endpoints — a mainland-China data source
 * that covers A-shares, HK stocks and US stocks, so the Yahoo path (blocked on the mainland network)
 * is bypassed for the full watchlist.
 *
 * Quote endpoint: `GET {baseUrl}/q=sh600519,hk00700,usAAPL` — one `v_<symbol>="…";` assignment per
 * requested symbol, GB18030-encoded (see [decodeGb18030]) and split on `~` into positional fields.
 *
 * K-line endpoint: `GET https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=<symbol>,day,,,<count>,qfq`
 * returns UTF-8 JSON, mapped into the shared [HistoricalDataResult] shape.
 *
 * @param baseUrl the Tencent quote API base URL (e.g. `https://qt.gtimg.cn/`). The K-line endpoint is a
 * fixed host inside the same `ifzq.gtimg.cn` domain.
 * @param httpClient the Ktor client to use; defaults to a freshly configured client.
 */
class TencentFinanceApi(
    private val baseUrl: String,
    private val httpClient: HttpClient = createHttpClient()
) : AShareQuoteApi {

    override suspend fun getQuotes(symbols: List<String>): List<Quote> {
        if (symbols.isEmpty()) return emptyList()
        val requested = symbols.mapNotNull { sym ->
            ChinaSymbols.parse(sym)?.let { it.tencent to sym }
        }.toMap()
        if (requested.isEmpty()) return emptyList()
        val query = requested.keys.joinToString(",")
        val bytes: ByteArray = httpClient.get("${baseUrl.trimEnd('/')}/q=$query").body()
        return parseTencentQuotes(decodeGb18030(bytes), requested)
    }

    override suspend fun getChartData(
        symbol: String,
        interval: String,
        range: String
    ): HistoricalDataResult {
        val china = ChinaSymbols.parse(symbol)
            ?: throw NoSuchElementException("Not a China-market symbol: $symbol")
        // Map the Yahoo-style [range] onto Tencent's K-line period + bar count so each range shows a
        // genuinely different trend (intraday minutes for 1d, daily for 1mo, weekly for 3mo, monthly for
        // the longer ranges) instead of the same daily series for every selection.
        val (period, count) = when (range) {
            "1d" -> "min" to 240
            "14d" -> "day" to 14
            "1mo" -> "day" to 30
            "3mo" -> "week" to 14
            "1y" -> "month" to 12
            "5y" -> "month" to 60
            else -> "month" to 200
        }
        val url =
            "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=${china.tencent},${period},,,${count},qfq"
        val text: String = httpClient.get(url).body()
        val json = Json.Default.parseToJsonElement(text).jsonObject
        val node = json["data"]?.jsonObject?.get(china.tencent)?.jsonObject
            ?: json["data"]?.jsonObject?.get(symbol)?.jsonObject
        val rows = node?.get("qfqday")?.jsonArray ?: node?.get("day")?.jsonArray
            ?: throw NoSuchElementException("No K-line data for $symbol")
        val timestamps = ArrayList<Long>()
        val opens = ArrayList<Double?>()
        val closes = ArrayList<Double?>()
        val lows = ArrayList<Double?>()
        val highs = ArrayList<Double?>()
        val volumes = ArrayList<Long?>()
        for (element in rows) {
            val row = element.jsonArray
            val date = row.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
            // The chart layer treats timestamps as Unix *seconds* (matching Yahoo); the Chinese sources
            // hand back a `yyyy-MM-dd` date, so convert to epoch milliseconds then to seconds.
            timestamps.add(dateToEpochMillis(date.substringBefore(' ')) / 1000)
            opens.add(row.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull())
            closes.add(row.getOrNull(2)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull())
            highs.add(row.getOrNull(3)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull())
            lows.add(row.getOrNull(4)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull())
            volumes.add(row.getOrNull(5)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong())
        }
        return buildKlineResult(china, timestamps, opens, closes, lows, highs, volumes)
    }
}

/** Positional field indexes within a Tencent quote payload's `~`-delimited row. */
private const val IDX_NAME = 1
private const val IDX_CODE = 2
private const val IDX_LAST_PRICE = 3
private const val IDX_PREVIOUS_CLOSE = 4
private const val IDX_OPEN = 5
private const val IDX_VOLUME_LOTS = 6
private const val IDX_CHANGE = 31
private const val IDX_CHANGE_PERCENT = 32
private const val IDX_DAY_HIGH = 33
private const val IDX_DAY_LOW = 34
private const val IDX_MARKET_CAP_YI = 45

/** The minimum number of `~`-delimited fields a usable row must carry. */
private const val MIN_FIELDS = 33

/**
 * Parses a Tencent `qt.gtimg.cn` response into [Quote]s. [requested] maps each encoded symbol
 * (`sh600519`, `hk00700`, `usAAPL`) back to the original spelling the caller stored, so the watchlist
 * keeps the user's symbol and the persisted quote matches the stored ticker.
 */
internal fun parseTencentQuotes(text: String, requested: Map<String, String>): List<Quote> {
    val quotes = ArrayList<Quote>()
    val rowPattern = Regex("""v_(sh\d{6}|sz\d{6}|bj\d{6}|hk\d{4,5}|us[A-Za-z.]+)="([^"]*)"""")
    for (match in rowPattern.findAll(text)) {
        val encoded = match.groupValues[1]
        val fields = match.groupValues[2].split('~')
        if (fields.size < MIN_FIELDS) continue
        val lastTradePrice = fields.getOrNull(IDX_LAST_PRICE)?.toFloatOrNull() ?: continue
        val code = fields.getOrNull(IDX_CODE).orEmpty()
        val market = when {
            encoded.startsWith("sh") || encoded.startsWith("sz") || encoded.startsWith("bj") -> ChinaMarket.A
            encoded.startsWith("hk", ignoreCase = true) -> ChinaMarket.HK
            encoded.startsWith("us", ignoreCase = true) -> ChinaMarket.US
            else -> continue
        }
        val previousClose = fields.getOrNull(IDX_PREVIOUS_CLOSE)?.toFloatOrNull() ?: lastTradePrice
        val change = fields.getOrNull(IDX_CHANGE)?.toFloatOrNull() ?: (lastTradePrice - previousClose)
        val changePercent = fields.getOrNull(IDX_CHANGE_PERCENT)?.toFloatOrNull()
            ?: if (previousClose == 0f) 0f else ((lastTradePrice - previousClose) / previousClose) * 100f

        val quote = Quote(
            symbol = requested[encoded] ?: encoded,
            name = fields.getOrNull(IDX_NAME)?.takeIf { it.isNotBlank() } ?: code,
            lastTradePrice = lastTradePrice,
            changeInPercent = changePercent,
            change = change,
        )
        quote.stockExchange = when (market) {
            ChinaMarket.A -> {
                when (AShareSymbols.parse(encoded)?.exchange) {
                    AShareExchange.SHANGHAI -> "SHH"
                    AShareExchange.SHENZHEN -> "SHZ"
                    AShareExchange.BEIJING -> "BSE"
                    null -> "SHH"
                }
            }
            ChinaMarket.HK -> "HKG"
            ChinaMarket.US -> "NYQ"
        }
        quote.currencyCode = market.currencyCode
        quote.previousClose = previousClose
        quote.open = fields.getOrNull(IDX_OPEN)?.toFloatOrNull()
        quote.dayHigh = fields.getOrNull(IDX_DAY_HIGH)?.toFloatOrNull()
        quote.dayLow = fields.getOrNull(IDX_DAY_LOW)?.toFloatOrNull()
        quote.regularMarketVolume = fields.getOrNull(IDX_VOLUME_LOTS)?.toFloatOrNull()
            ?.let { (it * 100).toLong() }
        quote.marketCap = fields.getOrNull(IDX_MARKET_CAP_YI)?.toFloatOrNull()
            ?.let { (it * 100_000_000f).toLong() }
        quote.marketState = if (ChinaMarketHours.isOpenNow(market)) "REGULAR" else "CLOSED"
        quote.tradeable = true
        quote.triggerable = true
        quotes.add(quote)
    }
    return quotes
}
