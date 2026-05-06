package com.example.basketballrotation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    trainingViewModel: TrainingViewModel = viewModel(),
) {
    val uiState by trainingViewModel.uiState.collectAsState()
    val ageGroups = listOf(
        "Mini Ball (Ages 5-6)",
        "Under 8's",
        "Under 10's",
        "Under 12's",
        "Under 14's",
        "Under 16's",
        "Under 18's",
    )
    
    var expanded by remember { mutableStateOf(false) }
    var selectedAgeGroup by rememberSaveable { mutableStateOf(ageGroups.first()) }

    if (isExpanded) {
        Row(
            modifier = modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    onGenerateClick = { trainingViewModel.generateDrills(selectedAgeGroup) },
                    isLoading = uiState is TrainingUiState.Loading,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TrainingResultsPanel(uiState = uiState)
            }
        }
    } else {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Training drills",
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
                onGenerateClick = { trainingViewModel.generateDrills(selectedAgeGroup) },
                isLoading = uiState is TrainingUiState.Loading,
            )

            TrainingResultsPanel(uiState = uiState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingSelectionPanel(
    selectedAgeGroup: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    ageGroups: List<String>,
    onAgeGroupSelect: (String) -> Unit,
    onGenerateClick: () -> Unit,
    isLoading: Boolean,
) {
    SectionCard(title = "Select age group") {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = selectedAgeGroup,
                onValueChange = {},
                readOnly = true,
                label = { Text("Age group") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                ageGroups.forEach { age ->
                    DropdownMenuItem(
                        text = { Text(age) },
                        onClick = { onAgeGroupSelect(age) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onGenerateClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            Text("Generate AI Drills")
        }
    }
}

@Composable
fun TrainingResultsPanel(uiState: TrainingUiState) {
    when (val state = uiState) {
        is TrainingUiState.Idle -> {
            EmptyMessage("Select an age group and click generate to see basketball drills tailored by AI.")
        }

        is TrainingUiState.Loading -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is TrainingUiState.Success -> {
            state.drills.forEach { drill ->
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
                    text = state.message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun DrillCard(drill: BasketballDrill) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = drill.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = drill.description,
                style = MaterialTheme.typography.bodyMedium,
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            Text(
                text = "Teaching Points",
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
