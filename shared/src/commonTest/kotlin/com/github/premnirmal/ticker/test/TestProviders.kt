package com.github.premnirmal.ticker.test

import com.github.premnirmal.ticker.model.HistoryProvider
import com.github.premnirmal.ticker.network.AShareQuoteApi
import com.github.premnirmal.ticker.network.data.HistoricalDataResult
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.network.installDefaults
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * Builders that wire the shared providers to fakes backed by Ktor [MockEngine]s, so tests can
 * exercise them without real networking.
 *
 * Quotes and charts now flow exclusively through [AShareQuoteApi] (Yahoo Finance was removed), so
 * the fakes below implement that single interface instead of wiring up one HTTP client per endpoint.
 */
object TestProviders {

    private const val CHART_URL = "https://example.com/chart/"

    val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    fun unusedEngine(): MockEngine = MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) }

    /**
     * A [HistoryProvider] whose K-line requests are served by [engine]: whatever chart JSON the
     * engine returns is decoded straight into [HistoricalDataResult] — the same model the real
     * A-share sources hand back — so tests can keep expressing fixtures as raw JSON.
     */
    fun historyProvider(engine: MockEngine = unusedEngine()): HistoryProvider {
        val client = HttpClient(engine) { installDefaults() }
        return HistoryProvider(
            aShareQuoteApi = object : AShareQuoteApi {
                override suspend fun getQuotes(symbols: List<String>): List<Quote> = emptyList()

                override suspend fun getChartData(
                    symbol: String,
                    interval: String,
                    range: String,
                ): HistoricalDataResult = client.get("$CHART_URL$symbol").body()
            }
        )
    }
}
