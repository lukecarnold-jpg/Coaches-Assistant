package com.example.basketballrotation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun GameNotesPanel(
    onClick: () -> Unit
) {
    SectionCard(title = "Coaching Notes") {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📝 Open & Edit Notes")
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
                Text("Coaching Notes")
                OutlinedButton(onClick = onExport) {
                    Text("📤 Export TXT")
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
                    placeholder = { Text("Write strategy or observations here...") },
                    label = { Text("Notes (Keyboard/Pen support)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
