package com.github.premnirmal.ticker.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.change_percent
import com.github.premnirmal.shared.resources.cost_price
import com.github.premnirmal.shared.resources.ic_more
import com.github.premnirmal.shared.resources.ic_remove_circle
import com.github.premnirmal.shared.resources.market_cap
import com.github.premnirmal.shared.resources.remove
import com.github.premnirmal.shared.resources.shares_with_cost
import com.github.premnirmal.shared.resources.today_profit
import com.github.premnirmal.shared.resources.total_gain_amount
import com.github.premnirmal.shared.resources.total_profit
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.tickerwidget.ui.AppCard
import com.github.premnirmal.tickerwidget.ui.theme.SharedColours
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val QUOTE_MAX_LINES = 1

private const val DASH = "—"

private val TABLE_ROW_PADDING_H = 12.dp
private val NAME_MIN_WIDTH = 160.dp
private val RIGHT_COLUMN_WIDTH = 130.dp
private val COLUMN_GAP = 8.dp
private val NAME_PRICE_GAP = 6.dp
private val ROW_VERTICAL_GAP = 4.dp

/**
 * Narrowest width the watchlist table may take.
 *
 * The name column stretches into whatever is left over — every row is laid out at the same width, so
 * the columns still line up — while the right column keeps a fixed width so the figures stay
 * right-aligned in every row. The host uses this as a *minimum*, widening the table to the viewport
 * when the screen is wider so no empty gap is left on the right.
 */
val QUOTE_TABLE_WIDTH: Dp = NAME_MIN_WIDTH + COLUMN_GAP + RIGHT_COLUMN_WIDTH +
    (TABLE_ROW_PADDING_H * 2) + 24.dp

/**
 * One watchlist row. Two side-by-side blocks:
 *   left   — symbol + name (top), shares · cost (bottom)
 *   right  — current price + change % (top), total P&L (bottom)
 *
 * The cell renders inside a horizontally-scrolling container whose width matches this row, so every
 * row scrolls together and the columns stay locked in place. The removal menu, when shown, sits past
 * the right edge.
 */
@Composable
fun QuoteTableRow(
    quote: Quote,
    modifier: Modifier = Modifier,
    onClick: (Quote) -> Unit,
    onRemoveClick: (Quote) -> Unit = {},
    showMore: Boolean = false,
) {
    val up = quote.isUp
    val down = quote.isDown
    val gain = quote.gainLoss()
    val hasPositions = quote.hasPositions()

    Row(
        modifier = modifier
            .clickable { onClick(quote) }
            .padding(horizontal = TABLE_ROW_PADDING_H, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left block: name on top, holdings + cost below. Stretches to fill the row.
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                QuoteSymbolText(text = quote.symbol)
                Spacer(Modifier.width(NAME_PRICE_GAP))
                QuoteNameText(
                    text = quote.name,
                    maxLines = QUOTE_MAX_LINES,
                )
            }
            Spacer(Modifier.height(ROW_VERTICAL_GAP))
            Text(
                text = if (hasPositions) {
                    stringResource(
                        Res.string.shares_with_cost,
                        quote.numSharesString(),
                        quote.priceFormat.format(quote.position?.averagePrice() ?: 0f),
                    )
                } else {
                    DASH
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(COLUMN_GAP))
        // Right block: price + change% on top, total P&L below, right-aligned.
        Column(
            modifier = Modifier.width(RIGHT_COLUMN_WIDTH),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = quote.priceFormat.format(quote.lastTradePrice),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Spacer(Modifier.width(NAME_PRICE_GAP))
                Text(
                    text = quote.changePercentStringWithSign(),
                    style = MaterialTheme.typography.bodySmall,
                    color = SharedColours.changeColour(up, down),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(ROW_VERTICAL_GAP))
            Text(
                text = if (hasPositions) {
                    stringResource(Res.string.total_gain_amount, quote.gainLossString())
                } else {
                    DASH
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (hasPositions) {
                    SharedColours.changeColour(gain > 0, gain < 0)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
        if (showMore) {
            MoreIcon(
                modifier = Modifier.padding(start = 4.dp),
                onClick = { onRemoveClick(quote) },
            )
        }
    }
}

/**
 * Shared (Compose Multiplatform) quote row rendered identically on Android and iOS. Laid out as a
 * table row with four columns — 股票代码 / 名称 / 当前价格 / 今日涨跌 — so it lines up with the
 * suggestion rows in the search/trending list.
 *
 * The watchlist uses [QuoteTableRow] instead, which lays the same figures out as two stacked blocks
 * and scrolls horizontally in sync with every other row.
 */
@Composable
fun QuoteCard(
    quote: Quote,
    modifier: Modifier = Modifier,
    quoteNameMaxLines: Int = QUOTE_MAX_LINES,
    interactionSource: MutableInteractionSource? = null,
    onClick: (Quote) -> Unit,
    onRemoveClick: (Quote) -> Unit = {},
    showMore: Boolean = false,
) {
    AppCard(
        modifier = modifier,
        interactionSource = interactionSource,
        onClick = { onClick(quote) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuoteSymbolText(
                modifier = Modifier.weight(1.1f),
                text = quote.symbol,
            )
            QuoteNameText(
                modifier = Modifier
                    .weight(2f)
                    .padding(start = 8.dp),
                text = quote.name,
                maxLines = quoteNameMaxLines,
            )
            val up = quote.isUp
            val down = quote.isDown
            Text(
                modifier = Modifier.weight(1f),
                text = quote.priceFormat.format(quote.lastTradePrice),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
            Text(
                modifier = Modifier.weight(1f),
                text = quote.changePercentStringWithSign(),
                style = MaterialTheme.typography.bodyMedium,
                color = SharedColours.changeColour(up, down),
                maxLines = 1,
                textAlign = TextAlign.End,
            )
            if (showMore) {
                MoreIcon(
                    modifier = Modifier.padding(start = 4.dp),
                    onClick = { onRemoveClick(quote) },
                )
            }
        }
    }
}



@Composable
fun QuoteSymbolText(
    modifier: Modifier = Modifier,
    text: String,
) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
fun QuoteNameText(
    modifier: Modifier = Modifier,
    text: String,
    maxLines: Int = 2,
) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MoreIcon(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var showPopup by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            modifier = Modifier.size(16.dp),
            onClick = {
                showPopup = !showPopup
            },
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_more),
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = showPopup,
            onDismissRequest = {
                showPopup = false
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable(role = Role.Button) {
                        showPopup = false
                        onClick()
                    }
            ) {
                Icon(
                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                    painter = painterResource(Res.drawable.ic_remove_circle),
                    contentDescription = null,
                )
                Text(
                    text = stringResource(Res.string.remove),
                )
            }
        }
    }
}
