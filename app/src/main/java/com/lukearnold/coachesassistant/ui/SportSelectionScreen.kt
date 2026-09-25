package com.lukearnold.coachesassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.lukearnold.coachesassistant.data.Sport

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
            // This screen has no Scaffold to keep it clear of the status and navigation bars
            // (the app draws edge to edge), so it does that itself.
            .windowInsetsPadding(WindowInsets.safeDrawing)
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

        Column(verticalArrangement = Arrangement.spacedBy(layout.sectionGap)) {
            // Court Sports
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text(
                    text = "Court Sports",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                SportTile(emoji = Sport.BASKETBALL.emoji, label = Sport.BASKETBALL.displayName) {
                    onSelectSport(Sport.BASKETBALL)
                }
                SportTile(emoji = Sport.NETBALL.emoji, label = Sport.NETBALL.displayName) {
                    onSelectSport(Sport.NETBALL)
                }
                SportTile(emoji = Sport.FUTSAL.emoji, label = Sport.FUTSAL.displayName) {
                    onSelectSport(Sport.FUTSAL)
                }
                SportTile(emoji = Sport.HANDBALL.emoji, label = Sport.HANDBALL.displayName) {
                    onSelectSport(Sport.HANDBALL)
                }
            }

            // Field Sports
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text(
                    text = "Field Sports",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                SportTile(emoji = Sport.SOCCER.emoji, label = Sport.SOCCER.displayName) {
                    onSelectSport(Sport.SOCCER)
                }
                SportTile(emoji = Sport.AFL.emoji, label = Sport.AFL.displayName) {
                    onSelectSport(Sport.AFL)
                }
                SportTile(emoji = Sport.RUGBY_UNION.emoji, label = "Rugby") {
                    showRugbyDialog = true
                }
                SportTile(emoji = Sport.TOUCH_FOOTBALL.emoji, label = Sport.TOUCH_FOOTBALL.displayName) {
                    onSelectSport(Sport.TOUCH_FOOTBALL)
                }
                SportTile(emoji = Sport.FLAG_FOOTBALL.emoji, label = Sport.FLAG_FOOTBALL.displayName) {
                    onSelectSport(Sport.FLAG_FOOTBALL)
                }
                SportTile(emoji = Sport.GAELIC_FOOTBALL.emoji, label = Sport.GAELIC_FOOTBALL.displayName) {
                    onSelectSport(Sport.GAELIC_FOOTBALL)
                }
                SportTile(emoji = Sport.FIELD_HOCKEY.emoji, label = Sport.FIELD_HOCKEY.displayName) {
                    onSelectSport(Sport.FIELD_HOCKEY)
                }
                SportTile(emoji = Sport.LACROSSE.emoji, label = Sport.LACROSSE.displayName) {
                    onSelectSport(Sport.LACROSSE)
                }
                SportTile(emoji = Sport.ULTIMATE_FRISBEE.emoji, label = Sport.ULTIMATE_FRISBEE.displayName) {
                    onSelectSport(Sport.ULTIMATE_FRISBEE)
                }
            }

            // Ice & Water Sports
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text(
                    text = "Ice & Water Sports",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                SportTile(emoji = Sport.ICE_HOCKEY.emoji, label = Sport.ICE_HOCKEY.displayName) {
                    onSelectSport(Sport.ICE_HOCKEY)
                }
                SportTile(emoji = Sport.WATER_POLO.emoji, label = Sport.WATER_POLO.displayName) {
                    onSelectSport(Sport.WATER_POLO)
                }
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
