package com.traceme.app.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The ratchet under the sliders: every step crossed feels like a small physical
 * notch — a short haptic tap plus a dry click.
 *
 * Deliberately not `View.performHapticFeedback` / `AudioManager.playSoundEffect`
 * only: those are silently skipped when the phone has "touch sounds" or
 * "touch vibration" switched off, which is exactly why the sliders felt dead.
 * Here the vibration goes straight to the [Vibrator] and the click comes from a
 * [ToneGenerator], so it works whatever the system sound settings say.
 */
class SliderTicker(context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator?.takeIf { it.hasVibrator() }
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                ?.takeIf { it.hasVibrator() }
        }
    } catch (e: Exception) {
        null
    }

    private val audio: AudioManager? = try {
        context.getSystemService(AudioManager::class.java)
    } catch (e: Exception) {
        null
    }

    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 60)
    } catch (e: Exception) {
        null
    }

    /** One notch per step crossed, spaced so a fast drag still sounds like a ratchet. */
    fun tick(steps: Int = 1) {
        val count = steps.coerceIn(1, 6)
        for (index in 0 until count) {
            if (index == 0) {
                emit()
            } else {
                handler.postDelayed({ emit() }, 26L * index)
            }
        }
    }

    private fun emit() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(18L, 140))
        } catch (e: Exception) {
            // no vibrator, ignore
        }
        try {
            tone?.startTone(ToneGenerator.TONE_CDMA_PIP, 22)
        } catch (e: Exception) {
            // tone generator busy or unavailable
        }
        try {
            audio?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.55f)
        } catch (e: Exception) {
            // system effects disabled, the tone already covered it
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        try {
            tone?.stopTone()
            tone?.release()
        } catch (e: Exception) {
            // ignore
        }
    }
}

@Composable
fun rememberSliderTicker(): SliderTicker {
    val context = LocalContext.current
    val ticker = remember(context) { SliderTicker(context) }
    DisposableEffect(ticker) {
        onDispose { ticker.release() }
    }
    return ticker
}
