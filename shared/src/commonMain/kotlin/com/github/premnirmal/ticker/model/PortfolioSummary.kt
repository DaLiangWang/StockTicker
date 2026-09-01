package com.github.premnirmal.ticker.model

import com.github.premnirmal.ticker.components.AppNumberFormat
import com.github.premnirmal.ticker.components.DecimalFormatter
import com.github.premnirmal.ticker.components.signedPercent
import com.github.premnirmal.ticker.components.signedValue
import com.github.premnirmal.ticker.network.data.Quote

/**
 * Aggregated profit & loss across every watchlist symbol that has a position (i.e. imported
 * shares + cost price), derived from the latest [Quote]s.
 *
 * The three headline numbers the app and the home-screen widget surface are:
 *  - [marketValue]  — 当前市值: what the holdings are worth right now (`Σ price × shares`).
 *  - [totalGainLoss] — 累计盈亏: market value minus what was paid (`Σ cost × shares`).
 *  - [todayGainLoss] — 今日盈亏: today's move applied to the held shares (`Σ dayChange × shares`).
 *
 * The class is deliberately platform-agnostic (it only depends on the already-shared [Quote] and
 * multiplatform [AppNumberFormat]) so Android and iOS compute identical numbers.
 */
data class PortfolioSummary(
    /** Σ lastTradePrice × shares — what the portfolio is worth right now. */
    val marketValue: Float = 0f,
    /** Σ costPrice × shares — the total amount paid for the held shares. */
    val totalCost: Float = 0f,
    /** [marketValue] − [totalCost]. */
    val totalGainLoss: Float = 0f,
    /** Σ today's per-share change × shares. */
    val todayGainLoss: Float = 0f,
    /** Number of symbols that contribute to the summary (i.e. that have positions). */
    val positionCount: Int = 0,
) {

    /** 累计收益率 — total P&L as a percentage of the total cost. */
    val totalGainLossPercent: Float
        get() = if (totalCost == 0f) 0f else (totalGainLoss / totalCost) * 100f

    /** 今日涨跌幅 — today's P&L as a percentage of yesterday's closing value. */
    val todayGainLossPercent: Float
        get() {
            val previousValue = marketValue - todayGainLoss
            return if (previousValue == 0f) 0f else (todayGainLoss / previousValue) * 100f
        }

    /** Whether any position contributed to this summary. */
    val isEmpty: Boolean get() = positionCount == 0

    val isTotalGain: Boolean get() = totalGainLoss >= 0f
    val isTodayGain: Boolean get() = todayGainLoss >= 0f

    fun marketValueString(): String = AppNumberFormat.selected.format(marketValue)
    fun totalCostString(): String = AppNumberFormat.selected.format(totalCost)

    fun totalGainLossString(): String = signedValue(totalGainLoss)
    fun todayGainLossString(): String = signedValue(todayGainLoss)

    fun totalGainLossPercentString(): String = signedPercent(totalGainLossPercent)
    fun todayGainLossPercentString(): String = signedPercent(todayGainLossPercent)

    companion object {

        val Empty = PortfolioSummary()

        /**
         * Aggregates [quotes] into a single summary. Symbols without a position
         * ([Quote.hasPositions] == false) are ignored, so adding a symbol to the watchlist without
         * importing a position never distorts the P&L.
         */
        fun from(quotes: List<Quote>): PortfolioSummary {
            val held = quotes.filter { it.hasPositions() }
            if (held.isEmpty()) return Empty
            var marketValue = 0.0
            var totalCost = 0.0
            var todayGainLoss = 0.0
            for (quote in held) {
                marketValue += quote.holdings().toDouble()
                totalCost += (quote.position?.totalPaidPrice() ?: 0f).toDouble()
                todayGainLoss += quote.dayChange().toDouble()
            }
            return PortfolioSummary(
                marketValue = marketValue.toFloat(),
                totalCost = totalCost.toFloat(),
                totalGainLoss = (marketValue - totalCost).toFloat(),
                todayGainLoss = todayGainLoss.toFloat(),
                positionCount = held.size,
            )
        }
    }
}

/**
 * Convenience accessor so callers can format an arbitrary amount with the same formatter the
 * summary strings use (e.g. when rendering a currency-prefixed market value).
 */
fun PortfolioSummary.formatAmount(value: Float, formatter: DecimalFormatter = AppNumberFormat.selected): String =
    formatter.format(value)
