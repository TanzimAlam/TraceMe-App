package com.traceme.app.location

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.traceme.app.CheckInActivity
import com.traceme.app.MainActivity
import com.traceme.app.R
import com.traceme.app.data.Repo
import com.traceme.app.data.UserSettings
import com.traceme.app.data.WatchState
import com.traceme.app.emergency.EmergencyDispatcher
import com.traceme.app.util.AlarmTone
import com.traceme.app.util.Notifier
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class WatchSnapshot(
    val running: Boolean = false,
    val state: WatchState = WatchState.IDLE,
    val hasFix: Boolean = false,
    val accuracy: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val stillForMillis: Long = 0L,
    val quietRemainingMillis: Long = 0L,
    val promptRemainingMillis: Long = 0L,
    val stuckAfterMillis: Long = 120_000L,
    val responseMillis: Long = 60_000L
)

/**
 * Foreground service that owns the night watch: GPS stream, motion sensors,
 * the "stopped moving" timer, the check-in prompt and the escape to SOS.
 */
class TrackingService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var repo: Repo
    private lateinit var sensorManager: SensorManager
    private val handler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var settings = UserSettings()
    private var running = false
    private var previousLocation: Location? = null
    private var motionEnergy = 0f
    private var lastNotificationAt = 0L

    override fun onCreate() {
        super.onCreate()
        repo = Repo.get(this)
        settings = repo.loadSettings()
        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settings = repo.loadSettings()
        startForegroundNow()

        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_SAFE -> resolveSafe()
            ACTION_QUIET -> startQuiet(
                intent?.getIntExtra(EXTRA_MINUTES, settings.quietMinutes) ?: settings.quietMinutes
            )
            ACTION_HELP -> escalate("You asked for help")
        }

        if (!running) beginTracking()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        handler.removeCallbacks(ticker)
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // ignore
        }
        sensorManager.unregisterListener(sensorListener)
        AlarmTone.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    // ---------- lifecycle of the watch ----------

    private fun beginTracking() {
        running = true
        isRunning = true
        startedAt = SystemClock.elapsedRealtime()
        lastMovementAt = startedAt
        state = WatchState.WATCHING
        acquireWakeLock()
        requestLocations()
        registerSensors()
        handler.post(ticker)
        repo.log("watch", "Night watch started")
        updateNotification()
    }

    private fun shutdown() {
        running = false
        isRunning = false
        state = WatchState.IDLE
        handler.removeCallbacks(ticker)
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // ignore
        }
        sensorManager.unregisterListener(sensorListener)
        AlarmTone.stop()
        releaseWakeLock()
        repo.log("watch", "Night watch ended")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestLocations() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            fused.lastLocation.addOnSuccessListener { location -> location?.let { onNewLocation(it) } }
        } catch (e: SecurityException) {
            repo.log("watch", "Location permission is missing")
        }
    }

    private fun acquireWakeLock() {
        try {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TraceMe::watch").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            wakeLock = null
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            // ignore
        }
        wakeLock = null
    }

    // ---------- inputs ----------

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onNewLocation(it) }
        }
    }

    private fun onNewLocation(location: Location) {
        latitude = location.latitude
        longitude = location.longitude
        accuracy = if (location.hasAccuracy()) location.accuracy else 0f
        hasFix = true

        val previous = previousLocation
        previousLocation = location
        if (previous != null) {
            val moved = previous.distanceTo(location)
            // Ignore GPS jitter: only count movement beyond the fix accuracy.
            val tolerance = maxOf(12f, min(accuracy, 60f))
            if (moved > tolerance) noteMovement()
        }
        if (location.hasSpeed() && location.speed > 1.2f) noteMovement()
        if (SystemClock.elapsedRealtime() - lastNotificationAt > 15_000L) updateNotification()
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> noteMovement()
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val magnitude = sqrt(x * x + y * y + z * z)
                    val deviation = abs(magnitude - SensorManager.GRAVITY_EARTH)
                    if (deviation > 1.8f) motionEnergy += deviation
                }
                Sensor.TYPE_GYROSCOPE -> {
                    val spin = abs(event.values[0]) + abs(event.values[1]) + abs(event.values[2])
                    if (spin > 0.9f) motionEnergy += spin
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun registerSensors() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyroscope != null) {
            sensorManager.registerListener(sensorListener, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        }
        val steps = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (steps != null) {
            sensorManager.registerListener(sensorListener, steps, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun noteMovement() {
        lastMovementAt = SystemClock.elapsedRealtime()
        motionEnergy = 0f
    }

    // ---------- the loop ----------

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            evaluate()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun evaluate() {
        val now = SystemClock.elapsedRealtime()

        if (motionEnergy > 14f) noteMovement()
        motionEnergy *= 0.55f
        if (motionEnergy < 0.01f) motionEnergy = 0f

        when (state) {
            WatchState.WATCHING -> {
                if (now - lastMovementAt >= settings.stuckSeconds * 1000L) promptUser()
            }
            WatchState.PROMPTING -> {
                if (now >= promptDeadline) {
                    AlarmTone.stop()
                    escalate("No answer to the safety check")
                }
            }
            WatchState.QUIET -> {
                if (now >= quietUntil) {
                    state = WatchState.WATCHING
                    lastMovementAt = now
                    repo.log("quiet", "Quiet window finished")
                    updateNotification()
                }
            }
            else -> Unit
        }

        if (now - lastNotificationAt > 15_000L) updateNotification()
    }

    private fun promptUser() {
        state = WatchState.PROMPTING
        promptDeadline = SystemClock.elapsedRealtime() + settings.responseSeconds * 1000L
        AlarmTone.start(this)
        repo.log(
            "check",
            "Asked if you're okay after ${settings.stuckSeconds / 60} min without movement"
        )
        updateNotification()

        val safe = servicePendingIntent(ACTION_SAFE, 11)
        val quiet = servicePendingIntent(ACTION_QUIET, 12, settings.quietMinutes)
        Notifier.showCheckIn(
            this,
            settings.responseSeconds,
            listOf(
                NotificationCompat.Action(0, "I'm safe", safe),
                NotificationCompat.Action(0, "Quiet ${settings.quietMinutes} min", quiet)
            )
        )

        try {
            startActivity(
                Intent(this, CheckInActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(CheckInActivity.EXTRA_WINDOW_MILLIS, settings.responseSeconds * 1000L)
            )
        } catch (e: Exception) {
            // Background activity start can be blocked; the notification still shows.
        }
    }

    fun resolveSafe() {
        AlarmTone.stop()
        state = WatchState.WATCHING
        lastMovementAt = SystemClock.elapsedRealtime()
        Notifier.cancel(this, Notifier.ID_CHECKIN)
        repo.log("check", "You confirmed you're safe")
        updateNotification()
    }

    fun startQuiet(minutes: Int) {
        AlarmTone.stop()
        state = WatchState.QUIET
        quietUntil = SystemClock.elapsedRealtime() + minutes * 60_000L
        lastMovementAt = SystemClock.elapsedRealtime()
        Notifier.cancel(this, Notifier.ID_CHECKIN)
        repo.log("quiet", "Checks paused for $minutes min")
        updateNotification()
    }

    private fun escalate(reason: String) {
        if (state == WatchState.SOS) return
        state = WatchState.SOS
        AlarmTone.stop()
        repo.log("sos", "Escalated · $reason")
        EmergencyDispatcher.dispatch(
            this,
            if (hasFix) latitude else null,
            if (hasFix) longitude else null,
            accuracy,
            reason
        )
        updateNotification()
    }

    // ---------- notification ----------

    private fun startForegroundNow() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, Notifier.ID_WATCH, buildNotification(), type)
        } catch (e: Exception) {
            repo.log("watch", "Could not start the foreground service: ${e.message}")
            stopSelf()
        }
    }

    private fun updateNotification() {
        lastNotificationAt = SystemClock.elapsedRealtime()
        try {
            val notification = buildNotification()
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(Notifier.ID_WATCH, notification)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when (state) {
            WatchState.WATCHING ->
                if (hasFix) "Watching · GPS ±${accuracy.toInt()} m" else "Watching · waiting for GPS fix"
            WatchState.QUIET -> "Checks paused · resumes at ${clockTime(quietUntil)}"
            WatchState.PROMPTING -> "Are you okay? Tap to answer"
            WatchState.SOS -> "SOS active · alerting your contacts"
            WatchState.IDLE -> "Idle"
        }

        return NotificationCompat.Builder(this, Notifier.CH_WATCH)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("TraceMe · night watch")
            .setContentText(text)
            .setOngoing(state != WatchState.IDLE)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "I'm safe", servicePendingIntent(ACTION_SAFE, 1))
            .addAction(
                0,
                "Quiet ${settings.quietMinutes}m",
                servicePendingIntent(ACTION_QUIET, 2, settings.quietMinutes)
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int, minutes: Int = 0): PendingIntent {
        val intent = Intent(this, TrackingService::class.java).setAction(action)
        if (minutes > 0) intent.putExtra(EXTRA_MINUTES, minutes)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clockTime(monotonicDeadline: Long): String {
        val wallClock = System.currentTimeMillis() + (monotonicDeadline - SystemClock.elapsedRealtime())
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = wallClock
        return String.format("%02d:%02d", calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))
    }

    companion object {
        const val ACTION_START = "com.traceme.app.action.START"
        const val ACTION_STOP = "com.traceme.app.action.STOP"
        const val ACTION_SAFE = "com.traceme.app.action.SAFE"
        const val ACTION_QUIET = "com.traceme.app.action.QUIET"
        const val ACTION_HELP = "com.traceme.app.action.HELP"
        const val EXTRA_MINUTES = "minutes"

        @Volatile
        var isRunning: Boolean = false
            internal set

        @Volatile
        var state: WatchState = WatchState.IDLE
            internal set

        @Volatile
        var latitude: Double = 0.0
            internal set

        @Volatile
        var longitude: Double = 0.0
            internal set

        @Volatile
        var accuracy: Float = 0f
            internal set

        @Volatile
        var hasFix: Boolean = false
            internal set

        private var startedAt: Long = 0L
        private var lastMovementAt: Long = 0L
        private var quietUntil: Long = 0L
        private var promptDeadline: Long = 0L

        fun start(context: Context) {
            val intent = Intent(context, TrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            if (!isRunning) return
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }

        fun confirmSafe(context: Context) {
            if (!isRunning) return
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_SAFE))
        }

        fun quiet(context: Context, minutes: Int) {
            if (!isRunning) return
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_QUIET)
                .putExtra(EXTRA_MINUTES, minutes)
            context.startService(intent)
        }

        fun helpNow(context: Context) {
            if (!isRunning) return
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_HELP))
        }

        fun snapshot(): WatchSnapshot {
            val now = SystemClock.elapsedRealtime()
            return WatchSnapshot(
                running = isRunning,
                state = state,
                hasFix = hasFix,
                accuracy = accuracy,
                latitude = latitude,
                longitude = longitude,
                stillForMillis = if (isRunning) (now - lastMovementAt).coerceAtLeast(0L) else 0L,
                quietRemainingMillis = (quietUntil - now).coerceAtLeast(0L),
                promptRemainingMillis = (promptDeadline - now).coerceAtLeast(0L)
            )
        }
    }
}
