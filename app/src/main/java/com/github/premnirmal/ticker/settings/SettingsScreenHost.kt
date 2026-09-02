package com.github.premnirmal.ticker.settings

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.premnirmal.ticker.CustomTabs
import com.github.premnirmal.ticker.navigation.HomeRoute
import com.github.premnirmal.ticker.navigation.rememberScrollToTopAction
import com.github.premnirmal.ticker.ui.fadingEdges
import com.github.premnirmal.tickerwidget.R
import com.github.premnirmal.tickerwidget.ui.Divider
import com.github.premnirmal.tickerwidget.ui.theme.alegreyaFontFamily
import com.github.premnirmal.tickerwidget.ui.theme.boldFontFamily
import org.koin.androidx.compose.koinViewModel

/**
 * Android host for the shared [SettingsScreen]. Resolves the Koin [SettingsViewModel], the localised
 * labels/string-arrays, the CSV-text import dialog, the [CustomTabs] open-source link, the
 * [Divider] slot, the fonts, the [fadingEdges] and the navigation
 * [rememberScrollToTopAction] registration, then delegates to the shared screen.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val settingsData by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val primaryColor = MaterialTheme.colorScheme.primary

    SettingsScreen(
        settingsData = settingsData,
        settingsTitle = stringResource(id = R.string.app_settings),
        appThemeTitle = stringResource(id = R.string.app_theme),
        updateIntervalTitle = stringResource(id = R.string.update_interval),
        startTimeTitle = stringResource(id = R.string.start_time),
        endTimeTitle = stringResource(id = R.string.end_time),
        updateDaysTitle = stringResource(id = R.string.update_days),
        roundTwoDpTitle = stringResource(id = R.string.round_two_dp),
        roundTwoDpSubtitle = stringResource(id = R.string.round_two_dp_desc),
        openSourceText = GITHUB_URL,
        confirmLabel = stringResource(id = R.string.ok),
        dismissLabel = stringResource(id = R.string.cancel),
        themes = stringArrayResource(id = R.array.app_themes),
        syncPeriods = stringArrayResource(id = R.array.sync_periods),
        days = stringArrayResource(id = R.array.days),
        onThemeSelected = { viewModel.setThemePref(it) },
        onUpdateIntervalSelected = { viewModel.setUpdateIntervalPref(it) },
        onStartTimeSet = { time, hour, minute -> viewModel.setStartTime(time, hour, minute) },
        onEndTimeSet = { time, hour, minute -> viewModel.setEndTime(time, hour, minute) },
        onUpdateDaysSelected = { viewModel.setUpdateDaysPref(it) },
        onRoundToTwoDpChanged = { viewModel.setRoundToTwoDp(it) },
        onAShareDataSourceSelected = { viewModel.setAShareDataSource(it) },
        onWidgetAutoRefreshChanged = { viewModel.setWidgetAutoRefresh(it) },
        onOpenSource = { CustomTabs.openTab(context, GITHUB_URL, primaryColor.toArgb()) },
        aShareDataSourceTitle = stringResource(id = R.string.a_share_data_source),
        aShareDataSources = stringArrayResource(id = R.array.a_share_data_sources),
        widgetAutoRefreshTitle = stringResource(id = R.string.widget_auto_refresh),
        widgetAutoRefreshSubtitle = stringResource(id = R.string.widget_auto_refresh_desc),
        modifier = modifier,
        divider = { Divider() },
        versionFontFamily = alegreyaFontFamily,
        openSourceFontFamily = boldFontFamily,
        listFadingEdges = { state: ScrollableState -> Modifier.fadingEdges(state) },
        registerScrollToTop = { scrollToTop ->
            rememberScrollToTopAction(HomeRoute.Settings, scrollToTop = scrollToTop)
        },
    )
}

private const val GITHUB_URL = "https://github.com/DaLiangWang/StockTicker"
