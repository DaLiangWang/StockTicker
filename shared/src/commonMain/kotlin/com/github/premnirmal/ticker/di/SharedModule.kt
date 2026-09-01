package com.github.premnirmal.ticker.di

import com.github.premnirmal.ticker.model.HistoryProvider
import com.github.premnirmal.ticker.network.AShareQuoteApi
import com.github.premnirmal.ticker.network.A_SHARE_EAST_MONEY
import com.github.premnirmal.ticker.network.A_SHARE_TENCENT
import com.github.premnirmal.ticker.network.TrendingProvider
import com.github.premnirmal.ticker.network.PreferenceAShareQuoteApi
import com.github.premnirmal.ticker.network.StocksApi
import com.github.premnirmal.ticker.network.SuggestionsProvider
import com.github.premnirmal.ticker.settings.PortfolioSerializer
import org.koin.dsl.module

/**
 * Koin module for the platform-agnostic services that already live in `:shared` `commonMain`
 * (orchestrators built from plain constructors). Their leaf dependencies — the Ktor/HTTP API
 * clients, the [com.github.premnirmal.ticker.network.CrumbStore], the [kotlinx.serialization.json.Json]
 * instance and the application [kotlinx.coroutines.CoroutineScope] — are contributed by the platform
 * module (Android wires them in `:app`; iOS wires its own equivalents in `iosMain`), so this single
 * module is reused by every platform.
 */
val sharedModule = module {
    single {
        StocksApi(
            aShareQuoteApi = get(),
        )
    }
    // A-share quotes: both sources are provided by the platform module (they need an engine-backed
    // Ktor client); this picks between them from the user's Settings preference at call time.
    single<AShareQuoteApi> {
        PreferenceAShareQuoteApi(
            preferences = get(),
            tencent = get(A_SHARE_TENCENT),
            eastMoney = get(A_SHARE_EAST_MONEY),
        )
    }
    single {
        TrendingProvider(
            coroutineScope = get(),
            apeWisdom = get(),
            stocksApi = get()
        )
    }
    single { HistoryProvider(aShareQuoteApi = get()) }
    single { SuggestionsProvider(stocksApi = get()) }
    single { PortfolioSerializer(json = get()) }
}
