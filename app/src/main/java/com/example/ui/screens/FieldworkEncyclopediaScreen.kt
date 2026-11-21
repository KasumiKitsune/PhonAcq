package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TipsAndUpdates
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.ResponsiveContentContainer
import com.example.ui.components.StudioMotion
import com.example.ui.components.StudioSidebarNavItem
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FieldworkEncyclopediaScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backVisualState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = true,
        state = backVisualState
    ) {
        onBack()
    }

    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedVolumeName by rememberSaveable { mutableStateOf("ALL") }

    val selectedVolume = remember(selectedVolumeName) {
        EncyclopediaVolume.entries.firstOrNull { it.name == selectedVolumeName }
    }

    val filteredEntries = remember(searchQuery, selectedVolume) {
        EncyclopediaRepository.filterEntries(query = searchQuery, volume = selectedVolume)
    }

    var selectedDesktopEntryId by rememberSaveable {
        mutableStateOf(EncyclopediaRepository.allEntries.first().id)
    }

    val expandedMobileIds = remember {
        mutableStateMapOf<String, Boolean>().apply {
            put(EncyclopediaRepository.allEntries.first().id, true)
        }
    }

    LaunchedEffect(filteredEntries) {
        if (filteredEntries.isNotEmpty() && filteredEntries.none { it.id == selectedDesktopEntryId }) {
            selectedDesktopEntryId = filteredEntries.first().id
        }
    }

    val activeDesktopEntry = remember(filteredEntries, selectedDesktopEntryId) {
        filteredEntries.firstOrNull { it.id == selectedDesktopEntryId }
            ?: filteredEntries.firstOrNull()
    }

    val mobileListState = rememberLazyListState()

    fun jumpToRelatedEntry(targetId: String, isTwoPane: Boolean) {
        val target = EncyclopediaRepository.getEntryById(targetId) ?: return
        if (filteredEntries.none { it.id == targetId }) {
            searchQuery = ""
            selectedVolumeName = "ALL"
        }
        if (isTwoPane) {
            selectedDesktopEntryId = target.id
        } else {
            expandedMobileIds[target.id] = true
            scope.launch {
                val targetList = EncyclopediaRepository.filterEntries(
                    query = if (filteredEntries.none { it.id == targetId }) "" else searchQuery,
                    volume = if (filteredEntries.none { it.id == targetId }) null else selectedVolume
                )
                val idx = targetList.indexOfFirst { it.id == targetId }
                if (idx >= 0) {
                    mobileListState.animateScrollToItem(idx)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .predictiveBackLayer(backVisualState)
            .testTag("fieldwork_encyclopedia_screen")
    ) {
        val isTwoPaneLayout = maxWidth >= 760.dp
        val isSingleRowFilterBar = maxWidth >= 680.dp
        val sidebarWidth by animateDpAsState(
            targetValue = if (maxWidth >= 960.dp) 292.dp else 252.dp,
            animationSpec = StudioMotion.mediumInTween(),
            label = "encyclopediaSidebarWidth"
        )

        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "语音学百科",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "田野调查方法、语音学概念、图谱判读与语料归档规范",
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
                                modifier = Modifier.testTag("encyclopedia_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )

                    EncyclopediaSearchAndVolumeBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        selectedVolume = selectedVolume,
                        onSelectVolume = { vol -> selectedVolumeName = vol?.name ?: "ALL" },
                        isSingleRowBar = isSingleRowFilterBar
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            if (filteredEntries.isEmpty()) {
                EncyclopediaEmptySearchView(
                    searchQuery = searchQuery,
                    selectedVolume = selectedVolume,
                    onSearchAllVolumes = { selectedVolumeName = "ALL" },
                    onClearSearch = {
                        searchQuery = ""
                        selectedVolumeName = "ALL"
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            } else if (isTwoPaneLayout) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 1120.dp)
                            .fillMaxSize()
                    ) {
                        val sidebarScroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(sidebarScroll)
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .testTag("encyclopedia_desktop_sidebar"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                var lastVol: EncyclopediaVolume? = null
                                filteredEntries.forEach { entry ->
                                    if (entry.volume != lastVol) {
                                        if (lastVol != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        Text(
                                            text = entry.volume.fullTitle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                        lastVol = entry.volume
                                    }

                                    EncyclopediaSidebarItem(
                                        entry = entry,
                                        selected = activeDesktopEntry?.id == entry.id,
                                        onClick = { selectedDesktopEntryId = entry.id }
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                            }
                            PlatformVerticalScrollbar(
                                scrollState = sidebarScroll,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                            )
                        }

                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("encyclopedia_desktop_reader_pane"),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val currentEntry = activeDesktopEntry ?: filteredEntries.first()
                            AnimatedContent(
                                targetState = currentEntry.id,
                                transitionSpec = {
                                    val fromNum = EncyclopediaRepository.getEntryById(initialState)?.number ?: 0
                                    val toNum = EncyclopediaRepository.getEntryById(targetState)?.number ?: 0
                                    val dir = if (toNum >= fromNum) 1 else -1
                                    (fadeIn(tween(210, easing = FastOutSlowInEasing)) +
                                        slideInVertically(tween(230, easing = FastOutSlowInEasing)) { fullH -> dir * (fullH / 18) }) togetherWith
                                        (fadeOut(tween(130)) +
                                            slideOutVertically(tween(150)) { fullH -> -dir * (fullH / 24) })
                                },
                                label = "EncyclopediaReaderTransition"
                            ) { entryId ->
                                val entry = EncyclopediaRepository.getEntryById(entryId) ?: filteredEntries.first()
                                val readerScroll = rememberScrollState()
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(readerScroll)
                                            .padding(horizontal = 24.dp, vertical = 18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .widthIn(max = 780.dp)
                                                .fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            EncyclopediaDesktopHeaderCard(entry = entry)

                                            EncyclopediaEntryBodyContent(
                                                entry = entry,
                                                onClickRelated = { relId -> jumpToRelatedEntry(relId, isTwoPane = true) }
                                            )

                                            val curIdx = filteredEntries.indexOfFirst { it.id == entry.id }
                                            if (filteredEntries.size > 1 && curIdx >= 0) {
                                                EncyclopediaPrevNextBar(
                                                    prevEntry = filteredEntries.getOrNull(curIdx - 1),
                                                    nextEntry = filteredEntries.getOrNull(curIdx + 1),
                                                    onSelectEntry = { selectedDesktopEntryId = it.id }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(24.dp))
                                        }
                                    }
                                    PlatformVerticalScrollbar(
                                        scrollState = readerScroll,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ResponsiveContentContainer(
                        maxWidth = 760.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = mobileListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = filteredEntries,
                                key = { it.id }
                            ) { entry ->
                                val isOpen = expandedMobileIds[entry.id] == true || searchQuery.isNotBlank()
                                ExpandableEncyclopediaCard(
                                    entry = entry,
                                    expanded = isOpen,
                                    onToggle = {
                                        expandedMobileIds[entry.id] = !isOpen
                                    },
                                    onClickRelated = { relId ->
                                        jumpToRelatedEntry(relId, isTwoPane = false)
                                    }
                                )
                            }

                            item(key = "bottom_spacer") {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                    PlatformVerticalScrollbar(
                        listState = mobileListState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun EncyclopediaSearchAndVolumeBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedVolume: EncyclopediaVolume?,
    onSelectVolume: (EncyclopediaVolume?) -> Unit,
    isSingleRowBar: Boolean
) {
    val cs = MaterialTheme.colorScheme
    if (isSingleRowBar) {
        // Sleek single-row bar on >=680dp so article content gets maximum vertical room
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1120.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VolumeFilterChip(
                        selected = selectedVolume == null,
                        icon = null,
                        label = "全部 (${EncyclopediaRepository.allEntries.size})",
                        onClick = { onSelectVolume(null) },
                        modifier = Modifier.testTag("encyclopedia_vol_ALL")
                    )
                    EncyclopediaVolume.entries.forEach { vol ->
                        VolumeFilterChip(
                            selected = selectedVolume == vol,
                            icon = vol.icon,
                            label = vol.shortTitle,
                            onClick = { onSelectVolume(vol) },
                            modifier = Modifier.testTag("encyclopedia_vol_${vol.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "搜索术语（入声、共振峰、蓝牙、TextGrid...）",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("encyclopedia_clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空搜索",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cs.surfaceContainerLowest,
                        unfocusedContainerColor = cs.surfaceContainerLowest,
                        unfocusedBorderColor = cs.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .widthIn(min = 220.dp, max = 340.dp)
                        .heightIn(min = 48.dp)
                        .testTag("encyclopedia_search_input")
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "搜索术语或主题（如：入声、连读变调、共振峰、蓝牙、降噪、TextGrid）",
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("encyclopedia_clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清空搜索",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerLowest,
                    unfocusedContainerColor = cs.surfaceContainerLowest,
                    unfocusedBorderColor = cs.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("encyclopedia_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VolumeFilterChip(
                    selected = selectedVolume == null,
                    icon = null,
                    label = "全部 (${EncyclopediaRepository.allEntries.size})",
                    onClick = { onSelectVolume(null) },
                    modifier = Modifier.testTag("encyclopedia_vol_ALL")
                )
                EncyclopediaVolume.entries.forEach { vol ->
                    VolumeFilterChip(
                        selected = selectedVolume == vol,
                        icon = vol.icon,
                        label = vol.shortTitle,
                        onClick = { onSelectVolume(vol) },
                        modifier = Modifier.testTag("encyclopedia_vol_${vol.name}")
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeFilterChip(
    selected: Boolean,
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState

    val chipBg by animateColorAsState(
        targetValue = when {
            selected -> cs.primaryContainer
            isHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.35f)
            else -> cs.surfaceContainerLow
        },
        animationSpec = StudioMotion.microTween(),
        label = "volumeChipBg"
    )
    val chipBorder by animateColorAsState(
        targetValue = when {
            selected -> cs.primary
            isHovered -> cs.primary.copy(alpha = 0.55f)
            else -> cs.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "volumeChipBorder"
    )
    val chipIconTint by animateColorAsState(
        targetValue = when {
            selected -> cs.primary
            isHovered -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = StudioMotion.microTween(),
        label = "volumeChipIconTint"
    )
    val chipTextColor by animateColorAsState(
        targetValue = when {
            selected -> cs.onPrimaryContainer
            isHovered -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = StudioMotion.microTween(),
        label = "volumeChipTextColor"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = chipBg,
        border = BorderStroke(
            width = if (selected) 1.2.dp else 1.dp,
            color = chipBorder
        ),
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .hoverable(hoverSrc)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = hoverSrc, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = chipIconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = chipTextColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun EncyclopediaSidebarItem(
    entry: EncyclopediaEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    StudioSidebarNavItem(
        title = entry.title,
        subtitle = entry.oneLineSummary,
        icon = entry.volume.icon,
        selected = selected,
        onClick = onClick,
        iconContainerColor = cs.primaryContainer.copy(alpha = 0.6f),
        iconTint = cs.primary,
        trailing = {
            Surface(
                shape = CircleShape,
                color = if (selected) cs.primary else cs.surfaceContainerHigh,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${entry.number}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        modifier = Modifier.testTag("enc_item_${entry.id}")
    )
}

@Composable
private fun EncyclopediaDesktopHeaderCard(entry: EncyclopediaEntry) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UiButtonPill(
                    icon = entry.volume.icon,
                    label = entry.volume.shortTitle,
                    tone = BadgeTone.Primary
                )
                Text(
                    text = "第 ${entry.number} 篇",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.outline
                )
            }
            Text(
                text = entry.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 24.sp,
                color = cs.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncyclopediaEntryBodyContent(
    entry: EncyclopediaEntry,
    onClickRelated: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    // 1. 核心提要 Banner
    ManualKeySummaryBanner(
        icon = Icons.Default.TipsAndUpdates,
        title = "核心提要",
        summary = entry.oneLineSummary
    )

    // 2. Material You Dynamic Diagram (if available for this entry)
    if (entry.diagramId != null) {
        EncyclopediaDynamicDiagramCard(
            diagramId = entry.diagramId,
            caption = entry.diagramCaption
        )
    }

    // 3. Body Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            entry.paragraphs.forEach { paragraph ->
                RichManualText(
                    text = paragraph,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 4. 田野与实验提示
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, cs.secondary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = cs.secondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(17.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "田野与实验提示",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onSecondaryContainer
                )
                RichManualText(
                    text = entry.fieldworkTip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 5. 相关词条延伸跳转
    val relatedEntries = remember(entry.relatedIds) {
        entry.relatedIds.mapNotNull { EncyclopediaRepository.getEntryById(it) }
    }
    if (relatedEntries.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "相关词条",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                relatedEntries.forEach { rel ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cs.surfaceContainerLow,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onClickRelated(rel.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = rel.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableEncyclopediaCard(
    entry: EncyclopediaEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClickRelated: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val chevronRot by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "EncCardRot"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) cs.surfaceContainerLow else cs.surfaceContainerLowest
        ),
        border = BorderStroke(
            width = if (expanded) 1.3.dp else 1.dp,
            color = if (expanded) cs.primary else cs.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("enc_item_${entry.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UiButtonPill(
                            icon = entry.volume.icon,
                            label = entry.volume.shortTitle,
                            tone = BadgeTone.Primary
                        )
                        Text(
                            text = "#${entry.number}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.outline
                        )
                    }
                    Text(
                        text = entry.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 20.sp,
                        color = cs.onSurface
                    )
                    if (!expanded) {
                        Text(
                            text = entry.oneLineSummary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (expanded) cs.primaryContainer.copy(alpha = 0.7f) else cs.surfaceContainerHigh,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起词条" else "展开词条",
                            tint = if (expanded) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(chevronRot)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(200)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(170)) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 15.dp, end = 15.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                    EncyclopediaEntryBodyContent(
                        entry = entry,
                        onClickRelated = onClickRelated
                    )
                }
            }
        }
    }
}

@Composable
private fun EncyclopediaPrevNextBar(
    prevEntry: EncyclopediaEntry?,
    nextEntry: EncyclopediaEntry?,
    onSelectEntry: (EncyclopediaEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prevEntry != null) {
            OutlinedButton(
                onClick = { onSelectEntry(prevEntry) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(
                    text = "← 上一篇：${prevEntry.title}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (nextEntry != null) {
            FilledTonalButton(
                onClick = { onSelectEntry(nextEntry) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(
                    text = "下一篇：${nextEntry.title} →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EncyclopediaEmptySearchView(
    searchQuery: String,
    selectedVolume: EncyclopediaVolume?,
    onSearchAllVolumes: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = cs.primary.copy(alpha = 0.65f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (selectedVolume != null) {
                "在「${selectedVolume.shortTitle}」分卷下没找到包含“$searchQuery”的词条"
            } else {
                "没有找到包含“$searchQuery”的百科词条"
            },
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "可尝试搜索“入声”、“连读变调”、“共振峰”、“蓝牙”、“降噪”或“TextGrid”",
            fontSize = 12.5.sp,
            color = cs.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selectedVolume != null) {
                FilledTonalButton(
                    onClick = onSearchAllVolumes,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("在全部五大卷中查找", fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = onClearSearch,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("查看全部词条")
            }
        }
    }
}
