package com.example.basketballrotation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.basketballrotation.data.*
import kotlinx.coroutines.delay

@Composable
fun PlayerActionCard(
    stat: PlayerGameStat,
    onAddScore: (Long, Int) -> Unit,
    onAddFoul: (Long, Int) -> Unit,
    onAddMvpVote: (Long) -> Unit,
    gameTimerLastStartedAt: Long?,
    gameRunningTimeMillis: Long,
    enabled: Boolean = true,
) {
    val layout = LocalResponsiveLayout.current

    var shiftDisplayTime by remember(stat.shiftStartGameTimeMillis, gameRunningTimeMillis, gameTimerLastStartedAt) {
        mutableLongStateOf(stat.currentShiftTimeMillis)
    }

    LaunchedEffect(stat.shiftStartGameTimeMillis, gameRunningTimeMillis, gameTimerLastStartedAt) {
        val shiftStart = stat.shiftStartGameTimeMillis
        if (shiftStart == null) {
            shiftDisplayTime = 0L
        } else if (gameTimerLastStartedAt != null) {
            while (true) {
                val currentGameTimeMillis = gameRunningTimeMillis + (System.currentTimeMillis() - gameTimerLastStartedAt)
                shiftDisplayTime = maxOf(0L, currentGameTimeMillis - shiftStart)
                delay(100)
            }
        } else {
            shiftDisplayTime = maxOf(0L, gameRunningTimeMillis - shiftStart)
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(layout.smallGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap)) {
                    Text(
                        text = stat.player.displayLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Points: ${stat.points}   Fouls: ${stat.fouls}   Subbed off: ${stat.timesSubbedOff}")
                    if (enabled) {
                        Row {
                            TextButton(
                                onClick = { onAddScore(stat.player.id, -1) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(layout.compactControlHeight)
                            ) {
                                Text("-1 pt", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row {
                        Text(
                            text = "Shift: ${formatDuration(shiftDisplayTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(layout.smallGap))
                        Text(
                            text = "Total: ${formatDuration(stat.totalTimeMillis - stat.currentShiftTimeMillis + shiftDisplayTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(if (stat.currentShiftTimeMillis > 0 || gameTimerLastStartedAt != null) "ON COURT" else "BENCH")
                    Spacer(Modifier.height(4.dp))
                    if (enabled) {
                        TextButton(
                            onClick = { onAddMvpVote(stat.player.id) },
                            contentPadding = PaddingValues(horizontal = layout.smallGap),
                            modifier = Modifier.height(layout.controlHeight),
                            colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text("MVP Vote (${stat.mvpVotes})", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.smallGap)
                ) {
                    SmallActionButton(label = "+1", modifier = Modifier.weight(1f)) {
                        onAddScore(stat.player.id, 1)
                    }
                    SmallActionButton(label = "+2", modifier = Modifier.weight(1f)) {
                        onAddScore(stat.player.id, 2)
                    }
                    SmallActionButton(label = "+3", modifier = Modifier.weight(1f)) {
                        onAddScore(stat.player.id, 3)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Fouls",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedCard(
                        shape = RoundedCornerShape(layout.cardCornerRadius),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onAddFoul(stat.player.id, -1) },
                                modifier = Modifier.size(layout.iconButtonSize)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = stat.fouls.toString(),
                                modifier = Modifier.padding(horizontal = layout.smallGap),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onAddFoul(stat.player.id, 1) },
                                modifier = Modifier.size(layout.iconButtonSize)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add foul", modifier = Modifier.size(layout.inlineIconSize))
                            }
                        }
                    }
                }
            }
        }
    }
}
