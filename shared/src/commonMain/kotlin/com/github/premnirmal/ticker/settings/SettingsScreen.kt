package com.github.premnirmal.ticker.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.premnirmal.ticker.navigation.LocalContentBottomPadding
import com.github.premnirmal.ticker.ui.CheckboxPreference
import com.github.premnirmal.ticker.ui.ListPreference
import com.github.premnirmal.ticker.ui.MultiSelectListPreference
import com.github.premnirmal.ticker.ui.SettingsText
import com.github.premnirmal.ticker.ui.TimeSelectorPreference
import com.github.premnirmal.ticker.ui.TopBar

/**
 * Settings screen, shared by Android and iOS. The screen is stateless: the state it renders and the
 * events it raises are hoisted as parameters so it has no Android, navigation, or
 * dependency-injection dependencies:
 *  - the settings snapshot as a [SettingsData] value,
 *  - the user-visible strings and the three string arrays (themes, sync periods, days) as
 *    [String]/[Array] parameters resolved by the host via `stringResource`/`stringArrayResource`,
 *  - all user actions as callback lambdas,
 *  - the `Divider` as a composable [divider] slot (it lives in the Android `:UI` module),
 *  - the alarm-permission banner as an optional [alarmPermissionBanner] slot,
 *  - the fading-edge decoration as [listFadingEdges] (Android `RuntimeShader`),
 *  - the navigation scroll-to-top registration as [registerScrollToTop],
 *  - the version/open-source fonts as nullable [FontFamily] parameters.
 * The Android `SettingsScreenHost.kt` in `:app` supplies them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsData: SettingsData,
    // Strings
    settingsTitle: String,
    appThemeTitle: String,
    updateIntervalTitle: String,
    startTimeTitle: String,
    endTimeTitle: String,
    updateDaysTitle: String,
    autoSortTitle: String,
    autoSortSubtitle: String,
    roundTwoDpTitle: String,
    roundTwoDpSubtitle: String,
    shareTitle: String,
    exportTitle: String,
    exportSubtitle: String,
    openSourceText: String,
    confirmLabel: String,
    dismissLabel: String,
    // String arrays
    themes: Array<String>,
    syncPeriods: Array<String>,
    days: Array<String>,
    // Callbacks
    onThemeSelected: (Int) -> Unit,
    onUpdateIntervalSelected: (Int) -> Unit,
    onStartTimeSet: (time: String, hour: Int, minute: Int) -> Unit,
    onEndTimeSet: (time: String, hour: Int, minute: Int) -> Unit,
    onUpdateDaysSelected: (Set<Int>) -> Unit,
    onAutoSortChanged: (Boolean) -> Unit,
    onRoundToTwoDpChanged: (Boolean) -> Unit,
    onSharePortfolio: () -> Unit,
    onImportPositions: () -> Unit,
    onExportPortfolio: () -> Unit,
    onAShareDataSourceSelected: (Int) -> Unit = {},
    onOpenSource: () -> Unit,
    // Strings (optional additions — hosts that do not wire the feature pass nothing)
    importPositionsTitle: String = "",
    importPositionsSubtitle: String = "",
    importPositionsTemplateTitle: String = "",
    onCopyTemplate: () -> Unit = {},
    aShareDataSourceTitle: String = "",
    aShareDataSources: Array<String> = emptyArray(),
    // Slots & modifiers
    modifier: Modifier = Modifier,
    showAlarmPermissionRequest: Boolean = false,
    alarmPermissionBanner: @Composable () -> Unit = {},
    divider: @Composable () -> Unit = {},
    versionFontFamily: FontFamily? = null,
    openSourceFontFamily: FontFamily? = null,
    listFadingEdges: (ScrollableState) -> Modifier = { Modifier },
    registerScrollToTop: @Composable (scrollToTop: suspend () -> Unit) -> Unit = {},
) {
    val state = rememberLazyListState()
    registerScrollToTop {
        state.animateScrollToItem(0)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface),
        topBar = {
            TopBar(text = settingsTitle)
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val bottomNavPadding = LocalContentBottomPadding.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(listFadingEdges(state)),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding() + bottomNavPadding,
            ),
            state = state,
        ) {
            if (showAlarmPermissionRequest) {
                stickyHeader(key = "alarm_permission_banner") {
                    alarmPermissionBanner()
                }
            }
            settingsItems(
                settingsData = settingsData,
                appThemeTitle = appThemeTitle,
                updateIntervalTitle = updateIntervalTitle,
                startTimeTitle = startTimeTitle,
                endTimeTitle = endTimeTitle,
                updateDaysTitle = updateDaysTitle,
                autoSortTitle = autoSortTitle,
                autoSortSubtitle = autoSortSubtitle,
                roundTwoDpTitle = roundTwoDpTitle,
                roundTwoDpSubtitle = roundTwoDpSubtitle,
                shareTitle = shareTitle,
                exportTitle = exportTitle,
                exportSubtitle = exportSubtitle,
                openSourceText = openSourceText,
                confirmLabel = confirmLabel,
                dismissLabel = dismissLabel,
                themes = themes,
                syncPeriods = syncPeriods,
                days = days,
                onThemeSelected = onThemeSelected,
                onUpdateIntervalSelected = onUpdateIntervalSelected,
                onStartTimeSet = onStartTimeSet,
                onEndTimeSet = onEndTimeSet,
                onUpdateDaysSelected = onUpdateDaysSelected,
                onAutoSortChanged = onAutoSortChanged,
                onRoundToTwoDpChanged = onRoundToTwoDpChanged,
                onSharePortfolio = onSharePortfolio,
                onImportPositions = onImportPositions,
                onExportPortfolio = onExportPortfolio,
                onAShareDataSourceSelected = onAShareDataSourceSelected,
                importPositionsTitle = importPositionsTitle,
                importPositionsSubtitle = importPositionsSubtitle,
                importPositionsTemplateTitle = importPositionsTemplateTitle,
                onCopyTemplate = onCopyTemplate,
                aShareDataSourceTitle = aShareDataSourceTitle,
                aShareDataSources = aShareDataSources,
                onOpenSource = onOpenSource,
                divider = divider,
                openSourceFontFamily = openSourceFontFamily,
            )
        }
    }
}

@Suppress("LongMethod", "LongParameterList")
private fun LazyListScope.settingsItems(
    settingsData: SettingsData,
    appThemeTitle: String,
    updateIntervalTitle: String,
    startTimeTitle: String,
    endTimeTitle: String,
    updateDaysTitle: String,
    autoSortTitle: String,
    autoSortSubtitle: String,
    roundTwoDpTitle: String,
    roundTwoDpSubtitle: String,
    shareTitle: String,
    exportTitle: String,
    exportSubtitle: String,
    openSourceText: String,
    confirmLabel: String,
    dismissLabel: String,
    themes: Array<String>,
    syncPeriods: Array<String>,
    days: Array<String>,
    onThemeSelected: (Int) -> Unit,
    onUpdateIntervalSelected: (Int) -> Unit,
    onStartTimeSet: (time: String, hour: Int, minute: Int) -> Unit,
    onEndTimeSet: (time: String, hour: Int, minute: Int) -> Unit,
    onUpdateDaysSelected: (Set<Int>) -> Unit,
    onAutoSortChanged: (Boolean) -> Unit,
    onRoundToTwoDpChanged: (Boolean) -> Unit,
    onSharePortfolio: () -> Unit,
    onImportPositions: () -> Unit,
    onExportPortfolio: () -> Unit,
    onAShareDataSourceSelected: (Int) -> Unit,
    importPositionsTitle: String,
    importPositionsSubtitle: String,
    importPositionsTemplateTitle: String,
    onCopyTemplate: () -> Unit,
    aShareDataSourceTitle: String,
    aShareDataSources: Array<String>,
    onOpenSource: () -> Unit,
    divider: @Composable () -> Unit,
    openSourceFontFamily: FontFamily?,
) {
    item {
        ListPreference(
            title = appThemeTitle,
            items = themes,
            selected = settingsData.themePref,
            onSelected = onThemeSelected,
        )
        divider()
    }
    item {
        ListPreference(
            title = updateIntervalTitle,
            items = syncPeriods,
            selected = settingsData.updateIntervalPref,
            onSelected = onUpdateIntervalSelected,
        )
        divider()
    }
    item {
        TimeSelectorPreference(
            title = startTimeTitle,
            hour = settingsData.startTime.hour,
            minute = settingsData.startTime.minute,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onTimeSet = onStartTimeSet,
        )
        divider()
    }
    item {
        TimeSelectorPreference(
            title = endTimeTitle,
            hour = settingsData.endTime.hour,
            minute = settingsData.endTime.minute,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onTimeSet = onEndTimeSet,
        )
        divider()
    }
    item {
        MultiSelectListPreference(
            title = updateDaysTitle,
            items = days,
            selected = settingsData.updateDays.map { it - 1 }.toSet(),
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onSelected = { selected ->
                onUpdateDaysSelected(selected.map { it + 1 }.toSet())
            },
        )
        divider()
    }
    item {
        CheckboxPreference(
            title = autoSortTitle,
            subtitle = autoSortSubtitle,
            checked = settingsData.autoSort ?: false,
            enabled = !settingsData.hasWidgets,
            showCheckbox = !settingsData.hasWidgets,
            onCheckChanged = onAutoSortChanged,
        )
        divider()
    }
    item {
        CheckboxPreference(
            title = roundTwoDpTitle,
            subtitle = roundTwoDpSubtitle,
            checked = settingsData.roundToTwoDp,
            onCheckChanged = onRoundToTwoDpChanged,
        )
        divider()
    }
    item {
        SettingsText(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSharePortfolio() },
            title = shareTitle,
        )
        divider()
    }
    if (importPositionsTitle.isNotEmpty()) {
        item {
            SettingsText(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onImportPositions() },
                title = importPositionsTitle,
                subtitle = importPositionsSubtitle,
            )
            divider()
        }
    }
    if (importPositionsTemplateTitle.isNotEmpty()) {
        item {
            SettingsText(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopyTemplate() },
                title = importPositionsTemplateTitle,
            )
            divider()
        }
    }
    item {
        SettingsText(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExportPortfolio() },
            title = exportTitle,
            subtitle = exportSubtitle,
        )
        divider()
    }
    if (aShareDataSourceTitle.isNotEmpty() && aShareDataSources.isNotEmpty()) {
        item {
            ListPreference(
                title = aShareDataSourceTitle,
                items = aShareDataSources,
                selected = settingsData.aShareDataSourcePref,
                onSelected = onAShareDataSourceSelected,
            )
            divider()
        }
    }
    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { onOpenSource() }
        ) {
            Text(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .align(Alignment.Center),
                text = openSourceText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = openSourceFontFamily,
            )
        }
        divider()
    }
}
