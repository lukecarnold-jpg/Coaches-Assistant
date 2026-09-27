package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun GameNotesPanel(
    onClick: () -> Unit
) {
    SectionCard(title = stringResource(R.string.coaching_notes)) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.open_edit_notes))
        }
    }
}

@Composable
fun CoachingNotesDialog(
    notes: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onExport: () -> Unit
) {
    var text by remember { mutableStateOf(notes) }
    val layout = LocalResponsiveLayout.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.coaching_notes))
                OutlinedButton(onClick = onExport) {
                    Text(stringResource(R.string.export_txt))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = layout.notesMinHeight),
                    placeholder = { Text(stringResource(R.string.write_strategy_or_observations_here)) },
                    label = { Text(stringResource(R.string.notes_keyboard_pen_support)) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) {
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
