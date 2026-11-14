package com.example.link

import com.example.i18n.S
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.CRC32

/**
 * Pure JDK 17 LAN / Hotspot transport for PhonAcq Multi-Device Collaboration.
 * Works identically on Android, Windows Desktop, and local JVM loopback tests:
 *  - UDP Discovery Beacons (port 41728)
 *  - TCP Framed Control + Lossless WAV Sync + Heartbeat RTT
 *  - UDP Real-Time 16kHz Ear-Monitor + dBFS Telemetry Stream
 */
class WirelessLinkTransport(
    private val scope: CoroutineScope,
    private val onJsonMessage: (JSONObject) -> Unit,
    private val onBinaryWavReceived: (meta: JSONObject, wavBytes: ByteArray, crcValid: Boolean) -> Unit,
    private val onUdpAudioTelemetry: (dbfs: Float, isClipping: Boolean, pcm16k: ShortArray) -> Unit,
    private val onPeerConnected: (peerName: String, peerRole: LinkRole) -> Unit,
    private val onPeerDisconnected: (reason: String) -> Unit,
    private val onRttUpdated: (rttMs: Int) -> Unit
) {
    companion object {
        const val DISCOVERY_UDP_PORT = 41728
        const val DEFAULT_TCP_PORT = 41730
        private const val BEACON_MAGIC = "PHONACQ_LINK_V1"

        private const val FRAME_JSON: Byte = 0x01
        private const val FRAME_WAV: Byte = 0x02
        private const val FRAME_PING: Byte = 0x04
        private const val FRAME_PONG: Byte = 0x05

        private const val UDP_AUDIO_MAGIC: Short = 0x5041 // 'PA'

        fun listLocalIpv4Addresses(): List<String> {
            val result = linkedSetOf<String>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
                while (interfaces.hasMoreElements()) {
                    val nif = interfaces.nextElement()
                    if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                    val addrs = nif.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            addr.hostAddress?.let { result.add(it) }
                        }
                    }
                }
            } catch (_: Exception) {}
            return result.toList()
        }

        private fun listBroadcastTargets(): List<InetAddress> {
            val targets = linkedSetOf<InetAddress>()
            try {
                targets.add(InetAddress.getByName("255.255.255.255"))
                val interfaces = NetworkInterface.getNetworkInterfaces()
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        val nif = interfaces.nextElement()
                        if (!nif.isUp || nif.isLoopback) continue
                        for (ia in nif.interfaceAddresses) {
                            ia.broadcast?.let { targets.add(it) }
                        }
                    }
                }
            } catch (_: Exception) {}
            return targets.toList()
        }

        fun computeCrc32(bytes: ByteArray): Long {
            val crc = CRC32()
            crc.update(bytes)
            return crc.value
        }
    }

    val hostId: String = UUID.randomUUID().toString().take(8)

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers = _discoveredPeers.asStateFlow()

    @Volatile
    var boundTcpPort: Int = 0
        private set

    @Volatile
    var boundUdpAudioPort: Int = 0
        private set

    @Volatile
    private var peerIpAddress: InetAddress? = null
    @Volatile
    private var peerUdpAudioPort: Int = 0

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var dataOut: DataOutputStream? = null
    private val writeLock = Any()

    private var udpAudioSocket: DatagramSocket? = null
    private var discoveryListenSocket: DatagramSocket? = null

    private var hostAcceptJob: Job? = null
    private var beaconBroadcastJob: Job? = null
    private var discoveryListenJob: Job? = null
    private var tcpReaderJob: Job? = null
    private var tcpWriterJob: Job? = null
    private var outboundChannel: Channel<ByteArray>? = null
    private var udpAudioReaderJob: Job? = null
    private var heartbeatJob: Job? = null

    @Volatile
    private var expectedPin: String = ""
    @Volatile
    private var localDeviceName: String = "PhonAcq"
    @Volatile
    private var localRole: LinkRole = LinkRole.STANDALONE

    fun startDiscoveryListener() {
        if (discoveryListenJob?.isActive == true) return
        discoveryListenJob = scope.launch(Dispatchers.IO) {
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 1500
                    bind(InetSocketAddress(DISCOVERY_UDP_PORT))
                }
                discoveryListenSocket = sock
                val buf = ByteArray(2048)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        sock.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        if (json.optString("magic") != BEACON_MAGIC) continue
                        val remoteHostId = json.optString("hostId", "")
                        if (remoteHostId.isBlank() || remoteHostId == hostId) continue

                        val senderIp = packet.address?.hostAddress ?: continue
                        val role = runCatching {
                            LinkRole.valueOf(json.optString("hostRole", LinkRole.CONTROLLER.name))
                        }.getOrDefault(LinkRole.CONTROLLER)

                        val peer = DiscoveredPeer(
                            hostId = remoteHostId,
                            deviceName = json.optString("deviceName", S.wireless.transportLanPeerFallback),
                            ipAddress = senderIp,
                            tcpPort = json.optInt("tcpPort", DEFAULT_TCP_PORT),
                            hostRole = role,
                            projectName = json.optString("projectName", S.wireless.transportProjectFallback),
                            lastSeenMs = System.currentTimeMillis()
                        )
                        updateDiscoveredPeer(peer)
                    } catch (_: SocketTimeoutException) {
                        pruneStalePeers()
                    } catch (_: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (_: Exception) {
                // Port may already be bound by another instance on desktop; manual IP still works
            } finally {
                try {
                    sock?.close()
                } catch (_: Exception) {}
                discoveryListenSocket = null
            }
        }
    }

    fun stopDiscoveryListener() {
        discoveryListenJob?.cancel()
        discoveryListenJob = null
        try {
            discoveryListenSocket?.close()
        } catch (_: Exception) {}
        discoveryListenSocket = null
    }

    private fun updateDiscoveredPeer(peer: DiscoveredPeer) {
        val now = System.currentTimeMillis()
        val updated = _discoveredPeers.value
            .filter { it.hostId != peer.hostId && (now - it.lastSeenMs) < 9000L } + peer
        _discoveredPeers.value = updated.sortedBy { it.deviceName }
    }

    private fun pruneStalePeers() {
        val now = System.currentTimeMillis()
        val filtered = _discoveredPeers.value.filter { (now - it.lastSeenMs) < 9000L }
        if (filtered.size != _discoveredPeers.value.size) {
            _discoveredPeers.value = filtered
        }
    }

    /**
     * Starts hosting a wireless session on an available TCP port + UDP audio port,
     * broadcasting discovery beacons until a peer authenticates with [pin].
     */
    fun startHosting(
        deviceName: String,
        role: LinkRole,
        pin: String,
        projectNameProvider: () -> String,
        preferredPort: Int = 0
    ): Int {
        closeSessionSilently()
        localDeviceName = deviceName
        localRole = role
        expectedPin = pin

        val srv = try {
            if (preferredPort > 0) {
                ServerSocket(preferredPort)
            } else {
                try {
                    ServerSocket(DEFAULT_TCP_PORT)
                } catch (_: Exception) {
                    ServerSocket(0)
                }
            }
        } catch (_: Exception) {
            ServerSocket(0)
        }
        boundTcpPort = srv.localPort
        serverSocket = srv

        ensureUdpAudioSocketOpen()

        // Broadcast UDP discovery beacons every 1.2s across all LAN / Hotspot subnets
        beaconBroadcastJob = scope.launch(Dispatchers.IO) {
            var bcastSock: DatagramSocket? = null
            try {
                bcastSock = DatagramSocket().apply { broadcast = true }
                while (isActive && activeSocket == null) {
                    val payload = JSONObject().apply {
                        put("magic", BEACON_MAGIC)
                        put("hostId", hostId)
                        put("deviceName", localDeviceName)
                        put("tcpPort", boundTcpPort)
                        put("hostRole", localRole.name)
                        put("projectName", projectNameProvider())
                    }.toString().toByteArray(Charsets.UTF_8)

                    for (target in listBroadcastTargets()) {
                        try {
                            bcastSock.send(DatagramPacket(payload, payload.size, target, DISCOVERY_UDP_PORT))
                        } catch (_: Exception) {}
                    }
                    delay(1200L)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    bcastSock?.close()
                } catch (_: Exception) {}
            }
        }

        hostAcceptJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val client = srv.accept()
                    client.tcpNoDelay = true
                    client.keepAlive = true
                    if (!handleIncomingHandshake(client)) {
                        try {
                            client.close()
                        } catch (_: Exception) {}
                        continue
                    }
                    break
                }
            } catch (_: Exception) {}
        }

        return boundTcpPort
    }

    private fun handleIncomingHandshake(client: Socket): Boolean {
        return try {
            client.soTimeout = 8000
            val dis = DataInputStream(BufferedInputStream(client.getInputStream()))
            val dos = DataOutputStream(BufferedOutputStream(client.getOutputStream()))

            val frameType = dis.readByte()
            if (frameType != FRAME_JSON) return false
            val len = dis.readInt()
            if (len <= 0 || len > 1_000_000) return false
            val bytes = ByteArray(len)
            dis.readFully(bytes)
            val hello = JSONObject(String(bytes, Charsets.UTF_8))
            if (hello.optString("type") != "HELLO_AUTH") return false

            val submittedPin = hello.optString("pin", "")
            if (submittedPin != expectedPin) {
                val rejectBytes = JSONObject().apply {
                    put("type", "HELLO_REJECT")
                    put("reason", S.wireless.pinMismatchReason)
                }.toString().toByteArray(Charsets.UTF_8)
                dos.writeByte(FRAME_JSON.toInt())
                dos.writeInt(rejectBytes.size)
                dos.write(rejectBytes)
                dos.flush()
                return false
            }

            val peerName = hello.optString("deviceName", S.wireless.transportGenericPeerFallback)
            val peerUdpPort = hello.optInt("udpAudioPort", 0)
            val assignedPeerRole = if (localRole == LinkRole.CONTROLLER) {
                LinkRole.PROMPT_DISPLAY
            } else {
                LinkRole.CONTROLLER
            }

            val acceptBytes = JSONObject().apply {
                put("type", "HELLO_ACCEPT")
                put("hostDeviceName", localDeviceName)
                put("hostRole", localRole.name)
                put("assignedRole", assignedPeerRole.name)
                put("udpAudioPort", boundUdpAudioPort)
            }.toString().toByteArray(Charsets.UTF_8)

            dos.writeByte(FRAME_JSON.toInt())
            dos.writeInt(acceptBytes.size)
            dos.write(acceptBytes)
            dos.flush()

            client.soTimeout = 0
            peerIpAddress = client.inetAddress
            peerUdpAudioPort = peerUdpPort
            activeSocket = client
            synchronized(writeLock) {
                dataOut = dos
            }
            beaconBroadcastJob?.cancel()
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            serverSocket = null

            startConnectedLoops(dis)
            onPeerConnected(peerName, assignedPeerRole)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Connects as a joining client to [ip]:[port] using the 6-digit [pin].
     * Returns `Result.success(assignedLocalRole)` or `Result.failure`.
     */
    suspend fun connectToHost(
        ip: String,
        port: Int,
        pin: String,
        deviceName: String
    ): Result<Pair<LinkRole, String>> = withContext(Dispatchers.IO) {
        closeSessionSilently()
        localDeviceName = deviceName
        ensureUdpAudioSocketOpen()

        val sock = Socket()
        try {
            sock.connect(InetSocketAddress(ip.trim(), port), 6000)
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.soTimeout = 8000

            val dis = DataInputStream(BufferedInputStream(sock.getInputStream()))
            val dos = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))

            val helloBytes = JSONObject().apply {
                put("type", "HELLO_AUTH")
                put("pin", pin.trim())
                put("deviceName", deviceName)
                put("udpAudioPort", boundUdpAudioPort)
            }.toString().toByteArray(Charsets.UTF_8)

            dos.writeByte(FRAME_JSON.toInt())
            dos.writeInt(helloBytes.size)
            dos.write(helloBytes)
            dos.flush()

            val respType = dis.readByte()
            if (respType != FRAME_JSON) {
                sock.close()
                return@withContext Result.failure(IllegalStateException(S.wireless.handshakeInvalidFormat))
            }
            val respLen = dis.readInt()
            val respBytes = ByteArray(respLen)
            dis.readFully(respBytes)
            val resp = JSONObject(String(respBytes, Charsets.UTF_8))

            if (resp.optString("type") != "HELLO_ACCEPT") {
                val reason = resp.optString("reason", S.wireless.pinWrongOrCancelled)
                sock.close()
                return@withContext Result.failure(IllegalArgumentException(reason))
            }

            val assignedRole = LinkRole.valueOf(resp.getString("assignedRole"))
            val hostRole = LinkRole.valueOf(resp.getString("hostRole"))
            val hostName = resp.optString("hostDeviceName", S.wireless.hostDeviceFallback)
            peerIpAddress = sock.inetAddress
            peerUdpAudioPort = resp.optInt("udpAudioPort", 0)
            localRole = assignedRole

            sock.soTimeout = 0
            activeSocket = sock
            synchronized(writeLock) {
                dataOut = dos
            }

            startConnectedLoops(dis)
            onPeerConnected(hostName, hostRole)
            Result.success(assignedRole to hostName)
        } catch (e: Exception) {
            try {
                sock.close()
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    private fun ensureUdpAudioSocketOpen() {
        if (udpAudioSocket != null && udpAudioSocket?.isClosed == false) return
        try {
            val sock = DatagramSocket(0).apply {
                soTimeout = 1500
            }
            boundUdpAudioPort = sock.localPort
            udpAudioSocket = sock
            udpAudioReaderJob?.cancel()
            udpAudioReaderJob = scope.launch(Dispatchers.IO) {
                val buf = ByteArray(4096)
                while (isActive && !sock.isClosed) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        sock.receive(pkt)
                        if (pkt.length < 10) continue
                        val bb = ByteBuffer.wrap(pkt.data, 0, pkt.length).order(ByteOrder.LITTLE_ENDIAN)
                        val magic = bb.short
                        if (magic != UDP_AUDIO_MAGIC) continue
                        val dbfs = bb.float
                        val clipFlag = bb.get().toInt() != 0
                        val sampleCount = bb.short.toInt().coerceAtLeast(0)
                        val availableShorts = (pkt.length - 9) / 2
                        val actualShorts = minOf(sampleCount, availableShorts)
                        val shorts = ShortArray(actualShorts)
                        for (i in 0 until actualShorts) {
                            shorts[i] = bb.short
                        }
                        onUdpAudioTelemetry(dbfs, clipFlag, shorts)
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                        if (!isActive || sock.isClosed) break
                    }
                }
            }
        } catch (_: Exception) {
            boundUdpAudioPort = 0
        }
    }

    private fun startConnectedLoops(dis: DataInputStream) {
        outboundChannel?.close()
        val ch = Channel<ByteArray>(Channel.UNLIMITED)
        outboundChannel = ch

        tcpWriterJob?.cancel()
        tcpWriterJob = scope.launch(Dispatchers.IO) {
            for (framedBytes in ch) {
                if (!isActive) break
                val wroteOk = synchronized(writeLock) {
                    val out = dataOut ?: return@synchronized false
                    try {
                        out.write(framedBytes)
                        out.flush()
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                if (!wroteOk) break
            }
        }

        tcpReaderJob?.cancel()
        tcpReaderJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val frameType = dis.readByte()
                    when (frameType) {
                        FRAME_JSON -> {
                            val len = dis.readInt()
                            if (len <= 0 || len > 8_000_000) break
                            val payload = ByteArray(len)
                            dis.readFully(payload)
                            val json = JSONObject(String(payload, Charsets.UTF_8))
                            onJsonMessage(json)
                        }
                        FRAME_WAV -> {
                            val metaLen = dis.readInt()
                            if (metaLen <= 0 || metaLen > 512_000) break
                            val metaBytes = ByteArray(metaLen)
                            dis.readFully(metaBytes)
                            val meta = JSONObject(String(metaBytes, Charsets.UTF_8))
                            val wavLen = dis.readInt()
                            if (wavLen < 0 || wavLen > 80_000_000) break
                            val wavBytes = ByteArray(wavLen)
                            dis.readFully(wavBytes)
                            val expectedCrc = meta.optLong("crc32", -1L)
                            val actualCrc = computeCrc32(wavBytes)
                            onBinaryWavReceived(meta, wavBytes, expectedCrc == -1L || expectedCrc == actualCrc)
                        }
                        FRAME_PING -> {
                            val sentTs = dis.readLong()
                            sendRawPong(sentTs)
                        }
                        FRAME_PONG -> {
                            val sentTs = dis.readLong()
                            val rtt = (System.currentTimeMillis() - sentTs).toInt().coerceIn(1, 9999)
                            onRttUpdated(rtt)
                        }
                        else -> break
                    }
                }
            } catch (_: Exception) {
            } finally {
                val hadActive = activeSocket != null
                closeSessionSilently()
                if (hadActive) {
                    onPeerDisconnected(S.wireless.linkDisconnectedReason)
                }
            }
        }

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && activeSocket != null) {
                sendRawPing()
                delay(2500L)
            }
        }
    }

    fun sendJson(json: JSONObject): Boolean {
        val ch = outboundChannel ?: return false
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        val framed = ByteBuffer.allocate(5 + payload.size).apply {
            put(FRAME_JSON)
            putInt(payload.size)
            put(payload)
        }.array()
        return ch.trySend(framed).isSuccess
    }

    fun sendWavFile(meta: JSONObject, wavFile: File): Boolean {
        if (!wavFile.exists() || wavFile.length() <= 44L) return false
        return try {
            val wavBytes = wavFile.readBytes()
            val crc = computeCrc32(wavBytes)
            val enrichedMeta = JSONObject(meta.toString()).apply {
                put("crc32", crc)
                put("byteLength", wavBytes.size)
            }
            val metaBytes = enrichedMeta.toString().toByteArray(Charsets.UTF_8)
            synchronized(writeLock) {
                val out = dataOut ?: return false
                out.writeByte(FRAME_WAV.toInt())
                out.writeInt(metaBytes.size)
                out.write(metaBytes)
                out.writeInt(wavBytes.size)
                out.write(wavBytes)
                out.flush()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun sendUdpAudioTelemetry(dbfs: Float, isClipping: Boolean, pcm16k: ShortArray) {
        val sock = udpAudioSocket ?: return
        val addr = peerIpAddress ?: return
        val port = peerUdpAudioPort
        if (port <= 0) return

        val cappedCount = pcm16k.size.coerceAtMost(960)
        val totalLen = 9 + cappedCount * 2
        val buf = ByteArray(totalLen)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(UDP_AUDIO_MAGIC)
        bb.putFloat(dbfs)
        bb.put(if (isClipping) 1.toByte() else 0.toByte())
        bb.putShort(cappedCount.toShort())
        for (i in 0 until cappedCount) {
            bb.putShort(pcm16k[i])
        }
        try {
            sock.send(DatagramPacket(buf, buf.size, addr, port))
        } catch (_: Exception) {}
    }

    private fun sendRawPing() {
        val ch = outboundChannel ?: return
        val framed = ByteBuffer.allocate(9).apply {
            put(FRAME_PING)
            putLong(System.currentTimeMillis())
        }.array()
        ch.trySend(framed)
    }

    private fun sendRawPong(ts: Long) {
        val ch = outboundChannel ?: return
        val framed = ByteBuffer.allocate(9).apply {
            put(FRAME_PONG)
            putLong(ts)
        }.array()
        ch.trySend(framed)
    }

    fun disconnect(notifyPeer: Boolean = true) {
        val activeOut = synchronized(writeLock) { dataOut }
        if (notifyPeer && activeOut != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val payload = JSONObject().put("type", "BYE").toString().toByteArray(Charsets.UTF_8)
                    synchronized(writeLock) {
                        dataOut?.let { out ->
                            out.writeByte(FRAME_JSON.toInt())
                            out.writeInt(payload.size)
                            out.write(payload)
                            out.flush()
                        }
                    }
                }
                closeSessionSilently()
            }
        } else {
            closeSessionSilently()
        }
    }

    private fun closeSessionSilently() {
        outboundChannel?.close()
        outboundChannel = null
        tcpWriterJob?.cancel()
        tcpWriterJob = null
        hostAcceptJob?.cancel()
        hostAcceptJob = null
        beaconBroadcastJob?.cancel()
        beaconBroadcastJob = null
        tcpReaderJob?.cancel()
        tcpReaderJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        udpAudioReaderJob?.cancel()
        udpAudioReaderJob = null

        synchronized(writeLock) {
            try {
                dataOut?.close()
            } catch (_: Exception) {}
            dataOut = null
        }
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        try {
            udpAudioSocket?.close()
        } catch (_: Exception) {}
        udpAudioSocket = null

        peerIpAddress = null
        peerUdpAudioPort = 0
        boundTcpPort = 0
        boundUdpAudioPort = 0
    }
}
