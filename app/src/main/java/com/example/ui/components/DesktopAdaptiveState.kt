package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalPhoneticFontFamily
import com.example.ui.theme.containsPhoneticOrCombiningGlyphs
import kotlin.math.roundToInt

/**
 * Shared adaptive state between Android and Windows Desktop.
 * Coordinates desktop keyboard shortcut focus suspension, collapsible panes,
 * and the integrated desktop studio toolbar.
 */
object DesktopAdaptiveState {
    var isDesktopPlatform by mutableStateOf(false)
    var isProjectSidebarCollapsed by mutableStateOf(false)
    var hasCollapsibleProjectSidebar by mutableStateOf(false)
    var showShortcutCheatSheet by mutableStateOf(false)

    val shortcutSuspendTokens = mutableStateListOf<Any>()

    val canTriggerBareKeyShortcuts: Boolean
        get() = shortcutSuspendTokens.isEmpty()

    var desktopTopBarSlot: (@Composable () -> Unit)? by mutableStateOf(null)

    // Platform scrollbar slots: no-op on Android, populated with Compose Desktop VerticalScrollbar in DesktopMain.kt
    var listScrollbarSlot: (@Composable BoxScope.(LazyListState, Modifier) -> Unit)? by mutableStateOf(null)
    var gridScrollbarSlot: (@Composable BoxScope.(LazyGridState, Modifier) -> Unit)? by mutableStateOf(null)
    var scrollScrollbarSlot: (@Composable BoxScope.(ScrollState, Modifier) -> Unit)? by mutableStateOf(null)
}

/**
 * Renders a native Desktop vertical scrollbar when registered; no-op on Android touch devices.
 */
@Composable
fun BoxScope.PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    DesktopAdaptiveState.listScrollbarSlot?.invoke(this, listState, modifier)
}

@Composable
fun BoxScope.PlatformVerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    DesktopAdaptiveState.gridScrollbarSlot?.invoke(this, gridState, modifier)
}

@Composable
fun BoxScope.PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    DesktopAdaptiveState.scrollScrollbarSlot?.invoke(this, scrollState, modifier)
}

/**
 * Suspends single-key desktop shortcuts (such as Space, Left/Right arrows, Enter, P, F, M)
 * while a text-input screen or dialog is active, so typing in TextFields never conflicts with shortcuts.
 */
@Composable
fun SuspendDesktopShortcutsWhen(active: Boolean) {
    val token = remember { Any() }
    DisposableEffect(active) {
        if (active) {
            if (!DesktopAdaptiveState.shortcutSuspendTokens.contains(token)) {
                DesktopAdaptiveState.shortcutSuspendTokens.add(token)
            }
        } else {
            DesktopAdaptiveState.shortcutSuspendTokens.remove(token)
        }
        onDispose {
            DesktopAdaptiveState.shortcutSuspendTokens.remove(token)
        }
    }
}

private val BadgeLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

private val PhoneticChipLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

/**
 * Renders a crisp desktop keyboard keycap badge (e.g. `[Space] 录音`).
 */
@Composable
fun KeycapBadge(
    keyText: String,
    actionText: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 22.dp, minHeight = 20.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = keyText,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = LocalTextStyle.current.copy(lineHeightStyle = BadgeLineHeightStyle)
                )
            }
        }
        if (!actionText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = actionText,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LocalTextStyle.current.copy(lineHeightStyle = BadgeLineHeightStyle)
            )
        }
    }
}

/**
 * Refined single-line filter/action/input chip with 100% opaque `lerp` hover tinting,
 * zero shadow elevation (preventing Skia's 9-patch shadow ring artifact on Desktop),
 * crisp border transitions, pointer hand cursor, and single-line text protection.
 */
@Composable
fun StudioFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    labelText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isErrorVariant: Boolean = false,
    showCheckmark: Boolean = true,
    horizontalPadding: Dp = 11.dp,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = if (isDesktop && enabled) {
        interactionSource.collectIsHoveredAsState().value
    } else {
        false
    }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val accentColor = if (isErrorVariant) colorScheme.error else colorScheme.primary
    val selectedBg = if (isErrorVariant) {
        lerp(colorScheme.surface, colorScheme.errorContainer, if (isHovered && enabled) 0.96f else 0.82f)
    } else {
        lerp(colorScheme.surface, colorScheme.primaryContainer, if (isHovered && enabled) 0.96f else 0.85f)
    }
    val selectedText = if (isErrorVariant) colorScheme.onErrorContainer else colorScheme.onPrimaryContainer

    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> lerp(colorScheme.surface, colorScheme.surfaceContainerLow, 0.5f)
            selected -> selectedBg
            isHovered -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.42f)
            else -> colorScheme.surface
        },
        animationSpec = tween(150),
        label = "StudioFilterChipBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> colorScheme.outlineVariant.copy(alpha = 0.35f)
            selected -> accentColor.copy(alpha = if (isHovered) 0.8f else 0.6f)
            isHovered -> accentColor.copy(alpha = 0.65f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.75f)
        },
        animationSpec = tween(150),
        label = "StudioFilterChipBorder"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            selected -> selectedText
            isHovered -> accentColor
            else -> colorScheme.onSurfaceVariant
        },
        animationSpec = tween(150),
        label = "StudioFilterChipText"
    )

    val targetScale = when {
        !enabled -> 1f
        isPressed -> 0.95f
        isHovered && isDesktop -> 1.015f
        else -> 1f
    }
    val chipScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "StudioFilterChipScale"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(if ((selected || isHovered) && enabled) 1.2.dp else 1.dp, borderColor),
        modifier = modifier
            .height(32.dp)
            .graphicsLayer {
                scaleX = chipScale
                scaleY = chipScale
            }
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (enabled) {
                    Modifier
                        .hoverable(interactionSource)
                        .then(if (isDesktop) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Row(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(5.dp))
                } else {
                    AnimatedVisibility(
                        visible = selected && showCheckmark,
                        enter = expandHorizontally(tween(160)) + fadeIn(tween(150)),
                        exit = shrinkHorizontally(tween(140)) + fadeOut(tween(120))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                    }
                }
                val isPhoneticLabel = remember(labelText) { containsPhoneticOrCombiningGlyphs(labelText) }
                val phoneticFontFamily = LocalPhoneticFontFamily.current
                Text(
                    text = labelText,
                    fontSize = if (isPhoneticLabel) 13.5.sp else 12.sp,
                    lineHeight = if (isPhoneticLabel) 19.sp else 14.sp,
                    fontFamily = if (isPhoneticLabel) phoneticFontFamily else null,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(
                        lineHeightStyle = if (isPhoneticLabel) PhoneticChipLineHeightStyle else BadgeLineHeightStyle
                    )
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(5.dp))
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Switch that supports BOTH tap-to-toggle AND continuous horizontal thumb dragging,
 * with tactile thumb stretch on press/drag, smooth color interpolation along the drag path, and spring physics.
 */
@Composable
fun StudioSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val animatedFraction by animateFloatAsState(
        targetValue = if (isDragging) dragFraction else if (checked) 1f else 0f,
        animationSpec = if (isDragging) {
            spring(dampingRatio = 1f, stiffness = 3200f)
        } else {
            spring(dampingRatio = 0.72f, stiffness = 520f)
        },
        label = "StudioSwitchFraction"
    )
    val progress = animatedFraction.coerceIn(0f, 1f)

    val trackWidth = 52.dp
    val trackHeight = 30.dp
    val thumbRestSize = lerpDp(16.dp, 22.dp, progress)
    val thumbWidth by animateDpAsState(
        targetValue = if ((isPressed || isDragging) && enabled) 26.dp else thumbRestSize,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 680f),
        label = "StudioSwitchThumbWidth"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if ((isPressed || isDragging) && enabled) 22.dp else thumbRestSize,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 680f),
        label = "StudioSwitchThumbHeight"
    )

    val minPad = 4.dp
    val maxTravelPx = with(density) { (trackWidth - thumbWidth - minPad * 2).toPx().coerceAtLeast(1f) }
    val dragRangePx = with(density) { (trackWidth - 22.dp - minPad * 2).toPx().coerceAtLeast(1f) }

    val colorScheme = MaterialTheme.colorScheme
    val uncheckedTrack = lerp(
        colorScheme.surfaceContainerHighest,
        colorScheme.primaryContainer,
        if (isHovered && enabled) 0.25f else 0f
    )
    val checkedTrack = if (isHovered && enabled) {
        lerp(colorScheme.primary, colorScheme.primaryContainer, 0.14f)
    } else {
        colorScheme.primary
    }
    val trackColor = lerp(uncheckedTrack, checkedTrack, progress).let {
        if (enabled) it else it.copy(alpha = 0.45f)
    }

    val uncheckedBorder = if (isHovered && enabled) colorScheme.primary.copy(alpha = 0.7f) else colorScheme.outline
    val borderColor = lerp(uncheckedBorder, checkedTrack, progress).let {
        if (enabled) it else it.copy(alpha = 0.35f)
    }

    val uncheckedThumb = if (isHovered && enabled) colorScheme.onSurface else colorScheme.outline
    val checkedThumb = colorScheme.onPrimary
    val thumbColor = lerp(uncheckedThumb, checkedThumb, progress)

    val gestureModifier = if (enabled && onCheckedChange != null) {
        Modifier
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = {
                        currentOnCheckedChange?.invoke(!currentChecked)
                    }
                )
            }
            .pointerInput(Unit) {
                var startFraction = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        startFraction = if (currentChecked) 1f else 0f
                        dragFraction = startFraction
                        isDragging = true
                        isPressed = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragFraction = (dragFraction + dragAmount / dragRangePx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val delta = dragFraction - startFraction
                        val targetChecked = when {
                            delta > 0.18f -> true
                            delta < -0.18f -> false
                            else -> dragFraction >= 0.5f
                        }
                        isDragging = false
                        isPressed = false
                        if (targetChecked != currentChecked) {
                            currentOnCheckedChange?.invoke(targetChecked)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        isPressed = false
                    }
                )
            }
    } else {
        Modifier
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = 36.dp)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
            .then(gestureModifier)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .size(trackWidth, trackHeight)
                .clip(CircleShape)
                .background(trackColor, CircleShape)
                .border(1.5.dp, borderColor, CircleShape)
                .padding(horizontal = minPad)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        val px = progress * maxTravelPx
                        IntOffset(if (px.isFinite()) px.roundToInt() else 0, 0)
                    }
                    .size(thumbWidth, thumbHeight)
                    .background(thumbColor, CircleShape)
            ) {
                if (progress > 0.25f) {
                    val iconScale = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                                alpha = iconScale
                            }
                    )
                }
            }
        }
    }
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
    return start + (stop - start) * fraction
}

/**
 * Interactive full-row switch card item that supports both clicking anywhere on the row
 * AND dragging the [StudioSwitch] thumb directly.
 */
@Composable
fun StudioSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elevatedSurface: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    rowTestTag: String? = null,
    switchTestTag: String? = null,
    compact: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val baseBg = if (elevatedSurface) colorScheme.surface else colorScheme.surfaceContainerLow
    val rowBg by animateColorAsState(
        targetValue = when {
            !enabled -> baseBg.copy(alpha = 0.6f)
            elevatedSurface && checked -> lerp(colorScheme.surface, colorScheme.primaryContainer, if (isHovered) 0.52f else 0.38f)
            isHovered -> lerp(baseBg, colorScheme.primaryContainer, 0.35f)
            else -> baseBg
        },
        animationSpec = tween(140),
        label = "StudioSwitchRowBg"
    )
    val rowBorder by animateColorAsState(
        targetValue = when {
            !enabled -> if (elevatedSurface) colorScheme.outlineVariant.copy(alpha = 0.35f) else colorScheme.primary.copy(alpha = 0f)
            elevatedSurface && checked -> colorScheme.primary.copy(alpha = if (isHovered) 0.65f else 0.48f)
            isHovered -> colorScheme.primary.copy(alpha = 0.38f)
            elevatedSurface -> colorScheme.outlineVariant.copy(alpha = 0.65f)
            else -> colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = tween(140),
        label = "StudioSwitchRowBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.99f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "StudioSwitchRowScale"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = rowBg,
        border = BorderStroke(1.dp, rowBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (enabled) {
                    Modifier
                        .hoverable(interactionSource)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
            .then(
                if (rowTestTag != null) {
                    Modifier.testTag(rowTestTag)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 8.dp else 12.dp,
                    vertical = if (compact) 6.dp else 9.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                }
                Column(modifier = Modifier.weight(1f)) {
                    val titleColor by animateColorAsState(
                        targetValue = when {
                            !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            isHovered -> colorScheme.primary
                            else -> colorScheme.onSurface
                        },
                        animationSpec = StudioMotion.microTween(),
                        label = "StudioSwitchRowTitleColor"
                    )
                    Text(
                        text = title,
                        fontSize = if (compact) 13.5.sp else 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 11.5.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 16.5.sp
                        )
                    }
                }
            }
            StudioSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = if (enabled) onCheckedChange else null,
                modifier = if (switchTestTag != null) {
                    Modifier.testTag(switchTestTag)
                } else {
                    Modifier
                }
            )
        }
    }
}

/**
 * Interactive single-choice radio row with animated selection border, hover tint, and press scale.
 */
@Composable
fun StudioRadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val rowBg by animateColorAsState(
        targetValue = when {
            selected -> lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, if (isHovered) 0.55f else 0.42f)
            isHovered -> lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.28f)
            else -> colorScheme.surfaceContainerLow
        },
        animationSpec = tween(140),
        label = "StudioRadioRowBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> colorScheme.primary.copy(alpha = 0.55f)
            isHovered -> colorScheme.primary.copy(alpha = 0.32f)
            else -> colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = tween(140),
        label = "StudioRadioRowBorder"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 550f),
        label = "StudioRadioDotScale"
    )
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed) 0.988f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "StudioRadioRowScale"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = rowBg,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(10.dp))
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
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val radioRingColor by animateColorAsState(
                targetValue = if (selected || isHovered) colorScheme.primary else colorScheme.outline,
                animationSpec = StudioMotion.microTween(),
                label = "StudioRadioRingColor"
            )
            val radioTextColor by animateColorAsState(
                targetValue = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                animationSpec = StudioMotion.microTween(),
                label = "StudioRadioTextColor"
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .border(
                        width = if (selected) 2.dp else 1.5.dp,
                        color = radioRingColor,
                        shape = CircleShape
                    )
            ) {
                if (dotScale > 0.01f) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                            }
                            .background(colorScheme.primary, CircleShape)
                    )
                }
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = radioTextColor
            )
        }
    }
}

/**
 * Standalone tactile rounded checkbox matching [StudioCheckboxRow] and [StudioSwitch].
 */
@Composable
fun StudioCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val boxBg by animateColorAsState(
        targetValue = when {
            !enabled && checked -> colorScheme.primary.copy(alpha = 0.45f)
            !enabled -> colorScheme.primary.copy(alpha = 0f)
            checked -> colorScheme.primary
            else -> colorScheme.primary.copy(alpha = 0f)
        },
        animationSpec = tween(140),
        label = "StudioStandaloneCheckBoxBg"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 580f),
        label = "StudioStandaloneCheckScale"
    )
    val boxScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "StudioStandaloneCheckPressScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
            .semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            }
            .then(
                if (enabled && onCheckedChange != null) {
                    Modifier
                        .hoverable(interactionSource)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
    ) {
        val boxBorderColor by animateColorAsState(
            targetValue = when {
                !enabled -> colorScheme.outlineVariant
                checked || isHovered -> colorScheme.primary
                else -> colorScheme.outline
            },
            animationSpec = StudioMotion.microTween(),
            label = "StudioStandaloneCheckBorder"
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = boxScale
                    scaleY = boxScale
                }
                .clip(RoundedCornerShape(6.dp))
                .background(boxBg, RoundedCornerShape(6.dp))
                .border(
                    width = 1.5.dp,
                    color = boxBorderColor,
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            if (checkScale > 0.01f) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colorScheme.onPrimary,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                )
            }
        }
    }
}

/**
 * Interactive checkbox row with animated spring checkmark, hover highlight, and press scale.
 */
@Composable
fun StudioCheckboxRow(
    checked: Boolean,
    label: String,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val hoverBaseTint = lerp(colorScheme.surface, colorScheme.primaryContainer, 0.22f)
    val rowBg by animateColorAsState(
        targetValue = when {
            !enabled -> colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
            checked -> lerp(colorScheme.surface, colorScheme.primaryContainer, if (isHovered) 0.45f else 0.28f)
            isHovered -> hoverBaseTint
            else -> hoverBaseTint.copy(alpha = 0f)
        },
        animationSpec = tween(140),
        label = "StudioCheckRowBg"
    )
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.988f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "StudioCheckRowScale"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = rowBg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (enabled && onCheckedChange != null) {
                    Modifier
                        .hoverable(interactionSource)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioCheckbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.size(20.dp)
            )
            val checkRowLabelColor by animateColorAsState(
                targetValue = when {
                    !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    checked -> colorScheme.onSurface
                    isHovered -> colorScheme.primary
                    else -> colorScheme.onSurfaceVariant
                },
                animationSpec = StudioMotion.microTween(),
                label = "StudioCheckRowLabelColor"
            )
            Text(
                text = label,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                color = checkRowLabelColor
            )
        }
    }
}

/**
 * Sliding-pill segmented control that supports both clicking AND horizontal dragging
 * across options with smooth spring animation.
 */
@Composable
fun StudioSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTags: List<String>? = null,
    maxControlWidth: Dp = (options.size * 176).dp.coerceAtMost(480.dp)
) {
    if (options.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    val currentSelected by rememberUpdatedState(selectedIndex.coerceIn(0, options.lastIndex))
    val currentOnSelected by rememberUpdatedState(onOptionSelected)

    var isDragging by remember { mutableStateOf(false) }
    var dragIndexFloat by remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    val animatedIndex by animateFloatAsState(
        targetValue = if (isDragging) dragIndexFloat else selectedIndex.toFloat(),
        animationSpec = if (isDragging) {
            spring(dampingRatio = 1f, stiffness = 2800f)
        } else {
            spring(dampingRatio = 0.84f, stiffness = 520f)
        },
        label = "SegmentedThumbIndex"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .widthIn(max = maxControlWidth)
            .fillMaxWidth()
            .height(40.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .pointerInput(options.size) {
                    val totalW = size.width.toFloat().coerceAtLeast(1f)
                    val segW = totalW / options.size
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragIndexFloat = ((offset.x / segW) - 0.5f).coerceIn(0f, options.lastIndex.toFloat())
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragIndexFloat = (dragIndexFloat + dragAmount / segW).coerceIn(0f, options.lastIndex.toFloat())
                        },
                        onDragEnd = {
                            val targetIdx = (if (dragIndexFloat.isFinite()) dragIndexFloat.roundToInt() else currentSelected)
                                .coerceIn(0, options.lastIndex)
                            isDragging = false
                            if (targetIdx != currentSelected) {
                                currentOnSelected(targetIdx)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        ) {
            val innerWidth = if (this@BoxWithConstraints.maxWidth.value.isFinite()) {
                this@BoxWithConstraints.maxWidth
            } else {
                240.dp
            }
            val segmentWidth = innerWidth / options.size.coerceAtLeast(1)
            val segmentWidthPx = with(LocalDensity.current) { segmentWidth.toPx() }.let { if (it.isFinite()) it else 0f }
            val clampedIndex = (if (animatedIndex.isFinite()) animatedIndex else selectedIndex.toFloat())
                .coerceIn(0f, options.lastIndex.toFloat())

            // Sliding active pill indicator
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = colorScheme.primaryContainer,
                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .offset {
                        val offsetPx = clampedIndex * segmentWidthPx
                        IntOffset(if (offsetPx.isFinite()) offsetPx.roundToInt() else 0, 0)
                    }
            ) {}

            Row(modifier = Modifier.fillMaxSize()) {
                options.forEachIndexed { idx, label ->
                    val isSel = idx == selectedIndex
                    val tag = optionTestTags?.getOrNull(idx)
                    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
                    val isHovered by isHoveredState
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(11.dp))
                            .hoverable(hoverSrc)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = hoverSrc,
                                indication = null
                            ) {
                                if (idx != selectedIndex) onOptionSelected(idx)
                            }
                            .then(
                                if (tag != null) {
                                    Modifier.testTag(tag)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val segLabelColor by animateColorAsState(
                            targetValue = when {
                                isSel -> colorScheme.onPrimaryContainer
                                isHovered -> colorScheme.primary
                                else -> colorScheme.onSurfaceVariant
                            },
                            animationSpec = StudioMotion.microTween(),
                            label = "StudioSegmentedLabelColor"
                        )
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = segLabelColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private val StaticUnHoveredState: State<Boolean> = mutableStateOf(false)

/**
 * Helper that returns a remembered [MutableInteractionSource] and its hover [State].
 * On non-desktop touch devices, skips launching a per-item hover collection coroutine!
 */
@Composable
fun rememberStudioInteraction(): Pair<MutableInteractionSource, State<Boolean>> {
    val src = remember { MutableInteractionSource() }
    val hovered = if (DesktopAdaptiveState.isDesktopPlatform) {
        src.collectIsHoveredAsState()
    } else {
        StaticUnHoveredState
    }
    return src to hovered
}

/**
 * Adds desktop pointer hand cursor and spring press/hover scale micro-interaction
 * driven by a shared [MutableInteractionSource].
 */
@Composable
fun Modifier.studioTactile(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressScale: Float = 0.965f,
    hoverScale: Float = 1.0f
): Modifier {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered = if (isDesktop && enabled && hoverScale != 1.0f) {
        interactionSource.collectIsHoveredAsState().value
    } else {
        false
    }
    val targetScale = when {
        !enabled -> 1f
        isPressed -> pressScale
        isHovered -> hoverScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "StudioTactileScale"
    )
    return this
        .then(if (enabled && isDesktop) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

