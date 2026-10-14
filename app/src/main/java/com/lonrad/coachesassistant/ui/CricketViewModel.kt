package com.lonrad.coachesassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.AppDatabase
import com.lonrad.coachesassistant.data.CricketFieldPositionEntity
import com.lonrad.coachesassistant.data.CricketRepository
import com.lonrad.coachesassistant.data.CricketScorecardEntity
import com.lonrad.coachesassistant.data.GameRepository
import com.lonrad.coachesassistant.data.Sport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Cricket's scorecards, innings scores, field plans, and season totals for [sport]. The game itself
 * (creating, choosing, ending, notes, attendance) is AppViewModel's, as for every sport.
 */
class CricketViewModel(
    application: Application,
    private val sport: Sport,
    gameRepository: GameRepository,
    private val repository: CricketRepository,
) : AndroidViewModel(application) {
    private val _messages = MessageQueue()
    val messages = _messages.messages

    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job = launchSafely(_messages, block = block)

    /** Season totals across this sport's finished games; only computed while something observes it. */
    val season: StateFlow<CricketSeasonSummary> =
        combine(
            gameRepository.playersForSport(sport),
            gameRepository.gamesForSport(sport),
            repository.observeFinishedScorecards(sport),
            ::calculateCricketSeason,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CricketSeasonSummary())

    fun scorecards(gameId: Long): Flow<List<CricketScorecardEntity>> = repository.observeScorecards(gameId)

    fun field(gameId: Long): Flow<List<CricketFieldPositionEntity>> = repository.observeField(gameId)

    fun saveScorecard(card: CricketScorecardEntity) {
        launchSafely { repository.saveScorecard(card) }
    }

    fun updateInnings(gameId: Long, extras: Int, opponentRuns: Int, opponentWickets: Int) {
        launchSafely { repository.updateInnings(gameId, extras, opponentRuns, opponentWickets) }
    }

    fun setFieldPosition(gameId: Long, position: String, playerId: Long?) {
        launchSafely { repository.setFieldPosition(gameId, position, playerId) }
    }

    fun clearField(gameId: Long) {
        launchSafely { repository.clearField(gameId) }
    }

    fun copyLatestField(gameId: Long) {
        launchSafely {
            val copied = repository.copyLatestField(gameId, sport)
            _messages.emit(UiMessage(uiText(if (copied) R.string.msg_field_copied else R.string.msg_no_earlier_field)))
        }
    }
}

/** Builds the [CricketViewModel] for [sport] with the app's database. */
fun cricketViewModelFactory(sport: Sport): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val database = AppDatabase.getInstance(application)
        CricketViewModel(application, sport, GameRepository(database), CricketRepository(database))
    }
}
