package com.texter.app.ui.documents

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.texter.app.data.db.entity.SavedDocumentEntity
import com.texter.app.R
import com.texter.app.ui.components.RenameDialog
import com.texter.app.ui.editor.EditorSource
import com.texter.app.ui.rememberAppContainer
import com.texter.app.util.formatLastEdited
import com.texter.app.text.findMatches
import com.texter.app.ui.theme.nord0
import com.texter.app.ui.theme.nord13

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDocumentsScreen(onNavigateToEditor: (EditorSource) -> Unit, onNavigateToSettings: () -> Unit) {
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
            viewModel.prepareOpen(uri) { source ->
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
        topBar = {
            TopAppBar(
                title = { Text("Texter", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { showNewFileDialog = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "New file")
                }
                FloatingActionButton(
                    onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_folder_open), contentDescription = "Open file")
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
                placeholder = { Text("Search files") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (viewModel.searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            if (documents.isEmpty()) {
                EmptyState(hasSearch = viewModel.searchText.isNotBlank())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 144.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(documents, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            searchQuery = viewModel.searchText,
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (hasSearch) Alignment.TopCenter else Alignment.Center
    ) {
        Text(
            text = if (hasSearch) "No matching files" else "No saved files yet.\nOpen a file or create one.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp).padding(top = if (hasSearch) 24.dp else 0.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentRow(
    document: SavedDocumentEntity,
    searchQuery: String,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onCopyContent: () -> Unit,
    onShare: () -> Unit,
    onSaveLocalStorage: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val highlightedName = remember(document.displayName, searchQuery) {
        buildAnnotatedString {
            append(document.displayName)
            if (searchQuery.isNotBlank()) {
                findMatches(document.displayName, searchQuery).forEach { match ->
                    addStyle(SpanStyle(background = nord13, color = nord0), match.start, match.end)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = highlightedName,
                style = MaterialTheme.typography.titleSmall,
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
                    text = { Text("Copy") },
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
                    text = { Text("Save a copy") },
                    onClick = {
                        menuExpanded = false
                        onSaveLocalStorage()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}
