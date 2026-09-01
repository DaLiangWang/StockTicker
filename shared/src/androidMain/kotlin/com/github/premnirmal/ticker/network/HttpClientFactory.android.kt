package com.github.premnirmal.ticker.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient

/**
 * Creates a Ktor [HttpClient] that reuses an already-configured [OkHttpClient] as its engine, so the
 * multiplatform networking layer talks to the domestic (Tencent / East Money) endpoints through the
 * shared OkHttp stack while still going through Ktor and the shared lenient JSON content negotiation.
 *
 * @param okHttpClient the preconfigured OkHttp client.
 */
fun createHttpClient(okHttpClient: OkHttpClient): HttpClient = HttpClient(OkHttp) {
    engine {
        preconfigured = okHttpClient
    }
    installDefaults()
}

/**
 * Builds a [TencentFinanceApi] (A-share quotes) backed by the [okHttpClient].
 */
fun createTencentFinanceApi(baseUrl: String, okHttpClient: OkHttpClient): TencentFinanceApi =
    TencentFinanceApi(baseUrl = baseUrl, httpClient = createHttpClient(okHttpClient))

/**
 * Builds an [EastMoneyApi] (A-share quotes) backed by the [okHttpClient].
 */
fun createEastMoneyApi(baseUrl: String, okHttpClient: OkHttpClient): EastMoneyApi =
    EastMoneyApi(baseUrl = baseUrl, httpClient = createHttpClient(okHttpClient))
