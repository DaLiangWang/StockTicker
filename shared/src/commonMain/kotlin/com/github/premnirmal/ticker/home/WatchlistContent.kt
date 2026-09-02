package com.github.premnirmal.ticker.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.market_value
import com.github.premnirmal.shared.resources.today_gain_loss
import com.github.premnirmal.shared.resources.total_gain_loss
import com.github.premnirmal.ticker.model.PortfolioSummary
import com.github.premnirmal.ticker.navigation.LocalContentBottomPadding
import com.github.premnirmal.ticker.detail.QUOTE_TABLE_WIDTH
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.tickerwidget.ui.theme.SharedColours
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

/**
 * Home watchlist screen, shared by Android and iOS. The screen is stateless: the state it renders
 * and the events it raises are hoisted as parameters so it has no Android, navigation or
 * dependency-injection dependencies:
 *  - the collapsing-header title/subtitle/widget tabs from plain values
 *    ([appName]/[subtitle]/[hasWidgets]/[widgets]),
 *  - the holdings popup gating from [hasHoldings]/[totalGainLoss] plus the popup itself as a
 *    [totalHoldingsPopup] slot,
 *  - the refresh state/event as [isRefreshing]/[onRefresh] and the quote tap as [onQuoteClick],
 *  - the localised app name as a [String] and the holdings icon as a [Painter] ([totalHoldingsIcon]),
 *  - the theme-aware header background as a nullable [Painter] ([headerBackground]; null = no image,
 *    e.g. in the dual-pane list),
 *  - the quote card as a composable [quoteCard] slot (it still pulls in the not-yet-shared theme),
 *  - the navigation scroll-to-top registrations as [registerResetScroll]/[registerWidgetScroll].
 * The Android `WatchlistContent` host in `:app` supplies them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistContent(
    appName: String,
    hasHoldings: Boolean,
    isRefreshing: Boolean,
    widgets: List<WatchlistWidget>,
    totalGainLoss: TotalGainLoss?,
    totalHoldingsIcon: Painter,
    lastUpdated: String,
    onRefresh: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    quoteCard: @Composable (
        quote: Quote,
        modifier: Modifier,
        onClick: () -> Unit,
        onRemoveClick: (Quote) -> Unit,
    ) -> Unit,
    totalHoldingsPopup: @Composable (totalHoldings: TotalGainLoss, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    listFadingEdges: (ScrollableState) -> Modifier = { Modifier },
    registerWidgetScroll: @Composable (index: Int, scroll: suspend () -> Unit) -> Unit = { _, _ -> },
) {
    var showTotalHoldingsPopup by remember {
        mutableStateOf(false)
    }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            appName = appName,
            hasHoldings = hasHoldings,
            totalHoldingsIcon = totalHoldingsIcon,
            onTotalHoldingsClick = { showTotalHoldingsPopup = true },
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val constraints = this.constraints
            val rowState = rememberLazyListState()
            val hapticFeedback = LocalHapticFeedback.current
            val windowInfo = LocalWindowInfo.current
            val density = LocalDensity.current
            val gridSize = remember(windowInfo.containerSize, constraints.maxWidth, constraints.maxHeight) {
                // Cap the grid to the window when its size is known, but fall back to the available
                // layout constraints when the window size is reported as zero. On iOS the window's
                // containerSize is momentarily 0 after the app is backgrounded and reopened, and taking
                // min(..., 0) would size the grid to 0 width and leave the watchlist entirely blank.
                val containerWidth = windowInfo.containerSize.width
                val containerHeight = windowInfo.containerSize.height
                val effectiveWidth =
                    if (containerWidth > 0) min(constraints.maxWidth, containerWidth) else constraints.maxWidth
                val effectiveHeight =
                    if (containerHeight > 0) containerHeight else constraints.maxHeight
                val widthDp = with(density) { effectiveWidth.toDp() }
                val heightDp = with(density) { effectiveHeight.toDp() }
                DpSize(widthDp, heightDp)
            }
            Content(
                widgets = widgets,
                gridSize = gridSize,
                rowState = rowState,
                hapticFeedback = hapticFeedback,
                isRefreshing = isRefreshing,
                lastUpdated = lastUpdated,
                onRefresh = onRefresh,
                onQuoteClick = onQuoteClick,
                quoteCard = quoteCard,
                listFadingEdges = listFadingEdges,
                registerWidgetScroll = registerWidgetScroll,
            )
        }
    }
    if (showTotalHoldingsPopup && totalGainLoss != null) {
        totalHoldingsPopup(totalGainLoss) {
            showTotalHoldingsPopup = false
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopAppBar(
    modifier: Modifier = Modifier,
    appName: String,
    hasHoldings: Boolean,
    totalHoldingsIcon: Painter,
    onTotalHoldingsClick: () -> Unit,
) {
    com.github.premnirmal.ticker.ui.TopBar(
        modifier = modifier,
        text = appName,
        colors = TopAppBarDefaults.topAppBarColors(),
        actions = {
            if (hasHoldings) {
                IconButton(
                    onClick = onTotalHoldingsClick,
                ) {
                    Icon(
                        painter = totalHoldingsIcon,
                        contentDescription = null,
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    widgets: List<WatchlistWidget>,
    gridSize: DpSize,
    rowState: LazyListState,
    hapticFeedback: HapticFeedback,
    isRefreshing: Boolean,
    lastUpdated: String,
    onRefresh: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    quoteCard: @Composable (
        quote: Quote,
        modifier: Modifier,
        onClick: () -> Unit,
        onRemoveClick: (Quote) -> Unit,
    ) -> Unit,
    listFadingEdges: (ScrollableState) -> Modifier,
    registerWidgetScroll: @Composable (index: Int, scroll: suspend () -> Unit) -> Unit,
) {
    if (widgets.isEmpty()) {
        return
    }
    run {
        val width = gridSize.width
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start,
            state = rowState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
        ) {
            items(widgets.size) { index ->
                val widget = widgets[index]
                val quotesList by widget.stocks.collectAsState()
                var quotes by remember(quotesList) { mutableStateOf(quotesList) }
                val lazyStaggeredGridState = rememberLazyStaggeredGridState()
                val reorderableLazyStaggeredGridState = rememberReorderableLazyStaggeredGridState(
                    lazyStaggeredGridState
                ) { from, to ->
                    quotes = quotes.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
                registerWidgetScroll(index) {
                    lazyStaggeredGridState.animateScrollToItem(0)
                }
                val horizontalScrollState = rememberScrollState()
                PullToRefreshBox(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    onRefresh = onRefresh,
                    isRefreshing = isRefreshing
                ) {
                    // One shared horizontal scroll so every row of the table moves together.
                    //
                    // The scroll container needs an explicit, bounded width: the surrounding LazyRow
                    // measures its pages with an *unbounded* width, so fillMaxWidth() here would hand
                    // horizontalScroll() infinite constraints and crash. `width` is the page viewport.
                    Box(
                        modifier = Modifier
                            .width(width)
                            .fillMaxHeight()
                            .horizontalScroll(horizontalScrollState),
                    ) {
                        // Header and table share one horizontal scroll container so they pan
                        // together instead of drifting apart when the user scrolls sideways.
                        Column(
                            modifier = Modifier
                                // At least wide enough for the columns; wider screens stretch the
                                // name column instead of leaving a gap on the right.
                                .width(maxOf(QUOTE_TABLE_WIDTH, width))
                                .fillMaxHeight(),
                        ) {
                            WidgetTabHeader(
                                quotes = quotes,
                                lastUpdated = lastUpdated,
                            )
                            LazyVerticalStaggeredGrid(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .then(listFadingEdges(lazyStaggeredGridState)),
                                state = lazyStaggeredGridState,
                            columns = StaggeredGridCells.Fixed(1),
                            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp + LocalContentBottomPadding.current),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                        ) {
                            itemsIndexed(
                                quotes,
                                key = { _, quote -> quote.symbol }
                            ) { _, quote ->
                                ReorderableItem(reorderableLazyStaggeredGridState, key = quote.symbol) {
                                    quoteCard(
                                        quote,
                                        Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.GestureThresholdActivate
                                                )
                                            },
                                            onDragStopped = {
                                                val tickers = quotes.map { it.symbol }
                                                widget.rearrange(tickers)
                                                widget.setAutoSort(false)
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            },
                                        ),
                                        { onQuoteClick(quote) },
                                        { q -> widget.removeStock(q.symbol) },
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Summary strip rendered at the top of every watchlist page.
 *
 * Each page is one widget, so the totals come from that page's quotes alone — two pages holding
 * different portfolios report different numbers, which is exactly why the summary lives inside the
 * page rather than in a single shared header. The refresh time is global (one quote fetch feeds
 * every widget) and therefore reads the same on each page.
 */
@Composable
private fun WidgetTabHeader(
    quotes: List<Quote>,
    lastUpdated: String,
) {
    val summary = remember(quotes) { PortfolioSummary.from(quotes) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryStatistic(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.market_value),
                value = summary.marketValueString(),
            )
            SummaryStatistic(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.today_gain_loss),
                value = summary.todayGainLossString(),
                subtitle = summary.todayGainLossPercentString(),
                valueColor = SharedColours.changeColour(
                    up = summary.todayGainLoss > 0f,
                    down = summary.todayGainLoss < 0f,
                ),
            )
            SummaryStatistic(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.total_gain_loss),
                value = summary.totalGainLossString(),
                subtitle = summary.totalGainLossPercentString(),
                valueColor = SharedColours.changeColour(
                    up = summary.totalGainLoss > 0f,
                    down = summary.totalGainLoss < 0f,
                ),
            )
        }
        if (lastUpdated.isNotEmpty()) {
            Text(
                text = lastUpdated,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One label/value pair of [WidgetTabHeader]. [subtitle] carries the matching percentage, and both
 * it and [value] take [valueColor] so a gain or loss reads as a single coloured block.
 */
@Composable
private fun SummaryStatistic(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = valueColor,
            )
        }
    }
}

