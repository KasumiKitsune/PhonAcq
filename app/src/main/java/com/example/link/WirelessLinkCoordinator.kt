package com.example.link

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import com.example.audio.AudioRecorder
import com.example.audio.NaturalAudioCoach
import com.example.audio.SpeechQualityEvaluator
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.utils.WordImageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Result returned synchronously/suspendably after Project Host finishes stopping & saving a local take.
 */
data class CompletedHostTakeInfo(
    val projectId: Long,
    val wordId: Long,
    val speakerId: Long,
    val seqPos: Int,
    val wordText: String,
    val wordRemark: String,
    val wordGroup: String,
    val speakerNote: String,
    val wavFile: File,
    val finalStatus: String,
    val originalIndex: Int = seqPos
)

/**
 * Host callbacks implemented by RecordingViewModel so WirelessLinkCoordinator
 * stays decoupled, modular, and easy to unit-test in isolation.
 */
interface WirelessLinkHostBridge {
    val sharedRecorder: AudioRecorder
    fun buildCurrentSessionSnapshot(
        activeMicOwner: ActiveMicOwner,
        policy: PromptDisplayPolicy,
        overrideIsRecording: Boolean? = null,
        sessionCode: String = "",
        latestCoaching: PromptCoachingFeedback? = null
    ): RemoteSessionSnapshot

    fun performLocalStartRecording()
    fun performLocalStopRecording()
    suspend fun performLocalStopRecordingAwait(tailHangoverMs: Long = 115L): CompletedHostTakeInfo?
    fun performLocalSelectSeqPos(seqPos: Int)
    fun performLocalNextWord(): Boolean
    fun performLocalPrevWord(): Boolean
    fun performLocalJumpNextUnrecorded(): Boolean
    fun performLocalToggleShuffle()
    fun performLocalSetShuffleEnabled(enabled: Boolean)
    fun performLocalSetShufflingMode(modeName: String)
    fun performLocalReshuffle()
    fun performLocalSelectProject(projectId: Long)
    fun performLocalSelectSpeaker(speakerId: Long)
    fun performLocalCreateSpeaker(name: String, gender: String, age: Int, dialect: String)
    fun performLocalToggleQualityFlag(wordId: Long)
    fun performLocalUpdateSpeakerNote(wordId: Long, newNote: String)
    fun resolveRecordedWavFile(projectId: Long, wordId: Long, speakerId: Long): File?
    fun resolveWordImageFile(projectId: Long, wordId: Long, imageToken: String): File? = null
    suspend fun ingestRemoteRecordedWav(
        projectId: Long,
        wordId: Long,
        speakerId: Long,
        tempWavFile: File
    ): String
}

/**
 * Coordinates multi-device wireless collaboration (看读端 × 遥控端) across phones, tablets, and PCs:
 *  - Manages pairing PIN, auto-discovery, role assignment, and active-mic endpoint edge-glow epochs
 *  - Routes microphone capture to either 看读端近场拾音 or 遥控端麦克风拾音 with live standby pre-roll
 *  - Streams real-time 16kHz audio + dBFS levels to the 遥控端 headphone monitor
 *  - Supports 3 decoupled storage modes (HOST_ONLY / HOST_AND_CAPTURE_BACKUP / DUAL_FULL_MIRROR)
 *    via self-contained Wireless Session Capsules without forcing heterogeneous Room DB sync
 *  - Coordinates press-and-hold auto-advance, quality hold guard, and natural-language speaker coaching.
 */
class WirelessLinkCoordinator(
    private val context: Context,
    private val bridge: WirelessLinkHostBridge,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    val earMonitor = LiveEarMonitorPlayer(context)

    private val _activeRole = MutableStateFlow(LinkRole.STANDALONE)
    val activeRole = _activeRole.asStateFlow()

    private val _isHostingWaiting = MutableStateFlow(false)
    val isHostingWaiting = _isHostingWaiting.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _connectionEpoch = MutableStateFlow(0)
    val connectionEpoch = _connectionEpoch.asStateFlow()

    private val _isProjectHost = MutableStateFlow(false)
    val isProjectHost = _isProjectHost.asStateFlow()

    private val _pairingPin = MutableStateFlow("000000")
    val pairingPin = _pairingPin.asStateFlow()

    private val _localIpAddresses = MutableStateFlow<List<String>>(emptyList())
    val localIpAddresses = _localIpAddresses.asStateFlow()

    private val _hostingTcpPort = MutableStateFlow(0)
    val hostingTcpPort = _hostingTcpPort.asStateFlow()

    private val _peerDeviceName = MutableStateFlow("")
    val peerDeviceName = _peerDeviceName.asStateFlow()

    private val _rttMs = MutableStateFlow(0)
    val rttMs = _rttMs.asStateFlow()

    private val _activeMicOwner = MutableStateFlow(ActiveMicOwner.PROMPT_DISPLAY_MIC)
    val activeMicOwner = _activeMicOwner.asStateFlow()

    private val _promptPolicy = MutableStateFlow(PromptDisplayPolicy.loadFromPrefs(prefs))
    val promptPolicy = _promptPolicy.asStateFlow()

    private val _sessionSnapshot = MutableStateFlow(RemoteSessionSnapshot(policy = _promptPolicy.value))
    val sessionSnapshot = _sessionSnapshot.asStateFlow()

    private val _remoteDbfs = MutableStateFlow(-60f)
    val remoteDbfs = _remoteDbfs.asStateFlow()

    private val _remoteIsClipping = MutableStateFlow(false)
    val remoteIsClipping = _remoteIsClipping.asStateFlow()

    private val _remoteWaveform = MutableStateFlow<List<Float>>(emptyList())
    val remoteWaveform = _remoteWaveform.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner = _statusBanner.asStateFlow()

    private val _isRemoteAuditionPlaying = MutableStateFlow(false)
    val isRemoteAuditionPlaying = _isRemoteAuditionPlaying.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount = _pendingSyncCount.asStateFlow()

    private val _capsulesRefreshVersion = MutableStateFlow(0)
    val capsulesRefreshVersion = _capsulesRefreshVersion.asStateFlow()

    private val _imageCacheVersion = MutableStateFlow(0)
    val imageCacheVersion = _imageCacheVersion.asStateFlow()

    private val requestedImageKeys = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var currentSessionCode: String = generateSessionCode()

    @Volatile
    private var latestCoachingState: PromptCoachingFeedback? = null

    // Dedicated recorder used when the non-Project-Host device captures audio into a temp WAV
    private val auxiliaryRecorder = AudioRecorder()
    private var activeAuxCaptureToken: Long = 0L
    private var activeAuxCaptureMeta: JSONObject? = null
    private var activeAuxCaptureFile: File? = null
    @Volatile
    private var peerCaptureActive: Boolean = false
    private var auditionPlayer: MediaPlayer? = null
    private val waveformRing = ArrayDeque<Float>(48)

    private val pendingSyncDir: File by lazy {
        File(context.filesDir, "link_pending_sync").apply { mkdirs() }
    }

    val transport = WirelessLinkTransport(
        scope = scope,
        onJsonMessage = ::handleIncomingJson,
        onBinaryWavReceived = ::handleIncomingWav,
        onUdpAudioTelemetry = ::handleIncomingUdpTelemetry,
        onPeerConnected = ::handlePeerConnected,
        onPeerDisconnected = ::handlePeerDisconnected,
        onRttUpdated = { ms -> _rttMs.value = ms }
    )

    val discoveredPeers = transport.discoveredPeers

    init {
        refreshPendingSyncCount()
        wireRecorderLiveTelemetry(bridge.sharedRecorder)
        wireRecorderLiveTelemetry(auxiliaryRecorder)
    }

    private fun generateSessionCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(5) {
            append('S')
            repeat(4) { append(alphabet[Random.nextInt(alphabet.length)]) }
        }
    }

    private fun syncRecorderStandbyState() {
        val role = _activeRole.value
        val connected = _isConnected.value
        if (!connected || role == LinkRole.STANDALONE) {
            auxiliaryRecorder.setStandbyPreRollActive(false)
            if (role != LinkRole.STANDALONE) {
                bridge.sharedRecorder.setStandbyPreRollActive(false)
            }
            return
        }

        val isThisDeviceActiveMic = _activeMicOwner.value.isActiveOnRole(role)
        val lowThresh = prefs.getFloat("low_volume", 250f)
        val rate = prefs.getInt("sample_rate_hz", 44100)
        val sourceStrat = prefs.getString("audio_source_strategy", "VOICE_RECOGNITION") ?: "VOICE_RECOGNITION"
        val preRoll = prefs.getBoolean("pre_roll_enabled", true)

        if (_isProjectHost.value) {
            auxiliaryRecorder.setStandbyPreRollActive(false)
            bridge.sharedRecorder.setLowVolumeThreshold(lowThresh)
            bridge.sharedRecorder.configureHardware(rate, sourceStrat, preRoll)
            bridge.sharedRecorder.setStandbyPreRollActive(isThisDeviceActiveMic)
        } else {
            bridge.sharedRecorder.setStandbyPreRollActive(false)
            auxiliaryRecorder.setLowVolumeThreshold(lowThresh)
            auxiliaryRecorder.configureHardware(rate, sourceStrat, preRoll)
            auxiliaryRecorder.setStandbyPreRollActive(isThisDeviceActiveMic)
        }
    }

    private fun wireRecorderLiveTelemetry(recorder: AudioRecorder) {
        var lastWavePushMs = 0L
        recorder.onLivePcmFrame = { samples, count, rate, dbfs, clip ->
            if (_isConnected.value) {
                val localRoleNow = _activeRole.value
                if (localRoleNow == LinkRole.PROMPT_DISPLAY) {
                    // Stream 16kHz live audio + dBFS to Controller for headphone monitoring & meter
                    val pcm16k = LiveEarMonitorPlayer.downsampleTo16kHz(samples, count, rate)
                    transport.sendUdpAudioTelemetry(dbfs, clip, pcm16k)
                } else if (localRoleNow == LinkRole.CONTROLLER) {
                    // Controller is capturing locally: update meter & optional local sidetone monitor
                    _remoteDbfs.value = dbfs
                    _remoteIsClipping.value = clip
                    val now = System.currentTimeMillis()
                    if (now - lastWavePushMs >= 40L) {
                        lastWavePushMs = now
                        appendWaveformDbfs(dbfs)
                    }
                    if (earMonitor.isMonitoringEnabled.value) {
                        val pcm16k = LiveEarMonitorPlayer.downsampleTo16kHz(samples, count, rate)
                        earMonitor.feed16kHzPcmFrame(pcm16k)
                    }
                }
            }
        }
    }

    private fun appendWaveformDbfs(dbfs: Float) {
        val norm = ((dbfs + 60f) / 60f).coerceIn(0.03f, 1f)
        synchronized(waveformRing) {
            if (waveformRing.size >= 48) waveformRing.removeFirst()
            waveformRing.addLast(norm)
            _remoteWaveform.value = waveformRing.toList()
        }
    }

    fun defaultDeviceName(): String {
        val model = Build.MODEL.trim()
        return if (model.isNotEmpty()) model else S.wireless.fallbackDeviceName
    }

    fun refreshLocalAddresses() {
        _localIpAddresses.value = WirelessLinkTransport.listLocalIpv4Addresses()
    }

    fun startDiscoveryScan() {
        refreshLocalAddresses()
        transport.startDiscoveryListener()
    }

    fun stopDiscoveryScan() {
        transport.stopDiscoveryListener()
    }

    /**
     * Starts hosting on the Project Host device.
     * [hostRole] can be either `LinkRole.CONTROLLER` ("本机作遥控端") or `LinkRole.PROMPT_DISPLAY` ("本机作看读端").
     */
    fun startHostingSession(
        hostRole: LinkRole,
        initialMicOwner: ActiveMicOwner = ActiveMicOwner.PROMPT_DISPLAY_MIC,
        preferredPort: Int = 0
    ): Int {
        val pin = (100000 + Random.nextInt(900000)).toString()
        currentSessionCode = generateSessionCode()
        latestCoachingState = null
        _pairingPin.value = pin
        _activeMicOwner.value = initialMicOwner
        _isProjectHost.value = true
        _isConnected.value = false
        _isHostingWaiting.value = true
        _statusBanner.value = null
        refreshLocalAddresses()

        val snap = bridge.buildCurrentSessionSnapshot(
            activeMicOwner = initialMicOwner,
            policy = _promptPolicy.value,
            sessionCode = currentSessionCode,
            latestCoaching = null
        )
        _sessionSnapshot.value = snap

        val port = transport.startHosting(
            deviceName = defaultDeviceName(),
            role = hostRole,
            pin = pin,
            projectNameProvider = {
                bridge.buildCurrentSessionSnapshot(
                    activeMicOwner = _activeMicOwner.value,
                    policy = _promptPolicy.value,
                    sessionCode = currentSessionCode,
                    latestCoaching = latestCoachingState
                ).projectName
            },
            preferredPort = preferredPort
        )
        _hostingTcpPort.value = port
        // Enter role preview once peer connects; keep waiting state visible in Pairing Dialog
        _activeRole.value = hostRole
        return port
    }

    /**
     * Joins an existing session hosted by another device at [ip]:[port] using [pin].
     */
    suspend fun joinPeerSession(ip: String, port: Int, pin: String): Result<LinkRole> {
        _statusBanner.value = null
        latestCoachingState = null
        val res = transport.connectToHost(
            ip = ip,
            port = port,
            pin = pin,
            deviceName = defaultDeviceName()
        )
        return res.map { (assignedRole, _) ->
            _isProjectHost.value = false
            _isHostingWaiting.value = false
            _activeRole.value = assignedRole
            syncRecorderStandbyState()
            transport.sendJson(JSONObject().put("type", "REQUEST_SNAPSHOT"))
            flushPendingSyncQueue()
            assignedRole
        }
    }

    fun cancelOrDisconnect() {
        stopRemoteAudition()
        earMonitor.setMonitoringEnabled(false)
        auxiliaryRecorder.setStandbyPreRollActive(false)
        bridge.sharedRecorder.setStandbyPreRollActive(false)
        if (auxiliaryRecorder.isRecording.value) {
            scope.launch { auxiliaryRecorder.stopRecording(0L) }
        }
        transport.disconnect(notifyPeer = true)
        _isHostingWaiting.value = false
        _isConnected.value = false
        _connectionEpoch.value = 0
        latestCoachingState = null
        peerCaptureActive = false
        _peerDeviceName.value = ""
        _rttMs.value = 0
        _activeRole.value = LinkRole.STANDALONE
        _remoteDbfs.value = -60f
        _remoteIsClipping.value = false
        _remoteWaveform.value = emptyList()
        requestedImageKeys.clear()
    }

    // =========================================================================
    // Policy & Mic Routing Updates
    // =========================================================================

    fun updatePromptPolicy(transform: (PromptDisplayPolicy) -> PromptDisplayPolicy) {
        val next = transform(_promptPolicy.value)
        _promptPolicy.value = next
        next.saveToPrefs(prefs)
        _sessionSnapshot.value = _sessionSnapshot.value.copy(policy = next)
        if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "POLICY_UPDATE")
                    put("policy", next.toJson())
                }
            )
        }
    }

    fun resetPromptPolicyToDefault() {
        val def = PromptDisplayPolicy()
        _promptPolicy.value = def
        def.saveToPrefs(prefs)
        _sessionSnapshot.value = _sessionSnapshot.value.copy(policy = def)
        if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "POLICY_UPDATE")
                    put("policy", def.toJson())
                }
            )
        }
    }

    fun toggleIntermissionBreak() {
        updatePromptPolicy { it.copy(isIntermissionBreak = !it.isIntermissionBreak) }
    }

    fun toggleTemporaryRemarkForCurrentWord() {
        updatePromptPolicy {
            it.copy(revealRemarkTemporaryForCurrentWord = !it.revealRemarkTemporaryForCurrentWord)
        }
    }

    fun toggleTemporaryTextVisibilityForCurrentWord() {
        updatePromptPolicy {
            it.copy(toggleTextTemporaryForCurrentWord = !it.toggleTextTemporaryForCurrentWord)
        }
    }

    private fun remoteImageCacheFile(projectId: Long, imageToken: String): File {
        val safeToken = imageToken
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "img" }
        return File(WordImageManager.getLinkImageCacheDir(context), "p${projectId}_$safeToken")
    }

    private fun requestWordImageFromHostIfNeeded(projectId: Long, wordId: Long, imageToken: String) {
        if (_isProjectHost.value || !_isConnected.value || projectId <= 0L || imageToken.isBlank()) return
        val cached = remoteImageCacheFile(projectId, imageToken)
        if (cached.exists() && cached.length() > 0L) return
        val reqKey = "${projectId}:${imageToken}"
        if (!requestedImageKeys.add(reqKey)) return
        transport.sendJson(
            JSONObject().apply {
                put("type", "CMD_REQUEST_WORD_IMAGE")
                put("projectId", projectId)
                put("wordId", wordId)
                put("imageToken", imageToken)
            }
        )
    }

    private fun prefetchSnapshotImages(snap: RemoteSessionSnapshot) {
        if (_isProjectHost.value || !_isConnected.value || snap.projectId <= 0L || snap.words.isEmpty()) return
        val cur = snap.currentSeqPos
        val candidatePositions = listOf(cur, cur + 1, cur - 1, cur + 2)
        for (pos in candidatePositions) {
            val w = snap.words.getOrNull(pos) ?: continue
            if (w.imageToken.isNotBlank()) {
                requestWordImageFromHostIfNeeded(snap.projectId, w.wordId, w.imageToken)
            }
        }
    }

    /**
     * Resolves the local or peer-cached image [File] for a [RemoteWordItem], automatically requesting
     * missing images over TCP from the Project Host when acting as a secondary client.
     */
    fun resolveRemoteWordImageFile(projectId: Long, word: RemoteWordItem?): File? {
        if (word == null || projectId <= 0L || word.imageToken.isBlank()) return null
        if (_isProjectHost.value) {
            return bridge.resolveWordImageFile(projectId, word.wordId, word.imageToken)
        }
        val cached = remoteImageCacheFile(projectId, word.imageToken)
        if (cached.exists() && cached.length() > 0L) {
            return cached
        }
        if (_isConnected.value) {
            requestWordImageFromHostIfNeeded(projectId, word.wordId, word.imageToken)
        }
        return null
    }

    fun setActiveMicOwner(owner: ActiveMicOwner) {
        if (_activeMicOwner.value == owner) return
        _activeMicOwner.value = owner
        _sessionSnapshot.value = _sessionSnapshot.value.copy(activeMicOwner = owner)
        syncRecorderStandbyState()
        if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "MIC_OWNER_UPDATE")
                    put("activeMicOwner", owner.name)
                }
            )
        }
    }

    // =========================================================================
    // Synchronized Actions (Invoked from either 遥控端 or permitted 看读端)
    // =========================================================================

    fun requestToggleRecording(initiatedByRole: LinkRole = _activeRole.value) {
        if (_sessionSnapshot.value.isRecording ||
            bridge.sharedRecorder.isRecording.value ||
            auxiliaryRecorder.isRecording.value
        ) {
            requestStopRecording(fromHoldRelease = false, initiatedByRole = initiatedByRole)
        } else {
            requestStartRecording()
        }
    }

    fun requestStartRecording() {
        stopRemoteAudition()
        latestCoachingState = null
        _sessionSnapshot.value = _sessionSnapshot.value.copy(latestCoaching = null)

        val micRole = if (_activeMicOwner.value == ActiveMicOwner.PROMPT_DISPLAY_MIC) {
            LinkRole.PROMPT_DISPLAY
        } else {
            LinkRole.CONTROLLER
        }

        if (_activeRole.value == micRole) {
            startCaptureOnThisDevice()
        } else if (_isConnected.value) {
            val snap = currentAuthoritativeSnapshot()
            val curWord = snap.currentWord ?: return
            val spk = snap.currentSpeakerSummary
            peerCaptureActive = true
            _sessionSnapshot.value = _sessionSnapshot.value.copy(isRecording = true, latestCoaching = null)
            transport.sendJson(
                JSONObject().apply {
                    put("type", "CMD_START_CAPTURE")
                    put("sessionCode", snap.sessionCode.ifBlank { currentSessionCode })
                    put("projectId", snap.projectId)
                    put("projectName", snap.projectName)
                    put("wordId", curWord.wordId)
                    put("wordText", curWord.text)
                    put("wordRemark", curWord.remark)
                    put("wordGroup", curWord.groupName)
                    put("speakerNote", curWord.speakerNote)
                    put("speakerId", snap.speakerId)
                    put("speakerName", snap.speakerName)
                    put("speakerGender", spk?.gender ?: "")
                    put("speakerAge", spk?.age ?: 0)
                    put("speakerDialect", spk?.dialect ?: "")
                    put("seqPos", snap.currentSeqPos)
                    put("originalIndex", curWord.originalIndex)
                }
            )
        }
    }

    fun requestStopRecording(
        fromHoldRelease: Boolean = false,
        initiatedByRole: LinkRole = _activeRole.value,
        immediateForNavigation: Boolean = false
    ) {
        val recordedByPromptSelfControl =
            initiatedByRole == LinkRole.PROMPT_DISPLAY && _promptPolicy.value.allowPromptStartStopRecord

        val micRole = if (_activeMicOwner.value == ActiveMicOwner.PROMPT_DISPLAY_MIC) {
            LinkRole.PROMPT_DISPLAY
        } else {
            LinkRole.CONTROLLER
        }

        if (_activeRole.value == micRole) {
            stopCaptureOnThisDevice(
                fromHoldRelease = fromHoldRelease,
                recordedByPromptSelfControl = recordedByPromptSelfControl,
                tailHangoverMs = if (immediateForNavigation) 0L else 115L
            )
        } else if (_isConnected.value) {
            peerCaptureActive = false
            _sessionSnapshot.value = _sessionSnapshot.value.copy(isRecording = false)
            transport.sendJson(
                JSONObject().apply {
                    put("type", "CMD_STOP_CAPTURE")
                    put("fromHoldRelease", fromHoldRelease)
                    put("recordedByPromptSelfControl", recordedByPromptSelfControl)
                    put("immediateForNavigation", immediateForNavigation)
                }
            )
        }
    }

    private fun stopActiveCaptureBeforeWordNavigation() {
        if (_sessionSnapshot.value.isRecording ||
            bridge.sharedRecorder.isRecording.value ||
            auxiliaryRecorder.isRecording.value
        ) {
            requestStopRecording(
                fromHoldRelease = false,
                immediateForNavigation = true
            )
        }
    }

    fun requestNextWord() {
        stopActiveCaptureBeforeWordNavigation()
        latestCoachingState = null
        resetTemporaryRemarkOnTurn()
        if (_isProjectHost.value) {
            bridge.performLocalNextWord()
            broadcastSnapshotFromProjectHost(includeWordList = false)
        } else if (_isConnected.value) {
            _sessionSnapshot.value = _sessionSnapshot.value.copy(latestCoaching = null)
            transport.sendJson(JSONObject().put("type", "CMD_NAV").put("op", "NEXT"))
        }
    }

    fun requestPrevWord() {
        stopActiveCaptureBeforeWordNavigation()
        latestCoachingState = null
        resetTemporaryRemarkOnTurn()
        if (_isProjectHost.value) {
            bridge.performLocalPrevWord()
            broadcastSnapshotFromProjectHost(includeWordList = false)
        } else if (_isConnected.value) {
            _sessionSnapshot.value = _sessionSnapshot.value.copy(latestCoaching = null)
            transport.sendJson(JSONObject().put("type", "CMD_NAV").put("op", "PREV"))
        }
    }

    fun requestJumpNextUnrecorded() {
        stopActiveCaptureBeforeWordNavigation()
        latestCoachingState = null
        resetTemporaryRemarkOnTurn()
        if (_isProjectHost.value) {
            bridge.performLocalJumpNextUnrecorded()
            broadcastSnapshotFromProjectHost(includeWordList = false)
        } else if (_isConnected.value) {
            _sessionSnapshot.value = _sessionSnapshot.value.copy(latestCoaching = null)
            transport.sendJson(JSONObject().put("type", "CMD_NAV").put("op", "NEXT_UNRECORDED"))
        }
    }

    fun requestSelectWordSeqPos(seqPos: Int) {
        stopActiveCaptureBeforeWordNavigation()
        latestCoachingState = null
        resetTemporaryRemarkOnTurn()
        if (_isProjectHost.value) {
            bridge.performLocalSelectSeqPos(seqPos)
            broadcastSnapshotFromProjectHost(includeWordList = false)
        } else if (_isConnected.value) {
            _sessionSnapshot.value = _sessionSnapshot.value.copy(latestCoaching = null)
            transport.sendJson(
                JSONObject().put("type", "CMD_NAV").put("op", "SELECT").put("seqPos", seqPos)
            )
        }
    }

    private fun prepareForShuffleChange() {
        stopRemoteAudition()
        latestCoachingState = null
        if (_sessionSnapshot.value.isRecording || bridge.sharedRecorder.isRecording.value || auxiliaryRecorder.isRecording.value) {
            requestStopRecording(fromHoldRelease = false)
        }
        resetTemporaryRemarkOnTurn()
    }

    fun requestToggleShuffle(): Boolean {
        prepareForShuffleChange()
        val currentEnabled = if (_isProjectHost.value || !_isConnected.value) {
            currentAuthoritativeSnapshot().isShuffleEnabled
        } else {
            _sessionSnapshot.value.isShuffleEnabled
        }
        val targetEnabled = !currentEnabled
        if (_isProjectHost.value) {
            bridge.performLocalToggleShuffle()
            broadcastSnapshotFromProjectHost(includeWordList = true)
        } else if (_isConnected.value) {
            _sessionSnapshot.value = _sessionSnapshot.value.copy(
                isShuffleEnabled = targetEnabled,
                latestCoaching = null
            )
            transport.sendJson(
                JSONObject().put("type", "CMD_SHUFFLE").put("op", "SET_ENABLED").put("enabled", targetEnabled)
            )
        } else {
            bridge.performLocalToggleShuffle()
        }
        return targetEnabled
    }

    fun requestSetShuffleEnabled(enabled: Boolean) {
        prepareForShuffleChange()
        if (_isProjectHost.value) {
            bridge.performLocalSetShuffleEnabled(enabled)
            broadcastSnapshotFromProjectHost(includeWordList = true)
        } else if (_isConnected.value) {
            _sessionSnapshot.value = _sessionSnapshot.value.copy(
                isShuffleEnabled = enabled,
                latestCoaching = null
            )
            transport.sendJson(
                JSONObject().put("type", "CMD_SHUFFLE").put("op", "SET_ENABLED").put("enabled", enabled)
            )
        } else {
            bridge.performLocalSetShuffleEnabled(enabled)
        }
    }

    fun requestSetShufflingMode(modeName: String) {
        prepareForShuffleChange()
        if (_isProjectHost.value) {
            bridge.performLocalSetShufflingMode(modeName)
            broadcastSnapshotFromProjectHost(includeWordList = true)
        } else if (_isConnected.value) {
            _sessionSnapshot.value = _sessionSnapshot.value.copy(
                isShuffleEnabled = true,
                shufflingMode = modeName,
                latestCoaching = null
            )
            transport.sendJson(
                JSONObject().put("type", "CMD_SHUFFLE").put("op", "SET_MODE").put("mode", modeName)
            )
        } else {
            bridge.performLocalSetShufflingMode(modeName)
        }
    }

    fun requestReshuffle() {
        prepareForShuffleChange()
        if (_isProjectHost.value) {
            bridge.performLocalReshuffle()
            broadcastSnapshotFromProjectHost(includeWordList = true)
        } else if (_isConnected.value) {
            _sessionSnapshot.value = _sessionSnapshot.value.copy(
                isShuffleEnabled = true,
                latestCoaching = null
            )
            transport.sendJson(
                JSONObject().put("type", "CMD_SHUFFLE").put("op", "RESHUFFLE")
            )
        } else {
            bridge.performLocalReshuffle()
        }
    }

    fun requestUpdateSpeakerNote(wordId: Long, newNote: String) {
        // Optimistic update on local snapshot
        val updatedWords = _sessionSnapshot.value.words.map {
            if (it.wordId == wordId) it.copy(speakerNote = newNote) else it
        }
        _sessionSnapshot.value = _sessionSnapshot.value.copy(words = updatedWords)

        if (_isProjectHost.value) {
            bridge.performLocalUpdateSpeakerNote(wordId, newNote)
            scope.launch(Dispatchers.IO) {
                delay(80L)
                broadcastSnapshotFromProjectHost(includeWordList = true)
            }
        } else if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "CMD_UPDATE_NOTE")
                    put("wordId", wordId)
                    put("note", newNote)
                }
            )
        }
    }

    fun requestToggleQualityFlag(wordId: Long) {
        if (_isProjectHost.value) {
            bridge.performLocalToggleQualityFlag(wordId)
            scope.launch(Dispatchers.IO) {
                delay(100L)
                broadcastSnapshotFromProjectHost(includeWordList = true)
            }
        } else if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "CMD_TOGGLE_FLAG")
                    put("wordId", wordId)
                }
            )
        }
    }

    fun requestSelectProject(projectId: Long) {
        if (projectId <= 0L) return
        stopRemoteAudition()
        latestCoachingState = null
        if (_sessionSnapshot.value.isRecording || bridge.sharedRecorder.isRecording.value || auxiliaryRecorder.isRecording.value) {
            requestStopRecording(fromHoldRelease = false)
        }
        resetTemporaryRemarkOnTurn()
        if (_isProjectHost.value) {
            bridge.performLocalSelectProject(projectId)
            scope.launch(Dispatchers.IO) {
                delay(120L)
                broadcastSnapshotFromProjectHost(includeWordList = true)
            }
        } else if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "CMD_SELECT_PROJECT")
                    put("projectId", projectId)
                }
            )
        }
    }

    fun requestSelectSpeaker(speakerId: Long) {
        if (speakerId <= 0L) return
        stopRemoteAudition()
        latestCoachingState = null
        if (_sessionSnapshot.value.isRecording || bridge.sharedRecorder.isRecording.value || auxiliaryRecorder.isRecording.value) {
            requestStopRecording(fromHoldRelease = false)
        }
        resetTemporaryRemarkOnTurn()
        if (_isProjectHost.value) {
            bridge.performLocalSelectSpeaker(speakerId)
            scope.launch(Dispatchers.IO) {
                delay(120L)
                broadcastSnapshotFromProjectHost(includeWordList = true)
            }
        } else if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "CMD_SELECT_SPEAKER")
                    put("speakerId", speakerId)
                }
            )
        }
    }

    fun requestCreateSpeaker(name: String, gender: String, age: Int, dialect: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        stopRemoteAudition()
        if (_isProjectHost.value) {
            bridge.performLocalCreateSpeaker(cleanName, gender, age, dialect.trim())
            scope.launch(Dispatchers.IO) {
                delay(180L)
                broadcastSnapshotFromProjectHost(includeWordList = true)
            }
        } else if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "CMD_CREATE_SPEAKER")
                    put("name", cleanName)
                    put("gender", gender)
                    put("age", age)
                    put("dialect", dialect.trim())
                }
            )
        }
    }

    /**
     * Plays the recorded `.wav` of the current word ONLY on the calling device (typically 遥控端 headphones)
     * without making any sound on the speaker's 看读端.
     */
    fun requestAuditionCurrentWordOnThisDevice() {
        if (_isRemoteAuditionPlaying.value) {
            stopRemoteAudition()
            return
        }
        val snap = currentAuthoritativeSnapshot()
        val word = snap.currentWord ?: return
        if (!word.isRecorded) {
            _statusBanner.value = S.wireless.wordHasNoRecording
            return
        }

        if (_isProjectHost.value) {
            val wav = bridge.resolveRecordedWavFile(snap.projectId, word.wordId, snap.speakerId)
            if (wav != null && wav.exists()) {
                playLocalAuditionFile(wav)
            } else {
                _statusBanner.value = S.wireless.syncInProgressWait
            }
        } else if (_isConnected.value) {
            _statusBanner.value = S.wireless.loadingAudition
            transport.sendJson(
                JSONObject().apply {
                    put("type", "CMD_REQUEST_AUDITION_WAV")
                    put("projectId", snap.projectId)
                    put("wordId", word.wordId)
                    put("speakerId", snap.speakerId)
                }
            )
        }
    }

    fun stopRemoteAudition() {
        try {
            auditionPlayer?.stop()
            auditionPlayer?.release()
        } catch (_: Exception) {}
        auditionPlayer = null
        _isRemoteAuditionPlaying.value = false
    }

    private fun playLocalAuditionFile(file: File) {
        stopRemoteAudition()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isRemoteAuditionPlaying.value = false
                    try {
                        it.release()
                    } catch (_: Exception) {}
                    if (auditionPlayer === it) auditionPlayer = null
                }
                prepare()
                start()
            }
            auditionPlayer = mp
            _isRemoteAuditionPlaying.value = true
            _statusBanner.value = null
        } catch (_: Exception) {
            _isRemoteAuditionPlaying.value = false
        }
    }

    private fun resetTemporaryRemarkOnTurn() {
        val pol = _promptPolicy.value
        if (pol.revealRemarkTemporaryForCurrentWord || pol.toggleTextTemporaryForCurrentWord) {
            updatePromptPolicy {
                it.copy(
                    revealRemarkTemporaryForCurrentWord = false,
                    toggleTextTemporaryForCurrentWord = false
                )
            }
        }
    }

    /**
     * Called by RecordingViewModel whenever project/speaker/words/recording state changes on Project Host.
     */
    fun notifyProjectHostStateChanged(includeWordList: Boolean = true) {
        if (_activeRole.value == LinkRole.STANDALONE || !_isProjectHost.value) return
        broadcastSnapshotFromProjectHost(includeWordList = includeWordList)
    }

    private fun currentAuthoritativeSnapshot(): RemoteSessionSnapshot {
        return if (_isProjectHost.value) {
            bridge.buildCurrentSessionSnapshot(
                activeMicOwner = _activeMicOwner.value,
                policy = _promptPolicy.value,
                sessionCode = currentSessionCode,
                latestCoaching = latestCoachingState
            )
        } else {
            _sessionSnapshot.value
        }
    }

    private fun broadcastSnapshotFromProjectHost(
        includeWordList: Boolean = true,
        overrideIsRecording: Boolean? = null
    ) {
        if (!_isProjectHost.value) return
        val effectiveOverride = overrideIsRecording ?: if (peerCaptureActive) true else null
        val snap = bridge.buildCurrentSessionSnapshot(
            activeMicOwner = _activeMicOwner.value,
            policy = _promptPolicy.value,
            overrideIsRecording = effectiveOverride,
            sessionCode = currentSessionCode,
            latestCoaching = latestCoachingState
        )
        _sessionSnapshot.value = snap
        if (_isConnected.value) {
            transport.sendJson(
                JSONObject().apply {
                    put("type", "SESSION_SNAPSHOT")
                    put("snapshot", snap.toJson(includeWordList = includeWordList))
                }
            )
        }
    }

    private fun buildCoachingFeedbackForTake(
        wordId: Long,
        seqPos: Int,
        finalStatus: String,
        recordedByPromptSelfControl: Boolean
    ): PromptCoachingFeedback? {
        val messages = NaturalAudioCoach.fromStatus(
            qualityStatus = finalStatus,
            recordedByPromptSelfControl = recordedByPromptSelfControl
        ) ?: return null
        return PromptCoachingFeedback(
            wordId = wordId,
            seqPos = seqPos,
            qualityStatus = finalStatus,
            speakerHint = messages.speakerNaturalHint,
            controllerHint = messages.controllerTechnicalHint,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun shouldBlockAutoAdvanceForStatus(pol: PromptDisplayPolicy, finalStatus: String): Boolean {
        val holdGuardActive = pol.holdOnQualityIssueWhenAutoAdvance ||
            pol.audioCoachingMode == PromptAudioCoachingMode.GENTLE_AND_HOLD
        return holdGuardActive && SpeechQualityEvaluator.isBlockingForAutoAdvance(finalStatus)
    }

    // =========================================================================
    // Local / Auxiliary Audio Capture Execution
    // =========================================================================

    private fun startCaptureOnThisDevice(explicitMeta: JSONObject? = null) {
        latestCoachingState = null
        if (_isProjectHost.value) {
            bridge.performLocalStartRecording()
            broadcastSnapshotFromProjectHost(includeWordList = false, overrideIsRecording = true)
        } else {
            // Non-Project-Host device captures lossless 44.1/48kHz WAV into pending sync folder
            if (auxiliaryRecorder.isRecording.value) return
            val snap = _sessionSnapshot.value
            val word = snap.currentWord
            val spk = snap.currentSpeakerSummary
            val projectId = explicitMeta?.optLong("projectId", snap.projectId) ?: snap.projectId
            val wordId = explicitMeta?.optLong("wordId", word?.wordId ?: 0L) ?: (word?.wordId ?: 0L)
            val speakerId = explicitMeta?.optLong("speakerId", snap.speakerId) ?: snap.speakerId
            val seqPos = explicitMeta?.optInt("seqPos", snap.currentSeqPos) ?: snap.currentSeqPos
            val origIdx = explicitMeta?.optInt("originalIndex", word?.originalIndex ?: seqPos)
                ?: (word?.originalIndex ?: seqPos)
            if (projectId <= 0L || wordId <= 0L) return

            val ts = System.currentTimeMillis()
            val outWav = File(pendingSyncDir, "sync_p${projectId}_w${wordId}_s${speakerId}_$ts.wav")
            val meta = JSONObject().apply {
                put("purpose", "RECORDED_TAKE_SYNC")
                put("sessionCode", explicitMeta?.optString("sessionCode", snap.sessionCode) ?: snap.sessionCode)
                put("projectId", projectId)
                put("projectName", explicitMeta?.optString("projectName", snap.projectName) ?: snap.projectName)
                put("wordId", wordId)
                put("wordText", explicitMeta?.optString("wordText", word?.text ?: "") ?: (word?.text ?: ""))
                put("wordRemark", explicitMeta?.optString("wordRemark", word?.remark ?: "") ?: (word?.remark ?: ""))
                put("wordGroup", explicitMeta?.optString("wordGroup", word?.groupName ?: "") ?: (word?.groupName ?: ""))
                put("speakerNote", explicitMeta?.optString("speakerNote", word?.speakerNote ?: "") ?: (word?.speakerNote ?: ""))
                put("speakerId", speakerId)
                put("speakerName", explicitMeta?.optString("speakerName", snap.speakerName) ?: snap.speakerName)
                put("speakerGender", explicitMeta?.optString("speakerGender", spk?.gender ?: "") ?: (spk?.gender ?: ""))
                put("speakerAge", explicitMeta?.optInt("speakerAge", spk?.age ?: 0) ?: (spk?.age ?: 0))
                put("speakerDialect", explicitMeta?.optString("speakerDialect", spk?.dialect ?: "") ?: (spk?.dialect ?: ""))
                put("seqPos", seqPos)
                put("originalIndex", origIdx)
                put("localFileName", outWav.name)
            }
            activeAuxCaptureMeta = meta
            activeAuxCaptureFile = outWav

            auxiliaryRecorder.setLowVolumeThreshold(prefs.getFloat("low_volume", 250f))
            auxiliaryRecorder.configureHardware(
                targetSampleRate = prefs.getInt("sample_rate_hz", 44100),
                sourceStrategy = prefs.getString("audio_source_strategy", "VOICE_RECOGNITION") ?: "VOICE_RECOGNITION",
                enablePreRoll = prefs.getBoolean("pre_roll_enabled", true)
            )
            activeAuxCaptureToken = auxiliaryRecorder.startRecording(
                outputFile = outWav,
                autoNextEnabled = false,
                vadThreshold = 1200f,
                silenceDurationMs = 1000L,
                onAutoNextTriggered = {}
            )
            _sessionSnapshot.value = _sessionSnapshot.value.copy(isRecording = true, latestCoaching = null)
            transport.sendJson(
                JSONObject().apply {
                    put("type", "REMOTE_CAPTURE_STATE")
                    put("isRecording", true)
                }
            )
        }
    }

    private fun stopCaptureOnThisDevice(
        fromHoldRelease: Boolean = false,
        recordedByPromptSelfControl: Boolean = false,
        tailHangoverMs: Long = 115L
    ) {
        if (_isProjectHost.value) {
            scope.launch(Dispatchers.IO) {
                val completed = bridge.performLocalStopRecordingAwait(tailHangoverMs = tailHangoverMs)
                val pol = _promptPolicy.value

                if (completed != null) {
                    val coaching = buildCoachingFeedbackForTake(
                        wordId = completed.wordId,
                        seqPos = completed.seqPos,
                        finalStatus = completed.finalStatus,
                        recordedByPromptSelfControl = recordedByPromptSelfControl
                    )
                    latestCoachingState = coaching

                    var advancedToNext = false
                    if (fromHoldRelease && pol.autoAdvanceOnHoldRelease) {
                        if (shouldBlockAutoAdvanceForStatus(pol, completed.finalStatus)) {
                            _statusBanner.value = S.wireless.holdQualityNoticeStay.withArgs("status" to S.quality.displayStatus(completed.finalStatus))
                        } else {
                            withContext(Dispatchers.Main) {
                                resetTemporaryRemarkOnTurn()
                                advancedToNext = bridge.performLocalNextWord()
                            }
                            if (advancedToNext) {
                                latestCoachingState = null
                            }
                        }
                    }

                    broadcastSnapshotFromProjectHost(includeWordList = true, overrideIsRecording = false)

                    // In DUAL_FULL_MIRROR mode, mirror Host-captured WAV to the peer's Session Capsule
                    if (pol.storageMode == WirelessStorageMode.DUAL_FULL_MIRROR &&
                        _isConnected.value &&
                        completed.wavFile.exists() &&
                        completed.wavFile.length() > 44L
                    ) {
                        val snapNow = currentAuthoritativeSnapshot()
                        val spk = snapNow.currentSpeakerSummary
                        val mirrorMeta = JSONObject().apply {
                            put("purpose", "HOST_WAV_MIRROR_SYNC")
                            put("sessionCode", currentSessionCode)
                            put("projectName", snapNow.projectName)
                            put("speakerName", snapNow.speakerName)
                            put("speakerGender", spk?.gender ?: "")
                            put("speakerAge", spk?.age ?: 0)
                            put("speakerDialect", spk?.dialect ?: "")
                            put("seqPos", completed.seqPos)
                            put("originalIndex", completed.originalIndex)
                            put("wordText", completed.wordText)
                            put("wordRemark", completed.wordRemark)
                            put("wordGroup", completed.wordGroup)
                            put("speakerNote", completed.speakerNote)
                        }
                        transport.sendWavFile(mirrorMeta, completed.wavFile)
                    }
                } else {
                    broadcastSnapshotFromProjectHost(includeWordList = true, overrideIsRecording = false)
                }
            }
        } else {
            val tokenSnap = activeAuxCaptureToken
            val meta = activeAuxCaptureMeta
            val wavFile = activeAuxCaptureFile
            activeAuxCaptureToken = 0L
            activeAuxCaptureMeta = null
            activeAuxCaptureFile = null
            if (tailHangoverMs == 0L) {
                auxiliaryRecorder.markStoppingImmediate(tokenSnap)
            }
            scope.launch(Dispatchers.IO) {
                val localQual = auxiliaryRecorder.stopRecording(
                    tailHangoverMs = tailHangoverMs,
                    expectedToken = tokenSnap,
                    expectedOutputFile = wavFile
                )
                _sessionSnapshot.value = _sessionSnapshot.value.copy(isRecording = false)
                transport.sendJson(
                    JSONObject().apply {
                        put("type", "REMOTE_CAPTURE_STATE")
                        put("isRecording", false)
                    }
                )
                if (meta != null && wavFile != null && wavFile.exists() && wavFile.length() > 44L) {
                    meta.put("fromHoldRelease", fromHoldRelease)
                    meta.put("recordedByPromptSelfControl", recordedByPromptSelfControl)

                    // Save sidecar .json in pendingSyncDir until Project Host acknowledges with WAV_SYNC_ACK
                    val metaFile = File(pendingSyncDir, "${wavFile.name}.json")
                    metaFile.writeText(meta.toString(), Charsets.UTF_8)
                    refreshPendingSyncCount()

                    val sent = transport.sendWavFile(meta, wavFile)
                    if (!sent) {
                        // Offline fallback: still surface local natural coaching on 看读端 even during a Wi-Fi blip
                        val offlineCoaching = buildCoachingFeedbackForTake(
                            wordId = meta.optLong("wordId", 0L),
                            seqPos = meta.optInt("seqPos", 0),
                            finalStatus = localQual.status,
                            recordedByPromptSelfControl = recordedByPromptSelfControl
                        )
                        _sessionSnapshot.value = _sessionSnapshot.value.copy(latestCoaching = offlineCoaching)
                        _statusBanner.value = S.wireless.disconnectedSavedLocally
                    }
                }
            }
        }
    }

    private fun archiveSyncFileIntoLocalCapsule(
        meta: JSONObject,
        sourceWav: File,
        deleteSourceAfterCopy: Boolean
    ) {
        if (!sourceWav.exists() || sourceWav.length() <= 44L) return
        val snap = _sessionSnapshot.value
        val spk = snap.currentSpeakerSummary
        val sessionCode = meta.optString("sessionCode", snap.sessionCode).ifBlank { currentSessionCode }
        val projName = meta.optString("projectName", snap.projectName).ifBlank { S.wireless.linkedWordlistFallback }
        val spkName = meta.optString("speakerName", snap.speakerName).ifBlank { S.common.defaultSpeakerFallback }
        val spkGender = meta.optString("speakerGender", spk?.gender ?: "")
        val spkAge = meta.optInt("speakerAge", spk?.age ?: 0)
        val spkDialect = meta.optString("speakerDialect", spk?.dialect ?: "")
        val seqPos = meta.optInt("seqPos", 0)
        val wordId = meta.optLong("wordId", 0L)
        val matchedWord = snap.words.find { it.wordId > 0L && it.wordId == wordId }
            ?: snap.words.getOrNull(seqPos)
        val canonicalSlot = meta.optInt("originalIndex", matchedWord?.originalIndex ?: seqPos).coerceAtLeast(0)

        val wordText = meta.optString("wordText", matchedWord?.text ?: "").ifBlank { matchedWord?.text.orEmpty() }
        val wordRemark = meta.optString("wordRemark", matchedWord?.remark ?: "").ifBlank { matchedWord?.remark.orEmpty() }
        val wordGroup = meta.optString("wordGroup", matchedWord?.groupName ?: "").ifBlank { matchedWord?.groupName.orEmpty() }
        val speakerNote = meta.optString("speakerNote", matchedWord?.speakerNote ?: "").ifBlank { matchedWord?.speakerNote.orEmpty() }

        WirelessSessionArchiveManager.archiveTakeIntoCapsule(
            context = context,
            sessionCode = sessionCode,
            projectName = projName,
            speakerName = spkName,
            speakerGender = spkGender,
            speakerAge = spkAge,
            speakerDialect = spkDialect,
            allSnapshotWords = snap.words,
            seqPos = canonicalSlot,
            wordText = wordText,
            wordRemark = wordRemark,
            wordGroup = wordGroup,
            speakerNote = speakerNote,
            sourceWavFile = sourceWav,
            deleteSourceAfterCopy = deleteSourceAfterCopy,
            lowVolumeRmsThreshold = prefs.getFloat("low_volume", 250f)
        )
        _capsulesRefreshVersion.value = _capsulesRefreshVersion.value + 1
    }

    /**
     * Rescues any unsynced offline WAV files in `pendingSyncDir` into a local self-contained
     * Wireless Session Capsule (`wireless_archives/`) so zero field audio is ever stranded if
     * the Project Host device becomes unavailable.
     */
    suspend fun rescuePendingSyncToLocalCapsule(): Int = withContext(Dispatchers.IO) {
        val metaFiles = pendingSyncDir.listFiles { _, name -> name.endsWith(".wav.json") }.orEmpty()
        var rescued = 0
        for (mf in metaFiles) {
            val wavName = mf.name.removeSuffix(".json")
            val wf = File(pendingSyncDir, wavName)
            if (!wf.exists() || wf.length() <= 44L) {
                mf.delete()
                wf.delete()
                continue
            }
            val meta = runCatching { JSONObject(mf.readText(Charsets.UTF_8)) }.getOrNull() ?: JSONObject()
            archiveSyncFileIntoLocalCapsule(meta, wf, deleteSourceAfterCopy = true)
            mf.delete()
            rescued++
        }
        refreshPendingSyncCount()
        rescued
    }

    fun notifyCapsulesChanged() {
        _capsulesRefreshVersion.value = _capsulesRefreshVersion.value + 1
    }

    // =========================================================================
    // Incoming Transport Handlers
    // =========================================================================

    private fun handlePeerConnected(peerName: String, peerRole: LinkRole) {
        requestedImageKeys.clear()
        _peerDeviceName.value = peerName
        _isConnected.value = true
        _isHostingWaiting.value = false
        _connectionEpoch.value = _connectionEpoch.value + 1
        syncRecorderStandbyState()

        val msg = S.wireless.connectedToPeer.withArgs("name" to peerName)
        _statusBanner.value = msg
        scope.launch {
            delay(3600L)
            if (_statusBanner.value == msg) {
                _statusBanner.value = null
            }
        }
        if (_isProjectHost.value) {
            broadcastSnapshotFromProjectHost(includeWordList = true)
        }
    }

    private fun handlePeerDisconnected(reason: String) {
        _isConnected.value = false
        _connectionEpoch.value = 0
        peerCaptureActive = false
        _rttMs.value = 0
        earMonitor.setMonitoringEnabled(false)
        syncRecorderStandbyState()
        if (_isProjectHost.value) {
            _statusBanner.value = reason
        } else {
            _statusBanner.value = if (_pendingSyncCount.value > 0) {
                S.wireless.disconnectedPendingSync.withArgs("reason" to reason, "count" to _pendingSyncCount.value)
            } else {
                reason
            }
        }
    }

    private fun handleIncomingUdpTelemetry(dbfs: Float, isClipping: Boolean, pcm16k: ShortArray) {
        _remoteDbfs.value = dbfs
        _remoteIsClipping.value = isClipping
        appendWaveformDbfs(dbfs)
        if (_activeRole.value == LinkRole.CONTROLLER) {
            earMonitor.feed16kHzPcmFrame(pcm16k)
        }
    }

    private fun handleIncomingJson(json: JSONObject) {
        when (json.optString("type")) {
            "REQUEST_SNAPSHOT" -> {
                if (_isProjectHost.value) {
                    broadcastSnapshotFromProjectHost(includeWordList = true)
                }
            }
            "SESSION_SNAPSHOT" -> {
                val snapObj = json.optJSONObject("snapshot") ?: return
                val prev = _sessionSnapshot.value
                val parsed = RemoteSessionSnapshot.fromJson(
                    snapObj,
                    fallbackWords = prev.words,
                    fallbackProjects = prev.availableProjects,
                    fallbackSpeakers = prev.availableSpeakers
                )
                if (parsed.sessionCode.isNotBlank()) {
                    currentSessionCode = parsed.sessionCode
                }
                val micChanged = _activeMicOwner.value != parsed.activeMicOwner
                _sessionSnapshot.value = parsed
                _activeMicOwner.value = parsed.activeMicOwner
                _promptPolicy.value = parsed.policy
                if (micChanged) {
                    syncRecorderStandbyState()
                }
                prefetchSnapshotImages(parsed)
            }
            "POLICY_UPDATE" -> {
                val pol = PromptDisplayPolicy.fromJson(json.optJSONObject("policy"))
                _promptPolicy.value = pol
                _sessionSnapshot.value = _sessionSnapshot.value.copy(policy = pol)
                if (_isProjectHost.value) pol.saveToPrefs(prefs)
            }
            "MIC_OWNER_UPDATE" -> {
                val owner = runCatching {
                    ActiveMicOwner.valueOf(json.optString("activeMicOwner"))
                }.getOrDefault(ActiveMicOwner.PROMPT_DISPLAY_MIC)
                _activeMicOwner.value = owner
                _sessionSnapshot.value = _sessionSnapshot.value.copy(activeMicOwner = owner)
                syncRecorderStandbyState()
            }
            "REMOTE_CAPTURE_STATE" -> {
                val rec = json.optBoolean("isRecording", false)
                peerCaptureActive = rec
                if (rec) {
                    latestCoachingState = null
                }
                _sessionSnapshot.value = _sessionSnapshot.value.copy(
                    isRecording = rec,
                    latestCoaching = if (rec) null else _sessionSnapshot.value.latestCoaching
                )
                if (_isProjectHost.value) {
                    broadcastSnapshotFromProjectHost(includeWordList = false, overrideIsRecording = rec)
                }
            }
            "CMD_START_CAPTURE" -> {
                startCaptureOnThisDevice(explicitMeta = json)
            }
            "CMD_STOP_CAPTURE" -> {
                val fromHold = json.optBoolean("fromHoldRelease", false)
                val byPromptSelf = json.optBoolean("recordedByPromptSelfControl", false)
                val immediateNav = json.optBoolean("immediateForNavigation", false)
                stopCaptureOnThisDevice(
                    fromHoldRelease = fromHold,
                    recordedByPromptSelfControl = byPromptSelf,
                    tailHangoverMs = if (immediateNav) 0L else 115L
                )
            }
            "CMD_NAV" -> {
                if (!_isProjectHost.value) return
                stopActiveCaptureBeforeWordNavigation()
                latestCoachingState = null
                resetTemporaryRemarkOnTurn()
                when (json.optString("op")) {
                    "NEXT" -> bridge.performLocalNextWord()
                    "PREV" -> bridge.performLocalPrevWord()
                    "NEXT_UNRECORDED" -> bridge.performLocalJumpNextUnrecorded()
                    "SELECT" -> bridge.performLocalSelectSeqPos(json.optInt("seqPos", 0))
                }
                broadcastSnapshotFromProjectHost(includeWordList = false)
            }
            "CMD_SHUFFLE" -> {
                if (!_isProjectHost.value) return
                latestCoachingState = null
                resetTemporaryRemarkOnTurn()
                when (json.optString("op")) {
                    "TOGGLE" -> bridge.performLocalToggleShuffle()
                    "SET_ENABLED" -> bridge.performLocalSetShuffleEnabled(json.optBoolean("enabled", false))
                    "SET_MODE" -> bridge.performLocalSetShufflingMode(json.optString("mode", "RANDOM_ALL"))
                    "RESHUFFLE" -> bridge.performLocalReshuffle()
                }
                broadcastSnapshotFromProjectHost(includeWordList = true)
            }
            "CMD_SELECT_PROJECT" -> {
                if (!_isProjectHost.value) return
                val projectId = json.optLong("projectId", 0L)
                if (projectId > 0L) {
                    latestCoachingState = null
                    resetTemporaryRemarkOnTurn()
                    bridge.performLocalSelectProject(projectId)
                    scope.launch(Dispatchers.IO) {
                        delay(120L)
                        broadcastSnapshotFromProjectHost(includeWordList = true)
                    }
                }
            }
            "CMD_SELECT_SPEAKER" -> {
                if (!_isProjectHost.value) return
                val speakerId = json.optLong("speakerId", 0L)
                if (speakerId > 0L) {
                    latestCoachingState = null
                    resetTemporaryRemarkOnTurn()
                    bridge.performLocalSelectSpeaker(speakerId)
                    scope.launch(Dispatchers.IO) {
                        delay(120L)
                        broadcastSnapshotFromProjectHost(includeWordList = true)
                    }
                }
            }
            "CMD_CREATE_SPEAKER" -> {
                if (!_isProjectHost.value) return
                val spkName = json.optString("name", "").trim()
                val gender = json.optString("gender", "男")
                val age = json.optInt("age", 30)
                val dialect = json.optString("dialect", "")
                if (spkName.isNotEmpty()) {
                    bridge.performLocalCreateSpeaker(spkName, gender, age, dialect)
                    scope.launch(Dispatchers.IO) {
                        delay(180L)
                        broadcastSnapshotFromProjectHost(includeWordList = true)
                    }
                }
            }
            "CMD_UPDATE_NOTE" -> {
                if (!_isProjectHost.value) return
                val wordId = json.optLong("wordId", 0L)
                val note = json.optString("note", "")
                if (wordId > 0L) {
                    bridge.performLocalUpdateSpeakerNote(wordId, note)
                    scope.launch(Dispatchers.IO) {
                        delay(80L)
                        broadcastSnapshotFromProjectHost(includeWordList = true)
                    }
                }
            }
            "CMD_TOGGLE_FLAG" -> {
                if (!_isProjectHost.value) return
                val wordId = json.optLong("wordId", 0L)
                if (wordId > 0L) {
                    bridge.performLocalToggleQualityFlag(wordId)
                    scope.launch(Dispatchers.IO) {
                        delay(100L)
                        broadcastSnapshotFromProjectHost(includeWordList = true)
                    }
                }
            }
            "CMD_REQUEST_AUDITION_WAV" -> {
                if (!_isProjectHost.value) return
                val projectId = json.optLong("projectId", 0L)
                val wordId = json.optLong("wordId", 0L)
                val speakerId = json.optLong("speakerId", 0L)
                val wav = bridge.resolveRecordedWavFile(projectId, wordId, speakerId)
                if (wav != null && wav.exists()) {
                    scope.launch(Dispatchers.IO) {
                        val meta = JSONObject().apply {
                            put("purpose", "AUDITION_PLAYBACK")
                            put("wordId", wordId)
                        }
                        transport.sendWavFile(meta, wav)
                    }
                } else {
                    transport.sendJson(
                        JSONObject().put("type", "AUDITION_UNAVAILABLE")
                    )
                }
            }
            "CMD_REQUEST_WORD_IMAGE" -> {
                if (!_isProjectHost.value) return
                val projectId = json.optLong("projectId", 0L)
                val wordId = json.optLong("wordId", 0L)
                val imageToken = json.optString("imageToken", "")
                if (projectId <= 0L || imageToken.isBlank()) return
                val imgFile = bridge.resolveWordImageFile(projectId, wordId, imageToken)
                if (imgFile != null && imgFile.exists() && imgFile.length() > 0L) {
                    scope.launch(Dispatchers.IO) {
                        val meta = JSONObject().apply {
                            put("purpose", "WORD_IMAGE_SYNC")
                            put("projectId", projectId)
                            put("wordId", wordId)
                            put("imageToken", imageToken)
                        }
                        transport.sendWavFile(meta, imgFile)
                    }
                }
            }
            "AUDITION_UNAVAILABLE" -> {
                _statusBanner.value = S.wireless.wordHasNoRecording
            }
            "WAV_SYNC_ACK" -> {
                val ackName = json.optString("localFileName", "")
                if (ackName.isNotBlank()) {
                    scope.launch(Dispatchers.IO) {
                        val wf = File(pendingSyncDir, ackName)
                        val mf = File(pendingSyncDir, "$ackName.json")
                        val storageMode = _promptPolicy.value.storageMode
                        if ((storageMode == WirelessStorageMode.HOST_AND_CAPTURE_BACKUP ||
                                storageMode == WirelessStorageMode.DUAL_FULL_MIRROR) &&
                            wf.exists() && wf.length() > 44L
                        ) {
                            val metaObj = if (mf.exists()) {
                                runCatching { JSONObject(mf.readText(Charsets.UTF_8)) }.getOrNull() ?: JSONObject()
                            } else {
                                JSONObject()
                            }
                            archiveSyncFileIntoLocalCapsule(metaObj, wf, deleteSourceAfterCopy = true)
                        } else {
                            wf.delete()
                        }
                        mf.delete()
                        refreshPendingSyncCount()
                    }
                }
            }
            "BYE" -> {
                transport.disconnect(notifyPeer = false)
                handlePeerDisconnected(S.wireless.peerDisconnected)
                if (!_isProjectHost.value) {
                    _activeRole.value = LinkRole.STANDALONE
                }
            }
        }
    }

    private fun handleIncomingWav(meta: JSONObject, wavBytes: ByteArray, crcValid: Boolean) {
        val purpose = meta.optString("purpose")
        if (purpose == "WORD_IMAGE_SYNC") {
            val projectId = meta.optLong("projectId", 0L)
            val imageToken = meta.optString("imageToken", "")
            val reqKey = "${projectId}:${imageToken}"
            if (!crcValid || wavBytes.isEmpty() || projectId <= 0L || imageToken.isBlank()) {
                requestedImageKeys.remove(reqKey)
                return
            }
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val dst = remoteImageCacheFile(projectId, imageToken)
                    dst.parentFile?.mkdirs()
                    val tmp = File(dst.parentFile, "${dst.name}.tmp")
                    tmp.writeBytes(wavBytes)
                    tmp.copyTo(dst, overwrite = true)
                    tmp.delete()
                }
                requestedImageKeys.remove(reqKey)
                _imageCacheVersion.value = _imageCacheVersion.value + 1
            }
            return
        }

        if (!crcValid || wavBytes.size <= 44) {
            _statusBanner.value = S.wireless.syncRetryBanner
            return
        }
        when (purpose) {
            "RECORDED_TAKE_SYNC" -> {
                if (!_isProjectHost.value) return
                val projectId = meta.optLong("projectId", 0L)
                val wordId = meta.optLong("wordId", 0L)
                val speakerId = meta.optLong("speakerId", 0L)
                val seqPos = meta.optInt("seqPos", _sessionSnapshot.value.currentSeqPos)
                val remoteFileName = meta.optString("localFileName", "")
                val fromHoldRelease = meta.optBoolean("fromHoldRelease", false)
                val recordedByPromptSelfControl = meta.optBoolean("recordedByPromptSelfControl", false)
                if (projectId <= 0L || wordId <= 0L) return

                scope.launch(Dispatchers.IO) {
                    val tempFile = File(
                        context.cacheDir,
                        "incoming_link_${projectId}_${wordId}_${System.currentTimeMillis()}.wav"
                    )
                    tempFile.writeBytes(wavBytes)
                    val finalStatus = bridge.ingestRemoteRecordedWav(projectId, wordId, speakerId, tempFile)
                    tempFile.delete()

                    transport.sendJson(
                        JSONObject().apply {
                            put("type", "WAV_SYNC_ACK")
                            put("localFileName", remoteFileName)
                            put("qualityStatus", finalStatus)
                        }
                    )

                    peerCaptureActive = false
                    val pol = _promptPolicy.value
                    // Only emit coaching / auto-advance if this synced WAV belongs to the currently active word
                    val curAuthoritative = currentAuthoritativeSnapshot()
                    val isCurrentWordTake = curAuthoritative.currentWord?.wordId == wordId

                    if (isCurrentWordTake) {
                        latestCoachingState = buildCoachingFeedbackForTake(
                            wordId = wordId,
                            seqPos = seqPos,
                            finalStatus = finalStatus,
                            recordedByPromptSelfControl = recordedByPromptSelfControl
                        )
                        if (fromHoldRelease && pol.autoAdvanceOnHoldRelease) {
                            if (shouldBlockAutoAdvanceForStatus(pol, finalStatus)) {
                                _statusBanner.value = S.wireless.holdQualityNoticeStay.withArgs("status" to S.quality.displayStatus(finalStatus))
                            } else {
                                var advanced = false
                                withContext(Dispatchers.Main) {
                                    resetTemporaryRemarkOnTurn()
                                    advanced = bridge.performLocalNextWord()
                                }
                                if (advanced) {
                                    latestCoachingState = null
                                }
                            }
                        }
                    }

                    broadcastSnapshotFromProjectHost(includeWordList = true, overrideIsRecording = false)
                }
            }
            "HOST_WAV_MIRROR_SYNC" -> {
                if (_isProjectHost.value) return
                scope.launch(Dispatchers.IO) {
                    val tempMirror = File(
                        context.cacheDir,
                        "incoming_mirror_${System.currentTimeMillis()}.wav"
                    )
                    tempMirror.writeBytes(wavBytes)
                    archiveSyncFileIntoLocalCapsule(meta, tempMirror, deleteSourceAfterCopy = true)
                }
            }
            "AUDITION_PLAYBACK" -> {
                scope.launch(Dispatchers.IO) {
                    val tempAudition = File(context.cacheDir, "remote_audition_current.wav")
                    tempAudition.writeBytes(wavBytes)
                    withContext(Dispatchers.Main) {
                        playLocalAuditionFile(tempAudition)
                    }
                }
            }
        }
    }

    private fun flushPendingSyncQueue() {
        scope.launch(Dispatchers.IO) {
            val metaFiles = pendingSyncDir.listFiles { _, name -> name.endsWith(".wav.json") }.orEmpty()
            for (mf in metaFiles) {
                if (!_isConnected.value) break
                val wavName = mf.name.removeSuffix(".json")
                val wf = File(pendingSyncDir, wavName)
                if (!wf.exists() || wf.length() <= 44L) {
                    mf.delete()
                    wf.delete()
                    continue
                }
                val meta = runCatching { JSONObject(mf.readText(Charsets.UTF_8)) }.getOrNull() ?: continue
                // Never trigger surprise auto-advance from old queued takes flushed upon reconnect
                meta.put("fromHoldRelease", false)
                transport.sendWavFile(meta, wf)
            }
            refreshPendingSyncCount()
        }
    }

    private fun refreshPendingSyncCount() {
        val count = pendingSyncDir.listFiles { _, name -> name.endsWith(".wav.json") }?.size ?: 0
        _pendingSyncCount.value = count
    }

    fun clearBanner() {
        _statusBanner.value = null
    }
}
