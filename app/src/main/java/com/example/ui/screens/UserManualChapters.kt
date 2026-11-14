package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.KeycapBadge

@Composable
internal fun RenderManualChapterBody(
    chapterId: Int,
    isDesktop: Boolean
) {
    when (chapterId) {
        // --- Simple Mode Chapters (101 ~ 105) ---
        101 -> SimpleChapter01QuickStartContent(isDesktop)
        102 -> SimpleChapter02WordImportContent(isDesktop)
        103 -> SimpleChapter03FieldworkTipsContent(isDesktop)
        104 -> SimpleChapter04ArchiveExportContent(isDesktop)
        105 -> SimpleChapter05SettingsAndMoreContent(isDesktop)

        // --- Advanced Mode Chapters (1 ~ 8) ---
        1 -> AdvChapter01WorkspaceAndQuickStartContent(isDesktop)
        2 -> AdvChapter02SpeakerAndPitchFitContent(isDesktop)
        3 -> AdvChapter03WordListAndPolysyllableContent(isDesktop)
        4 -> AdvChapter04RecordingAndQuietControlContent(isDesktop)
        5 -> AdvChapter05QuickNotesAndMultiTakeContent()
        6 -> AdvChapter06AcousticsAndChartsContent(isDesktop)
        7 -> AdvChapter07ExportAndTextGridContent()
        8 -> AdvChapter08SettingsCleanupAndOfflineContent(isDesktop)
    }
}

// =========================================================================
// 简洁模式说明 (Chapters 101 ~ 105)
// =========================================================================

@Composable
private fun SimpleChapter01QuickStartContent(isDesktop: Boolean) {
    ManualKeySummaryBanner(
        icon = Icons.Default.Flag,
        title = "三步完成采录：选用字表 → 选定发音人 → 逐词录制与导出",
        summary = "同一份字表可供多位发音人共用；发音时保持底部音量条落在绿色区间即可。"
    )

    ManualStepCard(
        stepNumber = "1",
        title = "准备调查字表",
        points = listOf(
            if (isDesktop) {
                "点击左侧导航栏 [[edit_note|字表]]（或项目栏右下角 [[add|新建项目]]），可直接选用内置的 5 套调查字表，或导入电脑中的 Excel / Word / CSV / TXT 字表。"
            } else {
                "在底部 [[list|字表项目]] 页面点击右下角 [[add|新建项目]]，可直接选用内置的 5 套调查字表，或导入手机中的 Excel / Word / CSV / TXT 字表。"
            },
            "字表建好后，不同发音人可直接共用同一项目，无需重复建表。"
        )
    )

    ManualStepCard(
        stepNumber = "2",
        title = "选定当前发音人",
        points = listOf(
            if (isDesktop) {
                "点击左侧项目卡片上的发音人标签，或在录音台左上角点击 [[person|发音人]]，填写姓名、性别、年龄与方言点即可新建或切换。"
            } else {
                "点击首页项目卡片上的发音人标签，或在录音台左上角点击 [[person|发音人]]，填写姓名、性别、年龄与方言点即可新建或切换。"
            },
            "每位发音人的录制进度、音频文件与随手记均独立保存，切换发音人不会相互覆盖。"
        )
    )

    ManualStepCard(
        stepNumber = "3",
        title = if (isDesktop) "逐词录制并保存到电脑" else "逐词录制并分享或保存",
        points = listOf(
            if (isDesktop) {
                "开录前先在窗口顶部选择输入麦克风。点击红色录音键（或按键盘 **Space / F9**）开始录制；可在工具栏切换「读完自动翻页」或「按住录」（按住说话、松开即存）。"
            } else {
                "点击底部红色录音键开始录制；可在工具栏切换「读完自动翻页」或「按住录」（按住下方大按钮说话、松开即完成保存）。"
            },
            if (isDesktop) {
                "录满后筛选栏会提示直达导出，也可打开左侧 [[folder|录音库]] 点击右上角 [[share|导出项目]]，将音频文件夹与原生 Excel 汇总表（或 Praat 工程文件）保存到电脑。"
            } else {
                "录满后筛选栏会提示直达导出，也可打开底部 [[folder|录音库]] 点击右上角 [[share|导出项目]]，生成音频与原生 Excel 压缩包或 Praat 工程文件，一键分享或另存到本地。"
            }
        )
    )
}

@Composable
private fun SimpleChapter02WordImportContent(isDesktop: Boolean) {
    ManualQaCard(
        icon = Icons.AutoMirrored.Filled.LibraryBooks,
        question = "已有做好的 Excel 或 Word 字表，如何直接导入？",
        summaryNote = "点击「导入文件」选择文档，自动识别词条、分组与提示",
        points = listOf(
            if (isDesktop) {
                "在新建或编辑字表页面点击顶部 [[folder|导入文件]]，选中电脑中的 `.xlsx`、`.docx`、`.csv` 或 `.txt` 文件。"
            } else {
                "在新建或编辑字表页面点击顶部 [[folder|导入文件]]，选中手机中的 `.xlsx`、`.docx`、`.csv` 或 `.txt` 文件。"
            },
            "表格中的「字词」「分组」「备注/释义」会自动对应填入，导入保存后即可直接开录。"
        )
    )

    ManualQaCard(
        icon = Icons.Default.ContentPaste,
        question = "复制了一段文字，如何快速拆成字表或给词条配图？",
        summaryNote = "点「粘贴为字表」自动拆分词条；支持开启图片词卡看图说词",
        points = listOf(
            "复制文字后，在字表编辑页点击 [[paste|粘贴为字表]]（或切到 [[txt|TXT 文本批量编辑]] 标签页粘贴）。",
            "每行一词或顿号连写的 `巴、波、衣、乌` 都会自动拆成独立词卡；行首序号自动去除，`【阴平组】` 自动识别为词条分组，`巴 (pa55)` 括号内文字自动转为公共提示。",
            "**看图说方言（图片词卡）**：面对不识字的长辈或“有音无字”的方言词，可在编辑页开启图片词卡，点击词条左侧「配图」从相册选图或现场拍照；中途调整词序或补充词条，只要保留带 [[chip|原词条]] 标记的旧词，已有录音均完好保留。"
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimpleChapter03FieldworkTipsContent(isDesktop: Boolean) {
    ManualQaCard(
        icon = Icons.Default.Mic,
        question = "怎样让录出来的声音干净清晰、不爆音？",
        summaryNote = "先试音测底噪 + 避开蓝牙耳机麦 + 保持一拳半距离观察绿条",
        points = listOf(
            if (isDesktop) {
                "开录前请关闭风扇、空调等持续噪音源，点击左侧导航栏 [[eq|试音]] 检测环境底噪并试录几秒自检。"
            } else {
                "开录前请关闭风扇、空调等持续噪音源，打开左上角菜单 [[eq|试音与环境检查]] 检测环境底噪并试录自检。"
            },
            if (isDesktop) {
                "**在顶部选准有线声卡**：请在窗口顶部下拉菜单选择有线麦克风或外接声卡，**切勿使用蓝牙耳机麦克风**（蓝牙通话链路会切除高频并压缩音质）。"
            } else {
                "**切勿使用蓝牙耳机录音**：请直接使用手机内置麦克风或有线领夹麦（连接蓝牙耳机时录音台顶部会自动提示）。"
            },
            "嘴部距麦克风约 **15～20 厘米（一拳半）** 并略微偏开角度以防气流喷麦。发音时底部音量条落在 **绿色区间** 最清晰；若冲入红色提示过载，请适当拉远距离后重录。"
        )
    )

    ManualQaCard(
        icon = Icons.Default.Visibility,
        question = if (isDesktop) {
            "如何放大字词方便看读，并避免鼠标点击声混入录音？"
        } else {
            "长辈看不清小字，或担心手指点屏幕录进杂音怎么办？"
        },
        summaryNote = if (isDesktop) {
            "点「看读」或按 M 键进入大字看读模式，用空格键与方向键安静操控"
        } else {
            "点「看读」放大字词并隐去干扰信息，用手机侧面音量键安静操控"
        },
        visualPreview = if (isDesktop) {
            {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShortcutInlinePair("Space / F9", "录音/停止")
                    ShortcutInlinePair("← / →", "上一词/下一词")
                    ShortcutInlinePair("Enter / P", "试听回放")
                    ShortcutInlinePair("F", "标记重录")
                    ShortcutInlinePair("M", "看读模式")
                }
            }
        } else null,
        points = listOf(
            if (isDesktop) {
                "**纯净看读模式**：点击录音台顶栏 **看读**（或按 `M` 键），字词将居中放大并收起无关控件，顶栏可按需点按切换显示汉字或提示；按 `Esc` 键或点 **退出** 即可返回。"
            } else {
                "**纯净看读与音量键控制**：点击录音台顶栏 **看读** 放大字词，顶栏可按需独立切换显示汉字或提示；前往「设置 → 录音与操控」开启 **音量键控制**，即可按机身音量键安静录音与翻页。"
            },
            "**乱序防唱读与多遍切换**：点击工具栏 [[shuffle|乱序]] 可打乱同调类字连读产生的“唱书调”；同一词条重录 2 遍以上时，卡片上方会出现 **共 N 遍** 标记，点开即可试听挑出最佳一遍。",
            "**随手记备注**：遇到文白异读或特殊音变，点击词卡下方 **随手记** 即可点选标签（再次点击可取消）或输入说明，导出时自动写入 Excel 汇总表。"
        )
    )

    ManualQaCard(
        icon = Icons.Default.Devices,
        question = "手头有两台设备（如平板/电脑 + 手机），如何分工看字与操控？",
        summaryNote = "使用「无线互联」将一台设为看读端、另一台设为遥控端",
        points = listOf(
            "两台设备接入同一 Wi-Fi（无外网环境可开启手机 **个人热点** 互连），点击顶部的 **无线互联** 图标。",
            "发起互联后会显示 **6 位配对码**。可将大屏设为 **看读端**（提供宣纸、水墨等柔和字卡及「中场休息」画面），另一台设为 **遥控端**（佩戴耳机实时监听、查看电平、回放检查并随手记音），无损音频将自动归档至发起端工程。"
        )
    )
}

@Composable
private fun SimpleChapter04ArchiveExportContent(isDesktop: Boolean) {
    ManualQaCard(
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        question = "录完一轮后，如何快速检查是否有漏录或杂音？",
        summaryNote = "点击「连续试听」顺序回放，需补录词条点旗标筛出集中重录",
        points = listOf(
            if (isDesktop) {
                "打开左侧 [[folder|录音库]]，点击顶部 **连续试听**，软件会按顺序自动回放已录词条。"
            } else {
                "打开底部 [[folder|录音库]]，点击顶部 **连续试听**，软件会按顺序自动回放已录词条。"
            },
            "听到有杂音或读错的词条，点击右侧 **旗标按钮** 标为 `已标重录`；回到录音台点亮 [[chip|仅待录]]，即可只显示未录与标重录的词条，集中补录完毕。"
        )
    )

    ManualQaCard(
        icon = Icons.Default.FolderZip,
        question = "点击「导出项目」后，不同格式分别包含什么内容？",
        summaryNote = "整理归档选压缩包 (.zip)；直接用 Praat 分析选 .Collection",
        points = listOf(
            "**录音压缩包 (`.zip`)**：解压后包含按序号整洁命名的 `.wav` 无损音频（多人开录时默认全选并按发音人分目录），以及用 Excel / WPS 直接打开的双工作表 `录音汇总表.xlsx`（含已录汇总与完整调查字表）；配图词表可勾选附带 `词卡图片/`，勾选 `Praat 标注文件 (.TextGrid)` 时还会同步生成同名标注。",
            "**Praat 工程 (`.Collection`)**：将全部音频与配对标注合并为单个文件。在电脑 Praat 中点击 `Read from file...` 打开该文件，所有词条与标注即一次性成对加载。",
            if (isDesktop) {
                "**保存到电脑**：选定导出「当前发音人」或「全部已录」后，点击右下角 **保存到电脑** 选择目标文件夹即可。"
            } else {
                "**分享或另存**：选定导出范围后，点击 **分享** 可直接发送至微信或邮箱；点击 **另存到本地** 可存入手机文件夹。"
            }
        )
    )
}

@Composable
private fun SimpleChapter05SettingsAndMoreContent(isDesktop: Boolean) {
    ManualQaCard(
        icon = Icons.Default.Settings,
        question = "字号调节、备份恢复与缓存清理在哪里操作？",
        summaryNote = "打开「设置」进入对应分类即可修改或导入备份",
        points = listOf(
            if (isDesktop) {
                "点击左侧导航栏底部的 [[settings|设置]]，在左侧点选分类即可修改对应选项："
            } else {
                "打开左上角菜单中的 [[settings|设置]]，进入对应分类页面即可修改："
            },
            "**调大看读字号**：进入 **显示与提示**，选择「大字」或「特大」，或开启看读模式自动隐藏提示。",
            "**更改 TextGrid 标注层**：进入 **TextGrid 标注**，可直接点选预设模板或自定义各层名称。",
            "**恢复备份与清理缓存**：进入 **导出与存储**，点击 **导入项目备份 (.zip)** 可将之前导出的压缩包完整恢复为本地项目；点击 **清缓存** 可清除打包临时文件（不会删除正式录音）。"
        )
    )

    ManualQaCard(
        icon = Icons.Default.Insights,
        question = "需要查看声调曲线、元音图或使用国际音标键盘怎么办？",
        summaryNote = "前往「设置 → 界面模式」切换为进阶模式，已有数据完整保留",
        points = listOf(
            "进入「[[settings|设置]] → **界面模式**」切换为 **进阶模式**，即可启用单字语图与调值分析、全表声调聚类与元音轨迹图、国际音标快捷面板、多遍录音择优及 PhonTracer 工程导出。",
            "切换模式不会影响任何已有字表、录音文件或备注记录，随时可自由往返切换。"
        )
    )
}

// =========================================================================
// 进阶模式说明 (Chapters 1 ~ 8)
// =========================================================================

@Composable
private fun AdvChapter01WorkspaceAndQuickStartContent(isDesktop: Boolean) {
    ManualKeySummaryBanner(
        icon = if (isDesktop) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
        title = if (isDesktop) {
            "左侧管字表与发音人，顶部选麦克风，右侧专心录音与看图"
        } else {
            "底部三个按钮切换核心流程，试音与设置收在左上角菜单里"
        },
        summary = if (isDesktop) {
            "在左侧挑好字表和发音人、顶部选好声卡后，右侧就能一边录音记音，一边点开字词看声音图像（窗口拉宽后单字图像还会常驻在最右侧边栏）。"
        } else {
            "底部随时切换「字表项目 · 录音台 · 录音库」；试音测噪、发音人管理与设置在左上角菜单里打开（平板横屏时会自动变成左右分栏）。"
        }
    )

    ManualStepCard(
        stepNumber = "1",
        title = "建立或导入调查字表",
        points = listOf(
            if (isDesktop) {
                "点击左侧导航栏 [[edit_note|字表]]（或项目栏右下角 [[add|新建项目]]），可直接套用内置的 5 套调查模板，或点 [[folder|导入文件]] 读取 `.xlsx` / `.docx` / `.csv` / `.txt` 文档，也可复制文字点 [[paste|粘贴为字表]]。"
            } else {
                "在底部 [[list|字表项目]] 右下角点击 [[add|新建项目]]，可直接套用内置的 5 套调查模板，或点 [[folder|导入文件]] 读取 `.xlsx` / `.docx` / `.csv` / `.txt` 文档，也可复制文字点 [[paste|粘贴为字表]]。"
            },
            "如果需要让某份字表只显示特定调查点的发音人，可在编辑字表时勾选对应的 **关联发音人分组**；不选则默认显示所有发音人。"
        )
    )

    ManualStepCard(
        stepNumber = "2",
        title = "登记发音人并测一下房间噪音",
        points = listOf(
            "打开 [[groups|发音人管理]] → [[person_add|添加发音人]]，填好姓名或代号、性别、年龄、方言点及所属分组。",
            if (isDesktop) {
                "在窗口顶部下拉框选好 **外接声卡或有线麦克风**（不要选蓝牙耳机麦），再点左侧 [[eq|试音]] 测几秒环境噪音，确认房间够安静。"
            } else {
                "打开左上角菜单 → [[eq|试音与环境检查]] 测几秒环境噪音，确认房间够安静，并使用手机自带麦或有线领夹麦（不要连蓝牙耳机麦）。"
            }
        )
    )

    ManualStepCard(
        stepNumber = "3",
        title = "点名字开录、当场看图检查、导出工程文件",
        points = listOf(
            "在项目卡片上直接点击某位发音人标签（如 `张大爷 0/60`），就进入这位发音人的录音台。",
            "录音时可随时点 [[edit_note|随手记]] 标国际音标，点词卡右侧图标看这个字的声音图像与调值；录完去 [[folder|录音库]] 点顶部 **声学格局** 看全表声调与元音汇总图，最后点右上角 **导出项目** 存成 `.zip`、Praat `.Collection` 或 PhonTracer `.teproj`。"
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvChapter02SpeakerAndPitchFitContent(isDesktop: Boolean) {
    ManualQaCard(
        icon = Icons.Default.PeopleAlt,
        question = "同一份字表要找多位发音人调查，怎么切换和按调查点筛选？",
        summaryNote = "点名字秒切发音人，每人录音与记音完全分开存",
        visualPreview = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UiButtonPill(icon = Icons.Default.CheckCircle, label = "张大爷 60/60", tone = BadgeTone.Secondary)
                UiButtonPill(icon = Icons.Default.GraphicEq, label = "李阿姨 32/60", tone = BadgeTone.Primary)
                UiButtonPill(icon = Icons.Default.PersonOutline, label = "王青年 0/60", tone = BadgeTone.Neutral)
            }
        },
        points = listOf(
            "**换人不串音**：在项目卡片或录音台左上角点击人名就能切换发音人。每位发音人的音频、多遍录音记录和当场记音各自独立保存。",
            "**一人可挂多个分组标签**：编辑发音人时可以同时勾选多个分组（例如同时勾上 [[group|苏州点]] 和 [[group|老年组]]）。字表只要关联其中任意一个组，这位发音人就会自动列在卡片上。",
            if (isDesktop) {
                "如果某张字表下没看到想找的发音人，点左侧项目卡片右上角 [[edit_note|编辑]]，把关联分组改成「不限分组」或勾上他所在的组即可。"
            } else {
                "如果首页字表卡片上没看到想找的发音人，点卡片右上角 [[edit_note|编辑]]，把关联分组改成「不限分组」或勾上他所在的组即可。"
            }
        )
    )

    ManualQaCard(
        icon = Icons.Default.GraphicEq,
        question = "男声低沉、女声清亮，软件给出的五度调值怎么贴合不同人的嗓音？",
        summaryNote = "录满 5 个词自动适应个人音高范围，支持一键重算或手动指定上下限",
        points = listOf(
            "每位发音人录满 **5 个词** 后，软件会在后台自动根据他的实际发音测出个人音高上下限，并在录满 **12 词、25 词** 时继续自动调准，不管是低沉男声还是高亢女声，给出的 `55`、`214` 等参考调值都会贴合他本人的嗓音。",
            "在 **发音人管理**、**切换发音人弹窗** 或单字 **声学详情** 中，既可点击 [[tune|自动测算 / 重算]] 用全套录音重新校准，也可点击 **手动音域** 直接指定基频上下限（如针对高龄嘎裂声设为 `55–220 Hz`）。"
        )
    )
}

@Composable
private fun AdvChapter03WordListAndPolysyllableContent(isDesktop: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ManualSubHeading(
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            title = "分清两个分组：「发音人分组」与「词条分组」"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    UiButtonPill(icon = Icons.Default.Groups, label = "发音人分组", tone = BadgeTone.Secondary)
                    Text("给「人」分类", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                    Text(
                        "例如：苏州点、杭州点、老年组。\n用来控制某份字表下列出哪些发音人。",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    UiButtonPill(icon = Icons.Default.Category, label = "词条分组", tone = BadgeTone.Tertiary)
                    Text("给「字词」分类", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                    Text(
                        "例如：阴平组、入声组、双字连读。\n用来按组筛选词条、组内乱序和按组汇总声调。",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    ManualQaCard(
        icon = Icons.Default.Description,
        question = "如何批量导入字表或配置图片词卡？中途修改字表会丢失录音吗？",
        summaryNote = "支持表格/纯文本导入与词条配图，保留「原词条」标记即不丢录音",
        points = listOf(
            if (isDesktop) {
                "在左侧 [[edit_note|字表]] 编辑页点击 [[folder|导入文件]]，选中 `.xlsx`、`.docx`、`.csv` 或 `.txt` 文档即可自动解析词条、分组与注释；也可复制文本点击 [[paste|粘贴为字表]]。"
            } else {
                "在字表编辑页点击 [[folder|导入文件]]，选中 `.xlsx`、`.docx`、`.csv` 或 `.txt` 文档即可自动解析词条、分组与注释；也可复制文本点击 [[paste|粘贴为字表]]。"
            },
            "**图片词卡（看图引出法）**：采集“有音无字”的方言本字或面向不识字发音人时，可在编辑页开启图片词卡，点击词条左侧「配图」绑定照片；录音时支持双指或滚轮缩放看图，规避汉字字面诱导。",
            "**中途改表保留录音**：修改已开录的字表时，只要未删除带 [[chip|原词条]] 标记的词条，调整顺序、修改组名或补充说明均不会影响各发音人已录制的音频。"
        )
    )

    ManualQaCard(
        icon = Icons.AutoMirrored.Filled.Segment,
        question = "录制双字组、三字组等多音节词条时，系统如何处理音节边界？",
        summaryNote = "自动定位各音节起止位置，同步呈现在声学详情、元音图与导出标注中",
        points = listOf(
            "录制多音节词条后，算法会结合词条音节数、能量包络谷值与共振峰变化，自动估算每个音节的起止边界。",
            "切分结果将直接用于：单字声学详情中的分音节指标检视、全表元音图中的跨音节虚线轨迹，以及导出 `.TextGrid` 与 `.teproj` 时的初始音节层边界（同时支持在单字详情页直接导出单条 `.TextGrid` 或 `.Collection`）。"
        )
    )

    ExpandableDeepDiveNote(
        badgeLabel = "写法示例",
        title = "展开查看：用纯文本批量粘贴建表的格式写法",
        points = listOf(
            "`【阴平组】` 或 `# 阴平组` 单独写一行：自动把后面的词归进「阴平组」。",
            "`巴 (pa55)` 或 `诗 古清平` 或 `梯: ti55`：自动拆成 **字词** + **公共提示**（行首 `1.`、`01、` 序号会自动去掉）。",
            "`时、题、人、明`：同一行用顿号或逗号隔开的词，会自动拆成一条条独立词卡。"
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvChapter04RecordingAndQuietControlContent(isDesktop: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ManualSubHeading(
            icon = Icons.Default.Tune,
            title = if (isDesktop) {
                "四种录音操控方式 + 键盘安静操作"
            } else {
                "四种录音操控方式 + 手机侧面音量键操作"
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecordingModeMiniCard(
                icon = Icons.Default.TouchApp,
                title = "点按录音",
                tag = "稳妥默认",
                desc = "点一下开始录，读完再点一下停下并保存。",
                modifier = Modifier.weight(1f)
            )
            RecordingModeMiniCard(
                icon = Icons.Default.FastForward,
                title = "读完自动翻页",
                tag = "连续快录",
                desc = "读完一词停顿片刻，自动保存并翻到下一词。",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecordingModeMiniCard(
                icon = Icons.Default.PanToolAlt,
                title = "按住录松手存",
                tag = "干脆利落",
                desc = if (isDesktop) {
                    "按住鼠标左键开口读，一松手就存好并切下一词。"
                } else {
                    "手指按住大按钮开口读，一松手就存好并切下一词。"
                },
                modifier = Modifier.weight(1f)
            )
            if (isDesktop) {
                RecordingModeMiniCard(
                    icon = Icons.Default.Keyboard,
                    title = "键盘快捷键",
                    tag = "无鼠标声",
                    desc = "用键盘控制录音、翻页、试听与全屏看读，不录进鼠标点击声。",
                    modifier = Modifier.weight(1f)
                )
            } else {
                RecordingModeMiniCard(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "侧面音量键",
                    tag = "无触屏声",
                    desc = "按「音量+录音、音量-翻页」，避免指尖敲屏幕录进杂音。",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (isDesktop) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShortcutInlinePair("Space / F9", "开始 / 停止录音")
                    ShortcutInlinePair("← / →", "上一词 / 下一词")
                    ShortcutInlinePair("Enter / P", "回放试听当前词")
                    ShortcutInlinePair("F", "标记 / 取消重录")
                    ShortcutInlinePair("M", "大字看读全屏")
                    ShortcutInlinePair("F5", "刷新声卡列表")
                    ShortcutInlinePair("Esc", "停止录音 / 返回")
                }
            }
        }
    }

    ManualSubHeading(
        icon = Icons.Default.Visibility,
        title = "防诱导提示、防截断保护与双设备无线互联"
    )

    ManualFeatureRow(
        text = if (isDesktop) {
            "**纯净看读与载体句框架**：按 `M` 键或点顶栏 **看读** 可放大目标字并 **自动隐藏音标备注与调类组名**，避免诱导发音人；在「设置 → 显示与提示」开启 **载体句提示**（如 `我说 ___ 这个词`），可将目标词嵌入固定句框以稳定语速和句调。"
        } else {
            "**纯净看读与载体句框架**：点击顶栏 **看读** 可放大目标字并 **自动隐藏音标备注与调类组名**，避免诱导发音人（顶栏胶囊可临时切换显隐汉字或提示）；在「设置 → 显示与提示」开启 **载体句提示**（如 `我说 ___ 这个词`），可将目标词嵌入固定句框以稳定语速和句调。"
        }
    )

    ManualFeatureRow(
        text = "**乱序呈现防唱读（自动记忆进度）**：点击工具栏 [[shuffle|乱序]] 可打破同调类字连读产生的“唱书调”，在「设置 → 显示与提示」中可选 **全表完全打乱** 或 **按分组依次打乱**。中途退出会自动保存位置，导出时仍按原表序号整理。"
    )

    ManualFeatureRow(
        text = "**前置预录缓冲与防截断切音**：内置环形预录缓冲确保按下录音键瞬间开口也不吞塞音除阻；若发音人语速偏慢或韵尾拖长，可在「设置 → 收音与声学引擎」将自动切音灵敏度调为 **宽松防截断**，外接专业声卡时可切换 **48 kHz** 采样率。"
    )

    ManualFeatureRow(
        text = "**无线互联（看读端 × 遥控端）**：两台设备在同一 Wi-Fi 或手机个人热点下通过 6 位配对码建立连接。可将平板或电脑设为 **看读端**（支持宣纸、水墨、深色黑板或柔和主题，并在「设置 → 无线互联」中配置翻页权限与显示项）；调查人使用 **遥控端** 戴耳机实时监听、观测电平、静默回放、切换「中场休息」并随手记国际音标，所有无损 `.wav`、速记与 `录音核对清单.xlsx` 实时归档至发起端。"
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvChapter05QuickNotesAndMultiTakeContent() {
    ManualQaCard(
        icon = Icons.Default.EditNote,
        question = "现场听到文白异读或特殊音值，如何快速记音或临时加录异读？",
        summaryNote = "点击「随手记」在光标处插国际音标，支持当场派生紧邻异读词条",
        visualPreview = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UiButtonPill(icon = null, label = "55", tone = BadgeTone.Primary)
                UiButtonPill(icon = null, label = "dʑ", tone = BadgeTone.Primary)
                UiButtonPill(icon = null, label = "tɕʰ", tone = BadgeTone.Primary)
                UiButtonPill(icon = null, label = "ʔ", tone = BadgeTone.Primary)
                UiButtonPill(icon = null, label = "文读", tone = BadgeTone.Secondary)
                UiButtonPill(icon = null, label = "白读", tone = BadgeTone.Secondary)
            }
        },
        points = listOf(
            "**个人记音与公共提示分离存储**：点击词卡下方 [[edit_note|随手记]]，上方的 **当场记音** 仅归属当前发音人（导出时写入 `acoustic_summary.xlsx` 与 `.TextGrid` 标注层）；下方的 **词表公共提示** 则由全表共享。",
            "**光标处精准插入与自定义音标库**：面板内置 **调值、声母、韵母、常用标签** 四栏按键，点击即可在光标位置插入音标或切换标签；前往「[[settings|设置]] → **随手记与标签**」可按调查点自定义增删声韵调符号。",
            "**现场派生文白异读**：发音人读出文白两读或又音时，在弹窗底部点击 **派生异读词条**（或在多遍管理弹窗点击 **存为异读**），即可在当前词后紧邻插入 `字·白读` 等新词条，不断流继续采录。"
        )
    )

    ManualQaCard(
        icon = Icons.Default.History,
        question = "同一个词录了好几遍，怎么把之前录得更好的一遍挑出来用？",
        summaryNote = "默认帮你留着最近 3 遍录音，点左上角「共 N 遍」试听切换或分流异读",
        points = listOf(
            "**重录不会删掉旧录音**：重录某个词时，默认会保留最近 **3 遍** 录音（在「设置 → 录音与操控」里可改成保留 1 / 3 / 5 遍）。",
            "**逐遍试听挑最好的一遍**：录过 2 遍以上的词，卡片上方会出现 [[history|共 2 遍 / 共 3 遍]] 按钮。点开就能逐遍回放、查看时长与音质评估，指定正式采用版本，或将某遍直接另存为独立异读词条。",
            "**插旗集中补录**：发现某条录音有杂音或偏轻，点右上角状态胶囊标成 `已标重录`，再回录音台点亮 [[chip|仅待录]] 就能把所有要补录的词筛出来一次录完。"
        )
    )
}

@Composable
private fun AdvChapter06AcousticsAndChartsContent(isDesktop: Boolean) {
    ManualKeySummaryBanner(
        icon = Icons.Default.Insights,
        title = "当场就能看清每个字读得准不准、整张字表的声调和元音长什么样",
        summary = "想看某一个字的图像与调值，点词卡右侧的「声学图标」；想看整张表的声调归类、元音分布和多人对比，去「录音库」点顶部的「声学格局」。"
    )

    ManualQaCard(
        icon = Icons.Default.GraphicEq,
        question = "1. 点开单个字的「声学详情」，能看到什么、怎么用？",
        summaryNote = if (isDesktop) {
            "点词条右侧声学图标打开（窗口较宽时会自动停靠在右侧边栏，边录边看）"
        } else {
            "在录音台或录音库点击词条右侧的声学图标即可打开"
        },
        points = listOf(
            "**对照查看两项参考调值**：顶部同时显示 **这类字在全表里的代表调值** 和 **当前这个字单读出来的调值**；遇到短促的入声字会自动标出短调标记，如果这个字跟同组其他字差别很大，也会直接提醒你听听是不是发生了变调或读错了。",
            "**边播边看声音图像与曲线**：图上可以一键开关 **共振峰轨迹（看元音舌位变化）**、**基频曲线（看声调高低走向）** 和 **韵母测量范围**；点右上角播放键，进度线会跟着声音同步走，哪个位置拐弯或掉调一目了然。",
            "**多人同词对比试听**：如果有多位发音人都录过这个词，滑到底部就能直接点其他发音人的名字，当场对比不同人读同一个字的声音。"
        )
    )

    ManualQaCard(
        icon = Icons.Default.BubbleChart,
        question = "2. 打开录音库顶部的「声学格局」，能得到哪几张全表汇总图？",
        summaryNote = "全表声调归类图 + 单元音/复元音走势图 + 多发音人同图对比",
        points = listOf(
            "**全表声调归类图**：把整张字表的声调归纳成几条清晰的代表曲线（舒声长调与短促入声调分开展示），点一下就能在 **按读音自动归类** 和 **按词表分组归类** 之间切换，还能单独筛出读音偏离较大的字来复查；如果字表里有两字或三字词，也会把常见的连读调型整理出来。",
            "**全表元音分布与滑音箭头图**：单元音会聚成清晰的发音范围圈，复元音会画出 **带箭头的平滑轨迹** 标出舌位从哪滑向哪，多音节词则会用 **虚线箭头（`╌╌▶`）** 把前后每个字的元音串起来。支持双指或点按钮放大看每个字的位置，**点图上任意一个点就能直接听到那个字的原声**。",
            "**多发音人元音同图对比与报告导出**：当项目里有 **2 位以上发音人** 录过音时，可开启 Lobanov 归一化同图对比；点击面板右上角 **导出报告**，即可一键打包多工作表 `声学与音系归纳报告.xlsx` 与高清 PNG 图表，直接用于论文制表与插图。"
        )
    )
}

@Composable
private fun AdvChapter07ExportAndTextGridContent() {
    ManualKeySummaryBanner(
        icon = Icons.Default.FolderZip,
        title = "去「录音库」点右上角「导出项目」，点选格式卡片即可打包带走",
        summary = "不管是交一套带 Excel 统计表的整理好的音频文件夹，还是直接放进 Praat 或 PhonTracer 里继续做分析，都能一键生成。"
    )

    ManualSubHeading(
        icon = Icons.Default.IosShare,
        title = "三种导出格式分别会得到什么"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportFileTypeRow(
            extBadge = ".zip",
            title = "标准压缩包（音频文件夹 + 配对 TextGrid + 原生 Excel 工作簿）",
            desc = "解压后包含规范命名的无损 .wav 音频（默认 SPK01_001_字词.wav，可在设置中按需开启附带音标后缀）、同名 .TextGrid 标注、原生 Excel 工作簿 acoustic_summary.xlsx，并可按需勾选包含词卡配图与 audio/takes/ 历史重录遍数。"
        )
        ExportFileTypeRow(
            extBadge = ".Collection",
            title = "Praat 工程文件（在 Praat 里一次打开全部词条）",
            desc = "把所有录好的声音、配对的 TextGrid 标注和汇总表装进单独一个文件。在电脑 Praat 软件里点 Read from file... 打开它，全部词条就会成对出现在列表里，不用一个个拖文件。日常选默认的「二进制格式」体积更小、打开更快；需要用外部脚本读纯文本时可选「UTF-8 文本格式」。"
        )
        ExportFileTypeRow(
            extBadge = ".teproj",
            title = "PhonTracer 工程包（直接进 PhonTracer 微调边界）",
            desc = "把全套音频以及每个词自动切好的音节起止边界、音标和注释打包在一起，在电脑上用 PhonTracer 直接打开就能继续微调边界和做后续统计。"
        )
    }

    ManualQaCard(
        icon = Icons.Default.AccountTree,
        question = "导出的 `.TextGrid` 包含哪几层？如何修改层名与静音符号？",
        summaryNote = "前往「设置 → TextGrid 标注」选择预设模板，或开启自定义层级",
        points = listOf(
            "**默认模板（通用三层）**：自动生成 `words`（词条）、`syllables`（自动切分音节）和 `tones`（调值）三层，首尾静音段留空，可直接适配主流 Praat 提取脚本。",
            "**预设模板一键切换**：内置 `words + phones` 双层（适合强制对齐 MFA）、汉语方言声韵调四层、ProsodyPro 单层等 6 套模板，点击切换即可在下方实时预览文件内容。",
            "**自定义层级与格式**：选择「自定义层级与格式」后，可自由勾选所需层级、修改中英文层名、设置首尾静音符号（留空或 `sil` / `sp`），以及选择 Praat 长格式或精简短格式。"
        )
    )
}

@Composable
private fun AdvChapter08SettingsCleanupAndOfflineContent(isDesktop: Boolean) {
    ManualQaCard(
        icon = Icons.Default.Tune,
        question = if (isDesktop) {
            "如何防止误触覆盖已有录音，或直接查看电脑本地音频目录？"
        } else {
            "如何防止误触覆盖已有录音，或按发音人清理测试数据？"
        },
        summaryNote = if (isDesktop) {
            "开启重录确认防覆盖，点击顶部「本地语料目录」直达音频文件夹"
        } else {
            "开启重录确认防覆盖，支持按发音人单独清理录音与缓存"
        },
        points = listOf(
            "**重录前确认保护**：前往「[[settings|设置]] → **录音与操控**」开启 **重录前弹窗确认**，对已录词条再次触发录音时会先弹窗询问，避免误触覆盖。",
            if (isDesktop) {
                "**直达本地语料目录**：点击窗口顶部工具栏右侧的 [[folder|本地语料目录]]，即可在资源管理器中直接打开存放原始 `.wav` 音频的文件夹。"
            } else {
                "**分类设置与手势返回**：打开 [[settings|设置]] 进入对应分类即可调整参数，向内滑动返回手势会先退回设置主菜单。"
            },
            if (isDesktop) {
                "**按人清理与清缓存**：点击左侧导航栏 [[pie_chart|统计]]（或项目列表顶部统计卡片），可单独清空某位发音人的录音或删除旧项目；前往「设置 → 导出与存储」点击 [[delete|清缓存]] 可清理打包临时文件。"
            } else {
                "**按人清理与清缓存**：点击首页顶部统计卡片（或侧栏菜单 [[pie_chart|录音统计与清理]]），可单独清空某位发音人的录音或删除旧项目；前往「设置 → 导出与存储」点击 [[delete|清缓存]] 可清理打包临时文件。"
            }
        )
    )

    ManualQaCard(
        icon = Icons.Default.CloudOff,
        question = "在无网络信号的田野调查点，声学分析与导出能否正常使用？",
        summaryNote = "全流程本地运算与存储，无需连接互联网",
        points = listOf(
            "从字表导入、无损录音、多音节边界估算、语图与声调元音格局绘制，到导出 `.zip` / `.Collection` / `.teproj` 工程（以及局域网/手机热点下的无线互联），**均在设备本地离线完成**，语料不会上传至任何外部服务器。"
        )
    )
}

@Composable
private fun ShortcutInlinePair(
    keyLabel: String,
    actionDesc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        KeycapBadge(keyLabel)
        Text(
            text = actionDesc,
            style = TextStyle(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
