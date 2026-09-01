package com.github.premnirmal.ticker.model

import com.github.premnirmal.ticker.components.AppLogger
import com.github.premnirmal.ticker.components.ioDispatcher
import com.github.premnirmal.ticker.network.AShareQuoteApi
import com.github.premnirmal.ticker.network.data.DataPoint
import kotlinx.coroutines.withContext

/**
 * Fetches a symbol's chart history for a [Range] and maps it into the shared [ChartData] model.
 * Charts are served by the domestic (mainland-China) sources via [AShareQuoteApi] (which already
 * covers A/HK/US instruments), so Yahoo Finance is no longer involved.
 */
class HistoryProvider(
    private val aShareQuoteApi: AShareQuoteApi
) {

    suspend fun fetchDataByRange(
        symbol: String,
        range: Range
    ): FetchResult<ChartData> = withContext(ioDispatcher) {
        val chartData = try {
            val historicalData = aShareQuoteApi.getChartData(
                symbol = symbol,
                interval = range.intervalParam(),
                range = range.rangeParam()
            )
            with(historicalData.chart.result.first()) {
                val chartPreviousClose = meta.chartPreviousClose.toFloat()
                val regularMarketPrice = meta.regularMarketPrice.toFloat()
                val dataQuote = indicators?.quote?.firstOrNull()
                val highs = dataQuote?.high
                val lows = dataQuote?.low
                val opens = dataQuote?.open
                val closes = dataQuote?.close
                val dataPoints = timestamp?.mapIndexed { i, stamp ->
                    if (highs == null || lows == null || opens == null || closes == null ||
                        highs[i] === null || lows[i] === null ||
                        opens[i] === null || closes[i] === null
                    ) {
                        null
                    } else {
                        DataPoint(
                            stamp.toFloat(),
                            highs[i]!!.toFloat(),
                            lows[i]!!.toFloat(),
                            opens[i]!!.toFloat(),
                            closes[i]!!.toFloat()
                        )
                    }
                }?.filterNotNull()?.sorted().orEmpty()
                ChartData(
                    chartPreviousClose = chartPreviousClose,
                    regularMarketPrice = regularMarketPrice,
                    dataPoints = dataPoints
                )
            }
        } catch (ex: Exception) {
            AppLogger.w(ex)
            return@withContext FetchResult.failure(
                FetchException("Error fetching datapoints", ex)
            )
        }
        return@withContext FetchResult.success(chartData)
    }
}
