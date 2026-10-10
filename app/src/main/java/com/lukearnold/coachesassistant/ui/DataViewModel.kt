package com.lukearnold.coachesassistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.BackupIo
import com.lukearnold.coachesassistant.data.GameCsvIo
import com.lukearnold.coachesassistant.data.GameRepository
import com.lukearnold.coachesassistant.data.Sport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    /** Saves every sport's data (not just this one's) to [uri]; see BackupIo. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                backupIo.export(getApplication<Application>().contentResolver, uri)
            }.onSuccess { counts ->
                _messages.emit(UiMessage(uiText(R.string.msg_backup_saved, playerCount(counts.players), gameCount(counts.games))))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_backup_failed)))
            }
        }
    }

    /** Replaces all data, for every sport, with the backup at [uri]; nothing changes if it can't be read. */
    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                backupIo.restore(getApplication<Application>().contentResolver, uri)
            }.onSuccess { counts ->
                _messages.emit(UiMessage(uiText(R.string.msg_restored, playerCount(counts.players), gameCount(counts.games))))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_restore_failed)))
            }
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
