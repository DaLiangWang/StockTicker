package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.components.AppLogger
import com.github.premnirmal.ticker.components.ioDispatcher
import com.github.premnirmal.ticker.model.FetchException
import com.github.premnirmal.ticker.model.FetchResult
import com.github.premnirmal.ticker.network.data.Quote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aggregates the "trending" (most-active) stock feed. Yahoo Finance was removed, so trending now relies
 * on ApeWisdom (Reddit-mention momentum). The app-start prefetch and the search suggestions depend on it.
 */
class TrendingProvider(
    private val coroutineScope: CoroutineScope,
    private val apeWisdom: ApeWisdom,
    private val stocksApi: StocksApi
) {

    private var cachedTrendingStocks: List<Quote> = emptyList()

    fun initCache() {
        coroutineScope.launch {
            AppLogger.d("$TAG initCache: prefetching trending stocks")
            fetchTrendingStocks()
        }
    }

    suspend fun fetchTrendingStocks(useCache: Boolean = false): FetchResult<List<Quote>> =
        withContext(ioDispatcher) {
            try {
                if (useCache && cachedTrendingStocks.isNotEmpty()) {
                    return@withContext FetchResult.success(cachedTrendingStocks)
                }
                val result = apeWisdom.getTrendingStocks().results
                val data = result.map { it.ticker }
                val trendingResult = stocksApi.getStocks(data)
                if (trendingResult.wasSuccessful) {
                    cachedTrendingStocks = trendingResult.data
                }
                return@withContext trendingResult
            } catch (ex: Exception) {
                AppLogger.w(ex, "$TAG fetchTrendingStocks failed")
                return@withContext FetchResult.failure<List<Quote>>(
                    FetchException("Error fetching trending", ex)
                )
            }
        }

    private companion object {
        private const val TAG = "[TrendingProvider]"
    }
}
