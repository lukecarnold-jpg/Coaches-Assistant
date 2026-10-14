package com.lukearnold.coachesassistant.ui

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lukearnold.coachesassistant.BuildConfig
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.ai.ACCESS_LOCAL_NETWORK
import com.lukearnold.coachesassistant.ai.DRILL_REPORT_EMAIL
import com.lukearnold.coachesassistant.ai.Drill
import com.lukearnold.coachesassistant.ai.DrillReportReason
import com.lukearnold.coachesassistant.ai.buildDrillReport
import com.lukearnold.coachesassistant.ai.toEmailIntent
import com.lukearnold.coachesassistant.data.Sport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    sport: Sport,
    trainingViewModel: TrainingViewModel = viewModel(),
) {
    val layout = LocalResponsiveLayout.current
    val uiState by trainingViewModel.uiState.collectAsStateWithLifecycle()
    val ageGroups = listOf(
        R.string.ages_5_6,
        R.string.under_8_s,
        R.string.under_10_s,
        R.string.under_12_s,
        R.string.under_14_s,
        R.string.under_16_s,
        R.string.under_18_s,
    )

    var expanded by remember { mutableStateOf(value = false) }
    // Kept as the resource, so the choice survives a change of language; the prompt gets its text.
    var selectedAgeGroup by rememberSaveable { mutableIntStateOf(ageGroups.first()) }
    val selectedAgeGroupText = stringResource(selectedAgeGroup)
    val languageTag = stringResource(R.string.content_language_tag)

    // A local AI server needs local network access on Android 17+; ask for it first if needed.
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) trainingViewModel.generateDrills(sport, selectedAgeGroupText, languageTag) else trainingViewModel.reportLocalNetworkDenied()
    }
    val generate = {
        if (trainingViewModel.localNetworkPermissionNeeded()) {
            localNetworkPermissionLauncher.launch(ACCESS_LOCAL_NETWORK)
        } else {
            trainingViewModel.generateDrills(sport, selectedAgeGroupText, languageTag)
        }
    }

    if (isExpanded) {
        Column(modifier = modifier.padding(layout.screenPadding)) {
            Text(
                text = stringResource(R.string.training_drills),
                modifier = Modifier.fillMaxWidth().padding(bottom = layout.sectionGap),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
                ) {
                    TrainingSelectionPanel(
                        selectedAgeGroup = selectedAgeGroup,
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        ageGroups = ageGroups,
                        onAgeGroupSelect = {
                            selectedAgeGroup = it
                            expanded = false
                        },
                        onGenerateClick = generate,
                        isLoading = uiState is TrainingUiState.Loading,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
                ) {
                    TrainingResultsPanel(
                        uiState = uiState,
                        sport = sport,
                        onCancel = trainingViewModel::cancelGeneration,
                        onRemoveDrill = trainingViewModel::removeDrill,
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(layout.screenPadding),
            verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
        ) {
            Text(
                text = stringResource(R.string.training_drills),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            TrainingSelectionPanel(
                selectedAgeGroup = selectedAgeGroup,
                expanded = expanded,
                onExpandedChange = { expanded = it },
                ageGroups = ageGroups,
                onAgeGroupSelect = {
                    selectedAgeGroup = it
                    expanded = false
                },
                onGenerateClick = generate,
                isLoading = uiState is TrainingUiState.Loading,
            )

            TrainingResultsPanel(
                uiState = uiState,
                sport = sport,
                onCancel = trainingViewModel::cancelGeneration,
                onRemoveDrill = trainingViewModel::removeDrill,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingSelectionPanel(
    @StringRes selectedAgeGroup: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    ageGroups: List<Int>,
    onAgeGroupSelect: (Int) -> Unit,
    onGenerateClick: () -> Unit,
    isLoading: Boolean,
) {
    val layout = LocalResponsiveLayout.current

    SectionCard(title = stringResource(R.string.select_age_group)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = stringResource(selectedAgeGroup),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.age_group)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                ageGroups.forEach { age ->
                    DropdownMenuItem(
                        text = { Text(stringResource(age)) },
                        onClick = { onAgeGroupSelect(age) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }

        Spacer(Modifier.height(layout.smallGap))

        Button(
            onClick = onGenerateClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            Text(stringResource(R.string.generate_ai_drills))
        }
    }
}

/** [onRemoveDrill] takes a drill off the list once the coach has reported it. */
@Composable
fun TrainingResultsPanel(uiState: TrainingUiState, sport: Sport, onCancel: () -> Unit, onRemoveDrill: (Drill) -> Unit) {
    when (uiState) {
        is TrainingUiState.Idle -> {
            EmptyMessage(stringResource(R.string.training_empty, stringResource(sport.label)))
        }

        is TrainingUiState.Loading -> {
            // A request can take a minute or more (a local server loading its model), so it can be
            // called off rather than waited out.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LocalResponsiveLayout.current.smallGap),
            ) {
                CircularProgressIndicator()
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }

        is TrainingUiState.Success -> {
            val context = LocalContext.current
            var reporting by remember { mutableStateOf<Drill?>(null) }

            uiState.drills.forEach { drill ->
                DrillCard(drill, onReport = { reporting = drill })
            }

            reporting?.let { drill ->
                ReportDrillDialog(
                    onDismiss = { reporting = null },
                    onSend = { reason, details ->
                        val report = buildDrillReport(drill, uiState.source, reason, details, BuildConfig.VERSION_NAME, Build.VERSION.RELEASE)
                        val opened = try {
                            context.startActivity(report.toEmailIntent())
                            true
                        } catch (_: ActivityNotFoundException) {
                            false
                        }
                        if (opened) {
                            reporting = null
                            onRemoveDrill(drill)
                        }
                        opened
                    },
                )
            }
        }

        is TrainingUiState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    text = uiState.message.asString(),
                    modifier = Modifier.padding(LocalResponsiveLayout.current.cardPadding),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun DrillCard(drill: Drill, onReport: () -> Unit) {
    val layout = LocalResponsiveLayout.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(layout.smallGap),
        ) {
            Text(
                text = drill.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = stringResource(R.string.description),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = drill.description,
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = layout.tinyGap))

            Text(
                text = stringResource(R.string.teaching_points),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = drill.teachingPoints,
                style = MaterialTheme.typography.bodySmall,
            )

            TextButton(onClick = onReport, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(layout.tinyGap))
                Text(stringResource(R.string.report_drill))
            }
        }
    }
}

/**
 * Asks why a drill is being reported, as Google Play requires for AI-generated content. [onSend]
 * opens the email app with the report and returns false if there isn't one, in which case the
 * dialog stays open and gives the address to write to instead.
 */
@Composable
fun ReportDrillDialog(onDismiss: () -> Unit, onSend: (DrillReportReason, String) -> Boolean) {
    val layout = LocalResponsiveLayout.current
    var reason by rememberSaveable { mutableStateOf<DrillReportReason?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    var noEmailApp by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_drill_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.smallGap),
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    DrillReportReason.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = reason == option, role = Role.RadioButton) { reason = option }
                                .padding(vertical = layout.tinyGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = reason == option, onClick = null)
                            Spacer(Modifier.width(layout.smallGap))
                            Text(stringResource(option.label))
                        }
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(stringResource(R.string.report_details_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.report_drill_explanation),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (noEmailApp) {
                    // Selectable, so the address can be copied into whatever the coach uses instead.
                    SelectionContainer {
                        Text(
                            text = stringResource(R.string.report_no_email_app, DRILL_REPORT_EMAIL),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { reason?.let { noEmailApp = !onSend(it, details) } },
                enabled = reason != null,
            ) {
                Text(stringResource(R.string.send_report))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
