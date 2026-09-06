package com.example.basketballrotation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClearAllData: () -> Unit,
    geminiApiKey: String = "",
    hasBuiltInApiKey: Boolean = false,
    onSaveGeminiApiKey: (String) -> Unit = {},
    onClearGeminiApiKey: () -> Unit = {},
) {
    val layout = LocalResponsiveLayout.current
    var showConfirm by remember { mutableStateOf(value = false) }
    var apiKeyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var apiKeyVisible by remember { mutableStateOf(value = false) }

    Column(
        modifier = modifier.padding(layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        SectionCard(title = "AI training assistant") {
            Text(
                when {
                    geminiApiKey.isNotBlank() -> "A Gemini API key is saved on this device. AI drill generation is enabled."
                    hasBuiltInApiKey -> "Using the GEMINI_API_KEY built into this app. AI drill generation is enabled."
                    else -> "No Gemini API key configured. Add one below to enable AI drill generation on the Training tab."
                }
            )
            Spacer(Modifier.height(layout.smallGap))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(layout.smallGap))
            Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Button(
                    onClick = { onSaveGeminiApiKey(apiKeyInput.trim()) },
                    enabled = apiKeyInput.trim() != geminiApiKey,
                ) {
                    Text("Save")
                }
                if (geminiApiKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            apiKeyInput = ""
                            onClearGeminiApiKey()
                        },
                    ) {
                        Text("Remove saved key")
                    }
                }
            }
            Spacer(Modifier.height(layout.smallGap))
            Text(
                "Get a free key from Google AI Studio, then paste it here. The key is stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(title = "Data management") {
            Text("Clear all local data including players, games, and events. This action cannot be undone.")
            Spacer(Modifier.height(layout.smallGap))
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
