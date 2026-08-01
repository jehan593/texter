package com.texter.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.texter.app.TexterApplication
import com.texter.app.di.AppContainer

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember { (context.applicationContext as TexterApplication).container }
}
