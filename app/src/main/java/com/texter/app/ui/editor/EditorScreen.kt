package com.texter.app.ui.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlin.math.ceil

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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val editorPaddingPx = with(LocalDensity.current) { 12.dp.toPx() }
    var editorFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var readerMode by rememberSaveable(navKey) { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (!viewModel.isSaving) {
            if (viewModel.hasUnsavedChanges) showLeaveDialog = true else onBack()
        }
    }

    BackHandler(onBack = requestBack)

    LaunchedEffect(
        scrollState.viewportSize,
        viewModel.textFieldValue.selection,
        textLayoutResult,
        editorFocused,
        readerMode
    ) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val viewportHeight = scrollState.viewportSize
        if (!editorFocused || readerMode || viewportHeight <= 0) return@LaunchedEffect
        val cursor = layout.getCursorRect(
            viewModel.textFieldValue.selection.end.coerceIn(0, layout.layoutInput.text.length)
        )
        val visibleTop = scrollState.value.toFloat()
        val cursorTop = cursor.top + editorPaddingPx
        val cursorBottom = cursor.bottom + editorPaddingPx
        val target = when {
            cursorBottom + editorPaddingPx > visibleTop + viewportHeight ->
                ceil(cursorBottom + editorPaddingPx - viewportHeight).toInt()
            cursorTop - editorPaddingPx < visibleTop ->
                (cursorTop - editorPaddingPx).toInt()
            else -> return@LaunchedEffect
        }
        scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue))
    }

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
        // "application/octet-stream" rather than "text/plain": the latter has ".txt" as its
        // registered extension, and Android's SAF picker force-appends the registered extension
        // for the launcher's MIME type onto whatever filename is passed in (so "test.py" would
        // become "test.py.txt"). octet-stream has no such mapping, so the typed extension survives.
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(viewModel::saveToLocalStorage) }

    val documentInfo = viewModel.documentInfo

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = requestBack, enabled = !viewModel.isSaving) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Text(
                            text = (documentInfo?.displayName ?: "") + if (viewModel.isDirty) " •" else "",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        Icon(
                            imageVector = if (readerMode) Icons.Default.Lock else Icons.Default.Edit,
                            contentDescription = if (readerMode) "Reader mode" else "Editing mode",
                            modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search in document")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Reader mode") },
                                trailingIcon = { Switch(checked = readerMode, onCheckedChange = null) },
                                onClick = {
                                    readerMode = !readerMode
                                    if (readerMode) {
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                    }
                                }
                            )
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
                                text = { Text("Clear text") },
                                enabled = !readerMode && viewModel.textFieldValue.text.isNotEmpty(),
                                onClick = {
                                    menuExpanded = false
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    viewModel.clearText()
                                    coroutineScope.launch {
                                        scrollState.scrollTo(0)
                                        snackbarHostState.showSnackbar("Text cleared")
                                    }
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

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                ) {
                    val viewportHeight = maxHeight
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        BasicTextField(
                            value = viewModel.textFieldValue,
                            onValueChange = viewModel::onTextChanged,
                            readOnly = readerMode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = viewportHeight)
                                .padding(12.dp)
                                .onFocusChanged { editorFocused = it.isFocused },
                            textStyle = TextStyle(
                                fontFamily = EditorFontFamily,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = { TransformedText(displayedText, OffsetMapping.Identity) },
                            onTextLayout = { textLayoutResult = it }
                        )
                    }
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!viewModel.isSaving) showLeaveDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text("Keep changes?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Keep saves this file in the app. Discard loses unsaved changes.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.save {
                            showLeaveDialog = false
                            if (!viewModel.hasUnsavedChanges) onBack()
                        }
                    },
                    enabled = !viewModel.isSaving,
                    shape = CircleShape
                ) { Text(if (viewModel.isSaving) "Saving…" else "Keep") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { showLeaveDialog = false },
                        enabled = !viewModel.isSaving
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            showLeaveDialog = false
                            onBack()
                        },
                        enabled = !viewModel.isSaving,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Discard") }
                }
            }
        )
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
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = {
                Text("Find in file", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (viewModel.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
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
