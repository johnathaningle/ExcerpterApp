package com.johnathaningle.easynotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.johnathaningle.easynotes.navigation.AppNavigation
import com.johnathaningle.easynotes.ui.theme.EasyNotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyNotesTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
