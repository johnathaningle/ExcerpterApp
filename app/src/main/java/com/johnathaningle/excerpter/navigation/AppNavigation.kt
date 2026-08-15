package com.johnathaningle.excerpter.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.johnathaningle.excerpter.ui.home.HomeScreen
import com.johnathaningle.excerpter.ui.settings.SettingsScreen
import com.johnathaningle.excerpter.ui.viewer.MasterNoteScreen
import com.johnathaningle.excerpter.ui.viewer.ViewerScreen
import com.johnathaningle.excerpter.ui.viewer.ViewerViewModel

object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer"
    const val SETTINGS = "settings"
    const val MASTER_NOTE = "master_note"
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
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.VIEWER) {
            val pdfUri = PdfHolder.currentUri ?: return@composable
            ViewerScreen(
                pdfUri = pdfUri,
                onBack = {
                    PdfHolder.currentUri = null
                    navController.popBackStack()
                },
                onOpenMasterNote = { navController.navigate(Routes.MASTER_NOTE) }
            )
        }

        composable(Routes.MASTER_NOTE) {
            // Share the viewer's ViewModel (which holds the PDF + master note state)
            // rather than creating a fresh one scoped to this destination.
            val viewerEntry = navController.getBackStackEntry(Routes.VIEWER)
            val viewModel: ViewerViewModel = viewModel(viewModelStoreOwner = viewerEntry)
            MasterNoteScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
