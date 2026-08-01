package com.texter.app.ui.editor

/** What to load into the editor — deliberately distinct from "is this in the saved list", since
 *  opening or creating a file must NOT touch the saved list until the user explicitly taps
 *  "Save in app" (see EditorViewModel.save). */
sealed interface EditorSource {
    data class Saved(val documentId: Long) : EditorSource

    data class Opened(
        val displayName: String,
        val content: String,
        val sourceUri: String?,
        val sourceWritable: Boolean
    ) : EditorSource

    data class New(val displayName: String) : EditorSource
}
