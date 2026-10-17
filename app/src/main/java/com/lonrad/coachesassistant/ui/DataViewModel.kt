package com.lonrad.coachesassistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.AppDatabase
import com.lonrad.coachesassistant.data.BackupFormat
import com.lonrad.coachesassistant.data.BackupFormatException
import com.lonrad.coachesassistant.data.BackupIo
import com.lonrad.coachesassistant.data.GameCsvIo
import com.lonrad.coachesassistant.data.GameRepository
import com.lonrad.coachesassistant.data.Sport
import com.lonrad.coachesassistant.shared.R as SharedR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Moving data in and out for [sport]: backup and restore, CSV and notes files, and clearing data. */
class DataViewModel(
    application: Application,
    private val sport: Sport,
    private val repository: GameRepository,
    private val csvIo: GameCsvIo,
    private val backupIo: BackupIo,
) : AndroidViewModel(application) {
    private val _messages = MessageQueue()
    val messages = _messages.messages

    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job = launchSafely(_messages, block = block)

    fun clearAllData() {
        launchSafely {
            repository.clearAllData()
            _messages.emit(UiMessage(uiText(R.string.msg_all_data_cleared)))
        }
    }

    fun clearSelectedData(players: Boolean, games: Boolean, events: Boolean) {
        launchSafely {
            repository.clearSelectedData(sport, players, games, events)
            _messages.emit(UiMessage(uiText(R.string.msg_selected_data_cleared)))
        }
    }

    /** The text of an encrypted backup that's been picked and is waiting for its password, or null. */
    private val _pendingRestore = MutableStateFlow<String?>(null)
    val pendingRestore = _pendingRestore.asStateFlow()

    /** Saves every sport's data (not just this one's) to [uri], encrypted with [password] if it isn't null. */
    fun exportBackup(uri: Uri, password: String?) {
        viewModelScope.launch {
            runCatching {
                backupIo.export(getApplication<Application>().contentResolver, uri, password)
            }.onSuccess { counts ->
                _messages.emit(UiMessage(uiText(R.string.msg_backup_saved, playerCount(counts.players), gameCount(counts.games))))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_backup_failed)))
            }
        }
    }

    /**
     * Reads the backup at [uri]. A plain one replaces all data, for every sport, straight away; an
     * encrypted one waits in [pendingRestore] for its password. Nothing changes if it can't be read.
     */
    fun chooseRestoreFile(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                backupIo.readFile(getApplication<Application>().contentResolver, uri)
            }.onSuccess { text ->
                if (BackupFormat.isEncrypted(text)) {
                    _pendingRestore.value = text
                } else {
                    restore(text, password = null)
                }
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_restore_failed)))
            }
        }
    }

    /**
     * Restores the encrypted backup waiting in [pendingRestore]. A wrong password keeps it waiting
     * so the coach can try again; any other outcome ends the restore.
     */
    fun restoreWithPassword(password: String) {
        val text = _pendingRestore.value ?: return
        viewModelScope.launch {
            restore(text, password)
        }
    }

    /** Gives up on the encrypted backup waiting for its password. */
    fun cancelRestore() {
        _pendingRestore.value = null
    }

    private suspend fun restore(text: String, password: String?) {
        runCatching {
            backupIo.restore(text, password)
        }.onSuccess { counts ->
            _pendingRestore.value = null
            _messages.emit(UiMessage(uiText(R.string.msg_restored, playerCount(counts.players), gameCount(counts.games))))
        }.onFailure {
            val wrongPassword = it is BackupFormatException && it.messageRes == SharedR.string.backup_password_wrong
            if (!wrongPassword) _pendingRestore.value = null
            _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_restore_failed)))
        }
    }

    fun exportPlayersCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportPlayersCsv(getApplication<Application>().contentResolver, uri, sport)
            }.onSuccess {
                _messages.emit(UiMessage(uiText(R.string.msg_roster_exported)))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_export_failed)))
            }
        }
    }

    fun importPlayersCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.importPlayersCsv(getApplication<Application>().contentResolver, uri, sport, sport.config.squadPositions)
            }.onSuccess { result ->
                _messages.emit(UiMessage(rosterImportMessage(result)))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_import_failed)))
            }
        }
    }

    fun exportGameNotesTxt(gameId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportGameNotesTxt(getApplication<Application>().contentResolver, getApplication<Application>().resources, gameId, uri)
            }.onSuccess {
                _messages.emit(UiMessage(uiText(R.string.msg_notes_exported)))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_export_failed)))
            }
        }
    }

    fun exportGameCsv(gameId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportGameCsv(getApplication<Application>().contentResolver, gameId, uri)
            }.onSuccess {
                _messages.emit(UiMessage(uiText(R.string.msg_game_csv_exported)))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_export_failed)))
            }
        }
    }
}

private fun playerCount(count: Int) = pluralText(R.plurals.player_count, count)

private fun gameCount(count: Int) = pluralText(R.plurals.game_count, count)

/** Builds the [DataViewModel] for [sport] with the app's database. */
fun dataViewModelFactory(sport: Sport): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val database = AppDatabase.getInstance(application)
        DataViewModel(application, sport, GameRepository(database), GameCsvIo(database), BackupIo(database))
    }
}
