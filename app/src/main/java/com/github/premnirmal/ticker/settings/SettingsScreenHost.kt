package com.github.premnirmal.ticker.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
 * labels/string-arrays, the file-picker launchers for share/export, the CSV-text import dialog, the
 * "copy CSV template" clipboard action, the [CustomTabs] open-source link, the alarm-permission
 * banner, the [Divider] slot, the fonts, the [fadingEdges] and the navigation
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

    // File-picker launchers for share / export only. Position imports are now entered as CSV text in
    // a dialog (see [showImportDialog] below), so there is no document picker for them.
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) {
        if (it != null) {
            viewModel.sharePortfolio(context, it)
        } else {
            context.showDialog(R.string.error_sharing)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) {
        if (it != null) {
            viewModel.exportPortfolio(context, it)
        } else {
            context.showDialog(R.string.error_exporting)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    var showImportDialog by remember { mutableStateOf(false) }
    var csvText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    SettingsScreen(
        settingsData = settingsData,
        settingsTitle = stringResource(id = R.string.app_settings),
        appThemeTitle = stringResource(id = R.string.app_theme),
        updateIntervalTitle = stringResource(id = R.string.update_interval),
        startTimeTitle = stringResource(id = R.string.start_time),
        endTimeTitle = stringResource(id = R.string.end_time),
        updateDaysTitle = stringResource(id = R.string.update_days),
        autoSortTitle = stringResource(id = R.string.auto_sort),
        autoSortSubtitle = stringResource(id = R.string.auto_sort_desc),
        roundTwoDpTitle = stringResource(id = R.string.round_two_dp),
        roundTwoDpSubtitle = stringResource(id = R.string.round_two_dp_desc),
        shareTitle = stringResource(id = R.string.action_share),
        exportTitle = stringResource(id = R.string.action_export),
        exportSubtitle = stringResource(id = R.string.export_desc),
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
        onAutoSortChanged = { viewModel.setAutoSort(it) },
        onRoundToTwoDpChanged = { viewModel.setRoundToTwoDp(it) },
        onSharePortfolio = { shareLauncher.launch("portfolio.txt") },
        onImportPositions = { showImportDialog = true },
        onExportPortfolio = { exportLauncher.launch("portfolio.json") },
        onAShareDataSourceSelected = { viewModel.setAShareDataSource(it) },
        onOpenSource = { CustomTabs.openTab(context, GITHUB_URL, primaryColor.toArgb()) },
        importPositionsTitle = stringResource(id = R.string.action_import_positions),
        importPositionsSubtitle = stringResource(id = R.string.import_positions_desc),
        importPositionsTemplateTitle = stringResource(id = R.string.import_positions_template),
        onCopyTemplate = {
            clipboard.setPrimaryClip(
                ClipData.newPlainText("csv_template", POSITIONS_CSV_TEMPLATE)
            )
            context.showDialog(R.string.positions_import_template_copied)
        },
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
                TextButton(
                    onClick = {
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("csv_template", POSITIONS_CSV_TEMPLATE)
                        )
                        context.showDialog(R.string.positions_import_template_copied)
                    }
                ) { Text(text = stringResource(id = R.string.import_positions_template)) }
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

private const val POSITIONS_CSV_TEMPLATE = "symbol,market,name,quantity,cost_price,currency,note\n" +
    "515210,a,钢铁ETF国泰,2800,1.1733,CNY,\n" +
    "600022,a,山东钢铁,3800,1.3713,CNY,\n" +
    "600066,a,宇通客车,300,30.5970,CNY,\n" +
    "600819,a,耀皮玻璃,1800,5.7723,CNY,\n" +
    "600825,a,新华传媒,1900,5.2901,CNY,\n" +
    "605368,a,蓝天燃气,1500,7.0147,CNY,\n" +
    "002756,a,永兴材料,100,46.0010,CNY,\n" +
    "159516,a,半导体设备ETF国泰,13300,0.7032,CNY,"
