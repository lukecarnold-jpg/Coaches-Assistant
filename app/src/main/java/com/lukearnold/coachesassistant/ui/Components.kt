package com.lukearnold.coachesassistant.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(layout.smallGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

@Composable
fun SmallActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = layout.controlHeight),
        contentPadding = PaddingValues(horizontal = layout.smallGap, vertical = layout.tinyGap),
    ) {
        Text(label)
    }
}

@Composable
fun SelectablePlayerRow(
    text: String,
    selected: Boolean,
    badgeNumber: Int? = null,
    /** Secondary text shown at the end of the row, e.g. how far behind their playing-time share. */
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    val background =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Announced as selected / not selected, not just "double-tap to activate".
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick),
        shape = RoundedCornerShape(layout.cardCornerRadius),
        color = background,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(layout.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (badgeNumber != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeNumber.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(layout.tinyGap))
            }
            Text(
                text = text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (trailingText != null) {
                Spacer(Modifier.width(layout.tinyGap))
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun GameListCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    info: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(layout.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap), content = info)
            Column(content = actions)
        }
    }
}

@Composable
fun StatusBadge(text: String, modifier: Modifier = Modifier) {
    val layout = LocalResponsiveLayout.current
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = layout.smallGap + 2.dp, vertical = layout.tinyGap + 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
