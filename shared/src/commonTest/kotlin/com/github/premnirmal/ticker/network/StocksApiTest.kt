package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.network.data.HistoricalDataResult
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.network.data.SuggestionsNet.SuggestionNet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StocksApiTest {

    /**
     * Stands in for the Tencent/East Money clients. [quotes] and [suggestions] default to a happy
     * path and can be replaced with throwing/empty implementations to exercise the failure paths.
     */
    private class FakeAShareQuoteApi(
        private val quotes: (List<String>) -> List<Quote> = { symbols -> symbols.map { Quote(symbol = it) } },
        private val suggestions: (String) -> List<SuggestionNet> = { emptyList() },
    ) : AShareQuoteApi {

        override suspend fun getQuotes(symbols: List<String>): List<Quote> = quotes(symbols)

        override suspend fun getSuggestions(query: String): List<SuggestionNet> = suggestions(query)

        override suspend fun getChartData(
            symbol: String,
            interval: String,
            range: String,
        ): HistoricalDataResult = error("charts are not exercised here")
    }

    private fun api(fake: AShareQuoteApi = FakeAShareQuoteApi()) = StocksApi(aShareQuoteApi = fake)

    @Test
    fun getStocksReturnsQuotesOnSuccess() = runTest {
        val result = api().getStocks(listOf("AAPL", "MSFT"))

        assertTrue(result.wasSuccessful)
        assertEquals(listOf("AAPL", "MSFT"), result.data.map { it.symbol })
    }

    @Test
    fun getStocksOrdersResultsByRequestedTickers() = runTest {
        // The source returns results out of order; StocksApi must reorder them to match the request.
        val fake = FakeAShareQuoteApi(
            quotes = { symbols -> symbols.reversed().map { Quote(symbol = it) } }
        )

        val result = api(fake).getStocks(listOf("AAPL", "MSFT"))

        assertEquals(listOf("AAPL", "MSFT"), result.data.map { it.symbol })
    }

    @Test
    fun getStocksResolvesBareAShareCodes() = runTest {
        // A CSV import stores bare A-share codes (600022) while search stores the canonical
        // `sh`/`sz` form. Sources echo back whichever spelling we sent, so the bare codes must still
        // resolve — keying the result by the raw symbol dropped them and left the watchlist at 0.
        val fake = FakeAShareQuoteApi(
            quotes = { symbols -> symbols.map { Quote(symbol = it, lastTradePrice = 12.5f) } }
        )

        val result = api(fake).getStocks(listOf("600022", "002756", "sz000001"))

        assertTrue(result.wasSuccessful)
        assertEquals(listOf("600022", "002756", "sz000001"), result.data.map { it.symbol })
        assertEquals(listOf(12.5f, 12.5f, 12.5f), result.data.map { it.lastTradePrice })
    }

    @Test
    fun getStocksDegradesToEmptySuccessWhenSourceThrows() = runTest {
        // fetchQuotes swallows a source failure and yields no quotes, so the batch reports an empty
        // success instead of failing the whole refresh.
        val fake = FakeAShareQuoteApi(quotes = { error("source unavailable") })

        val result = api(fake).getStocks(listOf("AAPL"))

        assertTrue(result.wasSuccessful)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun getStockReturnsQuoteOnSuccess() = runTest {
        val result = api().getStock("AAPL")

        assertTrue(result.wasSuccessful)
        assertEquals("AAPL", result.data.symbol)
    }

    @Test
    fun getStockReturnsFailureWhenSourceHasNoQuote() = runTest {
        val fake = FakeAShareQuoteApi(quotes = { emptyList() })

        val result = api(fake).getStock("AAPL")

        assertFalse(result.wasSuccessful)
        assertTrue(result.hasError)
    }

    @Test
    fun getSuggestionsReturnsResultsOnSuccess() = runTest {
        val fake = FakeAShareQuoteApi(
            suggestions = { listOf(SuggestionNet("AAPL"), SuggestionNet("AMZN")) }
        )

        val result = api(fake).getSuggestions("a")

        assertTrue(result.wasSuccessful)
        assertEquals(listOf("AAPL", "AMZN"), result.data.map { it.symbol })
    }

    @Test
    fun getSuggestionsReturnsFailureWhenSourceThrows() = runTest {
        val fake = FakeAShareQuoteApi(suggestions = { error("source unavailable") })

        val result = api(fake).getSuggestions("a")

        assertFalse(result.wasSuccessful)
        assertTrue(result.hasError)
    }

    @Test
    fun getSuggestionsKeepsLocalAShareMatchWhenSourceThrows() = runTest {
        // A complete A-share code resolves locally, so it is still offered when the remote search
        // fails — the local hit means the request is not reported as a failure.
        val fake = FakeAShareQuoteApi(suggestions = { error("source unavailable") })

        val result = api(fake).getSuggestions("600519")

        assertTrue(result.wasSuccessful)
        assertTrue(result.data.isNotEmpty())
    }
}
