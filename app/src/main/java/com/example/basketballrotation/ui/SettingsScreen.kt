package com.example.basketballrotation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
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
import com.example.basketballrotation.ai.AiProvider

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClearAllData: () -> Unit,
    apiKeys: Map<AiProvider, String> = emptyMap(),
    selectedProvider: AiProvider = AiProvider.GEMINI,
    hasBuiltInApiKey: Boolean = false,
    onSelectProvider: (AiProvider) -> Unit = {},
    onSaveApiKey: (AiProvider, String) -> Unit = { _, _ -> },
    onClearApiKey: (AiProvider) -> Unit = {},
) {
    val layout = LocalResponsiveLayout.current
    var showConfirm by remember { mutableStateOf(value = false) }
    val savedKey = apiKeys[selectedProvider].orEmpty()
    var apiKeyInput by remember(selectedProvider, savedKey) { mutableStateOf(savedKey) }
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
            Text("Choose which AI generates training drills, then add its API key. Each provider requires its own key — the app does not support signing in with a Gemini, Claude, or ChatGPT account.")
            Spacer(Modifier.height(layout.smallGap))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
                verticalArrangement = Arrangement.spacedBy(layout.smallGap),
            ) {
                AiProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = provider == selectedProvider,
                        onClick = { onSelectProvider(provider) },
                        label = { Text(provider.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(layout.smallGap))

            Text(
                when {
                    savedKey.isNotBlank() ->
                        "Your saved ${selectedProvider.displayName} API key is active. AI drill generation is enabled."
                    (selectedProvider == AiProvider.GEMINI) && hasBuiltInApiKey ->
                        "Using the GEMINI_API_KEY built into this app. AI drill generation is enabled."
                    else ->
                        "No ${selectedProvider.displayName} API key configured. Add one below to enable AI drill generation on the Training tab."
                }
            )
            Spacer(Modifier.height(layout.smallGap))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("${selectedProvider.displayName} API key") },
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
                    onClick = { onSaveApiKey(selectedProvider, apiKeyInput.trim()) },
                    enabled = apiKeyInput.trim() != savedKey,
                ) {
                    Text("Save")
                }
                if (savedKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            apiKeyInput = ""
                            onClearApiKey(selectedProvider)
                        },
                    ) {
                        Text("Remove saved key")
                    }
                }
            }
            Spacer(Modifier.height(layout.smallGap))
            Text(
                "${selectedProvider.getKeyHint} The key is stored only on this device.",
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
