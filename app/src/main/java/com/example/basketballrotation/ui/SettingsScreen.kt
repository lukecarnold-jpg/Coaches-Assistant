package com.example.basketballrotation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClearAllData: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(value = false) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        SectionCard(title = "Data management") {
            Text("Clear all local data including players, games, and events. This action cannot be undone.")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Clear all data")
            }
        }

        SectionCard(title = "Exports") {
            Text("To view exported CSV files, use your device's file manager (e.g., Files or Downloads app).")
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("Confirm deletion") },
                text = { Text("Are you sure you want to delete all data? This will remove all players and games.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearAllData()
                            showConfirm = false
                        },
                    ) {
                        Text("Delete everything", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfirm = false },
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
