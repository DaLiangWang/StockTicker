package com.github.premnirmal.ticker.settings

import com.github.premnirmal.ticker.network.data.Quote

/**
 * One parsed row of a positions import file.
 *
 * A positions file carries the data the in-app holdings editor also captures — how many shares are
 * held and what they cost — in a plain text form that is easy to produce from a spreadsheet or a
 * broker export: one row per symbol, `symbol,shares,cost`.
 *
 * @param symbol the ticker symbol, normalised (trimmed, surrounding quotes stripped).
 * @param shares the number of shares held, or `null` when the row only names a symbol.
 * @param price the per-share cost price, or `null` when the row only names a symbol.
 */
data class PositionImportEntry(
    val symbol: String,
    val shares: Float? = null,
    val price: Float? = null,
) {
    /** Whether the row carries a complete position (shares **and** a cost price). */
    val hasPosition: Boolean
        get() {
            val sharesValue = shares
            val priceValue = price
            return sharesValue != null && priceValue != null && sharesValue > 0f
        }
}

/**
 * Pure, platform-agnostic parsing of the positions CSV import format.
 *
 * No platform IO and no Android/iOS dependencies, so a file produced on one platform imports on the
 * other. The Android file IO lives in [PositionsImportTask].
 */
object PositionImportParser {

    /** Row separators: Windows, Unix and legacy Mac line endings. */
    private val LINE_SEPARATORS = arrayOf("\r\n", "\n", "\r")

    /** Column separators, in priority order (tab wins, then half/full-width comma, semicolon). */
    private val COLUMN_SEPARATORS = arrayOf("\t", ",", "，", ";", "；")

    /**
     * Characters that decorate numbers in broker/spreadsheet exports but carry no value:
     * thousands separators, currency symbols, percent signs and whitespace variants.
     */
    private val NUMBER_NOISE = charArrayOf(',', '，', '¥', '$', '￥', '%', ' ', ' ')

    /**
     * Parses [text] into position rows.
     *
     * Accepted shapes, one per line:
     * - `AAPL,10,180.50` — symbol with a full position.
     * - `600519,100,1650.00` / `sh600519,100,1650.00` — A-share symbols, with or without a prefix.
     * - `AAPL` — symbol only; the row yields an entry with no position so the caller can still add
     *   it to the watchlist.
     *
     * Tabs, half-width/full-width commas and semicolons all work as column separators. Numbers may
     * carry thousands separators, currency symbols or surrounding whitespace. A header row (or any
     * row whose share/price columns are not numeric) is skipped rather than reported as an error, so
     * exporting a spreadsheet with its header intact works out of the box.
     */
    fun parse(text: String): List<PositionImportEntry> {
        val entries = ArrayList<PositionImportEntry>()
        for (rawLine in splitLines(text)) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val columns = splitColumns(line)
            val symbol = cleanSymbol(columns.first())
            if (symbol.isEmpty()) continue
            // Extended holdings format: symbol,market,name,quantity,cost_price[,currency,note].
            // quantity sits at column 3, cost price at column 4. A header row (non-numeric at those
            // columns) is skipped, as are the symbol-only / legacy rows handled below.
            if (columns.size >= 5) {
                val shares = parseNumber(columns[3])
                if (shares == null || shares <= 0f) continue
                val price = columns.getOrNull(4)?.let { parseNumber(it) }
                entries.add(
                    PositionImportEntry(
                        symbol = symbol,
                        shares = shares,
                        price = price?.takeIf { it > 0f }
                    )
                )
                continue
            }
            // A single column means "symbol only" (the legacy tickers-list shape).
            if (columns.size == 1) {
                entries.add(PositionImportEntry(symbol = symbol))
                continue
            }
            // Two or more columns: the second one must be a share count. If it is not, this is a
            // header row (or otherwise unusable) and is skipped.
            val shares = parseNumber(columns[1])
            if (shares == null || shares <= 0f) continue
            val price = columns.getOrNull(2)?.let { parseNumber(it) }
            entries.add(
                PositionImportEntry(
                    symbol = symbol,
                    shares = shares,
                    price = price?.takeIf { it > 0f }
                )
            )
        }
        return entries
    }

    /**
     * Serializes [quotes] to the positions text produced/consumed by [parse]: one row per symbol,
     * holding the total shares and the average cost price. Symbols without a position are emitted
     * with empty share/price columns.
     */
    fun serialize(quotes: List<Quote>): String = buildString {
        for (quote in quotes) {
            val position = quote.position
            if (position == null || position.holdings.isEmpty()) {
                append(quote.symbol).append(',')
            } else {
                append(quote.symbol).append(',')
                append(position.totalShares().stripTrailingZeros())
                append(',')
                append(position.averagePrice().stripTrailingZeros())
            }
            append('\n')
        }
    }

    private fun splitLines(text: String): List<String> {
        var result = listOf(text)
        for (separator in LINE_SEPARATORS) {
            result = result.flatMap { it.split(separator) }
        }
        return result
    }

    private fun splitColumns(line: String): List<String> {
        for (separator in COLUMN_SEPARATORS) {
            if (line.contains(separator)) {
                return line.split(separator).map { it.trim() }
            }
        }
        return listOf(line)
    }

    private fun cleanSymbol(raw: String): String = raw
        .trim()
        .trim('"', '\'', ' ')
        .replace(" ", "")

    private fun parseNumber(raw: String): Float? {
        val cleaned = raw
            .trim()
            .trim('"', '\'')
            .filter { it !in NUMBER_NOISE }
        if (cleaned.isEmpty()) return null
        // Parenthesised values are the spreadsheet convention for negatives, e.g. "(1,234.50)".
        val isNegative = cleaned.startsWith('(') && cleaned.endsWith(')')
        val digits = cleaned.trim('(', ')')
        return digits.toFloatOrNull()?.let { if (isNegative) -it else it }
    }

    private fun Float.stripTrailingZeros(): String {
        val asInt = this.toLong()
        return if (this == asInt.toFloat()) asInt.toString() else this.toString()
    }
}
