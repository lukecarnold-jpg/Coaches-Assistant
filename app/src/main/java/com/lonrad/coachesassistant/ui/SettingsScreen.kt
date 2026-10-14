package com.lonrad.coachesassistant.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lonrad.coachesassistant.BuildConfig
import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.ai.ACCESS_LOCAL_NETWORK
import com.lonrad.coachesassistant.ai.AiProvider
import com.lonrad.coachesassistant.ai.CustomEndpointConfig
import com.lonrad.coachesassistant.ai.endpointUrlProblem
import com.lonrad.coachesassistant.ai.hasLocalNetworkPermission
import com.lonrad.coachesassistant.ai.needsLocalNetworkPermission
import com.lonrad.coachesassistant.data.Sport
import java.time.LocalDate

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
    /** Each provider's model chosen here, or "" for its default (see AiProvider.defaultModel). */
    modelOverrides: Map<AiProvider, String> = emptyMap(),
    onSaveModel: (AiProvider, String) -> Unit = { _, _ -> },
    currentSport: Sport? = null,
    onSwitchSport: () -> Unit = {},
    onBackup: (Uri) -> Unit = {},
    onRestore: (Uri) -> Unit = {},
    keepScreenOnWhileClockRuns: Boolean = true,
    onKeepScreenOnWhileClockRunsChange: (Boolean) -> Unit = {},
    clockNotification: Boolean = false,
    onClockNotificationChange: (Boolean) -> Unit = {},
    /** The current sport's minimum players to field a team (see SportConfig.minimumOnCourt). */
    minimumOnCourt: Int? = null,
    onMinimumOnCourtChange: (Int) -> Unit = {},
    suggestSubs: Boolean = true,
    onSuggestSubsChange: (Boolean) -> Unit = {},
    notifySubs: Boolean = false,
    onNotifySubsChange: (Boolean) -> Unit = {},
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
    val savedModelOverride = modelOverrides[selectedProvider].orEmpty()
    var modelOverrideInput by remember(selectedProvider, savedModelOverride) { mutableStateOf(savedModelOverride) }
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
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (currentSport != null) {
            SectionCard(title = stringResource(R.string.sport)) {
                Text(stringResource(R.string.currently_coaching, currentSport.emoji, stringResource(currentSport.label)))
                Spacer(Modifier.height(layout.smallGap))
                Button(onClick = onSwitchSport) {
                    Text(stringResource(R.string.switch_sport))
                }
                if (minimumOnCourt != null) {
                    Spacer(Modifier.height(layout.sectionGap))
                    MinimumOnCourtSetting(currentSport, minimumOnCourt, onMinimumOnCourtChange)
                }
            }
        }

        SectionCard(title = stringResource(R.string.substitutions_settings)) {
            SettingSwitch(
                title = stringResource(R.string.suggest_substitutions_setting),
                description = stringResource(R.string.suggest_substitutions_setting_description),
                checked = suggestSubs,
                onCheckedChange = onSuggestSubsChange,
            )
            Spacer(Modifier.height(layout.smallGap))
            SettingSwitch(
                title = stringResource(R.string.notify_substitutions_setting),
                description = stringResource(R.string.notify_substitutions_setting_description),
                checked = notifySubs && suggestSubs,
                onCheckedChange = onNotifySubsChange,
                enabled = suggestSubs,
            )
        }

        SectionCard(title = stringResource(R.string.display)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.keep_screen_on_while_the_clock_runs))
                    Text(
                        stringResource(R.string.stops_the_phone_locking_mid_game_the_normal_scre),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = keepScreenOnWhileClockRuns,
                    onCheckedChange = onKeepScreenOnWhileClockRunsChange,
                )
            }
            Spacer(Modifier.height(layout.smallGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.game_clock_notification_setting))
                    Text(
                        stringResource(R.string.game_clock_notification_setting_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = clockNotification, onCheckedChange = onClockNotificationChange)
            }
        }

        SectionCard(title = stringResource(R.string.language_setting)) {
            LanguageSetting()
        }

        SectionCard(title = stringResource(R.string.ai_training_assistant)) {
            Text(stringResource(R.string.choose_which_ai_generates_training_drills_then_a))
            Spacer(Modifier.height(layout.smallGap))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
                verticalArrangement = Arrangement.spacedBy(layout.smallGap),
            ) {
                AiProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = provider == selectedProvider,
                        onClick = { onSelectProvider(provider) },
                        label = { Text(stringResource(provider.label)) },
                    )
                }
            }
            Spacer(Modifier.height(layout.smallGap))

            Text(
                when {
                    savedKey.isNotBlank() ->
                        stringResource(R.string.api_key_active, stringResource(selectedProvider.label))

                    (selectedProvider == AiProvider.GEMINI) && hasBuiltInApiKey ->
                        stringResource(R.string.using_the_gemini_api_key_built_into_this_app_ai)

                    isOther ->
                        stringResource(R.string.no_api_key_saved_hosted_providers_need_one_a_loc)

                    else ->
                        stringResource(R.string.api_key_missing, stringResource(selectedProvider.label))
                },
            )
            Spacer(Modifier.height(layout.smallGap))

            if (isOther) {
                OutlinedTextField(
                    value = endpointUrlInput,
                    onValueChange = { endpointUrlInput = it },
                    label = { Text(stringResource(R.string.endpoint_url)) },
                    placeholder = { Text("https://.../v1/chat/completions") },
                    singleLine = true,
                    isError = endpointProblem != null,
                    supportingText = when {
                        endpointProblem != null -> {
                            { Text(stringResource(endpointProblem)) }
                        }

                        endpointNeedsLocalNetwork -> {
                            { Text(stringResource(R.string.this_server_is_on_your_local_network_so_android)) }
                        }

                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(layout.smallGap))
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text(stringResource(R.string.model_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(layout.smallGap))
            }
            selectedProvider.defaultModel?.let { defaultModel ->
                OutlinedTextField(
                    value = modelOverrideInput,
                    onValueChange = { modelOverrideInput = it },
                    label = { Text(stringResource(R.string.model_name)) },
                    placeholder = { Text(defaultModel) },
                    supportingText = { Text(stringResource(R.string.model_default_hint, defaultModel)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(layout.smallGap))
            }

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(stringResource(R.string.provider_api_key, stringResource(selectedProvider.label))) },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (apiKeyVisible) stringResource(R.string.hide_api_key) else stringResource(R.string.show_api_key),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(layout.smallGap))
            Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                val endpointChanged = isOther &&
                    ((endpointUrlInput.trim() != customEndpoint.endpointUrl) || (modelInput.trim() != customEndpoint.model))
                val modelChanged = !isOther && modelOverrideInput.trim() != savedModelOverride
                Button(
                    onClick = {
                        onSaveApiKey(selectedProvider, apiKeyInput.trim())
                        if (modelChanged) onSaveModel(selectedProvider, modelOverrideInput.trim())
                        if (isOther) {
                            onSaveCustomEndpoint(CustomEndpointConfig(endpointUrlInput.trim(), modelInput.trim()))
                            // Ask now, while the user is setting it up, rather than on first use.
                            if (endpointNeedsLocalNetwork && !context.hasLocalNetworkPermission()) {
                                localNetworkPermissionLauncher.launch(ACCESS_LOCAL_NETWORK)
                            }
                        }
                    },
                    enabled = ((apiKeyInput.trim() != savedKey) || endpointChanged || modelChanged) && (endpointProblem == null),
                ) {
                    Text(stringResource(R.string.save))
                }
                if (savedKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            apiKeyInput = ""
                            onClearApiKey(selectedProvider)
                        },
                    ) {
                        Text(stringResource(R.string.remove_saved_key))
                    }
                }
            }
            Spacer(Modifier.height(layout.smallGap))
            Text(
                stringResource(R.string.key_hint_stored_on_device, stringResource(selectedProvider.keyHint)),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(title = stringResource(R.string.backup)) {
            if (BuildConfig.LEGACY_TRANSITION_BUILD) {
                Text(
                    stringResource(R.string.this_is_the_old_app_updated_so_you_can_move_your),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(layout.smallGap))
            }
            Text(
                stringResource(R.string.save_every_sport_s_players_and_games_to_a_file_e),
            )
            Spacer(Modifier.height(layout.smallGap))
            Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Button(onClick = { backupLauncher.launch("coaches-assistant-backup-${LocalDate.now()}.json") }) {
                    Text(stringResource(R.string.back_up_all_data))
                }
                OutlinedButton(onClick = { showRestoreConfirm = true }) {
                    Text(stringResource(R.string.restore_from_backup))
                }
            }
        }

        SectionCard(title = stringResource(R.string.data_management)) {
            Text(
                if (currentSport != null) {
                    stringResource(R.string.data_management_description, stringResource(currentSport.label))
                } else {
                    stringResource(R.string.data_management_description_no_sport)
                },
            )
            Spacer(Modifier.height(layout.smallGap))
            Button(
                onClick = { showDeleteOptions = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.delete_data))
            }
        }

        SectionCard(title = stringResource(R.string.exports)) {
            Text(stringResource(R.string.to_view_exported_csv_files_use_your_device_s_fil))
        }

        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text(stringResource(R.string.restore_from_backup_question)) },
                text = {
                    Text(
                        stringResource(R.string.the_backup_replaces_all_current_players_games_an),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRestoreConfirm = false
                            restoreLauncher.launch(arrayOf("*/*"))
                        },
                    ) {
                        Text(stringResource(R.string.choose_backup_file), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text(stringResource(R.string.confirm_deletion)) },
                text = {
                    Text(
                        if (currentSport != null) {
                            stringResource(R.string.delete_all_confirm, stringResource(currentSport.label))
                        } else {
                            stringResource(R.string.delete_all_confirm_no_sport)
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearAllData()
                            showConfirm = false
                        },
                    ) {
                        Text(stringResource(R.string.delete_everything), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfirm = false },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        if (showDeleteOptions) {
            AlertDialog(
                onDismissRequest = { showDeleteOptions = false },
                title = { Text(stringResource(R.string.delete_local_data)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                        Text(
                            if (currentSport != null) {
                                stringResource(R.string.delete_selected_prompt, stringResource(currentSport.label))
                            } else {
                                stringResource(R.string.delete_selected_prompt_no_sport)
                            },
                        )
                        DataDeletionOption(
                            label = stringResource(R.string.players),
                            checked = deletePlayers,
                            onCheckedChange = { deletePlayers = it },
                        )
                        DataDeletionOption(
                            label = stringResource(R.string.games),
                            checked = deleteGames,
                            onCheckedChange = { deleteGames = it },
                        )
                        DataDeletionOption(
                            label = stringResource(R.string.events),
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
                            Text(stringResource(R.string.delete_all_data_every_sport), color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(
                            onClick = {
                                onClearSelectedData(deletePlayers, deleteGames, deleteEvents)
                                showDeleteOptions = false
                            },
                            enabled = deletePlayers || deleteGames || deleteEvents,
                        ) {
                            Text(stringResource(R.string.delete_selected), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteOptions = false }) {
                        Text(stringResource(R.string.cancel))
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

/**
 * The fewest players [sport]'s team can take the field with: a − / + stepper from 1 to a full
 * side, starting at the sport's rules, with a way back to them.
 */
@Composable
private fun MinimumOnCourtSetting(sport: Sport, minimum: Int, onChange: (Int) -> Unit) {
    val config = sport.config
    Text(stringResource(R.string.minimum_on_court_title), style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val fewer = stringResource(R.string.fewer)
        val more = stringResource(R.string.more)
        IconButton(onClick = { onChange(minimum - 1) }, enabled = minimum > 1, modifier = Modifier.semantics { contentDescription = fewer }) {
            Text("−", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
        }
        Text(
            pluralStringResource(R.plurals.minimum_on_court_value, minimum, minimum, config.onCourtSize),
            style = MaterialTheme.typography.bodyLarge,
        )
        IconButton(onClick = { onChange(minimum + 1) }, enabled = minimum < config.onCourtSize, modifier = Modifier.semantics { contentDescription = more }) {
            Text("+", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
        }
    }
    Text(
        stringResource(R.string.minimum_on_court_explanation, stringResource(sport.label), config.minimumOnCourt),
        style = MaterialTheme.typography.bodySmall,
    )
    if (minimum != config.minimumOnCourt) {
        TextButton(onClick = { onChange(config.minimumOnCourt) }) {
            Text(stringResource(R.string.minimum_on_court_reset, config.minimumOnCourt))
        }
    }
}

/** A setting with a title, a short explanation, and a switch. */
@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Chips for the app's language (see AppLanguage); choosing one switches the app straight away. */
@Composable
private fun LanguageSetting() {
    val layout = LocalResponsiveLayout.current
    val current = remember { AppLanguage.current() }
    Text(stringResource(R.string.language_setting_description))
    Spacer(Modifier.height(layout.smallGap))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
        verticalArrangement = Arrangement.spacedBy(layout.smallGap),
    ) {
        AppLanguage.entries.forEach { language ->
            FilterChip(
                selected = language == current,
                onClick = { if (language != current) AppLanguage.apply(language) },
                label = { Text(language.nativeName ?: stringResource(R.string.language_system_default)) },
            )
        }
    }
}
