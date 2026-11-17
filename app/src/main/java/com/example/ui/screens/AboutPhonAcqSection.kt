package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.i18n.S
import com.example.ui.components.StudioModalDialog
import com.example.i18n.withArgs
import com.example.ui.components.StudioDialogEnterAnimation

private const val URL_AUTHOR_GITHUB = "https://github.com/KasumiKitsune"
private const val URL_REPO_PHONACQ = "https://github.com/KasumiKitsune/PhonAcq"
private const val URL_REPO_PHONTRACER = "https://github.com/KasumiKitsune/PhonTracer"
private const val URL_REPO_PRAAT_ZH = "https://github.com/KasumiKitsune/praat-simplified-chinese"

@Composable
fun AboutCategoryContent() {
    AboutPhonAcqContent()
}

@Composable
fun AboutPhonAcqDialog(
    onDismiss: () -> Unit
) {
    StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.example.ui.components.StudioDialogEnterAnimation(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .testTag("about_phonacq_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PhonAcqLogo(modifier = Modifier.size(30.dp))
                            Column {
                                Text(
                                    text = S.about.title,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME.removePrefix("v")}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("about_dialog_close_icon")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = S.common.close,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        AboutPhonAcqContent()
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("about_dialog_confirm_button")
                        ) {
                            Text(S.common.close, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutPhonAcqContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme

    fun openOrCopyUrl(url: String, label: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
            clipboardManager.setText(AnnotatedString(url))
            Toast.makeText(
                context,
                S.about.copiedLinkWithUrlToast.withArgs("label" to label, "url" to url),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun copyUrlToClipboard(url: String, label: String) {
        clipboardManager.setText(AnnotatedString(url))
        Toast.makeText(
            context,
            S.about.copiedLinkToast.withArgs("label" to label),
            Toast.LENGTH_SHORT
        ).show()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Identity & Positioning Overview Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.20f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surface,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PhonAcqLogo(modifier = Modifier.size(32.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = S.common.appName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME.removePrefix("v")}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = S.about.heroSubtitle,
                            fontSize = 11.5.sp,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = S.about.heroDescription,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        S.about.tagLosslessWav,
                        S.about.tagDualMode,
                        S.about.tagPictureCards,
                        S.about.tagWirelessLink,
                        "Praat & PhonTracer"
                    ).forEach { badge ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.surface.copy(alpha = 0.85f),
                            border = BorderStroke(0.8.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = badge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Developer & Repository Section
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = S.about.sectionDevRepos,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )

                AboutLinkItemCard(
                    icon = Icons.Default.Person,
                    title = S.about.devCardTitle,
                    urlDisplay = "github.com/KasumiKitsune",
                    description = S.about.devCardDesc,
                    onOpen = { openOrCopyUrl(URL_AUTHOR_GITHUB, S.about.devPageLabel) },
                    onCopy = { copyUrlToClipboard(URL_AUTHOR_GITHUB, S.about.devPageLabel) },
                    testTag = "about_link_author_github"
                )

                AboutLinkItemCard(
                    icon = Icons.Default.Code,
                    title = S.about.repoCardTitle,
                    urlDisplay = "github.com/KasumiKitsune/PhonAcq",
                    description = S.about.repoCardDesc,
                    onOpen = { openOrCopyUrl(URL_REPO_PHONACQ, S.about.repoPageLabel) },
                    onCopy = { copyUrlToClipboard(URL_REPO_PHONACQ, S.about.repoPageLabel) },
                    testTag = "about_link_repo_phonacq"
                )
            }
        }

        // 3. Companion Open-Source Phonetics Ecosystem
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = S.about.sectionCompanionTools,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                    Text(
                        text = S.about.companionToolsIntro,
                        fontSize = 12.sp,
                        lineHeight = 17.5.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                // Workflow pill banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(0.8.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = S.about.workflowDiagramLine,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                AboutLinkItemCard(
                    icon = Icons.Default.AccountTree,
                    title = S.about.phonTracerTitle,
                    roleBadge = S.about.phonTracerBadge,
                    urlDisplay = "github.com/KasumiKitsune/PhonTracer",
                    description = S.about.phonTracerDesc,
                    onOpen = { openOrCopyUrl(URL_REPO_PHONTRACER, S.about.phonTracerPageLabel) },
                    onCopy = { copyUrlToClipboard(URL_REPO_PHONTRACER, S.about.phonTracerPageLabel) },
                    testTag = "about_link_repo_phontracer"
                )

                AboutLinkItemCard(
                    icon = Icons.Default.Translate,
                    title = S.about.praatZhTitle,
                    roleBadge = S.about.praatZhBadge,
                    urlDisplay = "github.com/KasumiKitsune/praat-simplified-chinese",
                    description = S.about.praatZhDesc,
                    onOpen = { openOrCopyUrl(URL_REPO_PRAAT_ZH, S.about.praatZhPageLabel) },
                    onCopy = { copyUrlToClipboard(URL_REPO_PRAAT_ZH, S.about.praatZhPageLabel) },
                    testTag = "about_link_repo_praat_zh"
                )
            }
        }

        // 4. Local Offline & Data Privacy
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .background(colorScheme.secondaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = S.about.privacyTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = S.about.privacyBody,
                        fontSize = 12.sp,
                        lineHeight = 17.5.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLinkItemCard(
    icon: ImageVector,
    title: String,
    urlDisplay: String,
    description: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    testTag: String,
    roleBadge: String? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onOpen)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!roleBadge.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = roleBadge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = urlDisplay,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = S.about.copyLinkDesc,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpen,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = S.about.openInBrowserDesc,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 17.5.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
