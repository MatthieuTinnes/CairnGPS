package app.matthieu.cairngps.demo

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import app.matthieu.cairngps.BuildConfig

/**
 * Screenshot/screencast demo mode: replaces every live data source with deterministic synthetic
 * data and swaps the Room database for a throwaway one seeded with a fictional history, so
 * captures never expose the real position, tracks or waypoints of whoever is holding the phone.
 *
 * Debug builds only. [isAvailable] is [BuildConfig.DEBUG], a compile-time constant, so in the
 * release build every call site below folds to the "off" branch and R8 shrinks the whole
 * `demo` package out of the APK — the F-Droid artifact contains none of this.
 *
 * The flag is stored in its own [android.content.SharedPreferences] file rather than in
 * `SettingsRepository`'s DataStore because it has to be readable *synchronously* from
 * `CairnApplication.onCreate`: it decides which database file to open, before any coroutine has
 * had a chance to run.
 */
object DemoMode {

    private const val PREFS_NAME = "demo_mode"
    private const val KEY_ENABLED = "enabled"

    /** Whether demo mode can be turned on at all — debug builds only. */
    val isAvailable: Boolean get() = BuildConfig.DEBUG

    // Read once at process start and then never re-read: the database file and the repositories'
    // data sources are picked during startup, so flipping this mid-process would leave the app in
    // an inconsistent half-demo state. setEnabled() restarts the process instead.
    @Volatile
    private var enabled: Boolean = false

    /** Whether the current process is running with synthetic data. */
    val isEnabled: Boolean get() = isAvailable && enabled

    /** Latches the persisted flag for this process. Called once from `CairnApplication.onCreate`. */
    fun init(context: Context) {
        enabled = isAvailable && prefs(context).getBoolean(KEY_ENABLED, false)
    }

    /** The persisted flag, which [isEnabled] only picks up on the next process start. */
    fun isPersistedEnabled(context: Context): Boolean =
        isAvailable && prefs(context).getBoolean(KEY_ENABLED, false)

    /**
     * Persists the flag and restarts the app so the new value takes effect. A synchronous commit
     * rather than the usual `apply()`: the process is killed on the next line, and an asynchronous
     * write would be lost before it reached disk.
     */
    fun setEnabled(context: Context, value: Boolean) {
        if (!isAvailable) return
        prefs(context).edit(commit = true) { putBoolean(KEY_ENABLED, value) }
        restartApp(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launch?.let(context::startActivity)
        Runtime.getRuntime().exit(0)
    }
}
