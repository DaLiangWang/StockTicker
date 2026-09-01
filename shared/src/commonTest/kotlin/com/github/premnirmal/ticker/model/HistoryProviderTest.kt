package com.github.premnirmal.ticker.model

import com.github.premnirmal.ticker.network.AShareQuoteApi
import com.github.premnirmal.ticker.network.data.HistoricalDataResult
import com.github.premnirmal.ticker.network.data.Quote
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryProviderTest {

    /** Lenient like the production clients: unknown fields must not break decoding. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun chartJson(): String =
        """
        {"chart":{"result":[{
          "meta":{"currency":"USD","symbol":"AAPL","regularMarketPrice":12.0,"chartPreviousClose":10.0},
          "timestamp":[300,100,200],
          "indicators":{"quote":[{
            "open":[3.0,1.0,2.0],
            "high":[3.5,1.5,2.5],
            "low":[2.5,0.5,1.5],
            "close":[3.2,1.2,2.2],
            "volume":[300,100,200]
          }]}
        }],"error":null}}
        """.trimIndent()

    /** A chart whose second candle has a null close so it must be filtered out. */
    private fun chartJsonWithGap(): String =
        """
        {"chart":{"result":[{
          "meta":{"currency":"USD","symbol":"AAPL","regularMarketPrice":12.0,"chartPreviousClose":10.0},
          "timestamp":[100,200],
          "indicators":{"quote":[{
            "open":[1.0,2.0],
            "high":[1.5,2.5],
            "low":[0.5,1.5],
            "close":[1.2,null],
            "volume":[100,200]
          }]}
        }],"error":null}}
        """.trimIndent()

    /** Serves the already-decoded [result], i.e. the model the real A-share sources return. */
    private fun historyProvider(result: () -> HistoricalDataResult): HistoryProvider =
        HistoryProvider(
            aShareQuoteApi = object : AShareQuoteApi {
                override suspend fun getQuotes(symbols: List<String>): List<Quote> = emptyList()

                override suspend fun getChartData(
                    symbol: String,
                    interval: String,
                    range: String,
                ): HistoricalDataResult = result()
            }
        )

    @Test
    fun fetchDataByRange_mapsAndSortsDataPoints() = runTest {
        val chart = json.decodeFromString<HistoricalDataResult>(chartJson())
        val provider = historyProvider { chart }

        val result = provider.fetchDataByRange("AAPL", Range.ONE_DAY)

        assertTrue(result.wasSuccessful)
        val data = result.data
        assertEquals(10.0f, data.chartPreviousClose)
        assertEquals(12.0f, data.regularMarketPrice)
        assertEquals(2.0f, data.change)
        // Points are returned ordered by their timestamp regardless of input order.
        assertEquals(listOf(100f, 200f, 300f), data.dataPoints.map { it.xVal })
        assertEquals(1.2f, data.dataPoints.first().closeVal)
        assertTrue(data.isUp)
    }

    @Test
    fun fetchDataByRange_dropsPointsWithMissingValues() = runTest {
        val chart = json.decodeFromString<HistoricalDataResult>(chartJsonWithGap())
        val provider = historyProvider { chart }

        val result = provider.fetchDataByRange("AAPL", Range.ONE_DAY)

        assertTrue(result.wasSuccessful)
        assertEquals(listOf(100f), result.data.dataPoints.map { it.xVal })
    }

    @Test
    fun fetchDataByRange_returnsFailureOnError() = runTest {
        val provider = HistoryProvider(
            aShareQuoteApi = object : AShareQuoteApi {
                override suspend fun getQuotes(symbols: List<String>): List<Quote> = emptyList()

                override suspend fun getChartData(
                    symbol: String,
                    interval: String,
                    range: String,
                ): HistoricalDataResult = error("chart unavailable")
            }
        )

        val result = provider.fetchDataByRange("AAPL", Range.ONE_DAY)

        assertFalse(result.wasSuccessful)
    }
}
