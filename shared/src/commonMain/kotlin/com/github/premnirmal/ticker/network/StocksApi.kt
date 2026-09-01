package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.components.AppLogger
import com.github.premnirmal.ticker.components.ioDispatcher
import com.github.premnirmal.ticker.model.FetchException
import com.github.premnirmal.ticker.model.FetchResult
import com.github.premnirmal.ticker.network.AShareExchange
import com.github.premnirmal.ticker.network.AShareSymbols
import com.github.premnirmal.ticker.network.ChinaSymbols
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.network.data.SuggestionsNet.SuggestionNet
import kotlinx.coroutines.withContext

/**
 * Orchestrates quote/suggestion fetching through the domestic (mainland-China) data sources — Tencent
 * Finance and East Money — exposed via [AShareQuoteApi]. Every symbol (A-share, HK, US or other
 * instruments the Chinese sources cover) is routed to [AShareQuoteApi]; Yahoo Finance was removed so
 * only the domestic sources remain. The public contract is unchanged so existing `:app` callers do not
 * need to change.
 *
 * Created by premnirmal on 3/3/16.
 */
class StocksApi(
    private val aShareQuoteApi: AShareQuoteApi
) {

    private companion object {
        private const val TAG = "[StocksApi]"
        private const val MAX_A_SHARE_BATCH = 60
    }

    suspend fun getSuggestions(query: String): FetchResult<List<SuggestionNet>> =
        withContext(ioDispatcher) {
            val suggestionList = ArrayList<SuggestionNet>()
            var sawFailure = false
            try {
                suggestionList.addAll(localSuggestions(query))
            } catch (e: Exception) {
                AppLogger.e(e, "$TAG A-share suggestion lookup failed")
                sawFailure = true
            }
            try {
                suggestionList.addAll(aShareQuoteApi.getSuggestions(query))
            } catch (e: Exception) {
                AppLogger.e(e, "$TAG suggestion lookup failed")
                sawFailure = true
            }
            if (suggestionList.isEmpty() && sawFailure) {
                return@withContext FetchResult.failure(FetchException("Error fetching"))
            }
            return@withContext FetchResult.success<List<SuggestionNet>>(suggestionList)
        }

    /**
     * Resolves a complete A-share code (`600519`, `sh600519`, `600519.SH`) to a local suggestion so the
     * symbol is always offered even with no network.
     */
    private fun localSuggestions(query: String): List<SuggestionNet> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val aShare = AShareSymbols.parse(trimmed) ?: return emptyList()
        return listOf(
            SuggestionNet(symbol = aShare.canonical).apply {
                exch = when (aShare.exchange) {
                    AShareExchange.SHANGHAI -> "SHH"
                    AShareExchange.SHENZHEN -> "SHZ"
                    AShareExchange.BEIJING -> "BSE"
                }
                exchDisp = aShare.exchange.displaySuffix
                typeDisp = aShare.exchange.displaySuffix
            }
        )
    }

    suspend fun getStocks(tickerList: List<String>): FetchResult<List<Quote>> =
        withContext(ioDispatcher) {
            try {
                AppLogger.d("$TAG getStocks: requesting ${tickerList.size} symbols")
                val quotesBySymbol = fetchQuotes(tickerList)
                val quotes = quotesBySymbol.toOrderedList(tickerList)
                return@withContext FetchResult.success(quotes)
            } catch (ex: Exception) {
                AppLogger.e(ex, "$TAG getStocks failed")
                return@withContext FetchResult.failure(FetchException("Failed to fetch", ex))
            }
        }

    suspend fun getStock(ticker: String): FetchResult<Quote> =
        withContext(ioDispatcher) {
            try {
                val quote = fetchQuotes(listOf(ticker))[ticker]
                    ?: return@withContext FetchResult.failure(FetchException("Failed to fetch $ticker"))
                return@withContext FetchResult.success(quote)
            } catch (ex: Exception) {
                AppLogger.e(ex)
                return@withContext FetchResult.failure(FetchException("Failed to fetch $ticker", ex))
            }
        }

    private suspend fun fetchQuotes(symbols: List<String>): Map<String, Quote> =
        withContext(ioDispatcher) {
            try {
                AppLogger.d("$TAG fetchQuotes: requesting ${symbols.size} symbols")
                val result = LinkedHashMap<String, Quote>()
                symbols.chunked(MAX_A_SHARE_BATCH).forEach { batch ->
                    val requested = batch.associateBy { canonicalKey(it) }
                    aShareQuoteApi.getQuotes(batch).forEach { quote ->
                        // Sources echo back whichever spelling we sent (a bare `600022` comes back
                        // bare), so the quote must be matched — and the result keyed — by its
                        // canonical form: [toOrderedList] looks quotes up by that same key, and
                        // keying by the raw symbol silently dropped every bare A-share code.
                        val key = canonicalKey(quote.symbol)
                        val symbol = requested[key] ?: quote.symbol
                        quote.symbol = symbol
                        result[key] = quote
                    }
                }
                result
            } catch (e: Exception) {
                AppLogger.e(e, "$TAG fetch failed")
                emptyMap()
            }
        }

    private fun Map<String, Quote>.toOrderedList(tickerList: List<String>): List<Quote> {
        val quotes = ArrayList<Quote>()
        for (symbol in tickerList) {
            this[canonicalKey(symbol)]?.let { quotes.add(it) }
        }
        return quotes
    }

    /**
     * Canonical lookup key for [symbol], so every accepted spelling of one instrument (bare `600022`,
     * `sh600022`, `600022.SH`) collapses onto a single key.
     */
    private fun canonicalKey(symbol: String): String = ChinaSymbols.parse(symbol)?.canonical ?: symbol
}
