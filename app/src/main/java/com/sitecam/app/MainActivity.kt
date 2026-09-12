package com.sitecam.app

import android.os.Bundle
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
