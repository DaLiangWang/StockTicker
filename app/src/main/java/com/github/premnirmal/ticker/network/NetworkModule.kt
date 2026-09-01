package com.github.premnirmal.ticker.network

import com.github.premnirmal.tickerwidget.BuildConfig
import com.github.premnirmal.tickerwidget.R
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

internal const val CONNECTION_TIMEOUT: Long = 5000
internal const val READ_TIMEOUT: Long = 5000

/**
 * Android networking graph. Replaces the former Hilt `NetworkModule`: builds the OkHttp clients and
 * the per-endpoint API clients for the domestic (mainland-China) data sources. The orchestrators that
 * consume these clients (`StocksApi`, `TrendingProvider`, `HistoryProvider`) live in the shared
 * [com.github.premnirmal.ticker.di.sharedModule].
 */
val networkModule = module {
    single {
        val logger = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .addInterceptor(logger)
            .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .build()
    }

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            coerceInputValues = true
            prettyPrint = true
        }
    }

    single { ApeWisdom(baseUrl = androidContext().getString(R.string.apewisdom_endpoint)) }

    // A-share (mainland China) sources. These are plain public endpoints that need no crumb/cookie
    // bootstrap, so they run on the unauthenticated OkHttp-backed Ktor engine.
    single<AShareQuoteApi>(A_SHARE_TENCENT) {
        createTencentFinanceApi(
            baseUrl = androidContext().getString(R.string.tencent_finance_endpoint),
            okHttpClient = get()
        )
    }
    single<AShareQuoteApi>(A_SHARE_EAST_MONEY) {
        createEastMoneyApi(
            baseUrl = androidContext().getString(R.string.eastmoney_endpoint),
            okHttpClient = get()
        )
    }
}
