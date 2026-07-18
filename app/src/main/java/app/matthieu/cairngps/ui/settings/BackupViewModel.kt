package app.matthieu.cairngps.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.BackupImportError
import app.matthieu.cairngps.data.BackupRepository
import app.matthieu.cairngps.data.InvalidBackupException
import app.matthieu.cairngps.ui.common.factoryOf
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One-shot outcome of an export/import attempt, consumed by the Settings screen as a snackbar. */
sealed interface BackupEvent {
    data object ExportSuccess : BackupEvent
    data object ExportError : BackupEvent
    data object ImportSuccess : BackupEvent
    data class ImportError(val reason: BackupImportError) : BackupEvent
}

/**
 * Drives the full data backup export/import ([BackupRepository]) for the Settings screen — the
 * only screen that wires this up. Split out of [SettingsViewModel] (which every other screen also
 * instantiates just to read [AppSettings][app.matthieu.cairngps.data.AppSettings]) so those seven
 * other screens no longer carry a backup dependency they never use.
 */
class BackupViewModel(private val backupRepository: BackupRepository) : ViewModel() {

    private val _isBackupWorking = MutableStateFlow(false)

    /** True while an export or import is in flight — the screen disables both actions meanwhile. */
    val isBackupWorking: StateFlow<Boolean> = _isBackupWorking.asStateFlow()

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val backupEvents: SharedFlow<BackupEvent> = _backupEvents.asSharedFlow()

    /** Writes every piece of user data to [output], a stream the caller opened and this will close. */
    fun exportBackup(output: OutputStream) {
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
     * Restores every piece of user data from [input] — a stream the caller opened and this will
     * close — replacing everything currently stored. The caller is expected to have already
     * confirmed this with the user.
     */
    fun importBackup(input: InputStream) {
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
        fun factory(backupRepository: BackupRepository): ViewModelProvider.Factory =
            factoryOf { BackupViewModel(backupRepository) }
    }
}
