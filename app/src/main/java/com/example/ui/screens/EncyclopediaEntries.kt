package com.example.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
enum class EncyclopediaVolume(
    val shortTitle: String,
    val fullTitle: String,
    val subtitle: String,
    val icon: ImageVector
) {
    FIELDWORK(
        shortTitle = "田野调查",
        fullTitle = "第一卷 · 方言与田野调查方法",
        subtitle = "合作人遴选、列表语调控制、载体句、文白异读与看图引出法",
        icon = Icons.Default.Groups
    ),
    PHONETICS(
        shortTitle = "语音学基础",
        fullTitle = "第二卷 · 语音学与音系学概念",
        subtitle = "五度标调、舒促声韵、元音动程、清浊送气、发声态、擦音与国际音标",
        icon = Icons.AutoMirrored.Filled.VolumeUp
    ),
    VISUAL_CHARTS(
        shortTitle = "声学图谱",
        fullTitle = "第三卷 · 声学图谱与参数判读",
        subtitle = "波形削波、基频微扰、宽窄带语图谱、共振峰元音图、归一化与声韵切分",
        icon = Icons.Default.GraphicEq
    ),
    AUDIO_GEAR(
        shortTitle = "采录声学",
        fullTitle = "第四卷 · 采录设备与声学保真",
        subtitle = "降噪算法危害、蓝牙窄带频宽限制、偏轴防喷摆位与无损采样规范",
        icon = Icons.Default.MicOff
    ),
    COMPUTER_FILES(
        shortTitle = "文件与归档",
        fullTitle = "第五卷 · 语料归档与文件格式",
        subtitle = "压缩包解压流程、wav 与 TextGrid 同名配对、Collection 工程与字符编码",
        icon = Icons.Default.FolderZip
    )
}

@Immutable
enum class EncyclopediaDiagramId(val svgFileName: String) {
    ENC_01_SPEAKER_COHORT_COMPARE("enc_01_speaker_cohort_compare.svg"),
    ENC_01_SING_SONG_SHUFFLE("enc_01_sing_song_shuffle.svg"),
    ENC_02_CARRIER_SENTENCE_FRAME("enc_02_carrier_sentence_frame.svg"),
    ENC_03_WEN_BAI_LAYERS("enc_03_wen_bai_layers.svg"),
    ENC_04_TONE_SANDHI_CHAIN("enc_04_tone_sandhi_chain.svg"),
    ENC_06_CLEAN_PROMPT_CARD("enc_06_clean_prompt_card.svg"),
    ENC_25_PICTURE_ELICITATION_BYPASS("enc_25_picture_elicitation_bypass.svg"),
    ENC_05_CHAO_FIVE_PITCH_SCALE("enc_05_chao_five_pitch_scale.svg"),
    ENC_06_CHECKED_VS_SMOOTH_TONE("enc_06_checked_vs_smooth_tone.svg"),
    ENC_07_VOWEL_MONOPHTHONG_GLIDE("enc_07_vowel_monophthong_glide.svg"),
    ENC_08_VOICING_AND_ASPIRATION("enc_08_voicing_and_aspiration.svg"),
    ENC_26_PHONATION_GLOTTAL_STATES("enc_26_phonation_glottal_states.svg"),
    ENC_27_FRICATIVE_COG_SPECTRA("enc_27_fricative_cog_spectra.svg"),
    ENC_11_IPA_NARROW_BROAD("enc_11_ipa_narrow_broad.svg"),
    ENC_09_WAVEFORM_CLIPPING_CUP("enc_09_waveform_clipping_cup.svg"),
    ENC_10_F0_EDGE_PERTURBATION("enc_10_f0_edge_perturbation.svg"),
    ENC_11_SPECTROGRAM_LANDSCAPE("enc_11_spectrogram_landscape.svg"),
    ENC_12_ORAL_CAVITY_TO_VOWEL_CHART("enc_12_oral_cavity_to_vowel_chart.svg"),
    ENC_13_VOCAL_TRACT_NORMALIZATION("enc_13_vocal_tract_normalization.svg"),
    ENC_28_COARTICULATION_BOUNDARIES("enc_28_coarticulation_boundaries.svg"),
    ENC_14_AI_DENOISE_ERASES_FRICATIVES("enc_14_ai_denoise_erases_fricatives.svg"),
    ENC_15_BLUETOOTH_NARROWBAND_CUT("enc_15_bluetooth_narrowband_cut.svg"),
    ENC_16_MIC_DISTANCE_ANGLE("enc_16_mic_distance_angle.svg"),
    ENC_20_LOSSLESS_WAV_VS_MP3("enc_20_lossless_wav_vs_mp3.svg"),
    ENC_17_ZIP_BOX_EXTRACT_FIRST("enc_17_zip_box_extract_first.svg"),
    ENC_18_WAV_TEXTGRID_COLLECTION_LAYERS("enc_18_wav_textgrid_collection_layers.svg"),
    ENC_23_CSV_UTF8_BOM_ENCODING("enc_23_csv_utf8_bom_encoding.svg"),
    ENC_24_WAV_TEXTGRID_STEM_PAIRING("enc_24_wav_textgrid_stem_pairing.svg")
}

@Immutable
data class EncyclopediaEntry(
    val id: String,
    val number: Int,
    val volume: EncyclopediaVolume,
    val title: String,
    val keywords: List<String>,
    val oneLineSummary: String,
    val paragraphs: List<String>,
    val diagramId: EncyclopediaDiagramId? = null,
    val diagramCaption: String? = null,
    val fieldworkTip: String,
    val relatedIds: List<String> = emptyList()
)

object EncyclopediaRepository {

    val allEntries: List<EncyclopediaEntry> = listOf(
        // ==================== 第一卷：方言与田野调查方法 ====================
        EncyclopediaEntry(
            id = "speaker_selection",
            number = 1,
            volume = EncyclopediaVolume.FIELDWORK,
            title = "发音合作人的遴选标准（老派与新派）",
            keywords = listOf("发音人", "合作人", "老派", "新派", "年龄", "牙齿", "漏风", "外居"),
            oneLineSummary = "优先选择在当地出生并成长、无长期外居史且发音器官条件良好的母语者。",
            paragraphs = listOf(
                "方言或民族语调查的第一步是寻找合适的发音合作人。合作人的语言经历与生理状态，直接决定了整套录音能否真实代表当地语音面貌。遴选时主要考察三个方面：",
                "**语言经历纯正**：合作人宜自幼在调查点长大，日常家庭与邻里交际均以当地母语为主，成年后没有长期在外地定居生活的经历，以免口音混入外地方言或普通话成分。",
                "**发音条件完好**：正式录音前可通过几分钟家常交谈留意其嗓音与牙齿状况。若门牙缺失较多，发平舌音、唇齿音（如 `[s]、[ts]、[f]`）时气流会从齿缝异常漏出，影响听辨与高频声学测量。",
                "**老派与新派对照**：年长合作人通常保留更传统的音系格局（称为 **老派**），青年合作人则常体现正在发生的语音演变（称为 **新派**，例如部分声母合流或入声舒化）。用同一份字表分别采录不同年龄层，就能清晰对比当地语音几十年的演变轨迹。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_01_SPEAKER_COHORT_COMPARE,
            diagramCaption = "同一调查点的新老派合作人对照，可清晰呈现传统音类的保持与合流演变轨迹",
            fieldworkTip = "新建发音人档案时，建议一并记下出生年份、具体乡镇村落及受教育程度，便于后期整理报告与跨代际对比。",
            relatedIds = listOf("wen_bai_reading", "tract_normalization", "picture_elicitation")
        ),

        EncyclopediaEntry(
            id = "sing_song_intonation",
            number = 2,
            volume = EncyclopediaVolume.FIELDWORK,
            title = "列表语调（“唱书调”）与词表乱序",
            keywords = listOf("唱书调", "列表语调", "乱序", "顺读", "吟诵", "套读", "打乱"),
            oneLineSummary = "连续顺读同声调例字容易形成机械的吟诵节拍，打乱词表顺序可还原自然的单字本调。",
            paragraphs = listOf(
                "就像平时一口气点名或背乘法表会不自觉带上固定腔调一样，发音人连续朗读几十个排列整齐的单字时，很容易陷入机械的节拍——要么每个字像喊口号一样前高后低，要么整串字带着起伏的吟诵感。语音学上称之为 **列表语调（List Intonation，俗称“唱书调”）**。",
                "传统方言调查字表大多按声调或韵部归类编排，同一声调的字往往十几个连在一起。顺着念下去最容易诱发“唱书调”，导致录下来的音高曲线混入了大量朗读腔调，掩盖了单字原本的声调走向。",
                "将词表顺序随机打乱，让不同声调、不同声韵母的字词交替出现，能自然打断顺读惯性，促使发音人每看一个词都重新在脑海里检索日常读法，从而读出真实的 **单字引证调（Citation Tone）**。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_01_SING_SONG_SHUFFLE,
            diagramCaption = "同调字集中顺读易叠加周期性的列表语调；随机穿插排列有助于保持自然的单字本调",
            fieldworkTip = "录音途中若听出发音人语速变机械、不同声调越念越像，可在录音台点亮「乱序」开关，立即打断惯性节律。",
            relatedIds = listOf("carrier_sentence", "chao_tone_letters", "hide_remark_bias")
        ),

        EncyclopediaEntry(
            id = "carrier_sentence",
            number = 3,
            volume = EncyclopediaVolume.FIELDWORK,
            title = "载体句（框架句）在声学实验中的作用",
            keywords = listOf("载体句", "框架句", "例句框", "我说这个词", "句末下倾", "塌调"),
            oneLineSummary = "把目标词放进固定短句中间朗读，可稳定气息与语速，避免单念时起音偏高或末尾塌调。",
            paragraphs = listOf(
                "人说话时有一条自然的呼吸规律：刚开口时肺部气压足，音高往往略微偏高；说到句号结尾时气息松弛，音高和音量都会自然往下滑落，语音学称为 **语调下倾（Declination）**。如果只孤立念一个单字，这个字既是句首又是句尾，高平调的后半段很容易因为气息松懈而掉下来。",
                "为了在声学实验中测得最稳定的数据，研究者常把目标词嵌进一句固定的日常短句中间——例如“我说 ___ 这个词”或“请把 ___ 念一遍”。这个固定句框就叫 **载体句（Carrier Sentence，又称框架句）**。目标词处在句子中段，气息平稳，前后固定的衬字还能作为衡量语速和音域高低的天然标尺。",
                "需要注意：在吴语、闽语等连读变调非常活跃的方言里，前后的衬字可能会引起目标词变调。设计载体句时，应挑选语法上松散的句式，或让发音人在目标词前后留出极轻微的自然停顿。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_02_CARRIER_SENTENCE_FRAME,
            diagramCaption = "孤立单念易受句末气息衰减影响；置于固定载体句中段可维持稳定的基频与时长基线",
            fieldworkTip = "做精细声学对比实验时，可在「设置 → 显示与提示」中开启载体句提示；若录制上千字的大字表，则常采用单字乱序以兼顾效率与合作人体力。",
            relatedIds = listOf("sing_song_intonation", "f0_perturbation", "tone_sandhi")
        ),

        EncyclopediaEntry(
            id = "wen_bai_reading",
            number = 4,
            volume = EncyclopediaVolume.FIELDWORK,
            title = "文白异读与历史语音层次",
            keywords = listOf("文白异读", "文读", "白读", "读书音", "口语音", "历史层次", "两读"),
            oneLineSummary = "同一个字在方言里并存日常口语音（白读）与读书书面音（文读），折射出不同时代的语音层次。",
            paragraphs = listOf(
                "在许多汉语方言中，同一个汉字在日常说话和念书读报时会有两套不同的读音，这就是 **文白异读**。例如“流血”与“心血”中的“血”，或“大人”与“大学”中的“大”，在不少南方方言里声韵母甚至声调都截然不同。",
                "**白读（口语音）** 通常是当地话更早传承下来的本土底层读音，多保留在身体部位、农事家务、亲属称谓和老地名等核心口语词里；**文读（读书音）** 则多来自历代权威官话或传统蒙学读书音，常用于书面词汇、成语、科技术语与新词中。",
                "当发音人犹豫说“这个字土话一种讲法、照字念又是另一种讲法”时，千万不要只挑一个了事——这两种读音都是真实宝贵的语言事实，应当分别录下并记明各自搭配的词语。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_03_WEN_BAI_LAYERS,
            diagramCaption = "同一汉字在日常核心口语词与书面合成词中常分化出白读与文读两套语音形式",
            fieldworkTip = "遇到有文白两读的字，可用日常口语词和书面词分别举例引导，并在录音台「随手记」中点击「白读」或「文读」快捷标签加以区分。",
            relatedIds = listOf("picture_elicitation", "hide_remark_bias", "ipa_basics")
        ),

        EncyclopediaEntry(
            id = "tone_sandhi",
            number = 5,
            volume = EncyclopediaVolume.FIELDWORK,
            title = "单字调与连读变调",
            keywords = listOf("连读变调", "单字调", "本调", "变调", "两字组", "前字变调", "广用式"),
            oneLineSummary = "字词连读成词时声调会按规则发生改变；完整描写方言声调需结合单字表与多字连读词表。",
            paragraphs = listOf(
                "一个字单独念出来的声调叫 **单字调（本调）**；当两个或更多字组合成词连着说时，声调往往会发生有规律的变化，称为 **连读变调（Tone Sandhi）**。最常见的例子就是普通话里两个上声字相连（如“老虎”），前一个 `214` 会自动变成升调 `35`。",
                "各地方言的连读变调机制千差万别：北方官话和闽南语大多是 **前字变调（右重型）**，后字保持本调而前字随着后字调类改变；上海、苏州等北部吴语则是典型的 **广用式变调（左重扩展型）**，整个双音节甚至三音节词的音高走向主要由第一个字的调类决定，后面的字失去原有独立调型，共同分担首字声调的延伸轨迹。",
                "因此，方言调查既要录单字表来摸清基本调类，也要录两字组词表来考察真实语流中的变调规律——有时单字调里已经合并消失的古声调，反而在连读变调中留下了清晰的区分痕迹。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_04_TONE_SANDHI_CHAIN,
            diagramCaption = "音节孤立单念呈现各自本调；进入双音节连读环境后按音系规则发生声调交替或扩展",
            fieldworkTip = "采录双音节或多音节词时，请提醒合作人像平时聊天一样自然连读成一个整词，不要一字一顿地拆成单字来念。",
            relatedIds = listOf("chao_tone_letters", "checked_entering_tone", "syllable_segmentation_coarticulation")
        ),

        EncyclopediaEntry(
            id = "hide_remark_bias",
            number = 6,
            volume = EncyclopediaVolume.FIELDWORK,
            title = "看读视觉干扰与纯净大字看读界面",
            keywords = listOf("大字模式", "藏起备注", "视觉诱导", "普通话干扰", "小眼睛", "看读", "无线互联"),
            oneLineSummary = "给发音人看读时隐藏音韵学术注记与普通话释义，可避免照字折算造成的文读偏向。",
            paragraphs = listOf(
                "调查者手头的工作词表通常写满了辅助信息，比如《广韵》音韵地位（如“宕开一平唐”）、拟测音标或普通话对译解释。",
                "如果把带有这些注释的屏幕直接递给发音人看，很容易产生两种干扰：一是发音人一看到普通话解释，脑子里先浮现普通话读音，不自觉地折算成带普通话色彩的“文读音”；二是满屏陌生术语容易让长辈紧张拘谨，误以为像考试一样有“标准答案”。",
                "单机采录时点亮录音台右上角的 **大字看读模式**（或使用 **无线互联** 将另一台平板/手机设为「看读端」），发音人面前就只呈现干净清晰的大号字词或图片，而分组、释义和记音栏只留在调查者端。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_06_CLEAN_PROMPT_CARD,
            diagramCaption = "纯净看读画面隐藏音韵地位与普通话释义，避免视觉注释诱导文读或折中读音",
            fieldworkTip = "双设备田野采录时，可在「无线互联」设置中将看读端切换为温和的「暖色宣纸」或「墨水屏」外观，并默认隐藏备注，仅在遇到多音字时由遥控端临时点亮单条提示。",
            relatedIds = listOf("picture_elicitation", "wen_bai_reading", "speaker_selection")
        ),

        EncyclopediaEntry(
            id = "picture_elicitation",
            number = 7,
            volume = EncyclopediaVolume.FIELDWORK,
            title = "看图引出法与“有音无字”词条采录",
            keywords = listOf("看图发音", "图片", "配图", "有音无字", "本字", "词表诱导", "训读", "无线互联"),
            oneLineSummary = "用实物图片直接唤起日常概念，可绕开汉字字面干扰，自然引出地道方言词与“有音无字”词。",
            paragraphs = listOf(
                "汉语方言和少数民族语言里有许多每天都在说、却写不出通用汉字的核心词（俗称 **“有音无字”** 或本字生僻），也有不少词与普通话说法完全不同——比如同一种昆虫，普通话写“蜻蜓”，西南官话叫“丁丁猫”，胶辽官话叫“蚂螂”，闽南语叫“田婴”。",
                "如果直接把写着“蜻蜓”或“膝盖”的文字卡拿给合作人看，识字的合作人往往会顺着汉字字面逐字念出新派文读音（即 **看字生训**），而真正的本土口语词反而被藏了起来；面对不识字或视力欠佳的老年合作人，纯文字词表更难以独立看读。",
                "**看图引出法（Picture Elicitation）** 通过展示清晰的实物或动作图片，让合作人越过文字中介，直接从视觉概念唤起母语里最地道的第一反应词，既能自然采得纯正白读与本土词汇，也能让调查过程更轻松直观。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_25_PICTURE_ELICITATION_BYPASS,
            diagramCaption = "直接看普通话词形易诱发按字面文读；实物配图可绕开文字干扰，直接引出地道本土词汇",
            fieldworkTip = "在「字表编辑器」中可为词条批量关联实物图片；配合「无线互联」的「配有图片时隐去文字（纯看图发音）」开关，即可让看读端只展示大图。",
            relatedIds = listOf("hide_remark_bias", "wen_bai_reading", "speaker_selection")
        ),

        // ==================== 第二卷：语音学与音系学概念 ====================
        EncyclopediaEntry(
            id = "chao_tone_letters",
            number = 8,
            volume = EncyclopediaVolume.PHONETICS,
            title = "五度标调法与个体相对音域",
            keywords = listOf("五度标调法", "五度值", "调值", "55", "35", "214", "51", "赵元任", "T值", "音域"),
            oneLineSummary = "把每个人自然说话的最低音到最高音按听感等分为 1 至 5 度，用来记录声调的相对高低走向。",
            paragraphs = listOf(
                "为什么身材高大的男士（平均嗓音约 `110 Hz`）和嗓音清亮的女士（平均约 `220 Hz`）说同一个高平调“妈”字，我们听起来觉得是同一个声调？因为人耳辨别声调，听的不是绝对频率高低，而是这个音在说话人 **自身正常嗓音范围（调域）** 里处于偏高还是偏低的位置。",
                "语言学家赵元任先生据此创制了通行的 **五度标调法**：把发音人自然说话的最低音定为 `1` 度，半低 `2` 度，适中 `3` 度，半高 `4` 度，最高音定为 `5` 度。顺着发音先后记下起点、拐点和终点的度数，就能直观写出调值——如普通话阴平高平调 `55`、阳平中升调 `35`、上声降升曲折调 `214`、去声全降调 `51`。",
                "由于人耳对音高的感知遵循对数规律（`100 Hz` 升到 `200 Hz` 与 `200 Hz` 升到 `400 Hz` 听起来同样升高八度），声学计算时需先对基频取对数，再按经典的 **T 值公式** `T = [(lg F0 - lg Fmin) / (lg Fmax - lg Fmin)] × 5` 映射到 0～5 区间，从而跨越男女嗓音差异实现客观定调。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_05_CHAO_FIVE_PITCH_SCALE,
            diagramCaption = "五度制衡量的是个体调域内的相对高低：无论男低音还是女高音，各自调域顶端的平调均记作 55",
            fieldworkTip = "每位新发音人录满包含高低声调的若干词条后，可在单字详情页点击「个人音域校准」，让五度参考线精准贴合该合作人的真实嗓音跨度。",
            relatedIds = listOf("checked_entering_tone", "f0_perturbation", "tone_sandhi")
        ),

        EncyclopediaEntry(
            id = "checked_entering_tone",
            number = 9,
            volume = EncyclopediaVolume.PHONETICS,
            title = "舒声与促声（入声塞尾与喉塞尾）",
            keywords = listOf("入声", "促声", "舒声", "塞音韵尾", "喉塞尾", "短调", "-p", "-t", "-k"),
            oneLineSummary = "元音或鼻音收尾、声音可自然拖长的叫舒声；以闭口塞尾或喉塞急促收住的短调叫促声（入声）。",
            paragraphs = listOf(
                "试着用普通话念“巴、麻、妈”，只要气息够用，韵母可以一直平缓地拖长下去——这类以元音或鼻音（`-m / -n / -ŋ`）收尾、发音舒展的声调统称 **舒声**。",
                "而在粤语、闽语、客家话、吴语、晋语、赣语及江淮官话等保留古入声的方言里，念“十、八、六、百”这类字时，声音一出来就像被突然刹住一样短促干脆，这就是 **促声（入声）**。它之所以无法拖长，是因为音节末尾带有一套“截流”动作，主要分两类：\n• **唯闭塞音韵尾 `[-p, -t, -k]`**（多见于粤、闽、客等方言）：收尾时双唇紧闭、舌尖抵齿龈或舌根抵软腭，只堵住气流而不重新张口爆破，声音瞬间截止。\n• **喉塞韵尾 `[-ʔ]`**（多见于吴语、晋语、江淮官话等）：收尾时喉部声门迅速关拢锁住气流，听感短促微紧。",
                "由于入声时长往往只有舒声的三分之一到一半，标调时只写一个数字表示短平调（如高入记 `[5]`、低入记 `[2]`），或用两个紧挨的数字表示短促升降（如 `[54]`、`[23]`）。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_06_CHECKED_VS_SMOOTH_TONE,
            diagramCaption = "舒声音节能量平缓延展；促声（入声）受塞音韵尾或喉塞闭合制约，波形包络短促截断",
            fieldworkTip = "分辨 `[-p, -t, -k]` 三套入声尾时，除观察合作人收尾是否闭唇外，还可借助语图谱末尾共振峰走向（如 `-p` 使 F1/F2 同降，`-k` 使 F2/F3 聚拢）客观印证。",
            relatedIds = listOf("chao_tone_letters", "phonation_types", "ai_denoise_harm")
        ),

        EncyclopediaEntry(
            id = "monophthong_diphthong",
            number = 10,
            volume = EncyclopediaVolume.PHONETICS,
            title = "单元音稳态与复元音动程",
            keywords = listOf("单元音", "复元音", "二合元音", "三合元音", "动程", "滑音", "韵腹", "韵尾", "箭头"),
            oneLineSummary = "单元音发音时舌位与口型保持不动；复元音在同一音节内连续滑动，声学上呈现带方向的轨迹。",
            paragraphs = listOf(
                "发“衣 `[i]`”或“阿 `[a]`”时，从开口到收尾，舌头高低前后与嘴唇圆展基本保持在同一个位置不动，这叫 **单元音（Monophthong）**。在语图谱上，它的共振峰横杠是平直稳定的；在元音图上，它表现为一个集中的圆点。",
                "发“爱 `[ai]`”或“腰 `[iau]`”时，虽然听起来仍是一个音节，但口腔和舌头其实在做连贯的滑行运动——比如 `[ai]` 从大开口的低元音一路滑向高前元音。这种在一个音节内部连续过渡的元音叫 **复元音（Diphthong / Triphthong）**，舌位与共振峰滑行的路径和幅度就叫 **元音动程**。",
                "正因如此，分析复元音绝不能只取整段时间的平均值（否则 `[ai]` 的起点和终点一平均，就会被误算成中间的 `[ə]`），而必须按时间先后分段采样，用 **有向曲线箭头** 画出它从哪里出发、滑向哪里。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_07_VOWEL_MONOPHTHONG_GLIDE,
            diagramCaption = "单元音在 F1-F2 平面内聚为稳态靶点；复元音表现为由起点滑向终点的有向动程箭头",
            fieldworkTip = "不少汉语方言会把古复元音读成单元音（如苏州话、太原话把“来、菜”的韵母读成纯单元音 `[E]` 或 `[ɛ]`），在单字详情页查看动程箭头长短即可一目了然。",
            relatedIds = listOf("formant_vowel_chart", "spectrogram_basics", "syllable_segmentation_coarticulation")
        ),

        EncyclopediaEntry(
            id = "voicing_aspiration",
            number = 11,
            volume = EncyclopediaVolume.PHONETICS,
            title = "清浊对立与送气特征（VOT 嗓音起始时间）",
            keywords = listOf("清音", "浊音", "送气", "不送气", "全浊", "VOT", "声带振动", "吴语", "湘语"),
            oneLineSummary = "“巴、怕、爬（方言浊音）”的区别，关键在于嘴唇张开瞬间与声带开始振动之间的时间差（VOT）。",
            paragraphs = listOf(
                "许多初学者容易把普通话拼音 `b、d、g`（如“巴 `[pa]`”）误当成英语或吴语里的“浊音 `[b, d, g]`”。其实普通话的 `b` 和 `p` 都是声带不提前振动的 **清塞音**，区别仅在于送不送气。实验语音学用一个极直观的秒表指标 **VOT（Voice Onset Time，嗓音起始时间）** 把三者分得清清楚楚——以双唇张开（除阻）那一刻为 `0 毫秒`，看声带什么时候开始振动：",
                "• **真浊塞音 `[b, d, g]`（负 VOT）**：双唇还没张开、嘴巴憋气闭塞期间，喉咙里的声带就已经嗡嗡振动了（语图谱底部在爆破前有一条低频黑带，叫 **浊音横杠**），常见于老湘语、部分吴语及法语、俄语。\n• **清不送气音 `[p, t, k]`（零或短正 VOT）**：双唇张开的同时或十多毫秒内，声带紧跟着立刻起振，几乎没有喷气空隙（如普通话“巴 `[pa]`”）。\n• **清送气音 `[pʰ, tʰ, kʰ]`（长正 VOT）**：双唇张开后声门仍敞开，先喷出五六十毫秒以上的强气流，随后声带才起振进入元音（如普通话“怕 `[pʰa]`”）。",
                "补充：上海、苏州等北部吴语单字开头的所谓“浊声母”（如“爬”），单念时声带往往并不提前振动，而是伴随柔和的 **清音浊流（弛声/气声 `[pɦ]`）** 并搭配低声调；只有夹在词语中间连读时，才会变成声带持续振动的真浊音。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_08_VOICING_AND_ASPIRATION,
            diagramCaption = "声带起振早于、同步于或明显晚于辅音除阻，分别对应真浊音、清不送气音与清送气音",
            fieldworkTip = "不确定合作人发的是真浊音还是清不送气音时，除了把手轻触喉结感受闭唇时有无振动，打开单字宽带语图谱查看除阻竖线前有无低频浊音横杠最为准确。",
            relatedIds = listOf("phonation_types", "mic_plosive_distance", "ipa_basics")
        ),

        EncyclopediaEntry(
            id = "phonation_types",
            number = 12,
            volume = EncyclopediaVolume.PHONETICS,
            title = "发声态：常态浊音、气声与嘎裂声（紧喉）",
            keywords = listOf("发声态", "气声", "弛声", "嘎裂声", "气泡音", "紧喉", "松紧元音", "清音浊流"),
            oneLineSummary = "除音高与音色外，声带松紧与闭合方式还会产生常态、气声、嘎裂声（紧喉）等辨义发声态。",
            paragraphs = listOf(
                "同样是声带振动发出的元音，如果调节喉部肌肉的松紧程度，听感质地会大不相同。语音学把声带不同的振动模式称为 **发声态（Phonation Types）**，最核心的有三种：",
                "• **常态浊音（Modal Voice）**：声带松紧适中，每一次开合都干脆利落、周期均匀，声音清亮饱满，是大多数语言最基础的嗓音状态。\n• **气声 / 弛声（Breathy / Slack Voice，记作 `[a̤]`）**：声带比较松弛，闭合时后部留有一道小缝隙，一边振动一边漏出柔和的气流，听起来像带叹息感。吴语阳调类的“清音浊流”、苗瑶语及部分藏缅语的松元音都具有典型的气声特征（频谱上第一谐波相对更强，即 `H1 - H2` 偏高）。\n• **嘎裂声 / 紧喉（Creaky / Tense Voice，记作 `[a̰]`）**：声带绷紧挤压，只有前段断续慢速地挤开气泡般的低沉脉冲（类似清晨刚醒时的“气泡音”）。普通话上声 `214` 的谷底、不少南方方言的入声或曲折调中段，以及彝缅语支的 **紧元音** 常伴随明显的紧喉或嘎裂化。",
                "了解发声态对看懂声学图表非常关键：当发音转入嘎裂声时，声带每两次脉冲常常一强一弱黏连在一起，算法测出的基频 `F0` 会突然掉成原来的一半（八度跳降）甚至断开——这并非录音坏了，恰恰是紧喉/嘎裂发声态的真实声学印记。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_26_PHONATION_GLOTTAL_STATES,
            diagramCaption = "常态浊音脉冲均匀；气声声门留隙伴随湍流；嘎裂声声门紧缩呈低频不规则双脉冲",
            fieldworkTip = "采录有松紧元音对立或气声声母的方言/民族语时，严禁开启任何降噪功能，并在「随手记」中结合听感记录气声或紧喉特征。",
            relatedIds = listOf("voicing_aspiration", "f0_perturbation", "checked_entering_tone")
        ),

        EncyclopediaEntry(
            id = "fricative_affricate_spectra",
            number = 13,
            volume = EncyclopediaVolume.PHONETICS,
            title = "擦音与塞擦音：平翘舌与舌面音的声学区分",
            keywords = listOf("擦音", "塞擦音", "平翘舌", "舌尖前", "卷舌", "舌面音", "频谱重心", "CoG", "尖团"),
            oneLineSummary = "擦音靠狭窄缝隙摩擦出声，塞擦音是“先堵后擦”；平舌、翘舌与舌面音可通过高频噪声重心高低清晰区分。",
            paragraphs = listOf(
                "把舌头靠近上齿龈或硬腭留出一条窄缝，让气流高速挤过去产生“嘶嘶、师师、西西”的摩擦声，就是 **擦音（Fricative，如 `[s, ʂ, ɕ]`）**；如果舌头先把气流完全堵死一瞬，紧接着松开一条窄缝摩擦出去（即“塞音 + 同部位擦音”合为一体），就是 **塞擦音（Affricate，如 `[ts, tʂ, tɕ]`，对应拼音 `z/c、zh/ch、j/q`）**。",
                "汉语方言调查经常要分辨平翘舌（如“四 `[s]`”与“十 `[ʂ]`”）或尖团音（如“精 `[ts]`”与“经 `[tɕ]`”）。它们的声学秘密藏在摩擦窄缝前方的 **前腔空隙大小** 里——就像小哨子声音尖、大哨子声音低沉一样，摩擦点前方的空腔越小，噪声集中的频率（称为 **频谱重心 Center of Gravity, CoG**）就越高：",
                "• **舌尖前音（平舌 `[s, ts]`）**：舌尖直抵上齿背，前面几乎没有空腔，摩擦声最高亢尖锐，能量集中在 **`6000～9000 Hz`**。\n• **舌尖后/卷舌音（翘舌 `[ʂ, tʂ]`）**：舌尖后缩翘起，舌下与齿后形成较大的共鸣空腔，摩擦声浑厚偏低，能量集中在 **`2500～4500 Hz`**。\n• **舌面前音（`[ɕ, tɕ]`）**：舌面抬向硬腭形成狭长通道，摩擦能量居中偏高（约 **`4000～6500 Hz`**），且紧接其后的元音起首 `F2` 会被明显拉高。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_27_FRICATIVE_COG_SPECTRA,
            diagramCaption = "平舌 [s] 频谱重心最高，翘舌 [ʂ] 重心最低，舌面 [ɕ] 居中且伴随元音高 F2 过渡",
            fieldworkTip = "正因为平舌 `[s]` 的关键特征高达 6～9 kHz，一旦使用蓝牙耳机麦克风（4 kHz 截断），平翘舌与尖团音在声学图上就会彻底混同，因此务必使用全频段麦克风采录。",
            relatedIds = listOf("bluetooth_mic_cut", "ai_denoise_harm", "spectrogram_basics")
        ),

        EncyclopediaEntry(
            id = "ipa_basics",
            number = 14,
            volume = EncyclopediaVolume.PHONETICS,
            title = "国际音标（IPA）与宽式、严式记音",
            keywords = listOf("国际音标", "IPA", "拼音", "方括号", "斜杠", "宽式", "严式", "音位"),
            oneLineSummary = "国际音标遵循“一音一符”，记音时用方括号 `[ ]` 如实描写具体读音（严式），用斜杠 `/ /` 归纳音系音位（宽式）。",
            paragraphs = listOf(
                "为什么调查方言不能直接用汉语拼音记音？因为汉语拼音是为普通话量身定制的拼写工具，为了省字母合并了许多不同的音——比如拼音字母 `i` 在 `yi（衣）`、`zi（资）`、`zhi（知）` 里其实代表三个完全不同的元音，而且方言里大量的浊辅音、鼻化韵、入声塞尾在拼音里根本没有对应符号。**国际音标（IPA）** 则严格遵循 **“一音一符、一符一音”**（汉语方言学还常沿用赵元任补充的舌尖元音 `ɿ、ʅ` 等符号），全世界学者看到同一个音标就能准确还原读音。",
                "实际记音分两个步骤与层次，并用不同的括号严格区分：\n• **严式记音（放在方括号 `[ ]` 内）**：“听到什么细微差别就如实记什么”，包括轻微鼻化、喉塞尾、声调具体度数等。田野调查第一现场应当先用严式记音忠实留存细节。\n• **宽式记音（放在斜杠 `/ /` 内）**：等全部字表录完、摸清哪些差别能区分词义（音位对立）、哪些只是受前后音影响自动产生的变体后，把非辨义细节精简掉，归纳出整洁的音系表。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_11_IPA_NARROW_BROAD,
            diagramCaption = "汉语拼音常合并条件变体；国际音标严格区分实际音值（方括号严式）与音系对立（斜杠宽式）",
            fieldworkTip = "录音台内置了方言常用国际音标快捷栏，现场拿不准如何音位归并时，先用方括号严式记下实际听感或打上标签，回室内结合录音库反复比对后再做归纳。",
            relatedIds = listOf("voicing_aspiration", "fricative_affricate_spectra", "wen_bai_reading")
        ),

        // ==================== 第三卷：声学图谱与参数判读 ====================
        EncyclopediaEntry(
            id = "waveform_clipping",
            number = 15,
            volume = EncyclopediaVolume.VISUAL_CHARTS,
            title = "时域波形与削波失真（Clipping）",
            keywords = listOf("波形", "爆音", "削波", "破音", "过响", "信噪比", "电平", "红条"),
            oneLineSummary = "录音音量超过数字上限 `0 dBFS` 会把波峰硬生生削平，产生无法修复的破音与虚假高频。",
            paragraphs = listOf(
                "录音台上的波形图记录的是空气声压随时间的起伏。数字录音有一个绝对的天花板，叫做 **`0 dBFS`（满刻度分贝）**：声音越轻，分贝负数越小（如安静房间约 `-50 dB`）；声音越响，越逼近 `0 dB`。",
                "如果合作人靠麦克风太近或突然大声念开口字，声波冲过了 `0 dBFS` 天花板，超出部分的圆润波峰和波谷就会像撞上平顶一样被硬生生削成直线——这就是 **削波失真（Clipping，俗称爆音/破音）**。一旦发生削波，不仅听起来刺耳失真，还会在频谱上凭空制造出大量假的高频条纹，严重干扰共振峰和擦音测量，而且后期任何软件都无法真正修好。",
                "反过来声音也不能太小，否则放大音量时环境底噪也会跟着变响。最理想的录音音量是让正常说话峰值落在 **`-18 dBFS` 到 `-6 dBFS`** 之间（对应录音台电平条的绿色舒适区），既清晰干净，又留足了防爆音的安全余量。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_09_WAVEFORM_CLIPPING_CUP,
            diagramCaption = "处于安全余量内的波形包络圆润完整；过载冲顶会发生硬削波，造成不可逆的频谱失真",
            fieldworkTip = "正式开录前可先进入「试音」页面，请合作人按自然音量读一句测试语，确认不触发红色过载提示后再开始录字表。",
            relatedIds = listOf("mic_plosive_distance", "lossless_wav_samplerate", "spectrogram_basics")
        ),

        EncyclopediaEntry(
            id = "f0_perturbation",
            number = 16,
            volume = EncyclopediaVolume.VISUAL_CHARTS,
            title = "基频（F0）曲线与声母微扰（弯头与降尾）",
            keywords = listOf("基频", "F0", "声调曲线", "弯头", "降尾", "声母微扰", "嘎裂声", "韵母测量窗"),
            oneLineSummary = "声调曲线开头和结尾常有辅音与气息带来的短暂小钩，定调时应取中间稳定的韵母主体段。",
            paragraphs = listOf(
                "**基频（F0）** 就是声带每秒钟振动的次数（单位 Hz），它在图上画出的轨迹就是我们看到的 **音高（声调）曲线**。刚接触声学图的同学常会疑惑：明明耳朵听起来是很平的阴平调 `55`，为什么曲线最开头总往下钩一点，或者最末尾往下掉一点？",
                "其实人耳在听声调时会自动过滤掉头尾极短暂的生理惯性，而精密仪器却会如实画出来，这叫 **声母微扰（弯头）** 与 **降尾**：\n• **起首“弯头”**：发清塞音、清擦音（如 `p、t、s`）时声门张力大、气流急，刚进入元音的前 20～40 毫秒音高往往会被短暂带高随后回落；而在真浊辅音后起首音高则常略微偏低。\n• **末尾“降尾”**：音节快收尾时气息自然减弱，或者转入低沉的气泡音（嘎裂声），音高曲线末端就会往下滑落甚至跳变。",
                "因此，判断一个字的真实调型，要“掐头去尾”看中间最平稳的 **韵母携调段**，不要被头尾二三十毫秒的生理小弯钩误导。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_10_F0_EDGE_PERTURBATION,
            diagramCaption = "清辅音除阻易引起起首弯头，末尾气弱或嘎裂易引起降尾；声调提取应聚焦中间稳定的携调段",
            fieldworkTip = "PhonAcq 单字详情页中的「韵母测量窗」已自动剔除起首辅音微扰与末尾弱能量段，直接提取核心稳定区间计算五度调值。",
            relatedIds = listOf("chao_tone_letters", "phonation_types", "voicing_aspiration")
        ),

        EncyclopediaEntry(
            id = "spectrogram_basics",
            number = 17,
            volume = EncyclopediaVolume.VISUAL_CHARTS,
            title = "宽带语图谱（Spectrogram）的声学判读",
            keywords = listOf("语图谱", "声谱图", "宽带语图谱", "窄带语图谱", "冲直条", "乱纹", "横杠", "共振峰"),
            oneLineSummary = "横轴看时间、纵轴看频率高低、颜色深浅看能量强弱，一张图看清辅音爆破、摩擦与元音共振峰。",
            paragraphs = listOf(
                "如果说普通波形图只能看出声音“什么时候响、什么时候轻”，那么 **语图谱（Spectrogram，又称声谱图）** 就像是给声音拍了一张彩色 X 光片：从左到右是时间推进，从下到上是频率由低到高，颜色越深亮代表那个频段的声音能量越强。",
                "语图谱分两种常用视图：**窄带语图谱**（窗长约 30 ms）频率看得很细，能看清一条条平行的声带谐波细横线，适合观察音高起伏；而语音切分与元音分析最常用的是时间反应极快的 **宽带语图谱**（窗长约 5 ms），不同音类在上面有非常鲜明的“长相”：\n• **空白段 + 冲直条**：塞音（`p, t, k`）憋气闭塞时是一小段空白，张口爆破瞬间会出现一条上下贯通的细竖线（冲直条）。\n• **高频乱纹**：擦音（`s, sh, x`）在上方中高频区呈现一片像沙砾或浓雾一样的密集斑点。\n• **共振峰深色横杠**：进入元音后，画面上会浮现三四条粗壮清晰的深色水平宽带，从下往上依次就是决定元音音色的第一、第二、第三共振峰（`F1、F2、F3`）。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_11_SPECTROGRAM_LANDSCAPE,
            diagramCaption = "宽带语图谱中，前段高频沙状乱纹对应擦音声母，后段水平深色宽带对应元音各阶共振峰",
            fieldworkTip = "在单字详情页的宽带语图谱上，彩色圆点标出了软件自动追踪的 F1/F2 轨迹；对照底图深色横杠的位置，就能直观检验共振峰读数是否准确。",
            relatedIds = listOf("formant_vowel_chart", "syllable_segmentation_coarticulation", "bluetooth_mic_cut")
        ),

        EncyclopediaEntry(
            id = "formant_vowel_chart",
            number = 18,
            volume = EncyclopediaVolume.VISUAL_CHARTS,
            title = "共振峰（F1、F2）与声学元音四边形",
            keywords = listOf("共振峰", "F1", "F2", "F3", "元音图", "舌位", "圆唇", "Bark"),
            oneLineSummary = "口腔形状像可调节的共鸣箱：嘴巴越张大 F1 越高，舌头越往前伸 F2 越高；倒转坐标轴即对应舌位图。",
            paragraphs = listOf(
                "为什么同样是声带振动，把嘴张大发“阿 `[a]`”和把嘴扁平发“衣 `[i]`”听起来完全不同？我们可以把喉咙和口腔想象成一只软体共鸣箱：声带发出原始嗡鸣声后，舌头的高低前后和嘴唇的圆展改变了口腔空腔的大小，从而放大了特定频率的泛音——这些被重点放大的频率高峰就叫 **共振峰（Formants）**。",
                "决定元音身份的主要是前两个共振峰，规律非常直观：\n• **`F1` 对应开口度 / 舌位高低（反比）**：嘴巴张得越大、舌位越低（如 `[a]`），`F1` 数值越高（可达 `700～1000 Hz`）；舌位越高、嘴巴越合拢（如 `[i, u]`），`F1` 数值越低（约 `250～350 Hz`）。\n• **`F2` 对应舌位前后与圆唇（正比）**：舌头越往牙齿前部推（如 `[i]`），前口腔变小，`F2` 越高（可达 `2200 Hz` 以上）；舌头越往后缩或把嘴唇拢圆延长声道（如 `[u]`，圆唇会显著拉低 `F2`），`F2` 就越低（可低至 `700～900 Hz`）。",
                "语音学家做了一个巧妙的坐标设计：把纵轴 `F1` **从上往下递增**，把横轴 `F2` **从右往左递增**（并常按人耳听感换算为 Bark 标度）。这样画出来的声学散点图，就和一个人面朝左侧时的口腔舌位图（左上角高前 `[i]`、右上角高后 `[u]`、最下方低元音 `[a]`）严丝合缝地重合了！"
            ),
            diagramId = EncyclopediaDiagramId.ENC_12_ORAL_CAVITY_TO_VOWEL_CHART,
            diagramCaption = "将 F1 纵轴向下、F2 横轴向左逆置后，声学共振峰散点空间与人体口腔舌位高低前后一一对应",
            fieldworkTip = "打开录音库的「声学格局」视图，点击元音图上的任意点或箭头即可当场回放该字原声，把耳朵听到的音色与舌位高低前后直观对应起来。",
            relatedIds = listOf("monophthong_diphthong", "tract_normalization", "spectrogram_basics")
        ),

        EncyclopediaEntry(
            id = "tract_normalization",
            number = 19,
            volume = EncyclopediaVolume.VISUAL_CHARTS,
            title = "多发音人对比与声道归一化（Normalization）",
            keywords = listOf("归一化", "声道归一化", "多人对比", "男女差异", "声道长度", "Lobanov", "Z-score"),
            oneLineSummary = "消除男女老少喉管与口腔长短带来的频率整体偏移，让不同人的元音格局可以公平叠图对比。",
            paragraphs = listOf(
                "管乐器管身越短，吹出来的共鸣频率就越高；人的声道也是如此。成年男性的声道（从声门到嘴唇）平均长约 `17 厘米`，成年女性平均约 `14 厘米`，儿童则更短。因此，哪怕一男一女用完全地道的同一种方言发同一个元音，女性测出来的 `F1、F2` 赫兹数值也会比男性整体高出一截。",
                "如果直接把爷爷（老派男）和孙女（新派女）的原始赫兹数值画在同一张元音图上，两人的元音圈子会一左一右错开一大块——但这主要是生理体型差异，并不是方言发生了巨变。",
                "**声道归一化（Vocal Tract Normalization，常用 Lobanov Z-score 法）** 就像把不同尺码的脚印按各自全掌比例缩放到同一张标准网格纸上：先算出每个人自己全套元音的中心点和跨度，再换算成相对位置。经过归一化对齐后，男女体型差异被干净剥离，真正属于方言新老派的音变差异立刻清晰显现。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_13_VOCAL_TRACT_NORMALIZATION,
            diagramCaption = "归一化前男女元音空间因声道长短差异整体错位；归一化后中心对齐，真实显现方言音系异同",
            fieldworkTip = "要做准确的多人元音归一化对比，每位合作人应尽量录齐包含高前 `[i]`、高后 `[u]` 和低元音 `[a]` 三个顶角在内的代表字（建议不少于 10 个词条）。",
            relatedIds = listOf("formant_vowel_chart", "speaker_selection", "chao_tone_letters")
        ),

        EncyclopediaEntry(
            id = "syllable_segmentation_coarticulation",
            number = 20,
            volume = EncyclopediaVolume.VISUAL_CHARTS,
            title = "语流协同发音与声韵切分边界原则",
            keywords = listOf("切分", "边界", "协同发音", "声韵切分", "过渡段", "音征", "TextGrid", "对齐"),
            oneLineSummary = "自然说话时前后音素会平滑衔接着发；在 Praat 中切分边界需依据脉冲起点与频谱突变等统一声学标志。",
            paragraphs = listOf(
                "初学 Praat 标注时最常遇到的困惑是：“人说话不是一个音素接一个音素像串珠子那样分开的吗？为什么语图谱上声母和韵母连成一片，边界到底该拉在哪里？”这是因为人在说话时，发前一个辅音的同时舌头已经在提前向后一个元音的位置移动了，这种前后音相互渗透的现象叫做 **协同发音（Coarticulation）**。",
                "为了让不同研究者的测量结果可重复、可对比，实验语音学制定了一套清晰的 **声学地标切分惯例**：\n• **塞音 / 送气音进入元音的边界**：不切在爆破冲直条上，而是切在送气噪声结束、语图谱底部 **第一个规则声带脉冲与清晰 `F1` 横杠同时出现** 的过零点处（这样前一段长度正好就是标准 VOT）。\n• **擦音进入元音的边界**：切在上方高频密集摩擦乱纹突然消失、下方规则元音共振峰正式成型的交界处。\n• **元音进入鼻音韵尾（`-n / -ŋ`）的边界**：鼻腔通道打开瞬间，口腔能量被分流，语图谱上表现为整体颜色骤然变淡、`F1` 以上共振峰突然减弱并出现低频鼻音横杠，此处即为鼻尾起点。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_28_COARTICULATION_BOUNDARIES,
            diagramCaption = "依据爆破冲直条、首个规则声门脉冲及鼻音能量骤降点，可在连续语流中确立可重复的标注边界",
            fieldworkTip = "PhonAcq 导出 `.TextGrid` 与 `.Collection` 时已利用能量包络与基频/频谱特征预置了音节与声韵边界，导入 Praat 后只需按上述声学地标微调抽检即可，大幅节省人工拉线时间。",
            relatedIds = listOf("spectrogram_basics", "voicing_aspiration", "file_types_guide")
        ),

        // ==================== 第四卷：采录设备与声学保真 ====================
        EncyclopediaEntry(
            id = "ai_denoise_harm",
            number = 21,
            volume = EncyclopediaVolume.AUDIO_GEAR,
            title = "为什么语音采录严禁开启通话降噪或 AI 降噪？",
            keywords = listOf("降噪", "AI降噪", "通话降噪", "语音增强", "擦音", "失真", "谱减法", "ENC"),
            oneLineSummary = "通话与 AI 降噪只追求日常听清字义，会把擦音、送气声和入声短尾当成背景杂音一并抹除。",
            paragraphs = listOf(
                "平时打微信语音、开视频会议或拍短视频时，“AI 一键降噪”能把马路车流声滤得很干净。但对于方言归档和语音学分析来说，开启任何软件或硬件降噪都是大忌。",
                "原因在于物理声学上，人类发出的许多关键辅音——比如平翘舌擦音（`[s, ʂ, ɕ, f, x]`）、送气音喷出的气流（`[pʰ, tʰ, kʰ]`）以及微弱的轻声和入声喉塞尾——本质上就是 **不规则的高频气流摩擦声**，在算法眼里和风扇声、空调声长得非常像。",
                "一旦经过实时降噪或后期强降噪处理，这些辅音的开头和摩擦频谱就会被算法当成“杂音”削掉一大截，元音内部也会留下像水波纹一样的假信号（称为“音乐噪声”）。因此，科学采录必须保留 **原汁原味的未加工无损波形**，靠关好门窗、关掉风扇从源头获得安静环境。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_14_AI_DENOISE_ERASES_FRICATIVES,
            diagramCaption = "实时或后期降噪算法极易将首尾关键的清擦音噪波与弱塞尾误判为环境底噪予以抹除",
            fieldworkTip = "若外接领夹麦或 USB 麦克风，请先检查麦克风机身上的「降噪 / ENC」物理开关是否已关掉（切换至原声模式），并在「设置 → 录音与采录」中保持「原声直通」优先。",
            relatedIds = listOf("bluetooth_mic_cut", "fricative_affricate_spectra", "lossless_wav_samplerate")
        ),

        EncyclopediaEntry(
            id = "bluetooth_mic_cut",
            number = 22,
            volume = EncyclopediaVolume.AUDIO_GEAR,
            title = "为什么不能使用蓝牙耳机麦克风采录语料？",
            keywords = listOf("蓝牙", "蓝牙耳机", "无线耳机", "AirPods", "HFP", "SCO", "窄带", "发闷", "高频截断"),
            oneLineSummary = "蓝牙耳机一旦开启麦克风收音就会强制退回窄带通话模式，`4 kHz` 或 `8 kHz` 以上高频会被整片切空。",
            paragraphs = listOf(
                "很多人觉得昂贵的无线蓝牙耳机听歌音质很好，拿来录方言应该也不错——这是一个极易踩坑的误区。蓝牙耳机平时听歌走的是单向高音质通道（`A2DP`）；可一旦任何录音软件调用蓝牙耳机上的麦克风，系统底层就会瞬间强制切换到双向打电话的 **通话协议（HFP / SCO）**。",
                "受蓝牙双向通话带宽所限，这个模式下的实际采样率会暴跌到 **`8 kHz`（最高只能录到 `4 kHz` 频率）** 或 **`16 kHz`（最高只能录到 `8 kHz`）**，还会叠加耳机内置的强力通话降噪。",
                "打开语图谱一看就会发现，`4 kHz`（或 `8 kHz`）往上一片空白，像被刀齐刷刷切掉一样。而区分“四 `[s]`、十 `[ʂ]`、西 `[ɕ]`”等擦音和塞擦音最关键的能量恰恰分布在 `4～10 kHz` 高频区，一旦被切掉，录出来的声音就像隔着棉被说话一样发闷含混，无法用于声学分析。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_15_BLUETOOTH_NARROWBAND_CUT,
            diagramCaption = "机身内置麦或有线/USB 麦可完整保留 20kHz 全频段；蓝牙通话协议麦克风会硬性截断中高频频谱",
            fieldworkTip = "哪怕手头没有任何外接专业麦克风，直接断开蓝牙、用手机自带底部麦克风（离嘴约一拳半）录制的无损音频，其频段完整度也远远胜过任何蓝牙耳机。",
            relatedIds = listOf("fricative_affricate_spectra", "ai_denoise_harm", "lossless_wav_samplerate")
        ),

        EncyclopediaEntry(
            id = "mic_plosive_distance",
            number = 23,
            volume = EncyclopediaVolume.AUDIO_GEAR,
            title = "近讲气流冲击（喷麦）与偏轴收音摆位",
            keywords = listOf("喷麦", "噗噗声", "距离", "15cm", "偏轴", "防喷罩", "摆位", "送气"),
            oneLineSummary = "把麦克风放在嘴角斜前方约一拳半（15～20 cm、偏开 30°），既清晰收音又能避开送气气流撞击振膜。",
            paragraphs = listOf(
                "我们可以做一个小实验：把手掌放在嘴唇正前方五厘米处，念“啪 `[pʰa]`、他 `[tʰa]`”，手心立刻能感觉到一股猛烈的气流直冲出来；但如果把手掌往嘴角斜侧移开一点点，气流就几乎吹不到了，而听到的说话声却依然同样响亮。",
                "麦克风里娇嫩的拾音振膜也是如此：如果把手机底部麦克风或话筒正对着嘴巴贴得太近，发 `p、t、k、c、ch` 等送气音时，这股高速气团会直接撞在振膜上，产生沉闷刺耳的“噗噗”巨响，也就是 **喷麦（Plosive Pop）**，严重淹没后面的元音。",
                "田野现场哪怕没有海绵防风罩，只要采用 **偏轴近场摆位** 就能完美解决：让麦克风距离嘴唇 **15～20 厘米（约一拳半）**，往左或往右偏离鼻子正前方约 **30°～45°（朝向嘴角斜前方）**。声音向四面八方均匀传播，而直线喷出的气流则会从麦克风旁边擦身而过。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_16_MIC_DISTANCE_ANGLE,
            diagramCaption = "避开口唇正前方的定向喷气轴线，于 15～20 cm 嘴角斜侧 30° 处拾音可兼顾高信噪比与防喷效果",
            fieldworkTip = "把手机或话筒架放在硬桌面上录音时，建议在底下垫一条折叠毛巾或厚书本，能有效隔绝合作人手肘碰桌或翻页传来的低频闷响。",
            relatedIds = listOf("waveform_clipping", "voicing_aspiration", "bluetooth_mic_cut")
        ),

        EncyclopediaEntry(
            id = "lossless_wav_samplerate",
            number = 24,
            volume = EncyclopediaVolume.AUDIO_GEAR,
            title = "采样率（44.1kHz / 48kHz）与无损 WAV 归档标准",
            keywords = listOf("采样率", "44.1kHz", "48kHz", "16-bit", "PCM", "无损", "wav", "mp3", "微信语音"),
            oneLineSummary = "44.1kHz / 16-bit 无损 `.wav` 完整记录人声全部频段细节，而 MP3 或微信语音等有损格式会删改频谱。",
            paragraphs = listOf(
                "数字录音就像用高速相机给连续声波拍快照，每秒钟拍多少次就叫 **采样率**。根据 **奈奎斯特采样定理**，录音能还原的最高声音频率正好是采样率的一半。因此，采用国际通用的 **`44.1 kHz`（每秒采样 44100 次）** 就能完整记录 `0～22.05 kHz` 的全部声音，足以覆盖人耳听觉上限和所有辅音高频细节；搭配 **`16-bit` 位深** 可提供约 `96 dB` 的宽广动态范围，是语言学归档与声学实验的黄金标准。",
                "为什么不能直接用 `.mp3`、`.m4a` 或社交软件转发的语音条做声学研究？因为这些格式为了把文件体积缩小十倍，采用了 **心理声学有损压缩**：算法利用人耳“强音会盖住附近弱音”的错觉，把大量弱能量频段和高频细节悄悄删掉了。平时戴耳机听或许觉得差不多，但一放进 Praat 测共振峰或频谱重心，被删空的地方就会变成断层空洞，导致测量数据失真。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_20_LOSSLESS_WAV_VS_MP3,
            diagramCaption = "44.1kHz 无损 PCM 完整保存全部时频采样；有损压缩格式会因掩蔽裁剪在频谱中留下断层空洞",
            fieldworkTip = "日常方言与语音实验采录保持默认的 44.1 kHz / 16-bit 单声道无损 WAV 即可；若需与口述史摄像机视频音轨对齐剪辑，可在设置中切换为影视标准的 48 kHz。",
            relatedIds = listOf("file_types_guide", "bluetooth_mic_cut", "ai_denoise_harm")
        ),

        // ==================== 第五卷：语料归档与文件格式 ====================
        EncyclopediaEntry(
            id = "what_is_zip",
            number = 25,
            volume = EncyclopediaVolume.COMPUTER_FILES,
            title = "`.zip` 归档压缩包与先解压后分析流程",
            keywords = listOf("zip", "压缩包", "解压", "解压缩", "打包", "打不开", "拖不进", "文件夹"),
            oneLineSummary = "`.zip` 把成百上千个音频与标注无损打包成一个文件，传到电脑后务必先右键“全部解压缩”再导入软件。",
            paragraphs = listOf(
                "录完一份调查字表，往往会产生几百个独立的 `.wav` 音频、几百个 `.TextGrid` 标注文件和一张汇总表格。如果一个一个零散发送，很容易漏传或弄乱目录。把它们打包成一个 **`.zip` 压缩包** 就像装进一只密封文件箱——需要注意的是，`.zip` 是纯数据无损打包，解压出来的 `.wav` 与原文件分毫不差，绝不会像转 MP3 那样损失半点音质。",
                "在电脑上使用 `.zip` 时有一个新手最常遇到的卡壳点：在 Windows 电脑上直接双击 `.zip` 文件，虽然也能看到里面的文件列表，但那只是系统提供的 **“箱内只读预览”**，文件其实还没有真正拿出来放到硬盘上。此时如果直接从预览窗口把音频往 Praat 里拖，或者直接双击里面的表格修改保存，常常会报错失败或找不到刚才改过的内容。",
                "正确的做法非常简单：把 `.zip` 传到电脑桌面或文件夹后，**右键点击它 → 选择「全部解压缩」（或解压到当前文件夹）**，等旁边生成了一个普通的黄色文件夹，再打开那个普通文件夹进行 Praat 分析和表格编辑。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_17_ZIP_BOX_EXTRACT_FIRST,
            diagramCaption = "电脑端收到 .zip 归档包后，先右键执行解压缩释放为常规文件夹，再导入分析软件处理",
            fieldworkTip = "如果想跳过解压步骤、一次性把整套音频和标注直接装进 Praat，可在导出界面选择「Praat 工程 (.Collection)」，单个文件即可在 Praat 中直接打开。",
            relatedIds = listOf("file_types_guide", "same_name_pairing", "csv_excel_encoding")
        ),

        EncyclopediaEntry(
            id = "file_types_guide",
            number = 26,
            volume = EncyclopediaVolume.COMPUTER_FILES,
            title = "`.wav`、`.csv`、`.TextGrid` 与 `.Collection` 格式说明",
            keywords = listOf("wav", "csv", "TextGrid", "Collection", "文件后缀", "扩展名", "Praat"),
            oneLineSummary = "四种文件各司其职：`.wav` 存原声波形，`.csv` 存统计表格，`.TextGrid` 存时间标注，`.Collection` 打包整套 Praat 工程。",
            paragraphs = listOf(
                "导出语料包时看到的四种常见后缀文件，在语音学研究流程中各自承担明确角色：",
                "• **`.wav`（无损音频文件）**：真正记录声音波形的文件，可以用任何播放器试听，也是声学测量的原始依据。\n• **`.csv`（通用电子表格）**：用逗号分隔数据的纯文本统计表，汇总了每个词条的序号、分组、国际音标备注、时长以及自动测算的五度调值与共振峰数据，双击即可用 Excel、WPS 打开，也能直接读入 R 或 Python 绘图。\n• **`.TextGrid`（Praat 时间对齐标注文件）**：极轻量的纯文本文件，**它自己里面没有声音**，只精确到毫秒地记录“第几秒到第几秒是哪个字、哪段韵母、什么音标”。在 Praat 里把它和同名的 `.wav` 一起选中点 `View & Edit`，就能看到上层波形语图谱、下层对齐文字框的经典标注界面。\n• **`.Collection`（Praat 合一工程文件）**：把整个字表的所有 `Sound` 音频和配套 `TextGrid` 标注提前装进同一个工程文件里，在 Praat 中点 `Read from file...` 一次就能全部载入列表。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_18_WAV_TEXTGRID_COLLECTION_LAYERS,
            diagramCaption = ".wav 存储底层声波，.TextGrid 记录时间边界与文本层级，.Collection 则将成套对象合并封装",
            fieldworkTip = "提交课程作业、学位论文归档或长期备份推荐导出 `.zip` 标准文件包（独立的 `.wav` + `.TextGrid` + `.csv` 最通用长久）；自己在电脑上用 Praat 集中精修边界则推荐导出 `.Collection`。",
            relatedIds = listOf("what_is_zip", "same_name_pairing", "syllable_segmentation_coarticulation")
        ),

        EncyclopediaEntry(
            id = "csv_excel_encoding",
            number = 27,
            volume = EncyclopediaVolume.COMPUTER_FILES,
            title = "`.csv` 字符编码（UTF-8 BOM 与 GBK）与音标乱码成因",
            keywords = listOf("乱码", "csv", "Excel", "WPS", "编码", "UTF-8", "BOM", "GBK", "音标显示"),
            oneLineSummary = "国际音标依赖 UTF-8 编码保存；文件开头写入 UTF-8 BOM 标识头，就能让桌面版 Excel 双击直接正常显示。",
            paragraphs = listOf(
                "不少做方言调查的同学都遇到过这样的怪事：明明在记事本或手机里看得好好的 `.csv` 词表，一发到 Windows 电脑上用 Excel 双击打开，里面的汉字和国际音标全变成了“æˆ‘”或问号乱码。这不是文件坏了，而是两套 **文字编码本（Character Encoding）** 对不上号：",
                "早期中文 Windows 系统默认使用国标 **`GBK`** 编码，它认得常用汉字，却装不下成百上千个国际音标（IPA 特殊符号）和方言生僻字；现代国际通用的 **`UTF-8`** 编码则能装下全世界所有文字与音标。但如果一个纯 UTF-8 的 `.csv` 文件开头没有贴“身份标签”，部分桌面版 Excel 打开时会习惯性地拿旧的 `GBK` 密码本去硬翻，结果自然满屏乱码。",
                "PhonAcq 已经从双向替您处理好了这个问题：\n1. **导出 `.csv` 表格时**：自动在文件最开头写入标准的 **`UTF-8 BOM`（字节序标识头 `EF BB BF`）**，相当于贴好身份标签，无论在 Windows/Mac 版 Excel、WPS 还是统计软件里直接打开，汉字与国际音标都清晰端正。\n2. **导入外部词表时**：内置编码自动识别，无论您手头的旧词表是 `UTF-8`、`UTF-16` 还是传统 `GBK` 保存的，都能直接准确读入。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_23_CSV_UTF8_BOM_ENCODING,
            diagramCaption = "文件起始处写入 UTF-8 BOM 字节序标记，可引导桌面版 Excel 按 Unicode 正确解码汉字与国际音标",
            fieldworkTip = "如果在电脑 Excel 里修改了导出的 `.csv` 并需要重新保存，请在「另存为」格式中认准「CSV UTF-8 (逗号分隔) (*.csv)」或直接存为 `.xlsx` 工作簿，切勿误选普通的 ANSI/GBK CSV 以免丢失音标符号。",
            relatedIds = listOf("file_types_guide", "ipa_basics", "what_is_zip")
        ),

        EncyclopediaEntry(
            id = "same_name_pairing",
            number = 28,
            volume = EncyclopediaVolume.COMPUTER_FILES,
            title = "音频 `.wav` 与标注 `.TextGrid` 的同名配对规范",
            keywords = listOf("同名", "配对", "重命名", "改名", "文件夹", "脚本", "批处理", "Praat脚本"),
            oneLineSummary = "Praat 批量提取脚本依靠完全相同的主文件名（如 `001_巴.wav` 配 `001_巴.TextGrid`）自动配对音频与标注。",
            paragraphs = listOf(
                "后期用 Praat 脚本一次性批量提取几百个字的声调十点值或共振峰数据时，计算机怎么知道哪份 `.TextGrid` 标注对应哪段 `.wav` 录音呢？答案是：**它只认同一个文件夹下完全相同的主文件名（Filename Stem）**。",
                "举例来说，`.TextGrid` 文件内部其实并没有写死自己属于哪条音频；只要文件夹里同时躺着 `SPK01_001_巴.wav` 和 `SPK01_001_巴.TextGrid`，脚本读到音频时就会自动把后缀换成 `.TextGrid` 去找它的搭档。",
                "如果解压后您在电脑上只给 `.wav` 改了名字（比如改成了 `001_巴_重录.wav`），却忘了把旁边的 `.TextGrid` 改成一模一样的主文件名，这对搭档就会瞬间“失联”，导致批处理脚本报错中断。PhonAcq 导出的归档包已默认采用规范编号并保持音频与标注严格同名。"
            ),
            diagramId = EncyclopediaDiagramId.ENC_24_WAV_TEXTGRID_STEM_PAIRING,
            diagramCaption = "同目录下保持主文件名严格一致（如 001_巴），Praat 与批处理脚本才能自动关联波形与标注",
            fieldworkTip = "从解压文件夹中挑选部分代表字单独发给导师或放入脚本目录时，请记得按名称排序，把同名的 `.wav` 与 `.TextGrid` 成双成对一起复制。",
            relatedIds = listOf("file_types_guide", "what_is_zip", "syllable_segmentation_coarticulation")
        )
    )

    private val entryById: Map<String, EncyclopediaEntry> = allEntries.associateBy { it.id }

    fun getEntryById(id: String): EncyclopediaEntry? = entryById[id]

    fun filterEntries(
        query: String,
        volume: EncyclopediaVolume?
    ): List<EncyclopediaEntry> {
        val trimmed = query.trim().lowercase()
        return allEntries.filter { entry ->
            val matchesVol = volume == null || entry.volume == volume
            if (!matchesVol) return@filter false
            if (trimmed.isEmpty()) return@filter true

            entry.title.lowercase().contains(trimmed) ||
                entry.oneLineSummary.lowercase().contains(trimmed) ||
                entry.keywords.any { it.lowercase().contains(trimmed) } ||
                entry.paragraphs.any { it.lowercase().contains(trimmed) } ||
                entry.fieldworkTip.lowercase().contains(trimmed)
        }
    }
}
