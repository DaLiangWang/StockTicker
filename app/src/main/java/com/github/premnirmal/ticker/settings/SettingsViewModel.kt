package com.github.premnirmal.ticker.settings

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.showDialog
import com.github.premnirmal.ticker.widget.WidgetData
import com.github.premnirmal.ticker.widget.WidgetDataProvider
import com.github.premnirmal.tickerwidget.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsViewModel constructor(
    private val widgetDataProvider: WidgetDataProvider,
    private val appPreferences: AppPreferences,
    private val stocksProvider: StocksProvider,
) : ViewModel() {

    val settings: StateFlow<SettingsData>
        get() = _settings
    private val _settings by lazy {
        MutableStateFlow(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
    }
    val error: Flow<Int>
        get() = _error
    private val _error = MutableSharedFlow<Int>()

    val success: Flow<Int>
        get() = _success
    private val _success = MutableSharedFlow<Int>()

    private val widgetDataList: Flow<List<WidgetData>>
        get() = widgetDataProvider.widgetData

    init {
        viewModelScope.launch {
            widgetDataList.collect { widgetDataList ->
                val widgetData = widgetDataList.find { it.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID }
                widgetData?.let {
                    _settings.emit(buildData(it))
                }
            }
        }
    }

    fun setThemePref(themePref: Int) {
        viewModelScope.launch {
            appPreferences.themePref = themePref
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            broadcastUpdateWidget()
        }
    }

    fun setUpdateIntervalPref(intervalPref: Int) {
        viewModelScope.launch {
            appPreferences.updateIntervalPref = intervalPref
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            stocksProvider.scheduleUpdate()
            broadcastUpdateWidget()
        }
    }

    fun setStartTime(time: String, _hour: Int, _minute: Int) {
        viewModelScope.launch {
            appPreferences.setStartTime(time)
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            broadcastUpdateWidget()
        }
    }

    fun setEndTime(time: String, _hour: Int, _minute: Int) {
        viewModelScope.launch {
            appPreferences.setEndTime(time)
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            broadcastUpdateWidget()
        }
    }

    fun setUpdateDaysPref(days: Set<Int>) {
        viewModelScope.launch {
            if (days.isEmpty()) {
                _error.emit(R.string.days_updated_error_message)
                return@launch
            }
            appPreferences.setUpdateDays(days)
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            broadcastUpdateWidget()
        }
    }

    fun setAutoSort(autoSort: Boolean) {
        viewModelScope.launch {
            val widgetData = widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)
            widgetData.setAutoSort(autoSort)
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            broadcastUpdateWidget()
        }
    }

    fun setRoundToTwoDp(round: Boolean) {
        viewModelScope.launch {
            appPreferences.setRoundToTwoDecimalPlaces(round)
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            broadcastUpdateWidget()
        }
    }

    fun sharePortfolio(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = TickersExporter.exportTickers(context, uri, stocksProvider.tickers.value)
            if (result == null) {
                context.showDialog(context.getString(R.string.error_sharing))
                Timber.w(Throwable("Error sharing tickers"))
            } else {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf<String>())
                intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.my_stock_portfolio))
                intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_email_subject))
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                val launchIntent = Intent.createChooser(intent, context.getString(R.string.action_share))
                launchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(launchIntent)
            }
        }
    }

    fun exportPortfolio(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = PortfolioExporter.exportQuotes(context, uri, stocksProvider.portfolio.value)
            if (result == null) {
                context.showDialog(context.getString(R.string.error_exporting))
                Timber.w(Throwable("Error exporting tickers"))
            } else {
                context.showDialog(context.getString(R.string.exported_to))
            }
        }
    }

    fun importPortfolio(context: Context, fileUri: Uri) {
        val type = context.contentResolver.getType(fileUri)
        val task: ImportTask = if ("text/plain" == type) {
            TickersImportTask(widgetDataProvider)
        } else {
            PortfolioImportTask(stocksProvider)
        }
        viewModelScope.launch {
            val imported = task.import(context, fileUri)
            if (imported) {
                context.showDialog(context.getString(R.string.ticker_import_success))
            } else {
                context.showDialog(context.getString(R.string.ticker_import_fail))
            }
        }
    }

    /**
     * Imports positions (symbol, shares, cost price) from [fileUri] and reports the outcome as a
     * localised message. Unlike [importPortfolio] this creates real holdings, which is what the
     * 今日盈亏 / 累计盈亏 / 当前市值 figures are computed from.
     */
    fun importPositions(context: Context, fileUri: Uri) {
        viewModelScope.launch {
            val count = PositionsImportTask(stocksProvider).import(context, fileUri)
            val message = when {
                count == null -> context.getString(R.string.positions_import_fail)
                count == 0 -> context.getString(R.string.positions_import_empty)
                else -> context.getString(R.string.positions_import_success, count)
            }
            context.showDialog(message)
        }
    }

    /**
     * Imports positions from a raw CSV [text] the user pasted into the import dialog. Parses both the
     * extended `symbol,market,name,quantity,cost_price[,currency,note]` format and the legacy
     * `symbol,shares,cost` form via [PositionImportParser]. Returns the number of rows that became real
     * holdings, or `null` when nothing could be parsed. The host surfaces the outcome.
     */
    suspend fun importPositionsFromText(text: String): Int? {
        val entries = PositionImportParser.parse(text)
        if (entries.isEmpty()) return null
        stocksProvider.addStocks(entries.map { it.symbol })
        var imported = 0
        for (entry in entries) {
            val shares = entry.shares
            val price = entry.price
            if (entry.hasPosition && shares != null && price != null) {
                stocksProvider.addHolding(entry.symbol, shares, price)
                imported++
            }
        }
        return imported
    }

    /**
     * Selects which mainland-China source A-share quotes are fetched from
     * ([UserPreferences.A_SHARE_SOURCE_TENCENT] or [UserPreferences.A_SHARE_SOURCE_EAST_MONEY]). The
     * change takes effect on the next refresh — the shared source router reads the preference per
     * call.
     */
    fun setAShareDataSource(sourcePref: Int) {
        viewModelScope.launch {
            appPreferences.aShareDataSourcePref = sourcePref
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            broadcastUpdateWidget()
        }
    }

    private fun buildData(widgetData: WidgetData): SettingsData {
        return SettingsData(
            hasWidgets = widgetDataProvider.hasWidget(),
            themePref = appPreferences.themePref,
            updateIntervalPref = appPreferences.updateIntervalPref,
            updateDays = appPreferences.updateDays(),
            startTime = appPreferences.startTime(),
            endTime = appPreferences.endTime(),
            autoSort = if (!widgetDataProvider.hasWidget()) widgetData.autoSortEnabled() else null,
            roundToTwoDp = appPreferences.roundToTwoDecimalPlaces(),
            aShareDataSourcePref = appPreferences.aShareDataSourcePref
        )
    }

    private suspend fun broadcastUpdateWidget() {
        widgetDataProvider.broadcastUpdateAllWidgets()
    }
}
