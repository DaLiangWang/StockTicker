package com.github.premnirmal.ticker.detail

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.DisplayFeature
import com.github.premnirmal.ticker.navigation.calculateContentAndNavigationType
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.network.data.changeColour
import com.github.premnirmal.ticker.news.QuoteDetailViewModel
import com.github.premnirmal.ticker.portfolio.HoldingsActivity
import com.github.premnirmal.ticker.ui.ContentType.SINGLE_PANE
import com.github.premnirmal.ticker.ui.LocalAppMessaging
import com.github.premnirmal.ticker.ui.fadingEdges
import com.github.premnirmal.ticker.ui.formatAxisDate
import com.github.premnirmal.ticker.ui.formatAxisHour
import com.github.premnirmal.ticker.ui.formatAxisValue
import com.github.premnirmal.ticker.ui.formatChartMarker
import com.github.premnirmal.tickerwidget.R
import com.github.premnirmal.tickerwidget.ui.AppCard
import com.github.premnirmal.tickerwidget.ui.theme.ColourPalette
import com.google.accompanist.adaptive.FoldAwareConfiguration
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import org.koin.androidx.compose.koinViewModel

/**
 * Android host for the shared [com.github.premnirmal.ticker.detail.QuoteDetailScreen]. Resolves the
 * Koin [QuoteDetailViewModel]/[AppPreferences], collects the ViewModel state, derives the localised
 * [QuoteDetailItem] rows (`buildQuoteDetails`), the change/up/down [ColourPalette] colours, the
 * localised [QuoteDetailStrings], the `ic_refresh`/`ic_edit` icons, the
 * `AppCard`/`NewsCard`/`LinkText` slots, the platform chart formatters, the
 * `RuntimeShader`-based [fadingEdges], the [AppMessaging] snackbar host, the per-section
 * `Holdings`/`Alerts`/`Notes`/`Displayname` activity-result launchers and the adaptive Accompanist
 * [TwoPane] layout, owns the `loadQuote`/`fetchAll`/`fetchQuoteInRealTime`/`reset` lifecycle and the
 * range-change chart fetch, then delegates to the shared screen.
 */
@Composable
fun QuoteDetailScreen(
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass,
    contentType: com.github.premnirmal.ticker.ui.ContentType?,
    displayFeatures: List<DisplayFeature>,
    quote: Quote,
    viewModel: QuoteDetailViewModel = koinViewModel()
) {
    val resolvedContentType = contentType
        ?: calculateContentAndNavigationType(
            widthSizeClass = widthSizeClass, displayFeatures = displayFeatures
        ).second
    val context = LocalContext.current
    val appMessaging = LocalAppMessaging.current

    val quoteDetail by viewModel.quote.collectAsStateWithLifecycle(null)
    val currentQuote = quoteDetail?.dataSafe?.quote ?: quote
    val details = remember(quoteDetail) {
        quoteDetail?.takeIf { it.wasSuccessful }
            ?.let { buildQuoteDetails(it.data.quote, context) }
            ?: emptyList()
    }
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val chartData by viewModel.data.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val graphError by viewModel.dataFetchError.collectAsStateWithLifecycle()
    var isInPortfolio by remember(currentQuote, currentQuote.position) {
        mutableStateOf(viewModel.isInPortfolio(currentQuote.symbol))
    }

    val changeColour = chartData?.changeColour ?: currentQuote.changeColour

    // Per-section editable state, updated by the activity-result launchers below.
    var holdings by remember(currentQuote.position) { mutableStateOf(currentQuote.position) }

    val holdingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            holdings = result.data?.getParcelableExtra(HoldingsActivity.POSITIONS) ?: holdings
        }
    }

    val strings = QuoteDetailStrings(
        graphFetchFailed = stringResource(R.string.graph_fetch_failed),
        rangeOneDay = stringResource(R.string.one_day_short),
        rangeTwoWeeks = stringResource(R.string.two_weeks_short),
        rangeOneMonth = stringResource(R.string.one_month_short),
        rangeThreeMonth = stringResource(R.string.three_month_short),
        rangeOneYear = stringResource(R.string.one_year_short),
        rangeFiveYears = stringResource(R.string.five_years_short),
        rangeMax = stringResource(R.string.max),
        positions = stringResource(R.string.positions),
        alerts = stringResource(R.string.alerts),
        history = stringResource(R.string.position_history),
        buyIn = stringResource(R.string.buy_in),
        sellOut = stringResource(R.string.sell_out),
        shares = stringResource(R.string.shares),
        equityValue = stringResource(R.string.equity_value),
        averagePrice = stringResource(R.string.average_price),
        gainLoss = stringResource(R.string.gain_loss),
        dayChangeAmount = stringResource(R.string.day_change_amount),
        alertAbove = stringResource(R.string.alert_above),
        alertBelow = stringResource(R.string.alert_below),
    )

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { appMessaging.sendSnackbar(it) }
    }
    LaunchedEffect(currentQuote.symbol, range) {
        viewModel.fetchChartData(currentQuote.symbol, range)
    }

    QuoteDetailScreen(
        quote = currentQuote,
        chartData = chartData,
        changeColour = changeColour,
        upColor = ColourPalette.UpColour,
        downColor = ColourPalette.DownColour,
        details = details,
        isInPortfolio = isInPortfolio,
        isRefreshing = isRefreshing,
        range = range,
        graphError = graphError != null,
        position = holdings,
        historyLabel = strings.history,
        buyLabel = strings.buyIn,
        sellLabel = strings.sellOut,
        strings = strings,
        refreshIcon = painterResource(R.drawable.ic_refresh),
        editIcon = painterResource(R.drawable.ic_edit),
        snackbarHostState = appMessaging.snackbarHostState,
        onRefresh = {
            if (!isRefreshing) {
                viewModel.fetchAll(currentQuote)
            }
        },
        onCardClick = { title, data -> appMessaging.sendBottomSheet(title, data) },
        onEditPositions = {
            holdingsLauncher.launch(
                Intent(context, HoldingsActivity::class.java)
                    .putExtra(HoldingsActivity.TICKER, currentQuote.symbol)
            )
        },

        hourAxisFormatter = ::formatAxisHour,
        dateAxisFormatter = ::formatAxisDate,
        valueAxisFormatter = ::formatAxisValue,
        markerFormatter = ::formatChartMarker,
        card = { cardModifier, onClick, content ->
            AppCard(modifier = cardModifier, onClick = onClick, content = content)
        },
        modifier = modifier,
        listFadingEdges = { state -> Modifier.fadingEdges(state) },
        twoPane = if (resolvedContentType == SINGLE_PANE) {
            null
        } else {
            { first, second ->
                TwoPane(
                    strategy = HorizontalTwoPaneStrategy(
                        splitFraction = 1f / 2f,
                    ),
                    displayFeatures = displayFeatures,
                    foldAwareConfiguration = FoldAwareConfiguration.VerticalFoldsOnly,
                    first = first,
                    second = second,
                )
            }
        },
    )

    DisposableEffect(currentQuote.symbol) {
        viewModel.loadQuote(currentQuote.symbol)
        viewModel.fetchAll(currentQuote)
        viewModel.fetchQuoteInRealTime(currentQuote.symbol)
        onDispose {
            viewModel.reset()
        }
    }
}
