package com.lukearnold.coachesassistant.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lukearnold.coachesassistant.BuildConfig
import com.lukearnold.coachesassistant.ai.AiProvider
import com.lukearnold.coachesassistant.ai.CustomEndpointConfig
import com.lukearnold.coachesassistant.ai.ACCESS_LOCAL_NETWORK
import com.lukearnold.coachesassistant.ai.endpointUrlProblem
import com.lukearnold.coachesassistant.ai.hasLocalNetworkPermission
import com.lukearnold.coachesassistant.ai.needsLocalNetworkPermission
import java.time.LocalDate
import com.lukearnold.coachesassistant.data.Sport

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClearAllData: () -> Unit,
    onClearSelectedData: (players: Boolean, games: Boolean, events: Boolean) -> Unit = { _, _, _ -> },
    apiKeys: Map<AiProvider, String> = emptyMap(),
    selectedProvider: AiProvider = AiProvider.GEMINI,
    hasBuiltInApiKey: Boolean = false,
    customEndpoint: CustomEndpointConfig = CustomEndpointConfig(),
    onSelectProvider: (AiProvider) -> Unit = {},
    onSaveApiKey: (AiProvider, String) -> Unit = { _, _ -> },
    onClearApiKey: (AiProvider) -> Unit = {},
    onSaveCustomEndpoint: (CustomEndpointConfig) -> Unit = {},
    currentSport: Sport? = null,
    onSwitchSport: () -> Unit = {},
    onBackup: (Uri) -> Unit = {},
    onRestore: (Uri) -> Unit = {},
    keepScreenOnWhileClockRuns: Boolean = true,
    onKeepScreenOnWhileClockRunsChange: (Boolean) -> Unit = {},
) {
    val layout = LocalResponsiveLayout.current
    var showConfirm by remember { mutableStateOf(value = false) }
    var showRestoreConfirm by remember { mutableStateOf(value = false) }
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onBackup) }
    // Any file type: backups saved to cloud drives or downloads don't always keep a JSON type,
    // and the contents are checked before anything is restored.
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onRestore) }
    var showDeleteOptions by remember { mutableStateOf(value = false) }
    var deletePlayers by remember { mutableStateOf(value = true) }
    var deleteGames by remember { mutableStateOf(value = true) }
    var deleteEvents by remember { mutableStateOf(value = true) }
    val savedKey = apiKeys[selectedProvider].orEmpty()
    var apiKeyInput by remember(selectedProvider, savedKey) { mutableStateOf(savedKey) }
    var apiKeyVisible by remember { mutableStateOf(value = false) }
    var endpointUrlInput by remember(customEndpoint.endpointUrl) { mutableStateOf(customEndpoint.endpointUrl) }
    var modelInput by remember(customEndpoint.model) { mutableStateOf(customEndpoint.model) }
    val isOther = selectedProvider == AiProvider.OTHER
    val endpointProblem = if (isOther) endpointUrlProblem(endpointUrlInput) else null
    val context = LocalContext.current
    val endpointNeedsLocalNetwork = endpointProblem == null && needsLocalNetworkPermission(
        selectedProvider,
        CustomEndpointConfig(endpointUrlInput, modelInput),
        Build.VERSION.SDK_INT,
    )
    // The result needs no handling here: Generate on the Training tab asks again if it's still needed.
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (currentSport != null) {
            SectionCard(title = "Sport") {
                Text("Currently coaching ${currentSport.emoji} ${currentSport.displayName}.")
                Spacer(Modifier.height(layout.smallGap))
                Button(onClick = onSwitchSport) {
                    Text("Switch sport")
                }
            }
        }

        SectionCard(title = "Display") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep screen on while the clock runs")
                    Text(
                        "Stops the phone locking mid-game. The normal screen timeout returns when the clock stops.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = keepScreenOnWhileClockRuns,
                    onCheckedChange = onKeepScreenOnWhileClockRunsChange,
                )
            }
        }

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
                    isOther ->
                        "No API key saved. Hosted providers need one; a local server (such as Ollama) usually doesn't."
                    else ->
                        "No ${selectedProvider.displayName} API key configured. Add one below to enable AI drill generation on the Training tab."
                }
            )
            Spacer(Modifier.height(layout.smallGap))

            if (isOther) {
                OutlinedTextField(
                    value = endpointUrlInput,
                    onValueChange = { endpointUrlInput = it },
                    label = { Text("Endpoint URL") },
                    placeholder = { Text("https://.../v1/chat/completions") },
                    singleLine = true,
                    isError = endpointProblem != null,
                    supportingText = when {
                        endpointProblem != null -> { { Text(endpointProblem) } }
                        endpointNeedsLocalNetwork -> {
                            { Text("This server is on your local network, so Android will ask to allow local network access.") }
                        }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(layout.smallGap))
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Model name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(layout.smallGap))
            }

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
                val endpointChanged = isOther &&
                    ((endpointUrlInput.trim() != customEndpoint.endpointUrl) || (modelInput.trim() != customEndpoint.model))
                Button(
                    onClick = {
                        onSaveApiKey(selectedProvider, apiKeyInput.trim())
                        if (isOther) {
                            onSaveCustomEndpoint(CustomEndpointConfig(endpointUrlInput.trim(), modelInput.trim()))
                            // Ask now, while the user is setting it up, rather than on first use.
                            if (endpointNeedsLocalNetwork && !context.hasLocalNetworkPermission()) {
                                localNetworkPermissionLauncher.launch(ACCESS_LOCAL_NETWORK)
                            }
                        }
                    },
                    enabled = ((apiKeyInput.trim() != savedKey) || endpointChanged) && (endpointProblem == null),
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

        SectionCard(title = "Backup") {
            if (BuildConfig.LEGACY_TRANSITION_BUILD) {
                Text(
                    "This is the old app, updated so you can move your data to the new Coaches Assistant " +
                        "app: tap \"Back up all data\" below, then open the new app and use \"Restore from " +
                        "backup\" in its Settings. Re-enter your AI API keys there afterwards.",
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(layout.smallGap))
            }
            Text(
                "Save every sport's players and games to a file, e.g. to move to a new phone. " +
                    "API keys aren't included; add them again after restoring.",
            )
            Spacer(Modifier.height(layout.smallGap))
            Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Button(onClick = { backupLauncher.launch("coaches-assistant-backup-${LocalDate.now()}.json") }) {
                    Text("Back up all data")
                }
                OutlinedButton(onClick = { showRestoreConfirm = true }) {
                    Text("Restore from backup")
                }
            }
        }

        SectionCard(title = "Data management") {
            Text(
                "Delete ${currentSport?.displayName ?: "this sport"}'s players, games, or events, or delete " +
                    "everything for every sport at once. These actions cannot be undone.",
            )
            Spacer(Modifier.height(layout.smallGap))
            Button(
                onClick = { showDeleteOptions = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete data")
            }
        }

        SectionCard(title = "Exports") {
            Text("To view exported CSV files, use your device's file manager (e.g., Files or Downloads app).")
        }

        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text("Restore from backup?") },
                text = {
                    Text(
                        "The backup replaces all current players, games, and events for every sport. " +
                            "This can't be undone, so back up first if you might want today's data.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreConfirm = false
                            restoreLauncher.launch(arrayOf("*/*"))
                        },
                    ) {
                        Text("Choose backup file", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("Confirm deletion") },
                text = {
                    Text("Are you sure you want to delete all data? This removes every player and game for every sport, not just ${currentSport?.displayName ?: "this one"}.")
                },
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

        if (showDeleteOptions) {
            AlertDialog(
                onDismissRequest = { showDeleteOptions = false },
                title = { Text("Delete local data") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                        Text("Select the ${currentSport?.displayName ?: "current sport"} data to delete. Other sports are not affected.")
                        DataDeletionOption(
                            label = "Players",
                            checked = deletePlayers,
                            onCheckedChange = { deletePlayers = it },
                        )
                        DataDeletionOption(
                            label = "Games",
                            checked = deleteGames,
                            onCheckedChange = { deleteGames = it },
                        )
                        DataDeletionOption(
                            label = "Events",
                            checked = deleteEvents,
                            onCheckedChange = { deleteEvents = it },
                        )
                    }
                },
                confirmButton = {
                    Column {
                        TextButton(
                            onClick = {
                                showDeleteOptions = false
                                showConfirm = true
                            },
                        ) {
                            Text("Delete all data (every sport)", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(
                            onClick = {
                                onClearSelectedData(deletePlayers, deleteGames, deleteEvents)
                                showDeleteOptions = false
                            },
                            enabled = deletePlayers || deleteGames || deleteEvents,
                        ) {
                            Text("Delete selected", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteOptions = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun DataDeletionOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
