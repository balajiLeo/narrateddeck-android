package com.narrateddeck.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.narrateddeck.android.ui.navigation.Routes
import com.narrateddeck.android.ui.screens.ExportScreen
import com.narrateddeck.android.ui.screens.ImportScreen
import com.narrateddeck.android.ui.screens.ReviewScreen
import com.narrateddeck.android.ui.theme.NarratedDeckTheme
import com.narrateddeck.android.viewmodel.DeckViewModel

class MainActivity : ComponentActivity() {

    private val deckViewModel: DeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NarratedDeckTheme {
                NarratedDeckApp(deckViewModel)
            }
        }
    }
}

@Composable
fun NarratedDeckApp(viewModel: DeckViewModel) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.IMPORT) {
        composable(Routes.IMPORT) {
            ImportScreen(
                state = state,
                onPptxPicked = { uri, name ->
                    viewModel.importPptx(uri, name)
                },
                onContinue = {
                    if (state.slides.isNotEmpty()) {
                        navController.navigate(Routes.REVIEW)
                    }
                },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onNotesChange = viewModel::updateNotes,
                onPreview = viewModel::previewSpeak,
                onStop = viewModel::stopPreview,
                onRegenerate = viewModel::regenerateAudio,
                onRegenerateAll = viewModel::regenerateAllMissing,
                onGoExport = { navController.navigate(Routes.EXPORT) },
            )
        }
        composable(Routes.EXPORT) {
            ExportScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onExport = viewModel::exportZip,
            )
        }
    }
}
