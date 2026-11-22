package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

private const val DIALOG_EXIT_DURATION_MS = 148

val LocalStudioDialogVisible = compositionLocalOf { true }
private val LocalInsideStudioModal = compositionLocalOf { false }

/**
 * Absorbs pointer taps inside a visible dialog card (`Surface` / `Card`) so clicks on card
 * whitespace never fall through to the backdrop scrim, while clicks outside the card's bounds
 * reach the scrim cleanly.
 */
fun Modifier.consumeDialogCardTaps(): Modifier = this.pointerInput(Unit) {
    detectTapGestures { /* Absorb tap inside dialog card bounds */ }
}

/**
 * Represents a stacked modal dialog entry hosted inside the Desktop root window canvas
 * so both backdrop scrim and dialog card animate smoothly on enter AND exit without
 * AWT/Skia sub-window flash artifacts.
 */
class DesktopModalEntry internal constructor(
    val id: Long,
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean,
    dismissOnClickOutside: Boolean,
    usePlatformDefaultWidth: Boolean,
    content: @Composable () -> Unit
) {
    var onDismissRequest by mutableStateOf(onDismissRequest)
    var dismissOnBackPress by mutableStateOf(dismissOnBackPress)
    var dismissOnClickOutside by mutableStateOf(dismissOnClickOutside)
    var usePlatformDefaultWidth by mutableStateOf(usePlatformDefaultWidth)
    var isExiting by mutableStateOf(false)
    var content by mutableStateOf(content, neverEqualPolicy())
}

object DesktopModalManager {
    private val idCounter = AtomicLong(1L)

    var isOverlayHostActive by mutableStateOf(false)
        internal set

    val entries = mutableStateListOf<DesktopModalEntry>()

    internal fun nextId(): Long = idCounter.getAndIncrement()

    internal fun mount(entry: DesktopModalEntry) {
        entry.isExiting = false
        if (entries.none { it.id == entry.id }) {
            entries.add(entry)
        }
    }

    internal fun beginUnmount(entry: DesktopModalEntry) {
        if (entries.any { it.id == entry.id }) {
            entry.isExiting = true
        }
    }

    internal fun completeRemoval(entry: DesktopModalEntry) {
        if (entry.isExiting) {
            entries.removeAll { it.id == entry.id }
        }
    }
}

/**
 * Root overlay host mounted in `DesktopMain.kt` above `MainScreen` (and below floating Toasts).
 * Renders all active [StudioModalDialog] and [StudioAlertDialog] instances with bidirectional
 * backdrop scrim fade, spring entrance, and smooth exit fade/settle.
 */
@Composable
fun DesktopModalHost(modifier: Modifier = Modifier) {
    DisposableEffect(Unit) {
        DesktopModalManager.isOverlayHostActive = true
        onDispose {
            DesktopModalManager.isOverlayHostActive = false
            DesktopModalManager.entries.clear()
        }
    }

    val activeEntries = DesktopModalManager.entries
    if (activeEntries.isEmpty()) return

    Box(modifier = modifier.fillMaxSize()) {
        activeEntries.forEachIndexed { index, entry ->
            key(entry.id) {
                DesktopModalLayer(
                    entry = entry,
                    isNestedModal = index > 0
                )
            }
        }
    }
}

@Composable
private fun DesktopModalLayer(
    entry: DesktopModalEntry,
    isNestedModal: Boolean
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        entered = true
    }

    LaunchedEffect(entry.isExiting) {
        if (entry.isExiting) {
            delay(DIALOG_EXIT_DURATION_MS.toLong() + 12L)
            DesktopModalManager.completeRemoval(entry)
        }
    }

    val isVisibleTarget = entered && !entry.isExiting
    SuspendDesktopShortcutsWhen(!entry.isExiting)

    BackHandler(enabled = !entry.isExiting && entry.dismissOnBackPress) {
        entry.onDismissRequest()
    }

    val maxScrimAlpha = if (isNestedModal) 0.30f else 0.46f
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisibleTarget) maxScrimAlpha else 0f,
        animationSpec = if (isVisibleTarget) {
            tween(durationMillis = 180, easing = LinearOutSlowInEasing)
        } else {
            tween(durationMillis = DIALOG_EXIT_DURATION_MS, easing = FastOutLinearInEasing)
        },
        label = "DesktopModalScrimAlpha"
    )

    val cardScale by animateFloatAsState(
        targetValue = when {
            entry.isExiting -> 0.94f
            entered -> 1f
            else -> 0.92f
        },
        animationSpec = if (!entry.isExiting) {
            spring(dampingRatio = 0.80f, stiffness = 430f)
        } else {
            tween(durationMillis = DIALOG_EXIT_DURATION_MS, easing = FastOutLinearInEasing)
        },
        label = "DesktopModalCardScale"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isVisibleTarget) 1f else 0f,
        animationSpec = if (isVisibleTarget) {
            tween(durationMillis = 175, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = DIALOG_EXIT_DURATION_MS - 10, easing = FastOutLinearInEasing)
        },
        label = "DesktopModalCardAlpha"
    )

    val density = LocalDensity.current
    val enterOffsetPx = with(density) { 12.dp.toPx() }
    val exitOffsetPx = with(density) { 8.dp.toPx() }
    val cardTranslationY by animateFloatAsState(
        targetValue = when {
            entry.isExiting -> exitOffsetPx
            entered -> 0f
            else -> enterOffsetPx
        },
        animationSpec = if (!entry.isExiting) {
            tween(durationMillis = 200, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = DIALOG_EXIT_DURATION_MS, easing = FastOutLinearInEasing)
        },
        label = "DesktopModalCardTranslateY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (entry.isExiting) {
                    // Consume pointer events during exit fade so clicks cannot double-trigger buttons
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Animated backdrop scrim (clicking outside dismisses if allowed)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(Color.Black)
                .pointerInput(entry.dismissOnClickOutside, entry.isExiting) {
                    detectTapGestures {
                        if (!entry.isExiting && entry.dismissOnClickOutside) {
                            entry.onDismissRequest()
                        }
                    }
                }
        )

        // Dialog content container (individual dialog card Surface/Card consumes its own interior taps)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .then(
                    if (entry.usePlatformDefaultWidth) {
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .widthIn(max = 560.dp)
                    } else {
                        Modifier
                    }
                )
                .semantics { dialog() }
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                    translationY = cardTranslationY
                }
        ) {
            CompositionLocalProvider(
                LocalInsideStudioModal provides true,
                LocalStudioDialogVisible provides isVisibleTarget
            ) {
                entry.content()
            }
        }
    }
}

/**
 * Retains composition of an Android dialog during its exit transition (`148ms`).
 * On Desktop (`DesktopModalManager.isOverlayHostActive == true`), `DesktopModalHost` already
 * retains and animates the exiting snapshot automatically, so we avoid doubling the delay.
 */
@Composable
fun AnimatedDialogVisibility(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    if (DesktopModalManager.isOverlayHostActive) {
        if (visible) {
            content()
        }
        return
    }

    var shouldCompose by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            shouldCompose = true
        } else if (shouldCompose) {
            delay(DIALOG_EXIT_DURATION_MS.toLong())
            shouldCompose = false
        }
    }

    if (shouldCompose) {
        CompositionLocalProvider(LocalStudioDialogVisible provides visible) {
            content()
        }
    }
}

/**
 * Nullable-state counterpart to [AnimatedDialogVisibility] that caches the last non-null value
 * so exit animations have valid immutable data throughout their `148ms` fade-out.
 */
@Composable
fun <T : Any> AnimatedNullableDialogVisibility(
    value: T?,
    content: @Composable (T) -> Unit
) {
    if (DesktopModalManager.isOverlayHostActive) {
        if (value != null) {
            content(value)
        }
        return
    }

    var cachedValue by remember { mutableStateOf(value) }
    var isShowing by remember { mutableStateOf(value != null) }

    if (value != null) {
        cachedValue = value
    }

    LaunchedEffect(value != null) {
        if (value != null) {
            isShowing = true
        } else if (isShowing) {
            delay(DIALOG_EXIT_DURATION_MS.toLong())
            isShowing = false
            cachedValue = null
        }
    }

    val current = cachedValue
    if (isShowing && current != null) {
        CompositionLocalProvider(LocalStudioDialogVisible provides (value != null)) {
            content(current)
        }
    }
}

/**
 * Cross-platform Studio Modal Dialog with smooth spring entrance AND crisp exit transition.
 * - On Desktop (`DesktopModalHost` active): renders inside the unified hardware-accelerated
 *   window overlay stack with animated backdrop scrim, `Esc` support, and shortcut suspension.
 * - On Android / standalone tests: delegates to `Dialog` with [StudioDialogEnterAnimation].
 */
@Composable
@NonSkippableComposable
fun StudioModalDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    if (DesktopModalManager.isOverlayHostActive) {
        val entry = remember {
            DesktopModalEntry(
                id = DesktopModalManager.nextId(),
                onDismissRequest = onDismissRequest,
                dismissOnBackPress = properties.dismissOnBackPress,
                dismissOnClickOutside = properties.dismissOnClickOutside,
                usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
                content = content
            )
        }
        entry.onDismissRequest = onDismissRequest
        entry.dismissOnBackPress = properties.dismissOnBackPress
        entry.dismissOnClickOutside = properties.dismissOnClickOutside
        entry.usePlatformDefaultWidth = properties.usePlatformDefaultWidth
        entry.content = content

        DisposableEffect(entry) {
            DesktopModalManager.mount(entry)
            onDispose {
                DesktopModalManager.beginUnmount(entry)
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            SuspendDesktopShortcutsWhen(true)
            StudioDialogEnterAnimation {
                CompositionLocalProvider(LocalInsideStudioModal provides true) {
                    content()
                }
            }
        }
    }
}

/**
 * Drop-in replacement for Material 3 [AlertDialog] with smooth bidirectional enter & exit
 * animations on both Desktop and Android.
 */
@Composable
@NonSkippableComposable
fun StudioAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    val windowMetrics = LocalAppWindowMetrics.current
    if (DesktopModalManager.isOverlayHostActive) {
        StudioModalDialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = properties.dismissOnBackPress,
                dismissOnClickOutside = properties.dismissOnClickOutside,
                usePlatformDefaultWidth = false
            )
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                val availWidth = if (maxWidth.value.isFinite() && maxWidth > 0.dp) maxWidth else windowMetrics.widthDp
                val availHeight = if (maxHeight.value.isFinite() && maxHeight > 0.dp) maxHeight else windowMetrics.heightDp
                val computedMaxWidth = (availWidth - 32.dp).coerceAtMost(500.dp).coerceAtLeast(220.dp)
                val computedMinWidth = (availWidth - 32.dp).coerceAtMost(280.dp).coerceAtLeast(200.dp)
                val computedMaxHeight = (availHeight * 0.88f).coerceAtLeast(220.dp)

                Surface(
                    modifier = modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .widthIn(min = computedMinWidth, max = computedMaxWidth)
                        .heightIn(max = computedMaxHeight)
                        .consumeDialogCardTaps(),
                    shape = shape,
                    color = containerColor,
                    tonalElevation = tonalElevation,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                                    icon()
                                }
                            }
                        }
                        if (title != null) {
                            Box(
                                modifier = Modifier.align(
                                    if (icon != null) Alignment.CenterHorizontally else Alignment.Start
                                )
                            ) {
                                CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                                        title()
                                    }
                                }
                            }
                        }
                        if (text != null) {
                            val textScrollState = rememberScrollState()
                            val hasVerticalOverflow = textScrollState.maxValue > 0
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(textScrollState)
                                        .padding(
                                            end = if (hasVerticalOverflow && DesktopAdaptiveState.isDesktopPlatform) 10.dp else 0.dp
                                        )
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides textContentColor) {
                                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                            text()
                                        }
                                    }
                                }
                                if (hasVerticalOverflow) {
                                    PlatformVerticalScrollbar(
                                        scrollState = textScrollState,
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                            }
                            confirmButton()
                        }
                    }
                }
            }
        }
    } else {
        val visible = LocalStudioDialogVisible.current
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            entered = true
        }
        val showTarget = entered && visible
        val scale by animateFloatAsState(
            targetValue = when {
                !visible -> 0.94f
                entered -> 1f
                else -> 0.92f
            },
            animationSpec = if (visible) {
                spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
            } else {
                tween(durationMillis = DIALOG_EXIT_DURATION_MS, easing = FastOutLinearInEasing)
            },
            label = "AndroidAlertScale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (showTarget) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (visible) 175 else DIALOG_EXIT_DURATION_MS,
                easing = if (visible) FastOutSlowInEasing else FastOutLinearInEasing
            ),
            label = "AndroidAlertAlpha"
        )
        val androidMaxHeight = (windowMetrics.heightDp * 0.88f).coerceAtLeast(220.dp)

        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier
                .heightIn(max = androidMaxHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text?.let { body ->
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        body()
                    }
                }
            },
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties
        )
    }
}

/**
 * Material You bidirectional spring scale + fade + gentle lift wrapper for custom Dialogs.
 * Automatically avoids duplicating transforms when already hosted inside [StudioModalDialog].
 */
@Composable
fun StudioDialogEnterAnimation(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit
) {
    if (LocalInsideStudioModal.current) {
        Box(
            modifier = modifier,
            contentAlignment = contentAlignment
        ) {
            content()
        }
        return
    }

    val isVisible = LocalStudioDialogVisible.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        entered = true
    }

    val showTarget = entered && isVisible
    val scale by animateFloatAsState(
        targetValue = when {
            !isVisible -> 0.94f
            entered -> 1f
            else -> 0.90f
        },
        animationSpec = if (isVisible) {
            spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow
            )
        } else {
            tween(durationMillis = DIALOG_EXIT_DURATION_MS, easing = FastOutLinearInEasing)
        },
        label = "StudioDialogScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (showTarget) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 185 else DIALOG_EXIT_DURATION_MS,
            easing = if (isVisible) FastOutSlowInEasing else FastOutLinearInEasing
        ),
        label = "StudioDialogAlpha"
    )
    val density = LocalDensity.current
    val liftPx = with(density) { 14.dp.toPx() }
    val exitSettlePx = with(density) { 8.dp.toPx() }
    val translationY by animateFloatAsState(
        targetValue = when {
            !isVisible -> exitSettlePx
            entered -> 0f
            else -> liftPx
        },
        animationSpec = tween(
            durationMillis = if (isVisible) 210 else DIALOG_EXIT_DURATION_MS,
            easing = if (isVisible) FastOutSlowInEasing else FastOutLinearInEasing
        ),
        label = "StudioDialogLift"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
            this.translationY = translationY
        },
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

/**
 * Unified adaptive sheet/dialog container:
 * - On Desktop (`DesktopModalManager.isOverlayHostActive`) or wide windows (`>= 700.dp`),
 *   renders as a centered [StudioModalDialog] + [AdaptiveModalSurface] with smooth spring
 *   enter/exit and `Esc` dismissal.
 * - On narrow mobile screens, preserves native Material 3 [ModalBottomSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioAdaptiveSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    maxWidth: Dp = 560.dp,
    modifier: Modifier = Modifier,
    content: @Composable (onRequestClose: () -> Unit) -> Unit
) {
    val windowMetrics = LocalAppWindowMetrics.current
    val useDialog = DesktopModalManager.isOverlayHostActive || windowMetrics.widthDp >= 700.dp

    if (useDialog) {
        AnimatedDialogVisibility(visible = visible) {
            StudioModalDialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AdaptiveModalSurface(
                    targetMaxWidth = maxWidth,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = modifier
                ) {
                    content(onDismissRequest)
                }
            }
        }
    } else if (visible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            modifier = modifier
        ) {
            content {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismissRequest()
                }
            }
        }
    }
}
