package com.examplecomposeapp

import android.app.Application
import com.adgeistkit.AdgeistCore

class AdgeistComposeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AdgeistCore.initialize(this)
    }
}
