package com.github.premnirmal.ticker.settings

import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.widget.WidgetDataProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

internal interface ImportTask {

    suspend fun import(context: Context, fileUri: Uri): Boolean
}

internal open class TickersImportTask(private val widgetDataProvider: WidgetDataProvider) :
    ImportTask, KoinComponent {

    private val portfolioSerializer: PortfolioSerializer by inject()

    override suspend fun import(context: Context, fileUri: Uri): Boolean {
        var result = false
        val contentResolver = context.applicationContext.contentResolver
        try {
            contentResolver.openInputStream(fileUri)
                ?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val text: String = reader.readText()
                        val tickers = portfolioSerializer.parseTickers(text).toTypedArray()
                        if (widgetDataProvider.hasWidget()) {
                            widgetDataProvider.getAppWidgetIds()
                                .forEach { widgetId ->
                                    val widgetData = widgetDataProvider.dataForWidgetId(widgetId)
                                    widgetData.addTickers(listOf(*tickers))
                                }
                        } else {
                            val widgetData =
                                widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)
                            widgetData.addTickers(listOf(*tickers))
                        }
                        result = true
                    }
                }
        } catch (e: IOException) {
            Timber.e(e)
            result = false
        }

        return result
    }
}

/**
 * Imports positions (symbol, shares, cost price) from a text file.
 *
 * Unlike [TickersImportTask] (a plain symbol list) and [PortfolioImportTask] (a full portfolio JSON
 * snapshot), this reads the `symbol,shares,cost` rows a broker or spreadsheet exports and turns them
 * into real holdings, which is what drives the 今日盈亏 / 累计盈亏 / 当前市值 figures. Symbols whose
 * share/price columns are missing are still added to the watchlist, so a mixed file works.
 *
 * Parsing itself is platform-agnostic ([PositionImportParser]); this task only owns the Android IO.
 *
 * @return the number of positions imported, `0` when the file held no usable rows, or `null` on a
 * read failure.
 */
internal class PositionsImportTask(private val stocksProvider: StocksProvider) : KoinComponent {

    suspend fun import(context: Context, fileUri: Uri): Int? {
        return try {
            context.applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val text = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                val entries = PositionImportParser.parse(text)
                if (entries.isEmpty()) return null
                // Add every symbol in one batch so the watchlist triggers a single fetch rather than
                // one network round-trip per row.
                stocksProvider.addStocks(entries.map { it.symbol })
                var imported = 0
                for (entry in entries) {
                    val shares = entry.shares
                    val price = entry.price
                    if (entry.hasPosition && shares != null && price != null) {
                        stocksProvider.addHolding(entry.symbol, shares, price)
                        imported++
                    }
                }
                imported
            }
        } catch (e: Exception) {
            Timber.w(e)
            null
        }
    }
}

internal open class PortfolioImportTask(private val stocksProvider: StocksProvider) :
    ImportTask, KoinComponent {

    private val portfolioSerializer: PortfolioSerializer by inject()

    override suspend fun import(context: Context, fileUri: Uri): Boolean {
        val contentResolver = context.applicationContext.contentResolver
        return try {
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val jsonText: String = reader.readText()
                    val portfolio: List<Quote> = portfolioSerializer.deserializePortfolio(jsonText)
                    stocksProvider.addPortfolio(portfolio)
                    true
                }
            } ?: false
        } catch (e: Exception) {
            Timber.w(e)
            return false
        }
    }
}
