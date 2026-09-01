package com.github.premnirmal.ticker.home

import com.github.premnirmal.ticker.model.PortfolioSummary
import com.github.premnirmal.ticker.network.data.Quote
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic view of a single watchlist/widget shown as a tab in [WatchlistContent]. The
 * Android `WidgetData` is adapted to this interface by the `:app` host so the shared screen does not
 * depend on the Glance/`SharedPreferences`-backed widget model.
 */
interface WatchlistWidget {
    val name: String
    val stocks: StateFlow<List<Quote>>
    fun rearrange(tickers: List<String>)
    fun setAutoSort(autoSort: Boolean)
    fun removeStock(ticker: String)
}

/**
 * Pre-formatted portfolio P&L strings rendered by the total-holdings popup. The locale-aware number
 * formatting is done by the host (which owns the platform `NumberFormat`).
 *
 * [holdings] is the current market value and [gain]/[loss] split the accumulated P&L into its
 * positive and negative parts; [todayGainLoss], [todayGainLossPercent] and [totalGainLossPercent] are
 * the newer headline numbers derived from the imported positions (see [PortfolioSummary]).
 */
data class TotalGainLoss(
    val holdings: String,
    val gain: String,
    val loss: String,
    val todayGainLoss: String = "",
    val todayGainLossPercent: String = "",
    val totalGainLossPercent: String = "",
) {

    companion object {
        /** Builds the popup model from a [PortfolioSummary] computed over the whole portfolio. */
        fun from(summary: PortfolioSummary): TotalGainLoss = TotalGainLoss(
            holdings = summary.marketValueString(),
            gain = if (summary.totalGainLoss >= 0f) summary.totalGainLossString() else "",
            loss = if (summary.totalGainLoss < 0f) summary.totalGainLossString() else "",
            todayGainLoss = summary.todayGainLossString(),
            todayGainLossPercent = summary.todayGainLossPercentString(),
            totalGainLossPercent = summary.totalGainLossPercentString(),
        )
    }
}
