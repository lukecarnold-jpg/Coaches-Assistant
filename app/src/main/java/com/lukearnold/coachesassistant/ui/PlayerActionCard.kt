package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.stringResource
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
import com.lukearnold.coachesassistant.data.*

@Composable
fun PlayerActionCard(
    stat: PlayerGameStat,
    sportConfig: SportConfig,
    onAddScore: (Long, Int) -> Unit,
    onAddFoul: (Long, Int) -> Unit,
    onAddMvpVote: (Long) -> Unit,
    gameTimeMillis: () -> Long,
    fairShareAt: (gameTimeMillis: Long) -> FairShare?,
    enabled: Boolean = true,
) {
    val layout = LocalResponsiveLayout.current

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
                    Text(stringResource(R.string.player_card_stats, stat.points, stringResource(sportConfig.infraction.plural), stat.fouls, stat.timesSubbedOff))
                    if (enabled) {
                        Row {
                            TextButton(
                                onClick = { onAddScore(stat.player.id, -1) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(layout.compactControlHeight)
                            ) {
                                Text(stringResource(R.string.minus_one_point), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row {
                        val now = gameTimeMillis()
                        Text(
                            text = stringResource(R.string.shift_time, formatDuration(stat.shiftTimeAt(now))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(layout.smallGap))
                        Text(
                            text = stringResource(R.string.total_time_short, formatDuration(stat.totalTimeAt(now))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    val now = gameTimeMillis()
                    fairShareAt(now)?.let { share ->
                        Text(
                            text = stringResource(
                                R.string.target_and_pace,
                                formatDuration(share.fullGameMillis),
                                paceLabel(share.behindMillis(stat.totalTimeAt(now))).asString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(if (stat.onCourt) stringResource(R.string.on_court) else stringResource(R.string.bench))
                    Spacer(Modifier.height(4.dp))
                    if (enabled) {
                        TextButton(
                            onClick = { onAddMvpVote(stat.player.id) },
                            contentPadding = PaddingValues(horizontal = layout.smallGap),
                            modifier = Modifier.height(layout.controlHeight),
                            colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text(stringResource(R.string.mvp_vote_count, stat.mvpVotes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.smallGap)
                ) {
                    sportConfig.scoringOptions.forEach { option ->
                        SmallActionButton(label = option.label.asString(), modifier = Modifier.weight(1f)) {
                            onAddScore(stat.player.id, option.points)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.smallGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(sportConfig.infraction.label),
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
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_foul), modifier = Modifier.size(layout.inlineIconSize))
                            }
                        }
                    }
                }
            }
        }
    }
}
