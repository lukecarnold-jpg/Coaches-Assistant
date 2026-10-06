package com.lukearnold.coachesassistant.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.Sport
import com.lukearnold.coachesassistant.data.displayLabel

@Composable
fun RosterScreen(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    sport: Sport,
    players: List<PlayerEntity>,
    onAddPlayer: (String, String, String?) -> Unit,
    onUpdatePlayer: (PlayerEntity, String, String, String?) -> Unit,
    onDeletePlayer: (Long) -> Unit,
    onExportPlayers: (Uri) -> Unit,
    onImportPlayers: (Uri) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    val squadPositions = sport.config.squadPositions

    var name by rememberSaveable { mutableStateOf("") }
    var jerseyNumber by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable(sport) { mutableStateOf<String?>(null) }
    var showError by rememberSaveable { mutableStateOf(value = false) }

    var editingPlayer by remember { mutableStateOf<PlayerEntity?>(null) }
    var pendingRemoval by remember { mutableStateOf<PlayerEntity?>(null) }
    val requestRemove: (Long) -> Unit = { id -> pendingRemoval = players.firstOrNull { it.id == id } }

    pendingRemoval?.let { player ->
        ConfirmDialog(
            title = stringResource(R.string.remove_this_player_question),
            message = removePlayerConfirmMessage(player).asString(),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                pendingRemoval = null
                onDeletePlayer(player.id)
            },
            onDismiss = { pendingRemoval = null },
        )
    }
    var editName by remember { mutableStateOf("") }
    var editJerseyNumber by remember { mutableStateOf("") }
    var editPosition by remember { mutableStateOf<String?>(null) }

    val exportRosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let(onExportPlayers)
    }

    val importRosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onImportPlayers)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
    ) {
        Text(
            text = stringResource(R.string.team_roster),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
                ) {
                    RosterForm(
                        name = name,
                        onNameChange = {
                            name = it
                            showError = false
                        },
                        jerseyNumber = jerseyNumber,
                        onJerseyNumberChange = {
                            jerseyNumber = it
                            showError = false
                        },
                        squadPositions = squadPositions,
                        position = position,
                        onPositionChange = { position = it },
                        showError = showError,
                        onAddPlayer = {
                            if (name.isBlank()) {
                                showError = true
                            } else {
                                onAddPlayer(name, jerseyNumber, position)
                                name = ""
                                jerseyNumber = ""
                                position = null
                                showError = false
                            }
                        },
                        onExportPlayers = {
                            exportRosterLauncher.launch("players.csv")
                        },
                    ) {
                        importRosterLauncher.launch(arrayOf("text/comma-separated-values", "text/csv"))
                    }
                }

                Column(
                    modifier = Modifier.weight(1.5f),
                    verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
                ) {
                    RosterList(
                        players = players,
                        editingPlayer = editingPlayer,
                        editName = editName,
                        onEditNameChange = { editName = it },
                        editJerseyNumber = editJerseyNumber,
                        onEditJerseyNumberChange = { editJerseyNumber = it },
                        squadPositions = squadPositions,
                        editPosition = editPosition,
                        onEditPositionChange = { editPosition = it },
                        onSaveUpdate = { player ->
                            onUpdatePlayer(player, editName, editJerseyNumber, editPosition)
                            editingPlayer = null
                        },
                        onCancelEdit = { editingPlayer = null },
                        onStartEdit = { player ->
                            editingPlayer = player
                            editName = player.name
                            editJerseyNumber = player.jerseyNumber?.toString().orEmpty()
                            editPosition = player.position
                        },
                        onDeletePlayer = requestRemove,
                    )
                }
            }
        } else {
            RosterForm(
                name = name,
                onNameChange = {
                    name = it
                    showError = false
                },
                jerseyNumber = jerseyNumber,
                onJerseyNumberChange = {
                    jerseyNumber = it
                    showError = false
                },
                squadPositions = squadPositions,
                position = position,
                onPositionChange = { position = it },
                showError = showError,
                onAddPlayer = {
                    if (name.isBlank()) {
                        showError = true
                    } else {
                        onAddPlayer(name, jerseyNumber, position)
                        name = ""
                        jerseyNumber = ""
                        position = null
                        showError = false
                    }
                },
                onExportPlayers = {
                    exportRosterLauncher.launch("players.csv")
                },
            ) {
                importRosterLauncher.launch(arrayOf("text/comma-separated-values", "text/csv"))
            }

            RosterList(
                players = players,
                editingPlayer = editingPlayer,
                editName = editName,
                onEditNameChange = { editName = it },
                editJerseyNumber = editJerseyNumber,
                onEditJerseyNumberChange = { editJerseyNumber = it },
                squadPositions = squadPositions,
                editPosition = editPosition,
                onEditPositionChange = { editPosition = it },
                onSaveUpdate = { player ->
                    onUpdatePlayer(player, editName, editJerseyNumber, editPosition)
                    editingPlayer = null
                },
                onCancelEdit = { editingPlayer = null },
                onStartEdit = { player ->
                    editingPlayer = player
                    editName = player.name
                    editJerseyNumber = player.jerseyNumber?.toString().orEmpty()
                    editPosition = player.position
                },
                onDeletePlayer = requestRemove,
            )
        }
    }
}

@Composable
fun RosterForm(
    name: String,
    onNameChange: (String) -> Unit,
    jerseyNumber: String,
    onJerseyNumberChange: (String) -> Unit,
    squadPositions: List<String>,
    position: String?,
    onPositionChange: (String?) -> Unit,
    showError: Boolean,
    onAddPlayer: () -> Unit,
    onExportPlayers: () -> Unit,
    onImportPlayers: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    Column(verticalArrangement = Arrangement.spacedBy(layout.sectionGap)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.player_name)) },
            modifier = Modifier.fillMaxWidth(),
            isError = showError && name.isBlank(),
        )

        OutlinedTextField(
            value = jerseyNumber,
            onValueChange = onJerseyNumberChange,
            label = { Text(stringResource(R.string.jersey_number_optional)) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (squadPositions.isNotEmpty()) {
            PositionSelector(
                squadPositions = squadPositions,
                position = position,
                onPositionChange = onPositionChange,
            )
        }

        if (showError) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.player_name_is_required),
                    modifier = Modifier.padding(layout.cardPadding),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
        ) {
            Button(
                onClick = onAddPlayer,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.add_player))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
        ) {
            OutlinedButton(
                onClick = onExportPlayers,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.export_roster_csv))
            }

            OutlinedButton(
                onClick = onImportPlayers,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.import_roster_csv))
            }
        }
    }
}

@Composable
fun RosterList(
    players: List<PlayerEntity>,
    editingPlayer: PlayerEntity?,
    editName: String,
    onEditNameChange: (String) -> Unit,
    editJerseyNumber: String,
    onEditJerseyNumberChange: (String) -> Unit,
    squadPositions: List<String>,
    editPosition: String?,
    onEditPositionChange: (String?) -> Unit,
    onSaveUpdate: (PlayerEntity) -> Unit,
    onCancelEdit: () -> Unit,
    onStartEdit: (PlayerEntity) -> Unit,
    onDeletePlayer: (Long) -> Unit,
) {
    if (players.isEmpty()) {
        EmptyMessage(stringResource(R.string.no_players_yet_add_your_roster_first))
    } else {
        players.forEach { player ->
            if (editingPlayer?.id == player.id) {
                val layout = LocalResponsiveLayout.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(layout.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(layout.smallGap),
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = onEditNameChange,
                            label = { Text(stringResource(R.string.player_name)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = editJerseyNumber,
                            onValueChange = onEditJerseyNumberChange,
                            label = { Text(stringResource(R.string.jersey_number_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (squadPositions.isNotEmpty()) {
                            PositionSelector(
                                squadPositions = squadPositions,
                                position = editPosition,
                                onPositionChange = onEditPositionChange,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
                        ) {
                            Button(
                                onClick = { onSaveUpdate(player) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.save))
                            }
                            TextButton(
                                onClick = onCancelEdit,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LocalResponsiveLayout.current.cardPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = player.displayLabel(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!player.position.isNullOrBlank()) {
                                Text(
                                    text = positionLabel(player.position).asString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }

                        Row {
                            TextButton(
                                onClick = { onStartEdit(player) },
                            ) {
                                Text(stringResource(R.string.edit))
                            }
                            TextButton(
                                onClick = { onDeletePlayer(player.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(stringResource(R.string.remove))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PositionSelector(
    squadPositions: List<String>,
    position: String?,
    onPositionChange: (String?) -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap)) {
        Text(stringResource(R.string.position_optional), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
            verticalArrangement = Arrangement.spacedBy(layout.smallGap),
        ) {
            squadPositions.forEach { candidate ->
                FilterChip(
                    selected = position == candidate,
                    onClick = { onPositionChange(if (position == candidate) null else candidate) },
                    label = { Text(positionLabel(candidate).asString()) },
                )
            }
        }
    }
}
