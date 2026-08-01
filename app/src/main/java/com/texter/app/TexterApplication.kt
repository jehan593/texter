package com.texter.app

import android.app.Application
import com.texter.app.di.AppContainer
import com.texter.app.di.DefaultAppContainer

class TexterApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
