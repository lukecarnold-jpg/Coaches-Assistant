package com.lukearnold.coachesassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.GameRepository
import kotlinx.coroutines.launch

/**
 * Keeps what follows every sport's games in step with them while the app is open: Game Day mode,
 * the game clock notification, and substitution reminders. There's one for the whole app rather
 * than one in each sport's AppViewModel, which stay alive after switching sport and would each
 * repeat this work.
 */
class GameSyncViewModel(
    application: Application,
    repository: GameRepository,
    gameDayMode: GameDayMode,
) : AndroidViewModel(application) {
    init {
        viewModelScope.launch {
            repository.games.collect { allGames ->
                // Game Day mode lasts until its game ends. End Game turns it off directly; this also
                // covers the game being deleted, cleared, or replaced by a restore, in any sport.
                gameDayMode.stopUnlessPlaying(allGames.filter { !it.isFinished }.map { it.id })
                // Every sport's running clocks, so the notification follows whichever is running.
                GameClockNotification.sync(getApplication(), allGames)
                // Substitution reminders, which keep running with the app in the background.
                SubReminderService.startIfNeeded(getApplication(), allGames)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                GameSyncViewModel(application, GameRepository(AppDatabase.getInstance(application)), GameDayMode.get(application))
            }
        }
    }
}
