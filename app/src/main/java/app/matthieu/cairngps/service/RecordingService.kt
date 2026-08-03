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
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.StopResult
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.domain.format.distanceUnitLabel
import app.matthieu.cairngps.domain.format.formatAccuracy
import app.matthieu.cairngps.domain.format.formatAltitude
import app.matthieu.cairngps.domain.format.formatDistance
import app.matthieu.cairngps.domain.format.formatDuration
import app.matthieu.cairngps.domain.format.formatSpeed
import app.matthieu.cairngps.domain.format.shortUnitLabel
import app.matthieu.cairngps.domain.format.speedUnitLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
    private val settingsRepository: SettingsRepository by lazy {
        (application as CairnApplication).settingsRepository
    }

    // Kept in sync from settingsRepository so buildNotification (called synchronously from the
    // ticker loop) doesn't need a suspend read on every tick.
    private var unitSystem: UnitSystem = UnitSystem.METRIC

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            settingsRepository.settings.collect { unitSystem = it.unitSystem }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop(fromNotification = false)
            ACTION_STOP_FROM_NOTIFICATION -> handleStop(fromNotification = true)
            // Sticky restart after the process was killed: resume the in-progress recording that
            // RecordingRepository durably checkpointed, if there was one — see its class doc.
            null -> handleResume()
        }
        return START_STICKY
    }

    private fun handleStart() {
        if (!hasLocationPermission()) {
            // Should not happen: the UI only calls start() from behind LocationPermissionGate.
            stopSelf()
            return
        }
        recordingRepository.start(getString(R.string.session_default_name_prefix))
        showForegroundNotification()
    }

    private fun handleResume() {
        if (!hasLocationPermission()) {
            // Should not happen: a recording can only have started from behind the permission gate.
            stopSelf()
            return
        }
        scope.launch {
            if (recordingRepository.resumeIfActive()) {
                showForegroundNotification()
            } else {
                stopSelf()
            }
        }
    }

    /** Shared by a fresh start and a resume-after-process-death: shows the notification and starts its ticker. */
    private fun showForegroundNotification() {
        createNotificationChannel()
        val state = recordingRepository.state.value
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification(state, elapsedSince(state.startTimestamp)))
            isForeground = true
        }
        if (tickerJob == null) {
            tickerJob = scope.launch {
                // Loop is bounded by isRecording rather than `while (true)`, so a recording that
                // stops through a path other than handleStop() (e.g. the repository itself
                // clearing state) still tears the notification down instead of freezing on a
                // stale "0s" readout.
                while (recordingRepository.state.value.isRecording) {
                    val current = recordingRepository.state.value
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(current, elapsedSince(current.startTimestamp)))
                    delay(1_000L.milliseconds)
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

    private fun elapsedSince(startTimestamp: Long): Long =
        (System.currentTimeMillis() - startTimestamp).coerceAtLeast(0)

    /**
     * @param fromNotification Whether this Stop came from the notification's own action rather
     *                         than the app's UI. In that case the app may not have any screen on
     *                         top to show the discard banner, so a discarded recording is
     *                         reported via a system notification instead (see [notifyDiscarded]).
     */
    private fun handleStop(fromNotification: Boolean) {
        scope.launch {
            val result = recordingRepository.stop()
            tickerJob?.cancel()
            tickerJob = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            if (fromNotification && result is StopResult.Discarded) {
                notifyDiscarded(result)
            }
            stopSelf()
        }
    }

    /** One-shot notification telling the user their track wasn't saved; only used when [handleStop] can't rely on a visible banner. */
    private fun notifyDiscarded(result: StopResult.Discarded) {
        createDiscardedNotificationChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val message = result.lastRejectedAccuracyMeters?.let { accuracy ->
            getString(
                R.string.recording_discarded_message_fmt,
                formatAccuracy(accuracy, unitSystem),
                shortUnitLabel(unitSystem),
            )
        } ?: getString(R.string.recording_discarded_message_no_fix)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_DISCARDED)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(getString(R.string.recording_discarded_title))
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        notificationManager.notify(NOTIFICATION_ID_DISCARDED, notification)
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

    private fun createDiscardedNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_DISCARDED,
            getString(R.string.recording_discarded_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
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
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP_FROM_NOTIFICATION),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val title = getString(R.string.recording_notification_title_fmt, formatDuration(elapsedMs))
        val content = state.rejectedAccuracyMeters?.let { accuracy ->
            getString(
                R.string.recording_notification_signal_too_noisy_fmt,
                formatAccuracy(accuracy, unitSystem),
                shortUnitLabel(unitSystem),
            )
        } ?: getString(
            R.string.recording_notification_content,
            formatAltitude(state.currentAltitude, unitSystem),
            shortUnitLabel(unitSystem),
            formatSpeed(state.currentSpeed, unitSystem),
            speedUnitLabel(unitSystem),
            formatDistance(state.distanceMeters, unitSystem),
            distanceUnitLabel(unitSystem),
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
        private const val CHANNEL_ID_DISCARDED = "recording_discarded"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_ID_DISCARDED = 2
        private const val ACTION_START = "app.matthieu.cairngps.action.START_RECORDING"
        private const val ACTION_STOP = "app.matthieu.cairngps.action.STOP_RECORDING"
        private const val ACTION_STOP_FROM_NOTIFICATION = "app.matthieu.cairngps.action.STOP_RECORDING_FROM_NOTIFICATION"

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
