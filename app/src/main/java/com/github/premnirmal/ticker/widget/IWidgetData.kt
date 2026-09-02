package com.github.premnirmal.ticker.widget

import com.github.premnirmal.ticker.network.data.Quote
import kotlinx.coroutines.flow.StateFlow

interface IWidgetData {
    val widgetId: Int
    val widgetName: String

    val stocks: StateFlow<List<Quote>>
    val data: StateFlow<WidgetData.Data>

    val changeType: ChangeType

    val layoutType: LayoutType

    enum class ChangeType {
        Value,
        Percent,
    }

    /**
     * Only the holdings ("MyPortfolio") layout remains. The app now tracks positions exclusively, so
     * the animated / tabs / fixed quote layouts — and the preference that switched between them —
     * were removed; every widget renders the same holdings view.
     */
    enum class LayoutType {
        MyPortfolio;

        companion object {
            fun fromInt(value: Int): LayoutType = MyPortfolio
        }
    }

    enum class BackgroundType {
        System,
        Transparent,
        Translucent,
    }

    enum class TextColorType {
        System,
        Dark,
        Light,
    }
}
