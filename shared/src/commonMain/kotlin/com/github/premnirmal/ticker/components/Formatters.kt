package com.github.premnirmal.ticker.components

/**
 * Shared signed / percent formatting helpers. Consolidates the previously duplicated
 * `changeStringWithSign` / `changePercentStringWithSign` / `gainLossString` / `signed` logic that
 * lived in [com.github.premnirmal.ticker.network.data.Quote],
 * [com.github.premnirmal.ticker.model.ChartData] and
 * [com.github.premnirmal.ticker.model.PortfolioSummary].
 */

fun signedValue(value: Float, formatter: DecimalFormatter = AppNumberFormat.selected): String {
    val formatted = formatter.format(value)
    return if (value >= 0f) "+$formatted" else formatted
}

fun signedPercent(value: Float, formatter: DecimalFormatter = AppNumberFormat.TWO_DP): String {
    val formatted = formatter.format(value)
    return "${if (value >= 0f) "+" else ""}$formatted%"
}

fun percentString(value: Float, formatter: DecimalFormatter = AppNumberFormat.TWO_DP): String =
    "${formatter.format(value)}%"
