package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * Edits a game's coaching notes. What's typed survives rotation, and closing the dialog by tapping
 * outside it or pressing Back saves it (only Cancel discards it), so notes aren't lost to a stray
 * tap. Export saves first, so the file has what's on screen.
 */
@Composable
fun CoachingNotesDialog(
    notes: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onExport: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(notes) }
    val layout = LocalResponsiveLayout.current

    AlertDialog(
        onDismissRequest = { onSave(text) },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.coaching_notes))
                OutlinedButton(onClick = { onExport(text) }) {
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
