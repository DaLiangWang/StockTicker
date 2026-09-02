package com.github.premnirmal.ticker.settings

import android.appwidget.AppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.model.AlarmScheduler
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
    private val alarmScheduler: AlarmScheduler,
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

    /**
     * Persists the in-app home auto-refresh interval. This no longer affects the widgets: their
     * background refresh is a separate fixed 15-minute toggle (see [setWidgetAutoRefresh]).
     */
    fun setUpdateIntervalPref(intervalPref: Int) {
        viewModelScope.launch {
            appPreferences.updateIntervalPref = intervalPref
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
            broadcastUpdateWidget()
        }
    }

    /**
     * Toggles the fixed 15-minute WorkManager background refresh for the home-screen widgets,
     * enqueuing or cancelling the periodic work immediately so the change takes effect at once.
     */
    fun setWidgetAutoRefresh(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setWidgetAutoRefresh(enabled)
            if (enabled) {
                alarmScheduler.enqueuePeriodicRefresh()
            } else {
                alarmScheduler.cancelPeriodicRefresh()
            }
            _settings.emit(buildData(widgetDataProvider.dataForWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)))
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
            aShareDataSourcePref = appPreferences.aShareDataSourcePref,
            widgetAutoRefresh = appPreferences.widgetAutoRefresh
        )
    }

    private suspend fun broadcastUpdateWidget() {
        widgetDataProvider.broadcastUpdateAllWidgets()
    }
}
