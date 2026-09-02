package com.github.premnirmal.ticker.settings

import android.content.Intent
import android.os.Build.VERSION
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.premnirmal.ticker.CustomTabs
import com.github.premnirmal.ticker.home.HomeViewModel
import com.github.premnirmal.ticker.navigation.HomeRoute
import com.github.premnirmal.ticker.navigation.rememberScrollToTopAction
import com.github.premnirmal.ticker.showDialog
import com.github.premnirmal.ticker.ui.fadingEdges
import com.github.premnirmal.tickerwidget.R
import com.github.premnirmal.tickerwidget.ui.Divider
import com.github.premnirmal.tickerwidget.ui.theme.alegreyaFontFamily
import com.github.premnirmal.tickerwidget.ui.theme.boldFontFamily
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Android host for the shared [SettingsScreen]. Resolves the Koin [SettingsViewModel], the localised
 * labels/string-arrays, the CSV-text import dialog, the [CustomTabs] open-source link, the
 * alarm-permission banner, the [Divider] slot, the fonts, the [fadingEdges] and the navigation
 * [rememberScrollToTopAction] registration, then delegates to the shared screen.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val settingsData by viewModel.settings.collectAsStateWithLifecycle()
    val showAlarmPermissionRequest = remember { homeViewModel.showAlarmPermissionRequest }
    val context = LocalContext.current

    val primaryColor = MaterialTheme.colorScheme.primary

    var showImportDialog by remember { mutableStateOf(false) }
    var csvText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
        onOpenSource = { CustomTabs.openTab(context, GITHUB_URL, primaryColor.toArgb()) },
        aShareDataSourceTitle = stringResource(id = R.string.a_share_data_source),
        aShareDataSources = stringArrayResource(id = R.array.a_share_data_sources),
        modifier = modifier,
        showAlarmPermissionRequest = showAlarmPermissionRequest,
        alarmPermissionBanner = { AlarmPermissionBanner() },
        divider = { Divider() },
        versionFontFamily = alegreyaFontFamily,
        openSourceFontFamily = boldFontFamily,
        listFadingEdges = { state: ScrollableState -> Modifier.fadingEdges(state) },
        registerScrollToTop = { scrollToTop ->
            rememberScrollToTopAction(HomeRoute.Settings, scrollToTop = scrollToTop)
        },
    )

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(text = stringResource(id = R.string.action_import_positions)) },
            text = {
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(id = R.string.import_positions_desc)) },
                    singleLine = false,
                    minLines = 6,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val count = viewModel.importPositionsFromText(csvText)
                            showImportDialog = false
                            val message = when {
                                count == null -> context.getString(R.string.positions_import_fail)
                                count == 0 -> context.getString(R.string.positions_import_empty)
                                else -> context.getString(R.string.positions_import_success, count)
                            }
                            context.showDialog(message)
                        }
                    }
                ) { Text(text = stringResource(id = R.string.action_import)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AlarmPermissionBanner() {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            ),
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = stringResource(id = R.string.exact_alarm_permission_required_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                TextButton(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    onClick = {
                        if (VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        }
                    },
                ) {
                    Text(
                        text = stringResource(id = R.string.go_to_settings),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

private const val GITHUB_URL = "https://github.com/DaLiangWang/StockTicker"
