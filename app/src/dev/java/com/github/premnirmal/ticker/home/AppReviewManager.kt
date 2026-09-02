package com.github.premnirmal.ticker.home

import android.app.Activity
import android.content.Context

class AppReviewManager(context: Context) : IAppReviewManager {
    override fun launchReviewFlow(activity: Activity) {
        // No-op on dev builds: the real Play review flow only runs on prod.
    }
}