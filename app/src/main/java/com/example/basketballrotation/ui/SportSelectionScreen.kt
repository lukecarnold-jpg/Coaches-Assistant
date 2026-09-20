package com.example.basketballrotation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.basketballrotation.data.Sport

@Composable
fun SportSelectionScreen(
    modifier: Modifier = Modifier,
    onSelectSport: (Sport) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    var showRugbyDialog by remember { mutableStateOf(value = false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
    ) {
        Text(
            text = "Choose a sport",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Pick the sport you're coaching. You can switch sports later from Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
            SportTile(emoji = Sport.BASKETBALL.emoji, label = Sport.BASKETBALL.displayName) {
                onSelectSport(Sport.BASKETBALL)
            }
            SportTile(emoji = Sport.NETBALL.emoji, label = Sport.NETBALL.displayName) {
                onSelectSport(Sport.NETBALL)
            }
            SportTile(emoji = Sport.SOCCER.emoji, label = Sport.SOCCER.displayName) {
                onSelectSport(Sport.SOCCER)
            }
            SportTile(emoji = Sport.AFL.emoji, label = Sport.AFL.displayName) {
                onSelectSport(Sport.AFL)
            }
            SportTile(emoji = Sport.RUGBY_UNION.emoji, label = "Rugby") {
                showRugbyDialog = true
            }
        }
    }

    if (showRugbyDialog) {
        RugbyCodeDialog(
            onDismiss = { showRugbyDialog = false },
            onSelectCode = { sport ->
                showRugbyDialog = false
                onSelectSport(sport)
            },
        )
    }
}

@Composable
private fun RugbyCodeDialog(
    onDismiss: () -> Unit,
    onSelectCode: (Sport) -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which code?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text("Rugby splits into two codes. Which one do you coach?")
                SportTile(emoji = Sport.RUGBY_UNION.emoji, label = Sport.RUGBY_UNION.displayName) {
                    onSelectCode(Sport.RUGBY_UNION)
                }
                SportTile(emoji = Sport.RUGBY_LEAGUE.emoji, label = Sport.RUGBY_LEAGUE.displayName) {
                    onSelectCode(Sport.RUGBY_LEAGUE)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SportTile(
    emoji: String,
    label: String,
    onClick: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(layout.cardCornerRadius),
    ) {
        Row(
            modifier = Modifier.padding(layout.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
