package com.traceme.app

import android.app.Application
import com.traceme.app.util.Notifier

class TraceMeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
