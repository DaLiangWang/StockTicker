package com.github.premnirmal.ticker.settings

import android.appwidget.AppWidgetManager
import android.content.Context
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

    /**
     * Imports positions (symbol, shares, cost price) from [fileUri] and reports the outcome as a
     * localised message, creating real holdings — which is what the 今日盈亏 / 累计盈亏 / 当前市值
     * figures are computed from.
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
                stocksProvider.setHolding(entry.symbol, shares, price)
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
