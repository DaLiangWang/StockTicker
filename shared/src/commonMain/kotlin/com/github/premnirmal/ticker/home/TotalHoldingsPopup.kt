package com.github.premnirmal.ticker.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.gain
import com.github.premnirmal.shared.resources.loss
import com.github.premnirmal.shared.resources.market_value
import com.github.premnirmal.shared.resources.today_gain_loss
import com.github.premnirmal.shared.resources.total_gain_loss
import com.github.premnirmal.shared.resources.total_return_percent
import com.github.premnirmal.tickerwidget.ui.theme.SharedColours
import org.jetbrains.compose.resources.stringResource

/**
 * Shared (Compose Multiplatform) popup summarising the portfolio: current market value, today's P&L
 * and the accumulated P&L, all derived from the imported positions.
 *
 * Rendered identically on Android and iOS: the localised labels come from the shared string
 * resources and the gain/loss colours from [SharedColours]. Each figure is laid out as a small
 * label above a larger value and separated by a divider, so the numbers are what the eye lands on
 * instead of one undifferentiated line of text.
 */
@Composable
fun TotalHoldingsPopup(
    totalHoldings: TotalGainLoss,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .wrapContentSize()
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                PopupRow(
                    label = stringResource(Res.string.market_value),
                    value = totalHoldings.holdings,
                )
                PopupDivider()
                PopupRow(
                    label = stringResource(Res.string.today_gain_loss),
                    value = totalHoldings.todayGainLoss,
                    valueColor = colorForChange(totalHoldings.todayGainLoss),
                )
                PopupDivider()
                PopupRow(
                    label = stringResource(Res.string.total_return_percent),
                    value = totalHoldings.totalGainLossPercent,
                    valueColor = colorForChange(totalHoldings.totalGainLossPercent),
                )
                PopupDivider()
                // The accumulated P&L arrives as two separate figures, so each carries its own
                // label — a bare pair of coloured numbers gave no clue which was the gain.
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(Res.string.total_gain_loss),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    GainLossCell(
                        modifier = Modifier.weight(1f),
                        label = stringResource(Res.string.gain),
                        value = totalHoldings.gain,
                        color = SharedColours.UpColour,
                    )
                    GainLossCell(
                        modifier = Modifier.weight(1f),
                        label = stringResource(Res.string.loss),
                        value = totalHoldings.loss,
                        color = SharedColours.DownColour,
                    )
                }
            }
        }
    }
}

/** Small label above a larger value, so the figure reads first. */
@Composable
private fun PopupRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
        )
    }
}

/** One half of the accumulated P&L: a labelled gain or loss figure. */
@Composable
private fun GainLossCell(
    modifier: Modifier,
    label: String,
    value: String,
    color: Color,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

@Composable
private fun PopupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * Colours a signed amount by delegating to [SharedColours.changeColour], so the popup matches the
 * quote rows. Amounts that carry no sign (e.g. an empty string) fall back to the neutral colour.
 */
@Composable
private fun colorForChange(value: String) = SharedColours.changeColour(
    up = value.startsWith("+"),
    down = value.startsWith("-"),
)
