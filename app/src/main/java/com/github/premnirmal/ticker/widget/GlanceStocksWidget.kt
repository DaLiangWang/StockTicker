package com.github.premnirmal.ticker.widget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentSize
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.home.HomeActivity
import com.github.premnirmal.ticker.model.PortfolioSummary
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.network.data.Holding
import com.github.premnirmal.ticker.network.data.Position
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.tickerwidget.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private const val WIDGET_FONT_SIZE = 13f

/** Enlarged size for the three stacked portfolio values (今日盈亏 / 累计盈亏 / 当前市值). */
private const val SUMMARY_VALUE_FONT_SIZE = 20f

/** Smaller size for the header clock, which only shows `HH:mm:ss`. */
private const val HEADER_TIME_FONT_SIZE = 10f

/** Identifiers for the three optional portfolio summary columns, in display order. */
private const val COLUMN_TODAY = 0
private const val COLUMN_TOTAL = 1
private const val COLUMN_MARKET_VALUE = 2

class GlanceStocksWidget : GlanceAppWidget(), KoinComponent {

    private val stocksProvider: StocksProvider by inject()

    private val widgetDataProvider: WidgetDataProvider by inject()

    private val appPreferences: AppPreferences by inject()

    override val sizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<WidgetGlanceState> = WidgetGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        // Update the Glance state with current widget data and quotes
        val widgetData = widgetDataProvider.dataForWidgetId(appWidgetId)
        updateAppWidgetState(
            context = context,
            definition = stateDefinition,
            glanceId = id
        ) { state ->
            val currentQuotes = widgetData.stocks.value
            val currentState = widgetData.data.value
            val currentFetchState = stocksProvider.fetchState.value
            val currentIsRefreshing = appPreferences.isRefreshing.value
            state.copy(
                widgetState = SerializableWidgetState.from(
                    state = currentState,
                    fetchState = currentFetchState,
                    isRefreshing = currentIsRefreshing,
                ),
                quotes = currentQuotes,
            )
        }

        provideContent {
            val glanceState = currentState<WidgetGlanceState>()
            Content(glanceState)
        }
    }

    @Composable
    private fun Content(glanceState: WidgetGlanceState) {
        val colors = WidgetColors.colors()
        GlanceTheme(colors = colors) {
            GlanceWidget(
                widgetData = glanceState.widgetState,
                quotes = glanceState.quotes,
            )
        }
    }
}

@Composable
fun GlanceWidget(
    widgetData: SerializableWidgetState,
    quotes: List<Quote>,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            // Transparent so the launcher wallpaper shows through — the widget no longer paints its
            // own card background.
            // Backed by the user's background preference (transparent / translucent / solid, each
            // with a dark-mode variant). Note this takes a drawable resource, not a colour: the
            // card backgrounds carry rounded corners that a flat colour cannot express.
            .background(ImageProvider(widgetData.backgroundResource))
            // Tapping anywhere on the widget refreshes it. The widget is a read-only view, so it
            // deliberately does not launch the app.
            .clickable(actionRunCallback<RefreshCallback>())
            .padding(6.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            if (!widgetData.hideWidgetHeader) {
                Header(widgetData)
            }
            PortfolioSummaryRow(widgetData, quotes)
        }
    }
}

/**
 * The portfolio P&L strip rendered between the widget header and the quotes grid.
 *
 * It surfaces the three numbers the user opted into (当前市值 / 今日盈亏 / 累计盈亏) for the symbols in
 * *this* widget, aggregated with the shared [PortfolioSummary] so the widget and the in-app screen
 * can never disagree. Nothing is rendered when every toggle is off or when no symbol in the widget
 * has a position, so a watchlist-only widget keeps its old layout.
 */
@Composable
private fun ColumnScope.PortfolioSummaryRow(
    widgetData: SerializableWidgetState,
    quotes: List<Quote>,
) {
    val summary = remember(quotes) { PortfolioSummary.from(quotes) }
    val context = LocalContext.current
    val textColor = ColorProvider(widgetData.textColor)
    val isBold = widgetData.boldText

    if (summary.isEmpty) {
        Text(
            text = context.getString(R.string.no_symbols),
            style = TextStyle(
                color = textColor,
                fontSize = TextUnit(WIDGET_FONT_SIZE, TextUnitType.Sp),
                textAlign = TextAlign.Start,
            ),
            maxLines = 1,
        )
        return
    }

    // The three columns are individually toggleable (今日 / 累计 / 总资产), so each is gated by its
    // own preference and the alignment follows whichever columns remain visible — otherwise turning
    // one off would leave a gap where it used to sit.
    val shownColumns = buildList {
        if (widgetData.showTodayGainLoss) add(COLUMN_TODAY)
        if (widgetData.showTotalGainLoss) add(COLUMN_TOTAL)
        if (widgetData.showMarketValue) add(COLUMN_MARKET_VALUE)
    }

    fun alignOf(column: Int): TextAlign {
        val position = shownColumns.indexOf(column)
        return when {
            shownColumns.size == 1 -> TextAlign.Center
            position == 0 -> TextAlign.Start
            position == shownColumns.size - 1 -> TextAlign.End
            else -> TextAlign.Center
        }
    }

    Row(
        modifier = GlanceModifier.defaultWeight().fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (widgetData.showTodayGainLoss) {
            SummaryItem(
                label = context.getString(R.string.widget_title_today),
                value = summary.todayGainLossString(),
                valueColor = ColorProvider(
                    widgetData.getChangeColor(summary.todayGainLoss, summary.todayGainLossPercent)
                ),
                subtitle = summary.todayGainLossPercentString(),
                subtitleColor = ColorProvider(
                    widgetData.getChangeColor(summary.todayGainLoss, summary.todayGainLossPercent)
                ),
                fontSize = SUMMARY_VALUE_FONT_SIZE,
                isBold = isBold,
                textAlign = alignOf(COLUMN_TODAY),
            )
        }
        if (widgetData.showTotalGainLoss) {
            SummaryItem(
                label = context.getString(R.string.widget_title_total),
                value = summary.totalGainLossString(),
                valueColor = ColorProvider(
                    widgetData.getChangeColor(summary.totalGainLoss, summary.totalGainLossPercent)
                ),
                subtitle = summary.totalGainLossPercentString(),
                subtitleColor = ColorProvider(
                    widgetData.getChangeColor(summary.totalGainLoss, summary.totalGainLossPercent)
                ),
                fontSize = SUMMARY_VALUE_FONT_SIZE,
                isBold = isBold,
                textAlign = alignOf(COLUMN_TOTAL),
            )
        }
        if (widgetData.showMarketValue) {
            SummaryItem(
                label = context.getString(R.string.widget_title_market_value),
                value = summary.marketValueString(),
                valueColor = textColor,
                subtitle = context.getString(R.string.widget_currency_holdings, summary.positionCount),
                subtitleColor = ColorProvider(R.color.text_widget_header),
                fontSize = SUMMARY_VALUE_FONT_SIZE,
                isBold = isBold,
                textAlign = alignOf(COLUMN_MARKET_VALUE),
            )
        }
    }
}

/**
 * One label/value/subtitle column inside [PortfolioSummaryRow]. It claims an equal share of the row's
 * width via [GlanceModifier.defaultWeight] and aligns all three lines to [textAlign] (left / centre /
 * right). The title is always white, the value takes its colour from the caller, and the subtitle
 * is independently coloured so gains/losses can echo the value colour while the market-value column
 * shows a neutral subtitle.
 */
@Composable
private fun RowScope.SummaryItem(
    label: String,
    value: String,
    valueColor: ColorProvider,
    subtitle: String,
    subtitleColor: ColorProvider,
    fontSize: Float,
    isBold: Boolean,
    textAlign: TextAlign,
) {
    Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
            modifier = GlanceModifier.fillMaxWidth(),
            text = label,
            style = TextStyle(
                color = ColorProvider(R.color.widget_title_white),
                fontSize = TextUnit((fontSize - 9f).coerceAtLeast(7f), TextUnitType.Sp),
                textAlign = textAlign,
                fontWeight = FontWeight.Normal,
            ),
            maxLines = 1,
        )
        Text(
            modifier = GlanceModifier.fillMaxWidth(),
            text = value,
            style = TextStyle(
                color = valueColor,
                fontSize = TextUnit(fontSize, TextUnitType.Sp),
                textAlign = textAlign,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
        Text(
            modifier = GlanceModifier.fillMaxWidth(),
            text = subtitle,
            style = TextStyle(
                color = subtitleColor,
                fontSize = TextUnit((fontSize - 9f).coerceAtLeast(7f), TextUnitType.Sp),
                textAlign = textAlign,
                fontWeight = FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Header(
    widgetData: SerializableWidgetState,
) {
    val context = LocalContext.current
    val lastUpdatedText = when (val fetchState = widgetData.fetchState) {
        is SerializableFetchState.Success -> formatClock(fetchState.fetchTime)
        is SerializableFetchState.Failure -> context.getString(R.string.refresh_failed)
        else -> SerializableFetchState.NotFetched.displayString
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Refresh button on the left, clock on the right.
        if (widgetData.showRefreshButton) {
            Box(
                modifier = GlanceModifier.wrapContentSize().clickable(
                    onClick = actionRunCallback<RefreshCallback>()
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (widgetData.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = GlanceModifier.size(18.dp),
                        color = ColorProvider(R.color.text_widget_header),
                    )
                } else {
                    Image(
                        modifier = GlanceModifier.size(18.dp),
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ColorProvider(R.color.text_widget_header)),
                    )
                }
            }
        }

        Text(
            modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp),
            text = lastUpdatedText,
            style = TextStyle(
                color = ColorProvider(R.color.text_widget_header),
                fontSize = TextUnit(HEADER_TIME_FONT_SIZE, TextUnitType.Sp),
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

/** The refresh time as `HH:mm:ss` — the widget header has no room for a date or a weekday. */
private fun formatClock(epochMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))

@Composable
private fun MyPortfolio(
    stock: Quote,
    widgetData: SerializableWidgetState,
) {
    val textColor = ColorProvider(widgetData.textColor)
    val fontSize = WIDGET_FONT_SIZE
    val gainLossFormatted = stock.gainLossString()
    val gainLossPercentFormatted = stock.gainLossPercentString()
    // The "show currency" preference was removed, so amounts are always currency-formatted.
    val priceFormatted = stock.priceFormat.format(stock.lastTradePrice)
    val holdingsFormatted = stock.priceFormat.format(stock.holdings())
    val displayName = stock.properties?.displayname.takeUnless { it.isNullOrBlank() } ?: stock.symbol
    val gainLoss = stock.gainLoss()
    val gainLossColor = ColorProvider(widgetData.getChangeColor(gainLoss, gainLoss))
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .clickable(
                actionStartActivity<HomeActivity>(
                    actionParametersOf(
                        ActionParameters.Key<String>(HomeActivity.EXTRA_SYMBOL) to stock.symbol
                    )
                )
            )
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth().wrapContentHeight()) {
            Text(
                modifier = GlanceModifier.defaultWeight().padding(end = 2.dp),
                text = displayName,
                style = TextStyle(
                    color = textColor,
                    fontSize = TextUnit(fontSize, TextUnitType.Sp),
                    textAlign = TextAlign.Start,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                modifier = GlanceModifier.padding(end = 2.dp),
                text = holdingsFormatted,
                style = TextStyle(
                    color = textColor,
                    fontSize = TextUnit(fontSize, TextUnitType.Sp),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
            Text(
                modifier = GlanceModifier.defaultWeight().padding(end = 2.dp),
                text = gainLossFormatted,
                style = TextStyle(
                    color = gainLossColor,
                    fontSize = TextUnit(fontSize, TextUnitType.Sp),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
            Text(
                modifier = GlanceModifier.defaultWeight().padding(end = 2.dp),
                text = gainLossPercentFormatted,
                style = TextStyle(
                    color = gainLossColor,
                    fontSize = TextUnit(fontSize, TextUnitType.Sp),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }

        Row(modifier = GlanceModifier.fillMaxWidth().wrapContentHeight()) {
            Text(
                modifier = GlanceModifier.defaultWeight(),
                text = priceFormatted,
                style = TextStyle(
                    color = textColor,
                    fontSize = TextUnit(fontSize, TextUnitType.Sp),
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    fontStyle = FontStyle.Italic,
                ),
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 190, heightDp = 150)
@Composable
private fun WidgetSingleColumnPreview() {
    Box(modifier = GlanceModifier.background(color = MaterialTheme.colorScheme.inverseSurface).padding(20.dp)) {
        val data = previewDataState(
            layoutType = IWidgetData.LayoutType.MyPortfolio,
        )
        GlanceWidget(
            widgetData = data,
            quotes = listOf(
                fakeQuote("AAPL"),
                fakeQuote("MSFT"),
                fakeQuote("GOOG"),
                fakeQuote("AMZN"),
                fakeQuote("BRK-B")
            )
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 400, heightDp = 150)
@Composable
private fun WidgetEmptyPreview() {
    Box(modifier = GlanceModifier.background(color = MaterialTheme.colorScheme.inverseSurface).padding(20.dp)) {
        val data = previewDataState(
            layoutType = IWidgetData.LayoutType.MyPortfolio,
        )
        GlanceWidget(
            widgetData = data,
            quotes = emptyList(),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 350, heightDp = 150)
@Composable
private fun WidgetFixedPreview() {
    Box(modifier = GlanceModifier.background(color = MaterialTheme.colorScheme.inverseSurface).padding(20.dp)) {
        val data = previewDataState(
            layoutType = IWidgetData.LayoutType.MyPortfolio,
        )
        GlanceWidget(
            widgetData = data,
            quotes = listOf(
                fakeQuote("AAPL"),
                fakeQuote("MSFT"),
                fakeQuote("GOOG"),
                fakeQuote("AMZN"),
                fakeQuote("BRK-B")
            )
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 350, heightDp = 150)
@Composable
private fun WidgetFixedTranslucentPreview() {
    Box(modifier = GlanceModifier.background(color = MaterialTheme.colorScheme.inverseSurface).padding(20.dp)) {
        val data = previewDataState(
            layoutType = IWidgetData.LayoutType.MyPortfolio,
            backgroundResource = R.drawable.translucent_widget_bg,
        )
        GlanceWidget(
            widgetData = data,
            quotes = listOf(
                fakeQuote("AAPL"),
                fakeQuote("MSFT"),
                fakeQuote("GOOG"),
                fakeQuote("AMZN"),
                fakeQuote("BRK-B")
            )
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 400, heightDp = 120)
@Composable
private fun WidgetAnimatedPreview() {
    Box(modifier = GlanceModifier.background(color = MaterialTheme.colorScheme.inverseSurface).padding(20.dp)) {
        val data = previewDataState(
            layoutType = IWidgetData.LayoutType.MyPortfolio,
        )
        GlanceWidget(
            widgetData = data,
            quotes = listOf(
                fakeQuote("AAPL"),
                fakeQuote("MSFT"),
                fakeQuote("GOOG"),
                fakeQuote("AMZN"),
                fakeQuote("BRK-B")
            )
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 550, heightDp = 140)
@Composable
private fun WidgetTabsPreview() {
    Box(modifier = GlanceModifier.background(color = MaterialTheme.colorScheme.inverseSurface).padding(20.dp)) {
        val data = previewDataState(
            layoutType = IWidgetData.LayoutType.MyPortfolio,
        )
        GlanceWidget(
            widgetData = data,
            quotes = listOf(
                fakeQuote("AAPL"),
                fakeQuote("MSFT"),
                fakeQuote("GOOG"),
                fakeQuote("AMZN"),
                fakeQuote("BRK-B")
            )
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 550, heightDp = 200)
@Composable
private fun WidgetMyPortfolioPreview() {
    Box(modifier = GlanceModifier.background(color = MaterialTheme.colorScheme.inverseSurface).padding(20.dp)) {
        val data = previewDataState(
            layoutType = IWidgetData.LayoutType.MyPortfolio,
        )
        GlanceWidget(
            widgetData = data,
            quotes = listOf(
                fakeQuote("AAPL", fakePosition("AAPL")),
                fakeQuote("MSFT", fakePosition("MSFT")),
                fakeQuote("GOOG", fakePosition("GOOG")),
                fakeQuote("AMZN", fakePosition("AMZN")),
                fakeQuote("BRK-B", fakePosition("BRK-B"))
            )
        )
    }
}

private fun fakeQuote(symbol: String, position: Position? = null): Quote {
    return Quote(
        symbol = symbol,
        position = position,
        lastTradePrice = Random.nextDouble(122434.4242).toFloat(),
        change = Random.nextDouble(48.0).toFloat(),
        changeInPercent = Random.nextDouble(12.0).toFloat(),
    )
}

private fun fakePosition(symbol: String): Position {
    return Position(
        symbol = symbol,
        holdings = mutableListOf(
            Holding(
                symbol = symbol,
                shares = Random.nextDouble(10.0).toFloat(),
                price = Random.nextDouble(1434.4242).toFloat(),
            )
        )
    )
}

private fun previewDataState(
    layoutType: IWidgetData.LayoutType = IWidgetData.LayoutType.MyPortfolio,
    backgroundResource: Int = R.drawable.app_widget_background,
): SerializableWidgetState = SerializableWidgetState(
    layoutType = SerializableLayoutType.from(layoutType),
    boldText = false,
    changeType = SerializableChangeType.Percent,
    fontSize = 12f,
    isDarkMode = false,
    hideWidgetHeader = false,
    negativeTextColor = R.color.text_widget_negative,
    positiveTextColor = R.color.text_widget_positive,
    textColor = R.color.widget_text,
    backgroundResource = backgroundResource,
    isRefreshing = false,
    fetchState = SerializableFetchState.Success(System.currentTimeMillis()),
)

class RefreshCallback : ActionCallback, KoinComponent {
    private val stocksProvider: StocksProvider by inject()

    private val appPreferences: AppPreferences by inject()

    private val widgetDataProvider: WidgetDataProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val glanceAppWidgetManager = GlanceAppWidgetManager(context)
        val appWidgetId = glanceAppWidgetManager.getAppWidgetId(glanceId)
        appPreferences.setRefreshing(true)
        updateAppWidgetState(
            context = context,
            definition = WidgetGlanceStateDefinition,
            glanceId = glanceId
        ) { currentState ->
            currentState.copy(
                widgetState = currentState.widgetState.copy(isRefreshing = true)
            )
        }

        stocksProvider.fetch()

        val widgetData = widgetDataProvider.dataForWidgetId(appWidgetId)
        val currentQuotes = widgetData.stocks.value
        val currentFetchState = stocksProvider.fetchState.value
        updateAppWidgetState(
            context = context,
            definition = WidgetGlanceStateDefinition,
            glanceId = glanceId,
        ) { currentState ->
            currentState.copy(
                quotes = currentQuotes,
                widgetState = currentState.widgetState.copy(
                    fetchState = SerializableFetchState.from(currentFetchState),
                    isRefreshing = false,
                )
            )
        }
    }
}

class FlipTextCallback : ActionCallback, KoinComponent {
    private val stocksProvider: StocksProvider by inject()

    private val widgetDataProvider: WidgetDataProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val glanceAppWidgetManager = GlanceAppWidgetManager(context)
        val appWidgetId = glanceAppWidgetManager.getAppWidgetId(glanceId)
        val widgetData = widgetDataProvider.dataForWidgetId(appWidgetId)
        val currentQuotes = widgetData.stocks.value
        val currentFetchState = stocksProvider.fetchState.value
        // Update Glance state with the flipped change type
        updateAppWidgetState(
            context = context,
            definition = WidgetGlanceStateDefinition,
            glanceId = glanceId,
        ) { currentState ->
            val newChangeType = if (currentState.widgetState.changeType == SerializableChangeType.Value) {
                SerializableChangeType.Percent
            } else {
                SerializableChangeType.Value
            }
            widgetData.setChange(newChangeType == SerializableChangeType.Percent)
            currentState.copy(
                quotes = currentQuotes,
                widgetState = currentState.widgetState.copy(
                    changeType = newChangeType,
                    fetchState = SerializableFetchState.from(currentFetchState),
                )
            )
        }
    }
}
