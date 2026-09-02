package com.github.premnirmal.ticker.home

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.premnirmal.ticker.components.AppNumberFormat
import com.github.premnirmal.ticker.components.DecimalFormatter
import com.github.premnirmal.ticker.model.AlarmScheduler
import com.github.premnirmal.ticker.model.FetchState
import com.github.premnirmal.ticker.model.PortfolioSummary
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.network.TrendingProvider
import com.github.premnirmal.ticker.widget.WidgetData
import com.github.premnirmal.ticker.widget.WidgetDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class HomeViewModel constructor(
    application: Application,
    private val stocksProvider: StocksProvider,
    private val trendingProvider: TrendingProvider,
    private val widgetDataProvider: WidgetDataProvider,
    private val alarmScheduler: AlarmScheduler,
) : AndroidViewModel(application) {

    val fetchState: StateFlow<FetchState>
        get() = stocksProvider.fetchState

    val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing
    private val _isRefreshing = MutableStateFlow(false)

    val homeEvent: Flow<HomeEvent>
        get() = _homeEvent
    private val _homeEvent = MutableSharedFlow<HomeEvent>()

    val widgets: StateFlow<List<WidgetData>>
        get() = widgetDataProvider.widgetData
    val hasWidget: Flow<Boolean>
        get() = widgetDataProvider.hasWidget

    val hasHoldings: Boolean
        get() = stocksProvider.hasPositions()

    val showAlarmPermissionRequest: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmScheduler.canScheduleExactAlarm()

    private var fetchJob: Job? = null

    init {
        initCaches()
        viewModelScope.launch { widgetDataProvider.refreshWidgetDataList() }
    }

    private fun initCaches() {
        trendingProvider.initCache()
    }

    fun sendHomeEvent(event: HomeEvent) {
        viewModelScope.launch {
            _homeEvent.emit(event)
        }
    }

    /**
     * The portfolio's headline P&L numbers — current market value (当前市值), today's P&L (今日盈亏) and
     * the accumulated P&L (累计盈亏) — aggregated with the shared [PortfolioSummary] from the imported
     * positions and formatted with the platform `NumberFormat` so the values stay locale-aware.
     */
    val totalGainLoss: Flow<TotalGainLoss>
        get() = stocksProvider.portfolio.map { portfolio ->
            val summary = PortfolioSummary.from(portfolio)
            val format = AppNumberFormat.selected
            var totalGain = 0.0
            var totalLoss = 0.0
            for (quote in portfolio.filter { it.hasPositions() }) {
                val gainLoss = quote.gainLoss().toDouble()
                if (gainLoss > 0.0) {
                    totalGain += gainLoss
                } else {
                    totalLoss += gainLoss
                }
            }
            TotalGainLoss(
                holdings = format.format(summary.marketValue),
                gain = "+" + format.format(totalGain.toFloat()),
                loss = if (totalLoss != 0.0) format.format(totalLoss.toFloat()) else "",
                todayGainLoss = formatSigned(format, summary.todayGainLoss),
                todayGainLossPercent = formatSignedPercent(summary.todayGainLossPercent),
                totalGainLossPercent = formatSignedPercent(summary.totalGainLossPercent),
            )
        }

    private fun formatSigned(format: DecimalFormatter, value: Float): String {
        val formatted = format.format(value)
        return if (value >= 0f) "+$formatted" else formatted
    }

    private fun formatSignedPercent(value: Float): String =
        "${if (value >= 0f) "+" else ""}${String.format(Locale.getDefault(), "%.2f", value)}%"

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                stocksProvider.fetch()
                // Re-sync each widget's in-memory quote list from StocksProvider after the fetch so
                // that newly added symbols (e.g. an import) and freshly fetched quotes reach the home
                // screen even when the fetch produced no broadcast (empty/failed response).
                widgetDataProvider.refreshWidgetDataList()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun fetchPortfolioInRealTime() {
        fetchJob = viewModelScope.launch(Dispatchers.Default) {
            do {
                var isMarketOpen = false
                val result = stocksProvider.fetch(false)
                if (result.wasSuccessful) {
                    isMarketOpen = result.data.any { it.isMarketOpen }
                }
                delay(StocksProvider.DEFAULT_INTERVAL_MS)
            } while (result.wasSuccessful && isMarketOpen)
        }
    }

    fun stopRealTimeFetch() {
        fetchJob?.cancel()
    }
}
