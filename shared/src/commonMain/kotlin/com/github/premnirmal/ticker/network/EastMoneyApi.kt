package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.network.data.HistoricalDataResult
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.network.data.SuggestionsNet.SuggestionNet
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.appendPathSegments
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Multiplatform client for the East Money (东方财富) endpoints — a mainland-China data source that
 * covers A-shares, HK stocks and US stocks, so the Yahoo path (blocked on the mainland network) is
 * bypassed for the full watchlist.
 *
 * Quote endpoint: `GET {baseUrl}/ulist.np/get?secids=1.600519,116.00700,105.AAPL&fields=…`
 * (one row per requested `secid` in `data.diff`; UTF-8 JSON, no legacy charset handling).
 *
 * K-line endpoint: `GET https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=…&klt=101&fqt=1`
 * returns UTF-8 JSON; mapped into the shared [HistoricalDataResult] shape.
 *
 * @param baseUrl the East Money quote API base URL (e.g. `https://push2.eastmoney.com/api/qt/`).
 * @param httpClient the Ktor client to use; defaults to a freshly configured client.
 */
class EastMoneyApi(
    private val baseUrl: String,
    private val httpClient: HttpClient = createHttpClient()
) : AShareQuoteApi {

    override suspend fun getQuotes(symbols: List<String>): List<Quote> {
        if (symbols.isEmpty()) return emptyList()
        val requested = symbols.mapNotNull { sym ->
            ChinaSymbols.parse(sym)?.let { it.eastMoneySecId to sym }
        }.toMap()
        if (requested.isEmpty()) return emptyList()
        val response: EastMoneyEnvelope = httpClient.get(baseUrl.trimEnd('/')) {
            url { appendPathSegments("ulist.np", "get") }
            parameter("fltt", "2")
            parameter("invt", "2")
            parameter("ut", UT)
            parameter("fields", FIELDS)
            parameter("secids", requested.keys.joinToString(","))
        }.body()
        return response.toQuotes(requested)
    }

    /**
     * Searches East Money for A-share instruments matching [query] (a partial code, a pinyin stem or
     * part of the Chinese name) and maps the hits into the shared [SuggestionNet] model. Failures
     * degrade to an empty list — the caller merges these with Yahoo's, so a broken search endpoint
     * never breaks search.
     */
    override suspend fun getSuggestions(query: String): List<SuggestionNet> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val response: EastMoneySuggestResponse = httpClient.get("https://searchapi.eastmoney.com/api/suggest/get") {
                parameter("input", trimmed)
                parameter("type", "14")
                parameter("token", SEARCH_TOKEN)
                parameter("count", 10)
            }.body()
            response.toSuggestions()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getChartData(
        symbol: String,
        interval: String,
        range: String
    ): HistoricalDataResult {
        val china = ChinaSymbols.parse(symbol)
            ?: throw NoSuchElementException("Not a China-market symbol: $symbol")
        val response: EastMoneyKlineResp = httpClient.get("https://push2his.eastmoney.com/api/qt/stock/kline/get") {
            parameter("secid", china.eastMoneySecId)
            parameter("fields1", "f1,f2,f3,f4,f5,f6")
            parameter("fields2", "f51,f52,f53,f54,f55,f56")
            parameter("klt", eastMoneyKlt(range))
            parameter("fqt", "1")
            parameter("beg", "0")
            parameter("end", "20500101")
            parameter("lmt", eastMoneyKlineCount(range))
        }.body()
        val klines = response.data?.klines
            ?: throw NoSuchElementException("No K-line data for $symbol")
        val timestamps = ArrayList<Long>()
        val opens = ArrayList<Double?>()
        val closes = ArrayList<Double?>()
        val lows = ArrayList<Double?>()
        val highs = ArrayList<Double?>()
        val volumes = ArrayList<Long?>()
        for (line in klines) {
            val cols = line.split(",")
            if (cols.size < 6) continue
            // The chart layer treats timestamps as Unix *seconds* (matching Yahoo); East Money returns a
            // `yyyy-MM-dd [HH:mm]` string, so trim any time part, convert to epoch ms, then to seconds.
            timestamps.add(dateToEpochMillis(cols[0].substringBefore(' ')) / 1000)
            opens.add(cols[1].toDoubleOrNull())
            closes.add(cols[2].toDoubleOrNull())
            lows.add(cols[3].toDoubleOrNull())
            highs.add(cols[4].toDoubleOrNull())
            // East Money reports volume in 手 (lots of 100 shares); Yahoo uses shares.
            volumes.add(cols[5].toLongOrNull()?.times(100))
        }
        return buildKlineResult(china, timestamps, opens, closes, lows, highs, volumes)
    }

    private companion object {
        private const val UT = "fa5fd1943c7b386f172d6893dbfba10b"

        /** East Money's public web search token; also sent by the site's own search box. */
        private const val SEARCH_TOKEN = "D43BF722C8E33BDC906FB84D85E326E8"

        /**
         * East Money's field ids: f2 last price, f3 change %, f4 change, f5 volume (lots),
         * f6 turnover (CNY), f12 code, f13 market, f14 name, f15 high, f16 low, f17 open,
         * f18 previous close, f20 total market cap, f21 free-float market cap.
         */
        private const val FIELDS = "f1,f2,f3,f4,f5,f6,f12,f13,f14,f15,f16,f17,f18,f20,f21"
    }
}

@Serializable
private data class EastMoneyEnvelope(
    val rc: Int? = null,
    val data: EastMoneyData? = null,
)

@Serializable
private data class EastMoneyData(
    val diff: JsonElement? = null,
)

private fun EastMoneyEnvelope.toQuotes(requested: Map<String, String>): List<Quote> {
    val rows: List<JsonObject> = when (val diff = data?.diff) {
        is JsonArray -> diff.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(diff)
        else -> emptyList()
    }
    return rows.mapNotNull { it.toQuote(requested) }
}

private fun JsonObject.toQuote(requested: Map<String, String>): Quote? {
    // `f12` (code) and `f13` (market) identify the instrument; without them the row is unusable.
    val code = this["f12"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val market = this["f13"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
    val secid = "$market.$code"
    val chinaMarket = when (market) {
        1, 0 -> ChinaMarket.A
        116 -> ChinaMarket.HK
        105, 106, 107 -> ChinaMarket.US
        else -> ChinaMarket.A
    }
    val lastTradePrice = float("f2") ?: return null
    val previousClose = float("f18") ?: lastTradePrice
    val change = float("f4") ?: (lastTradePrice - previousClose)
    val changePercent = float("f3")
        ?: if (previousClose == 0f) 0f else ((lastTradePrice - previousClose) / previousClose) * 100f

    val quote = Quote(
        symbol = requested[secid] ?: code,
        name = this["f14"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: code,
        lastTradePrice = lastTradePrice,
        changeInPercent = changePercent,
        change = change,
    )
    quote.stockExchange = when (market) {
        1 -> "SHH"
        0 -> "SHZ"
        116 -> "HKG"
        else -> "NYQ"
    }
    quote.currencyCode = chinaMarket.currencyCode
    quote.previousClose = previousClose
    quote.open = float("f17")
    quote.dayHigh = float("f15")
    quote.dayLow = float("f16")
    quote.regularMarketVolume = float("f5")?.let { (it * 100).toLong() }
    quote.marketCap = float("f20")?.toLong()
    quote.marketState = if (ChinaMarketHours.isOpenNow(chinaMarket)) "REGULAR" else "CLOSED"
    quote.tradeable = true
    quote.triggerable = true
    return quote
}

private fun JsonObject.float(field: String): Float? =
    this[field]?.jsonPrimitive?.contentOrNull?.trim()?.toFloatOrNull()

// --- Symbol search ---

@Serializable
private data class EastMoneySuggestResponse(
    @SerialName("QuotationCodeTable") val quotationCodeTable: EastMoneySuggestTable? = null,
)

@Serializable
private data class EastMoneySuggestTable(
    @SerialName("Data") val data: List<EastMoneySuggestItem>? = null,
)

@Serializable
private data class EastMoneySuggestItem(
    @SerialName("Code") val code: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("MktNum") val market: String? = null,
    @SerialName("SecurityTypeName") val securityTypeName: String? = null,
)

private fun EastMoneySuggestResponse.toSuggestions(): List<SuggestionNet> =
    quotationCodeTable?.data.orEmpty().mapNotNull { item ->
        val code = item.code?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val aShare = AShareSymbols.parse(code) ?: return@mapNotNull null
        SuggestionNet(symbol = aShare.canonical).apply {
            name = item.name.orEmpty()
            exch = if (item.market == "1") "SHH" else "SHZ"
            exchDisp = item.securityTypeName.orEmpty()
            typeDisp = item.securityTypeName.orEmpty()
            isYahooFinance = false
        }
    }

// --- K-line ---

@Serializable
private data class EastMoneyKlineResp(
    val data: EastMoneyKlineData? = null,
)

@Serializable
private data class EastMoneyKlineData(
    val code: String? = null,
    val name: String? = null,
    val klines: List<String>? = null,
)

/** Maps a Yahoo-style [range] onto East Money's `klt` (K-line type) parameter. */
private fun eastMoneyKlt(range: String): String = when (range) {
    "1d" -> "5" // 5-minute
    "14d" -> "101" // daily
    "1mo" -> "101"
    "3mo" -> "102" // weekly
    "1y" -> "103" // monthly
    "5y" -> "103"
    else -> "106" // yearly
}

/** Maps a Yahoo-style [range] onto East Money's `lmt` (returned bar count) parameter. */
private fun eastMoneyKlineCount(range: String): String = when (range) {
    "1d" -> "320"
    "14d" -> "14"
    "1mo" -> "30"
    "3mo" -> "14"
    "1y" -> "12"
    "5y" -> "60"
    else -> "20"
}
