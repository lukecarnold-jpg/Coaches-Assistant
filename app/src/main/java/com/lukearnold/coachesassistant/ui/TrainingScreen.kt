package com.lukearnold.coachesassistant.ui

import androidx.annotation.StringRes
import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lukearnold.coachesassistant.ai.ACCESS_LOCAL_NETWORK
import com.lukearnold.coachesassistant.ai.Drill
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
    val uiState by trainingViewModel.uiState.collectAsState()
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

    // A local AI server needs local network access on Android 17+; ask for it first if needed.
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) trainingViewModel.generateDrills(sport, selectedAgeGroupText) else trainingViewModel.reportLocalNetworkDenied()
    }
    val generate = {
        if (trainingViewModel.localNetworkPermissionNeeded()) {
            localNetworkPermissionLauncher.launch(ACCESS_LOCAL_NETWORK)
        } else {
            trainingViewModel.generateDrills(sport, selectedAgeGroupText)
        }
    }

    if (isExpanded) {
        Column(modifier = modifier.padding(layout.screenPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = layout.sectionGap),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.training_drills),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(layout.cardCornerRadius),
                ) {
                    Text(
                        stringResource(R.string.coming_soon),
                        modifier = Modifier.padding(horizontal = layout.smallGap, vertical = layout.tinyGap),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
                ) {
                    SectionCard(title = stringResource(R.string.coming_soon_title)) {
                        Text(
                            stringResource(R.string.we_are_currently_developing_interactive_training),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

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
                    TrainingResultsPanel(uiState = uiState, sport = sport)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.training_drills),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(layout.cardCornerRadius),
                ) {
                    Text(
                        stringResource(R.string.coming_soon),
                        modifier = Modifier.padding(horizontal = layout.smallGap, vertical = layout.tinyGap),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

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

            SectionCard(title = stringResource(R.string.coming_soon_title)) {
                Text(
                    stringResource(R.string.we_are_currently_developing_interactive_training),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            TrainingResultsPanel(uiState = uiState, sport = sport)
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

@Composable
fun TrainingResultsPanel(uiState: TrainingUiState, sport: Sport) {
    when (uiState) {
        is TrainingUiState.Idle -> {
            EmptyMessage(stringResource(R.string.training_empty, stringResource(sport.label)))
        }

        is TrainingUiState.Loading -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is TrainingUiState.Success -> {
            uiState.drills.forEach { drill ->
                DrillCard(drill)
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
fun DrillCard(drill: Drill) {
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
        }
    }
}
