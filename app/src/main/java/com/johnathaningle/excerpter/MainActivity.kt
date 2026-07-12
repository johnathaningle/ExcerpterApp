package com.johnathaningle.excerpter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.johnathaningle.excerpter.navigation.AppNavigation
import com.johnathaningle.excerpter.ui.theme.ExcerpterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExcerpterTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
