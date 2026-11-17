package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.example.i18n.S
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.KeycapBadge
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.rememberStudioInteraction
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.tan

private data class RecordingTipPage(
    val canvasIndex: Int,
    val title: String,
    val description: String
)

private val SIMPLE_MODE_TIP_INDICES = setOf(0, 1, 3, 4, 5, 8)

private fun getRecordingTipPages(
    isDesktop: Boolean,
    isSimpleMode: Boolean = false
): List<RecordingTipPage> {
    val tips = S.tips
    val allPages = listOf(
        RecordingTipPage(
            canvasIndex = 0,
            title = tips.tip1.title,
            description = tips.tip1.description
        ),
        RecordingTipPage(
            canvasIndex = 1,
            title = tips.tip2.title,
            description = tips.tip2.description
        ),
        RecordingTipPage(
            canvasIndex = 2,
            title = tips.tip3.title,
            description = tips.tip3.description
        ),
        if (isDesktop) {
            RecordingTipPage(
                canvasIndex = 3,
                title = tips.tip4Desktop.title,
                description = tips.tip4Desktop.description
            )
        } else {
            RecordingTipPage(
                canvasIndex = 3,
                title = tips.tip4Mobile.title,
                description = tips.tip4Mobile.description
            )
        },
        RecordingTipPage(
            canvasIndex = 4,
            title = tips.tip5Title,
            description = if (isDesktop) tips.tip5DescDesktop else tips.tip5DescMobile
        ),
        RecordingTipPage(
            canvasIndex = 5,
            title = tips.tip6.title,
            description = tips.tip6.description
        ),
        RecordingTipPage(
            canvasIndex = 6,
            title = tips.tip7.title,
            description = tips.tip7.description
        ),
        RecordingTipPage(
            canvasIndex = 7,
            title = tips.tip8Title,
            description = tips.tip8DescDesktop
        ),
        RecordingTipPage(
            canvasIndex = 8,
            title = tips.tip9Title,
            description = if (isSimpleMode) {
                if (isDesktop) tips.tip9DescSimpleDesktop else tips.tip9DescSimpleMobile
            } else {
                tips.tip9DescAdv
            }
        )
    )
    return if (isSimpleMode) {
        allPages.filter { it.canvasIndex in SIMPLE_MODE_TIP_INDICES }
    } else {
        allPages
    }
}

/**
 * 滑动卡片式「大图动态引导」录音小贴士弹窗（支持桌面端双栏目录直达、横屏自适应与方向键翻页）
 */
@Composable
fun RecordingTipsDialog(
    onDismiss: () -> Unit,
    isFirstLaunch: Boolean = false,
    isSimpleMode: Boolean = false
) {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val tipPages = remember(isDesktop, isSimpleMode) { getRecordingTipPages(isDesktop, isSimpleMode) }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tipPages.size }
    )
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    val canvasBgColor = if (isDark) {
        lerp(colorScheme.surfaceVariant, colorScheme.primaryContainer, 0.25f)
    } else {
        lerp(colorScheme.surfaceVariant.copy(alpha = 0.55f), colorScheme.primaryContainer.copy(alpha = 0.45f), 0.5f)
    }

    StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val isWideDialog = maxWidth >= 640.dp && maxHeight >= 450.dp
            val isCompactLandscapeDialog = !isWideDialog && maxWidth >= 480.dp && maxHeight < 450.dp
            val isFirstPage = pagerState.currentPage == 0
            val isLastPage = pagerState.currentPage == tipPages.lastIndex

            Surface(
                modifier = Modifier
                    .widthIn(
                        max = when {
                            isWideDialog -> 608.dp
                            isCompactLandscapeDialog -> 596.dp
                            else -> 360.dp
                        }
                    )
                    .heightIn(max = maxHeight)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionLeft, Key.DirectionUp, Key.PageUp -> {
                                if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                                true
                            }
                            Key.DirectionRight, Key.DirectionDown, Key.PageDown -> {
                                if (pagerState.currentPage < tipPages.lastIndex) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    }
                    .testTag("recording_tips_dialog"),
                shape = RoundedCornerShape(if (isCompactLandscapeDialog) 22.dp else 26.dp),
                color = colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isWideDialog) 20.dp else 16.dp,
                            end = if (isWideDialog) 20.dp else 16.dp,
                            top = if (isCompactLandscapeDialog) 10.dp else 14.dp,
                            bottom = if (isCompactLandscapeDialog) 12.dp else 16.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row (with Skip button moved here so bottom controls never crowd or wrap)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.primaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = S.tips.headerTitle,
                                fontSize = if (isWideDialog) 14.5.sp else 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colorScheme.secondaryContainer.copy(alpha = 0.75f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${pagerState.currentPage + 1} / ${tipPages.size}",
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            if (isWideDialog && isDesktop) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    KeycapBadge("← / →")
                                    Text(
                                        text = S.tips.keyboardSwitchBadge,
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (!isLastPage) {
                                TextButton(
                                    onClick = onDismiss,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 32.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .testTag("tips_skip_button")
                                ) {
                                    Text(
                                        text = S.common.skip,
                                        fontSize = 12.5.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("close_tips_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = S.common.close,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactLandscapeDialog) 8.dp else 12.dp))

                    when {
                        isWideDialog -> {
                            // Desktop / Tablet Wide 2-Column Layout: Left all-visible 9-tip index + Right 16:9 canvas
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .width(198.dp)
                                        .height(338.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = if (tipPages.size <= 5) 10.dp else 6.dp),
                                        verticalArrangement = if (tipPages.size <= 5) Arrangement.spacedBy(8.dp) else Arrangement.SpaceBetween
                                    ) {
                                        tipPages.forEachIndexed { idx, item ->
                                            val isSelected = pagerState.currentPage == idx
                                            val (hoverSrc, isHoveredState) = rememberStudioInteraction()
                                            val isHovered by isHoveredState

                                            val itemBgColor by animateColorAsState(
                                                targetValue = when {
                                                    isSelected -> colorScheme.primaryContainer
                                                    isHovered -> lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.45f)
                                                    else -> colorScheme.surfaceContainerLow
                                                },
                                                animationSpec = tween(150),
                                                label = "TipDirBg_$idx"
                                            )
                                            val badgeBgColor by animateColorAsState(
                                                targetValue = when {
                                                    isSelected -> colorScheme.primary
                                                    isHovered -> lerp(colorScheme.surfaceContainerHigh, colorScheme.primaryContainer, 0.65f)
                                                    else -> colorScheme.surfaceContainerHigh
                                                },
                                                animationSpec = tween(150),
                                                label = "TipDirBadgeBg_$idx"
                                            )
                                            val badgeTextColor by animateColorAsState(
                                                targetValue = when {
                                                    isSelected -> colorScheme.onPrimary
                                                    isHovered -> colorScheme.primary
                                                    else -> colorScheme.onSurfaceVariant
                                                },
                                                animationSpec = tween(150),
                                                label = "TipDirBadgeTxt_$idx"
                                            )
                                            val titleTextColor by animateColorAsState(
                                                targetValue = when {
                                                    isSelected -> colorScheme.onPrimaryContainer
                                                    isHovered -> colorScheme.primary
                                                    else -> colorScheme.onSurface
                                                },
                                                animationSpec = tween(150),
                                                label = "TipDirTitleTxt_$idx"
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = itemBgColor,
                                                shadowElevation = 0.dp,
                                                tonalElevation = 0.dp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(if (tipPages.size <= 5) 44.dp else 33.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .hoverable(hoverSrc)
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .clickable(
                                                        interactionSource = hoverSrc,
                                                        indication = null
                                                    ) {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(idx)
                                                        }
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = badgeBgColor,
                                                        modifier = Modifier.size(19.dp)
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxSize()
                                                        ) {
                                                            Text(
                                                                text = "${idx + 1}",
                                                                fontSize = 10.sp,
                                                                lineHeight = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = badgeTextColor,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = item.title,
                                                        fontSize = 11.5.sp,
                                                        lineHeight = 14.sp,
                                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                        color = titleTextColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Right Column: HorizontalPager (16:9 Canvas) + Controls
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(338.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .testTag("recording_tips_pager"),
                                        pageSpacing = 12.dp,
                                        key = { tipPages[it].canvasIndex }
                                    ) { pageIndex ->
                                        val page = tipPages[pageIndex]
                                        val isPageActive = pagerState.currentPage == pageIndex
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(344.dp)
                                                    .height(192.dp)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(canvasBgColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AnimatedTipPageCanvas(
                                                    pageIndex = page.canvasIndex,
                                                    isPageActive = isPageActive,
                                                    colorScheme = colorScheme,
                                                    isDark = isDark
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = page.title,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.height(5.dp))

                                            Text(
                                                text = page.description,
                                                fontSize = 12.5.sp,
                                                color = colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 18.sp,
                                                maxLines = 2,
                                                minLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }

                                    // Bottom Controls inside Right Column
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            repeat(tipPages.size) { index ->
                                                val isSelected = pagerState.currentPage == index
                                                val dotWidth by animateDpAsState(
                                                    targetValue = if (isSelected) 18.dp else 6.dp,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                    label = "DotWidthWide"
                                                )
                                                val dotColor by animateColorAsState(
                                                    targetValue = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                                                    label = "DotColorWide"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .height(6.dp)
                                                        .width(dotWidth)
                                                        .clip(CircleShape)
                                                        .background(dotColor)
                                                        .clickable {
                                                            scope.launch { pagerState.animateScrollToPage(index) }
                                                        }
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (!isFirstPage) {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                        }
                                                    }
                                                },
                                                enabled = !isFirstPage,
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .testTag("tips_prev_button")
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    S.tips.previousTip,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    if (!isLastPage) {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                        }
                                                    } else {
                                                        onDismiss()
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .testTag("tips_next_or_done_button")
                                            ) {
                                                Text(
                                                    text = when {
                                                        !isLastPage -> S.tips.nextTip
                                                        isFirstLaunch -> S.tips.startRecordingAction
                                                        else -> S.common.gotIt
                                                    },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                if (!isLastPage) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowForward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        isCompactLandscapeDialog -> {
                            // Mobile Landscape (e.g. 800x360) Sleek 2-Column Pager Layout: Zero vertical clipping!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .weight(1.06f)
                                        .heightIn(min = 140.dp, max = 196.dp)
                                        .fillMaxHeight()
                                        .testTag("recording_tips_pager"),
                                    pageSpacing = 12.dp,
                                    key = { tipPages[it].canvasIndex }
                                ) { pageIndex ->
                                    val page = tipPages[pageIndex]
                                    val isPageActive = pagerState.currentPage == pageIndex
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(canvasBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedTipPageCanvas(
                                            pageIndex = page.canvasIndex,
                                            isPageActive = isPageActive,
                                            colorScheme = colorScheme,
                                            isDark = isDark
                                        )
                                    }
                                }

                                val currentPage = tipPages[pagerState.currentPage]
                                Column(
                                    modifier = Modifier
                                        .weight(0.94f)
                                        .heightIn(min = 140.dp, max = 196.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = currentPage.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = currentPage.description,
                                            fontSize = 12.5.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            lineHeight = 18.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            repeat(tipPages.size) { index ->
                                                val isSelected = pagerState.currentPage == index
                                                val dotWidth by animateDpAsState(
                                                    targetValue = if (isSelected) 16.dp else 5.dp,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                    label = "DotWidthLand"
                                                )
                                                val dotColor by animateColorAsState(
                                                    targetValue = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                                                    label = "DotColorLand"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .height(5.dp)
                                                        .width(dotWidth)
                                                        .clip(CircleShape)
                                                        .background(dotColor)
                                                        .clickable {
                                                            scope.launch { pagerState.animateScrollToPage(index) }
                                                        }
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!isFirstPage) {
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.testTag("tips_prev_button")
                                                ) {
                                                    Text(
                                                        text = S.tips.previousTip,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Button(
                                                onClick = {
                                                    if (!isLastPage) {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                        }
                                                    } else {
                                                        onDismiss()
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("tips_next_or_done_button")
                                            ) {
                                                Text(
                                                    text = when {
                                                        !isLastPage -> S.tips.nextTip
                                                        isFirstLaunch -> S.tips.startRecordingAction
                                                        else -> S.common.gotIt
                                                    },
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // Mobile Compact Single-Column Portrait Layout
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recording_tips_pager"),
                                pageSpacing = 12.dp,
                                key = { tipPages[it].canvasIndex }
                            ) { pageIndex ->
                                val page = tipPages[pageIndex]
                                val isPageActive = pagerState.currentPage == pageIndex
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(164.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(canvasBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedTipPageCanvas(
                                            pageIndex = page.canvasIndex,
                                            isPageActive = isPageActive,
                                            colorScheme = colorScheme,
                                            isDark = isDark
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = page.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = page.description,
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 19.sp,
                                        maxLines = 2,
                                        minLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bottom Bar: Left 9 Page Indicator Dots, Right Prev / Next / Done Button (Never wraps!)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    repeat(tipPages.size) { index ->
                                        val isSelected = pagerState.currentPage == index
                                        val dotWidth by animateDpAsState(
                                            targetValue = if (isSelected) 14.dp else 5.dp,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                            label = "DotWidth"
                                        )
                                        val dotColor by animateColorAsState(
                                            targetValue = if (isSelected) {
                                                colorScheme.primary
                                            } else {
                                                colorScheme.outlineVariant
                                            },
                                            label = "DotColor"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .height(5.dp)
                                                .width(dotWidth)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                                .clickable {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(index)
                                                    }
                                                }
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!isFirstPage) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                            modifier = Modifier.testTag("tips_prev_button")
                                        ) {
                                            Text(
                                                text = S.tips.previousTip,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (!isLastPage) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            } else {
                                                onDismiss()
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                                        modifier = Modifier.testTag("tips_next_or_done_button")
                                    ) {
                                        Text(
                                            text = when {
                                                !isLastPage -> S.tips.nextTip
                                                isFirstLaunch -> S.tips.startRecordingAction
                                                else -> S.common.gotIt
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedTipPageCanvas(
    pageIndex: Int,
    isPageActive: Boolean,
    colorScheme: ColorScheme,
    isDark: Boolean
) {
    val cycleDurationMs = when (pageIndex) {
        0, 1, 7 -> 5000
        2 -> 5400
        3 -> 6000
        4, 8 -> 4000
        5 -> 5600
        6 -> 5200
        else -> 5000
    }

    val t = if (isPageActive) {
        val transition = rememberInfiniteTransition(label = "TipPageClock_$pageIndex")
        val animVal by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cycleDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "TipClockValue"
        )
        animVal
    } else {
        0f
    }

    when (pageIndex) {
        0 -> TipIllustrationSmartPaste(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
        1 -> TipIllustrationSharedWordlist(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
        2 -> TipIllustrationShuffleAndFocus(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
        3 -> TipIllustrationLargeTextAndVolumeKey(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
        4 -> TipIllustrationDistance(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
        5 -> TipIllustrationVolumeBar(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
        6 -> TipIllustrationQuickNotesAndTakes(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
        7 -> TipIllustrationPitchCalibration(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
        8 -> TipIllustrationPlaybackAndExport(
            colorScheme = colorScheme,
            isDark = isDark,
            t = t,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ============================================================================
// KEYFRAME & CUBIC-BEZIER ANIMATION DELEGATES (Backed by TipAnimationEngine)
// ============================================================================

private typealias BezierCurve = TipBezierCurve
private val EaseInOut = TipEaseInOut
private val EaseStandard = TipEaseStandard
private val EaseSmooth = TipEaseSmooth
private val EaseSym = TipEaseSym
private val EaseOutBack = TipEaseOutBack
private val EaseOutQuart = TipEaseOutQuart
private val EaseOutExpo = TipEaseOutExpo
private val EaseSpringIn = TipEaseSpringIn
private val EaseStepEnd = TipEaseStepEnd

private data class Kf(
    val pct: Float,
    val v: Float,
    val curve: BezierCurve = EaseInOut
)

private fun evalKf(t: Float, vararg frames: Kf): Float {
    if (frames.isEmpty()) return 0f
    val p = t.coerceIn(0f, 1f) * 100f
    if (p <= frames.first().pct) return frames.first().v
    if (p >= frames.last().pct) return frames.last().v
    for (i in 0 until frames.size - 1) {
        val a = frames[i]
        val b = frames[i + 1]
        if (p <= b.pct) {
            val span = b.pct - a.pct
            if (span <= 1e-5f) return b.v
            val localT = (p - a.pct) / span
            val eased = a.curve.evaluate(localT)
            return a.v + (b.v - a.v) * eased
        }
    }
    return frames.last().v
}

private fun lerpF(a: Float, b: Float, f: Float): Float = lerpTipF(a, b, f)

private fun DrawScope.withPivotTransform(
    pivotX: Float,
    pivotY: Float,
    tx: Float = 0f,
    ty: Float = 0f,
    sx: Float = 1f,
    sy: Float = sx,
    rotDeg: Float = 0f,
    skewXDeg: Float = 0f,
    block: DrawScope.() -> Unit
) = withTipPivot(pivotX, pivotY, tx, ty, sx, sy, rotDeg, skewXDeg, block)

private fun svgPath(pathData: String, evenOdd: Boolean = false): Path =
    parseTipSvgPath(pathData, evenOdd)

// ============================================================================
// PAGE 1 (INDEX 0): 保持安静与合适距离 (4.0s Master Non-Linear Animation)
// ============================================================================

private object CachedTipDistancePaths {
    val collar by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 95 430 L 195 492 C 250 536, 304 602, 352 673 L 382 715 L 95 715 Z") }
    val lowerBackHair by lazy(LazyThreadSafetyMode.NONE) { svgPath("M -40 -35 L -40 500 L 120 530 L 135 546 L 185 498 L 184 552 C 220 514, 254 475, 276 440 L 210 350 Z") }
    val neckShadow by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 60 225 L 67 244 C 75 259, 86 270, 96 273 C 74 345, 46 415, 20 485 L -30 500 L -30 560 L 105 560 C 126 505, 155 455, 186 405 L 215 385 L 160 220 Z") }
    val sweater by lazy(LazyThreadSafetyMode.NONE) { svgPath("M -30 422 L 20 436 C 108 462, 188 545, 256 673 L 284 715 L -30 715 Z") }
    val upperHair1 by lazy(LazyThreadSafetyMode.NONE) {
        svgPath(
            "M -40 -35 L 384 -35 L 363 13 C 340 65, 312 115, 285 160 C 295 128, 304 100, 312 74 " +
                "C 245 125, 178 176, 115 226 C 129 192, 142 159, 153 128 C 116 156, 82 184, 48 212 L -40 212 Z"
        )
    }
    val upperHair2 by lazy(LazyThreadSafetyMode.NONE) {
        svgPath(
            "M 412 -35 L 561 -35 L 557 13 C 552 68, 535 120, 505 162 C 510 120, 513 72, 511 28 " +
                "C 492 78, 462 120, 426 152 C 422 145, 418 138, 414 131 C 402 145, 391 159, 379 172 " +
                "C 385 118, 393 65, 403 13 Z"
        )
    }
    val waveTravel by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 516 385 Q 550 452, 469 517") }
    val wave1a by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 473 420 Q 486 458, 435 495") }
    val wave1b by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 465 394 Q 484 445, 429 490") }
    val wave2a by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 516 385 Q 550 452, 469 517") }
    val wave2b by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 509 384 Q 538 450, 461 513") }
    val wave3a by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 555 470 Q 538 504, 501 526") }
    val wave3b by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 556 378 Q 596 450, 513 519") }
    val backHand by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 507 740 L 547 673 L 650 560 L 852 468 C 868 477, 877 492, 870 508 C 856 535, 768 604, 712 673 L 658 740 Z") }
    val frontThumb by lazy(LazyThreadSafetyMode.NONE) {
        svgPath(
            "M 507 740 L 547 673 C 580 618, 636 525, 672 466 C 684 446, 709 445, 720 462 " +
                "C 727 473, 725 489, 717 506 C 705 533, 692 561, 680 588 L 712 673 L 658 740 Z"
        )
    }
    val leftArrowHead by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 507 279 L 535 268 C 538 267, 540 270, 539 273 L 523 306 C 521 309, 518 309, 516 306 L 506 283 C 505 281, 505 279, 507 279 Z") }
    val rightArrowHead by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 803 404 L 789 367 C 788 364, 785 364, 783 367 L 767 408 C 766 411, 768 413, 771 412 L 801 407 C 803 407, 804 405, 803 404 Z") }

    val glyph1 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 682 199 L 692 199 L 692 247 L 682 247 L 682 210 L 672 216 L 668 208 L 682 199 Z") }
    val glyph5 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 709 199 L 739 199 L 739 208 L 718 208 L 717 217 C 720 216, 723 216, 726 216 C 736 216, 742 222, 742 231 C 742 241, 734 248, 721 248 C 714 248, 709 246, 706 244 L 709 236 C 712 238, 716 239, 721 239 C 728 239, 732 236, 732 231 C 732 226, 727 224, 719 224 C 715 224, 711 224, 708 225 L 709 199 Z") }
    val glyph2 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 780 205 C 784 201, 790 198, 797 198 C 808 198, 814 204, 814 212 C 814 219, 809 225, 799 233 L 792 238 L 815 238 L 815 247 L 780 247 L 780 239 L 793 227 C 801 220, 804 216, 804 212 C 804 208, 801 206, 796 206 C 791 206, 787 208, 784 211 L 780 205 Z") }
    val glyph0 by lazy(LazyThreadSafetyMode.NONE) {
        svgPath(
            "M 842 198 C 854 198, 861 207, 861 223 C 861 239, 854 248, 842 248 C 830 248, 823 239, 823 223 C 823 207, 830 198, 842 198 Z " +
                "M 842 207 C 836 207, 833 212, 833 223 C 833 234, 836 239, 842 239 C 848 239, 851 234, 851 223 C 851 212, 848 207, 842 207 Z",
            evenOdd = true
        )
    }
    val glyphC by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 897 245 C 894 247, 890 248, 886 248 C 875 248, 869 240, 869 229 C 869 218, 875 210, 886 210 C 890 210, 894 211, 897 213 L 895 221 C 892 219, 890 219, 887 219 C 881 219, 879 223, 879 229 C 879 235, 881 239, 887 239 C 890 239, 893 238, 895 237 L 897 245 Z") }
    val glyphM by lazy(LazyThreadSafetyMode.NONE) { svgPath("M 904 211 L 913 211 L 913 215 C 916 212, 919 210, 924 210 C 929 210, 932 212, 934 216 C 937 212, 941 210, 945 210 C 952 210, 956 215, 956 223 L 956 247 L 946 247 L 946 225 C 946 220, 944 218, 941 218 C 938 218, 935 220, 935 225 L 935 247 L 925 247 L 925 225 C 925 220, 923 218, 920 218 C 917 218, 914 220, 914 225 L 914 247 L 904 247 L 904 211 Z") }
}

@Composable
private fun TipIllustrationDistance(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val paths = CachedTipDistancePaths
    val dynamicMouthPath = remember { Path() }
    val dynamicFacePath = remember { Path() }
    val frameClipPath = remember {
        Path().apply {
            addRoundRect(RoundRect(Rect(20f, 13f, 1228f, 673f), CornerRadius(64f, 64f)))
        }
    }
    val phoneClipPath = remember {
        Path().apply {
            addRoundRect(RoundRect(Rect(offset = Offset(-12f, 0f), size = Size(527f, 59.2f)), CornerRadius(22f, 22f)))
        }
    }
    val badgeClipPath = remember {
        Path().apply {
            addRoundRect(RoundRect(Rect(offset = Offset(635f, 179f), size = Size(325f, 88f)), CornerRadius(44f, 44f)))
        }
    }

    // Dynamic colors aligned with Material 3 theme
    val outerCreamBg = if (isDark) {
        lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.15f)
    } else {
        lerp(colorScheme.surface, colorScheme.primaryContainer, 0.18f)
    }
    val circleBgColor = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.44f else 0.74f)
    val auraRingColor = colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.25f)
    val hairAndBadgeColor = if (isDark) {
        lerp(colorScheme.primary, colorScheme.primaryContainer, 0.2f)
    } else {
        lerp(colorScheme.primary, Color(0xFF2B3E5E), 0.35f)
    }
    val badgePopColor = lerp(hairAndBadgeColor, colorScheme.primary, 0.35f)
    val badgeTextColor = if (hairAndBadgeColor.luminance() > 0.5f) Color(0xFF1A212E) else Color.White
    val collarColor = colorScheme.surface
    val sweaterColor = lerp(colorScheme.secondaryContainer, colorScheme.primary, if (isDark) 0.3f else 0.25f)
    val skinColor = if (isDark) Color(0xFFEAC7B3) else Color(0xFFFADBC8)
    val skinShadowColor = if (isDark) Color(0xFFD5AD96) else Color(0xFFEDC4B1)
    val mouthColor = Color(0xFF9B534C)

    val phoneEndColor = lerp(hairAndBadgeColor, Color.White, 0.12f)
    val phoneBodyColor = lerp(hairAndBadgeColor, Color.Black, 0.14f)
    val phoneHighlightBase = lerp(hairAndBadgeColor, Color.White, 0.32f)
    val phoneHighlightActive = lerp(colorScheme.primary, Color.White, 0.45f)
    val phoneShimmerColor = lerp(colorScheme.primaryContainer, Color.White, 0.65f)

    val waveLightBase = colorScheme.primary.copy(alpha = if (isDark) 0.34f else 0.28f)
    val waveLightActive = lerp(colorScheme.primaryContainer, Color.White, 0.5f)
    val waveDarkBase = colorScheme.primary.copy(alpha = if (isDark) 0.56f else 0.46f)
    val waveDarkActive = colorScheme.primary

    // Curves from SVG 1
    val cOvershoot1 = BezierCurve(0.18f, 0.89f, 0.32f, 1.15f)
    val cOvershoot2 = BezierCurve(0.25f, 0.9f, 0.35f, 1.1f)
    val cFastOut = BezierCurve(0.45f, 0f, 0.2f, 1f)
    val cAura = BezierCurve(0.16f, 0.84f, 0.44f, 1f)
    val cTorso1 = BezierCurve(0.2f, 0.85f, 0.35f, 1.1f)
    val cHead2 = BezierCurve(0.22f, 0.88f, 0.36f, 1.12f)
    val cBangs1 = BezierCurve(0.18f, 0.89f, 0.32f, 1.25f)
    val cWave = BezierCurve(0.16f, 0.9f, 0.3f, 1.25f)
    val cLipSpline = BezierCurve(0.18f, 0.88f, 0.32f, 1f)

    Canvas(modifier = modifier) {
        val viewW = 1208f
        val viewH = 660f
        val scale = min(size.width / viewW, size.height / viewH)
        val offsetX = (size.width - viewW * scale) / 2f
        val offsetY = (size.height - viewH * scale) / 2f

        drawRect(color = outerCreamBg, size = size)

        withTransform({
            translate(offsetX - 20f * scale, offsetY - 13f * scale)
            scale(scale, scale, Offset.Zero)
        }) {
            clipPath(frameClipPath) {
                // 1. Outer Background
                drawRect(color = outerCreamBg, topLeft = Offset(20f, 13f), size = Size(1208f, 660f))

                // 2. Large Circular Background (bgBreathe)
                val bgTx = evalKf(t, Kf(0f, 0f), Kf(15f, -8f, cOvershoot1), Kf(38f, 14f, EaseSym), Kf(52f, 8f, cOvershoot2), Kf(66f, 12f, cFastOut), Kf(100f, 0f))
                val bgTy = evalKf(t, Kf(0f, 0f), Kf(15f, 4f, cOvershoot1), Kf(38f, -10f, EaseSym), Kf(52f, -5f, cOvershoot2), Kf(66f, -8f, cFastOut), Kf(100f, 0f))
                val bgScale = evalKf(t, Kf(0f, 1f), Kf(15f, 0.985f, cOvershoot1), Kf(38f, 1.035f, EaseSym), Kf(52f, 1.015f, cOvershoot2), Kf(66f, 1.028f, cFastOut), Kf(100f, 1f))
                withPivotTransform(535f, 535f, tx = bgTx, ty = bgTy, sx = bgScale) {
                    drawCircle(color = circleBgColor, radius = 603f, center = Offset(535f, 535f))
                }

                // Acoustic Aura Ring (bgAuraPulse)
                val auraAlpha = evalKf(t, Kf(0f, 0f), Kf(16f, 0f, cAura), Kf(28f, 0.45f), Kf(52f, 0f), Kf(53f, 0f), Kf(64f, 0.35f), Kf(88f, 0f), Kf(100f, 0f))
                if (auraAlpha > 0.01f) {
                    val auraTx = evalKf(t, Kf(0f, 0f), Kf(16f, 0f, cAura), Kf(52f, 110f), Kf(53f, 0f, cAura), Kf(88f, 100f), Kf(100f, 100f))
                    val auraTy = evalKf(t, Kf(0f, 0f), Kf(16f, 0f, cAura), Kf(52f, 48f), Kf(53f, 0f, cAura), Kf(88f, 44f), Kf(100f, 44f))
                    val auraScale = evalKf(t, Kf(0f, 0.35f), Kf(16f, 0.35f, cAura), Kf(52f, 1.35f), Kf(53f, 0.4f, cAura), Kf(88f, 1.25f), Kf(100f, 1.25f))
                    withPivotTransform(405f, 340f, tx = auraTx, ty = auraTy, sx = auraScale) {
                        drawCircle(
                            color = auraRingColor.copy(alpha = auraRingColor.alpha * auraAlpha),
                            radius = 240f,
                            center = Offset(405f, 340f),
                            style = Stroke(width = 28f)
                        )
                    }
                }

                // CHARACTER GROUP (torso-anim around 120, 650)
                val torsoTx = evalKf(t, Kf(0f, 0f), Kf(14f, -3f, cTorso1), Kf(34f, 5f, EaseSym), Kf(48f, 2f, cTorso1), Kf(62f, 4f, cFastOut), Kf(100f, 0f))
                val torsoTy = evalKf(t, Kf(0f, 0f), Kf(14f, 4f, cTorso1), Kf(34f, -5f, EaseSym), Kf(48f, -2f, cTorso1), Kf(62f, -4f, cFastOut), Kf(100f, 0f))
                val torsoRot = evalKf(t, Kf(0f, 0f), Kf(14f, -0.8f, cTorso1), Kf(34f, 1.2f, EaseSym), Kf(48f, 0.5f, cTorso1), Kf(62f, 0.9f, cFastOut), Kf(100f, 0f))

                val headTx = evalKf(t, Kf(0f, 0f), Kf(14f, -5f, cOvershoot1), Kf(32f, 8f, EaseSym), Kf(44f, 4f, cHead2), Kf(58f, 7f, cFastOut), Kf(78f, 2f, EaseStandard), Kf(100f, 0f))
                val headTy = evalKf(t, Kf(0f, 0f), Kf(14f, 2f, cOvershoot1), Kf(32f, -3f, EaseSym), Kf(44f, -1f, cHead2), Kf(58f, -2.5f, cFastOut), Kf(78f, -0.5f, EaseStandard), Kf(100f, 0f))
                val headRot = evalKf(t, Kf(0f, 0f), Kf(14f, -2.2f, cOvershoot1), Kf(32f, 3.4f, EaseSym), Kf(44f, 1.6f, cHead2), Kf(58f, 3.0f, cFastOut), Kf(78f, 0.7f, EaseStandard), Kf(100f, 0f))

                withPivotTransform(120f, 650f, tx = torsoTx, ty = torsoTy, rotDeg = torsoRot) {
                    // 3. Undershirt / Collar
                    drawPath(paths.collar, color = collarColor)

                    // 4 & 5. Back Hair and Shaded Neck moving with Head pivot (186, 405)
                    withPivotTransform(186f, 405f, tx = headTx, ty = headTy, rotDeg = headRot) {
                        val backHairRot = evalKf(t, Kf(0f, 0f), Kf(17f, 2.2f, cBangs1), Kf(37f, -3.8f, EaseSym), Kf(50f, -1.0f, cTorso1), Kf(65f, -3.0f, EaseStandard), Kf(85f, 0.6f, EaseStandard), Kf(100f, 0f))
                        withPivotTransform(180f, 360f, rotDeg = backHairRot) {
                            drawPath(paths.lowerBackHair, color = hairAndBadgeColor)
                        }
                        drawPath(paths.neckShadow, color = skinShadowColor)
                    }

                    // 6. Sweater / Shoulder
                    drawPath(paths.sweater, color = sweaterColor)

                    // HEAD GROUP (186, 405)
                    withPivotTransform(186f, 405f, tx = headTx, ty = headTy, rotDeg = headRot) {
                        // Lip-sync openness factor (0 = closed, 1 = wide open syllable, -0.35 = slight anticipation pursing)
                        val mouthOpen = evalKf(
                            t,
                            Kf(0f, 0f, EaseInOut),
                            Kf(14f, -0.35f, cLipSpline),
                            Kf(28f, 1.0f, EaseSym),
                            Kf(42f, 0.25f, cLipSpline),
                            Kf(58f, 1.12f, EaseSym),
                            Kf(75f, 0.12f, EaseStandard),
                            Kf(100f, 0f)
                        )

                        // Interpolate mouth & jaw vertices smoothly
                        val mx0 = lerpF(408f, 410f, mouthOpen)
                        val my0 = lerpF(324f, 321f, mouthOpen)
                        val mc1x = lerpF(390f, 385f, mouthOpen)
                        val mc1y = lerpF(320f, 313f, mouthOpen)
                        val mc2x = lerpF(372f, 358f, mouthOpen)
                        val mc2y = lerpF(324f, 320f, mouthOpen)
                        val midX = lerpF(371f, 356f, mouthOpen)
                        val midY = lerpF(335f, 337f, mouthOpen)
                        val mc3x = lerpF(370f, 354f, mouthOpen)
                        val mc3y = lerpF(344f, 354f, mouthOpen)
                        val mc4x = lerpF(382f, 371f, mouthOpen)
                        val mc4y = lerpF(350f, 366f, mouthOpen)
                        val mBotX = lerpF(397f, 390f, mouthOpen)
                        val mBotY = lerpF(352f, 369f, mouthOpen)
                        val mc5x = lerpF(401f, 397f, mouthOpen)
                        val mc5y = lerpF(343f, 353f, mouthOpen)
                        val mc6x = lerpF(405f, 404f, mouthOpen)
                        val mc6y = lerpF(333f, 337f, mouthOpen)

                        dynamicMouthPath.reset()
                        dynamicMouthPath.moveTo(mx0, my0)
                        dynamicMouthPath.cubicTo(mc1x, mc1y, mc2x, mc2y, midX, midY)
                        dynamicMouthPath.cubicTo(mc3x, mc3y, mc4x, mc4y, mBotX, mBotY)
                        dynamicMouthPath.cubicTo(mc5x, mc5y, mc6x, mc6y, mx0, my0)
                        dynamicMouthPath.close()
                        drawPath(dynamicMouthPath, color = mouthColor)

                        val jawDrop = mouthOpen * 12f
                        val jawBack = mouthOpen * 7f
                        dynamicFacePath.reset()
                        dynamicFacePath.moveTo(350f, -35f)
                        dynamicFacePath.lineTo(415f, -35f)
                        dynamicFacePath.lineTo(415f, 13f)
                        dynamicFacePath.lineTo(425f, 148f)
                        dynamicFacePath.cubicTo(444f, 179f, 464f, 205f, 483f, 223f)
                        dynamicFacePath.cubicTo(489f, 229f, 489f, 235f, 482f, 241f)
                        dynamicFacePath.cubicTo(468f, 252f, 452f, 261f, 438f, 270f)
                        dynamicFacePath.cubicTo(428f, 289f, 418f, 307f, mx0, my0)
                        dynamicFacePath.cubicTo(mc1x, mc1y, mc2x, mc2y, midX, midY)
                        dynamicFacePath.cubicTo(mc3x, mc3y, mc4x, mc4y, mBotX, mBotY)
                        dynamicFacePath.cubicTo(387f - jawBack, 380f + jawDrop, 377f - jawBack, 408f + jawDrop, 367f - jawBack, 434f + jawDrop)
                        dynamicFacePath.cubicTo(356f - jawBack, 462f + jawDrop, 333f - jawBack, 471f + jawDrop, 304f - jawBack, 463f + jawDrop)
                        dynamicFacePath.cubicTo(262f - jawBack * 0.6f, 451f + jawDrop * 0.7f, 222f, 429f + jawDrop * 0.35f, 186f, 405f)
                        dynamicFacePath.cubicTo(158f, 361f, 144f, 316f, 133f, 264f)
                        dynamicFacePath.cubicTo(102f, 266f, 62f, 256f, 48f, 212f)
                        dynamicFacePath.lineTo(140f, 110f)
                        dynamicFacePath.close()
                        drawPath(dynamicFacePath, color = skinColor)

                        // 9a. Upper Left Hair
                        drawPath(paths.upperHair1, color = hairAndBadgeColor)

                        // 9b. Upper Right Forehead Bangs (front-bangs-anim around 420, 13)
                        val bangsRot = evalKf(t, Kf(0f, 0f), Kf(18f, 2.8f, cBangs1), Kf(36f, -4.5f, EaseSym), Kf(48f, -1.2f, cTorso1), Kf(63f, -3.6f, EaseStandard), Kf(84f, 0.8f, EaseStandard), Kf(100f, 0f))
                        withPivotTransform(420f, 13f, rotDeg = bangsRot) {
                            drawPath(paths.upperHair2, color = hairAndBadgeColor)
                        }
                    }
                }

                // 10. SOUND WAVES
                // Travelling Acoustic Shockwave
                val travelAlpha = evalKf(t, Kf(0f, 0f), Kf(19f, 0f), Kf(27f, 0.85f), Kf(47f, 0f), Kf(50f, 0f), Kf(58f, 0.75f), Kf(79f, 0f), Kf(100f, 0f))
                if (travelAlpha > 0.01f) {
                    val travelTx = evalKf(t, Kf(0f, -75f), Kf(19f, -75f, cAura), Kf(47f, 65f), Kf(50f, -75f, cAura), Kf(79f, 60f), Kf(100f, 60f))
                    val travelTy = evalKf(t, Kf(0f, -32f), Kf(19f, -32f, cAura), Kf(47f, 28f), Kf(50f, -32f, cAura), Kf(79f, 26f), Kf(100f, 26f))
                    val travelScale = evalKf(t, Kf(0f, 0.65f), Kf(19f, 0.65f, cAura), Kf(47f, 1.32f), Kf(50f, 0.65f, cAura), Kf(79f, 1.28f), Kf(100f, 1.28f))
                    withPivotTransform(505f, 450f, tx = travelTx, ty = travelTy, sx = travelScale) {
                        drawPath(paths.waveTravel, color = waveLightActive.copy(alpha = travelAlpha * 0.7f), style = Stroke(width = 18f, cap = StrokeCap.Round))
                    }
                }

                // Wave 1 (Inner, pivot 458, 445)
                val w1Tx = evalKf(t, Kf(0f, 0f), Kf(14f, -12f, cWave), Kf(29f, 18f, EaseSym), Kf(43f, 4f, cWave), Kf(59f, 16f, EaseStandard), Kf(80f, 3f, EaseStandard), Kf(100f, 0f))
                val w1Ty = evalKf(t, Kf(0f, 0f), Kf(14f, -5f, cWave), Kf(29f, 8f, EaseSym), Kf(43f, 2f, cWave), Kf(59f, 7f, EaseStandard), Kf(80f, 1f, EaseStandard), Kf(100f, 0f))
                val w1Scale = evalKf(t, Kf(0f, 1f), Kf(14f, 0.88f, cWave), Kf(29f, 1.14f, EaseSym), Kf(43f, 0.95f, cWave), Kf(59f, 1.12f, EaseStandard), Kf(80f, 1.01f, EaseStandard), Kf(100f, 1f))
                val w1Alpha = evalKf(t, Kf(0f, 1f), Kf(14f, 0.72f, cWave), Kf(29f, 1f, EaseSym), Kf(43f, 0.85f, cWave), Kf(59f, 1f, EaseStandard), Kf(80f, 0.96f, EaseStandard), Kf(100f, 1f))
                val w1Glow = evalKf(t, Kf(0f, 0f), Kf(14f, 0f), Kf(29f, 1f), Kf(43f, 0f), Kf(59f, 0.85f), Kf(80f, 0f), Kf(100f, 0f))
                withPivotTransform(458f, 445f, tx = w1Tx, ty = w1Ty, sx = w1Scale) {
                    drawPath(paths.wave1a, color = lerp(waveLightBase, waveLightActive, w1Glow).copy(alpha = waveLightBase.alpha * w1Alpha), style = Stroke(width = 22f, cap = StrokeCap.Round))
                    drawPath(paths.wave1b, color = lerp(waveDarkBase, waveDarkActive, w1Glow).copy(alpha = waveDarkBase.alpha * w1Alpha), style = Stroke(width = 23f, cap = StrokeCap.Round))
                }

                // Wave 2 (Middle, pivot 502, 450)
                val w2Tx = evalKf(t, Kf(0f, 0f), Kf(16f, -14f, cWave), Kf(34f, 22f, EaseSym), Kf(48f, 6f, cWave), Kf(64f, 20f, EaseStandard), Kf(84f, 4f, EaseStandard), Kf(100f, 0f))
                val w2Ty = evalKf(t, Kf(0f, 0f), Kf(16f, -6f, cWave), Kf(34f, 10f, EaseSym), Kf(48f, 3f, cWave), Kf(64f, 9f, EaseStandard), Kf(84f, 1.5f, EaseStandard), Kf(100f, 0f))
                val w2Scale = evalKf(t, Kf(0f, 1f), Kf(16f, 0.9f, cWave), Kf(34f, 1.15f, EaseSym), Kf(48f, 0.96f, cWave), Kf(64f, 1.13f, EaseStandard), Kf(84f, 1.01f, EaseStandard), Kf(100f, 1f))
                val w2Alpha = evalKf(t, Kf(0f, 1f), Kf(16f, 0.75f, cWave), Kf(34f, 1f, EaseSym), Kf(48f, 0.86f, cWave), Kf(64f, 1f, EaseStandard), Kf(84f, 0.97f, EaseStandard), Kf(100f, 1f))
                val w2Glow = evalKf(t, Kf(0f, 0f), Kf(16f, 0f), Kf(34f, 1f), Kf(48f, 0f), Kf(64f, 0.85f), Kf(84f, 0f), Kf(100f, 0f))
                withPivotTransform(502f, 450f, tx = w2Tx, ty = w2Ty, sx = w2Scale) {
                    drawPath(paths.wave2a, color = lerp(waveLightBase, waveLightActive, w2Glow).copy(alpha = waveLightBase.alpha * w2Alpha), style = Stroke(width = 25f, cap = StrokeCap.Round))
                    drawPath(paths.wave2b, color = lerp(waveDarkBase, waveDarkActive, w2Glow).copy(alpha = waveDarkBase.alpha * w2Alpha), style = Stroke(width = 24f, cap = StrokeCap.Round))
                }

                // Wave 3 (Outer, pivot 545, 452)
                val w3Tx = evalKf(t, Kf(0f, 0f), Kf(18f, -16f, cWave), Kf(39f, 26f, EaseSym), Kf(53f, 8f, cWave), Kf(69f, 24f, EaseStandard), Kf(88f, 5f, EaseStandard), Kf(100f, 0f))
                val w3Ty = evalKf(t, Kf(0f, 0f), Kf(18f, -7f, cWave), Kf(39f, 12f, EaseSym), Kf(53f, 3.5f, cWave), Kf(69f, 11f, EaseStandard), Kf(88f, 2f, EaseStandard), Kf(100f, 0f))
                val w3Scale = evalKf(t, Kf(0f, 1f), Kf(18f, 0.91f, cWave), Kf(39f, 1.17f, EaseSym), Kf(53f, 0.97f, cWave), Kf(69f, 1.14f, EaseStandard), Kf(88f, 1.01f, EaseStandard), Kf(100f, 1f))
                val w3Alpha = evalKf(t, Kf(0f, 1f), Kf(18f, 0.78f, cWave), Kf(39f, 1f, EaseSym), Kf(53f, 0.88f, cWave), Kf(69f, 1f, EaseStandard), Kf(88f, 0.98f, EaseStandard), Kf(100f, 1f))
                val w3Glow = evalKf(t, Kf(0f, 0f), Kf(18f, 0f), Kf(39f, 1f), Kf(53f, 0f), Kf(69f, 0.85f), Kf(88f, 0f), Kf(100f, 0f))
                withPivotTransform(545f, 452f, tx = w3Tx, ty = w3Ty, sx = w3Scale) {
                    drawPath(paths.wave3a, color = lerp(waveDarkBase, waveDarkActive, w3Glow).copy(alpha = waveDarkBase.alpha * w3Alpha), style = Stroke(width = 26f, cap = StrokeCap.Round))
                    drawPath(paths.wave3b, color = lerp(waveLightBase, waveLightActive, w3Glow).copy(alpha = waveLightBase.alpha * w3Alpha), style = Stroke(width = 28f, cap = StrokeCap.Round))
                }

                // HAND & SMARTPHONE ASSEMBLY (pivot 620, 660)
                val hpTx = evalKf(t, Kf(0f, 0f), Kf(14f, -26f, cOvershoot1), Kf(38f, 32f, EaseSym), Kf(50f, 15f, cHead2), Kf(66f, 22f, cFastOut), Kf(85f, 4f, EaseStandard), Kf(100f, 0f))
                val hpTy = evalKf(t, Kf(0f, 0f), Kf(14f, -11f, cOvershoot1), Kf(38f, 13.5f, EaseSym), Kf(50f, 6.3f, cHead2), Kf(66f, 9.3f, cFastOut), Kf(85f, 1.7f, EaseStandard), Kf(100f, 0f))
                val hpRot = evalKf(t, Kf(0f, 0f), Kf(14f, 2.6f, cOvershoot1), Kf(38f, -3.8f, EaseSym), Kf(50f, -1.7f, cHead2), Kf(66f, -2.5f, cFastOut), Kf(85f, -0.4f, EaseStandard), Kf(100f, 0f))

                withPivotTransform(620f, 660f, tx = hpTx, ty = hpTy, rotDeg = hpRot) {
                    // 11. Hand (Back Layer)
                    drawPath(paths.backHand, color = skinColor)

                    // 12. Smartphone
                    withTransform({
                        translate(493f, 637f)
                        rotate(-32.36f, Offset.Zero)
                    }) {
                        clipPath(phoneClipPath) {
                            drawRect(color = phoneEndColor, topLeft = Offset(-12f, 0f), size = Size(527f, 59.2f))
                            drawRect(color = phoneBodyColor, topLeft = Offset(18f, 0f), size = Size(456f, 59.2f))

                            val barPulse = evalKf(t, Kf(0f, 0f), Kf(32f, 0f), Kf(40f, 1f), Kf(52f, 0f), Kf(62f, 0f), Kf(70f, 0.85f), Kf(85f, 0f), Kf(100f, 0f))
                            drawRoundRect(
                                color = lerp(phoneHighlightBase, phoneHighlightActive, barPulse),
                                topLeft = Offset(40f, 12f),
                                size = Size(454f, 17f),
                                cornerRadius = CornerRadius(8.5f, 8.5f)
                            )

                            // Voice recognition shimmer bar
                            val glowAlpha = evalKf(t, Kf(0f, 0f), Kf(30f, 0f, cAura), Kf(40f, 0.95f), Kf(52f, 0.2f), Kf(60f, 0f, cAura), Kf(70f, 0.85f), Kf(85f, 0f), Kf(100f, 0f))
                            if (glowAlpha > 0.01f) {
                                val glowTx = evalKf(t, Kf(0f, -60f), Kf(30f, -60f, cAura), Kf(40f, 0f), Kf(52f, 30f), Kf(60f, -40f, cAura), Kf(70f, 0f), Kf(85f, 40f), Kf(100f, 40f))
                                val glowSx = evalKf(t, Kf(0f, 0.6f), Kf(30f, 0.6f, cAura), Kf(40f, 1.08f), Kf(52f, 0.85f), Kf(60f, 0.7f, cAura), Kf(70f, 1.04f), Kf(85f, 0.8f), Kf(100f, 0.8f))
                                withPivotTransform(267f, 20.5f, tx = glowTx, sx = glowSx, sy = 1f) {
                                    drawRoundRect(
                                        color = phoneShimmerColor.copy(alpha = glowAlpha),
                                        topLeft = Offset(87f, 12f),
                                        size = Size(360f, 17f),
                                        cornerRadius = CornerRadius(8.5f, 8.5f)
                                    )
                                }
                            }
                        }
                    }

                    // 13. Hand (Front Thumb Layer with grip motion around 615, 625)
                    val thumbRot = evalKf(t, Kf(0f, 0f), Kf(15f, 1.6f, cBangs1), Kf(40f, -2.4f, EaseSym), Kf(54f, -0.8f, cTorso1), Kf(68f, -1.8f, EaseStandard), Kf(100f, 0f))
                    withPivotTransform(615f, 625f, rotDeg = thumbRot) {
                        drawPath(paths.frontThumb, color = skinColor)
                    }
                }

                // 14. DASHED DOUBLE-ARROW LINE
                val arrowLTx = evalKf(t, Kf(0f, 0f), Kf(14f, -7f, cOvershoot1), Kf(32f, 9f, EaseSym), Kf(44f, 4.5f, cHead2), Kf(58f, 8f, cFastOut), Kf(78f, 2f, EaseStandard), Kf(100f, 0f))
                val arrowLTy = evalKf(t, Kf(0f, 0f), Kf(14f, -3f, cOvershoot1), Kf(32f, 3.8f, EaseSym), Kf(44f, 1.9f, cHead2), Kf(58f, 3.4f, cFastOut), Kf(78f, 0.8f, EaseStandard), Kf(100f, 0f))
                withTransform({ translate(arrowLTx, arrowLTy) }) {
                    drawPath(paths.leftArrowHead, color = hairAndBadgeColor)
                    drawPath(paths.leftArrowHead, color = hairAndBadgeColor, style = Stroke(width = 2f, join = StrokeJoin.Round))
                }

                withTransform({ translate(hpTx, hpTy) }) {
                    drawPath(paths.rightArrowHead, color = hairAndBadgeColor)
                    drawPath(paths.rightArrowHead, color = hairAndBadgeColor, style = Stroke(width = 2f, join = StrokeJoin.Round))
                }

                // Stretchy 8 rounded dash segments along 22.894° axis at (507, 279)
                val dashTx = evalKf(t, Kf(0f, 0f), Kf(14f, -7.6f, cOvershoot1), Kf(38f, 9.8f, EaseSym), Kf(50f, 4.9f, cHead2), Kf(66f, 8.7f, cFastOut), Kf(85f, 1.8f, EaseStandard), Kf(100f, 0f))
                val dashSx = evalKf(t, Kf(0f, 1f), Kf(14f, 0.935f, cOvershoot1), Kf(38f, 1.078f, EaseSym), Kf(50f, 1.035f, cHead2), Kf(66f, 1.05f, cFastOut), Kf(85f, 1.008f, EaseStandard), Kf(100f, 1f))
                val dashSegments = listOf(
                    Offset(525f, 286f) to Offset(549f, 296f),
                    Offset(567f, 304f) to Offset(585f, 311f),
                    Offset(601f, 318f) to Offset(619f, 325f),
                    Offset(634f, 332f) to Offset(652f, 339f),
                    Offset(668f, 346f) to Offset(686f, 353f),
                    Offset(701f, 360f) to Offset(720f, 367f),
                    Offset(735f, 374f) to Offset(753f, 381f),
                    Offset(769f, 388f) to Offset(785f, 395f)
                )
                withTransform({
                    translate(507f, 279f)
                    rotate(22.894f, Offset.Zero)
                    translate(dashTx, 0f)
                    scale(dashSx, 1f, Offset.Zero)
                    rotate(-22.894f, Offset.Zero)
                    translate(-507f, -279f)
                }) {
                    dashSegments.forEachIndexed { idx, (start, end) ->
                        val startPct = 18f + idx * 2f
                        val peakPct = startPct + 7f
                        val endPct = startPct + 18f
                        val highlight = evalKf(t, Kf(0f, 0f), Kf(startPct, 0f), Kf(peakPct, 1f), Kf(endPct, 0f), Kf(100f, 0f))
                        drawLine(
                            color = lerp(hairAndBadgeColor, phoneHighlightActive, highlight),
                            start = start,
                            end = end,
                            strokeWidth = 8.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 15. "15-20cm" PILL BADGE & STAGGERED VECTOR TYPOGRAPHY (pivot 797.5, 223)
                val ringAlpha = evalKf(t, Kf(0f, 0f), Kf(20f, 0f, cAura), Kf(30f, 0.55f), Kf(54f, 0f), Kf(100f, 0f))
                if (ringAlpha > 0.01f) {
                    val ringSx = evalKf(t, Kf(0f, 0.95f), Kf(20f, 0.95f, cAura), Kf(54f, 1.22f), Kf(100f, 1.22f))
                    val ringSy = evalKf(t, Kf(0f, 0.95f), Kf(20f, 0.95f, cAura), Kf(54f, 1.48f), Kf(100f, 1.48f))
                    withPivotTransform(797.5f, 223f, sx = ringSx, sy = ringSy) {
                        drawRoundRect(
                            color = hairAndBadgeColor.copy(alpha = ringAlpha),
                            topLeft = Offset(635f, 179f),
                            size = Size(325f, 88f),
                            cornerRadius = CornerRadius(44f, 44f),
                            style = Stroke(width = 5f)
                        )
                    }
                }

                val bTx = evalKf(t, Kf(0f, 0f), Kf(14f, -6f, cBangs1), Kf(36f, 8f, EaseSym), Kf(49f, 2f, cHead2), Kf(63f, 5f, cFastOut), Kf(82f, 1f, EaseStandard), Kf(100f, 0f))
                val bTy = evalKf(t, Kf(0f, 0f), Kf(14f, 6f, cBangs1), Kf(36f, -14f, EaseSym), Kf(49f, -3f, cHead2), Kf(63f, -8f, cFastOut), Kf(82f, -1f, EaseStandard), Kf(100f, 0f))
                val bScale = evalKf(t, Kf(0f, 1f), Kf(14f, 0.94f, cBangs1), Kf(36f, 1.11f, EaseSym), Kf(49f, 0.97f, cHead2), Kf(63f, 1.045f, cFastOut), Kf(82f, 1f, EaseStandard), Kf(100f, 1f))
                val bRot = evalKf(t, Kf(0f, 0f), Kf(14f, -1.8f, cBangs1), Kf(36f, 2.2f, EaseSym), Kf(49f, -0.8f, cHead2), Kf(63f, 1.0f, cFastOut), Kf(82f, 0f, EaseStandard), Kf(100f, 0f))
                val bTint = evalKf(t, Kf(0f, 0f), Kf(14f, 0f), Kf(36f, 1f), Kf(52f, 0f), Kf(100f, 0f))

                withPivotTransform(797.5f, 223f, tx = bTx, ty = bTy, sx = bScale, rotDeg = bRot) {
                    drawRoundRect(
                        color = lerp(hairAndBadgeColor, badgePopColor, bTint),
                        topLeft = Offset(635f, 179f),
                        size = Size(325f, 88f),
                        cornerRadius = CornerRadius(44f, 44f)
                    )

                    // Glossy Light Sweep clipped to pill
                    val sheenAlpha = evalKf(t, Kf(0f, 0f), Kf(22f, 0f, cTorso1), Kf(30f, 0.22f), Kf(52f, 0f), Kf(100f, 0f))
                    if (sheenAlpha > 0.01f) {
                        val sheenTx = evalKf(t, Kf(0f, -260f), Kf(22f, -260f, cTorso1), Kf(52f, 320f), Kf(100f, 320f))
                        clipPath(badgeClipPath) {
                            withPivotTransform(797.5f, 223f, tx = sheenTx, skewXDeg = -24f) {
                                drawRect(
                                    color = Color.White.copy(alpha = sheenAlpha),
                                    topLeft = Offset(797.5f - 35f, 223f - 70f),
                                    size = Size(70f, 140f)
                                )
                            }
                        }
                    }

                    // Staggered "15-20cm" Vector Typography
                    val cHop = BezierCurve(0.2f, 0.9f, 0.3f, 1.3f)
                    withTransform({
                        translate(74f, 0f)
                        scale(0.892f, 1f, Offset.Zero)
                    }) {
                        fun drawHoppingGlyph(idx: Int, drawBlock: DrawScope.() -> Unit) {
                            val sPct = 18f + idx * 3f
                            val pPct = 30f + idx * 3f
                            val ePct = 52f + idx * 3f
                            val dy = evalKf(t, Kf(0f, 0f), Kf(sPct, 0f, cHop), Kf(pPct, -7f, EaseStandard), Kf(ePct, 0f), Kf(100f, 0f))
                            withTransform({ translate(0f, dy) }, drawBlock)
                        }

                        drawHoppingGlyph(0) { drawPath(paths.glyph1, color = badgeTextColor) }
                        drawHoppingGlyph(1) { drawPath(paths.glyph5, color = badgeTextColor) }
                        drawHoppingGlyph(2) {
                            drawRoundRect(
                                color = badgeTextColor,
                                topLeft = Offset(750f, 220f),
                                size = Size(22f, 8.5f),
                                cornerRadius = CornerRadius(1.5f, 1.5f)
                            )
                        }
                        drawHoppingGlyph(3) { drawPath(paths.glyph2, color = badgeTextColor) }
                        drawHoppingGlyph(4) { drawPath(paths.glyph0, color = badgeTextColor) }
                        drawHoppingGlyph(5) { drawPath(paths.glyphC, color = badgeTextColor) }
                        drawHoppingGlyph(6) { drawPath(paths.glyphM, color = badgeTextColor) }
                    }
                }
            }
        }
    }
}

// ============================================================================
// PAGE 2 (INDEX 1): 留意底部音量条 (5.6s Seamless Two-Act Animation)
// ============================================================================

private object CachedTipVolumeBarPaths {
    val bottomRightCurve by lazy(LazyThreadSafetyMode.NONE) { svgPath("M296 225C314 198 354 180 400 180V201C400 214.255 389.255 225 376 225H296Z") }
    val checkMark by lazy(LazyThreadSafetyMode.NONE) { svgPath("M103 60L111 68L126 53") }
    val exclamStem by lazy(LazyThreadSafetyMode.NONE) { svgPath("M292 41V55") }
    val accent1 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M334 38L344 20") }
    val accent2 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M342 48L360 40") }

    val greenBarsClip by lazy(LazyThreadSafetyMode.NONE) {
        Path().apply {
            val xs = floatArrayOf(38f, 52f, 66f, 80f, 94f, 108f, 122f, 136f, 150f, 164f, 178f)
            xs.forEach { x ->
                addRoundRect(RoundRect(Rect(offset = Offset(x, 72f), size = Size(9f, 88f)), CornerRadius(4.5f, 4.5f)))
            }
        }
    }
    val redBarsClip by lazy(LazyThreadSafetyMode.NONE) {
        Path().apply {
            val xs = floatArrayOf(214f, 229f, 244f, 259f, 274f, 289f, 304f, 319f, 334f, 349f)
            xs.forEach { x ->
                addRoundRect(RoundRect(Rect(offset = Offset(x, 88f), size = Size(10f, 72f)), CornerRadius(4.5f, 4.5f)))
            }
        }
    }
}

@Composable
private fun TipIllustrationVolumeBar(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val paths = CachedTipVolumeBarPaths

    val curveColor = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.38f else 0.68f)
    val cardColor = colorScheme.surface
    val cardShadowColor = colorScheme.onSurface.copy(alpha = if (isDark) 0.28f else 0.1f)
    val dividerColor = colorScheme.outlineVariant

    // Semantic Green & Red harmonized with Material 3 theme
    val greenMain = if (isDark) Color(0xFF55C98E) else Color(0xFF4F946E)
    val greenLight = if (isDark) Color(0xFF6AD59F) else Color(0xFF67A782)
    val greenBadge = if (isDark) Color(0xFF4CC38A) else Color(0xFF5B9E77)

    val redMain = if (isDark) Color(0xFFFF685D) else Color(0xFFDD5B51)
    val redSoft = if (isDark) Color(0xFFFF7A70) else Color(0xFFE0655A)
    val redHot = if (isDark) Color(0xFFFF453A) else Color(0xFFD93829)

    Canvas(modifier = modifier) {
        val contentW = 392f
        val contentH = 216f
        val scale = min(size.width / contentW, size.height / contentH)
        val offsetX = size.width / 2f - 200f * scale
        val offsetY = size.height / 2f - 112.5f * scale

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            // 1. Decorative bottom-right curve (arcBreathe around 348, 202)
            val arcScale = evalKf(t, Kf(0f, 1f, EaseSmooth), Kf(50f, 1.04f, EaseSmooth), Kf(100f, 1f))
            val arcTx = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(50f, -2f, EaseSmooth), Kf(100f, 0f))
            val arcTy = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(50f, -1f, EaseSmooth), Kf(100f, 0f))
            withPivotTransform(348f, 202f, tx = arcTx, ty = arcTy, sx = arcScale) {
                drawPath(paths.bottomRightCurve, color = curveColor)
            }

            // 2. Card Shadow
            drawRoundRect(
                color = cardShadowColor,
                topLeft = Offset(20f, 70f),
                size = Size(360f, 114f),
                cornerRadius = CornerRadius(22f, 22f)
            )

            // Main Card Group (cardShock vibration in Act 2: 54%..82%)
            val shockTx = evalKf(
                t,
                Kf(0f, 0f), Kf(54f, 0f),
                Kf(57f, 1.8f), Kf(60f, -1.6f), Kf(63f, 1.4f), Kf(66f, -1.2f),
                Kf(69f, 1f), Kf(72f, -0.8f), Kf(76f, 0.4f), Kf(82f, 0f), Kf(100f, 0f)
            )
            val shockTy = evalKf(
                t,
                Kf(0f, 0f), Kf(54f, 0f),
                Kf(57f, -1.2f), Kf(60f, 1f), Kf(63f, -0.8f), Kf(66f, 0.8f),
                Kf(69f, -0.5f), Kf(72f, 0.4f), Kf(76f, -0.2f), Kf(82f, 0f), Kf(100f, 0f)
            )
            val shockRot = evalKf(
                t,
                Kf(0f, 0f), Kf(54f, 0f),
                Kf(57f, 0.4f), Kf(60f, -0.35f), Kf(63f, 0.3f), Kf(66f, -0.25f),
                Kf(69f, 0.2f), Kf(72f, -0.15f), Kf(76f, 0.08f), Kf(82f, 0f), Kf(100f, 0f)
            )

            withPivotTransform(200f, 121f, tx = shockTx, ty = shockTy, rotDeg = shockRot) {
                drawRoundRect(
                    color = cardColor,
                    topLeft = Offset(20f, 64f),
                    size = Size(360f, 114f),
                    cornerRadius = CornerRadius(22f, 22f)
                )

                // Center Vertical Divider Line
                drawLine(
                    color = dividerColor,
                    start = Offset(200f, 80f),
                    end = Offset(200f, 162f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )

                // 3. Left Zone: 11 Green Waveform Bars clipped to capsule slots
                clipPath(paths.greenBarsClip) {
                    val g1 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(10f, -16f, EaseSmooth), Kf(22f, 8f, EaseSmooth), Kf(34f, -10f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(72f, -3f, EaseSmooth), Kf(100f, 0f))
                    val g2 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(12f, -22f, EaseSmooth), Kf(24f, 6f, EaseSmooth), Kf(36f, -14f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(74f, 3f, EaseSmooth), Kf(100f, 0f))
                    val g3 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(14f, -18f, EaseSmooth), Kf(26f, 12f, EaseSmooth), Kf(38f, -12f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(76f, -4f, EaseSmooth), Kf(100f, 0f))
                    val g4 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(10f, 18f, EaseSmooth), Kf(20f, -8f, EaseSmooth), Kf(32f, 14f, EaseSmooth), Kf(41f, -4f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(70f, 4f, EaseSmooth), Kf(100f, 0f))
                    val g5 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(12f, -18f, EaseSmooth), Kf(23f, 14f, EaseSmooth), Kf(35f, -14f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(73f, -3f, EaseSmooth), Kf(100f, 0f))
                    val g6 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(14f, -28f, EaseSmooth), Kf(26f, 6f, EaseSmooth), Kf(37f, -20f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(75f, -4f, EaseSmooth), Kf(100f, 0f))
                    val g7 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(11f, 16f, EaseSmooth), Kf(22f, -14f, EaseSmooth), Kf(34f, 12f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(71f, 3f, EaseSmooth), Kf(100f, 0f))
                    val g8 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(13f, 24f, EaseSmooth), Kf(25f, -4f, EaseSmooth), Kf(36f, 18f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(74f, 5f, EaseSmooth), Kf(100f, 0f))
                    val g9 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(15f, -12f, EaseSmooth), Kf(27f, 16f, EaseSmooth), Kf(38f, -8f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(76f, -3f, EaseSmooth), Kf(100f, 0f))
                    val g10 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(12f, -20f, EaseSmooth), Kf(24f, 8f, EaseSmooth), Kf(36f, -14f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(72f, -3f, EaseSmooth), Kf(100f, 0f))
                    val g11 = evalKf(t, Kf(0f, 0f, EaseSmooth), Kf(15f, -16f, EaseSmooth), Kf(27f, 6f, EaseSmooth), Kf(39f, -10f, EaseSmooth), Kf(48f, 0f, EaseSmooth), Kf(75f, 3f, EaseSmooth), Kf(100f, 0f))

                    val greenSpecs = listOf(
                        Triple(38f, 128f + g1, greenLight),
                        Triple(52f, 134f + g2, greenLight),
                        Triple(66f, 122f + g3, greenMain),
                        Triple(80f, 102f + g4, greenMain),
                        Triple(94f, 118f + g5, greenLight),
                        Triple(108f, 130f + g6, greenMain),
                        Triple(122f, 112f + g7, greenLight),
                        Triple(136f, 96f + g8, greenLight),
                        Triple(150f, 110f + g9, greenMain),
                        Triple(164f, 128f + g10, greenMain),
                        Triple(178f, 132f + g11, greenLight)
                    )
                    greenSpecs.forEach { (x, y, c) ->
                        drawRoundRect(color = c, topLeft = Offset(x, y), size = Size(9f, 90f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    }
                }

                // 4. Right Zone: Distortion Ghost Block (ghostBuzz around 311, 124)
                val ghostTx = evalKf(
                    t,
                    Kf(0f, 0f), Kf(46f, 0f), Kf(51f, 0f, EaseOutExpo),
                    Kf(57f, -3.5f), Kf(60f, 3.5f), Kf(63f, -3f), Kf(66f, 3f),
                    Kf(69f, -2.5f), Kf(72f, 2.5f), Kf(75f, -2f), Kf(79f, 1.5f), Kf(92f, 0f), Kf(100f, 0f)
                )
                val ghostTy = evalKf(t, Kf(0f, 0f), Kf(46f, 0f), Kf(51f, 12f, EaseOutExpo), Kf(57f, 0f), Kf(92f, 0f), Kf(100f, 0f))
                val ghostSx = evalKf(
                    t,
                    Kf(0f, 1f), Kf(46f, 1f), Kf(51f, 0.85f, EaseOutExpo),
                    Kf(57f, 1.36f), Kf(60f, 1.42f), Kf(63f, 1.38f), Kf(66f, 1.44f),
                    Kf(69f, 1.35f), Kf(72f, 1.38f), Kf(75f, 1.28f), Kf(79f, 1.15f), Kf(92f, 1f), Kf(100f, 1f)
                )
                val ghostSy = evalKf(t, Kf(0f, 1f), Kf(46f, 1f), Kf(51f, 0.65f, EaseOutExpo), Kf(57f, 1f), Kf(92f, 1f), Kf(100f, 1f))
                val ghostAlpha = evalKf(
                    t,
                    Kf(0f, 0.35f), Kf(46f, 0.35f), Kf(51f, 0.12f, EaseOutExpo),
                    Kf(57f, 0.68f), Kf(66f, 0.75f), Kf(79f, 0.48f), Kf(92f, 0.35f), Kf(100f, 0.35f)
                )
                withPivotTransform(311f, 124f, tx = ghostTx, ty = ghostTy, sx = ghostSx, sy = ghostSy) {
                    drawRoundRect(
                        color = redSoft.copy(alpha = ghostAlpha),
                        topLeft = Offset(278f, 88f),
                        size = Size(66f, 72f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }

                // 10 Red Waveform Bars clipped to y=88..160
                clipPath(paths.redBarsClip) {
                    val rLead1Ty = evalKf(t, Kf(0f, 0f), Kf(46f, 0f), Kf(51f, 20f, EaseOutExpo), Kf(57f, -16f), Kf(61f, -11f), Kf(69f, -11f), Kf(73f, -16f), Kf(82f, 4f), Kf(90f, 0f), Kf(100f, 0f))
                    val rLead2Ty = evalKf(t, Kf(0f, 0f), Kf(46f, 0f), Kf(51f, 26f, EaseOutExpo), Kf(57f, -10f), Kf(75f, -10f), Kf(82f, 3f), Kf(90f, 0f), Kf(100f, 0f))

                    val clipATx = evalKf(t, Kf(0f, 0f), Kf(51f, 0f), Kf(57f, -1f), Kf(60f, 1f), Kf(63f, -1f), Kf(66f, 1f), Kf(69f, -0.8f), Kf(73f, 0.8f), Kf(77f, 0f), Kf(100f, 0f))
                    val clipATy = evalKf(t, Kf(0f, 0f), Kf(20f, 1.5f), Kf(46f, 0f), Kf(51f, 32f, EaseOutExpo), Kf(57f, 0f), Kf(60f, 2f), Kf(63f, 0f), Kf(66f, 1.5f), Kf(69f, 0f), Kf(73f, 2f), Kf(77f, 0f), Kf(100f, 0f))

                    val clipBTx = evalKf(t, Kf(0f, 0f), Kf(51.5f, 0f), Kf(57.5f, 1f), Kf(61f, -1f), Kf(64f, 1f), Kf(67f, -1f), Kf(71f, 0.8f), Kf(75f, -0.5f), Kf(79f, 0f), Kf(100f, 0f))
                    val clipBTy = evalKf(t, Kf(0f, 0f), Kf(25f, 2f), Kf(46f, 0f), Kf(51.5f, 24f, EaseOutExpo), Kf(57.5f, 0f), Kf(61f, 1f), Kf(64f, 0f), Kf(67f, 2f), Kf(71f, 0f), Kf(75f, 1f), Kf(79f, 0f), Kf(100f, 0f))

                    val clipCTx = evalKf(t, Kf(0f, 0f), Kf(52f, 0f), Kf(58f, -1f), Kf(61.5f, 1f), Kf(65f, -1f), Kf(68.5f, 1f), Kf(72f, -0.6f), Kf(78f, 0f), Kf(100f, 0f))
                    val clipCTy = evalKf(t, Kf(0f, 0f), Kf(30f, 1.5f), Kf(46f, 0f), Kf(52f, 36f, EaseOutExpo), Kf(58f, 0f), Kf(61.5f, 2.5f), Kf(65f, 0f), Kf(68.5f, 1.5f), Kf(72f, 0f), Kf(78f, 0f), Kf(100f, 0f))

                    val hotMix = evalKf(t, Kf(0f, 0f), Kf(52f, 0f), Kf(57f, 1f), Kf(73f, 1f), Kf(82f, 0f), Kf(100f, 0f))
                    val barRed = lerp(redMain, redHot, hotMix)
                    val barSoftRed = lerp(redSoft, redHot, hotMix)

                    drawRoundRect(color = barSoftRed, topLeft = Offset(214f, 112f + rLead1Ty), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barRed, topLeft = Offset(229f, 96f + rLead2Ty), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barRed, topLeft = Offset(244f + clipATx, 88f + clipATy), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barRed, topLeft = Offset(259f + clipBTx, 88f + clipBTy), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barRed, topLeft = Offset(274f + clipATx, 88f + clipATy), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barRed, topLeft = Offset(289f + clipBTx, 88f + clipBTy), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barRed, topLeft = Offset(304f + clipATx, 88f + clipATy), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barRed, topLeft = Offset(319f + clipBTx, 88f + clipBTy), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barSoftRed, topLeft = Offset(334f + clipCTx, 88f + clipCTy), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                    drawRoundRect(color = barSoftRed, topLeft = Offset(349f + clipCTx, 88f + clipCTy), size = Size(10f, 110f), cornerRadius = CornerRadius(4.5f, 4.5f))
                }

                // Clipping Ceiling Dashed Line at y=88
                val ceilingAlpha = evalKf(t, Kf(0f, 0f), Kf(54f, 0f), Kf(57f, 0.95f), Kf(61f, 0.4f), Kf(65f, 0.95f), Kf(69f, 0.4f), Kf(73f, 0.95f), Kf(82f, 0f), Kf(100f, 0f))
                if (ceilingAlpha > 0.01f) {
                    val ceilingSx = evalKf(t, Kf(0f, 0.6f), Kf(54f, 0.6f), Kf(57f, 1f), Kf(73f, 1f), Kf(82f, 0.6f), Kf(100f, 0.6f))
                    withPivotTransform(293f, 88f, sx = ceilingSx, sy = 1f) {
                        drawLine(
                            color = redHot.copy(alpha = ceilingAlpha),
                            start = Offset(226f, 88f),
                            end = Offset(361f, 88f),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                        )
                    }
                }
            }

            // 5. Top-Left Green OK Badge (✓) & Ripple
            val okRippleAlpha = evalKf(t, Kf(0f, 0f), Kf(6f, 0f), Kf(9f, 0.65f, EaseOutExpo), Kf(28f, 0f), Kf(100f, 0f))
            if (okRippleAlpha > 0.01f) {
                val okRippleScale = evalKf(t, Kf(0f, 0.9f), Kf(6f, 0.9f), Kf(9f, 1f, EaseOutExpo), Kf(28f, 1.65f), Kf(100f, 1.65f))
                withPivotTransform(114f, 60f, sx = okRippleScale) {
                    drawCircle(color = greenBadge.copy(alpha = okRippleAlpha), radius = 24f, center = Offset(114f, 60f), style = Stroke(width = 2.5f))
                }
            }

            val okTy = evalKf(t, Kf(0f, 0f), Kf(3f, 0f), Kf(6f, 2f, EaseOutBack), Kf(13f, -5f, EaseOutQuart), Kf(19f, 1f), Kf(25f, -1.5f), Kf(32f, 0f), Kf(100f, 0f))
            val okScale = evalKf(t, Kf(0f, 1f), Kf(3f, 1f), Kf(6f, 0.88f, EaseOutBack), Kf(13f, 1.18f, EaseOutQuart), Kf(19f, 0.95f), Kf(25f, 1.04f), Kf(32f, 1f), Kf(100f, 1f))
            val okRot = evalKf(t, Kf(0f, 0f), Kf(3f, 0f), Kf(6f, -4f, EaseOutBack), Kf(13f, 5f, EaseOutQuart), Kf(19f, -2f), Kf(25f, 1f), Kf(32f, 0f), Kf(100f, 0f))
            val checkScale = evalKf(t, Kf(0f, 1f), Kf(5f, 1f), Kf(7f, 0.85f, EaseOutQuart), Kf(15f, 1.15f), Kf(22f, 0.96f), Kf(36f, 1f), Kf(100f, 1f))
            withPivotTransform(114f, 60f, ty = okTy, sx = okScale, rotDeg = okRot) {
                drawCircle(color = greenBadge, radius = 24f, center = Offset(114f, 60f))
                withPivotTransform(114f, 60f, sx = checkScale) {
                    drawPath(paths.checkMark, color = Color.White, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            // 6. Top-Right Red Warning Badge (!) + Dual Ripples + Accent Rays
            fun drawWarnRipple(tShifted: Float, strokeW: Float) {
                val wAlpha = evalKf(tShifted, Kf(0f, 0f), Kf(54f, 0f), Kf(57f, 0.75f, EaseOutExpo), Kf(78f, 0f), Kf(100f, 0f))
                if (wAlpha > 0.01f) {
                    val wScale = evalKf(tShifted, Kf(0f, 0.9f), Kf(54f, 0.9f), Kf(57f, 1f, EaseOutExpo), Kf(78f, 1.85f), Kf(100f, 1.85f))
                    withPivotTransform(292f, 54f, sx = wScale) {
                        drawCircle(color = redMain.copy(alpha = wAlpha), radius = 24f, center = Offset(292f, 54f), style = Stroke(width = strokeW))
                    }
                }
            }
            drawWarnRipple(t, 2.5f)
            drawWarnRipple((t + 1f - 0.35f / 5.6f) % 1f, 2f)

            val wTy = evalKf(
                t,
                Kf(0f, 0f), Kf(50f, 0f), Kf(53f, 3f, EaseOutBack),
                Kf(57f, -6f), Kf(60f, -6f), Kf(63f, -5f), Kf(66f, -5f),
                Kf(69f, -4f), Kf(72f, -3f), Kf(76f, -1f), Kf(81f, 1f), Kf(88f, 0f), Kf(100f, 0f)
            )
            val wScale = evalKf(
                t,
                Kf(0f, 1f), Kf(50f, 1f), Kf(53f, 0.86f, EaseOutBack),
                Kf(57f, 1.24f), Kf(60f, 1.26f), Kf(63f, 1.22f), Kf(66f, 1.22f),
                Kf(69f, 1.16f), Kf(72f, 1.12f), Kf(76f, 1.05f), Kf(81f, 0.97f), Kf(88f, 1f), Kf(100f, 1f)
            )
            val wRot = evalKf(
                t,
                Kf(0f, 0f), Kf(50f, 0f), Kf(53f, 0f, EaseOutBack),
                Kf(57f, -14f), Kf(60f, 14f), Kf(63f, -12f), Kf(66f, 12f),
                Kf(69f, -9f), Kf(72f, 8f), Kf(76f, -4f), Kf(81f, 2f), Kf(88f, 0f), Kf(100f, 0f)
            )
            val exclSx = evalKf(t, Kf(0f, 1f), Kf(53f, 1f), Kf(58f, 1.12f), Kf(62f, 0.94f), Kf(66f, 1.12f), Kf(70f, 0.94f), Kf(84f, 1f), Kf(100f, 1f))
            val exclSy = evalKf(t, Kf(0f, 1f), Kf(53f, 1f), Kf(58f, 1.18f), Kf(62f, 0.92f), Kf(66f, 1.18f), Kf(70f, 0.92f), Kf(84f, 1f), Kf(100f, 1f))

            withPivotTransform(292f, 54f, ty = wTy, sx = wScale, rotDeg = wRot) {
                drawCircle(color = redMain, radius = 24f, center = Offset(292f, 54f))
                withPivotTransform(292f, 54f, sx = exclSx, sy = exclSy) {
                    drawPath(paths.exclamStem, color = Color.White, style = Stroke(width = 5.5f, cap = StrokeCap.Round))
                    drawCircle(color = Color.White, radius = 3f, center = Offset(292f, 65f))
                }
            }

            // Ray 1 (pivot 334, 38)
            val r1Tx = evalKf(t, Kf(0f, 0f), Kf(50f, 0f), Kf(54f, -2f, EaseOutBack), Kf(58f, 4.5f), Kf(63f, 1f), Kf(68f, 4f), Kf(75f, -0.5f), Kf(88f, 0f), Kf(100f, 0f))
            val r1Ty = evalKf(t, Kf(0f, 0f), Kf(50f, 0f), Kf(54f, 3.5f, EaseOutBack), Kf(58f, -8f), Kf(63f, -2f), Kf(68f, -7f), Kf(75f, 1f), Kf(88f, 0f), Kf(100f, 0f))
            val r1Scale = evalKf(t, Kf(0f, 1f), Kf(50f, 1f), Kf(54f, 0.75f, EaseOutBack), Kf(58f, 1.22f), Kf(63f, 0.95f), Kf(68f, 1.18f), Kf(75f, 0.96f), Kf(88f, 1f), Kf(100f, 1f))
            withPivotTransform(334f, 38f, tx = r1Tx, ty = r1Ty, sx = r1Scale) {
                drawPath(paths.accent1, color = redSoft, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }

            // Ray 2 (pivot 342, 48)
            val r2Tx = evalKf(t, Kf(0f, 0f), Kf(51f, 0f), Kf(55f, -3.5f, EaseOutBack), Kf(59f, 8f), Kf(64f, 2f), Kf(69f, 7f), Kf(76f, -1f), Kf(88f, 0f), Kf(100f, 0f))
            val r2Ty = evalKf(t, Kf(0f, 0f), Kf(51f, 0f), Kf(55f, 1.5f, EaseOutBack), Kf(59f, -3.5f), Kf(64f, -1f), Kf(69f, -3f), Kf(76f, 0.5f), Kf(88f, 0f), Kf(100f, 0f))
            val r2Scale = evalKf(t, Kf(0f, 1f), Kf(51f, 1f), Kf(55f, 0.75f, EaseOutBack), Kf(59f, 1.22f), Kf(64f, 0.95f), Kf(69f, 1.18f), Kf(76f, 0.96f), Kf(88f, 1f), Kf(100f, 1f))
            withPivotTransform(342f, 48f, tx = r2Tx, ty = r2Ty, sx = r2Scale) {
                drawPath(paths.accent2, color = redSoft, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }
    }
}

// ============================================================================
// PAGE 3 (INDEX 2): 大字模式与音量键 (6.0s Seamless 3-Act Animation)
// ============================================================================

private object CachedTipLargeTextPaths {
    val phoneOuter by lazy(LazyThreadSafetyMode.NONE) { svgPath("M54 180V42C54 29.8497 63.8497 20 76 20H182C194.15 20 204 29.8497 204 42V180H54Z") }
    val phoneScreen by lazy(LazyThreadSafetyMode.NONE) { svgPath("M62 180V43C62 34.7157 68.7157 28 77 28H181C189.284 28 196 34.7157 196 43V180H62Z") }
    val eyeOutline by lazy(LazyThreadSafetyMode.NONE) { svgPath("M163 52C164.8 48.8 167.3 47.5 170 47.5C172.7 47.5 175.2 48.8 177 52C175.2 55.2 172.7 56.5 170 56.5C167.3 56.5 164.8 55.2 163 52Z") }
    val eyeToCardArc by lazy(LazyThreadSafetyMode.NONE) { svgPath("M153 54C140 56 132 62 130 72") }
    val charWen by lazy(LazyThreadSafetyMode.NONE) { svgPath("M129 97V105M107 106H151M140 106C136.5 123 125 136 107 143M118 112C124 125 135 136 151 143") }
    val badgeSmallA by lazy(LazyThreadSafetyMode.NONE) { svgPath("M51 84C51 85.5 49.8 86.5 48.2 86.5C46.6 86.5 45.5 85.5 45.5 84C45.5 82.5 46.8 81.5 48.5 81.5H51V84ZM51 86.5V80.5C51 79 49.8 78 48 78C46.8 78 46 78.5 45.8 79") }
    val badgeBigA by lazy(LazyThreadSafetyMode.NONE) { svgPath("M57 86.5L61.5 75.5L66 86.5M58.5 83.5H64.5") }
    val volUpBtn by lazy(LazyThreadSafetyMode.NONE) { svgPath("M198 70H207C209.209 70 211 71.7909 211 74V96C211 98.2091 209.209 100 207 100H198V70Z") }
    val volDownBtn by lazy(LazyThreadSafetyMode.NONE) { svgPath("M198 116H207C209.209 116 211 117.791 211 120V142C211 144.209 209.209 146 207 146H198V116Z") }
    val micSoundWaves by lazy(LazyThreadSafetyMode.NONE) { svgPath("M246 77C244.5 79.2 244.5 82.8 246 85M272 77C273.5 79.2 273.5 82.8 272 85") }
    val micStand by lazy(LazyThreadSafetyMode.NONE) { svgPath("M251 81C251 85.4 254.6 89 259 89C263.4 89 267 85.4 267 81M259 89V93") }
    val nextArrow by lazy(LazyThreadSafetyMode.NONE) { svgPath("M249 131H262M257 126L262 131L257 136") }
    val nextBar by lazy(LazyThreadSafetyMode.NONE) { svgPath("M267 125V137") }
}

@Composable
private fun TipIllustrationLargeTextAndVolumeKey(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val paths = CachedTipLargeTextPaths

    val capsuleBg = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.36f else 0.65f)
    val phoneBodyColor = if (isDark) {
        lerp(colorScheme.primary, colorScheme.surfaceContainerHighest, 0.35f)
    } else {
        lerp(colorScheme.primary, Color(0xFF2B3E5E), 0.35f)
    }
    val screenColor = colorScheme.surface
    val subtleBarColor = colorScheme.surfaceContainerHighest
    val primaryAccent = colorScheme.primary
    val primaryBright = lerp(colorScheme.primary, Color(0xFF2563EB), 0.45f)
    val onPrimaryAccent = colorScheme.onPrimary

    val greenAccent = if (isDark) Color(0xFF4CC38A) else Color(0xFF1E8E3E)
    val greenPillBg = if (isDark) lerp(colorScheme.surfaceContainerHigh, greenAccent, 0.28f) else Color(0xFFCEEAD6)
    val bluePillBg = if (isDark) colorScheme.primaryContainer.copy(alpha = 0.65f) else colorScheme.primaryContainer

    val charCardBgBase = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.38f else 0.48f)
    val charCardBgBlue = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.58f else 0.72f)
    val charCardBgGreen = greenPillBg.copy(alpha = if (isDark) 0.5f else 0.65f)
    val charCardStrokeBase = colorScheme.primary.copy(alpha = 0.35f)

    val isDesktopIllustration = DesktopAdaptiveState.isDesktopPlatform
    val desktopScreenClip = remember {
        Path().apply {
            addRoundRect(RoundRect(Rect(54f, 26f, 188f, 162f), CornerRadius(12f, 12f)))
        }
    }
    val keycapArrowRight = remember {
        svgPath("M204 131H214M210.5 127.5L214 131L210.5 134.5")
    }

    // Marching dashes phase (24px total cycle over 6s)
    val dashPhase = -24f * t

    Canvas(modifier = modifier) {
        val contentW = 314f
        val contentH = 174f
        val scale = min(size.width / contentW, size.height / contentH)
        val offsetX = size.width / 2f - 160f * scale
        val offsetY = size.height / 2f - 90f * scale

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            // 1. Background Decorative Capsule (bgBreathe around 160, 90)
            val bgScale = evalKf(t, Kf(0f, 1f, EaseSmooth), Kf(50f, 1.025f, EaseSmooth), Kf(100f, 1f))
            val bgAlpha = evalKf(t, Kf(0f, 0.65f, EaseSmooth), Kf(50f, 0.8f, EaseSmooth), Kf(100f, 0.65f))
            withPivotTransform(160f, 90f, sx = bgScale) {
                drawRoundRect(
                    color = capsuleBg.copy(alpha = capsuleBg.alpha * (bgAlpha / 0.65f)),
                    topLeft = Offset(24f, 26f),
                    size = Size(272f, 128f),
                    cornerRadius = CornerRadius(64f, 64f)
                )
            }

            if (isDesktopIllustration) {
                // Desktop Studio Window Frame + Tactile Space & Arrow Keycaps
                // Guide lines from Space/Arrow keycaps to Mic & Next badges
                drawLine(
                    color = greenAccent,
                    start = Offset(218f, 85f),
                    end = Offset(234f, 85f),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), dashPhase)
                )
                drawLine(
                    color = phoneBodyColor,
                    start = Offset(218f, 131f),
                    end = Offset(234f, 131f),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), dashPhase)
                )

                // Desktop Studio Window Outer Bezel + Screen
                drawRoundRect(
                    color = phoneBodyColor,
                    topLeft = Offset(48f, 20f),
                    size = Size(146f, 148f),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawRoundRect(
                    color = screenColor,
                    topLeft = Offset(54f, 26f),
                    size = Size(134f, 136f),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                // Window titlebar traffic-light dots
                drawCircle(color = Color(0xFFEF5350).copy(alpha = 0.75f), radius = 2.6f, center = Offset(64f, 35f))
                drawCircle(color = Color(0xFFFFCA28).copy(alpha = 0.75f), radius = 2.6f, center = Offset(72f, 35f))
                drawCircle(color = greenAccent.copy(alpha = 0.75f), radius = 2.6f, center = Offset(80f, 35f))
                drawRoundRect(color = subtleBarColor, topLeft = Offset(64f, 48f), size = Size(48f, 7f), cornerRadius = CornerRadius(3.5f, 3.5f))

                // Tactile [Space] Keycap (depresses on Act 2: 33%..60%)
                val spacePress = evalKf(t, Kf(0f, 0f), Kf(33f, 0f), Kf(37f, 2.8f), Kf(52f, 2.8f, EaseOutBack), Kf(56f, -0.5f), Kf(60f, 0f), Kf(100f, 0f))
                drawRoundRect(
                    color = greenAccent.copy(alpha = 0.45f),
                    topLeft = Offset(197f, 74f),
                    size = Size(24f, 24f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                withTransform({ translate(0f, spacePress) }) {
                    drawRoundRect(
                        color = screenColor,
                        topLeft = Offset(197f, 71f),
                        size = Size(24f, 24f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = greenAccent,
                        topLeft = Offset(197f, 71f),
                        size = Size(24f, 24f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 2f)
                    )
                    // Spacebar U-bar symbol
                    drawLine(color = greenAccent, start = Offset(202f, 84f), end = Offset(202f, 87f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawLine(color = greenAccent, start = Offset(202f, 87f), end = Offset(216f, 87f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawLine(color = greenAccent, start = Offset(216f, 84f), end = Offset(216f, 87f), strokeWidth = 2f, cap = StrokeCap.Round)
                }

                // Tactile [→] Keycap (depresses on Act 3: 64%..84%)
                val arrowPress = evalKf(t, Kf(0f, 0f), Kf(64f, 0f), Kf(67f, 2.8f), Kf(75f, 2.8f, EaseOutBack), Kf(79f, -0.5f), Kf(84f, 0f), Kf(100f, 0f))
                drawRoundRect(
                    color = phoneBodyColor.copy(alpha = 0.45f),
                    topLeft = Offset(197f, 120f),
                    size = Size(24f, 24f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                withTransform({ translate(0f, arrowPress) }) {
                    drawRoundRect(
                        color = screenColor,
                        topLeft = Offset(197f, 117f),
                        size = Size(24f, 24f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = phoneBodyColor,
                        topLeft = Offset(197f, 117f),
                        size = Size(24f, 24f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 2f)
                    )
                    withTransform({ translate(0f, -2f) }) {
                        drawPath(keycapArrowRight, color = phoneBodyColor, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            } else {
                // 2. Mobile Physical Volume Keys & Dashed Guide Lines
                drawLine(
                    color = greenAccent,
                    start = Offset(205f, 85f),
                    end = Offset(234f, 85f),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), dashPhase)
                )
                val gBeamAlpha = evalKf(t, Kf(0f, 0f), Kf(35f, 0f), Kf(37f, 1f, EaseOutQuart), Kf(44f, 1f), Kf(46f, 0f), Kf(100f, 0f))
                if (gBeamAlpha > 0.01f) {
                    val gBeamProg = evalKf(t, Kf(0f, 0f), Kf(35f, 0f, EaseOutQuart), Kf(44f, 1f), Kf(100f, 1f))
                    val sX = lerpF(205f, 234f, (gBeamProg - 0.35f).coerceIn(0f, 1f))
                    val eX = lerpF(205f, 234f, (gBeamProg * 1.25f).coerceIn(0f, 1f))
                    if (eX > sX) {
                        drawLine(color = greenAccent.copy(alpha = gBeamAlpha), start = Offset(sX, 85f), end = Offset(eX, 85f), strokeWidth = 3.2f, cap = StrokeCap.Round)
                    }
                }

                drawLine(
                    color = phoneBodyColor,
                    start = Offset(205f, 131f),
                    end = Offset(234f, 131f),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), dashPhase)
                )
                val bBeamAlpha = evalKf(t, Kf(0f, 0f), Kf(65f, 0f), Kf(67f, 1f, EaseOutQuart), Kf(74f, 1f), Kf(76f, 0f), Kf(100f, 0f))
                if (bBeamAlpha > 0.01f) {
                    val bBeamProg = evalKf(t, Kf(0f, 0f), Kf(65f, 0f, EaseOutQuart), Kf(74f, 1f), Kf(100f, 1f))
                    val sX = lerpF(205f, 234f, (bBeamProg - 0.35f).coerceIn(0f, 1f))
                    val eX = lerpF(205f, 234f, (bBeamProg * 1.25f).coerceIn(0f, 1f))
                    if (eX > sX) {
                        drawLine(color = primaryBright.copy(alpha = bBeamAlpha), start = Offset(sX, 131f), end = Offset(eX, 131f), strokeWidth = 3.2f, cap = StrokeCap.Round)
                    }
                }

                val volUpTx = evalKf(t, Kf(0f, 0f), Kf(33f, 0f), Kf(37f, -4.8f), Kf(52f, -4.8f, EaseOutBack), Kf(56f, 1.2f), Kf(60f, 0f), Kf(100f, 0f))
                withTransform({ translate(volUpTx, 0f) }) {
                    drawPath(paths.volUpBtn, color = greenAccent)
                    drawCircle(color = greenAccent, radius = 3f, center = Offset(211f, 85f))
                }

                val volDownTx = evalKf(t, Kf(0f, 0f), Kf(64f, 0f), Kf(67f, -4.8f), Kf(75f, -4.8f, EaseOutBack), Kf(79f, 1.2f), Kf(84f, 0f), Kf(100f, 0f))
                withTransform({ translate(volDownTx, 0f) }) {
                    drawPath(paths.volDownBtn, color = phoneBodyColor)
                    drawCircle(color = phoneBodyColor, radius = 3f, center = Offset(211f, 131f))
                }

                // 3. Smartphone Body & Inner Screen
                drawPath(paths.phoneOuter, color = phoneBodyColor)
                drawPath(paths.phoneScreen, color = screenColor)

                // Earpiece & Top Bar
                drawRoundRect(color = subtleBarColor, topLeft = Offset(115f, 34f), size = Size(28f, 4f), cornerRadius = CornerRadius(2f, 2f))
                drawRoundRect(color = subtleBarColor, topLeft = Offset(76f, 48f), size = Size(40f, 8f), cornerRadius = CornerRadius(4f, 4f))
            }

            // 4. Top-Right "Eye" Button (Act 1: 0%..32%)
            val eyeRipAlpha = evalKf(t, Kf(0f, 0f), Kf(6f, 0f), Kf(8f, 0.75f, EaseOutExpo), Kf(22f, 0f), Kf(100f, 0f))
            if (eyeRipAlpha > 0.01f) {
                val eyeRipScale = evalKf(t, Kf(0f, 0.9f), Kf(6f, 0.9f), Kf(8f, 1f, EaseOutExpo), Kf(22f, 1.85f), Kf(100f, 1.85f))
                withPivotTransform(170f, 52f, sx = eyeRipScale) {
                    drawCircle(color = primaryBright.copy(alpha = eyeRipAlpha), radius = 15f, center = Offset(170f, 52f), style = Stroke(width = 2f))
                }
            }

            val eyeOuterScale = evalKf(t, Kf(0f, 1f), Kf(3f, 1f), Kf(6f, 0.8f, EaseOutBack), Kf(12f, 1.24f, EaseOutQuart), Kf(18f, 0.95f), Kf(24f, 1.03f), Kf(32f, 1f), Kf(100f, 1f))
            withPivotTransform(170f, 52f, sx = eyeOuterScale) {
                drawCircle(color = primaryAccent.copy(alpha = 0.24f), radius = 15f, center = Offset(170f, 52f))
            }

            val eyeInnerScale = evalKf(t, Kf(0f, 1f), Kf(3f, 1f), Kf(6f, 0.78f, EaseOutBack), Kf(11f, 1.18f, EaseOutQuart), Kf(17f, 0.96f), Kf(23f, 1.02f), Kf(30f, 1f), Kf(100f, 1f))
            val eyeInnerTint = evalKf(t, Kf(0f, 0f), Kf(6f, 0f), Kf(11f, 1f), Kf(17f, 0f), Kf(100f, 0f))
            withPivotTransform(170f, 52f, sx = eyeInnerScale) {
                drawCircle(color = lerp(phoneBodyColor, primaryBright, eyeInnerTint), radius = 11f, center = Offset(170f, 52f))
            }

            val eyeBlinkSx = evalKf(t, Kf(0f, 1f), Kf(3f, 1f), Kf(6f, 1.08f, EaseOutBack), Kf(11f, 1.14f), Kf(18f, 0.98f), Kf(28f, 1f), Kf(100f, 1f))
            val eyeBlinkSy = evalKf(t, Kf(0f, 1f), Kf(3f, 1f), Kf(6f, 0.12f, EaseOutBack), Kf(11f, 1.18f), Kf(18f, 0.98f), Kf(28f, 1f), Kf(100f, 1f))
            val pupilTx = evalKf(t, Kf(0f, 0f), Kf(7f, 0f), Kf(12f, -1.8f), Kf(20f, -1.8f), Kf(28f, 0f), Kf(100f, 0f))
            val pupilTy = evalKf(t, Kf(0f, 0f), Kf(7f, 0f), Kf(12f, 1.2f), Kf(20f, 1.2f), Kf(28f, 0f), Kf(100f, 0f))
            val pupilScale = evalKf(t, Kf(0f, 1f), Kf(7f, 1f), Kf(12f, 1.2f), Kf(20f, 1.2f), Kf(28f, 1f), Kf(100f, 1f))
            withPivotTransform(170f, 52f, sx = eyeBlinkSx, sy = eyeBlinkSy) {
                drawPath(paths.eyeOutline, color = onPrimaryAccent, style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                withPivotTransform(170f, 52f, tx = pupilTx, ty = pupilTy, sx = pupilScale) {
                    drawCircle(color = onPrimaryAccent, radius = 2f, center = Offset(170f, 52f))
                }
            }

            // Dashed Arc from Eye to Card + Energy Beam
            drawPath(
                path = paths.eyeToCardArc,
                color = phoneBodyColor,
                style = Stroke(width = 1.8f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), dashPhase))
            )
            val arcPulseAlpha = evalKf(t, Kf(0f, 0f), Kf(6f, 0f), Kf(8f, 1f, EaseOutQuart), Kf(17f, 1f), Kf(19f, 0f), Kf(100f, 0f))
            if (arcPulseAlpha > 0.01f) {
                drawPath(
                    path = paths.eyeToCardArc,
                    color = primaryBright.copy(alpha = arcPulseAlpha),
                    style = Stroke(width = 2.8f, cap = StrokeCap.Round)
                )
            }

            // 5. Center Screen Clipped Area (supports Act 3 page-turn carousel!)
            clipPath(if (isDesktopIllustration) desktopScreenClip else paths.phoneScreen) {
                // Outgoing Card (Act 3: 68%..81% slides left -112px)
                val outAlpha = evalKf(
                    t,
                    Kf(0f, 0f), Kf(67f, 0f, EaseStepEnd),
                    Kf(68f, 1f, BezierCurve(0.32f, 0f, 0.2f, 1f)),
                    Kf(81f, 0f), Kf(100f, 0f)
                )
                if (outAlpha > 0.01f) {
                    val outTx = evalKf(t, Kf(0f, 0f), Kf(67f, 0f, EaseStepEnd), Kf(68f, 0f, BezierCurve(0.32f, 0f, 0.2f, 1f)), Kf(81f, -112f), Kf(100f, 0f))
                    val outScale = evalKf(t, Kf(0f, 1f), Kf(67f, 1f, EaseStepEnd), Kf(68f, 1f, BezierCurve(0.32f, 0f, 0.2f, 1f)), Kf(81f, 0.88f), Kf(100f, 1f))
                    withPivotTransform(129f, 117f, tx = outTx, sx = outScale) {
                        drawRoundRect(color = charCardBgBase.copy(alpha = charCardBgBase.alpha * outAlpha), topLeft = Offset(76f, 74f), size = Size(106f, 86f), cornerRadius = CornerRadius(16f, 16f))
                        drawRoundRect(color = charCardStrokeBase.copy(alpha = charCardStrokeBase.alpha * outAlpha), topLeft = Offset(76f, 74f), size = Size(106f, 86f), cornerRadius = CornerRadius(16f, 16f), style = Stroke(width = 2f))
                        drawRoundRect(color = primaryAccent.copy(alpha = 0.38f * outAlpha), topLeft = Offset(115f, 85f), size = Size(28f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))
                        drawPath(paths.charWen, color = phoneBodyColor.copy(alpha = outAlpha), style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }

                // Main Card (Act 1 bounce + Act 3 slide-in from right +112px)
                val mainTx = evalKf(
                    t,
                    Kf(0f, 0f), Kf(67f, 0f, EaseStepEnd),
                    Kf(68f, 112f, EaseSpringIn),
                    Kf(80f, -4.5f, EaseOutBack),
                    Kf(86f, 1.2f),
                    Kf(92f, 0f), Kf(100f, 0f)
                )
                val mainTy = evalKf(
                    t,
                    Kf(0f, 0f), Kf(8f, 0f),
                    Kf(12f, 2f, EaseOutBack),
                    Kf(18f, -2f, EaseOutQuart),
                    Kf(24f, 0.5f),
                    Kf(32f, 0f), Kf(100f, 0f)
                )
                val mainScale = evalKf(
                    t,
                    Kf(0f, 1f), Kf(8f, 1f),
                    Kf(12f, 0.93f, EaseOutBack),
                    Kf(18f, 1.05f, EaseOutQuart),
                    Kf(24f, 0.99f),
                    Kf(32f, 1f),
                    Kf(67f, 1f, EaseStepEnd),
                    Kf(68f, 0.88f, EaseSpringIn),
                    Kf(80f, 1.02f, EaseOutBack),
                    Kf(86f, 0.995f),
                    Kf(92f, 1f), Kf(100f, 1f)
                )
                val mainAlpha = evalKf(
                    t,
                    Kf(0f, 1f), Kf(67f, 1f, EaseStepEnd),
                    Kf(68f, 0f, EaseSpringIn),
                    Kf(80f, 1f), Kf(100f, 1f)
                )

                val blueTint = evalKf(t, Kf(0f, 0f), Kf(10f, 0f), Kf(17f, 1f), Kf(30f, 0f), Kf(100f, 0f))
                val greenTint = evalKf(t, Kf(0f, 0f), Kf(37f, 0f), Kf(43f, 1f), Kf(52f, 1f), Kf(60f, 0f), Kf(100f, 0f))
                val cardBg = lerp(lerp(charCardBgBase, charCardBgBlue, blueTint), charCardBgGreen, greenTint)
                val cardStroke = lerp(lerp(charCardStrokeBase, primaryBright, blueTint), greenAccent, greenTint)

                withPivotTransform(129f, 117f, tx = mainTx, ty = mainTy, sx = mainScale) {
                    drawRoundRect(color = cardBg.copy(alpha = cardBg.alpha * mainAlpha), topLeft = Offset(76f, 74f), size = Size(106f, 86f), cornerRadius = CornerRadius(16f, 16f))
                    drawRoundRect(color = cardStroke.copy(alpha = cardStroke.alpha * mainAlpha), topLeft = Offset(76f, 74f), size = Size(106f, 86f), cornerRadius = CornerRadius(16f, 16f), style = Stroke(width = 2f))

                    // Pinyin Bar (pinyinAnim around 129, 87.5)
                    val pySx = evalKf(t, Kf(0f, 1f), Kf(10f, 1f), Kf(17f, 1.18f), Kf(30f, 1f), Kf(37f, 1f), Kf(41f, 1.38f), Kf(46f, 1.15f), Kf(51f, 1.35f), Kf(60f, 1f), Kf(100f, 1f))
                    val pySy = evalKf(t, Kf(0f, 1f), Kf(10f, 1f), Kf(17f, 1.15f), Kf(30f, 1f), Kf(37f, 1f), Kf(41f, 1.2f), Kf(46f, 1.1f), Kf(51f, 1.2f), Kf(60f, 1f), Kf(100f, 1f))
                    val pyColor = lerp(primaryAccent.copy(alpha = 0.42f), greenAccent, greenTint).copy(alpha = mainAlpha)
                    withPivotTransform(129f, 87.5f, sx = pySx, sy = pySy) {
                        drawRoundRect(color = pyColor, topLeft = Offset(115f, 85f), size = Size(28f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))
                    }

                    // Hanzi "文" (hanziZoom around 129, 120)
                    val hzTx = evalKf(
                        t,
                        Kf(0f, 0f), Kf(67f, 0f, EaseStepEnd),
                        Kf(68f, 10f, EaseSpringIn),
                        Kf(81f, -2f, EaseOutBack),
                        Kf(87f, 0.5f),
                        Kf(92f, 0f), Kf(100f, 0f)
                    )
                    val hzTy = evalKf(
                        t,
                        Kf(0f, 0f), Kf(8f, 0f),
                        Kf(12f, 4f, EaseOutBack),
                        Kf(18f, -3f, EaseOutQuart),
                        Kf(23f, 1f), Kf(27f, -0.5f), Kf(32f, 0f),
                        Kf(38f, 0f), Kf(42f, -1.5f), Kf(46f, 0f), Kf(50f, -1.5f), Kf(54f, 0f),
                        Kf(100f, 0f)
                    )
                    val hzScale = evalKf(
                        t,
                        Kf(0f, 1f), Kf(8f, 1f),
                        Kf(12f, 0.52f, EaseOutBack),
                        Kf(18f, 1.24f, EaseOutQuart),
                        Kf(23f, 0.93f), Kf(27f, 1.04f), Kf(32f, 1f),
                        Kf(38f, 1f), Kf(42f, 1.08f), Kf(46f, 1.01f), Kf(50f, 1.09f), Kf(54f, 1.02f), Kf(60f, 1f),
                        Kf(67f, 1f, EaseStepEnd),
                        Kf(68f, 0.84f, EaseSpringIn),
                        Kf(81f, 1.06f, EaseOutBack),
                        Kf(87f, 0.985f),
                        Kf(92f, 1f), Kf(100f, 1f)
                    )
                    withPivotTransform(129f, 120f, tx = hzTx, ty = hzTy, sx = hzScale) {
                        drawPath(paths.charWen, color = phoneBodyColor.copy(alpha = mainAlpha), style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    // Act 2 Equalizer Bars at bottom of card (37%..58%)
                    val eqAlpha = evalKf(t, Kf(0f, 0f), Kf(37f, 0f), Kf(40f, 0.95f), Kf(53f, 0.95f), Kf(58f, 0f), Kf(100f, 0f)) * mainAlpha
                    if (eqAlpha > 0.01f) {
                        val eq1Sy = evalKf(t, Kf(0f, 0.1f), Kf(37f, 0.1f), Kf(41f, 1f), Kf(46f, 0.4f), Kf(51f, 1f), Kf(58f, 0.1f), Kf(100f, 0.1f))
                        val eq2Sy = evalKf(t, Kf(0f, 0.1f), Kf(37f, 0.1f), Kf(40f, 0.5f), Kf(44f, 1.25f), Kf(49f, 0.5f), Kf(53f, 1.25f), Kf(58f, 0.1f), Kf(100f, 0.1f))
                        val eq3Sy = evalKf(t, Kf(0f, 0.1f), Kf(37f, 0.1f), Kf(41f, 1.45f), Kf(45f, 0.55f), Kf(50f, 1.45f), Kf(58f, 0.1f), Kf(100f, 0.1f))

                        val eqBars = listOf(
                            115.5f to eq1Sy,
                            121.5f to eq2Sy,
                            127.5f to eq3Sy,
                            133.5f to eq2Sy,
                            139.5f to eq1Sy
                        )
                        eqBars.forEach { (x, sy) ->
                            withPivotTransform(x + 1.5f, 151f, sx = 1f, sy = sy) {
                                drawRoundRect(color = greenAccent.copy(alpha = eqAlpha), topLeft = Offset(x, 147f), size = Size(3f, 8f), cornerRadius = CornerRadius(1.5f, 1.5f))
                            }
                        }
                    }
                }
            }

            // 6. Left Floating "aA" Pill Badge (badgeBounce around 61, 82)
            val aaTx = evalKf(
                t,
                Kf(0f, 0f), Kf(7f, 0f),
                Kf(11f, 4f, EaseOutBack), Kf(17f, -6f, EaseOutQuart), Kf(22f, 2f), Kf(27f, -1f), Kf(32f, 0f),
                Kf(68f, 0f), Kf(73f, -2f, EaseOutBack), Kf(79f, -3f), Kf(85f, 0.5f), Kf(92f, 0f), Kf(100f, 0f)
            )
            val aaTy = evalKf(
                t,
                Kf(0f, 0f), Kf(7f, 0f),
                Kf(11f, 1f, EaseOutBack), Kf(17f, -3f, EaseOutQuart), Kf(22f, 1f), Kf(27f, -0.5f), Kf(32f, 0f),
                Kf(68f, 0f), Kf(73f, 1f, EaseOutBack), Kf(79f, -3f), Kf(85f, 0.5f), Kf(92f, 0f), Kf(100f, 0f)
            )
            val aaScale = evalKf(
                t,
                Kf(0f, 1f), Kf(7f, 1f),
                Kf(11f, 0.84f, EaseOutBack), Kf(17f, 1.22f, EaseOutQuart), Kf(22f, 0.95f), Kf(27f, 1.03f), Kf(32f, 1f),
                Kf(68f, 1f), Kf(73f, 0.94f, EaseOutBack), Kf(79f, 1.08f), Kf(85f, 0.99f), Kf(92f, 1f), Kf(100f, 1f)
            )
            val aaRot = evalKf(
                t,
                Kf(0f, 0f), Kf(7f, 0f),
                Kf(11f, 5f, EaseOutBack), Kf(17f, -8f, EaseOutQuart), Kf(22f, 3f), Kf(27f, -1f), Kf(32f, 0f),
                Kf(68f, 0f), Kf(73f, -3f, EaseOutBack), Kf(79f, 4f), Kf(85f, -1f), Kf(92f, 0f), Kf(100f, 0f)
            )
            val bigATy = evalKf(t, Kf(0f, 0f), Kf(11f, 0f), Kf(17f, -2.2f, EaseOutBack), Kf(22f, 0.6f), Kf(28f, 0f), Kf(100f, 0f))
            val bigAScale = evalKf(t, Kf(0f, 1f), Kf(11f, 1f), Kf(17f, 1.18f, EaseOutBack), Kf(22f, 0.95f), Kf(28f, 1f), Kf(100f, 1f))

            withPivotTransform(61f, 82f, tx = aaTx, ty = aaTy, sx = aaScale, rotDeg = aaRot) {
                drawRoundRect(color = phoneBodyColor, topLeft = Offset(34f, 68f), size = Size(54f, 28f), cornerRadius = CornerRadius(14f, 14f))
                drawRoundRect(color = screenColor, topLeft = Offset(34f, 68f), size = Size(54f, 28f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 2.5f))
                drawPath(paths.badgeSmallA, color = Color.White, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                withPivotTransform(61.5f, 81f, ty = bigATy, sx = bigAScale) {
                    drawPath(paths.badgeBigA, color = Color.White, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            // 7. Right Callout Pills: Record (Act 2) & Next Page (Act 3)
            // Record Halo
            val recHaloAlpha = evalKf(t, Kf(0f, 0f), Kf(36f, 0f), Kf(39f, 0.65f, EaseOutExpo), Kf(55f, 0f), Kf(100f, 0f))
            if (recHaloAlpha > 0.01f) {
                val recHaloScale = evalKf(t, Kf(0f, 0.95f), Kf(36f, 0.95f), Kf(39f, 1f, EaseOutExpo), Kf(55f, 1.36f), Kf(100f, 1.36f))
                withPivotTransform(259f, 85f, sx = recHaloScale) {
                    drawRoundRect(color = greenAccent.copy(alpha = recHaloAlpha), topLeft = Offset(232f, 66f), size = Size(54f, 38f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 2f))
                }
            }

            val recTx = evalKf(t, Kf(0f, 0f), Kf(35f, 0f), Kf(40f, 5f, EaseOutQuart), Kf(45f, 3f), Kf(50f, 4.5f, EaseOutBack), Kf(57f, -1.5f), Kf(63f, 0f), Kf(100f, 0f))
            val recScale = evalKf(t, Kf(0f, 1f), Kf(35f, 1f), Kf(40f, 1.14f, EaseOutQuart), Kf(45f, 1.06f), Kf(50f, 1.12f, EaseOutBack), Kf(57f, 0.97f), Kf(63f, 1f), Kf(100f, 1f))
            withPivotTransform(259f, 85f, tx = recTx, sx = recScale) {
                drawRoundRect(color = greenPillBg, topLeft = Offset(232f, 66f), size = Size(54f, 38f), cornerRadius = CornerRadius(14f, 14f))
                drawRoundRect(color = screenColor, topLeft = Offset(232f, 66f), size = Size(54f, 38f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 2f))

                // Sound wave arcs around mic
                val swAlpha = evalKf(t, Kf(0f, 0f), Kf(37f, 0f), Kf(41f, 0.9f), Kf(46f, 0.4f), Kf(51f, 0.95f), Kf(58f, 0f), Kf(100f, 0f))
                if (swAlpha > 0.01f) {
                    val swScale = evalKf(t, Kf(0f, 0.7f), Kf(37f, 0.7f), Kf(41f, 1.05f), Kf(46f, 0.85f), Kf(51f, 1.12f), Kf(58f, 0.7f), Kf(100f, 0.7f))
                    withPivotTransform(259f, 81f, sx = swScale) {
                        drawPath(paths.micSoundWaves, color = greenAccent.copy(alpha = swAlpha), style = Stroke(width = 1.6f, cap = StrokeCap.Round))
                    }
                }

                // Mic Icon Dance (259, 83.5)
                val micTy = evalKf(t, Kf(0f, 0f), Kf(36f, 0f), Kf(40f, -2.5f), Kf(45f, 0.5f), Kf(50f, -2f), Kf(55f, 0f), Kf(100f, 0f))
                val micScale = evalKf(t, Kf(0f, 1f), Kf(36f, 1f), Kf(40f, 1.12f), Kf(45f, 0.96f), Kf(50f, 1.1f), Kf(55f, 1f), Kf(100f, 1f))
                val micRot = evalKf(t, Kf(0f, 0f), Kf(36f, 0f), Kf(40f, -7f), Kf(45f, 6f), Kf(50f, -4f), Kf(55f, 0f), Kf(100f, 0f))
                withPivotTransform(259f, 83.5f, ty = micTy, sx = micScale, rotDeg = micRot) {
                    drawRoundRect(color = greenAccent, topLeft = Offset(255f, 74f), size = Size(8f, 13f), cornerRadius = CornerRadius(4f, 4f))
                    drawPath(paths.micStand, color = greenAccent, style = Stroke(width = 2f, cap = StrokeCap.Round))
                }
            }

            // Next Page Halo
            val nextHaloAlpha = evalKf(t, Kf(0f, 0f), Kf(66f, 0f), Kf(69f, 0.65f, EaseOutExpo), Kf(83f, 0f), Kf(100f, 0f))
            if (nextHaloAlpha > 0.01f) {
                val nextHaloScale = evalKf(t, Kf(0f, 0.95f), Kf(66f, 0.95f), Kf(69f, 1f, EaseOutExpo), Kf(83f, 1.36f), Kf(100f, 1.36f))
                withPivotTransform(259f, 131f, sx = nextHaloScale) {
                    drawRoundRect(color = phoneBodyColor.copy(alpha = nextHaloAlpha), topLeft = Offset(232f, 112f), size = Size(54f, 38f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 2f))
                }
            }

            val nextTx = evalKf(t, Kf(0f, 0f), Kf(65f, 0f), Kf(71f, 5.5f, EaseOutBack), Kf(78f, -1.5f), Kf(84f, 0.8f), Kf(90f, 0f), Kf(100f, 0f))
            val nextScale = evalKf(t, Kf(0f, 1f), Kf(65f, 1f), Kf(71f, 1.14f, EaseOutBack), Kf(78f, 0.96f), Kf(84f, 1.02f), Kf(90f, 1f), Kf(100f, 1f))
            withPivotTransform(259f, 131f, tx = nextTx, sx = nextScale) {
                drawRoundRect(color = bluePillBg, topLeft = Offset(232f, 112f), size = Size(54f, 38f), cornerRadius = CornerRadius(14f, 14f))
                drawRoundRect(color = screenColor, topLeft = Offset(232f, 112f), size = Size(54f, 38f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 2f))

                // Next Arrow Dash (255.5, 131)
                val arrTx = evalKf(t, Kf(0f, 0f), Kf(65f, 0f), Kf(68f, -4f, EaseOutBack), Kf(73f, 3.8f, EaseOutQuart), Kf(79f, -1f), Kf(88f, 0f), Kf(100f, 0f))
                val arrSx = evalKf(t, Kf(0f, 1f), Kf(65f, 1f), Kf(68f, 0.88f, EaseOutBack), Kf(73f, 1.1f, EaseOutQuart), Kf(79f, 0.97f), Kf(88f, 1f), Kf(100f, 1f))
                withPivotTransform(255.5f, 131f, tx = arrTx, sx = arrSx, sy = 1f) {
                    drawPath(paths.nextArrow, color = phoneBodyColor, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // Next Bar Recoil (267, 131)
                val barTx = evalKf(t, Kf(0f, 0f), Kf(71f, 0f), Kf(74f, 2.8f, EaseOutBack), Kf(80f, -0.8f), Kf(88f, 0f), Kf(100f, 0f))
                val barSy = evalKf(t, Kf(0f, 1f), Kf(71f, 1f), Kf(74f, 1.12f, EaseOutBack), Kf(80f, 0.96f), Kf(88f, 1f), Kf(100f, 1f))
                withPivotTransform(267f, 131f, tx = barTx, sx = 1f, sy = barSy) {
                    drawPath(paths.nextBar, color = phoneBodyColor, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

// ============================================================================
// PAGE 4 (INDEX 3): 随时试听与导出项目 (4.0s Narrative Flow Animation)
// ============================================================================

private object CachedTipPlaybackPaths {
    val bgArch by lazy(LazyThreadSafetyMode.NONE) { svgPath("M92 225C92 112 178 24 286 24C330 24 370 38 400 64V201C400 214.255 389.255 225 376 225H92Z") }
    val playTriangle by lazy(LazyThreadSafetyMode.NONE) { svgPath("M49 117L64 127L49 137V117Z") }
    val refreshArc1 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M172 119C174.5 112.5 182 109.5 189 112C194 113.8 197.5 118 198.5 123") }
    val refreshHead1 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M202 115L199 124L190 121") }
    val refreshArc2 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M198 135C195.5 141.5 188 144.5 181 142C176 140.2 172.5 136 171.5 131") }
    val refreshHead2 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M168 139L171 130L180 133") }
    val curvedArrow by lazy(LazyThreadSafetyMode.NONE) { svgPath("M156 76C188 42 244 38 284 68L294 56L302 88L268 86L276 76C240 52 192 54 156 76Z") }
    val folderBack by lazy(LazyThreadSafetyMode.NONE) { svgPath("M242 106C242 100.477 246.477 96 252 96H286L298 108H352C357.523 108 362 112.477 362 118V188C362 193.523 357.523 198 352 198H252C246.477 198 242 193.523 242 188V106Z") }
    val folderFrontFlap by lazy(LazyThreadSafetyMode.NONE) { svgPath("M246 130C246.8 124.8 251.2 121 256.5 121H362C367.8 121 372.2 126.2 371.2 132L361.5 190C360.7 194.6 356.7 198 352 198H246C242.5 198 240 195 240.5 191.5L246 130Z") }
    val folderCrease by lazy(LazyThreadSafetyMode.NONE) { svgPath("M242 106V192C242 195.5 244 198 247 198L252 124L242 106Z") }
    val exportArrow by lazy(LazyThreadSafetyMode.NONE) { svgPath("M339 117L356 100M356 100H343M356 100V113") }
    val accent1 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M358 72L364 52") }
    val accent2 by lazy(LazyThreadSafetyMode.NONE) { svgPath("M368 80L384 68") }
}

@Composable
private fun TipIllustrationPlaybackAndExport(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val paths = CachedTipPlaybackPaths
    val canvasClip = remember {
        Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, 400f, 225f), CornerRadius(24f, 24f)))
        }
    }

    val archColor = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.38f else 0.68f)
    val cardColor = colorScheme.surface
    val cardShadow = colorScheme.onSurface.copy(alpha = if (isDark) 0.28f else 0.14f)
    val primaryColor = if (isDark) {
        lerp(colorScheme.primary, colorScheme.primaryContainer, 0.15f)
    } else {
        lerp(colorScheme.primary, Color(0xFF3E557B), 0.35f)
    }
    val onPrimaryColor = if (primaryColor.luminance() > 0.5f) Color(0xFF1A212E) else Color.White
    val barIdleColor = primaryColor.copy(alpha = 0.45f)
    val barMidColor = primaryColor.copy(alpha = 0.72f)
    val arrowShimmerColor = lerp(primaryColor, Color.White, 0.45f)

    // Warm golden-amber 3D folder harmonized with dynamic theme
    val folderBackColor = if (isDark) {
        lerp(Color(0xFFE0AB58), colorScheme.tertiaryContainer, 0.25f)
    } else {
        lerp(Color(0xFFE0AB58), colorScheme.primary, 0.12f)
    }
    val folderFrontColor = if (isDark) {
        lerp(Color(0xFFF3C97E), colorScheme.tertiary, 0.2f)
    } else {
        lerp(Color(0xFFF3C97E), colorScheme.primaryContainer, 0.12f)
    }
    val folderCreaseColor = lerp(folderBackColor, Color.Black, 0.18f)
    val exportBadgeColor = primaryColor
    val sparkColor = folderFrontColor

    val cCardBounce = BezierCurve(0.18f, 0.9f, 0.32f, 1.25f)
    val cPlayPop = BezierCurve(0.16f, 0.95f, 0.28f, 1.38f)
    val cWaveBar = BezierCurve(0.2f, 0.9f, 0.3f, 1.3f)
    val cSpin = BezierCurve(0.18f, 0.92f, 0.28f, 1.18f)

    Canvas(modifier = modifier) {
        val contentW = 392f
        val contentH = 216f
        val scale = min(size.width / contentW, size.height / contentH)
        val offsetX = size.width / 2f - 200f * scale
        val offsetY = size.height / 2f - 112.5f * scale

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            clipPath(canvasClip) {
                // 1. Background Arch (bgArchBreathe around 286, 150)
                val archTx = evalKf(t, Kf(0f, 0f), Kf(25f, -3f, BezierCurve(0.2f, 0.85f, 0.35f, 1.15f)), Kf(60f, 4f, EaseStandard), Kf(100f, 0f))
                val archTy = evalKf(t, Kf(0f, 0f), Kf(25f, 2f, BezierCurve(0.2f, 0.85f, 0.35f, 1.15f)), Kf(60f, -3f, EaseStandard), Kf(100f, 0f))
                val archScale = evalKf(t, Kf(0f, 1f), Kf(25f, 0.985f, BezierCurve(0.2f, 0.85f, 0.35f, 1.15f)), Kf(60f, 1.03f, EaseStandard), Kf(100f, 1f))
                withPivotTransform(286f, 150f, tx = archTx, ty = archTy, sx = archScale) {
                    drawPath(paths.bgArch, color = archColor)
                }

                // 2. Left Floating Playback Card & Shadow
                val shTx = evalKf(t, Kf(0f, 0f, EaseSym), Kf(8f, 0f, cCardBounce), Kf(22f, 0f, EaseSym), Kf(45f, 1f, EaseStandard), Kf(100f, 0f))
                val shTy = evalKf(t, Kf(0f, 0f, EaseSym), Kf(8f, -1f, cCardBounce), Kf(22f, 4f, EaseSym), Kf(45f, 2.5f, EaseStandard), Kf(100f, 0f))
                val shSx = evalKf(t, Kf(0f, 1f, EaseSym), Kf(8f, 0.96f, cCardBounce), Kf(22f, 1.03f, EaseSym), Kf(45f, 1.015f, EaseStandard), Kf(100f, 1f))
                val shSy = evalKf(t, Kf(0f, 1f, EaseSym), Kf(8f, 0.96f, cCardBounce), Kf(22f, 1.06f, EaseSym), Kf(45f, 1.015f, EaseStandard), Kf(100f, 1f))
                val shAlpha = evalKf(t, Kf(0f, 0.6f, EaseSym), Kf(8f, 0.75f, cCardBounce), Kf(22f, 0.42f, EaseSym), Kf(45f, 0.48f, EaseStandard), Kf(100f, 0.6f))
                withPivotTransform(117f, 133f, tx = shTx, ty = shTy, sx = shSx, sy = shSy) {
                    drawRoundRect(
                        color = cardShadow.copy(alpha = cardShadow.alpha * (shAlpha / 0.6f)),
                        topLeft = Offset(18f, 92f),
                        size = Size(198f, 82f),
                        cornerRadius = CornerRadius(18f, 18f)
                    )
                }

                val cTx = evalKf(t, Kf(0f, 0f, EaseSym), Kf(8f, 0f, cCardBounce), Kf(22f, 0f, EaseSym), Kf(45f, 2f, EaseStandard), Kf(75f, 0f, EaseStandard), Kf(100f, 0f))
                val cTy = evalKf(t, Kf(0f, 0f, EaseSym), Kf(8f, 2.5f, cCardBounce), Kf(22f, -5f, EaseSym), Kf(45f, -3f, EaseStandard), Kf(75f, 0.5f, EaseStandard), Kf(100f, 0f))
                val cSx = evalKf(t, Kf(0f, 1f, EaseSym), Kf(8f, 0.98f, cCardBounce), Kf(22f, 1.02f, EaseSym), Kf(45f, 1.01f, EaseStandard), Kf(75f, 0.998f, EaseStandard), Kf(100f, 1f))
                val cSy = evalKf(t, Kf(0f, 1f, EaseSym), Kf(8f, 0.97f, cCardBounce), Kf(22f, 1.02f, EaseSym), Kf(45f, 1.01f, EaseStandard), Kf(75f, 0.998f, EaseStandard), Kf(100f, 1f))
                val cRot = evalKf(t, Kf(0f, 0f, EaseSym), Kf(8f, -0.4f, cCardBounce), Kf(22f, 0.6f, EaseSym), Kf(45f, 0.3f, EaseStandard), Kf(75f, 0f, EaseStandard), Kf(100f, 0f))

                withPivotTransform(117f, 127f, tx = cTx, ty = cTy, sx = cSx, sy = cSy, rotDeg = cRot) {
                    drawRoundRect(color = cardColor, topLeft = Offset(18f, 86f), size = Size(198f, 82f), cornerRadius = CornerRadius(18f, 18f))

                    // Play Ripple (54, 127)
                    val pRipAlpha = evalKf(t, Kf(0f, 0f), Kf(9f, 0f, EaseOutExpo), Kf(14f, 0.65f), Kf(38f, 0f), Kf(100f, 0f))
                    if (pRipAlpha > 0.01f) {
                        val pRipScale = evalKf(t, Kf(0f, 0.85f), Kf(9f, 0.85f, EaseOutExpo), Kf(38f, 1.65f), Kf(100f, 1.65f))
                        withPivotTransform(54f, 127f, sx = pRipScale) {
                            drawCircle(color = primaryColor.copy(alpha = pRipAlpha), radius = 22f, center = Offset(54f, 127f), style = Stroke(width = 2.5f))
                        }
                    }

                    // Play Button (54, 127)
                    val pScale = evalKf(t, Kf(0f, 1f, EaseSym), Kf(8f, 0.84f, cPlayPop), Kf(19f, 1.15f, EaseSym), Kf(30f, 0.96f, cCardBounce), Kf(44f, 1.04f, EaseStandard), Kf(65f, 1f), Kf(100f, 1f))
                    val pRot = evalKf(t, Kf(0f, 0f, EaseSym), Kf(8f, -6f, cPlayPop), Kf(19f, 4f, EaseSym), Kf(30f, -1f, cCardBounce), Kf(44f, 0f, EaseStandard), Kf(100f, 0f))
                    withPivotTransform(54f, 127f, sx = pScale, rotDeg = pRot) {
                        drawCircle(color = primaryColor, radius = 22f, center = Offset(54f, 127f))
                        drawPath(paths.playTriangle, color = onPrimaryColor)
                        drawPath(paths.playTriangle, color = onPrimaryColor, style = Stroke(width = 2f, join = StrokeJoin.Round))
                    }

                    // 9 Mini Waveform Bars (centered at y=127)
                    data class WaveBarSpec(
                        val cx: Float,
                        val baseH: Float,
                        val sPct: Float,
                        val p1Pct: Float, val s1: Float,
                        val p2Pct: Float, val s2: Float,
                        val p3Pct: Float, val s3: Float,
                        val ePct: Float
                    )
                    val barSpecs = listOf(
                        WaveBarSpec(90.25f, 12f, 9f, 16f, 2.1f, 25f, 0.55f, 35f, 1.65f, 58f),
                        WaveBarSpec(98.25f, 22f, 11f, 19f, 1.65f, 28f, 0.5f, 38f, 1.45f, 61f),
                        WaveBarSpec(106.25f, 16f, 13f, 22f, 2.2f, 31f, 0.6f, 41f, 1.75f, 64f),
                        WaveBarSpec(114.25f, 42f, 15f, 24f, 0.55f, 34f, 1.25f, 44f, 0.75f, 67f),
                        WaveBarSpec(122.25f, 26f, 17f, 26f, 1.65f, 36f, 0.5f, 46f, 1.4f, 70f),
                        WaveBarSpec(130.25f, 18f, 19f, 28f, 2.0f, 38f, 0.6f, 48f, 1.6f, 73f),
                        WaveBarSpec(138.25f, 32f, 21f, 30f, 0.6f, 40f, 1.38f, 50f, 0.8f, 76f),
                        WaveBarSpec(146.25f, 20f, 23f, 32f, 1.8f, 42f, 0.55f, 52f, 1.45f, 79f),
                        WaveBarSpec(154.25f, 12f, 25f, 34f, 2.2f, 44f, 0.65f, 54f, 1.7f, 82f)
                    )
                    barSpecs.forEach { spec ->
                        val sy = evalKf(
                            t,
                            Kf(0f, 1f),
                            Kf(spec.sPct, 1f, cWaveBar),
                            Kf(spec.p1Pct, spec.s1, EaseSym),
                            Kf(spec.p2Pct, spec.s2, cWaveBar),
                            Kf(spec.p3Pct, spec.s3, EaseStandard),
                            Kf(spec.ePct, 1f),
                            Kf(100f, 1f)
                        )
                        val colorMix = evalKf(
                            t,
                            Kf(0f, 0f),
                            Kf(spec.sPct, 0f),
                            Kf(spec.p1Pct, 1f),
                            Kf(spec.p2Pct, 0.55f),
                            Kf(spec.p3Pct, 1f),
                            Kf(spec.ePct, 0f),
                            Kf(100f, 0f)
                        )
                        val barColor = if (colorMix <= 0.5f) {
                            lerp(barIdleColor, barMidColor, colorMix * 2f)
                        } else {
                            lerp(barMidColor, primaryColor, (colorMix - 0.5f) * 2f)
                        }
                        withPivotTransform(spec.cx, 127f, sx = 1f, sy = sy) {
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(spec.cx - 2.25f, 127f - spec.baseH / 2f),
                                size = Size(4.5f, spec.baseH),
                                cornerRadius = CornerRadius(2.25f, 2.25f)
                            )
                        }
                    }

                    // Refresh / Re-record Icon (refreshSpin 360° around 185, 127)
                    val refScale = evalKf(t, Kf(0f, 1f), Kf(22f, 1f, EaseSmooth), Kf(28f, 0.88f, cSpin), Kf(48f, 1.16f, EaseStandard), Kf(58f, 1f), Kf(100f, 1f))
                    val refRot = evalKf(t, Kf(0f, 0f), Kf(22f, 0f, EaseSmooth), Kf(28f, -22f, cSpin), Kf(48f, 374f, EaseStandard), Kf(58f, 360f), Kf(100f, 360f))
                    withPivotTransform(185f, 127f, sx = refScale, rotDeg = refRot) {
                        drawPath(paths.refreshArc1, color = primaryColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                        drawPath(paths.refreshHead1, color = primaryColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(paths.refreshArc2, color = primaryColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                        drawPath(paths.refreshHead2, color = primaryColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }

                // 3. Flying Orb along Arc + Top Curved Guide Arrow
                val orbAlpha = evalKf(t, Kf(0f, 0f), Kf(32f, 0f), Kf(38f, 0.95f), Kf(45f, 1f), Kf(52f, 0f), Kf(100f, 0f))
                if (orbAlpha > 0.01f) {
                    val orbX = evalKf(t, Kf(0f, 156f), Kf(32f, 156f, EaseOutQuart), Kf(38f, 195f, EaseSmooth), Kf(45f, 248f, EaseSmooth), Kf(52f, 298f), Kf(100f, 298f))
                    val orbY = evalKf(t, Kf(0f, 76f), Kf(32f, 76f, EaseOutQuart), Kf(38f, 52f, EaseSmooth), Kf(45f, 50f, EaseSmooth), Kf(52f, 118f), Kf(100f, 118f))
                    val orbScale = evalKf(t, Kf(0f, 0.3f), Kf(32f, 0.3f, EaseOutQuart), Kf(38f, 1f, EaseSmooth), Kf(45f, 1.15f, EaseSmooth), Kf(52f, 0.4f), Kf(100f, 0f))
                    withPivotTransform(orbX, orbY, sx = orbScale) {
                        drawCircle(color = folderFrontColor.copy(alpha = orbAlpha), radius = 6f, center = Offset(orbX, orbY))
                        drawCircle(color = Color.White.copy(alpha = orbAlpha), radius = 3f, center = Offset(orbX, orbY))
                    }
                }

                // Curved Guide Arrow (pivot 156, 76)
                val gaTx = evalKf(t, Kf(0f, 0f), Kf(26f, 0f), Kf(33f, -4f, cSpin), Kf(47f, 5f, EaseSym), Kf(57f, -1f, EaseStandard), Kf(72f, 0f), Kf(100f, 0f))
                val gaTy = evalKf(t, Kf(0f, 0f), Kf(26f, 0f), Kf(33f, 3f, cSpin), Kf(47f, -4f, EaseSym), Kf(57f, 1f, EaseStandard), Kf(72f, 0f), Kf(100f, 0f))
                val gaSx = evalKf(t, Kf(0f, 1f), Kf(26f, 1f), Kf(33f, 0.92f, cSpin), Kf(47f, 1.06f, EaseSym), Kf(57f, 0.98f, EaseStandard), Kf(72f, 1f), Kf(100f, 1f))
                val gaSy = evalKf(t, Kf(0f, 1f), Kf(26f, 1f), Kf(33f, 0.95f, cSpin), Kf(47f, 1.08f, EaseSym), Kf(57f, 0.98f, EaseStandard), Kf(72f, 1f), Kf(100f, 1f))
                val gaRot = evalKf(t, Kf(0f, 0f), Kf(26f, 0f), Kf(33f, -4.5f, cSpin), Kf(47f, 3.2f, EaseSym), Kf(57f, -0.8f, EaseStandard), Kf(72f, 0f), Kf(100f, 0f))

                withPivotTransform(156f, 76f, tx = gaTx, ty = gaTy, sx = gaSx, sy = gaSy, rotDeg = gaRot) {
                    drawPath(paths.curvedArrow, color = primaryColor)
                    val shimAlpha = evalKf(t, Kf(0f, 0f), Kf(31f, 0f, EaseOutQuart), Kf(38f, 0.55f), Kf(54f, 0f), Kf(100f, 0f))
                    if (shimAlpha > 0.01f) {
                        val shimTx = evalKf(t, Kf(0f, 130f), Kf(31f, 130f, EaseOutQuart), Kf(54f, 325f), Kf(100f, 325f))
                        clipPath(paths.curvedArrow) {
                            withPivotTransform(0f, 65f, tx = shimTx, skewXDeg = -25f) {
                                drawRect(color = arrowShimmerColor.copy(alpha = shimAlpha), topLeft = Offset(0f, 30f), size = Size(28f, 70f))
                            }
                        }
                    }
                }

                // 4. Right 3D Archive Folder & Export Badge
                // Folder Ground Shadow (pivot 304, 198)
                val fsSx = evalKf(t, Kf(0f, 1f), Kf(34f, 1f, EaseSym), Kf(45f, 0.86f, cCardBounce), Kf(54f, 1.12f, cCardBounce), Kf(65f, 0.92f, EaseSym), Kf(90f, 1f), Kf(100f, 1f))
                val fsSy = evalKf(t, Kf(0f, 1f), Kf(34f, 1f, EaseSym), Kf(45f, 0.8f, cCardBounce), Kf(54f, 1.2f, cCardBounce), Kf(65f, 0.9f, EaseSym), Kf(90f, 1f), Kf(100f, 1f))
                val fsAlpha = evalKf(t, Kf(0f, 1f), Kf(34f, 1f, EaseSym), Kf(45f, 0.65f, cCardBounce), Kf(54f, 1f, cCardBounce), Kf(65f, 0.78f, EaseSym), Kf(90f, 1f), Kf(100f, 1f))
                withPivotTransform(304f, 198f, sx = fsSx, sy = fsSy) {
                    drawOval(
                        color = cardShadow.copy(alpha = cardShadow.alpha * fsAlpha),
                        topLeft = Offset(304f - 64f, 198f - 7f),
                        size = Size(128f, 14f)
                    )
                }

                // Folder Body Squash & Stretch (pivot 304, 198)
                val fbTy = evalKf(t, Kf(0f, 0f), Kf(34f, 0f, EaseSym), Kf(45f, -9f, cCardBounce), Kf(54f, 3f, cWaveBar), Kf(65f, -6f, EaseSym), Kf(76f, 1f, EaseStandard), Kf(90f, 0f), Kf(100f, 0f))
                val fbSx = evalKf(t, Kf(0f, 1f), Kf(34f, 1f, EaseSym), Kf(45f, 0.96f, cCardBounce), Kf(54f, 1.07f, cWaveBar), Kf(65f, 0.97f, EaseSym), Kf(76f, 1.01f, EaseStandard), Kf(90f, 1f), Kf(100f, 1f))
                val fbSy = evalKf(t, Kf(0f, 1f), Kf(34f, 1f, EaseSym), Kf(45f, 1.05f, cCardBounce), Kf(54f, 0.91f, cWaveBar), Kf(65f, 1.04f, EaseSym), Kf(76f, 0.99f, EaseStandard), Kf(90f, 1f), Kf(100f, 1f))
                val fbRot = evalKf(t, Kf(0f, 0f), Kf(34f, 0f, EaseSym), Kf(45f, -2.2f, cCardBounce), Kf(54f, 1.8f, cWaveBar), Kf(65f, -1.0f, EaseSym), Kf(76f, 0.3f, EaseStandard), Kf(90f, 0f), Kf(100f, 0f))

                withPivotTransform(304f, 198f, ty = fbTy, sx = fbSx, sy = fbSy, rotDeg = fbRot) {
                    // Folder Back Plate
                    drawPath(paths.folderBack, color = folderBackColor)

                    // Inner White Paper (paperSlideIn around 303, 144)
                    val pTx = evalKf(t, Kf(0f, 0f), Kf(34f, 0f, EaseSym), Kf(46f, -2f, cWaveBar), Kf(55f, 1f, cCardBounce), Kf(66f, 0f, EaseStandard), Kf(85f, 0f), Kf(100f, 0f))
                    val pTy = evalKf(t, Kf(0f, 0f), Kf(34f, 0f, EaseSym), Kf(46f, -11f, cWaveBar), Kf(55f, 4f, cCardBounce), Kf(66f, -4f, EaseStandard), Kf(85f, 0f), Kf(100f, 0f))
                    val pRot = evalKf(t, Kf(0f, 0f), Kf(34f, 0f, EaseSym), Kf(46f, -2f, cWaveBar), Kf(55f, 1f, cCardBounce), Kf(66f, -0.6f, EaseStandard), Kf(85f, 0f), Kf(100f, 0f))
                    withPivotTransform(303f, 144f, tx = pTx, ty = pTy, rotDeg = pRot) {
                        drawRoundRect(
                            color = cardColor,
                            topLeft = Offset(252f, 112f),
                            size = Size(102f, 64f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }

                    // Folder Left Edge Crease
                    drawPath(paths.folderCrease, color = folderCreaseColor)

                    // Folder Tilted Front Flap (flapOpenClose with 3D skewX & scaleY around 304, 198)
                    val flapSkewX = evalKf(t, Kf(0f, 0f), Kf(34f, 0f, EaseSym), Kf(46f, -6.5f, cCardBounce), Kf(55f, 2.2f, cCardBounce), Kf(67f, -1.5f, EaseStandard), Kf(85f, 0f), Kf(100f, 0f))
                    val flapSy = evalKf(t, Kf(0f, 1f), Kf(34f, 1f, EaseSym), Kf(46f, 0.92f, cCardBounce), Kf(55f, 1.03f, cCardBounce), Kf(67f, 0.98f, EaseStandard), Kf(85f, 1f), Kf(100f, 1f))
                    withPivotTransform(304f, 198f, sx = 1f, sy = flapSy, skewXDeg = flapSkewX) {
                        drawPath(paths.folderFrontFlap, color = folderFrontColor)
                    }

                    // Top-Right Export Badge (exportBadgePop around 348, 108)
                    val ebScale = evalKf(t, Kf(0f, 1f), Kf(46f, 1f, EaseSym), Kf(53f, 0.82f, cPlayPop), Kf(64f, 1.24f, EaseSym), Kf(75f, 0.94f, cCardBounce), Kf(86f, 1.04f, EaseStandard), Kf(96f, 1f), Kf(100f, 1f))
                    val ebRot = evalKf(t, Kf(0f, 0f), Kf(46f, 0f, EaseSym), Kf(53f, -8f, cPlayPop), Kf(64f, 6f, EaseSym), Kf(75f, -2f, cCardBounce), Kf(86f, 0.5f, EaseStandard), Kf(96f, 0f), Kf(100f, 0f))
                    val eaShoot = evalKf(t, Kf(0f, 0f), Kf(52f, 0f, cPlayPop), Kf(64f, 3.5f, EaseSym), Kf(76f, -1f, EaseStandard), Kf(94f, 0f), Kf(100f, 0f))

                    withPivotTransform(348f, 108f, sx = ebScale, rotDeg = ebRot) {
                        drawCircle(color = exportBadgeColor, radius = 22f, center = Offset(348f, 108f))
                        withTransform({ translate(eaShoot, -eaShoot) }) {
                            drawPath(paths.exportArrow, color = onPrimaryColor, style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }

                // 5. Top-Right Spark Rays (sparkRay1 around 358, 72; sparkRay2 around 368, 80)
                val sp1Tx = evalKf(t, Kf(0f, 0f), Kf(48f, 0f, EaseSym), Kf(55f, -2f, cPlayPop), Kf(66f, 3.5f, EaseSym), Kf(79f, -0.8f, cCardBounce), Kf(90f, 0f), Kf(100f, 0f))
                val sp1Ty = evalKf(t, Kf(0f, 0f), Kf(48f, 0f, EaseSym), Kf(55f, 6f, cPlayPop), Kf(66f, -11f, EaseSym), Kf(79f, 2.5f, cCardBounce), Kf(90f, 0f), Kf(100f, 0f))
                val sp1Scale = evalKf(t, Kf(0f, 1f), Kf(48f, 1f, EaseSym), Kf(55f, 0.65f, cPlayPop), Kf(66f, 1.28f, EaseSym), Kf(79f, 0.92f, cCardBounce), Kf(90f, 1f), Kf(100f, 1f))
                withPivotTransform(358f, 72f, tx = sp1Tx, ty = sp1Ty, sx = sp1Scale) {
                    drawPath(paths.accent1, color = sparkColor, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                }

                val sp2Tx = evalKf(t, Kf(0f, 0f), Kf(51f, 0f, EaseSym), Kf(58f, -5f, cPlayPop), Kf(69f, 9f, EaseSym), Kf(82f, -2f, cCardBounce), Kf(92f, 0f), Kf(100f, 0f))
                val sp2Ty = evalKf(t, Kf(0f, 0f), Kf(51f, 0f, EaseSym), Kf(58f, 3.8f, cPlayPop), Kf(69f, -6.8f, EaseSym), Kf(82f, 1.5f, cCardBounce), Kf(92f, 0f), Kf(100f, 0f))
                val sp2Scale = evalKf(t, Kf(0f, 1f), Kf(51f, 1f, EaseSym), Kf(58f, 0.65f, cPlayPop), Kf(69f, 1.28f, EaseSym), Kf(82f, 0.92f, cCardBounce), Kf(92f, 1f), Kf(100f, 1f))
                withPivotTransform(368f, 80f, tx = sp2Tx, ty = sp2Ty, sx = sp2Scale) {
                    drawPath(paths.accent2, color = sparkColor, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
