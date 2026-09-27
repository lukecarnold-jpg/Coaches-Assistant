package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lukearnold.coachesassistant.data.*

@Composable
fun AttendanceDialog(
    players: List<PlayerEntity>,
    absentPlayerIds: List<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val currentAbsent = remember { mutableStateListOf<Long>().apply { addAll(absentPlayerIds) } }
    val layout = LocalResponsiveLayout.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_attendance)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.smallGap)
            ) {
                Text(stringResource(R.string.select_players_who_are_absent_for_this_game))
                players.forEach { player ->
                    val isAbsent = currentAbsent.contains(player.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = isAbsent, role = Role.Checkbox) { absent ->
                                if (absent) currentAbsent.add(player.id) else currentAbsent.remove(player.id)
                            }
                            .padding(vertical = layout.tinyGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isAbsent, onCheckedChange = null)
                        Spacer(Modifier.width(layout.smallGap))
                        Text(player.displayLabel())
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(currentAbsent.toList()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EventEditDialog(
    event: GameEventEntity,
    onDismiss: () -> Unit,
    infraction: Infraction = Infraction.FOUL,
    onConfirm: (GameEventEntity) -> Unit,
) {
    var points by remember { mutableStateOf(event.points.toString()) }
    var note by remember { mutableStateOf(event.note) }
    val layout = LocalResponsiveLayout.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_event)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text(stringResource(R.string.event_type, event.type))
                if (
                    (event.type == EventType.SCORE.name) ||
                    (event.type == EventType.OPPONENT_SCORE.name) ||
                    (event.type == EventType.FOUL.name)
                ) {
                    OutlinedTextField(
                        value = points,
                        onValueChange = { points = it },
                        label = { Text(if (event.type == EventType.FOUL.name) stringResource(R.string.infraction_count_adjustment, stringResource(infraction.label)) else stringResource(R.string.points)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(event.copy(points = points.toIntOrNull() ?: event.points, note = note))
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
