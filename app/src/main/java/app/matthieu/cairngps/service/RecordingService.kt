package app.matthieu.cairngps.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.matthieu.cairngps.CairnApplication
import app.matthieu.cairngps.MainActivity
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.RecordingState
import app.matthieu.cairngps.domain.format.formatAltitude
import app.matthieu.cairngps.domain.format.formatDistanceKm
import app.matthieu.cairngps.domain.format.formatDuration
import app.matthieu.cairngps.domain.format.formatSpeedKmh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a recording alive (and visible via an ongoing notification) while
 * the app is backgrounded — the deliberate exception to the rest of the app's ON_START/ON_STOP GPS
 * lifecycle. This is now the single entry point for starting/stopping a recording: the recording
 * card (via [RecordingService.start]/[RecordingService.stop]) and the notification's own "Arrêter"
 * action both go through it, so [RecordingRepository] always has one caller driving its lifecycle.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var isForeground = false

    private val recordingRepository: RecordingRepository by lazy {
        (application as CairnApplication).recordingRepository
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            // Sticky restart after the process was killed: resume the notification only if a
            // recording is still tracked as active, otherwise there is nothing to show.
            null -> if (recordingRepository.isRecording) handleStart() else stopSelf()
        }
        return START_STICKY
    }

    private fun handleStart() {
        if (!hasLocationPermission()) {
            // Should not happen: the UI only calls start() from behind LocationPermissionGate.
            stopSelf()
            return
        }
        recordingRepository.start()
        createNotificationChannel()
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification(recordingRepository.state.value, elapsedMs = 0L))
            isForeground = true
        }
        if (tickerJob == null) {
            tickerJob = scope.launch {
                // Loop is bounded by isRecording rather than `while (true)`, so a recording that
                // stops through a path other than handleStop() (e.g. the repository itself
                // clearing state) still tears the notification down instead of freezing on a
                // stale "0s" readout.
                while (recordingRepository.state.value.isRecording) {
                    val state = recordingRepository.state.value
                    val elapsed = (System.currentTimeMillis() - state.startTimestamp).coerceAtLeast(0)
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state, elapsed))
                    delay(1_000L)
                }
                tickerJob = null
                if (isForeground) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForeground = false
                    stopSelf()
                }
            }
        }
    }

    private fun handleStop() {
        scope.launch {
            recordingRepository.stop(getString(R.string.session_default_name_prefix))
            tickerJob?.cancel()
            tickerJob = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: RecordingState, elapsedMs: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val title = getString(R.string.recording_notification_title_fmt, formatDuration(elapsedMs))
        val content = getString(
            R.string.recording_notification_content,
            formatAltitude(state.currentAltitude),
            formatSpeedKmh(state.currentSpeed),
            formatDistanceKm(state.distanceMeters),
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.recording_stop), stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "app.matthieu.cairngps.action.START_RECORDING"
        private const val ACTION_STOP = "app.matthieu.cairngps.action.STOP_RECORDING"

        /** Starts (or resumes) the recording foreground service. Requires location permission. */
        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops the current recording and tears down the foreground service. */
        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
