package com.example.basketballrotation.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.basketballrotation.data.PlayerEntity
import com.example.basketballrotation.data.displayLabel

@Composable
fun RosterScreen(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    players: List<PlayerEntity>,
    onAddPlayer: (String, String) -> Unit,
    onUpdatePlayer: (PlayerEntity, String, String) -> Unit,
    onDeletePlayer: (Long) -> Unit,
    onExportPlayers: (Uri) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var jerseyNumber by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(value = false) }

    var editingPlayer by remember { mutableStateOf<PlayerEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editJerseyNumber by remember { mutableStateOf("") }

    val exportRosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let(onExportPlayers)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Team roster",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        showError = showError,
                        onAddPlayer = {
                            if (name.isBlank()) {
                                showError = true
                            } else {
                                onAddPlayer(name, jerseyNumber)
                                name = ""
                                jerseyNumber = ""
                                showError = false
                            }
                        },
                        onExportPlayers = {
                            exportRosterLauncher.launch("players.csv")
                        },
                    )
                }

                Column(
                    modifier = Modifier.weight(1.5f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RosterList(
                        players = players,
                        editingPlayer = editingPlayer,
                        editName = editName,
                        onEditNameChange = { editName = it },
                        editJerseyNumber = editJerseyNumber,
                        onEditJerseyNumberChange = { editJerseyNumber = it },
                        onSaveUpdate = { player ->
                            onUpdatePlayer(player, editName, editJerseyNumber)
                            editingPlayer = null
                        },
                        onCancelEdit = { editingPlayer = null },
                        onStartEdit = { player ->
                            editingPlayer = player
                            editName = player.name
                            editJerseyNumber = player.jerseyNumber?.toString().orEmpty()
                        },
                        onDeletePlayer = onDeletePlayer
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
                showError = showError,
                onAddPlayer = {
                    if (name.isBlank()) {
                        showError = true
                    } else {
                        onAddPlayer(name, jerseyNumber)
                        name = ""
                        jerseyNumber = ""
                        showError = false
                    }
                },
                onExportPlayers = {
                exportRosterLauncher.launch("players.csv")
            },
            )

            RosterList(
                players = players,
                editingPlayer = editingPlayer,
                editName = editName,
                onEditNameChange = { editName = it },
                editJerseyNumber = editJerseyNumber,
                onEditJerseyNumberChange = { editJerseyNumber = it },
                onSaveUpdate = { player ->
                    onUpdatePlayer(player, editName, editJerseyNumber)
                    editingPlayer = null
                },
                onCancelEdit = { editingPlayer = null },
                onStartEdit = { player ->
                    editingPlayer = player
                    editName = player.name
                    editJerseyNumber = player.jerseyNumber?.toString().orEmpty()
                },
                onDeletePlayer = onDeletePlayer
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
    showError: Boolean,
    onAddPlayer: () -> Unit,
    onExportPlayers: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Player name") },
            modifier = Modifier.fillMaxWidth(),
            isError = showError && name.isBlank()
        )

        OutlinedTextField(
            value = jerseyNumber,
            onValueChange = onJerseyNumberChange,
            label = { Text("Jersey number (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (showError) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(
                    text = "Player Name is required.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddPlayer,
                modifier = Modifier.weight(1f)
            ) {
                Text("Add player")
            }

            OutlinedButton(
                onClick = onExportPlayers,
                modifier = Modifier.weight(1f)
            ) {
                Text("Export roster CSV")
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
    onSaveUpdate: (PlayerEntity) -> Unit,
    onCancelEdit: () -> Unit,
    onStartEdit: (PlayerEntity) -> Unit,
    onDeletePlayer: (Long) -> Unit,
) {
    if (players.isEmpty()) {
        EmptyMessage("No players yet. Add your roster first.")
    } else {
        players.forEach { player ->
            if (editingPlayer?.id == player.id) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = onEditNameChange,
                            label = { Text("Player name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editJerseyNumber,
                            onValueChange = onEditJerseyNumberChange,
                            label = { Text("Jersey number (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSaveUpdate(player) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save")
                            }
                            TextButton(
                                onClick = onCancelEdit,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = player.displayLabel(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row {
                            TextButton(
                                onClick = { onStartEdit(player) }
                            ) {
                                Text("Edit")
                            }
                            TextButton(
                                onClick = { onDeletePlayer(player.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
