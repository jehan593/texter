package com.texter.app.ui.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.texter.app.text.fileExtensionOf
import com.texter.app.text.highlightSearchMatches
import com.texter.app.text.highlightSyntax
import com.texter.app.ui.components.RenameDialog
import com.texter.app.ui.rememberAppContainer
import com.texter.app.ui.theme.EditorFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(source: EditorSource, navKey: String, onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: EditorViewModel = viewModel(
        key = navKey,
        factory = viewModelFactory {
            initializer {
                EditorViewModel(
                    source,
                    container.savedDocumentsRepository,
                    container.documentIoRepository,
                    container.shareFileRepository
                )
            }
        }
    )

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToMatchRequests.collect { match ->
            val layout = textLayoutResult ?: return@collect
            val line = layout.getLineForOffset(match.start)
            val top = layout.getLineTop(line).toInt().coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(top)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let(viewModel::saveToLocalStorage) }

    val documentInfo = viewModel.documentInfo

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Text(
                            text = (documentInfo?.displayName ?: "") + if (viewModel.isDirty) " •" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search in document")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Save in app") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.save()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuExpanded = false
                                    showRenameDialog = true
                                }
                            )
                            if (documentInfo != null && documentInfo.sourceWritable && documentInfo.sourceUri != null) {
                                DropdownMenuItem(
                                    text = { Text("Update original") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.updateOriginal()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Save to local storage") },
                                onClick = {
                                    menuExpanded = false
                                    createDocumentLauncher.launch(documentInfo?.displayName ?: "untitled.txt")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    menuExpanded = false
                                    val target = viewModel.prepareShareTarget()
                                    if (target != null) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = target.mimeType
                                            putExtra(Intent.EXTRA_STREAM, target.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                onClick = {
                                    menuExpanded = false
                                    clipboardManager.setText(AnnotatedString(viewModel.textFieldValue.text))
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Copied") }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.clearText()
                                }
                            )
                        }
                    }
                )
                if (viewModel.searchActive) {
                    SearchBar(viewModel = viewModel)
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        }
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            documentInfo == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("This document no longer exists")
                }
            }
            else -> {
                val fileExtension = remember(documentInfo.displayName) { fileExtensionOf(documentInfo.displayName) }
                val text = viewModel.textFieldValue.text
                val syntaxHighlighted = remember(text, fileExtension) { highlightSyntax(text, fileExtension) }
                val displayedText = remember(syntaxHighlighted, viewModel.matches, viewModel.currentMatchIndex) {
                    highlightSearchMatches(syntaxHighlighted, viewModel.matches, viewModel.currentMatchIndex)
                }

                BasicTextField(
                    value = viewModel.textFieldValue,
                    onValueChange = viewModel::onTextChanged,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    textStyle = TextStyle(
                        fontFamily = EditorFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = { TransformedText(displayedText, OffsetMapping.Identity) },
                    onTextLayout = { textLayoutResult = it }
                )
            }
        }
    }

    if (showRenameDialog && documentInfo != null) {
        RenameDialog(
            currentName = documentInfo.displayName,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                viewModel.rename(newName)
                showRenameDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(viewModel: EditorViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Search in document", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = if (viewModel.matches.isEmpty()) "0/0" else "${viewModel.currentMatchIndex + 1}/${viewModel.matches.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = viewModel::previousMatch) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = viewModel::nextMatch) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = viewModel::toggleSearch) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        }
    }
}
