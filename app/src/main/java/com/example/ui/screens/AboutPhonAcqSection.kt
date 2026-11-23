package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.i18n.withArgs
import com.example.ui.components.AdaptiveModalSurface
import com.example.ui.components.StudioDialogEnterAnimation
import com.example.ui.components.StudioModalDialog
import com.example.utils.AppUpdateController
import com.example.utils.AppUpdateManager
import com.example.utils.AppUpdatePlatformBridge
import com.example.utils.AppUpdateUiState

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
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        StudioDialogEnterAnimation {
            AdaptiveModalSurface(
                targetMaxWidth = 580.dp,
                maxHeightFraction = 0.88f,
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("about_phonacq_dialog")
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

@Composable
fun AboutPhonAcqContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    val updateState by AppUpdateController.state.collectAsState()

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

                AboutUpdatePanel(
                    state = updateState,
                    onCheckUpdates = { AppUpdateController.checkForUpdates(context) },
                    onStartDownload = { AppUpdateController.startDownload(context) },
                    onCancelDownload = { AppUpdateController.cancelDownload() },
                    onInstallDownloaded = {
                        val res = AppUpdateController.installDownloaded(context)
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    },
                    onOpenFolder = {
                        val opened = AppUpdateController.openDownloadFolder(context)
                        if (!opened) {
                            Toast.makeText(context, "无法打开下载目录", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenReleasePage = { url ->
                        openOrCopyUrl(url, S.about.updateReleasesPageLabel)
                    }
                )
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

@Composable
private fun AboutUpdatePanel(
    state: AppUpdateUiState,
    onCheckUpdates: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallDownloaded: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenReleasePage: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentVer = AppUpdateManager.normalizeVersion(BuildConfig.VERSION_NAME)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(
            1.dp,
            when (state) {
                is AppUpdateUiState.UpdateAvailable,
                is AppUpdateUiState.ReadyToInstall -> colorScheme.primary.copy(alpha = 0.45f)
                is AppUpdateUiState.Error -> colorScheme.error.copy(alpha = 0.40f)
                else -> colorScheme.outlineVariant.copy(alpha = 0.55f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_update_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                is AppUpdateUiState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = S.about.updateSectionTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = S.about.updateCurrentVersionLabel.withArgs("version" to currentVer),
                                fontSize = 11.5.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onCheckUpdates,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 34.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("about_check_update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = S.about.updateCheckButton,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                is AppUpdateUiState.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = S.about.updateCheckingHint,
                            fontSize = 12.5.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                is AppUpdateUiState.UpToDate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = S.about.updateUpToDateHint,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "v${state.currentVersion}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = onCheckUpdates,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("about_recheck_update_button")
                        ) {
                            Text(
                                text = S.about.updateRecheckButton,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                is AppUpdateUiState.UpdateAvailable -> {
                    val release = state.release
                    val asset = release.asset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = S.about.updateFoundTitle.withArgs("version" to release.versionName),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val dateLabel = release.publishedAt.take(10)
                        if (dateLabel.length == 10 && dateLabel[4] == '-') {
                            Text(
                                text = dateLabel,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (asset != null) {
                        val sizePart = if (asset.sizeBytes > 0L) {
                            " · ${AppUpdateManager.formatBytes(asset.sizeBytes)}"
                        } else {
                            ""
                        }
                        Text(
                            text = "${asset.name}$sizePart",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (release.releaseNotes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.8.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = S.about.updateNotesTitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = release.releaseNotes,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.5.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (asset != null) {
                            Button(
                                onClick = onStartDownload,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("about_download_update_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = S.about.updateDownloadButton,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            OutlinedButton(
                                onClick = { onOpenReleasePage(release.htmlUrl) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = S.about.updateOpenReleasePage,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            Button(
                                onClick = { onOpenReleasePage(release.htmlUrl) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = S.about.updateOpenReleasePage,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                is AppUpdateUiState.Downloading -> {
                    val hasTotal = state.totalBytes > 0L
                    val progress = if (hasTotal) {
                        (state.downloadedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        -1f
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = S.about.updateDownloadingTitle.withArgs("name" to state.asset.name),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onCancelDownload,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 28.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("about_cancel_download_button")
                        ) {
                            Text(
                                text = S.about.updateCancelButton,
                                fontSize = 11.5.sp,
                                color = colorScheme.error,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (hasTotal) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sizeText = if (hasTotal) {
                            val pct = (progress * 100f).toInt().coerceIn(0, 100)
                            "${AppUpdateManager.formatBytes(state.downloadedBytes)} / ${AppUpdateManager.formatBytes(state.totalBytes)} ($pct%)"
                        } else {
                            AppUpdateManager.formatBytes(state.downloadedBytes)
                        }
                        Text(
                            text = sizeText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurfaceVariant
                        )
                        val speedText = AppUpdateManager.formatSpeed(state.speedBytesPerSec)
                        if (speedText.isNotEmpty()) {
                            Text(
                                text = speedText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.primary
                            )
                        }
                    }
                }

                is AppUpdateUiState.Verifying -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = S.about.updateVerifyingHint,
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                is AppUpdateUiState.ReadyToInstall -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = S.about.updateReadyTitle.withArgs("version" to state.release.versionName),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (state.sha256Verified) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = S.about.updateShaVerifiedBadge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = state.downloadedFile.name,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onInstallDownloaded,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("about_install_update_button")
                        ) {
                            Text(
                                text = S.about.updateInstallButton,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (AppUpdatePlatformBridge.supportsRevealFolder()) {
                            OutlinedButton(
                                onClick = onOpenFolder,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = S.about.updateOpenFolderButton,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                is AppUpdateUiState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(17.dp)
                        )
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (state.pendingRelease != null) {
                                    onStartDownload()
                                } else {
                                    onCheckUpdates()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 34.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("about_retry_update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = S.about.updateRetryButton,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        OutlinedButton(
                            onClick = { onOpenReleasePage(state.releaseUrl) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 34.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = S.about.updateOpenReleasePage,
                                fontSize = 12.sp,
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
