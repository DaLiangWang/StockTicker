package com.github.premnirmal.ticker.settings

import com.github.premnirmal.ticker.Time

/**
 * Snapshot of the user's settings, used as immutable state by the shared [SettingsScreen].
 * Parceling was removed because this is only held in-memory by the ViewModel's `StateFlow`.
 *
 * [aShareDataSourcePref] selects which mainland-China source A-share quotes come from
 * ([com.github.premnirmal.ticker.UserPreferences.A_SHARE_SOURCE_TENCENT] or
 * [com.github.premnirmal.ticker.UserPreferences.A_SHARE_SOURCE_EAST_MONEY]).
 */
data class SettingsData(
    val hasWidgets: Boolean,
    val themePref: Int,
    val updateIntervalPref: Int,
    val updateDays: Set<Int>,
    val startTime: Time,
    val endTime: Time,
    val autoSort: Boolean?,
    val roundToTwoDp: Boolean,
    val aShareDataSourcePref: Int = 0,
)
