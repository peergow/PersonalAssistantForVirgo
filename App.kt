package com.disiplinai

import android.app.Application
import com.disiplinai.service.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
    }
}
