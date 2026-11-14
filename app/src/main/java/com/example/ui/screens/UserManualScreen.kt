package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction

internal data class ManualChapterMeta(
    val id: Int,
    val number: String,
    val title: String,
    val subtitle: String,
    val groupLabel: String,
    val icon: ImageVector
)

internal enum class BadgeTone {
    Primary,
    Secondary,
    Tertiary,
    Error,
    Neutral
}

internal val CenteredBadgeLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

private fun buildManualChapters(
    isSimpleMode: Boolean,
    isDesktop: Boolean
): List<ManualChapterMeta> {
    return if (isSimpleMode) {
        listOf(
            ManualChapterMeta(
                id = 101,
                number = "一",
                title = "三步开始录音",
                subtitle = "选用字表 → 登记发音人 → 逐词录制与导出",
                groupLabel = "快速开始",
                icon = Icons.Default.Flag
            ),
            ManualChapterMeta(
                id = 102,
                number = "二",
                title = "导入字表、粘贴建表与词条配图",
                subtitle = "支持 Excel / Word / TXT 导入、文本拆分与图片词卡",
                groupLabel = "快速开始",
                icon = Icons.Default.EditNote
            ),
            ManualChapterMeta(
                id = 103,
                number = "三",
                title = "清晰收音与安静操控",
                subtitle = if (isDesktop) {
                    "试音测噪、声卡选择、大字模式、键盘控制与无线互联"
                } else {
                    "试音测噪、避开蓝牙麦、大字模式、音量键控制与无线互联"
                },
                groupLabel = "录音操作",
                icon = Icons.Default.Mic
            ),
            ManualChapterMeta(
                id = 104,
                number = "四",
                title = "回放检查与打包导出",
                subtitle = if (isDesktop) {
                    "连续试听筛查、重录补齐，以及导出音频压缩包或 Praat 工程"
                } else {
                    "连续试听筛查、重录补齐，以及一键分享或另存到本地"
                },
                groupLabel = "检查与导出",
                icon = Icons.Default.FolderZip
            ),
            ManualChapterMeta(
                id = 105,
                number = "五",
                title = "常用设置与进阶功能",
                subtitle = "调节看读字号、选择标注模板、清理缓存与切换进阶模式",
                groupLabel = "检查与导出",
                icon = Icons.Default.Tune
            )
        )
    } else {
        listOf(
            ManualChapterMeta(
                id = 1,
                number = "一",
                title = "界面导览与采录流程",
                subtitle = if (isDesktop) {
                    "左侧管理字表与发音人，顶部选择声卡，右侧采录与声学检视"
                } else {
                    "底部切换字表、录音台与录音库，侧栏试音测噪与修改设置"
                },
                groupLabel = "准备与建表",
                icon = Icons.Default.Flag
            ),
            ManualChapterMeta(
                id = 2,
                number = "二",
                title = "多人共用字表与音域校准",
                subtitle = "一份字表多人独立采录，自动适配个人音域计算五度调值",
                groupLabel = "准备与建表",
                icon = Icons.Default.Groups
            ),
            ManualChapterMeta(
                id = 3,
                number = "三",
                title = "字表导入、配图词卡与多音节切分",
                subtitle = "支持表格/文本导入、词条配图、中途改表保音与多音节自动切分",
                groupLabel = "准备与建表",
                icon = Icons.Default.EditNote
            ),
            ManualChapterMeta(
                id = 4,
                number = "四",
                title = "录音方式、防诱导提示与无线互联",
                subtitle = if (isDesktop) {
                    "四种录音操控、键盘快捷键、大字模式、载体句与双设备无线互联"
                } else {
                    "四种录音操控、侧面音量键、大字模式、载体句与双设备无线互联"
                },
                groupLabel = "现场录音与记音",
                icon = Icons.Default.Mic
            ),
            ManualChapterMeta(
                id = 5,
                number = "五",
                title = "国际音标速记与多遍重录择优",
                subtitle = "四栏音标面板按人独立记音，保留历史遍数试听切换最佳版本",
                groupLabel = "现场录音与记音",
                icon = Icons.Default.History
            ),
            ManualChapterMeta(
                id = 6,
                number = "六",
                title = "单字声学图谱与全表格局分析",
                subtitle = "查看单字语图与调值曲线，生成全表声调聚类、元音轨迹与多人对比图",
                groupLabel = "看图分析与导出",
                icon = Icons.Default.Insights
            ),
            ManualChapterMeta(
                id = 7,
                number = "七",
                title = "工程导出与 TextGrid 标注配置",
                subtitle = "导出压缩包、Praat (.Collection) 或 PhonTracer (.teproj) 工程",
                groupLabel = "看图分析与导出",
                icon = Icons.Default.FolderZip
            ),
            ManualChapterMeta(
                id = 8,
                number = "八",
                title = "防误触保护、空间清理与离线运行",
                subtitle = if (isDesktop) {
                    "重录前确认、打开本地语料目录、按发音人清理与纯离线运行"
                } else {
                    "重录前确认、按发音人清理录音、清理缓存与纯离线运行"
                },
                groupLabel = "看图分析与导出",
                icon = Icons.AutoMirrored.Filled.HelpOutline
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManualScreen(
    onBack: () -> Unit,
    isSimpleMode: Boolean = false,
    onOpenEncyclopedia: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val manualBackState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = true,
        state = manualBackState
    ) {
        onBack()
    }

    // Silently match the current runtime platform & UI mode without showing any mode/platform switchers
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform

    val chapters = remember(isSimpleMode, isDesktop) {
        buildManualChapters(isSimpleMode = isSimpleMode, isDesktop = isDesktop)
    }

    val expandedChapterIds = remember(isSimpleMode) {
        mutableStateMapOf<Int, Boolean>().apply {
            put(chapters.first().id, true)
        }
    }

    var selectedDesktopChapterId by rememberSaveable(isSimpleMode) {
        mutableIntStateOf(chapters.first().id)
    }

    val activeDesktopChapter = remember(chapters, selectedDesktopChapterId) {
        chapters.firstOrNull { it.id == selectedDesktopChapterId } ?: chapters.first()
    }

    val allExpanded = chapters.all { expandedChapterIds[it.id] == true }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .predictiveBackLayer(manualBackState)
            .testTag("user_manual_screen")
    ) {
        val isTwoPaneLayout = maxWidth >= 700.dp || (isDesktop && maxWidth >= 560.dp)
        val sidebarWidth = if (maxWidth >= 940.dp) 276.dp else 242.dp

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "使用说明",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "采录流程、操控要点与导出指南",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("user_manual_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        actions = {
                            if (onOpenEncyclopedia != null) {
                                OutlinedButton(
                                    onClick = onOpenEncyclopedia,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .padding(end = if (isTwoPaneLayout) 12.dp else 6.dp)
                                        .height(34.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .testTag("manual_open_encyclopedia_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "语音学百科",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            if (!isTwoPaneLayout) {
                                FilledTonalButton(
                                    onClick = {
                                        val targetState = !allExpanded
                                        chapters.forEach { ch ->
                                            expandedChapterIds[ch.id] = targetState
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .height(34.dp)
                                        .testTag("toggle_all_chapters_button")
                                ) {
                                    Icon(
                                        imageVector = if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (allExpanded) "全部收起" else "全部展开",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            if (isTwoPaneLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val sidebarScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight()
                            .verticalScroll(sidebarScroll)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("manual_desktop_sidebar"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        var lastGroup = ""
                        chapters.forEach { chapter ->
                            if (chapter.groupLabel != lastGroup) {
                                if (lastGroup.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Text(
                                    text = chapter.groupLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                                lastGroup = chapter.groupLabel
                            }

                            ManualSidebarItem(
                                chapter = chapter,
                                selected = activeDesktopChapter.id == chapter.id,
                                onClick = { selectedDesktopChapterId = chapter.id }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("manual_desktop_reader_pane"),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        AnimatedContent(
                            targetState = activeDesktopChapter.id,
                            transitionSpec = {
                                val initialIdx = chapters.indexOfFirst { it.id == initialState }
                                val targetIdx = chapters.indexOfFirst { it.id == targetState }
                                val forward = targetIdx >= initialIdx
                                (androidx.compose.animation.slideInVertically(tween(210)) { fullH ->
                                    if (forward) fullH / 18 else -fullH / 18
                                } + fadeIn(tween(190))) togetherWith
                                    (androidx.compose.animation.slideOutVertically(tween(150)) { fullH ->
                                        if (forward) -fullH / 22 else fullH / 22
                                    } + fadeOut(tween(130)))
                            },
                            label = "ManualChapterReaderTransition"
                        ) { chapterId ->
                            val chapterMeta = chapters.firstOrNull { it.id == chapterId } ?: chapters.first()
                            val detailScroll = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(detailScroll)
                                    .padding(horizontal = 24.dp, vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 780.dp)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    ManualReaderChapterHeaderCard(chapter = chapterMeta)

                                    RenderManualChapterBody(
                                        chapterId = chapterMeta.id,
                                        isDesktop = isDesktop
                                    )

                                    val curIdx = chapters.indexOfFirst { it.id == chapterMeta.id }
                                    if (chapters.size > 1 && curIdx >= 0) {
                                        ManualChapterPrevNextBar(
                                            prevChapter = chapters.getOrNull(curIdx - 1),
                                            nextChapter = chapters.getOrNull(curIdx + 1),
                                            onSelectChapter = { selectedDesktopChapterId = it.id }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                val listState = key(isSimpleMode) { rememberLazyListState() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        chapters.forEach { chapter ->
                            item(key = "chapter_${chapter.id}") {
                                val isExpanded = expandedChapterIds[chapter.id] == true
                                ExpandableManualChapterCard(
                                    chapter = chapter,
                                    expanded = isExpanded,
                                    onToggle = {
                                        expandedChapterIds[chapter.id] = !isExpanded
                                    }
                                ) {
                                    RenderManualChapterBody(
                                        chapterId = chapter.id,
                                        isDesktop = isDesktop
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualSidebarItem(
    chapter: ManualChapterMeta,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val bgColor = when {
        selected -> cs.secondaryContainer.copy(alpha = 0.85f)
        isHovered -> cs.surfaceContainerHigh.copy(alpha = 0.65f)
        else -> cs.background
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = if (selected) BorderStroke(1.dp, cs.primary.copy(alpha = 0.35f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .hoverable(hoverSrc)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = hoverSrc, indication = null, onClick = onClick)
            .testTag("toc_item_${chapter.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) cs.primary else cs.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = chapter.icon,
                        contentDescription = null,
                        tint = if (selected) cs.onPrimary else cs.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "${chapter.number}、${chapter.title}",
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (selected) cs.onSecondaryContainer else cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = chapter.subtitle,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ManualReaderChapterHeaderCard(
    chapter: ManualChapterMeta
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cs.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = chapter.icon,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${chapter.number}、${chapter.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onSurface
                )
                Text(
                    text = chapter.subtitle,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ManualChapterPrevNextBar(
    prevChapter: ManualChapterMeta?,
    nextChapter: ManualChapterMeta?,
    onSelectChapter: (ManualChapterMeta) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prevChapter != null) {
            OutlinedButton(
                onClick = { onSelectChapter(prevChapter) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text("← 上一章：${prevChapter.title}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        if (nextChapter != null) {
            FilledTonalButton(
                onClick = { onSelectChapter(nextChapter) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text("下一章：${nextChapter.title} →", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExpandableManualChapterCard(
    chapter: ManualChapterMeta,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val (headerHoverSrc, isHeaderHoveredState) = rememberStudioInteraction()
    val isHeaderHovered by isHeaderHoveredState

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "ChevronRotation"
    )
    val containerColor = when {
        expanded -> MaterialTheme.colorScheme.surfaceContainerLow
        isHeaderHovered -> lerp(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.primaryContainer, 0.28f)
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val borderColor = when {
        expanded -> MaterialTheme.colorScheme.primary
        isHeaderHovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (expanded || isHeaderHovered) 1.4.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("toc_item_${chapter.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hoverable(headerHoverSrc)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = headerHoverSrc,
                        indication = null,
                        onClick = onToggle
                    )
                    .padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (expanded) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = chapter.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${chapter.number}、${chapter.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = chapter.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (expanded) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起章节" else "展开章节",
                            tint = if (expanded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(durationMillis = 180)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(durationMillis = 140))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 15.dp, end = 15.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    content()
                }
            }
        }
    }
}

// =========================================================================
// Shared Visual Hierarchy Components (Used by UserManualChapters.kt)
// =========================================================================

@Composable
internal fun ManualKeySummaryBanner(
    icon: ImageVector = Icons.Default.Lightbulb,
    title: String,
    summary: String
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.primaryContainer.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(17.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.primary
                )
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    lineHeight = 17.5.sp,
                    color = cs.onSurface.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
internal fun ExpandableDeepDiveNote(
    badgeLabel: String = "参考示例",
    title: String,
    points: List<String>
) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val chevronRot by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(180),
        label = "DeepDiveRot"
    )

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = cs.surfaceContainerLowest,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .clickable { open = !open }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    UiButtonPill(icon = Icons.Default.AddCircleOutline, label = badgeLabel, tone = BadgeTone.Neutral)
                    Text(
                        text = title,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier
                        .size(17.dp)
                        .rotate(chevronRot)
                )
            }

            AnimatedVisibility(visible = open) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                    points.forEach { pt ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(4.5.dp)
                                    .clip(CircleShape)
                                    .background(cs.primary.copy(alpha = 0.75f))
                            )
                            RichManualText(text = pt, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecordingModeMiniCard(
    icon: ImageVector,
    title: String,
    tag: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tag,
                        style = TextStyle(
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeightStyle = CenteredBadgeLineHeightStyle
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = desc,
                fontSize = 11.5.sp,
                lineHeight = 16.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ManualFeatureRow(
    text: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            )
            RichManualText(
                text = text,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ExportFileTypeRow(
    extBadge: String,
    title: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(78.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
                    .border(
                        width = 0.7.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(7.dp)
                    )
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = extBadge,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeightStyle = CenteredBadgeLineHeightStyle
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    lineHeight = 17.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ManualStepCard(
    stepNumber: String,
    title: String,
    points: List<String>
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber,
                            style = TextStyle(
                                fontSize = 12.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeightStyle = CenteredBadgeLineHeightStyle
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                points.forEach { pt ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 9.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                        )
                        RichManualText(
                            text = pt,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ManualQaCard(
    icon: ImageVector,
    question: String,
    summaryNote: String,
    visualPreview: (@Composable () -> Unit)? = null,
    points: List<String>
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = question,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summaryNote,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                }
            }

            if (visualPreview != null) {
                visualPreview()
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                points.forEach { pt ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 9.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                        )
                        RichManualText(
                            text = pt,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UiButtonPill(
    icon: ImageVector?,
    label: String,
    tone: BadgeTone = BadgeTone.Primary
) {
    val (bgColor, contentColor, borderColor) = when (tone) {
        BadgeTone.Primary -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        BadgeTone.Secondary -> Triple(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        BadgeTone.Tertiary -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        BadgeTone.Error -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        )
        BadgeTone.Neutral -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }

    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(0.7.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeightStyle = CenteredBadgeLineHeightStyle
            ),
            color = contentColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun RichManualText(
    text: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val tokenRegex = remember {
        Regex("""\[\[([a-z_]+)\|([^\]]+)]]|\*\*([^*]+)\*\*|`([^`]+)`""")
    }

    val chipTextStyle = remember {
        TextStyle(
            fontSize = 11.5.sp,
            lineHeight = 11.5.sp,
            fontWeight = FontWeight.Medium,
            lineHeightStyle = CenteredBadgeLineHeightStyle
        )
    }

    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        var inlineIndex = 0

        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }

            val iconKey = match.groups[1]?.value
            val btnLabel = match.groups[2]?.value
            val boldText = match.groups[3]?.value
            val codeText = match.groups[4]?.value

            when {
                iconKey != null && btnLabel != null -> {
                    val inlineId = "btn_${inlineIndex++}_$btnLabel"
                    val iconVec = resolveInlineIcon(iconKey)

                    val measuredLabel = textMeasurer.measure(
                        text = AnnotatedString(btnLabel),
                        style = chipTextStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                    val extraWidthDp = 6.dp + 12.dp + (if (iconVec != null) 16.dp else 0.dp)
                    val totalWidthSp = with(density) {
                        (measuredLabel.size.width + extraWidthDp.toPx()).toSp()
                    }
                    val totalHeightSp = with(density) { 21.dp.toSp() }

                    appendInlineContent(inlineId, btnLabel)
                    inlineContentMap[inlineId] = InlineTextContent(
                        placeholder = Placeholder(
                            width = totalWidthSp,
                            height = totalHeightSp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 1.5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(19.dp)
                                    .wrapContentWidth(unbounded = true)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(lerp(colorScheme.surface, colorScheme.surfaceContainerHighest, 0.8f))
                                    .border(
                                        width = 0.8.dp,
                                        color = colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(5.dp)
                                    )
                                    .padding(horizontal = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (iconVec != null) {
                                    Icon(
                                        imageVector = iconVec,
                                        contentDescription = null,
                                        tint = colorScheme.primary.copy(alpha = 0.85f),
                                        modifier = Modifier.size(11.5.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = btnLabel,
                                    color = colorScheme.onSurface.copy(alpha = 0.92f),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = chipTextStyle
                                )
                            }
                        }
                    }
                }
                boldText != null -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    ) {
                        append(boldText)
                    }
                }
                codeText != null -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            background = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                        )
                    ) {
                        append(" $codeText ")
                    }
                }
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    Text(
        text = annotatedString,
        inlineContent = inlineContentMap,
        style = TextStyle(
            fontSize = 13.sp,
            lineHeight = 21.5.sp,
            color = colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    )
}

private fun resolveInlineIcon(key: String): ImageVector? {
    return when (key) {
        "add" -> Icons.Default.Add
        "book" -> Icons.AutoMirrored.Filled.LibraryBooks
        "paste" -> Icons.Default.ContentPaste
        "check" -> Icons.Default.Check
        "groups" -> Icons.Default.Groups
        "person_add" -> Icons.Default.PersonAdd
        "person" -> Icons.Default.Person
        "eq" -> Icons.Default.GraphicEq
        "folder" -> Icons.Default.Folder
        "group" -> Icons.Default.GroupWork
        "edit_note" -> Icons.Default.EditNote
        "edit" -> Icons.Default.Edit
        "tune" -> Icons.Default.Tune
        "txt" -> Icons.Default.Description
        "save" -> Icons.Default.SaveAlt
        "share" -> Icons.Default.Share
        "settings" -> Icons.Default.Settings
        "pie_chart" -> Icons.Default.PieChart
        "delete" -> Icons.Default.DeleteSweep
        "eye" -> Icons.Default.Visibility
        "shuffle" -> Icons.Default.Shuffle
        "warn" -> Icons.Default.WarningAmber
        "history" -> Icons.Default.History
        "lightbulb" -> Icons.Default.Lightbulb
        "insights" -> Icons.Default.Insights
        "list" -> Icons.AutoMirrored.Filled.List
        "tree" -> Icons.Default.AccountTree
        "desktop" -> Icons.Default.DesktopWindows
        "phone" -> Icons.Default.PhoneAndroid
        "chip" -> null
        else -> null
    }
}

@Composable
internal fun ManualSubHeading(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
