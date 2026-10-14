package com.lonrad.coachesassistant.ui

import android.net.Uri
import com.lonrad.coachesassistant.data.GameEventEntity

/**
 * Everything the Game tab (GameScreen) can ask for. MainActivity implements it with the sport's
 * AppViewModel and DataViewModel, so the screen gets one object rather than a callback for each.
 */
interface GameActions {
    fun createGame(title: String, opponent: String, periodCount: Int, periodMins: Int)
    fun selectGame(gameId: Long)
    fun deleteGame(gameId: Long)
    fun setLineup(playerIds: List<Long>)

    /** Players coming on and going off; see AppViewModel.substitute for how they pair up. */
    fun substitute(playersIn: List<Long>, playersOut: List<Long>)

    /** "Sub now" on a substitution suggestion (see subSuggestion). */
    fun subNow(playerOn: Long, playerOff: Long)

    /** "Later" on a substitution suggestion: put off until [gameTimeMillis] plus the snooze. */
    fun subLater(key: Pair<Long, Long>, gameTimeMillis: Long)
    fun addPlayerScore(playerId: Long, points: Int)
    fun adjustTeamScore(points: Int)
    fun addOpponentScore(points: Int)
    fun addFoul(playerId: Long, adjustment: Int)
    fun addMvpVote(playerId: Long)
    fun toggleTimer()
    fun resetTimer()
    fun setGameTime(timeMillis: Long)
    fun advancePeriod()
    fun endGame()
    fun startGameDay()
    fun stopGameDay()
    fun updateAbsentPlayers(playerIds: List<Long>)
    fun updateNotes(notes: String)
    fun updateEvent(event: GameEventEntity)
    fun deleteEvent(event: GameEventEntity)
    fun exportGame(gameId: Long, uri: Uri)
    fun exportNotes(gameId: Long, uri: Uri)
    fun exportPlayers(uri: Uri)
}

/** The Game tab's actions for one sport, carried out by its view models. */
fun gameActions(app: AppViewModel, data: DataViewModel): GameActions = object : GameActions {
    override fun createGame(title: String, opponent: String, periodCount: Int, periodMins: Int) =
        app.createGame(title, opponent, periodCount, periodMins)
    override fun selectGame(gameId: Long) = app.selectGame(gameId)
    override fun deleteGame(gameId: Long) = app.deleteGame(gameId)
    override fun setLineup(playerIds: List<Long>) = app.setStartingLineup(playerIds)
    override fun substitute(playersIn: List<Long>, playersOut: List<Long>) = app.substitute(playersIn, playersOut)
    override fun subNow(playerOn: Long, playerOff: Long) = app.acceptSubSuggestion(playerOn, playerOff)
    override fun subLater(key: Pair<Long, Long>, gameTimeMillis: Long) = app.snoozeSubSuggestion(key, gameTimeMillis)
    override fun addPlayerScore(playerId: Long, points: Int) = app.addPlayerScore(playerId, points)
    override fun adjustTeamScore(points: Int) = app.adjustTeamScore(points)
    override fun addOpponentScore(points: Int) = app.addOpponentScore(points)
    override fun addFoul(playerId: Long, adjustment: Int) = app.addFoul(playerId, adjustment)
    override fun addMvpVote(playerId: Long) = app.addMvpVote(playerId)
    override fun toggleTimer() = app.toggleTimer()
    override fun resetTimer() = app.resetTimer()
    override fun setGameTime(timeMillis: Long) = app.setGameTime(timeMillis)
    override fun advancePeriod() = app.advancePeriod()
    override fun endGame() = app.endGame()
    override fun startGameDay() = app.startGameDay()
    override fun stopGameDay() = app.stopGameDay()
    override fun updateAbsentPlayers(playerIds: List<Long>) = app.updateAbsentPlayers(playerIds)
    override fun updateNotes(notes: String) = app.updateGameNotes(notes)
    override fun updateEvent(event: GameEventEntity) = app.updateEvent(event)
    override fun deleteEvent(event: GameEventEntity) = app.deleteEvent(event)
    override fun exportGame(gameId: Long, uri: Uri) = data.exportGameCsv(gameId, uri)
    override fun exportNotes(gameId: Long, uri: Uri) = data.exportGameNotesTxt(gameId, uri)
    override fun exportPlayers(uri: Uri) = data.exportPlayersCsv(uri)
}
