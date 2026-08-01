package com.texter.app.ui.documents

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.texter.app.data.db.entity.SavedDocumentEntity
import com.texter.app.ui.components.RenameDialog
import com.texter.app.ui.editor.EditorSource
import com.texter.app.ui.rememberAppContainer
import com.texter.app.util.formatLastEdited

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDocumentsScreen(onNavigateToEditor: (EditorSource) -> Unit) {
    val container = rememberAppContainer()
    val viewModel: SavedDocumentsViewModel = viewModel(
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
    val documents by viewModel.documents.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renamingDocument by remember { mutableStateOf<SavedDocumentEntity?>(null) }
    var pendingSaveLocalDocument by remember { mutableStateOf<SavedDocumentEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    LaunchedEffect(Unit) {
        viewModel.contentToCopy.collect { content ->
            clipboardManager.setText(AnnotatedString(content))
            snackbarHostState.showSnackbar("Copied")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { target ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = target.mimeType
                putExtra(Intent.EXTRA_STREAM, target.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.prepareOpen(uri, requestWritePermission = true) { source ->
                onNavigateToEditor(source)
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        // See EditorScreen's identical launcher for why octet-stream, not text/plain.
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val document = pendingSaveLocalDocument
        if (uri != null && document != null) {
            viewModel.saveToLocalStorage(document, uri)
        }
        pendingSaveLocalDocument = null
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Texter") }) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { showNewFileDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Create a new file")
                }
                FloatingActionButton(onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = "Open a file")
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = viewModel.searchText,
                onValueChange = viewModel::onSearchChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search saved documents") },
                singleLine = true
            )

            if (documents.isEmpty()) {
                EmptyState(hasSearch = viewModel.searchText.isNotBlank())
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(documents, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            onClick = { onNavigateToEditor(EditorSource.Saved(document.id)) },
                            onRename = { renamingDocument = document },
                            onCopyContent = { viewModel.requestCopyContent(document) },
                            onShare = { viewModel.requestShare(document) },
                            onSaveLocalStorage = {
                                pendingSaveLocalDocument = document
                                createDocumentLauncher.launch(document.displayName)
                            },
                            onDelete = { viewModel.delete(document) }
                        )
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { fileName ->
                showNewFileDialog = false
                onNavigateToEditor(EditorSource.New(fileName))
            }
        )
    }

    renamingDocument?.let { document ->
        RenameDialog(
            currentName = document.displayName,
            onDismiss = { renamingDocument = null },
            onRename = { newName ->
                viewModel.rename(document, newName)
                renamingDocument = null
            }
        )
    }
}

@Composable
private fun EmptyState(hasSearch: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (hasSearch) "No documents match your search" else "No documents yet — tap + to open a file",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentRow(
    document: SavedDocumentEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onCopyContent: () -> Unit,
    onShare: () -> Unit,
    onSaveLocalStorage: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatLastEdited(document.lastEditedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options for ${document.displayName}")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy content") },
                    onClick = {
                        menuExpanded = false
                        onCopyContent()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Save to local storage") },
                    onClick = {
                        menuExpanded = false
                        onSaveLocalStorage()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}
