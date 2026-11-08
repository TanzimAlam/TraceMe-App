package com.traceme.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.traceme.app.data.Repo
import com.traceme.app.data.WatchState
import com.traceme.app.location.TrackingService
import com.traceme.app.ui.ActionButton
import com.traceme.app.ui.Cream
import com.traceme.app.ui.Danger
import com.traceme.app.ui.Ink
import com.traceme.app.ui.InkSoft
import com.traceme.app.ui.Pink
import com.traceme.app.ui.QuietButton
import com.traceme.app.ui.SafeGreen
import com.traceme.app.ui.StillnessGauge
import com.traceme.app.ui.TraceMeTheme
import kotlinx.coroutines.delay

/**
 * "Are you okay?" — shown full screen over the lock screen. If it is not
 * answered before the window closes, the service escalates to SOS.
 */
class CheckInActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val windowMillis = intent.getLongExtra(EXTRA_WINDOW_MILLIS, 60_000L)
        val settings = Repo.get(this).loadSettings()

        setContent {
            TraceMeTheme {
                var remaining by remember { mutableStateOf(windowMillis) }

                LaunchedEffect(Unit) {
                    val startedAt = System.currentTimeMillis()
                    while (true) {
                        val left = windowMillis - (System.currentTimeMillis() - startedAt)
                        remaining = left.coerceAtLeast(0L)
                        if (left <= 0L || TrackingService.state != WatchState.PROMPTING) {
                            finish()
                            break
                        }
                        delay(250)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Pink, Cream)))
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Spacer(Modifier.height(28.dp))

                    StillnessGauge(
                        progress = if (windowMillis > 0L) remaining.toFloat() / windowMillis.toFloat() else 0f,
                        centerTitle = "${(remaining / 1000).coerceAtLeast(0)}s",
                        centerSubtitle = "to answer",
                        progressColor = Danger
                    )

                    Text(
                        "Are you okay?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Your phone hasn't moved for ${settings.stuckSeconds / 60} minutes. " +
                            "If you don't answer, TraceMe texts your contacts and calls " +
                            "${settings.emergencyNumber}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft,
                        textAlign = TextAlign.Center
                    )

                    ActionButton(
                        text = "I'm safe",
                        icon = Icons.Rounded.CheckCircle,
                        container = SafeGreen
                    ) {
                        TrackingService.confirmSafe(this@CheckInActivity)
                        finish()
                    }

                    QuietButton(
                        text = "Pause checks for ${settings.quietMinutes} min",
                        icon = Icons.Rounded.PauseCircle
                    ) {
                        TrackingService.quiet(this@CheckInActivity, settings.quietMinutes)
                        finish()
                    }

                    ActionButton(
                        text = "I need help now",
                        icon = Icons.Rounded.Phone,
                        container = Danger
                    ) {
                        TrackingService.helpNow(this@CheckInActivity)
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_WINDOW_MILLIS = "windowMillis"
    }
}
