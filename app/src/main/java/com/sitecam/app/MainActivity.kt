package com.sitecam.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sitecam.app.core.layout.rememberScreenEnvironment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.sitecam.app.feature.navigation.AppNavHost
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.SiteCamTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.attributes = params
        }
        super.onCreate(savedInstanceState)

        val app = application as SiteCamApplication
        val appContainer = app.appContainer

        lifecycle.addObserver(appContainer.mediaAvailabilitySync)

        setContent {
            SiteCamTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()
                    DisposableEffect(navController) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        val bars = WindowInsetsCompat.Type.systemBars()
                        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                            if (destination.route == "camera") {
                                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.hide(bars)
                            } else {
                                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                                // Restore as navigation starts, before the destination enters.
                                controller.show(bars)
                            }
                        }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose { navController.removeOnDestinationChangedListener(listener) }
                    }
                    val environment = rememberScreenEnvironment()
                    val entry by navController.currentBackStackEntryAsState()
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(navController = navController, appContainer = appContainer)
                        if (environment.profile.smallCover && entry?.destination?.route != "camera") {
                            // Keep the current page composed underneath, including unsaved editor drafts.
                            Box(Modifier.fillMaxSize().background(DarkBackground).clickable { }.padding(12.dp), contentAlignment = Alignment.Center) {
                                Text("展开手机继续操作")
                            }
                        }
                    }
                }
            }
        }
    }
}
