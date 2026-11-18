package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AcousticAnalyzer
import com.example.audio.AudioRecorder
import com.example.audio.SpeechQualityEvaluator
import com.example.data.*
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.ActiveMicOwner
import com.example.link.CompletedHostTakeInfo
import com.example.link.LinkRole
import com.example.link.PromptCoachingFeedback
import com.example.link.PromptDisplayPolicy
import com.example.link.RemoteSessionSnapshot
import com.example.link.RemoteWordItem
import com.example.link.WirelessLinkCoordinator
import com.example.link.WirelessLinkHostBridge
import com.example.link.WirelessSessionArchiveManager
import com.example.link.WirelessSessionCapsuleSummary
import com.example.utils.ExportOptions
import com.example.utils.ExportSummary
import com.example.utils.ImportArchiveSummary
import com.example.utils.SpreadsheetWordImporter
import com.example.utils.TextGridFormatConfig
import com.example.utils.TextGridPreset
import com.example.utils.WordImageManager
import com.example.utils.ZipUtility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

@Stable
class RecordingViewModel(
    application: Application,
    internal val repository: ProjectRepository
) : AndroidViewModel(application) {

    private val tag = "RecordingViewModel"
    internal val context = application.applicationContext

    val audioRecorder = AudioRecorder()
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    // Project listing state
    val allProjects: StateFlow<List<Project>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // Speakers listing state - EXPOSED FOR MULTI-SPEAKER SUPPORT
    val allSpeakers: StateFlow<List<Speaker>> = repository.allSpeakers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // Preferences
    private val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // Selection states
    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject = _currentProject.asStateFlow()

    private val _currentSpeaker = MutableStateFlow<Speaker?>(null)
    val currentSpeaker = _currentSpeaker.asStateFlow()

    private val _currentWords = MutableStateFlow<List<Word>>(emptyList())
    val currentWords = _currentWords.asStateFlow()

    // Recording and configuration settings
    private val _autoNextEnabled = MutableStateFlow(sharedPrefs.getBoolean("auto_next", false))
    val autoNextEnabled = _autoNextEnabled.asStateFlow()

    private val _pushToTalkEnabled = MutableStateFlow(sharedPrefs.getBoolean("push_to_talk", false))
    val pushToTalkEnabled = _pushToTalkEnabled.asStateFlow()

    private val _autoAdvanceOnHoldRelease = MutableStateFlow(
        sharedPrefs.getBoolean("auto_advance_on_hold_release", true)
    )
    val autoAdvanceOnHoldRelease = _autoAdvanceOnHoldRelease.asStateFlow()

    private val _holdOnQualityIssue = MutableStateFlow(
        sharedPrefs.getBoolean("hold_on_quality_issue", true)
    )
    val holdOnQualityIssue = _holdOnQualityIssue.asStateFlow()

    private val _lastHoldQualityNotice = MutableStateFlow<String?>(null)
    val lastHoldQualityNotice = _lastHoldQualityNotice.asStateFlow()

    private val _autoPlayAfterRecord = MutableStateFlow(sharedPrefs.getBoolean("auto_play_after", false))
    val autoPlayAfterRecord = _autoPlayAfterRecord.asStateFlow()

    private val _volumeKeyControlEnabled = MutableStateFlow(sharedPrefs.getBoolean("volume_key_ctrl", false))
    val volumeKeyControlEnabled = _volumeKeyControlEnabled.asStateFlow()

    private val _participantCleanMode = MutableStateFlow(false)
    val participantCleanMode = _participantCleanMode.asStateFlow()

    private val _vadThreshold = MutableStateFlow(sharedPrefs.getFloat("vad_threshold", 1200f))
    val vadThreshold = _vadThreshold.asStateFlow()

    private val _silenceDelayMs = MutableStateFlow(sharedPrefs.getLong("silence_delay", 1000L))
    val silenceDelayMs = _silenceDelayMs.asStateFlow()

    private val _lowVolumeThreshold = MutableStateFlow(sharedPrefs.getFloat("low_volume", 250f))
    val lowVolumeThreshold = _lowVolumeThreshold.asStateFlow()

    private val _creakySensitivity = MutableStateFlow(sharedPrefs.getString("creaky", "中") ?: "中")
    val creakySensitivity = _creakySensitivity.asStateFlow()

    private val _showPhoneticWord = MutableStateFlow<Word?>(null)
    val showPhoneticWord = _showPhoneticWord.asStateFlow()

    private val _showGlobalManagement = MutableStateFlow(false)
    val showGlobalManagement = _showGlobalManagement.asStateFlow()

    private val _characterSize = MutableStateFlow(sharedPrefs.getString("char_size", "中") ?: "中")
    val characterSize = _characterSize.asStateFlow()

    // Extended Basic Settings
    private val _volumeKeyAction = MutableStateFlow(
        sharedPrefs.getString("volume_key_action", "RECORD_NEXT") ?: "RECORD_NEXT" // "RECORD_NEXT", "PREV_NEXT", "RECORD_ONLY"
    )
    val volumeKeyAction = _volumeKeyAction.asStateFlow()

    private val _confirmOverwriteRecording = MutableStateFlow(
        sharedPrefs.getBoolean("confirm_overwrite", false)
    )
    val confirmOverwriteRecording = _confirmOverwriteRecording.asStateFlow()

    private val _hideRemarkInCleanMode = MutableStateFlow(
        sharedPrefs.getBoolean("hide_remark_clean_v2", true)
    )
    val hideRemarkInCleanMode = _hideRemarkInCleanMode.asStateFlow()

    private val _hideTextInCleanMode = MutableStateFlow(
        sharedPrefs.getBoolean("hide_text_clean_v1", false)
    )
    val hideTextInCleanMode = _hideTextInCleanMode.asStateFlow()

    private val _carrierSentenceEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("carrier_sentence_enabled", false)
    )
    val carrierSentenceEnabled = _carrierSentenceEnabled.asStateFlow()

    private val _carrierSentenceTemplate = MutableStateFlow(
        sharedPrefs.getString("carrier_sentence_template", S.recording.defaultCarrierTemplate)
            ?: S.recording.defaultCarrierTemplate
    )
    val carrierSentenceTemplate = _carrierSentenceTemplate.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(
        sharedPrefs.getBoolean("keep_screen_on", true)
    )
    val keepScreenOn = _keepScreenOn.asStateFlow()

    private val _exportNamingStyle = MutableStateFlow(
        sharedPrefs.getString("export_naming_style", "CODE_INDEX_WORD") ?: "CODE_INDEX_WORD"
    )
    val exportNamingStyle = _exportNamingStyle.asStateFlow()

    private val _exportFolderPerSpeaker = MutableStateFlow(
        sharedPrefs.getBoolean("export_folder_per_speaker", false)
    )
    val exportFolderPerSpeaker = _exportFolderPerSpeaker.asStateFlow()

    private val _exportAppendNoteToFileName = MutableStateFlow(
        sharedPrefs.getBoolean("export_append_note_to_filename", false)
    )
    val exportAppendNoteToFileName = _exportAppendNoteToFileName.asStateFlow()

    private val _textGridFormatConfig = MutableStateFlow(
        TextGridFormatConfig.fromJsonString(sharedPrefs.getString("textgrid_format_config_v1", null))
    )
    val textGridFormatConfig = _textGridFormatConfig.asStateFlow()

    // Advanced Settings
    private val _sampleRateHz = MutableStateFlow(
        sharedPrefs.getInt("sample_rate_hz", 44100)
    )
    val sampleRateHz = _sampleRateHz.asStateFlow()

    private val _audioSourceStrategy = MutableStateFlow(
        sharedPrefs.getString("audio_source_strategy", "VOICE_RECOGNITION") ?: "VOICE_RECOGNITION"
    )
    val audioSourceStrategy = _audioSourceStrategy.asStateFlow()

    private val _preRollEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("pre_roll_enabled", true)
    )
    val preRollEnabled = _preRollEnabled.asStateFlow()

    private val _maxRetainedTakes = MutableStateFlow(
        sharedPrefs.getInt("max_retained_takes", 3).coerceIn(1, 10)
    )
    val maxRetainedTakes = _maxRetainedTakes.asStateFlow()

    private val TAG_DELIMITER = "||"
    private val DEFAULT_QUICK_TONE_SYMBOLS = listOf(
        "55", "35", "214", "51", "44", "33", "21", "24", "42", "53",
        "˥", "˦", "˧", "˨", "˩",
        "ʔ", "ʰ", "̃"
    )
    private val DEFAULT_QUICK_IPA_INITIALS = DEFAULT_DIALECT_IPA_INITIALS
    private val DEFAULT_QUICK_IPA_FINALS = DEFAULT_DIALECT_IPA_FINALS
    private val DEFAULT_QUICK_FIELD_TAGS: List<String>
        get() = S.recording.defaultQuickFieldTags

    private fun loadStringListPref(key: String, defaultList: List<String>): List<String> {
        val raw = sharedPrefs.getString(key, null) ?: return defaultList
        if (raw.isBlank()) return emptyList()
        return raw.split(TAG_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val _quickToneSymbols = MutableStateFlow(
        loadStringListPref("quick_tone_symbols", DEFAULT_QUICK_TONE_SYMBOLS)
    )
    val quickToneSymbols = _quickToneSymbols.asStateFlow()

    private val _quickIpaInitials = MutableStateFlow(
        loadStringListPref("quick_ipa_initials", DEFAULT_QUICK_IPA_INITIALS)
    )
    val quickIpaInitials = _quickIpaInitials.asStateFlow()

    private val _quickIpaFinals = MutableStateFlow(
        loadStringListPref("quick_ipa_finals", DEFAULT_QUICK_IPA_FINALS)
    )
    val quickIpaFinals = _quickIpaFinals.asStateFlow()

    private val _quickFieldTags = MutableStateFlow(
        loadStringListPref("quick_field_tags", DEFAULT_QUICK_FIELD_TAGS)
    )
    val quickFieldTags = _quickFieldTags.asStateFlow()

    // App Theme State & Material You Dynamic Color
    private val _themeMode = MutableStateFlow(
        sharedPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
    )
    val themeMode = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("dynamic_color", true)
    )
    val dynamicColorEnabled = _dynamicColorEnabled.asStateFlow()

    private val _isSimpleMode = MutableStateFlow(
        sharedPrefs.getBoolean("is_simple_mode", false)
    )
    val isSimpleMode = _isSimpleMode.asStateFlow()

    private val _hasSelectedUiMode = MutableStateFlow(
        if (sharedPrefs.contains("has_selected_ui_mode_v1")) {
            sharedPrefs.getBoolean("has_selected_ui_mode_v1", false)
        } else {
            sharedPrefs.getBoolean("has_seen_recording_tips_v2", false)
        }
    )
    val hasSelectedUiMode = _hasSelectedUiMode.asStateFlow()

    private val _hasSeenRecordingTips = MutableStateFlow(
        sharedPrefs.getBoolean("has_seen_recording_tips_v2", false)
    )
    val hasSeenRecordingTips = _hasSeenRecordingTips.asStateFlow()

    fun setSimpleMode(enabled: Boolean) {
        _isSimpleMode.value = enabled
        _hasSelectedUiMode.value = true
        sharedPrefs.edit()
            .putBoolean("is_simple_mode", enabled)
            .putBoolean("has_selected_ui_mode_v1", true)
            .apply()
        if (enabled) {
            _showPhoneticWord.value = null
            _showGlobalManagement.value = false
        }
    }

    fun completeInitialModeSelection(simple: Boolean) {
        setSimpleMode(simple)
    }

    fun markRecordingTipsSeen() {
        _hasSeenRecordingTips.value = true
        _hasSelectedUiMode.value = true
        sharedPrefs.edit()
            .putBoolean("has_seen_recording_tips_v2", true)
            .putBoolean("has_selected_ui_mode_v1", true)
            .apply()
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        _dynamicColorEnabled.value = enabled
        sharedPrefs.edit().putBoolean("dynamic_color", enabled).apply()
    }

    fun setVolumeKeyAction(action: String) {
        _volumeKeyAction.value = action
        sharedPrefs.edit().putString("volume_key_action", action).apply()
    }

    fun setConfirmOverwriteRecording(enabled: Boolean) {
        _confirmOverwriteRecording.value = enabled
        sharedPrefs.edit().putBoolean("confirm_overwrite", enabled).apply()
    }

    fun setHideRemarkInCleanMode(enabled: Boolean) {
        _hideRemarkInCleanMode.value = enabled
        sharedPrefs.edit().putBoolean("hide_remark_clean_v2", enabled).apply()
    }

    fun setHideTextInCleanMode(enabled: Boolean) {
        _hideTextInCleanMode.value = enabled
        sharedPrefs.edit().putBoolean("hide_text_clean_v1", enabled).apply()
    }

    fun setCarrierSentenceEnabled(enabled: Boolean) {
        _carrierSentenceEnabled.value = enabled
        sharedPrefs.edit().putBoolean("carrier_sentence_enabled", enabled).apply()
    }

    fun setCarrierSentenceTemplate(template: String) {
        val cleaned = template.trim().ifEmpty { S.recording.defaultCarrierTemplate }
        _carrierSentenceTemplate.value = cleaned
        sharedPrefs.edit().putString("carrier_sentence_template", cleaned).apply()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        sharedPrefs.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    fun setExportNamingStyle(style: String) {
        _exportNamingStyle.value = style
        sharedPrefs.edit().putString("export_naming_style", style).apply()
    }

    fun setExportFolderPerSpeaker(enabled: Boolean) {
        _exportFolderPerSpeaker.value = enabled
        sharedPrefs.edit().putBoolean("export_folder_per_speaker", enabled).apply()
    }

    fun setExportAppendNoteToFileName(enabled: Boolean) {
        _exportAppendNoteToFileName.value = enabled
        sharedPrefs.edit().putBoolean("export_append_note_to_filename", enabled).apply()
    }

    fun setTextGridFormatConfig(config: TextGridFormatConfig) {
        _textGridFormatConfig.value = config
        sharedPrefs.edit().putString("textgrid_format_config_v1", config.toJsonString()).apply()
    }

    fun selectTextGridPreset(preset: TextGridPreset) {
        val current = _textGridFormatConfig.value
        val next = if (preset == TextGridPreset.CUSTOM) {
            current.copy(preset = TextGridPreset.CUSTOM)
        } else {
            TextGridFormatConfig.fromPreset(preset)
        }
        setTextGridFormatConfig(next)
    }

    fun setSampleRateHz(rate: Int) {
        _sampleRateHz.value = rate
        sharedPrefs.edit().putInt("sample_rate_hz", rate).apply()
        audioRecorder.configureHardware(rate, _audioSourceStrategy.value, _preRollEnabled.value)
    }

    fun setAudioSourceStrategy(strategy: String) {
        _audioSourceStrategy.value = strategy
        sharedPrefs.edit().putString("audio_source_strategy", strategy).apply()
        audioRecorder.configureHardware(_sampleRateHz.value, strategy, _preRollEnabled.value)
    }

    fun setPreRollEnabled(enabled: Boolean) {
        _preRollEnabled.value = enabled
        sharedPrefs.edit().putBoolean("pre_roll_enabled", enabled).apply()
        audioRecorder.configureHardware(_sampleRateHz.value, _audioSourceStrategy.value, enabled)
    }

    fun setMaxRetainedTakes(count: Int) {
        val coerced = count.coerceIn(1, 10)
        _maxRetainedTakes.value = coerced
        sharedPrefs.edit().putInt("max_retained_takes", coerced).apply()
    }

    private fun notifyQuickShorthandSynced() {
        if (wirelessLink.activeRole.value != LinkRole.STANDALONE && wirelessLink.isProjectHost.value) {
            wirelessLink.notifyProjectHostStateChanged(includeWordList = false)
        }
    }

    private fun normalizePhoneticSymbolEntry(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("◌") && trimmed.length > 1) {
            trimmed.removePrefix("◌")
        } else {
            trimmed
        }
    }

    fun setQuickToneSymbols(symbols: List<String>) {
        val cleaned = symbols.map(::normalizePhoneticSymbolEntry).filter { it.isNotEmpty() }.distinct()
        _quickToneSymbols.value = cleaned
        sharedPrefs.edit().putString("quick_tone_symbols", cleaned.joinToString(TAG_DELIMITER)).apply()
        notifyQuickShorthandSynced()
    }

    fun addQuickToneSymbol(symbol: String) {
        val trimmed = normalizePhoneticSymbolEntry(symbol)
        if (trimmed.isEmpty()) return
        val updated = (_quickToneSymbols.value + trimmed).distinct()
        setQuickToneSymbols(updated)
    }

    fun removeQuickToneSymbol(symbol: String) {
        setQuickToneSymbols(_quickToneSymbols.value.filterNot { it == symbol })
    }

    fun resetQuickToneSymbols() {
        _quickToneSymbols.value = DEFAULT_QUICK_TONE_SYMBOLS
        sharedPrefs.edit().remove("quick_tone_symbols").apply()
        notifyQuickShorthandSynced()
    }

    fun setQuickIpaInitials(symbols: List<String>) {
        val cleaned = symbols.map(::normalizePhoneticSymbolEntry).filter { it.isNotEmpty() }.distinct()
        _quickIpaInitials.value = cleaned
        sharedPrefs.edit().putString("quick_ipa_initials", cleaned.joinToString(TAG_DELIMITER)).apply()
        notifyQuickShorthandSynced()
    }

    fun addQuickIpaInitial(symbol: String) {
        val trimmed = normalizePhoneticSymbolEntry(symbol)
        if (trimmed.isEmpty()) return
        setQuickIpaInitials((_quickIpaInitials.value + trimmed).distinct())
    }

    fun removeQuickIpaInitial(symbol: String) {
        setQuickIpaInitials(_quickIpaInitials.value.filterNot { it == symbol })
    }

    fun resetQuickIpaInitials() {
        _quickIpaInitials.value = DEFAULT_QUICK_IPA_INITIALS
        sharedPrefs.edit().remove("quick_ipa_initials").apply()
        notifyQuickShorthandSynced()
    }

    fun setQuickIpaFinals(symbols: List<String>) {
        val cleaned = symbols.map(::normalizePhoneticSymbolEntry).filter { it.isNotEmpty() }.distinct()
        _quickIpaFinals.value = cleaned
        sharedPrefs.edit().putString("quick_ipa_finals", cleaned.joinToString(TAG_DELIMITER)).apply()
        notifyQuickShorthandSynced()
    }

    fun addQuickIpaFinal(symbol: String) {
        val trimmed = normalizePhoneticSymbolEntry(symbol)
        if (trimmed.isEmpty()) return
        setQuickIpaFinals((_quickIpaFinals.value + trimmed).distinct())
    }

    fun removeQuickIpaFinal(symbol: String) {
        setQuickIpaFinals(_quickIpaFinals.value.filterNot { it == symbol })
    }

    fun resetQuickIpaFinals() {
        _quickIpaFinals.value = DEFAULT_QUICK_IPA_FINALS
        sharedPrefs.edit().remove("quick_ipa_finals").apply()
        notifyQuickShorthandSynced()
    }

    fun setQuickFieldTags(tags: List<String>) {
        val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        _quickFieldTags.value = cleaned
        sharedPrefs.edit().putString("quick_field_tags", cleaned.joinToString(TAG_DELIMITER)).apply()
        notifyQuickShorthandSynced()
    }

    fun addQuickFieldTag(tagText: String) {
        val trimmed = tagText.trim()
        if (trimmed.isEmpty()) return
        val updated = (_quickFieldTags.value + trimmed).distinct()
        setQuickFieldTags(updated)
    }

    fun removeQuickFieldTag(tagText: String) {
        setQuickFieldTags(_quickFieldTags.value.filterNot { it == tagText })
    }

    fun resetQuickFieldTags() {
        _quickFieldTags.value = DEFAULT_QUICK_FIELD_TAGS
        sharedPrefs.edit().remove("quick_field_tags").apply()
        notifyQuickShorthandSynced()
    }

    fun resetAllQuickNotesToDefault() {
        resetQuickToneSymbols()
        resetQuickIpaInitials()
        resetQuickIpaFinals()
        resetQuickFieldTags()
    }

    fun resetAllSettingsToDefault() {
        setAutoNextEnabled(false)
        setPushToTalkEnabled(false)
        setAutoAdvanceOnHoldRelease(true)
        setHoldOnQualityIssue(true)
        setAutoPlayAfterRecord(false)
        setVolumeKeyControlEnabled(false)
        setVolumeKeyAction("RECORD_NEXT")
        setConfirmOverwriteRecording(false)
        setCharacterSize("中")
        setHideRemarkInCleanMode(true)
        setHideTextInCleanMode(false)
        setCarrierSentenceEnabled(false)
        setCarrierSentenceTemplate(S.recording.defaultCarrierTemplate)
        setKeepScreenOn(true)
        setExportNamingStyle("CODE_INDEX_WORD")
        setExportFolderPerSpeaker(false)
        setExportAppendNoteToFileName(false)
        setTextGridFormatConfig(TextGridFormatConfig())
        setVadThreshold(1200f)
        setSilenceDelayMs(1000L)
        setLowVolumeThreshold(250f)
        setCreakySensitivity("中")
        setSampleRateHz(44100)
        setAudioSourceStrategy("VOICE_RECOGNITION")
        setPreRollEnabled(true)
        setMaxRetainedTakes(3)
        setShuffleEnabled(false)
        setShufflingMode(ShufflingMode.RANDOM_ALL)
        resetAllQuickNotesToDefault()
        wirelessLink.resetPromptPolicyToDefault()
    }

    // Distinct non-blank speaker group names across speakers and projects (supporting multi-group speakers)
    val speakerGroups: StateFlow<List<String>> = combine(allSpeakers, allProjects) { speakers, projects ->
        val set = linkedSetOf<String>()
        speakers.forEach { spk ->
            spk.groupList.forEach { grp -> set.add(grp) }
        }
        projects.forEach { proj ->
            parseSpeakerGroups(proj.speakerGroupName).forEach { grp -> set.add(grp) }
        }
        set.toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // Multi-project word count & multi-speaker progress dashboard
    private val _projectCounts = MutableStateFlow<Map<Long, Pair<Int, Int>>>(emptyMap())
    val projectCounts = _projectCounts.asStateFlow()

    private val _projectSummaries = MutableStateFlow<Map<Long, ProjectMultiSpeakerSummary>>(emptyMap())
    val projectSummaries = _projectSummaries.asStateFlow()

    private val countsRefreshTrigger = MutableStateFlow(0)

    // Randomized Shuffle Order Lists (persisted across sessions)
    private val _isShuffleEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("shuffle_enabled", false)
    )
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

    private val _shufflingMode = MutableStateFlow(
        when (sharedPrefs.getString("shuffling_mode", "RANDOM_ALL")) {
            "BLOCK_BALANCED" -> ShufflingMode.BLOCK_BALANCED
            else -> ShufflingMode.RANDOM_ALL
        }
    )
    val shufflingMode = _shufflingMode.asStateFlow()

    private val _onlyPendingWords = MutableStateFlow(false)
    val onlyPendingWords = _onlyPendingWords.asStateFlow()

    private val _currentSeqIndex = MutableStateFlow(0)
    val currentSeqIndex = _currentSeqIndex.asStateFlow()

    // Sequence list maps sequence position to index in _currentWords list
    private val _wordSequence = MutableStateFlow<List<Int>>(emptyList())
    val wordSequence = _wordSequence.asStateFlow()

    // Reactive Combined Current Word State Flow (Eagerly started so currentWord.value is always up-to-date)
    val currentWord: StateFlow<Word?> = combine(
        _currentWords,
        _wordSequence,
        _currentSeqIndex
    ) { words, seq, pos ->
        if (words.isEmpty()) {
            null
        } else if (seq.isNotEmpty() && pos in seq.indices) {
            val actualIdx = seq[pos]
            words.getOrNull(actualIdx) ?: words.firstOrNull()
        } else {
            words.getOrNull(pos) ?: words.firstOrNull()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    private var projectCollectionJob: Job? = null
    private var lastCollectedProjectId: Long? = null
    private var lastCollectedSpeakerId: Long? = null

    // Visual elements: Real-time amplitude metrics from recorder
    val liveRms = audioRecorder.currentRms
    val liveDbfs = audioRecorder.currentDbfs
    val livePeak = audioRecorder.peakAmplitude
    val liveElapsedMs = audioRecorder.elapsedMs
    val activeSourceLabel = audioRecorder.activeSourceLabel
    val isClipping = audioRecorder.isClipping
    val isTooQuiet = audioRecorder.isTooQuiet
    val recordingWaveform = audioRecorder.waveformPoints
    val silenceProgress = audioRecorder.silenceProgress
    val isRecording = audioRecorder.isRecording

    // Playback state parameters
    private val _playingWordId = MutableStateFlow<Long?>(null)
    val playingWordId = _playingWordId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.bootstrapDefaultProjectIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Bootstrap error: ${e.message}", e)
            }

            launch {
                try {
                    allSpeakers.collect { speakers ->
                        if (_currentSpeaker.value == null && speakers.isNotEmpty()) {
                            _currentSpeaker.value = speakers.first()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(tag, "Error selecting default speaker", e)
                }
            }

            launch {
                try {
                    combine(currentProject, currentSpeaker) { project, speaker ->
                        project to speaker
                    }.collect { (project, speaker) ->
                        projectCollectionJob?.cancel()
                        if (project == null || speaker == null) {
                            _currentWords.value = emptyList()
                            _wordSequence.value = emptyList()
                        } else {
                            val isNewSessionTarget = lastCollectedProjectId != project.id || lastCollectedSpeakerId != speaker.id
                            lastCollectedProjectId = project.id
                            lastCollectedSpeakerId = speaker.id
                            projectCollectionJob = launch {
                                try {
                                    var isFirstEmissionForTarget = isNewSessionTarget
                                    repository.getWordsForProjectWithSpeaker(project.id, speaker.id).collect { words ->
                                        val prevWords = _currentWords.value
                                        val prevSelectedWordId = if (prevWords.isNotEmpty() && !isFirstEmissionForTarget) {
                                            getCurrentWord()?.id
                                        } else {
                                            null
                                        }
                                        _currentWords.value = words
                                        val structureChanged = prevWords.size != words.size ||
                                            prevWords.map { Triple(it.id, it.originalIndex, it.groupName) } !=
                                            words.map { Triple(it.id, it.originalIndex, it.groupName) }
                                        if (isFirstEmissionForTarget || structureChanged || (_wordSequence.value.isEmpty() && words.isNotEmpty())) {
                                            val wasFirst = isFirstEmissionForTarget
                                            val keepWord = !wasFirst && !structureChanged && prevSelectedWordId != null
                                            if (words.isNotEmpty()) {
                                                isFirstEmissionForTarget = false
                                            }
                                            if (wasFirst) {
                                                _onlyPendingWords.value = false
                                            }
                                            rebuildSequence(
                                                maintainCurrentWord = keepWord,
                                                targetWordId = prevSelectedWordId,
                                                forceReshuffle = false,
                                                restoreBookmark = wasFirst
                                            )
                                        } else if (_onlyPendingWords.value && words.none { !it.isRecorded || (it.qualityStatus != null && it.qualityStatus != "正常") }) {
                                            _onlyPendingWords.value = false
                                            rebuildSequence(
                                                maintainCurrentWord = true,
                                                targetWordId = prevSelectedWordId ?: getCurrentWord()?.id,
                                                forceReshuffle = false,
                                                restoreBookmark = false
                                            )
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e(tag, "Error loading words for project", e)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(tag, "Error observing project/speaker combo", e)
                }
            }

            launch {
                try {
                    combine(allProjects, allSpeakers, currentSpeaker, countsRefreshTrigger, _isSimpleMode) { projects, speakers, activeSpk, _, isSimple ->
                        Pair(Triple(projects, speakers, activeSpk), isSimple)
                    }.collect { (triple, isSimple) ->
                        val (projects, speakers, activeSpk) = triple
                        val (countsMap, summaryMap) = withContext(Dispatchers.IO) {
                            repository.getAllProjectsDashboardBatchSync(
                                projects = projects,
                                allSpeakers = speakers,
                                activeSpeakerId = activeSpk?.id,
                                ignoreSpeakerGroups = isSimple
                            )
                        }
                        _projectCounts.value = countsMap
                        _projectSummaries.value = summaryMap
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(tag, "Error watching project counts", e)
                }
            }
        }
    }

    fun setAutoNextEnabled(enabled: Boolean) {
        _autoNextEnabled.value = enabled
        sharedPrefs.edit().putBoolean("auto_next", enabled).apply()
        if (enabled) {
            _pushToTalkEnabled.value = false
            sharedPrefs.edit().putBoolean("push_to_talk", false).apply()
        }
    }

    fun setPushToTalkEnabled(enabled: Boolean) {
        _pushToTalkEnabled.value = enabled
        sharedPrefs.edit().putBoolean("push_to_talk", enabled).apply()
        if (enabled) {
            _autoNextEnabled.value = false
            sharedPrefs.edit().putBoolean("auto_next", false).apply()
        }
    }

    fun setAutoAdvanceOnHoldRelease(enabled: Boolean) {
        _autoAdvanceOnHoldRelease.value = enabled
        sharedPrefs.edit().putBoolean("auto_advance_on_hold_release", enabled).apply()
        wirelessLink.updatePromptPolicy { it.copy(autoAdvanceOnHoldRelease = enabled) }
    }

    fun setHoldOnQualityIssue(enabled: Boolean) {
        _holdOnQualityIssue.value = enabled
        sharedPrefs.edit().putBoolean("hold_on_quality_issue", enabled).apply()
        wirelessLink.updatePromptPolicy { it.copy(holdOnQualityIssueWhenAutoAdvance = enabled) }
    }

    fun clearHoldQualityNotice() {
        _lastHoldQualityNotice.value = null
    }

    fun setAutoPlayAfterRecord(enabled: Boolean) {
        _autoPlayAfterRecord.value = enabled
        sharedPrefs.edit().putBoolean("auto_play_after", enabled).apply()
    }

    fun setVolumeKeyControlEnabled(enabled: Boolean) {
        _volumeKeyControlEnabled.value = enabled
        sharedPrefs.edit().putBoolean("volume_key_ctrl", enabled).apply()
    }

    fun setParticipantCleanMode(enabled: Boolean) {
        _participantCleanMode.value = enabled
    }

    fun toggleParticipantCleanMode() {
        _participantCleanMode.value = !_participantCleanMode.value
    }

    fun setVadThreshold(value: Float) {
        _vadThreshold.value = value
        sharedPrefs.edit().putFloat("vad_threshold", value).apply()
    }

    fun applyVadSensitivityPreset(preset: String) {
        when (preset) {
            "高灵敏" -> {
                setVadThreshold(650f)
                setLowVolumeThreshold(180f)
            }
            "抗噪" -> {
                setVadThreshold(1900f)
                setLowVolumeThreshold(380f)
            }
            else -> {
                setVadThreshold(1200f)
                setLowVolumeThreshold(250f)
            }
        }
    }

    fun setOnlyPendingWords(enabled: Boolean) {
        val words = _currentWords.value
        val hasPending = words.any { !it.isRecorded || (it.qualityStatus != null && it.qualityStatus != "正常") }
        val finalEnabled = enabled && hasPending
        if (_onlyPendingWords.value == finalEnabled) return
        val curId = getCurrentWord()?.id
        _onlyPendingWords.value = finalEnabled
        rebuildSequence(maintainCurrentWord = true, targetWordId = curId, forceReshuffle = false, restoreBookmark = false)
    }

    fun toggleOnlyPendingWords(): Boolean {
        val next = !_onlyPendingWords.value
        setOnlyPendingWords(next)
        return _onlyPendingWords.value
    }

    fun setSilenceDelayMs(value: Long) {
        _silenceDelayMs.value = value
        sharedPrefs.edit().putLong("silence_delay", value).apply()
    }

    fun setCharacterSize(size: String) {
        _characterSize.value = size
        sharedPrefs.edit().putString("char_size", size).apply()
    }

    fun setLowVolumeThreshold(value: Float) {
        _lowVolumeThreshold.value = value
        sharedPrefs.edit().putFloat("low_volume", value).apply()
    }

    fun setCreakySensitivity(value: String) {
        _creakySensitivity.value = value
        sharedPrefs.edit().putString("creaky", value).apply()
    }

    fun showPhoneticWordDetail(word: Word?) {
        _showPhoneticWord.value = word
    }

    fun setShowGlobalManagement(show: Boolean) {
        _showGlobalManagement.value = show
    }

    fun setShufflingMode(mode: ShufflingMode) {
        if (mode == ShufflingMode.STANDARD) {
            setShuffleEnabled(false)
            return
        }
        _shufflingMode.value = mode
        sharedPrefs.edit().putString("shuffling_mode", mode.name).apply()
        if (!_isShuffleEnabled.value) {
            _isShuffleEnabled.value = true
            sharedPrefs.edit().putBoolean("shuffle_enabled", true).apply()
        }
        rebuildSequence(maintainCurrentWord = false, forceReshuffle = true)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        _isShuffleEnabled.value = enabled
        sharedPrefs.edit().putBoolean("shuffle_enabled", enabled).apply()
        if (enabled && _shufflingMode.value == ShufflingMode.STANDARD) {
            _shufflingMode.value = ShufflingMode.RANDOM_ALL
            sharedPrefs.edit().putString("shuffling_mode", ShufflingMode.RANDOM_ALL.name).apply()
        }
        rebuildSequence(maintainCurrentWord = false, forceReshuffle = enabled)
    }

    fun toggleShuffle() {
        setShuffleEnabled(!_isShuffleEnabled.value)
    }

    fun reshuffle() {
        if (!_isShuffleEnabled.value) {
            _isShuffleEnabled.value = true
            sharedPrefs.edit().putBoolean("shuffle_enabled", true).apply()
        }
        if (_shufflingMode.value == ShufflingMode.STANDARD) {
            _shufflingMode.value = ShufflingMode.RANDOM_ALL
            sharedPrefs.edit().putString("shuffling_mode", ShufflingMode.RANDOM_ALL.name).apply()
        }
        rebuildSequence(maintainCurrentWord = false, forceReshuffle = true)
    }

    /**
     * Shuffles [indices] while guaranteeing that for lists of size >= 2:
     * 1. The shuffled order is NEVER identical to the original [indices].
     * 2. The first element is not [avoidFirstIdx] (e.g., index 0 or the previously displayed word),
     *    so the user immediately sees a shuffled word at #001.
     */
    private fun shuffleNonIdentity(indices: List<Int>, avoidFirstIdx: Int? = 0): List<Int> {
        if (indices.size <= 1) return indices
        var candidate = indices.shuffled()
        var attempts = 0
        while (attempts < 16 && (candidate == indices || (avoidFirstIdx != null && candidate.firstOrNull() == avoidFirstIdx))) {
            candidate = indices.shuffled()
            attempts++
        }
        if (candidate == indices) {
            val mutable = candidate.toMutableList()
            val tmp = mutable[0]
            mutable[0] = mutable[1]
            mutable[1] = tmp
            candidate = mutable
        }
        if (avoidFirstIdx != null && candidate.firstOrNull() == avoidFirstIdx && candidate.size >= 2) {
            val mutable = candidate.toMutableList()
            val swapIdx = mutable.indexOfFirst { it != avoidFirstIdx }.takeIf { it > 0 } ?: 1
            val tmp = mutable[0]
            mutable[0] = mutable[swapIdx]
            mutable[swapIdx] = tmp
            candidate = mutable
        }
        return candidate
    }

    private fun saveSessionWordBookmark() {
        val projId = _currentProject.value?.id ?: return
        val spkId = _currentSpeaker.value?.id ?: return
        val wordId = getCurrentWord()?.id ?: return
        sharedPrefs.edit().putLong("last_word_${projId}_${spkId}", wordId).apply()
    }

    private fun rebuildSequence(
        maintainCurrentWord: Boolean = false,
        targetWordId: Long? = null,
        forceReshuffle: Boolean = false,
        restoreBookmark: Boolean = false
    ) {
        val wordList = _currentWords.value
        if (wordList.isEmpty()) {
            _wordSequence.value = emptyList()
            _currentSeqIndex.value = 0
            return
        }

        val projId = _currentProject.value?.id
        val spkId = _currentSpeaker.value?.id
        val prevDisplayedIdx = _wordSequence.value.getOrNull(_currentSeqIndex.value) ?: 0
        val indices = wordList.indices.toList()

        val baseSeq = if (_isShuffleEnabled.value) {
            val shufflePrefKey = if (projId != null && spkId != null) {
                "shuffle_ids_${projId}_${spkId}_${_shufflingMode.value.name}"
            } else {
                null
            }
            val idToIndex = HashMap<Long, Int>(wordList.size * 2)
            for (i in wordList.indices) {
                idToIndex[wordList[i].id] = i
            }

            val restoredSeq: List<Int>? = if (!forceReshuffle && shufflePrefKey != null) {
                val rawSaved = sharedPrefs.getString(shufflePrefKey, null)
                if (!rawSaved.isNullOrBlank()) {
                    val savedIds = rawSaved.split(',').mapNotNull { it.trim().toLongOrNull() }
                    if (savedIds.size == wordList.size && savedIds.all { idToIndex.containsKey(it) } && savedIds.distinct().size == wordList.size) {
                        savedIds.map { idToIndex.getValue(it) }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }

            if (restoredSeq != null) {
                restoredSeq
            } else {
                val freshlyShuffled = when (_shufflingMode.value) {
                    ShufflingMode.RANDOM_ALL, ShufflingMode.STANDARD -> {
                        shuffleNonIdentity(indices, avoidFirstIdx = prevDisplayedIdx)
                    }
                    ShufflingMode.BLOCK_BALANCED -> {
                        val groupIndexMap = linkedMapOf<String, MutableList<Int>>()
                        for (i in wordList.indices) {
                            val gName = wordList[i].groupName.trim()
                            groupIndexMap.getOrPut(gName) { mutableListOf() }.add(i)
                        }
                        val hasMultiItemGroup = groupIndexMap.values.any { it.size >= 2 }
                        if (groupIndexMap.size <= 1 || !hasMultiItemGroup) {
                            shuffleNonIdentity(indices, avoidFirstIdx = prevDisplayedIdx)
                        } else {
                            val sequence = mutableListOf<Int>()
                            var isFirstGroup = true
                            for ((_, grpIndices) in groupIndexMap) {
                                val avoid = if (isFirstGroup) prevDisplayedIdx else grpIndices.firstOrNull()
                                sequence.addAll(shuffleNonIdentity(grpIndices, avoidFirstIdx = avoid))
                                isFirstGroup = false
                            }
                            if (sequence == indices) {
                                shuffleNonIdentity(indices, avoidFirstIdx = prevDisplayedIdx)
                            } else {
                                sequence
                            }
                        }
                    }
                }
                if (shufflePrefKey != null) {
                    val serializedIds = freshlyShuffled.joinToString(",") { wordList[it].id.toString() }
                    sharedPrefs.edit().putString(shufflePrefKey, serializedIds).apply()
                }
                freshlyShuffled
            }
        } else {
            indices
        }

        val newSeq = if (_onlyPendingWords.value) {
            val pendingFiltered = baseSeq.filter { idx ->
                val w = wordList[idx]
                !w.isRecorded || (w.qualityStatus != null && w.qualityStatus != "正常")
            }
            if (pendingFiltered.isEmpty()) {
                _onlyPendingWords.value = false
                baseSeq
            } else {
                pendingFiltered
            }
        } else {
            baseSeq
        }

        _wordSequence.value = newSeq

        if (maintainCurrentWord && targetWordId != null) {
            val matchingSeqPos = newSeq.indexOfFirst { idx -> wordList[idx].id == targetWordId }
            _currentSeqIndex.value = if (matchingSeqPos != -1) matchingSeqPos else 0
        } else if (restoreBookmark && projId != null && spkId != null) {
            val savedWordId = sharedPrefs.getLong("last_word_${projId}_${spkId}", -1L)
            val bookmarkedPos = if (savedWordId > 0L) {
                newSeq.indexOfFirst { idx -> wordList[idx].id == savedWordId }
            } else {
                -1
            }
            if (bookmarkedPos != -1) {
                _currentSeqIndex.value = bookmarkedPos
            } else if (wordList.any { it.isRecorded } && wordList.any { !it.isRecorded }) {
                val firstUnrecPos = newSeq.indexOfFirst { idx -> !wordList[idx].isRecorded }
                _currentSeqIndex.value = if (firstUnrecPos != -1) firstUnrecPos else 0
            } else {
                _currentSeqIndex.value = 0
            }
        } else {
            _currentSeqIndex.value = 0
        }
    }

    fun getCurrentWord(): Word? {
        val words = _currentWords.value
        if (words.isEmpty()) return null
        val seq = _wordSequence.value
        val pos = _currentSeqIndex.value
        if (seq.isNotEmpty() && pos in seq.indices) {
            val idx = seq[pos]
            if (idx in words.indices) return words[idx]
        }
        return currentWord.value ?: words.getOrNull(pos) ?: words.firstOrNull()
    }

    fun selectWordBySequencePosition(pos: Int) {
        val seq = _wordSequence.value
        if (pos in seq.indices && _currentSeqIndex.value != pos) {
            stopRecordingForWordNavigation()
            _currentSeqIndex.value = pos
            stopPlayback()
            saveSessionWordBookmark()
        }
    }

    fun nextWord(): Boolean {
        val seq = _wordSequence.value
        val pos = _currentSeqIndex.value
        if (pos < seq.size - 1) {
            stopRecordingForWordNavigation()
            _currentSeqIndex.value = pos + 1
            stopPlayback()
            saveSessionWordBookmark()
            return true
        }
        return false
    }

    fun prevWord(): Boolean {
        val pos = _currentSeqIndex.value
        if (pos > 0) {
            stopRecordingForWordNavigation()
            _currentSeqIndex.value = pos - 1
            stopPlayback()
            saveSessionWordBookmark()
            return true
        }
        return false
    }

    /**
     * Jumps directly to the next unrecorded word in the current sequence (wraps around if needed).
     */
    fun jumpToNextUnrecorded(): Boolean {
        val words = _currentWords.value
        val seq = _wordSequence.value
        if (words.isEmpty() || seq.isEmpty()) return false
        val startPos = _currentSeqIndex.value
        for (offset in 1..seq.size) {
            val checkPos = (startPos + offset) % seq.size
            val wordIdx = seq[checkPos]
            val w = words.getOrNull(wordIdx)
            if (w != null && !w.isRecorded) {
                selectWordBySequencePosition(checkPos)
                return true
            }
        }
        return false
    }

    /**
     * Jumps directly to the next recorded word that has an abnormal quality warning (for rapid re-recording).
     */
    fun jumpToNextFlaggedWord(): Boolean {
        val words = _currentWords.value
        val seq = _wordSequence.value
        if (words.isEmpty() || seq.isEmpty()) return false
        val startPos = _currentSeqIndex.value
        for (offset in 1..seq.size) {
            val checkPos = (startPos + offset) % seq.size
            val wordIdx = seq[checkPos]
            val w = words.getOrNull(wordIdx)
            if (w != null && w.isRecorded && w.qualityStatus != null && w.qualityStatus != "正常") {
                selectWordBySequencePosition(checkPos)
                return true
            }
        }
        return false
    }

    fun getWordsForProjectFlow(projectId: Long): Flow<List<Word>> {
        return repository.getWordsForProject(projectId)
    }

    // Speakers selection and profiles management
    fun selectSpeaker(speaker: Speaker) {
        stopPlayback()
        _currentSpeaker.value = speaker
        val activeProj = _currentProject.value
        if (activeProj != null && activeProj.speakerId != speaker.id) {
            val updatedProj = activeProj.copy(speakerId = speaker.id)
            _currentProject.value = updatedProj
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateProject(updatedProj)
            }
        }
    }

    fun createSpeaker(
        name: String,
        gender: String,
        age: Int,
        dialect: String,
        notes: String,
        groupName: String = "",
        onFinished: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanGroup = normalizeSpeakerGroupsString(groupName)
            val newSpeakerId = repository.createSpeaker(name.trim(), gender, age, dialect.trim(), notes.trim(), cleanGroup)
            val newSpeaker = repository.getSpeakerById(newSpeakerId)
            forceRefreshCounts()
            withContext(Dispatchers.Main) {
                if (newSpeaker != null) {
                    selectSpeaker(newSpeaker)
                }
                onFinished()
            }
        }
    }

    fun deleteSpeaker(speaker: Speaker) {
        viewModelScope.launch(Dispatchers.IO) {
            // Also delete physical audio files of this speaker across all project directories
            val rootDir = context.getExternalFilesDir(null)
            rootDir?.listFiles { _, name -> name.startsWith("project_") }?.forEach { projDir ->
                projDir.listFiles { _, name -> name.contains("_speaker_${speaker.id}_") }?.forEach {
                    try { it.delete() } catch (ignored: Exception) {}
                }
            }
            repository.deleteSpeaker(speaker.id)
            withContext(Dispatchers.Main) {
                if (_currentSpeaker.value?.id == speaker.id) {
                    _currentSpeaker.value = allSpeakers.value.firstOrNull { it.id != speaker.id }
                }
            }
        }
    }

    fun deleteSpeakerRecordingsInProject(projectId: Long, speakerId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val projDir = File(context.getExternalFilesDir(null), "project_$projectId")
            if (projDir.exists()) {
                projDir.listFiles { _, name -> name.contains("_speaker_${speakerId}_") }?.forEach {
                    try { it.delete() } catch (ignored: Exception) {}
                }
            }
            val wordsList = repository.getWordsForProjectSync(projectId)
            for (w in wordsList) {
                val rec = repository.getSpeakerRecording(w.id, speakerId)
                if (rec != null) {
                    repository.deleteSpeakerRecording(w.id, speakerId)
                }
            }
            _takesRefreshTrigger.value++
            forceRefreshCounts()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    data class SpeakerStorageStat(
        val speaker: Speaker,
        val recordedCount: Int,
        val totalWords: Int,
        val totalDurationMs: Long,
        val activeBytes: Long,
        val backupTakeBytes: Long,
        val backupTakeCount: Int
    ) {
        val totalBytes: Long get() = activeBytes + backupTakeBytes
    }

    data class ProjectStorageStat(
        val project: Project,
        val totalWords: Int,
        val totalRecordedCount: Int,
        val totalDurationMs: Long,
        val activeBytes: Long,
        val backupTakeBytes: Long,
        val backupTakeCount: Int,
        val orphanBytes: Long,
        val orphanFileCount: Int,
        val speakerStats: List<SpeakerStorageStat>
    ) {
        val totalBytes: Long get() = activeBytes + backupTakeBytes + orphanBytes
    }

    data class CorpusStorageReport(
        val totalActiveRecordings: Int = 0,
        val totalDurationMs: Long = 0L,
        val activeSpeakersCount: Int = 0,
        val activeBytes: Long = 0L,
        val backupTakeBytes: Long = 0L,
        val backupTakeCount: Int = 0,
        val tempAndOrphanBytes: Long = 0L,
        val tempAndOrphanFileCount: Int = 0,
        val projectStats: Map<Long, ProjectStorageStat> = emptyMap()
    ) {
        val totalStorageBytes: Long get() = activeBytes + backupTakeBytes + tempAndOrphanBytes
    }

    private fun parseWordAndSpeakerFromWavName(fileName: String): Pair<Long, Long>? =
        parseWordAndSpeakerFromWavFileName(fileName)

    private fun computeDirectoryBytesAndCount(dir: File?): Pair<Long, Int> {
        if (dir == null || !dir.exists()) return 0L to 0
        var bytes = 0L
        var count = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                bytes += f.length()
                count++
            }
        }
        return bytes to count
    }

    suspend fun computeCorpusStorageReport(): CorpusStorageReport = withContext(Dispatchers.IO) {
        val projects = allProjects.value
        val speakers = allSpeakers.value
        val speakersById = speakers.associateBy { it.id }
        val summaries = _projectSummaries.value
        val entries = repository.getAllProjectRecordingEntries()

        val entriesByProject = entries.groupBy { it.projectId }
        val recordedSpeakerIdsGlobal = HashSet<Long>()

        var globalActiveRecordings = 0
        var globalDurationMs = 0L
        var globalActiveBytes = 0L
        var globalBackupBytes = 0L
        var globalBackupCount = 0
        var globalOrphanBytes = 0L
        var globalOrphanCount = 0

        val extRoot = context.getExternalFilesDir(null)
        val validProjectIds = projects.map { it.id }.toHashSet()

        val projectStatsMap = HashMap<Long, ProjectStorageStat>(projects.size * 2)

        for (proj in projects) {
            val projEntries = entriesByProject[proj.id] ?: emptyList()
            val summary = summaries[proj.id]
            val totalWords = summary?.totalWords ?: _projectCounts.value[proj.id]?.first ?: 0

            val activeFileToEntry = HashMap<String, com.example.data.ProjectRecordingEntryTuple>(projEntries.size * 2)
            val activePairSet = HashSet<Pair<Long, Long>>(projEntries.size * 2)
            val durationBySpeaker = HashMap<Long, Long>()
            val countBySpeaker = HashMap<Long, Int>()

            var projDurationMs = 0L
            for (e in projEntries) {
                if (e.recordingFile.isNotBlank()) {
                    activeFileToEntry[e.recordingFile] = e
                    activePairSet.add(e.wordId to e.speakerId)
                    recordedSpeakerIdsGlobal.add(e.speakerId)
                    val dur = e.durationMs.coerceAtLeast(0L)
                    projDurationMs += dur
                    durationBySpeaker[e.speakerId] = (durationBySpeaker[e.speakerId] ?: 0L) + dur
                    countBySpeaker[e.speakerId] = (countBySpeaker[e.speakerId] ?: 0) + 1
                }
            }

            globalActiveRecordings += activeFileToEntry.size
            globalDurationMs += projDurationMs

            val activeBytesBySpeaker = HashMap<Long, Long>()
            val backupBytesBySpeaker = HashMap<Long, Long>()
            val backupCountBySpeaker = HashMap<Long, Int>()

            var projActiveBytes = 0L
            var projBackupBytes = 0L
            var projBackupCount = 0
            var projOrphanBytes = 0L
            var projOrphanCount = 0

            val projDir = if (extRoot != null) File(extRoot, "project_${proj.id}") else null
            if (projDir != null && projDir.exists()) {
                projDir.listFiles()?.forEach { file ->
                    if (!file.isFile) return@forEach
                    val len = file.length()
                    val activeEntry = activeFileToEntry[file.name]
                    if (activeEntry != null) {
                        projActiveBytes += len
                        activeBytesBySpeaker[activeEntry.speakerId] =
                            (activeBytesBySpeaker[activeEntry.speakerId] ?: 0L) + len
                    } else {
                        val parsedPair = parseWordAndSpeakerFromWavName(file.name)
                        if (parsedPair != null && parsedPair in activePairSet && len > 44L) {
                            projBackupBytes += len
                            projBackupCount++
                            val sid = parsedPair.second
                            backupBytesBySpeaker[sid] = (backupBytesBySpeaker[sid] ?: 0L) + len
                            backupCountBySpeaker[sid] = (backupCountBySpeaker[sid] ?: 0) + 1
                        } else {
                            projOrphanBytes += len
                            projOrphanCount++
                        }
                    }
                }
                val imagesDir = File(projDir, WordImageManager.IMAGES_SUBDIR)
                if (imagesDir.exists() && imagesDir.isDirectory) {
                    val referencedImages = repository.getWordsForProjectSync(proj.id)
                        .mapNotNull { it.imagePath.takeIf { p -> p.isNotBlank() } }
                        .toHashSet()
                    imagesDir.listFiles()?.forEach { imgFile ->
                        if (!imgFile.isFile) return@forEach
                        val len = imgFile.length()
                        if (imgFile.name in referencedImages) {
                            projActiveBytes += len
                        } else {
                            projOrphanBytes += len
                            projOrphanCount++
                        }
                    }
                }
            }

            globalActiveBytes += projActiveBytes
            globalBackupBytes += projBackupBytes
            globalBackupCount += projBackupCount
            globalOrphanBytes += projOrphanBytes
            globalOrphanCount += projOrphanCount

            // Build per-speaker stats for all speakers who have recordings or belong to the project group
            val candidateSpeakerIds = LinkedHashSet<Long>()
            summary?.speakerProgressList?.forEach { candidateSpeakerIds.add(it.speaker.id) }
            countBySpeaker.keys.forEach { candidateSpeakerIds.add(it) }

            val spkStatList = ArrayList<SpeakerStorageStat>(candidateSpeakerIds.size)
            for (sid in candidateSpeakerIds) {
                val spk = speakersById[sid] ?: continue
                val recCnt = countBySpeaker[sid] ?: 0
                val actB = activeBytesBySpeaker[sid] ?: 0L
                val bakB = backupBytesBySpeaker[sid] ?: 0L
                val bakCnt = backupCountBySpeaker[sid] ?: 0
                if (recCnt > 0 || actB > 0L || bakB > 0L) {
                    spkStatList.add(
                        SpeakerStorageStat(
                            speaker = spk,
                            recordedCount = recCnt,
                            totalWords = totalWords,
                            totalDurationMs = durationBySpeaker[sid] ?: 0L,
                            activeBytes = actB,
                            backupTakeBytes = bakB,
                            backupTakeCount = bakCnt
                        )
                    )
                }
            }
            spkStatList.sortByDescending { it.recordedCount }

            projectStatsMap[proj.id] = ProjectStorageStat(
                project = proj,
                totalWords = totalWords,
                totalRecordedCount = activeFileToEntry.size,
                totalDurationMs = projDurationMs,
                activeBytes = projActiveBytes,
                backupTakeBytes = projBackupBytes,
                backupTakeCount = projBackupCount,
                orphanBytes = projOrphanBytes,
                orphanFileCount = projOrphanCount,
                speakerStats = spkStatList
            )
        }

        // Check for orphaned project_* directories whose projects were deleted
        extRoot?.listFiles { _, name -> name.startsWith("project_") }?.forEach { dir ->
            val pid = dir.name.removePrefix("project_").toLongOrNull()
            if (pid == null || pid !in validProjectIds) {
                val (b, c) = computeDirectoryBytesAndCount(dir)
                globalOrphanBytes += b
                globalOrphanCount += c
            }
        }

        // Check export cache & zip_import temp directories in cacheDir
        val exportCacheDir = File(context.cacheDir, "exports")
        val (expBytes, expCount) = computeDirectoryBytesAndCount(exportCacheDir)
        globalOrphanBytes += expBytes
        globalOrphanCount += expCount

        context.cacheDir.listFiles { _, name -> name.startsWith("zip_import_") }?.forEach { dir ->
            val (b, c) = computeDirectoryBytesAndCount(dir)
            globalOrphanBytes += b
            globalOrphanCount += c
        }

        CorpusStorageReport(
            totalActiveRecordings = globalActiveRecordings,
            totalDurationMs = globalDurationMs,
            activeSpeakersCount = recordedSpeakerIdsGlobal.size,
            activeBytes = globalActiveBytes,
            backupTakeBytes = globalBackupBytes,
            backupTakeCount = globalBackupCount,
            tempAndOrphanBytes = globalOrphanBytes,
            tempAndOrphanFileCount = globalOrphanCount,
            projectStats = projectStatsMap
        )
    }

    /**
     * Safely deletes temporary export ZIP/TextGrid caches and unreferenced orphan files
     * without touching any active recording or valid backup take.
     */
    fun cleanTempAndOrphanFiles(onComplete: (freedBytes: Long, deletedCount: Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var freedBytes = 0L
            var deletedCount = 0

            // 1. Clean cacheDir/exports and zip_import_*
            val exportCacheDir = File(context.cacheDir, "exports")
            if (exportCacheDir.exists()) {
                exportCacheDir.walkBottomUp().forEach { f ->
                    if (f.isFile) {
                        val len = f.length()
                        if (f.delete()) {
                            freedBytes += len
                            deletedCount++
                        }
                    }
                }
            }
            context.cacheDir.listFiles { _, name -> name.startsWith("zip_import_") }?.forEach { dir ->
                val (b, c) = computeDirectoryBytesAndCount(dir)
                if (dir.deleteRecursively()) {
                    freedBytes += b
                    deletedCount += c
                }
            }

            // 2. Clean orphaned project_* directories and unreferenced files inside valid project_* directories
            val validProjectIds = allProjects.value.map { it.id }.toHashSet()
            val entriesByProject = repository.getAllProjectRecordingEntries().groupBy { it.projectId }
            val extRoot = context.getExternalFilesDir(null)

            extRoot?.listFiles { _, name -> name.startsWith("project_") }?.forEach { dir ->
                val pid = dir.name.removePrefix("project_").toLongOrNull()
                if (pid == null || pid !in validProjectIds) {
                    val (b, c) = computeDirectoryBytesAndCount(dir)
                    if (dir.deleteRecursively()) {
                        freedBytes += b
                        deletedCount += c
                    }
                } else {
                    val projEntries = entriesByProject[pid] ?: emptyList()
                    val activeNames = projEntries.mapTo(HashSet()) { it.recordingFile }
                    val activePairs = projEntries.mapTo(HashSet()) { it.wordId to it.speakerId }
                    dir.listFiles()?.forEach { file ->
                        if (!file.isFile) return@forEach
                        if (file.name !in activeNames) {
                            val pair = parseWordAndSpeakerFromWavName(file.name)
                            val isValidBackup = pair != null && pair in activePairs && file.length() > 44L
                            if (!isValidBackup) {
                                val len = file.length()
                                if (file.delete()) {
                                    freedBytes += len
                                    deletedCount++
                                }
                            }
                        }
                    }
                    val referencedImages = repository.getWordsForProjectSync(pid)
                        .mapNotNull { it.imagePath.takeIf { p -> p.isNotBlank() } }
                        .toHashSet()
                    val (imgFreed, imgCnt) = WordImageManager.pruneUnreferencedProjectImages(context, pid, referencedImages)
                    freedBytes += imgFreed
                    deletedCount += imgCnt
                }
            }

            // Clean temporary link image cache & stale draft images (> 24h old)
            val linkImgDir = WordImageManager.getLinkImageCacheDir(context)
            if (linkImgDir.exists() && _currentProject.value == null) {
                linkImgDir.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        val len = f.length()
                        if (f.delete()) {
                            freedBytes += len
                            deletedCount++
                        }
                    }
                }
            }
            val cutoffMs = System.currentTimeMillis() - 24L * 3600L * 1000L
            WordImageManager.getDraftStagingDir(context).listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoffMs) {
                    val len = f.length()
                    if (f.delete()) {
                        freedBytes += len
                        deletedCount++
                    }
                }
            }

            invalidateProjectTakesIndex()
            _takesRefreshTrigger.value++
            withContext(Dispatchers.Main) {
                onComplete(freedBytes, deletedCount)
            }
        }
    }

    /**
     * Safely deletes older backup takes (Multi-Takes) while strictly preserving the active selected `.wav`
     * for every recorded word. If [targetProjectId] is null, cleans backup takes across all projects.
     */
    fun cleanBackupTakes(
        targetProjectId: Long? = null,
        onComplete: (freedBytes: Long, deletedCount: Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var freedBytes = 0L
            var deletedCount = 0

            val entriesByProject = repository.getAllProjectRecordingEntries().groupBy { it.projectId }
            val targetProjects = if (targetProjectId != null) {
                allProjects.value.filter { it.id == targetProjectId }
            } else {
                allProjects.value
            }
            val extRoot = context.getExternalFilesDir(null)

            for (proj in targetProjects) {
                val projDir = if (extRoot != null) File(extRoot, "project_${proj.id}") else null
                if (projDir == null || !projDir.exists()) continue

                val projEntries = entriesByProject[proj.id] ?: emptyList()
                val activeNames = projEntries.mapTo(HashSet()) { it.recordingFile }
                val activePairs = projEntries.mapTo(HashSet()) { it.wordId to it.speakerId }

                projDir.listFiles()?.forEach { file ->
                    if (!file.isFile) return@forEach
                    if (file.name !in activeNames) {
                        val pair = parseWordAndSpeakerFromWavName(file.name)
                        if (pair != null && pair in activePairs) {
                            val len = file.length()
                            if (file.delete()) {
                                freedBytes += len
                                deletedCount++
                            }
                        }
                    }
                }
            }

            sessionOriginalRecordings.clear()
            invalidateProjectTakesIndex(targetProjectId)
            _takesRefreshTrigger.value++
            withContext(Dispatchers.Main) {
                onComplete(freedBytes, deletedCount)
            }
        }
    }

    fun updateSpeaker(speaker: Speaker, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedSpeaker = speaker.copy(groupName = normalizeSpeakerGroupsString(speaker.groupName))
            repository.updateSpeaker(normalizedSpeaker)
            forceRefreshCounts()
            withContext(Dispatchers.Main) {
                if (_currentSpeaker.value?.id == normalizedSpeaker.id) {
                    _currentSpeaker.value = normalizedSpeaker
                }
                onComplete()
            }
        }
    }

    fun getSpeakerPitchRange(speakerId: Long): Pair<Float, Float>? {
        val sp = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
        val min = sp.getFloat("pitch_min_$speakerId", -1f)
        val max = sp.getFloat("pitch_max_$speakerId", -1f)
        return if (min > 40f && max > min + 15f) Pair(min, max) else null
    }

    fun saveSpeakerPitchRange(speakerId: Long, min: Float, max: Float, isAuto: Boolean = false) {
        context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("pitch_min_$speakerId", min)
            .putFloat("pitch_max_$speakerId", max)
            .putBoolean("pitch_auto_$speakerId", isAuto)
            .apply()
        com.example.audio.AcousticAnalyzer.clearCache()
    }

    fun clearSpeakerPitchRange(speakerId: Long) {
        context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE).edit()
            .remove("pitch_min_$speakerId")
            .remove("pitch_max_$speakerId")
            .remove("pitch_auto_$speakerId")
            .apply()
        com.example.audio.AcousticAnalyzer.clearCache()
    }

    private fun computeSpeakerPitchSpanSync(speaker: Speaker): Pair<Float, Float>? {
        val rootDir = context.getExternalFilesDir(null)
        val allWavs = rootDir?.listFiles { _, name -> name.startsWith("project_") }
            ?.flatMap { it.listFiles()?.toList() ?: emptyList() }
            ?.filter { it.name.endsWith(".wav") && it.name.contains("_speaker_${speaker.id}_") }
            ?: emptyList()

        val allVoicedPitches = mutableListOf<Float>()
        for (file in allWavs) {
            if (!file.exists() || file.length() < 1024) continue
            try {
                val analysis = AcousticAnalyzer.analyzeByGender(file, speaker.gender)
                if (analysis != null && analysis.isValidVoiced && analysis.rawVoicedPitches.isNotEmpty()) {
                    val raw = analysis.rawVoicedPitches
                    val sIdx = (raw.size * 0.20f).toInt().coerceIn(0, raw.size - 1)
                    val eIdx = (raw.size * 0.78f).toInt().coerceIn(sIdx, raw.size - 1)
                    for (idx in sIdx..eIdx) {
                        val hz = raw[idx]
                        if (hz in 72f..480f) {
                            allVoicedPitches.add(hz)
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        if (allVoicedPitches.size >= 8) {
            val sorted = allVoicedPitches.sorted()
            var pLow = sorted[(sorted.size * 0.08).toInt().coerceIn(0, sorted.size - 1)].coerceAtLeast(60f)
            var pHigh = sorted[(sorted.size * 0.92).toInt().coerceIn(0, sorted.size - 1)].coerceAtLeast(pLow + 25f)

            val logSpan = kotlin.math.log10(pHigh.toDouble() / pLow.toDouble())
            if (logSpan < 0.168) {
                val geoMean = kotlin.math.sqrt((pLow * pHigh).toDouble()).toFloat()
                val normG = speaker.gender.trim()
                val regCenter = when {
                    normG == "女" || geoMean >= 175f -> 205f
                    normG == "男" || geoMean <= 145f -> 130f
                    else -> 165f
                }
                val anchoredCenter = geoMean * 0.65f + regCenter * 0.35f
                val halfSpanFactor = Math.pow(10.0, 0.168 / 2.0).toFloat()
                pLow = minOf(pLow, anchoredCenter / halfSpanFactor).coerceAtLeast(60f)
                pHigh = maxOf(pHigh, anchoredCenter * halfSpanFactor).coerceAtLeast(pLow + 38f)
            }
            return Pair(pLow, pHigh)
        }
        return null
    }

    private fun maybeAutoCalibrateSpeakerPitch(speakerId: Long) {
        val sp = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
        val existing = getSpeakerPitchRange(speakerId)
        val isAuto = sp.getBoolean("pitch_auto_$speakerId", true)
        if (existing != null && !isAuto) return

        viewModelScope.launch(Dispatchers.IO) {
            val wavCount = projectSummaries.value.values.sumOf { summary ->
                summary.speakerProgressList.firstOrNull { it.speaker.id == speakerId }?.recordedCount ?: 0
            }.let { fromSummary ->
                if (fromSummary > 0) {
                    fromSummary
                } else {
                    val activeProjId = _currentProject.value?.id
                    if (activeProjId != null) {
                        getOrBuildProjectTakesIndex(activeProjId).keys.count { it.endsWith("_speaker_$speakerId") }
                    } else 0
                }
            }

            val shouldRun = (existing == null && wavCount >= 5) || wavCount == 12 || wavCount == 25
            if (!shouldRun) return@launch

            val spk = allSpeakers.value.firstOrNull { it.id == speakerId } ?: repository.getSpeakerById(speakerId) ?: return@launch
            val span = computeSpeakerPitchSpanSync(spk)
            if (span != null) {
                saveSpeakerPitchRange(spk.id, span.first, span.second, isAuto = true)
            }
        }
    }

    /**
     * Academically rigorous speaker pitch span calibration:
     * Aggregates all voiced F0 frames across all recorded tokens of this speaker
     * and computes the 5th to 95th percentile distribution (excluding octave-jump outliers).
     */
    fun estimatePitchRangeForSpeaker(speaker: Speaker, onComplete: (Pair<Float, Float>?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val span = computeSpeakerPitchSpanSync(speaker)
            withContext(Dispatchers.Main) {
                if (span != null) {
                    saveSpeakerPitchRange(speaker.id, span.first, span.second, isAuto = false)
                    onComplete(span)
                } else {
                    onComplete(null)
                }
            }
        }
    }

    private data class ActiveCaptureContext(
        val token: Long,
        val word: Word,
        val project: Project,
        val speaker: Speaker,
        val destFile: File,
        val seqPos: Int
    )

    private val captureContextLock = Any()
    @Volatile
    private var activeCaptureContext: ActiveCaptureContext? = null

    private fun consumeActiveCaptureContext(): ActiveCaptureContext? {
        synchronized(captureContextLock) {
            val cur = activeCaptureContext
            activeCaptureContext = null
            return cur
        }
    }

    // Audio Capture Trigger Management
    fun toggleRecording() {
        if (audioRecorder.isRecording.value) {
            triggerStopRecording()
        } else {
            triggerStartRecording()
        }
    }

    fun triggerStartRecording() {
        if (audioRecorder.isRecording.value) return
        val project = _currentProject.value ?: allProjects.value.firstOrNull()?.also { _currentProject.value = it } ?: return
        val speaker = _currentSpeaker.value ?: allSpeakers.value.firstOrNull()?.also { _currentSpeaker.value = it } ?: return
        val word = getCurrentWord() ?: return
        val seqPosNow = _currentSeqIndex.value

        stopPlayback()

        val projectDir = File(context.getExternalFilesDir(null), "project_${project.id}")
        val filename = "word_${word.id}_speaker_${speaker.id}_${System.currentTimeMillis()}.wav"
        val destFile = File(projectDir, filename)

        audioRecorder.setLowVolumeThreshold(_lowVolumeThreshold.value)
        audioRecorder.configureHardware(
            targetSampleRate = _sampleRateHz.value,
            sourceStrategy = _audioSourceStrategy.value,
            enablePreRoll = _preRollEnabled.value
        )
        val token = audioRecorder.startRecording(
            outputFile = destFile,
            autoNextEnabled = _autoNextEnabled.value,
            vadThreshold = _vadThreshold.value,
            silenceDurationMs = _silenceDelayMs.value,
            onAutoNextTriggered = {
                triggerStopRecordingAndAdvance()
            }
        )
        synchronized(captureContextLock) {
            activeCaptureContext = ActiveCaptureContext(
                token = token,
                word = word,
                project = project,
                speaker = speaker,
                destFile = destFile,
                seqPos = seqPosNow
            )
        }
    }

    /**
     * Stops any in-progress recording immediately (0ms hangover) when the user explicitly
     * switches to another word, flushing and saving the previous word's take without bleeding
     * into or ghosting the recording state on the newly selected word.
     */
    fun stopRecordingForWordNavigation() {
        val snap = consumeActiveCaptureContext()
        if (snap == null && !audioRecorder.isRecording.value) return
        audioRecorder.markStoppingImmediate(snap?.token ?: 0L)
        viewModelScope.launch(Dispatchers.IO) {
            stopAndPersistLocalRecordingInternal(
                tailHangoverMs = 0L,
                preConsumedContext = snap
            )
        }
    }

    // Session tracking for "Reset Session" & Multi-Take retention
    private val sessionOriginalRecordings = mutableMapOf<Long, SpeakerRecording?>()
    private var sessionActiveSpeakerId: Long? = null
    private var sessionActiveProjectId: Long? = null

    private val _takesRefreshTrigger = MutableStateFlow(0)
    val takesRefreshTrigger = _takesRefreshTrigger.asStateFlow()

    private val _isContinuousPlaying = MutableStateFlow(false)
    val isContinuousPlaying = _isContinuousPlaying.asStateFlow()
    private var continuousQueue: List<Word> = emptyList()
    private var continuousIndex: Int = -1

    // O(1) per-project WAV take index cache so swiping Pager or checking takes never calls File.listFiles() repeatedly
    private val projectTakesIndexCache = ConcurrentHashMap<Long, Map<String, List<File>>>()
    private val projectTakesLocks = ConcurrentHashMap<Long, Any>()

    private fun extractWavTimestamp(file: File): Long =
        extractWavTimestampFromFileName(file.name, file.lastModified())

    private fun invalidateProjectTakesIndex(projectId: Long? = null) {
        if (projectId == null) {
            projectTakesIndexCache.clear()
        } else {
            projectTakesIndexCache.remove(projectId)
        }
    }

    fun notifyExternalAudioImported(projectId: Long? = null) {
        invalidateProjectTakesIndex(projectId)
        _takesRefreshTrigger.value++
        forceRefreshCounts()
    }

    private fun getOrBuildProjectTakesIndex(projectId: Long): Map<String, List<File>> {
        projectTakesIndexCache[projectId]?.let { return it }
        val lock = projectTakesLocks.getOrPut(projectId) { Any() }
        synchronized(lock) {
            projectTakesIndexCache[projectId]?.let { return it }
            val projectDir = File(context.getExternalFilesDir(null), "project_$projectId")
            if (!projectDir.exists()) {
                val empty = emptyMap<String, List<File>>()
                projectTakesIndexCache[projectId] = empty
                return empty
            }
            val rawFiles = projectDir.listFiles { _, name -> name.endsWith(".wav") && name.startsWith("word_") }
                ?: emptyArray()
            val grouped = HashMap<String, MutableList<File>>(rawFiles.size.coerceAtLeast(16))
            for (f in rawFiles) {
                if (!f.isFile || f.length() <= 44L) continue
                // Supports both standard `word_<wordId>_speaker_<speakerId>_<ts>.wav`
                // and imported `.teproj` `word_<wordId>_speaker_<speakerId>_<ts>_<seq>.wav`
                val pair = parseWordAndSpeakerFromWavFileName(f.name) ?: continue
                val key = "word_${pair.first}_speaker_${pair.second}"
                grouped.getOrPut(key) { ArrayList(3) }.add(f)
            }
            val sortedMap = HashMap<String, List<File>>(grouped.size)
            for ((k, list) in grouped) {
                sortedMap[k] = if (list.size <= 1) {
                    list
                } else {
                    list.sortedWith(
                        compareByDescending<File> { extractWavTimestamp(it) }
                            .thenByDescending { it.lastModified() }
                            .thenByDescending { it.name }
                    )
                }
            }
            projectTakesIndexCache[projectId] = sortedMap
            return sortedMap
        }
    }

    /**
     * Retains up to [maxTakes] most recent `.wav` files for a `(wordId, speakerId)` pair,
     * AND always preserves the session-original `.wav` file so "Reset Session" never points to a deleted file.
     */
    internal fun pruneOldTakesKeepingRecent(
        files: List<File>,
        newestFile: File,
        wordId: Long,
        projectId: Long,
        maxTakes: Int = _maxRetainedTakes.value
    ) {
        if (files.size <= maxTakes) return
        val protectedOriginalName = sessionOriginalRecordings[wordId]?.recordingFile
        val sortedNewestFirst = files.sortedByDescending {
            if (it.name == newestFile.name) Long.MAX_VALUE else extractWavTimestamp(it)
        }
        var deletedAny = false
        sortedNewestFirst.drop(maxTakes).forEach { file ->
            if (file.name != newestFile.name && file.name != protectedOriginalName) {
                try {
                    if (file.delete()) deletedAny = true
                } catch (ignored: Exception) {}
            }
        }
        if (deletedAny) {
            invalidateProjectTakesIndex(projectId)
        }
    }

    fun getAvailableTakesForWord(
        projectId: Long,
        wordId: Long,
        speakerId: Long,
        forceRescan: Boolean = false
    ): List<File> {
        if (forceRescan) {
            invalidateProjectTakesIndex(projectId)
        }
        val index = getOrBuildProjectTakesIndex(projectId)
        val key = "word_${wordId}_speaker_${speakerId}"
        val indexedValid = index[key].orEmpty().filter { it.exists() && it.length() > 44L }
        val maxLimit = _maxRetainedTakes.value.coerceIn(1, 10)

        // Ensure the currently active recordingFile (if custom-named on import) is always present
        val activeName = if (_currentProject.value?.id == projectId && _currentSpeaker.value?.id == speakerId) {
            _currentWords.value.find { it.id == wordId }?.recordingFile
        } else {
            null
        }
        if (!activeName.isNullOrBlank() && indexedValid.none { it.name == activeName }) {
            val explicitFile = File(File(context.getExternalFilesDir(null), "project_$projectId"), activeName)
            if (explicitFile.exists() && explicitFile.length() > 44L) {
                return (listOf(explicitFile) + indexedValid).take(maxLimit)
            }
        }
        return indexedValid.take(maxLimit)
    }

    fun selectTakeForWord(word: Word, takeFile: File) {
        val project = _currentProject.value ?: return
        val speaker = _currentSpeaker.value ?: return
        if (!takeFile.exists() || takeFile.length() <= 44L) return

        stopPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            val report = SpeechQualityEvaluator.evaluate(takeFile, _lowVolumeThreshold.value)
            val exactDurMs = report.totalDurationMs.toLong().coerceAtLeast(100L)
            val estPeak = report.peakAmplitude.coerceIn(0, 32767)
            val estRms = report.avgSpeechRms.coerceAtLeast(0f)
            val finalStatus = determineDetailedQuality(
                initialStatus = report.primaryStatus,
                peak = estPeak,
                avgRms = estRms,
                durationMs = exactDurMs,
                file = takeFile,
                speakerGender = speaker.gender
            )
            repository.saveSpeakerRecording(
                wordId = word.id,
                speakerId = speaker.id,
                recordingFile = takeFile.name,
                durationMs = exactDurMs,
                qualityStatus = finalStatus,
                avgRms = estRms,
                peakAmplitude = estPeak
            )
            _takesRefreshTrigger.value++
            forceRefreshCounts()
            withContext(Dispatchers.Main) {
                if (_showPhoneticWord.value?.id == word.id) {
                    _showPhoneticWord.value = word.copy(
                        isRecorded = true,
                        recordingFile = takeFile.name,
                        durationMs = exactDurMs,
                        qualityStatus = finalStatus,
                        avgRms = estRms,
                        peakAmplitude = estPeak
                    )
                }
            }
        }
    }

    /**
     * Manually toggles a recorded word's quality status between "正常" and "人工标重录".
     * - If the word currently has an automatic warning ("削波/过载", "嘎裂声", "音量过低", "起止截断", "极短/空音") or "人工标重录",
     *   tapping this confirms/overrides it to "正常".
     * - If the word is currently "正常", tapping this marks it as "人工标重录" so it appears in the "建议重录" filter.
     */
    fun toggleManualQualityFlag(word: Word) {
        val speaker = _currentSpeaker.value ?: return
        val recFile = word.recordingFile ?: return
        if (!word.isRecorded) return

        val currentStatus = word.qualityStatus ?: "正常"
        val newStatus = if (currentStatus == "正常") "人工标重录" else "正常"

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSpeakerRecording(
                wordId = word.id,
                speakerId = speaker.id,
                recordingFile = recFile,
                durationMs = word.durationMs.coerceAtLeast(100L),
                qualityStatus = newStatus,
                avgRms = word.avgRms,
                peakAmplitude = word.peakAmplitude
            )
            forceRefreshCounts()
            withContext(Dispatchers.Main) {
                if (_showPhoneticWord.value?.id == word.id) {
                    _showPhoneticWord.value = word.copy(qualityStatus = newStatus)
                }
            }
        }
    }

    fun updateWordRemark(word: Word, newRemark: String) {
        val cleanRemark = newRemark.trim()
        viewModelScope.launch(Dispatchers.IO) {
            val baseWord = repository.getWordById(word.id) ?: word
            repository.updateWord(baseWord.copy(remark = cleanRemark))
            withContext(Dispatchers.Main) {
                if (_showPhoneticWord.value?.id == word.id) {
                    _showPhoneticWord.value = _showPhoneticWord.value?.copy(remark = cleanRemark)
                }
            }
        }
    }

    fun updateSpeakerWordNote(word: Word, newNote: String) {
        val speaker = _currentSpeaker.value ?: return
        val cleanNote = newNote.trim()
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSpeakerWordNote(word.id, speaker.id, cleanNote)
            withContext(Dispatchers.Main) {
                if (_showPhoneticWord.value?.id == word.id) {
                    _showPhoneticWord.value = _showPhoneticWord.value?.copy(speakerNote = cleanNote)
                }
            }
        }
    }

    fun updateWordNoteAndRemark(word: Word, newSpeakerNote: String, newRemark: String = word.remark) {
        val speaker = _currentSpeaker.value
        val cleanSpeakerNote = newSpeakerNote.trim()
        val cleanRemark = newRemark.trim()
        viewModelScope.launch(Dispatchers.IO) {
            if (speaker != null) {
                repository.saveSpeakerWordNote(word.id, speaker.id, cleanSpeakerNote)
            }
            if (cleanRemark != word.remark.trim()) {
                val baseWord = repository.getWordById(word.id) ?: word
                repository.updateWord(baseWord.copy(remark = cleanRemark))
            }
            withContext(Dispatchers.Main) {
                if (_showPhoneticWord.value?.id == word.id) {
                    _showPhoneticWord.value = _showPhoneticWord.value?.copy(
                        speakerNote = cleanSpeakerNote,
                        remark = cleanRemark
                    )
                }
            }
        }
    }

    suspend fun getCrossSpeakerRecordingsForWord(wordId: Long): List<Pair<Speaker, SpeakerRecording>> =
        withContext(Dispatchers.IO) {
            val recs = repository.getRecordingsForWord(wordId)
            val speakersById = allSpeakers.value.associateBy { it.id }
            recs.mapNotNull { rec ->
                val spk = speakersById[rec.speakerId] ?: repository.getSpeakerById(rec.speakerId)
                if (spk != null && rec.recordingFile.isNotBlank()) {
                    spk to rec
                } else {
                    null
                }
            }.sortedBy { it.first.id }
        }

    suspend fun getWordsForSpeakerSync(projectId: Long, speakerId: Long): List<Word> =
        withContext(Dispatchers.IO) {
            if (speakerId > 0L) {
                repository.getWordsForProjectWithSpeakerSync(projectId, speakerId)
            } else {
                repository.getWordsForProjectSync(projectId)
            }
        }

    suspend fun getProjectMultiSpeakerWordPairs(projectId: Long): List<Pair<Speaker, List<Word>>> =
        withContext(Dispatchers.IO) {
            val speakers = allSpeakers.value
            val result = ArrayList<Pair<Speaker, List<Word>>>()
            for (spk in speakers) {
                val spkWords = repository.getWordsForProjectWithSpeakerSync(projectId, spk.id)
                if (spkWords.any { it.isRecorded && !it.recordingFile.isNullOrEmpty() }) {
                    result.add(spk to spkWords)
                }
            }
            result
        }

    private fun checkSessionContext(speakerId: Long, projectId: Long) {
        if (sessionActiveSpeakerId != speakerId || sessionActiveProjectId != projectId) {
            sessionOriginalRecordings.clear()
            sessionActiveSpeakerId = speakerId
            sessionActiveProjectId = projectId
        }
    }

    internal fun determineDetailedQuality(
        initialStatus: String,
        peak: Int,
        avgRms: Float,
        durationMs: Long,
        file: File,
        speakerGender: String
    ): String {
        // 1. Evaluate waveform boundary truncation, clipping/flat-top, empty/short, low volume, and SNR first
        val eval = SpeechQualityEvaluator.evaluate(file, _lowVolumeThreshold.value)
        if (eval.primaryStatus != "正常") {
            return eval.primaryStatus
        }
        if (initialStatus != "正常" && initialStatus != "未录制") {
            return initialStatus
        }

        // 2. Only when primary capture boundaries & levels are healthy, check optional F0 creaky phonation
        if (_creakySensitivity.value != "关闭") {
            try {
                val analysis = AcousticAnalyzer.analyzeByGender(file, speakerGender)
                if (analysis != null) {
                    if (!analysis.isValidVoiced && eval.avgSpeechRms < _lowVolumeThreshold.value * 1.15f) {
                        return "音量过低"
                    }
                    val isCreaky = when (_creakySensitivity.value) {
                        "低" -> analysis.creakyBreaks >= 3 && analysis.jitterPercent >= 6.5f && analysis.shimmerPercent >= 16.0f
                        "高" -> analysis.creakyBreaks >= 1 && analysis.jitterPercent >= 3.6f && analysis.shimmerPercent >= 10.5f
                        else -> analysis.creakyBreaks >= 2 && analysis.jitterPercent >= 4.8f && analysis.shimmerPercent >= 13.0f
                    }
                    if (isCreaky) return "嘎裂声"
                }
            } catch (e: Exception) {
                Log.e(tag, "Acoustic evaluation error: ${e.message}")
            }
        }

        return "正常"
    }

    internal suspend fun saveRecordingWithSessionTracking(
        wordId: Long,
        speakerId: Long,
        projectId: Long,
        recordingFile: String,
        durationMs: Long,
        qualityStatus: String?,
        avgRms: Float,
        peakAmplitude: Int
    ) {
        checkSessionContext(speakerId, projectId)
        if (!sessionOriginalRecordings.containsKey(wordId)) {
            val existing = repository.getSpeakerRecording(wordId, speakerId)
            sessionOriginalRecordings[wordId] = existing
        }
        repository.saveSpeakerRecording(
            wordId = wordId,
            speakerId = speakerId,
            recordingFile = recordingFile,
            durationMs = durationMs,
            qualityStatus = qualityStatus,
            avgRms = avgRms,
            peakAmplitude = peakAmplitude
        )
        invalidateProjectTakesIndex(projectId)
        _takesRefreshTrigger.value++
        forceRefreshCounts()
        maybeAutoCalibrateSpeakerPitch(speakerId)
    }

    internal fun collectCandidateTakesAfterStop(
        projectId: Long,
        wordId: Long,
        speakerId: Long,
        targetFile: File?
    ): Pair<List<File>, File?> {
        val priorTakes = getOrBuildProjectTakesIndex(projectId)["word_${wordId}_speaker_${speakerId}"].orEmpty()
        if (targetFile != null && targetFile.exists() && targetFile.length() > 44L) {
            val combined = if (priorTakes.any { it.name == targetFile.name }) {
                priorTakes
            } else {
                listOf(targetFile) + priorTakes
            }
            return combined to targetFile
        }
        // Fallback directory scan only when targetFile wasn't written at expected path
        invalidateProjectTakesIndex(projectId)
        val refreshed = getOrBuildProjectTakesIndex(projectId)["word_${wordId}_speaker_${speakerId}"].orEmpty()
        return refreshed to refreshed.firstOrNull()
    }

    internal suspend fun stopAndPersistForWirelessAwait(tailHangoverMs: Long): CompletedHostTakeInfo? {
        return stopAndPersistLocalRecordingInternal(tailHangoverMs = tailHangoverMs)
    }

    private suspend fun stopAndPersistLocalRecordingInternal(
        tailHangoverMs: Long = 115L,
        preConsumedContext: ActiveCaptureContext? = null
    ): CompletedHostTakeInfo? = withContext(Dispatchers.IO) {
        val ctx = preConsumedContext ?: consumeActiveCaptureContext()
        val word = ctx?.word ?: getCurrentWord()
        val project = ctx?.project ?: _currentProject.value
        val speaker = ctx?.speaker ?: _currentSpeaker.value
        val targetFile = ctx?.destFile
        val seqPosNow = ctx?.seqPos ?: _currentSeqIndex.value

        val quality = audioRecorder.stopRecording(
            tailHangoverMs = tailHangoverMs,
            expectedToken = ctx?.token ?: 0L,
            expectedOutputFile = targetFile
        )
        if (word == null || project == null || speaker == null) return@withContext null

        val (files, newestFile) = collectCandidateTakesAfterStop(project.id, word.id, speaker.id, targetFile)
        if (newestFile != null && newestFile.exists() && newestFile.length() > 44L) {
            AcousticAnalyzer.getRealWaveformEnvelope(newestFile, 48)

            val finalStatus = determineDetailedQuality(
                initialStatus = quality.status,
                peak = quality.peakAmplitude,
                avgRms = quality.avgRms,
                durationMs = quality.durationMs.toLong(),
                file = newestFile,
                speakerGender = speaker.gender
            )

            val durMs = quality.durationMs.toLong().coerceAtLeast(100L)
            saveRecordingWithSessionTracking(
                wordId = word.id,
                speakerId = speaker.id,
                projectId = project.id,
                recordingFile = newestFile.name,
                durationMs = durMs,
                qualityStatus = finalStatus,
                avgRms = quality.avgRms,
                peakAmplitude = quality.peakAmplitude
            )

            pruneOldTakesKeepingRecent(files, newestFile, word.id, project.id)

            return@withContext CompletedHostTakeInfo(
                projectId = project.id,
                wordId = word.id,
                speakerId = speaker.id,
                seqPos = seqPosNow,
                wordText = word.text,
                wordRemark = word.remark,
                wordGroup = word.groupName,
                speakerNote = word.speakerNote,
                wavFile = newestFile,
                finalStatus = finalStatus,
                originalIndex = word.originalIndex.takeIf { it >= 0 } ?: seqPosNow
            )
        }
        null
    }

    fun triggerStopRecording() {
        val snap = consumeActiveCaptureContext()
        if (snap == null && !audioRecorder.isRecording.value) return
        val wordSnapshot = snap?.word ?: getCurrentWord()
        viewModelScope.launch(Dispatchers.IO) {
            val completed = stopAndPersistLocalRecordingInternal(
                tailHangoverMs = 115L,
                preConsumedContext = snap
            ) ?: return@launch
            if (_autoPlayAfterRecord.value && wordSnapshot != null && getCurrentWord()?.id == wordSnapshot.id) {
                withContext(Dispatchers.Main) {
                    val updatedWord = wordSnapshot.copy(
                        isRecorded = true,
                        recordingFile = completed.wavFile.name,
                        durationMs = (AcousticAnalyzer.getExactWavDurationSec(completed.wavFile) * 1000.0).toLong().coerceAtLeast(100L),
                        qualityStatus = completed.finalStatus
                    )
                    playWordAudio(updatedWord)
                }
            }
        }
    }

    private fun triggerStopRecordingAndAdvance() {
        val snap = consumeActiveCaptureContext()
        if (snap == null && !audioRecorder.isRecording.value) return
        audioRecorder.markStoppingImmediate(snap?.token ?: 0L)
        viewModelScope.launch(Dispatchers.IO) {
            // VAD already waited silenceDelayMs of silence, so 0ms extra hangover is needed
            val completed = stopAndPersistLocalRecordingInternal(
                tailHangoverMs = 0L,
                preConsumedContext = snap
            ) ?: return@launch
            if (_holdOnQualityIssue.value && SpeechQualityEvaluator.isBlockingForAutoAdvance(completed.finalStatus)) {
                _lastHoldQualityNotice.value = S.recording.holdQualityNoticeStay.withArgs("status" to S.quality.displayStatus(completed.finalStatus))
                return@launch
            }
            withContext(Dispatchers.Main) {
                val advanced = nextWord()
                if (advanced && _autoNextEnabled.value) {
                    delay(550)
                    triggerStartRecording()
                }
            }
        }
    }

    /**
     * Invoked when the user releases a press-and-hold gesture on the standalone recording trigger.
     * Respects [autoAdvanceOnHoldRelease] and [holdOnQualityIssue].
     */
    fun triggerStopRecordingFromHoldRelease() {
        val snap = consumeActiveCaptureContext()
        if (snap == null && !audioRecorder.isRecording.value) return
        val wordSnapshot = snap?.word ?: getCurrentWord()
        viewModelScope.launch(Dispatchers.IO) {
            val completed = stopAndPersistLocalRecordingInternal(
                tailHangoverMs = 115L,
                preConsumedContext = snap
            ) ?: return@launch
            val shouldAutoAdvance = _autoAdvanceOnHoldRelease.value || _pushToTalkEnabled.value
            if (!shouldAutoAdvance) {
                if (_autoPlayAfterRecord.value && wordSnapshot != null && getCurrentWord()?.id == wordSnapshot.id) {
                    withContext(Dispatchers.Main) {
                        playWordAudio(
                            wordSnapshot.copy(
                                isRecorded = true,
                                recordingFile = completed.wavFile.name,
                                qualityStatus = completed.finalStatus
                            )
                        )
                    }
                }
                return@launch
            }

            if (_holdOnQualityIssue.value && SpeechQualityEvaluator.isBlockingForAutoAdvance(completed.finalStatus)) {
                _lastHoldQualityNotice.value = S.recording.holdQualityNoticeStay.withArgs("status" to S.quality.displayStatus(completed.finalStatus))
                return@launch
            }

            withContext(Dispatchers.Main) {
                nextWord()
            }
        }
    }

    fun triggerStopRecordingAndAdvanceForPTT() {
        triggerStopRecordingFromHoldRelease()
    }

    // Media Player Audio Playback & Continuous Playlist Review
    fun startContinuousPlayback(words: List<Word>, startFromWordId: Long? = null) {
        val recordedWords = words.filter { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
        if (recordedWords.isEmpty()) {
            stopPlayback()
            return
        }
        val startIdx = if (startFromWordId != null) {
            recordedWords.indexOfFirst { it.id == startFromWordId }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        continuousQueue = recordedWords
        continuousIndex = startIdx
        _isContinuousPlaying.value = true
        playWordAudioInternal(recordedWords[startIdx], keepContinuous = true)
    }

    fun stopContinuousPlayback() {
        _isContinuousPlaying.value = false
        continuousQueue = emptyList()
        continuousIndex = -1
        stopPlayback()
    }

    fun playSpecificAudioFile(audioFile: File, tagId: Long) {
        if (_playingWordId.value == tagId) {
            stopPlayback()
            return
        }
        stopPlayback()
        _playingWordId.value = tagId

        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            if (!audioFile.exists() || audioFile.length() <= 44L) {
                _playingWordId.value = null
                return@launch
            }
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(context, Uri.fromFile(audioFile))
                    prepare()
                    start()
                }
                mediaPlayer = mp
                val fileDurationMs = ((audioFile.length() - 44L) * 1000L / (44100L * 2L)).toFloat().coerceAtLeast(1f)
                val realDuration = maxOf(mp.duration.toFloat(), fileDurationMs)

                mp.setOnCompletionListener {
                    if (_playingWordId.value == tagId) {
                        _playingWordId.value = null
                        _playbackProgress.value = 0f
                    }
                    playbackJob?.cancel()
                    viewModelScope.launch(Dispatchers.IO) {
                        try { it.release() } catch (ignored: Exception) {}
                    }
                }

                while (mp.isPlaying) {
                    _playbackProgress.value = (mp.currentPosition / realDuration).coerceIn(0f, 1f)
                    delay(35)
                }
                if (_playingWordId.value == tagId) {
                    _playingWordId.value = null
                    _playbackProgress.value = 0f
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(tag, "playSpecificAudioFile error: ${e.message}")
                }
                if (_playingWordId.value == tagId) {
                    _playingWordId.value = null
                    _playbackProgress.value = 0f
                }
            }
        }
    }

    fun playWordAudio(word: Word) {
        if (_playingWordId.value == word.id) {
            stopPlayback()
            return
        }
        playWordAudioInternal(word, keepContinuous = false)
    }

    private fun playWordAudioInternal(word: Word, keepContinuous: Boolean) {
        stopPlayback(preserveContinuousState = keepContinuous)

        val project = _currentProject.value ?: return
        val recordingName = word.recordingFile ?: return
        val audioFile = File(File(context.getExternalFilesDir(null), "project_${project.id}"), recordingName)

        _playingWordId.value = word.id

        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(tag, "Audio file is missing or empty.")
                _playingWordId.value = null
                _playbackProgress.value = 0f
                if (keepContinuous) {
                    withContext(Dispatchers.Main) { advanceContinuousPlayback() }
                }
                return@launch
            }

            try {
                val mp = MediaPlayer().apply {
                    setDataSource(context, Uri.fromFile(audioFile))
                    prepare()
                    start()
                }
                mediaPlayer = mp
                val fileDurationMs = ((audioFile.length() - 44L) * 1000L / (44100L * 2L)).toFloat().coerceAtLeast(1f)
                val realDuration = maxOf(mp.duration.toFloat(), fileDurationMs)

                mp.setOnCompletionListener {
                    if (_playingWordId.value == word.id) {
                        _playingWordId.value = null
                        _playbackProgress.value = 0f
                    }
                    playbackJob?.cancel()
                    viewModelScope.launch(Dispatchers.IO) {
                        try { it.release() } catch (ignored: Exception) {}
                    }
                    if (_isContinuousPlaying.value) {
                        viewModelScope.launch {
                            delay(480)
                            if (_isContinuousPlaying.value) {
                                advanceContinuousPlayback()
                            }
                        }
                    }
                }

                while (mp.isPlaying) {
                    _playbackProgress.value = (mp.currentPosition / realDuration).coerceIn(0f, 1f)
                    delay(35)
                }
                if (_playingWordId.value == word.id) {
                    _playingWordId.value = null
                    _playbackProgress.value = 0f
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(tag, "Playback error: ${e.message}")
                    _playingWordId.value = null
                    _playbackProgress.value = 0f
                    if (keepContinuous) {
                        withContext(Dispatchers.Main) { advanceContinuousPlayback() }
                    }
                }
            }
        }
    }

    private fun advanceContinuousPlayback() {
        val nextIdx = continuousIndex + 1
        if (_isContinuousPlaying.value && nextIdx in continuousQueue.indices) {
            continuousIndex = nextIdx
            playWordAudioInternal(continuousQueue[nextIdx], keepContinuous = true)
        } else {
            _isContinuousPlaying.value = false
            continuousQueue = emptyList()
            continuousIndex = -1
        }
    }

    fun stopPlayback(preserveContinuousState: Boolean = false) {
        if (!preserveContinuousState) {
            _isContinuousPlaying.value = false
            continuousQueue = emptyList()
            continuousIndex = -1
        }
        playbackJob?.cancel()
        playbackJob = null
        val oldPlayer = mediaPlayer
        mediaPlayer = null
        _playingWordId.value = null
        _playbackProgress.value = 0f
        if (oldPlayer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    oldPlayer.stop()
                } catch (ignored: Exception) {}
                try {
                    oldPlayer.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun removeRecordingOfWord(word: Word) {
        val project = _currentProject.value ?: return
        val speaker = _currentSpeaker.value ?: return
        stopPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            if (word.recordingFile != null) {
                val audioFile = File(File(context.getExternalFilesDir(null), "project_${project.id}"), word.recordingFile)
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            }
            invalidateProjectTakesIndex(project.id)
            repository.deleteSpeakerRecording(word.id, speaker.id)
            _takesRefreshTrigger.value++
            forceRefreshCounts()
        }
    }

    // Word CRUD for humane word list editing inside a project
    fun addWordToProject(projectId: Long, text: String, remark: String, groupName: String = "") {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addWordToProject(projectId, text, remark, groupName)
            forceRefreshCounts()
        }
    }

    fun updateWordItem(word: Word, newText: String, newRemark: String, newGroup: String = word.groupName) {
        if (newText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateWord(word.copy(text = newText.trim(), remark = newRemark.trim(), groupName = newGroup.trim()))
        }
    }

    fun deleteWordItem(word: Word) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteWordFromProject(word, context)
            invalidateProjectTakesIndex(word.projectId)
            forceRefreshCounts()
        }
    }

    /**
     * Derives a literary/colloquial or alternate pronunciation entry immediately after [baseWord].
     * If [specificTakeFile] is passed (e.g. from Multi-Take Switcher), copies that take into the new variant;
     * otherwise leaves it ready to record (or copies [copyCurrentAudio] when requested).
     */
    fun deriveVariantWord(
        baseWord: Word,
        variantSuffix: String,
        speakerNote: String = "",
        specificTakeFile: File? = null,
        copyCurrentAudio: Boolean = false,
        jumpToNewVariant: Boolean = true,
        onComplete: (Word?) -> Unit = {}
    ) {
        val cleanSuffix = variantSuffix.trim().ifEmpty { "异读" }
        val baseStem = baseWord.text.trim()
        val variantText = "$baseStem ($cleanSuffix)"
        val speakerId = _currentSpeaker.value?.id
        viewModelScope.launch(Dispatchers.IO) {
            val sourceWav: File? = when {
                specificTakeFile != null && specificTakeFile.exists() && specificTakeFile.length() > 44L -> specificTakeFile
                copyCurrentAudio && !baseWord.recordingFile.isNullOrBlank() -> {
                    val projDir = File(context.getExternalFilesDir(null), "project_${baseWord.projectId}")
                    File(projDir, baseWord.recordingFile).takeIf { it.exists() && it.length() > 44L }
                }
                else -> null
            }
            val created = repository.insertVariantWordAfter(
                baseWord = baseWord,
                variantText = variantText,
                variantRemark = baseWord.remark,
                copyFromWavFile = sourceWav,
                speakerId = speakerId,
                speakerNote = speakerNote.trim(),
                qualityStatus = "正常",
                context = context
            )
            invalidateProjectTakesIndex(baseWord.projectId)
            _takesRefreshTrigger.value++
            forceRefreshCounts()
            if (created != null && jumpToNewVariant) {
                // Give Room Flow a brief beat or select as soon as _currentWords emits
                delay(70)
            }
            withContext(Dispatchers.Main) {
                if (created != null && jumpToNewVariant) {
                    val seq = _wordSequence.value
                    val words = _currentWords.value
                    val targetPos = seq.indexOfFirst { idx -> words.getOrNull(idx)?.id == created.id }
                    if (targetPos >= 0) {
                        selectWordBySequencePosition(targetPos)
                    }
                }
                onComplete(created)
            }
        }
    }

    // Project Manager Interface
    fun selectProjectAndSpeaker(project: Project, speaker: Speaker?) {
        val isSameProject = _currentProject.value?.id == project.id
        _currentProject.value = project
        if (speaker != null) {
            selectSpeaker(speaker)
        } else {
            selectProject(project)
        }
        if (isSameProject && _currentWords.value.isNotEmpty()) {
            _onlyPendingWords.value = false
            rebuildSequence(maintainCurrentWord = false, restoreBookmark = true)
        }
    }

    fun selectProject(project: Project?) {
        val isSameProject = project != null && _currentProject.value?.id == project.id
        _currentProject.value = project
        if (project == null) {
            _currentSeqIndex.value = 0
            _onlyPendingWords.value = false
        } else {
            if (isSameProject && _currentWords.value.isNotEmpty()) {
                _onlyPendingWords.value = false
                rebuildSequence(maintainCurrentWord = false, restoreBookmark = true)
            }
            viewModelScope.launch {
                val speakers = allSpeakers.value
                val remembered = repository.getSpeakerById(project.speakerId)
                val targetSpeaker = if (_isSimpleMode.value) {
                    remembered ?: _currentSpeaker.value ?: speakers.firstOrNull()
                } else {
                    when {
                        remembered != null && remembered.matchesProjectGroup(project.speakerGroupName) -> remembered
                        project.speakerGroupName.isNotBlank() -> {
                            speakers.firstOrNull { it.matchesProjectGroup(project.speakerGroupName) } ?: remembered ?: speakers.firstOrNull()
                        }
                        else -> remembered ?: _currentSpeaker.value ?: speakers.firstOrNull()
                    }
                }
                if (targetSpeaker != null) {
                    _currentSpeaker.value = targetSpeaker
                }
            }
        }
    }

    fun forceRefreshCounts() {
        invalidateProjectTakesIndex()
        countsRefreshTrigger.value++
    }

    fun createNewProject(
        name: String,
        description: String,
        rawWordList: String,
        speakerGroupName: String = "",
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanGroup = speakerGroupName.trim()
            val speakers = allSpeakers.value
            val matchingSpeakerId = if (cleanGroup.isNotEmpty()) {
                speakers.firstOrNull { it.matchesProjectGroup(cleanGroup) }?.id ?: (_currentSpeaker.value?.id ?: 1L)
            } else {
                _currentSpeaker.value?.id ?: speakers.firstOrNull()?.id ?: 1L
            }
            val parsedWords = parseImportTextToParsedWords(rawWordList)
            val id = if (parsedWords.isEmpty()) {
                repository.createProjectWithParsedWords(name, description, listOf(ParsedWord(S.project.fallbackTestChar)), matchingSpeakerId, cleanGroup)
            } else {
                repository.createProjectWithParsedWords(name, description, parsedWords, matchingSpeakerId, cleanGroup)
            }
            forceRefreshCounts()
            val updatedProj = repository.getProjectById(id)
            withContext(Dispatchers.Main) {
                if (updatedProj != null) {
                    selectProject(updatedProj)
                }
                onComplete()
            }
        }
    }

    fun updateProjectDetails(
        project: Project,
        name: String,
        description: String,
        speakerGroupName: String = project.speakerGroupName,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = project.copy(
                name = name.trim(),
                description = description.trim(),
                speakerGroupName = speakerGroupName.trim()
            )
            repository.updateProject(updated)
            forceRefreshCounts()
            withContext(Dispatchers.Main) {
                if (_currentProject.value?.id == project.id) {
                    selectProject(updated)
                }
                onComplete()
            }
        }
    }

    fun createProjectFromDrafts(
        name: String,
        description: String,
        speakerGroupName: String = "",
        drafts: List<EditableWordDraft>,
        enablePicturePrompts: Boolean = false,
        onComplete: (Project?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanGroup = speakerGroupName.trim()
            val speakers = allSpeakers.value
            val matchingSpeakerId = if (cleanGroup.isNotEmpty()) {
                speakers.firstOrNull { it.matchesProjectGroup(cleanGroup) }?.id ?: (_currentSpeaker.value?.id ?: 1L)
            } else {
                _currentSpeaker.value?.id ?: speakers.firstOrNull()?.id ?: 1L
            }
            val parsedWords = drafts
                .filter { it.text.isNotBlank() }
                .map {
                    ParsedWord(
                        text = it.text.trim(),
                        remark = it.remark.trim(),
                        groupName = it.groupName.trim(),
                        imagePath = it.imagePath.trim()
                    )
                }
                .ifEmpty { listOf(ParsedWord(S.project.fallbackSampleChar)) }

            val id = repository.createProjectWithParsedWords(
                name = name.trim(),
                description = description.trim(),
                parsedWords = parsedWords,
                speakerId = matchingSpeakerId,
                speakerGroupName = cleanGroup,
                enablePicturePrompts = enablePicturePrompts,
                context = context
            )
            forceRefreshCounts()
            val createdProj = repository.getProjectById(id)
            withContext(Dispatchers.Main) {
                if (createdProj != null) {
                    selectProject(createdProj)
                }
                onComplete(createdProj)
            }
        }
    }

    fun saveEditedProjectWords(
        project: Project,
        name: String,
        description: String,
        speakerGroupName: String,
        drafts: List<EditableWordDraft>,
        enablePicturePrompts: Boolean = project.enablePicturePrompts,
        onComplete: (Project) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.saveEditedProjectWords(
                project = project,
                name = name,
                description = description,
                speakerGroupName = speakerGroupName,
                drafts = drafts,
                context = context,
                enablePicturePrompts = enablePicturePrompts
            )
            forceRefreshCounts()
            withContext(Dispatchers.Main) {
                if (_currentProject.value?.id == project.id) {
                    _currentProject.value = updated
                }
                onComplete(updated)
            }
        }
    }

    fun resetProjectRecordings() {
        val project = _currentProject.value ?: return
        val currentSpeaker = _currentSpeaker.value ?: return
        stopPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            if (sessionActiveSpeakerId == currentSpeaker.id && sessionActiveProjectId == project.id) {
                val projectDir = File(context.getExternalFilesDir(null), "project_${project.id}")
                sessionOriginalRecordings.forEach { (wordId, originalRecording) ->
                    if (originalRecording == null) {
                        repository.deleteSpeakerRecording(wordId, currentSpeaker.id)
                    } else {
                        val origFile = File(projectDir, originalRecording.recordingFile)
                        if (origFile.exists() && origFile.length() > 44L) {
                            repository.saveSpeakerRecording(
                                wordId = originalRecording.wordId,
                                speakerId = originalRecording.speakerId,
                                recordingFile = originalRecording.recordingFile,
                                durationMs = originalRecording.durationMs,
                                qualityStatus = originalRecording.qualityStatus,
                                avgRms = originalRecording.avgRms,
                                peakAmplitude = originalRecording.peakAmplitude
                            )
                        } else {
                            repository.deleteSpeakerRecording(wordId, currentSpeaker.id)
                        }
                    }
                }
                sessionOriginalRecordings.clear()
                _takesRefreshTrigger.value++
                forceRefreshCounts()
            }
        }
    }

    fun deleteProject(project: Project) {
        if (_currentProject.value?.id == project.id) {
            selectProject(null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProject(project.id, context)
        }
    }

    fun importProjectZip(
        inputStream: InputStream,
        onFinished: (ImportArchiveSummary) -> Unit
    ) = executeImportProjectZip(inputStream, onFinished)

    fun exportProjectAsZip(
        project: Project,
        options: ExportOptions,
        outputStream: OutputStream,
        onFinished: (ExportSummary) -> Unit
    ) = executeExportProjectAsZip(project, options, outputStream, onFinished)

    fun exportProjectAsTeproj(
        project: Project,
        exportAllSpeakers: Boolean = false,
        includePraatTextGrid: Boolean = true,
        outputStream: OutputStream,
        onFinished: (ExportSummary) -> Unit
    ) {
        exportProjectAsZip(
            project = project,
            options = ExportOptions(
                includeAudio = true,
                includeAnalysis = false,
                includeWordList = false,
                includeSpeakerProfile = false,
                includeExperimentProfile = false,
                includePraatTextGrid = includePraatTextGrid,
                exportAllSpeakers = exportAllSpeakers,
                exportAsTeproj = true
            ),
            outputStream = outputStream,
            onFinished = onFinished
        )
    }

    fun parseImportTextToParsedWords(text: String): List<ParsedWord> =
        parseImportTextToParsedWordsImpl(text)

    fun formatDraftsToTxt(drafts: List<EditableWordDraft>): String =
        formatWordDraftsToTxt(drafts)

    private val wirelessHostBridge: WirelessLinkHostBridge = createWirelessHostBridge()

    val wirelessLink = WirelessLinkCoordinator(
        context = context,
        bridge = wirelessHostBridge,
        scope = viewModelScope
    )

    suspend fun listWirelessSessionCapsules(): List<WirelessSessionCapsuleSummary> =
        WirelessSessionArchiveManager.listCapsules(context)

    fun exportWirelessCapsuleToUri(
        capsuleDir: File,
        targetUri: Uri,
        onComplete: (Boolean, String) -> Unit
    ) = executeExportWirelessCapsuleToUri(capsuleDir, targetUri, onComplete)

    fun importWirelessCapsuleAsProject(
        capsuleDir: File,
        onComplete: (Boolean, String) -> Unit
    ) = executeImportWirelessCapsuleAsProject(capsuleDir, onComplete)

    fun deleteWirelessCapsule(capsuleDir: File, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = WirelessSessionArchiveManager.deleteCapsule(capsuleDir)
            wirelessLink.notifyCapsulesChanged()
            withContext(Dispatchers.Main) {
                onComplete(ok)
            }
        }
    }

    fun rescueWirelessPendingToCapsule(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = wirelessLink.rescuePendingSyncToLocalCapsule()
            onComplete(count)
        }
    }

    init {
        // Keep standalone autoAdvanceOnHoldRelease / holdOnQualityIssue aligned with persisted PromptDisplayPolicy
        val initialPolicy = wirelessLink.promptPolicy.value
        if (!sharedPrefs.contains("auto_advance_on_hold_release")) {
            _autoAdvanceOnHoldRelease.value = initialPolicy.autoAdvanceOnHoldRelease
        }
        if (!sharedPrefs.contains("hold_on_quality_issue")) {
            _holdOnQualityIssue.value = initialPolicy.holdOnQualityIssueWhenAutoAdvance
        }

        // Automatically push authoritative session state from Project Host whenever project/speaker/words/cursor changes
        viewModelScope.launch {
            var prevWordHash = -1
            var prevProjId = -1L
            var prevSpkId = -1L
            combine(
                combine(_currentProject, _currentSpeaker, allProjects, allSpeakers) { proj, spk, projs, spks ->
                    val picHash = if (proj?.enablePicturePrompts == true) 997 else 0
                    Triple(proj?.id ?: 0L, spk?.id ?: 0L, projs.size * 31 + spks.size + picHash)
                },
                combine(_isShuffleEnabled, _shufflingMode) { shuf, mode ->
                    (if (shuf) 131 else 0) + mode.name.hashCode()
                },
                _currentWords,
                _wordSequence,
                _currentSeqIndex
            ) { meta, shufHash, words, seq, pos ->
                val wordDigest = words.fold((seq.hashCode() * 31 + meta.third) * 31 + shufHash) { acc, w ->
                    31 * acc + w.id.hashCode() + (if (w.isRecorded) 1 else 0) +
                        w.speakerNote.hashCode() + (w.qualityStatus?.hashCode() ?: 0) + w.imagePath.hashCode()
                }
                Triple(meta.first, meta.second, wordDigest to pos)
            }.collect { (projId, spkId, pair) ->
                if (wirelessLink.activeRole.value != LinkRole.STANDALONE && wirelessLink.isProjectHost.value) {
                    val includeWords = pair.first != prevWordHash || projId != prevProjId || spkId != prevSpkId
                    prevWordHash = pair.first
                    prevProjId = projId
                    prevSpkId = spkId
                    wirelessLink.notifyProjectHostStateChanged(includeWordList = includeWords)
                }
            }
        }
        viewModelScope.launch {
            audioRecorder.isRecording.collect {
                if (wirelessLink.activeRole.value != LinkRole.STANDALONE && wirelessLink.isProjectHost.value) {
                    wirelessLink.notifyProjectHostStateChanged(includeWordList = false)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wirelessLink.stopDiscoveryScan()
        wirelessLink.cancelOrDisconnect()
        stopPlayback()
        audioRecorder.setStandbyPreRollActive(false)
        viewModelScope.launch {
            audioRecorder.stopRecording(0L)
        }
    }

    companion object {
        val DEFAULT_DIALECT_IPA_INITIALS = listOf(
            "p", "pʰ", "b", "m", "f", "ʋ",
            "t", "tʰ", "d", "n", "l",
            "ts", "tsʰ", "dz", "s", "z",
            "tʂ", "tʂʰ", "dʐ", "ʂ", "ʐ",
            "ȶ", "ȶʰ", "ȡ", "ȵ",
            "tɕ", "tɕʰ", "dʑ", "ɕ", "ʑ",
            "k", "kʰ", "ɡ", "ŋ", "x", "ɣ",
            "h", "ɦ", "ʔ", "ɮ", "̥"
        )

        val DEFAULT_DIALECT_IPA_FINALS = listOf(
            "i", "y", "ʮ", "ʯ", "ɿ", "ʅ",
            "u", "ɯ", "ɨ", "ʉ",
            "e", "ᴇ", "ø", "ə", "ɤ", "o",
            "ɛ", "ɔ", "a", "ɑ", "ɐ", "æ",
            "n", "ŋ", "m", "ʔ", "p̚", "t̚", "k̚",
            "̃", "ː", "˞", "ˀ", "̱"
        )
    }
}

