package com.texter.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.texter.app.ui.documents.SavedDocumentsScreen
import com.texter.app.ui.documents.SavedDocumentsViewModel
import com.texter.app.ui.editor.EditorScreen
import com.texter.app.ui.editor.EditorSource
import com.texter.app.ui.rememberAppContainer
import com.texter.app.ui.theme.TexterTheme
import com.texter.app.ui.settings.SettingsScreen
import java.util.UUID

private sealed interface Screen {
    data object Documents : Screen
    data object Settings : Screen
    data class Editor(
        val source: EditorSource,
        val navKey: String = UUID.randomUUID().toString(),
        val returnToCaller: Boolean = false
    ) : Screen
}

private data class IncomingFile(val uri: Uri, val returnToCaller: Boolean)

private const val NAV_TRANSITION_MILLIS = 300

class MainActivity : ComponentActivity() {

    // A Compose State object (not `by remember`) so writes from onNewIntent — which runs outside
    // any composition — still trigger recomposition of whatever reads .value inside setContent.
    private val pendingImport = mutableStateOf<IncomingFile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingImport.value = extractIncomingFile(intent)

        setContent {
            TexterTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Documents) }
                val container = rememberAppContainer()
                val documentsViewModel: SavedDocumentsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            SavedDocumentsViewModel(
                                container.savedDocumentsRepository,
                                container.documentIoRepository,
                                container.shareFileRepository
                            )
                        }
                    }
                )

                val incomingFile = pendingImport.value
                LaunchedEffect(incomingFile) {
                    if (incomingFile != null) {
                        // Inspect the URI grant, including temporary write access from the sender.
                        documentsViewModel.prepareOpen(incomingFile.uri) { source ->
                            screen = Screen.Editor(source, returnToCaller = incomingFile.returnToCaller)
                        }
                        pendingImport.value = null
                    }
                }

                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    transitionSpec = {
                        val direction = if (targetState == Screen.Documents) {
                            AnimatedContentTransitionScope.SlideDirection.End
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Start
                        }
                        (slideIntoContainer(direction, tween(NAV_TRANSITION_MILLIS)) +
                            fadeIn(tween(NAV_TRANSITION_MILLIS))) togetherWith
                            (slideOutOfContainer(direction, tween(NAV_TRANSITION_MILLIS)) +
                                fadeOut(tween(NAV_TRANSITION_MILLIS)))
                    },
                    label = "Screen transition"
                ) { current ->
                    when (current) {
                        Screen.Documents -> SavedDocumentsScreen(
                            onNavigateToEditor = { source -> screen = Screen.Editor(source) },
                            onNavigateToSettings = { screen = Screen.Settings }
                        )
                        Screen.Settings -> SettingsScreen(
                            onBack = { screen = Screen.Documents },
                            isActive = screen == current
                        )
                        is Screen.Editor -> EditorScreen(
                            source = current.source,
                            navKey = current.navKey,
                            onBack = {
                                // EditorScreen resolves unsaved changes before invoking this.
                                if (current.returnToCaller) finish() else screen = Screen.Documents
                            },
                            isActive = screen == current
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingImport.value = extractIncomingFile(intent)
    }

    private fun extractIncomingFile(intent: Intent?): IncomingFile? {
        intent ?: return null
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return null
        return IncomingFile(uri, returnToCaller = intent.action == Intent.ACTION_VIEW)
    }
}
