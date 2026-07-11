package com.johnathaningle.excerpter.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.johnathaningle.excerpter.ui.home.HomeScreen
import com.johnathaningle.excerpter.ui.viewer.ViewerScreen

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer"
}

object PdfHolder {
    var currentUri: String? = null
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = Routes.HOME
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onPdfSelected = { uri ->
                    PdfHolder.currentUri = uri
                    navController.navigate(Routes.VIEWER)
                }
            )
        }

        composable(Routes.VIEWER) {
            val pdfUri = PdfHolder.currentUri ?: return@composable
            ViewerScreen(
                pdfUri = pdfUri,
                onBack = {
                    PdfHolder.currentUri = null
                    navController.popBackStack()
                }
            )
        }
    }
}
