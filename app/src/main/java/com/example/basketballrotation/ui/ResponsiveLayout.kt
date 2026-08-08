package com.example.basketballrotation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ResponsiveLayout(
    val widthDp: Int,
    val heightDp: Int,
    val screenPadding: Dp,
    val sectionGap: Dp,
    val cardPadding: Dp,
    val smallGap: Dp,
    val tinyGap: Dp,
    val cardCornerRadius: Dp,
    val controlHeight: Dp,
    val compactControlHeight: Dp,
    val iconButtonSize: Dp,
    val inlineIconSize: Dp,
    val notesMinHeight: Dp,
)

private val DefaultResponsiveLayout = ResponsiveLayout(
    widthDp = 360,
    heightDp = 800,
    screenPadding = 16.dp,
    sectionGap = 16.dp,
    cardPadding = 16.dp,
    smallGap = 8.dp,
    tinyGap = 4.dp,
    cardCornerRadius = 12.dp,
    controlHeight = 40.dp,
    compactControlHeight = 32.dp,
    iconButtonSize = 36.dp,
    inlineIconSize = 16.dp,
    notesMinHeight = 280.dp,
)

val LocalResponsiveLayout = compositionLocalOf { DefaultResponsiveLayout }

@Composable
fun rememberResponsiveLayout(): ResponsiveLayout {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp
        val scale = when {
            widthDp < 360 -> 0.90f
            widthDp < 600 -> 1.00f
            widthDp < 840 -> 1.10f
            else -> 1.20f
        }
        val heightScale = when {
            heightDp < 700 -> 0.92f
            heightDp < 900 -> 1.00f
            else -> 1.06f
        }
        val combinedScale = (scale + heightScale) / 2f

        ResponsiveLayout(
            widthDp = widthDp,
            heightDp = heightDp,
            screenPadding = (16f * combinedScale).dp,
            sectionGap = (16f * combinedScale).dp,
            cardPadding = (16f * combinedScale).dp,
            smallGap = (8f * combinedScale).dp,
            tinyGap = (4f * combinedScale).dp,
            cardCornerRadius = (12f * combinedScale).dp,
            controlHeight = (40f * combinedScale).dp,
            compactControlHeight = (32f * combinedScale).dp,
            iconButtonSize = (36f * combinedScale).dp,
            inlineIconSize = (16f * combinedScale).dp,
            notesMinHeight = (280f * combinedScale).dp,
        )
    }
}

@Composable
fun ResponsiveLayoutProvider(content: @Composable () -> Unit) {
    val layout = rememberResponsiveLayout()
    CompositionLocalProvider(LocalResponsiveLayout provides layout) {
        content()
    }
}
