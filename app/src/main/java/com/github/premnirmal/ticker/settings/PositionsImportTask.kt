package com.github.premnirmal.ticker.settings

import android.content.Context
import android.net.Uri
import com.github.premnirmal.ticker.model.StocksProvider
import org.koin.core.component.KoinComponent
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Imports positions (symbol, shares, cost price) from a text file.
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
                        stocksProvider.setHolding(entry.symbol, shares, price)
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
