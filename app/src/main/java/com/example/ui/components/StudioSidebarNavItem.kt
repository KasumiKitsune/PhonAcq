package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified desktop & tablet sidebar navigation item shared across Settings, User Manual,
 * Fieldwork Encyclopedia, and Desktop Studio File Picker.
 *
 * Uses a permanent `3.5.dp` indicator slot driven by `graphicsLayer { scaleY; alpha }`
 * to completely eliminate horizontal layout jumps when toggling selection, and coordinates
 * all surface/border/icon/text color transitions via a single [updateTransition].
 */
@Composable
fun StudioSidebarNavItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    compact: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val (interactionSource, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val isPressed by interactionSource.collectIsPressedAsState()

    val transition = updateTransition(
        targetState = Triple(selected, isHovered, isPressed),
        label = "StudioSidebarNavItem"
    )

    val itemBg by transition.animateColor(
        transitionSpec = { StudioMotion.microTween() },
        label = "sidebarItemBg"
    ) { (sel, hov, _) ->
        when {
            sel && hov -> lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.78f)
            sel -> lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.62f)
            hov -> colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            else -> Color.Transparent
        }
    }

    val borderCol by transition.animateColor(
        transitionSpec = { StudioMotion.microTween() },
        label = "sidebarItemBorder"
    ) { (sel, hov, _) ->
        when {
            sel -> colorScheme.primary.copy(alpha = 0.36f)
            hov -> colorScheme.outlineVariant.copy(alpha = 0.45f)
            else -> Color.Transparent
        }
    }

    val resolvedIconBg by transition.animateColor(
        transitionSpec = { StudioMotion.microTween() },
        label = "sidebarIconBg"
    ) { (sel, _, _) ->
        if (sel) colorScheme.primary else iconContainerColor
    }

    val resolvedIconFg by transition.animateColor(
        transitionSpec = { StudioMotion.microTween() },
        label = "sidebarIconFg"
    ) { (sel, _, _) ->
        if (sel) colorScheme.onPrimary else iconTint
    }

    val titleColor by transition.animateColor(
        transitionSpec = { StudioMotion.microTween() },
        label = "sidebarTitleColor"
    ) { (sel, hov, _) ->
        when {
            sel -> colorScheme.onPrimaryContainer
            hov -> colorScheme.primary
            else -> colorScheme.onSurface
        }
    }

    val subtitleColor by transition.animateColor(
        transitionSpec = { StudioMotion.microTween() },
        label = "sidebarSubtitleColor"
    ) { (sel, _, _) ->
        if (sel) colorScheme.onPrimaryContainer.copy(alpha = 0.78f) else colorScheme.onSurfaceVariant
    }

    val indicatorProgress by transition.animateFloat(
        transitionSpec = { StudioMotion.tactileSpring() },
        label = "sidebarIndicator"
    ) { (sel, _, _) ->
        if (sel) 1f else 0f
    }

    val pressScale by transition.animateFloat(
        transitionSpec = { StudioMotion.tactileSpring() },
        label = "sidebarPressScale"
    ) { (_, _, pressed) ->
        if (pressed) 0.985f else 1f
    }

    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)

    Surface(
        shape = shape,
        color = itemBg,
        border = BorderStroke(1.dp, borderCol),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (compact) 40.dp else 54.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compact) 6.dp else 8.dp,
                    end = if (compact) 10.dp else 11.dp,
                    top = if (compact) 7.dp else 9.dp,
                    bottom = if (compact) 7.dp else 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            // Permanent 3.5dp indicator slot: grows vertically with zero horizontal layout shift
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(if (compact) 20.dp else 26.dp)
                    .graphicsLayer {
                        val p = indicatorProgress.coerceIn(0f, 1f)
                        scaleY = p
                        alpha = p
                    }
                    .clip(CircleShape)
                    .background(colorScheme.primary)
            )

            Surface(
                shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
                color = resolvedIconBg,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.size(if (compact) 28.dp else 34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = resolvedIconFg,
                        modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = if (compact) 13.sp else 13.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        lineHeight = 14.5.sp,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailing != null) {
                trailing()
            }
        }
    }
}
