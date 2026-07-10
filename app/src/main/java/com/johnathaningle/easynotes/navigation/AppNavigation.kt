package com.johnathaningle.easynotes.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.johnathaningle.easynotes.ui.home.HomeScreen
import com.johnathaningle.easynotes.ui.viewer.ViewerScreen
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer/{pdfUri}"

    fun viewer(pdfUri: String): String {
        val encoded = URLEncoder.encode(pdfUri, "UTF-8")
        return "viewer/$encoded"
    }
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
                    navController.navigate(Routes.viewer(uri))
                }
            )
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(
                navArgument("pdfUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val pdfUri = backStackEntry.arguments?.getString("pdfUri") ?: return@composable
            val decodedUri = java.net.URLDecoder.decode(pdfUri, "UTF-8")
            ViewerScreen(
                pdfUri = decodedUri,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
