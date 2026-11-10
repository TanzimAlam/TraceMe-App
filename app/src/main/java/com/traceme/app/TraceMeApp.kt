package com.traceme.app

import android.app.Application
import com.traceme.app.util.Notifier
import org.osmdroid.config.Configuration
import java.io.File

class TraceMeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)

        // Keep OpenStreetMap tiles in app cache: no storage permission needed.
        val config = Configuration.getInstance()
        config.userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
        val root = File(cacheDir, "osmdroid")
        root.mkdirs()
        config.osmdroidBasePath = root
        config.osmdroidTileCache = File(root, "tiles")
    }
}
