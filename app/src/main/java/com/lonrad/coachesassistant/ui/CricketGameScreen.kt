package com.lonrad.coachesassistant.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.CricketScorecardEntity
import com.lonrad.coachesassistant.data.Dismissal
import com.lonrad.coachesassistant.data.GameEntity
import com.lonrad.coachesassistant.data.GameLineup
import com.lonrad.coachesassistant.data.PlayerEntity
import com.lonrad.coachesassistant.data.displayLabel
import com.lonrad.coachesassistant.data.formatOvers
import com.lonrad.coachesassistant.data.parseOvers
import kotlinx.coroutines.flow.flowOf

/**
 * The Game tab for cricket: no clock, periods, or substitutions. A game has the two teams' scores, a
 * scorecard per player (entered from the scorebook during or after the game), and a field plan.
 * Creating, choosing, ending, and deleting games, notes, and attendance work as for every sport.
 */
@Composable
fun CricketGameScreen(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    players: List<PlayerEntity>,
    games: List<GameEntity>,
    selectedGame: GameEntity?,
    lineup: GameLineup,
    cricketViewModel: CricketViewModel,
    onCreateGame: (title: String, opponent: String) -> Unit,
    onSelectGame: (Long) -> Unit,
    onDeleteGame: (Long) -> Unit,
    onExportGame: (Long, Uri) -> Unit,
    onEndGame: () -> Unit,
    onUpdateAbsentPlayers: (List<Long>) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onExportNotes: (Long, Uri) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    var gameTitle by rememberSaveable { mutableStateOf("") }
    var opponent by rememberSaveable { mutableStateOf("") }
    var showAttendanceDialog by remember { mutableStateOf(false) }
    var showNotesDialog by rememberSaveable { mutableStateOf(false) }
    var editingPlayerId by rememberSaveable(selectedGame?.id) { mutableStateOf<Long?>(null) }

    val exportGame = rememberIdFileExportLauncher(mimeType = "text/csv", onExport = onExportGame)
    val exportNotes = rememberIdFileExportLauncher(mimeType = "text/plain", onExport = onExportNotes)

    val gameId = selectedGame?.id
    val scorecards by remember(gameId) { gameId?.let(cricketViewModel::scorecards) ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val field by remember(gameId) { gameId?.let(cricketViewModel::field) ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val cardByPlayer = scorecards.associateBy { it.playerId }
    val availablePlayers = players.filter { it.id !in lineup.absentIds }

    val management: @Composable () -> Unit = {
        GameStatsPanel(
            gameTitle = gameTitle,
            onGameTitleChange = { gameTitle = it },
            opponent = opponent,
            onOpponentChange = { opponent = it },
            showPeriodSettings = false,
            onCreateGame = {
                onCreateGame(gameTitle, opponent)
                gameTitle = ""
                opponent = ""
            },
            games = games.filter { !it.isFinished },
            onSelectGame = onSelectGame,
            onDeleteGame = onDeleteGame,
            onExportGame = { id -> exportGame(id, "game_$id.csv") },
            selectedGameId = gameId,
            onlyManagement = true,
        )
    }
    val score: @Composable (GameEntity) -> Unit = { game ->
        InningsCard(
            game = game,
            onSave = { extras, runs, wickets -> cricketViewModel.updateInnings(game.id, extras, runs, wickets) },
            onEndGame = onEndGame,
            onManageAttendance = { showAttendanceDialog = true },
            onExport = { exportGame(game.id, "game_${game.id}.csv") },
        )
    }
    val cards: @Composable () -> Unit = {
        ScorecardsCard(players = availablePlayers, cardByPlayer = cardByPlayer, onEdit = { editingPlayerId = it.id })
    }
    val fieldPlan: @Composable (GameEntity) -> Unit = { game ->
        FieldPlanCard(
            players = availablePlayers,
            field = field,
            onSetPosition = { position, playerId -> cricketViewModel.setFieldPosition(game.id, position.name, playerId) },
            onClear = { cricketViewModel.clearField(game.id) },
            onCopyLastField = { cricketViewModel.copyLatestField(game.id) },
        )
    }

    if (isExpanded) {
        Row(
            modifier = modifier.padding(layout.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(layout.sectionGap),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
                if (selectedGame != null) {
                    score(selectedGame)
                    cards()
                } else {
                    EmptyMessage(stringResource(R.string.select_or_create_a_game_to_start_tracking))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
                if (selectedGame != null) GameNotesPanel(onClick = { showNotesDialog = true })
                management()
                if (selectedGame != null) fieldPlan(selectedGame)
            }
        }
    } else {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(layout.screenPadding),
            verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
        ) {
            Text(
                text = stringResource(R.string.game_tracker),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            management()
            if (selectedGame != null) {
                GameNotesPanel(onClick = { showNotesDialog = true })
                score(selectedGame)
                cards()
                fieldPlan(selectedGame)
            }
        }
    }

    editingPlayerId?.let { id -> players.firstOrNull { it.id == id } }?.let { player ->
        if (gameId != null) {
            ScorecardDialog(
                player = player,
                card = cardByPlayer[player.id] ?: CricketScorecardEntity(gameId = gameId, playerId = player.id),
                onDismiss = { editingPlayerId = null },
                onSave = { card ->
                    cricketViewModel.saveScorecard(card)
                    editingPlayerId = null
                },
            )
        }
    }

    if (showAttendanceDialog && selectedGame != null) {
        AttendanceDialog(
            players = players,
            absentPlayerIds = lineup.absentIds.toList(),
            onDismiss = { showAttendanceDialog = false },
            onConfirm = { ids ->
                onUpdateAbsentPlayers(ids)
                showAttendanceDialog = false
            },
        )
    }

    if (showNotesDialog && selectedGame != null) {
        CoachingNotesDialog(
            notes = selectedGame.notes,
            onDismiss = { showNotesDialog = false },
            onSave = { newNotes ->
                if (newNotes != selectedGame.notes) onUpdateNotes(newNotes)
                showNotesDialog = false
            },
            onExport = { newNotes ->
                if (newNotes != selectedGame.notes) onUpdateNotes(newNotes)
                exportNotes(selectedGame.id, "notes_${selectedGame.id}.txt")
            },
        )
    }
}

/**
 * Both teams' scores: ours from the scorecards plus extras, theirs as entered, with the extras and
 * their runs and wickets to fill in, and the game's actions.
 */
@Composable
private fun InningsCard(
    game: GameEntity,
    onSave: (extras: Int, opponentRuns: Int, opponentWickets: Int) -> Unit,
    onEndGame: () -> Unit,
    onManageAttendance: () -> Unit,
    onExport: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    var extras by rememberSaveable(game.id, game.extras) { mutableStateOf(game.extras.toString()) }
    var theirRuns by rememberSaveable(game.id, game.opponentScore) { mutableStateOf(game.opponentScore.toString()) }
    var theirWickets by rememberSaveable(game.id, game.opponentWickets) { mutableStateOf(game.opponentWickets.toString()) }
    var confirmEnd by remember { mutableStateOf(false) }
    val changed = extras != game.extras.toString() || theirRuns != game.opponentScore.toString() ||
        theirWickets != game.opponentWickets.toString()

    SectionCard(title = stringResource(R.string.cricket_score)) {
        if (game.isFinished) StatusBadge(stringResource(R.string.game_finished))
        Text(game.scoreLine().asString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.cricket_innings_hint), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
            NumberField(stringResource(R.string.cricket_extras), extras, { extras = it }, Modifier.weight(1f))
            NumberField(stringResource(R.string.cricket_their_runs), theirRuns, { theirRuns = it }, Modifier.weight(1f))
            NumberField(stringResource(R.string.cricket_their_wickets), theirWickets, { theirWickets = it }, Modifier.weight(1f))
        }
        Button(
            onClick = { onSave(extras.toIntOrNull() ?: 0, theirRuns.toIntOrNull() ?: 0, theirWickets.toIntOrNull() ?: 0) },
            enabled = changed,
        ) {
            Text(stringResource(R.string.cricket_save_score))
        }
        HorizontalDivider()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
            OutlinedButton(onClick = onManageAttendance) { Text(stringResource(R.string.manage_attendance)) }
            OutlinedButton(onClick = onExport) { Text(stringResource(R.string.export_game_csv)) }
            if (!game.isFinished) {
                Button(
                    onClick = { confirmEnd = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.end_game))
                }
            }
        }
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = stringResource(R.string.end_this_game_question),
            message = endGameConfirmMessage(game).asString(),
            confirmLabel = stringResource(R.string.end_game),
            dismissLabel = stringResource(R.string.keep_playing),
            onConfirm = {
                confirmEnd = false
                onEndGame()
            },
            onDismiss = { confirmEnd = false },
        )
    }
}

/** A player per row with what's on their scorecard so far; tapping one edits it. */
@Composable
private fun ScorecardsCard(
    players: List<PlayerEntity>,
    cardByPlayer: Map<Long?, CricketScorecardEntity>,
    onEdit: (PlayerEntity) -> Unit,
) {
    SectionCard(title = stringResource(R.string.cricket_scorecards)) {
        Text(stringResource(R.string.cricket_scorecards_hint), style = MaterialTheme.typography.bodySmall)
        if (players.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_players_yet_add_your_roster_first))
        }
        players.forEach { player ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onEdit(player) }
                    .padding(vertical = 4.dp),
            ) {
                Text(player.displayLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    scorecardSummary(cardByPlayer[player.id]),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A scorecard in a line, e.g. "32* (28) · 2/15 (4.0 ov) · 1 catch", or that nothing's entered. */
@Composable
private fun scorecardSummary(card: CricketScorecardEntity?): String {
    if (card == null || card.isEmpty) return stringResource(R.string.cricket_nothing_entered)
    val parts = buildList {
        val howOut = card.howOut
        if (howOut.batted) {
            val runs = if (howOut.isOut) card.runs.toString() else stringResource(R.string.cricket_runs_not_out, card.runs)
            // The app's current language, so it follows a change of language like the text does.
            val locale = LocalConfiguration.current.locales[0]
            val dismissal = stringResource(howOut.label).lowercase(locale)
            add(
                if (card.ballsFaced > 0) {
                    stringResource(R.string.cricket_batting_summary, runs, card.ballsFaced, dismissal)
                } else {
                    stringResource(R.string.cricket_batting_summary_no_balls, runs, dismissal)
                },
            )
        }
        if (card.ballsBowled > 0) {
            add(stringResource(R.string.cricket_bowling_summary, card.wickets, card.runsConceded, formatOvers(card.ballsBowled)))
        }
        if (card.catches > 0) add(pluralStringResource(R.plurals.cricket_catch_count, card.catches, card.catches))
        if (card.runOuts > 0) add(pluralStringResource(R.plurals.cricket_run_out_count, card.runOuts, card.runOuts))
        if (card.stumpings > 0) add(pluralStringResource(R.plurals.cricket_stumping_count, card.stumpings, card.stumpings))
    }
    return parts.joinToString(" · ")
}

private val Dismissal.label: Int
    get() = when (this) {
        Dismissal.DID_NOT_BAT -> R.string.dismissal_did_not_bat
        Dismissal.NOT_OUT -> R.string.dismissal_not_out
        Dismissal.RETIRED -> R.string.dismissal_retired
        Dismissal.BOWLED -> R.string.dismissal_bowled
        Dismissal.CAUGHT -> R.string.dismissal_caught
        Dismissal.LBW -> R.string.dismissal_lbw
        Dismissal.RUN_OUT -> R.string.dismissal_run_out
        Dismissal.STUMPED -> R.string.dismissal_stumped
        Dismissal.OTHER_OUT -> R.string.dismissal_other
    }

/** Edits one player's scorecard: batting (how out, runs, balls, 4s, 6s), bowling, and fielding. */
@Composable
private fun ScorecardDialog(
    player: PlayerEntity,
    card: CricketScorecardEntity,
    onDismiss: () -> Unit,
    onSave: (CricketScorecardEntity) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    fun Int.text() = if (this == 0) "" else toString()
    var howOut by rememberSaveable { mutableStateOf(card.howOut) }
    var runs by rememberSaveable { mutableStateOf(card.runs.text()) }
    var balls by rememberSaveable { mutableStateOf(card.ballsFaced.text()) }
    var fours by rememberSaveable { mutableStateOf(card.fours.text()) }
    var sixes by rememberSaveable { mutableStateOf(card.sixes.text()) }
    var overs by rememberSaveable { mutableStateOf(if (card.ballsBowled == 0) "" else formatOvers(card.ballsBowled)) }
    var maidens by rememberSaveable { mutableStateOf(card.maidens.text()) }
    var conceded by rememberSaveable { mutableStateOf(card.runsConceded.text()) }
    var wickets by rememberSaveable { mutableStateOf(card.wickets.text()) }
    var catches by rememberSaveable { mutableStateOf(card.catches.text()) }
    var runOuts by rememberSaveable { mutableStateOf(card.runOuts.text()) }
    var stumpings by rememberSaveable { mutableStateOf(card.stumpings.text()) }
    val ballsBowled = parseOvers(overs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(player.displayLabel()) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.smallGap),
            ) {
                Text(stringResource(R.string.cricket_batting), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.cricket_how_out), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(layout.tinyGap)) {
                    Dismissal.entries.forEach { dismissal ->
                        FilterChip(
                            selected = howOut == dismissal,
                            onClick = { howOut = dismissal },
                            label = { Text(stringResource(dismissal.label)) },
                        )
                    }
                }
                if (howOut.batted) {
                    Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                        NumberField(stringResource(R.string.cricket_runs), runs, { runs = it }, Modifier.weight(1f))
                        NumberField(stringResource(R.string.cricket_balls), balls, { balls = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                        NumberField(stringResource(R.string.cricket_fours), fours, { fours = it }, Modifier.weight(1f))
                        NumberField(stringResource(R.string.cricket_sixes), sixes, { sixes = it }, Modifier.weight(1f))
                    }
                }

                Text(stringResource(R.string.cricket_bowling), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = overs,
                    onValueChange = { text -> overs = text.filter { it.isDigit() || it == '.' }.take(5) },
                    label = { Text(stringResource(R.string.cricket_overs)) },
                    supportingText = {
                        Text(stringResource(if (ballsBowled == null) R.string.cricket_overs_invalid else R.string.cricket_overs_hint))
                    },
                    isError = ballsBowled == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                    NumberField(stringResource(R.string.cricket_maidens), maidens, { maidens = it }, Modifier.weight(1f))
                    NumberField(stringResource(R.string.cricket_wickets), wickets, { wickets = it }, Modifier.weight(1f))
                }
                NumberField(stringResource(R.string.cricket_runs_conceded), conceded, { conceded = it }, Modifier.fillMaxWidth())

                Text(stringResource(R.string.cricket_fielding), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                    NumberField(stringResource(R.string.cricket_catches), catches, { catches = it }, Modifier.weight(1f))
                    NumberField(stringResource(R.string.cricket_run_outs), runOuts, { runOuts = it }, Modifier.weight(1f))
                    NumberField(stringResource(R.string.cricket_stumpings), stumpings, { stumpings = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ballsBowled != null,
                onClick = {
                    fun String.n() = toIntOrNull() ?: 0
                    val batted = howOut.batted
                    onSave(
                        card.copy(
                            dismissal = howOut.name,
                            runs = if (batted) runs.n() else 0,
                            ballsFaced = if (batted) balls.n() else 0,
                            fours = if (batted) fours.n() else 0,
                            sixes = if (batted) sixes.n() else 0,
                            ballsBowled = ballsBowled ?: 0,
                            maidens = maidens.n(),
                            runsConceded = conceded.n(),
                            wickets = wickets.n().coerceAtMost(10),
                            catches = catches.n(),
                            runOuts = runOuts.n(),
                            stumpings = stumpings.n(),
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A whole-number field (digits only, up to four of them). */
@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(4)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
