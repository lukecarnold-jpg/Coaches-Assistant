package com.lonrad.coachesassistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.CricketFieldPositionEntity
import com.lonrad.coachesassistant.data.CricketPosition
import com.lonrad.coachesassistant.data.PlayerEntity
import com.lonrad.coachesassistant.data.displayLabel

/**
 * A game's field plan: the field with each position, and who's placed where. Tapping a position on
 * the field, or a player in the list below it, places someone; the list is there because positions
 * near the bat sit too close together to rely on tapping the field alone.
 */
@Composable
fun FieldPlanCard(
    players: List<PlayerEntity>,
    field: List<CricketFieldPositionEntity>,
    onSetPosition: (position: CricketPosition, playerId: Long?) -> Unit,
    onClear: () -> Unit,
    onCopyLastField: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    var leftHanded by rememberSaveable { mutableStateOf(false) }
    var choosingFor by rememberSaveable { mutableStateOf<String?>(null) } // a CricketPosition name
    var placingPlayer by rememberSaveable { mutableStateOf<Long?>(null) }

    val playerById = players.associateBy { it.id }
    val positionOf = field.mapNotNull { row -> CricketPosition.fromRaw(row.position)?.let { row.playerId to it } }.toMap()
    val playerAt = positionOf.entries.associate { (playerId, position) -> position to playerId }

    SectionCard(title = stringResource(R.string.cricket_field_plan)) {
        Text(stringResource(R.string.cricket_field_hint), style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cricket_left_handed), modifier = Modifier.weight(1f))
            Switch(checked = leftHanded, onCheckedChange = { leftHanded = it })
        }

        CricketFieldDiagram(
            leftHanded = leftHanded,
            playerAt = { position -> playerAt[position]?.let(playerById::get) },
            onPositionClick = { choosingFor = it.name },
        )

        Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap)) {
            players.forEach { player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { placingPlayer = player.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(player.displayLabel(), modifier = Modifier.weight(1f))
                    Text(
                        positionOf[player.id]?.displayName ?: stringResource(R.string.cricket_not_placed),
                        color = if (player.id in positionOf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
            OutlinedButton(onClick = onCopyLastField) { Text(stringResource(R.string.cricket_copy_field)) }
            TextButton(onClick = onClear, enabled = field.isNotEmpty()) { Text(stringResource(R.string.cricket_clear_field)) }
        }
    }

    choosingFor?.let(CricketPosition::fromRaw)?.let { position ->
        PickerDialog(
            title = stringResource(R.string.cricket_place_at, position.displayName),
            noneLabel = stringResource(R.string.cricket_nobody),
            noneSelected = position !in playerAt,
            options = players.map { player ->
                PickerOption(
                    label = player.displayLabel(),
                    detail = positionOf[player.id]?.takeIf { it != position }?.displayName,
                    selected = playerAt[position] == player.id,
                    onPick = { onSetPosition(position, player.id) },
                )
            },
            onPickNone = { onSetPosition(position, null) },
            onDismiss = { choosingFor = null },
        )
    }

    placingPlayer?.let(playerById::get)?.let { player ->
        PickerDialog(
            title = stringResource(R.string.cricket_where_fields, player.displayLabel()),
            noneLabel = stringResource(R.string.cricket_not_placed),
            noneSelected = player.id !in positionOf,
            options = CricketPosition.entries.map { position ->
                PickerOption(
                    label = position.displayName,
                    detail = playerAt[position]?.takeIf { it != player.id }?.let(playerById::get)?.displayLabel(),
                    selected = positionOf[player.id] == position,
                    onPick = { onSetPosition(position, player.id) },
                )
            },
            onPickNone = { positionOf[player.id]?.let { onSetPosition(it, null) } },
            onDismiss = { placingPlayer = null },
        )
    }
}

/**
 * The field from above: boundary, inner ring, and pitch, with the striker at the top. Each position
 * is a small dot with its code, or a filled circle with the player's number (or initials) once
 * someone is placed there. The field is mirrored for a [leftHanded] batter.
 */
@Composable
private fun CricketFieldDiagram(
    leftHanded: Boolean,
    playerAt: (CricketPosition) -> PlayerEntity?,
    onPositionClick: (CricketPosition) -> Unit,
) {
    val grass = MaterialTheme.colorScheme.secondaryContainer
    val lines = MaterialTheme.colorScheme.onSecondaryContainer
    val pitch = MaterialTheme.colorScheme.tertiaryContainer
    val placed = CricketPosition.entries.count { playerAt(it) != null }
    val description = pluralStringResource(R.plurals.cricket_field_diagram_description, placed, placed)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        val side = maxWidth
        Canvas(modifier = Modifier.matchParentSize()) {
            val s = size.minDimension
            val center = Offset(s / 2, s / 2)
            drawCircle(color = grass, radius = s / 2, center = center)
            drawCircle(
                color = lines.copy(alpha = 0.4f),
                radius = s * 0.27f,
                center = center,
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
            )
            drawRect(color = pitch, topLeft = Offset(s * 0.475f, s * 0.35f), size = Size(s * 0.05f, s * 0.3f))
        }
        val offSide = stringResource(R.string.cricket_off_side)
        val legSide = stringResource(R.string.cricket_leg_side)
        Text(
            if (leftHanded) legSide else offSide,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            if (leftHanded) offSide else legSide,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopEnd),
        )

        val spot = 30.dp
        CricketPosition.entries.forEach { position ->
            val player = playerAt(position)
            Box(
                modifier = Modifier
                    .offset(x = side * position.xFor(leftHanded) - spot / 2, y = side * position.y - spot / 2)
                    .size(spot)
                    .clickable { onPositionClick(position) },
                contentAlignment = Alignment.Center,
            ) {
                if (player != null) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            player.shortLabel(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .border(BorderStroke(1.5.dp, lines), CircleShape),
                        )
                        Text(position.code, fontSize = 9.sp, color = lines, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** What fits in a field spot: the player's number, or their initials without one. */
private fun PlayerEntity.shortLabel(): String =
    jerseyNumber?.toString() ?: name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

private data class PickerOption(val label: String, val detail: String?, val selected: Boolean, val onPick: () -> Unit)

/** A list to pick one option from, or none; picking closes it. [PickerOption.detail] is shown beside an option. */
@Composable
private fun PickerDialog(
    title: String,
    noneLabel: String,
    noneSelected: Boolean,
    options: List<PickerOption>,
    onPickNone: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PickerRow(noneLabel, null, noneSelected) {
                    onPickNone()
                    onDismiss()
                }
                options.forEach { option ->
                    PickerRow(option.label, option.detail, option.selected) {
                        option.onPick()
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PickerRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
