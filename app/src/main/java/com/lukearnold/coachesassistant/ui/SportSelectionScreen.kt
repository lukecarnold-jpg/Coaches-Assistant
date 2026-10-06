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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lukearnold.coachesassistant.R
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
            text = stringResource(R.string.choose_a_sport),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.pick_the_sport_you_re_coaching_you_can_switch_sp),
            style = MaterialTheme.typography.bodyMedium,
        )

        Column(verticalArrangement = Arrangement.spacedBy(layout.sectionGap)) {
            // Court Sports
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text(
                    text = stringResource(R.string.court_sports),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                SportTile(emoji = Sport.BASKETBALL.emoji, label = stringResource(Sport.BASKETBALL.label)) {
                    onSelectSport(Sport.BASKETBALL)
                }
                SportTile(emoji = Sport.NETBALL.emoji, label = stringResource(Sport.NETBALL.label)) {
                    onSelectSport(Sport.NETBALL)
                }
                SportTile(emoji = Sport.FUTSAL.emoji, label = stringResource(Sport.FUTSAL.label)) {
                    onSelectSport(Sport.FUTSAL)
                }
                SportTile(emoji = Sport.HANDBALL.emoji, label = stringResource(Sport.HANDBALL.label)) {
                    onSelectSport(Sport.HANDBALL)
                }
            }

            // Field Sports
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text(
                    text = stringResource(R.string.field_sports),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                SportTile(emoji = Sport.SOCCER.emoji, label = stringResource(Sport.SOCCER.label)) {
                    onSelectSport(Sport.SOCCER)
                }
                SportTile(emoji = Sport.AFL.emoji, label = stringResource(Sport.AFL.label)) {
                    onSelectSport(Sport.AFL)
                }
                SportTile(emoji = Sport.RUGBY_UNION.emoji, label = stringResource(R.string.rugby)) {
                    showRugbyDialog = true
                }
                SportTile(emoji = Sport.TOUCH_FOOTBALL.emoji, label = stringResource(Sport.TOUCH_FOOTBALL.label)) {
                    onSelectSport(Sport.TOUCH_FOOTBALL)
                }
                SportTile(emoji = Sport.FLAG_FOOTBALL.emoji, label = stringResource(Sport.FLAG_FOOTBALL.label)) {
                    onSelectSport(Sport.FLAG_FOOTBALL)
                }
                SportTile(emoji = Sport.GAELIC_FOOTBALL.emoji, label = stringResource(Sport.GAELIC_FOOTBALL.label)) {
                    onSelectSport(Sport.GAELIC_FOOTBALL)
                }
                SportTile(emoji = Sport.FIELD_HOCKEY.emoji, label = stringResource(Sport.FIELD_HOCKEY.label)) {
                    onSelectSport(Sport.FIELD_HOCKEY)
                }
                SportTile(emoji = Sport.LACROSSE.emoji, label = stringResource(Sport.LACROSSE.label)) {
                    onSelectSport(Sport.LACROSSE)
                }
                SportTile(emoji = Sport.ULTIMATE_FRISBEE.emoji, label = stringResource(Sport.ULTIMATE_FRISBEE.label)) {
                    onSelectSport(Sport.ULTIMATE_FRISBEE)
                }
            }

            // Ice & Water Sports
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text(
                    text = stringResource(R.string.ice_water_sports),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                SportTile(emoji = Sport.ICE_HOCKEY.emoji, label = stringResource(Sport.ICE_HOCKEY.label)) {
                    onSelectSport(Sport.ICE_HOCKEY)
                }
                SportTile(emoji = Sport.WATER_POLO.emoji, label = stringResource(Sport.WATER_POLO.label)) {
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
        title = { Text(stringResource(R.string.which_code_question)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                Text(stringResource(R.string.rugby_splits_into_two_codes_which_one_do_you_coa_question))
                SportTile(emoji = Sport.RUGBY_UNION.emoji, label = stringResource(Sport.RUGBY_UNION.label)) {
                    onSelectCode(Sport.RUGBY_UNION)
                }
                SportTile(emoji = Sport.RUGBY_LEAGUE.emoji, label = stringResource(Sport.RUGBY_LEAGUE.label)) {
                    onSelectCode(Sport.RUGBY_LEAGUE)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
