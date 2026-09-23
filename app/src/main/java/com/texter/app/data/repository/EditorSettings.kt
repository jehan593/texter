package com.texter.app.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

class EditorSettings(context: Context) {
    private val preferences = context.getSharedPreferences("editor_settings", Context.MODE_PRIVATE)

    var showLineNumbers by mutableStateOf(preferences.getBoolean("show_line_numbers", true))
        private set

    private var fileOverrides by mutableStateOf(
        preferences.all.filterKeys { it.startsWith("file_") }.mapValues { it.value as Boolean }
    )

    fun updateShowLineNumbers(show: Boolean) {
        showLineNumbers = show
        preferences.edit().putBoolean("show_line_numbers", show).apply()
    }

    fun lineNumbersOverride(fileKey: String): Boolean? = fileOverrides[preferenceKey(fileKey)]

    fun setLineNumbersOverride(fileKey: String, show: Boolean?) {
        val key = preferenceKey(fileKey)
        val editor = preferences.edit()
        if (show == null) {
            fileOverrides = fileOverrides - key
            editor.remove(key)
        } else {
            fileOverrides = fileOverrides + (key to show)
            editor.putBoolean(key, show)
        }
        editor.apply()
    }

    private fun preferenceKey(fileKey: String): String = "file_" +
        MessageDigest.getInstance("SHA-256").digest(fileKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
