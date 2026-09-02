package com.github.premnirmal.ticker.portfolio.search

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.DisplayFeature
import com.github.premnirmal.ticker.detail.QuoteCard
import com.github.premnirmal.ticker.navigation.HomeRoute
import com.github.premnirmal.ticker.navigation.rememberScrollToTopAction
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.ui.fadingEdges
import com.github.premnirmal.tickerwidget.R
import org.koin.androidx.compose.koinViewModel

/**
 * Android host for the shared [com.github.premnirmal.ticker.portfolio.search.SearchScreen]. Resolves
 * the Koin [SearchViewModel], the localised labels, the `ic_close` clear icon,
 * the Android `QuoteCard`/`SuggestionItem`/`AddSymbolDialog` slots, the `RuntimeShader`-based
 * [fadingEdges], the navigation [rememberScrollToTopAction] registration and the adaptive
 * Accompanist [TwoPane] layout, then delegates to the shared screen.
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass,
    displayFeatures: List<DisplayFeature>,
    onQuoteClick: (Quote) -> Unit,
    selectedWidgetId: Int? = null,
    searchViewModel: SearchViewModel = koinViewModel(),
) {
    val searchResults by searchViewModel.searchResult.collectAsStateWithLifecycle()
    val trendingStocks by searchViewModel.trendingStocks.collectAsStateWithLifecycle(emptyList())
    val isRefreshing by searchViewModel.isRefreshing.collectAsStateWithLifecycle()
    LaunchedEffect(trendingStocks.isEmpty()) {
        if (trendingStocks.isEmpty()) {
            searchViewModel.fetchTrending()
        }
    }
    SearchScreen(
        searchTitle = stringResource(id = R.string.action_search),
        searchFieldLabel = stringResource(id = R.string.enter_a_symbol),
        suggestionsErrorText = stringResource(id = R.string.error_fetching_suggestions),
        clearIcon = painterResource(id = R.drawable.ic_close),
        searchResults = searchResults,
        trendingStocks = trendingStocks,
        isRefreshing = isRefreshing,
        onQueryChange = { query -> searchViewModel.fetchResults(query) },
        onRefresh = { searchViewModel.fetchTrending() },
        onQuoteClick = onQuoteClick,
        quoteCard = { quote, onClick ->
            QuoteCard(
                quote = quote,
                onClick = onClick,
                quoteNameMaxLines = 1,
            )
        },
        suggestionItem = { suggestion, onSuggestionClick, onSuggestionAddRemoveClick ->
            SuggestionItem(
                suggestion = suggestion,
                onSuggestionClick = onSuggestionClick,
                onSuggestionAddRemoveClick = onSuggestionAddRemoveClick,
            )
        },
        modifier = modifier,
        selectedWidgetId = selectedWidgetId,
        listFadingEdges = { state: ScrollableState -> Modifier.fadingEdges(state) },
        registerScrollToTop = { scrollToTop ->
            rememberScrollToTopAction(HomeRoute.Search, scrollToTop = scrollToTop)
        },
        addSymbolDialog = { symbol, onDismissRequest ->
            AddSymbolDialog(
                symbol = symbol,
                onDismissRequest = onDismissRequest,
            )
        },
    )
}
