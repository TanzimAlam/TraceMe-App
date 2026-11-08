package com.traceme.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

/** Looping alarm tone so a check-in is noticed even with the screen off. */
object AlarmTone {

    private var player: MediaPlayer? = null

    fun start(context: Context) {
        stop()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            player = MediaPlayer().apply {
                setDataSource(context.applicationContext, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            player = null
        }
    }

    fun stop() {
        val current = player ?: return
        try {
            if (current.isPlaying) current.stop()
        } catch (e: Exception) {
            // already stopped
        }
        try {
            current.release()
        } catch (e: Exception) {
            // ignore
        }
        player = null
    }
}
