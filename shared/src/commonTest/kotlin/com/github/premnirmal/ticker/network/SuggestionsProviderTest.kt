package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.network.data.HistoricalDataResult
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.network.data.SuggestionsNet.SuggestionNet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionsProviderTest {

    private fun provider(suggestions: (String) -> List<SuggestionNet>): SuggestionsProvider {
        val api = StocksApi(
            aShareQuoteApi = object : AShareQuoteApi {
                override suspend fun getQuotes(symbols: List<String>): List<Quote> = emptyList()

                override suspend fun getSuggestions(query: String): List<SuggestionNet> = suggestions(query)

                override suspend fun getChartData(
                    symbol: String,
                    interval: String,
                    range: String,
                ): HistoricalDataResult = error("charts are not exercised here")
            }
        )
        return SuggestionsProvider(api)
    }

    @Test
    fun fetchSuggestions_appendsUpperCasedQueryWhenMissing() = runTest {
        val result = provider { listOf(SuggestionNet("AAPL")) }.fetchSuggestions("goog")

        assertTrue(result.wasSuccessful)
        assertEquals(listOf("AAPL", "GOOG"), result.data.map { it.symbol })
    }

    @Test
    fun fetchSuggestions_doesNotDuplicateQueryAlreadyPresent() = runTest {
        val result = provider { listOf(SuggestionNet("AAPL"), SuggestionNet("GOOG")) }
            .fetchSuggestions("goog")

        assertTrue(result.wasSuccessful)
        assertEquals(listOf("AAPL", "GOOG"), result.data.map { it.symbol })
    }

    @Test
    fun fetchSuggestions_returnsEmptySuccessForEmptyQuery() = runTest {
        val result = provider { error("must not be called for an empty query") }.fetchSuggestions("")

        assertTrue(result.wasSuccessful)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun fetchSuggestions_returnsFailureWhenRequestFails() = runTest {
        val result = provider { error("source unavailable") }.fetchSuggestions("goog")

        assertFalse(result.wasSuccessful)
    }
}
