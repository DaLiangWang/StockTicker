package com.github.premnirmal.ticker.home

import android.os.Bundle
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.compose.rememberNavController
import com.github.premnirmal.ticker.base.BaseActivity
import com.github.premnirmal.ticker.navigation.Graph
import com.github.premnirmal.ticker.navigation.RootNavigationGraphHost
import com.google.accompanist.adaptive.calculateDisplayFeatures
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeActivity : BaseActivity() {
    override val simpleName = "HomeActivity"

    private val appReviewManager: IAppReviewManager by inject()

    private val viewModel: HomeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            stocksProvider.schedule()
        }
    }

    @Composable
    override fun ShowContent() {
        HomeScreen()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    private fun HomeScreen() {
        val windowSizeClass = calculateWindowSizeClass(this)
        val navHostController = rememberNavController()
        DisposableEffect(navHostController) {
            val listener = NavController.OnDestinationChangedListener { _: NavController, destination: NavDestination, _: Bundle? ->
                if (destination.route == Graph.QUOTE_DETAIL) {
                    viewModel.sendHomeEvent(HomeEvent.PromptRate)
                }
            }
            navHostController.addOnDestinationChangedListener(listener)
            onDispose {
                navHostController.removeOnDestinationChangedListener(listener)
            }
        }
        var rateDialogShown by rememberSaveable {
            mutableStateOf(false)
        }
        RootNavigationGraphHost(
            windowWidthSizeClass = windowSizeClass.widthSizeClass,
            windowHeightSizeClass = windowSizeClass.heightSizeClass,
            displayFeatures = calculateDisplayFeatures(this),
            navHostController = navHostController
        )
        LaunchedEffect(Unit) {
            intent.getStringExtra(EXTRA_SYMBOL)?.let {
                navHostController.navigate(route = "${Graph.QUOTE_DETAIL}/$it")
            }
        }
        LaunchedEffect(Unit) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.homeEvent.collect { event ->
                    when (event) {
                        is HomeEvent.PromptRate -> {
                            if (!rateDialogShown && appPreferences.shouldPromptRate()) {
                                appReviewManager.launchReviewFlow(this@HomeActivity)
                                rateDialogShown = true
                            }
                        }
                    }
                }
            }
        }
        DisposableEffect(Unit) {
            viewModel.fetchPortfolioInRealTime()
            onDispose {
                viewModel.stopRealTimeFetch()
            }
        }
    }

    companion object {
        const val EXTRA_SYMBOL: String = "EXTRA_SYMBOL"
    }
}
