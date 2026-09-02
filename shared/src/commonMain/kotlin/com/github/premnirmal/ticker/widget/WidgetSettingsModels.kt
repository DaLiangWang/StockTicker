package com.github.premnirmal.ticker.widget

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic view of a single widget's editable settings, rendered by the shared
 * [WidgetsScreen]. The Android `WidgetData` (backed by Glance/`SharedPreferences`) is adapted to this
 * interface by the `:app` host so the shared screen does not depend on the Android widget model.
 */
interface WidgetSettings {
    /** Reactive snapshot of the preference values rendered by the screen. */
    val prefs: StateFlow<WidgetPrefs>

    fun setWidgetName(value: String)
    fun setAutoSort(value: Boolean)

    @Deprecated("will be removed in future version")
    fun setFontSize(value: Int)
    fun setBgPref(value: Int)
    fun setTextColorPref(value: Int)
    fun setBoldEnabled(value: Boolean)
    fun setHideHeader(value: Boolean)
    fun setShowRefreshButton(value: Boolean)

    /** Shows/hides the portfolio's current market value (当前市值) in the widget summary row. */
    fun setShowMarketValue(value: Boolean)

    /** Shows/hides today's portfolio profit & loss (今日盈亏) in the widget summary row. */
    fun setShowTodayGainLoss(value: Boolean)

    /** Shows/hides the accumulated portfolio profit & loss (累计盈亏) in the widget summary row. */
    fun setShowTotalGainLoss(value: Boolean)
}

/**
 * The subset of widget preference values rendered by [WidgetsScreen]. The Android-only fields
 * (`@DrawableRes`/`@ColorRes` resources used to actually paint the widget) stay on the Android
 * `WidgetData.Prefs` and are not exposed here.
 */
data class WidgetPrefs(
    val name: String,
    val autoSort: Boolean,
    @Deprecated("will be removed in future version")
    val fontSizePref: Int,
    val backgroundPref: Int,
    val textColourPref: Int,
    val boldText: Boolean,
    val hideWidgetHeader: Boolean,
    val showRefreshButton: Boolean,
    val showMarketValue: Boolean,
    val showTodayGainLoss: Boolean,
    val showTotalGainLoss: Boolean,
)

/**
 * The localised labels and string-array options rendered by [WidgetsScreen]. They are resolved by
 * the `:app` host (via `stringResource`/`stringArrayResource`) and passed in so the shared screen has
 * no Android resource dependency.
 */
class WidgetSettingsStrings(
    val widgetName: String,
    val addStock: String,
    val trendingStocks: String,
    val background: String,
    val backgrounds: Array<String>,
    val textColor: String,
    val textColors: Array<String>,
    val boldChange: String,
    val boldChangeDesc: String,
    val hideHeader: String,
    val hideHeaderDesc: String,
    val showRefresh: String,
    val showRefreshDesc: String,
    val showMarketValue: String,
    val showMarketValueDesc: String,
    val showTodayGainLoss: String,
    val showTodayGainLossDesc: String,
    val showTotalGainLoss: String,
    val showTotalGainLossDesc: String,
)
