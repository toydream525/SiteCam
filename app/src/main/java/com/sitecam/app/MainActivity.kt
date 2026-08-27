package com.sitecam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

        setContent {
            SiteCamTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        appContainer = appContainer
                    )
                }
            }
        }
    }
}
