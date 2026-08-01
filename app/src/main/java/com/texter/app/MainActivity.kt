package com.texter.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import java.util.UUID

private sealed interface Screen {
    data object Documents : Screen
    data class Editor(val source: EditorSource, val navKey: String = UUID.randomUUID().toString()) : Screen
}

class MainActivity : ComponentActivity() {

    // A Compose State object (not `by remember`) so writes from onNewIntent — which runs outside
    // any composition — still trigger recomposition of whatever reads .value inside setContent.
    private val pendingImportUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingImportUri.value = extractOpenableUri(intent)

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

                val importUri = pendingImportUri.value
                LaunchedEffect(importUri) {
                    if (importUri != null) {
                        // Not our own picker, so don't assume write access — prepareOpen only
                        // marks the source writable if a persistable write grant actually
                        // succeeds for this URI. Never touches the saved list either way.
                        documentsViewModel.prepareOpen(importUri, requestWritePermission = false) { source ->
                            screen = Screen.Editor(source)
                        }
                        pendingImportUri.value = null
                    }
                }

                when (val current = screen) {
                    Screen.Documents -> SavedDocumentsScreen(
                        onNavigateToEditor = { source -> screen = Screen.Editor(source) }
                    )
                    is Screen.Editor -> EditorScreen(
                        source = current.source,
                        navKey = current.navKey,
                        onBack = { screen = Screen.Documents }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingImportUri.value = extractOpenableUri(intent)
    }

    private fun extractOpenableUri(intent: Intent?): Uri? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
    }
}
