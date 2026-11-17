package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.i18n.S
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.rememberStudioInteraction

@Composable
fun ModeSelectionOnboardingDialog(
    onSelectMode: (isSimpleMode: Boolean) -> Unit
) {
    StudioModalDialog(
        onDismissRequest = { /* Mandatory initial mode choice */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val isSideBySide = maxWidth >= 680.dp && maxHeight >= 360.dp
            val isShortViewport = maxHeight < 520.dp

            com.example.ui.components.StudioDialogEnterAnimation {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .widthIn(max = if (isSideBySide) 820.dp else 460.dp)
                        .fillMaxWidth()
                        .testTag("mode_selection_onboarding_dialog")
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = if (isSideBySide) 28.dp else 20.dp,
                            vertical = if (isShortViewport) 16.dp else 24.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PhonAcqLogo(modifier = Modifier.size(if (isShortViewport) 26.dp else 32.dp))
                        Text(
                            text = S.onboarding.welcomeTitle,
                            style = if (isShortViewport) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = S.onboarding.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(if (isShortViewport) 14.dp else 20.dp))

                    if (isSideBySide) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ModeOptionCard(
                                title = S.onboarding.simpleTitle,
                                badgeText = S.onboarding.simpleBadge,
                                description = S.onboarding.simpleDescription,
                                highlights = listOf(
                                    S.onboarding.simpleBullet1,
                                    S.onboarding.simpleBullet2,
                                    S.onboarding.simpleBullet3
                                ),
                                icon = Icons.Default.AutoAwesome,
                                buttonLabel = S.onboarding.enterSimpleButton,
                                isRecommended = true,
                                compact = isShortViewport,
                                testTag = "mode_select_simple_card",
                                buttonTestTag = "mode_select_simple_button",
                                onClick = { onSelectMode(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            ModeOptionCard(
                                title = S.onboarding.advancedTitle,
                                badgeText = S.onboarding.advancedBadge,
                                description = S.onboarding.advancedDescription,
                                highlights = listOf(
                                    S.onboarding.advancedBullet1,
                                    S.onboarding.advancedBullet2,
                                    S.onboarding.advancedBullet3
                                ),
                                icon = Icons.Default.Analytics,
                                buttonLabel = S.onboarding.enterAdvancedButton,
                                isRecommended = false,
                                compact = isShortViewport,
                                testTag = "mode_select_advanced_card",
                                buttonTestTag = "mode_select_advanced_button",
                                onClick = { onSelectMode(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ModeOptionCard(
                                title = S.onboarding.simpleTitle,
                                badgeText = S.onboarding.simpleBadge,
                                description = S.onboarding.simpleDescription,
                                highlights = listOf(
                                    S.onboarding.simpleBullet1,
                                    S.onboarding.simpleBullet2Compact
                                ),
                                icon = Icons.Default.AutoAwesome,
                                buttonLabel = S.onboarding.enterSimpleButton,
                                isRecommended = true,
                                compact = isShortViewport,
                                testTag = "mode_select_simple_card",
                                buttonTestTag = "mode_select_simple_button",
                                onClick = { onSelectMode(true) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            ModeOptionCard(
                                title = S.onboarding.advancedTitle,
                                badgeText = null,
                                description = S.onboarding.advancedDescription,
                                highlights = listOf(
                                    S.onboarding.advancedBullet1Compact,
                                    S.onboarding.advancedBullet2Compact
                                ),
                                icon = Icons.Default.GraphicEq,
                                buttonLabel = S.onboarding.enterAdvancedButton,
                                isRecommended = false,
                                compact = isShortViewport,
                                testTag = "mode_select_advanced_card",
                                buttonTestTag = "mode_select_advanced_button",
                                onClick = { onSelectMode(false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isShortViewport) 12.dp else 18.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = S.onboarding.switchModeFooterNote,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    badgeText: String?,
    description: String,
    highlights: List<String>,
    icon: ImageVector,
    buttonLabel: String,
    isRecommended: Boolean,
    compact: Boolean,
    testTag: String,
    buttonTestTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState

    val baseBg = if (isRecommended) {
        lerp(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.primaryContainer, 0.22f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val containerColor = if (isHovered) {
        lerp(baseBg, MaterialTheme.colorScheme.primaryContainer, 0.2f)
    } else {
        baseBg
    }
    val borderColor = when {
        isRecommended -> MaterialTheme.colorScheme.primary
        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isRecommended) 1.8.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .hoverable(hoverSrc)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = hoverSrc,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isRecommended) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isRecommended) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!badgeText.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = if (isRecommended) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecommended) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    highlights.forEach { line ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isRecommended) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(15.dp)
                            )
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))

            if (isRecommended) {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(buttonTestTag)
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(buttonTestTag)
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
