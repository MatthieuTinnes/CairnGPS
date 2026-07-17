package app.matthieu.cairngps.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.AppSettings
import app.matthieu.cairngps.data.BackupImportError
import app.matthieu.cairngps.data.BackupRepository
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.InvalidBackupException
import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.ThemeMode
import app.matthieu.cairngps.ui.common.factoryOf
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot outcome of an export/import attempt, consumed by the Settings screen as a snackbar. */
sealed interface BackupEvent {
    data object ExportSuccess : BackupEvent
    data object ExportError : BackupEvent
    data object ImportSuccess : BackupEvent
    data class ImportError(val reason: BackupImportError) : BackupEvent
}

/**
 * Exposes [AppSettings] as a [StateFlow] and writes changes back through the [SettingsRepository].
 * Shared by the home, records and settings screens; DataStore keeps every observer in sync.
 *
 * Also drives the full data backup export/import ([BackupRepository]) — only the Settings screen
 * wires [exportBackup]/[importBackup] up to UI, so [backupRepository] is optional; every other
 * screen only needs [settings] and passes `null`.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val backupRepository: BackupRepository? = null,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _isBackupWorking = MutableStateFlow(false)

    /** True while an export or import is in flight — the screen disables both actions meanwhile. */
    val isBackupWorking: StateFlow<Boolean> = _isBackupWorking.asStateFlow()

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val backupEvents: SharedFlow<BackupEvent> = _backupEvents.asSharedFlow()

    fun setCoordinateFormat(format: CoordinateFormat) {
        viewModelScope.launch { repository.setCoordinateFormat(format) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setNorthReference(reference: NorthReference) {
        viewModelScope.launch { repository.setNorthReference(reference) }
    }

    /**
     * Writes every piece of user data to [output], a stream the caller opened and will close.
     * A no-op if this instance wasn't given a [backupRepository] (see the class doc).
     */
    fun exportBackup(output: OutputStream) {
        val backupRepository = backupRepository ?: return
        viewModelScope.launch {
            _isBackupWorking.value = true
            val event = try {
                backupRepository.export(output)
                BackupEvent.ExportSuccess
            } catch (e: IOException) {
                BackupEvent.ExportError
            }
            _isBackupWorking.value = false
            _backupEvents.emit(event)
        }
    }

    /**
     * Restores every piece of user data from [input] — a stream the caller opened and will
     * close — replacing everything currently stored. The caller is expected to have already
     * confirmed this with the user. A no-op if this instance wasn't given a [backupRepository]
     * (see the class doc).
     */
    fun importBackup(input: InputStream) {
        val backupRepository = backupRepository ?: return
        viewModelScope.launch {
            _isBackupWorking.value = true
            val event = try {
                backupRepository.import(input)
                BackupEvent.ImportSuccess
            } catch (e: InvalidBackupException) {
                BackupEvent.ImportError(e.reason)
            }
            _isBackupWorking.value = false
            _backupEvents.emit(event)
        }
    }

    companion object {
        fun factory(repository: SettingsRepository, backupRepository: BackupRepository? = null): ViewModelProvider.Factory =
            factoryOf { SettingsViewModel(repository, backupRepository) }
    }
}
