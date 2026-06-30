package com.courtside.pickleball.wear

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.SystemClock
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.WearSyncContract
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object WearTabletFallbackSync {
    private const val TAG = "WearTabletFallback"
    private const val DISCOVERY_PROTOCOL = "RALLYSCORE_WATCH_TABLET_DISCOVERY_V1"
    private const val STATE_PROTOCOL = "RALLYSCORE_WATCH_TABLET_STATE_V1"
    private const val COMMAND_PROTOCOL = "RALLYSCORE_WATCH_TABLET_COMMAND_V1"
    private const val DISCOVERY_PORT = 45461
    private const val SOCKET_TIMEOUT_MS = 1_200
    private const val CONNECTION_GRACE_MS = 12_000L
    private const val DISCOVERY_STALE_MS = 8_000L
    private const val PREFS_NAME = "wear_tablet_fallback_sync"
    private const val KEY_SELECTED_COURT_CODE = "selected_court_code"

    enum class DiscoveryStatus {
        Searching,
        Found,
        Connected
    }

    data class TabletCandidate(
        val courtCode: String,
        val endpoint: InetSocketAddress
    )

    private data class TabletCandidateRecord(
        val courtCode: String,
        val endpoint: InetSocketAddress,
        val lastSeenAtElapsed: Long
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tabletScoreState = MutableStateFlow<PhoneScoreState?>(null)
    val tabletScoreState: StateFlow<PhoneScoreState?> = _tabletScoreState.asStateFlow()
    private val _tabletConnected = MutableStateFlow(false)
    val tabletConnected: StateFlow<Boolean> = _tabletConnected.asStateFlow()
    private val _discoveryStatus = MutableStateFlow(DiscoveryStatus.Searching)
    val discoveryStatus: StateFlow<DiscoveryStatus> = _discoveryStatus.asStateFlow()
    private val _discoveredTablets = MutableStateFlow<List<TabletCandidate>>(emptyList())
    val discoveredTablets: StateFlow<List<TabletCandidate>> = _discoveredTablets.asStateFlow()
    private val _selectedCourtCode = MutableStateFlow<String?>(null)
    val selectedCourtCode: StateFlow<String?> = _selectedCourtCode.asStateFlow()

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var discoveryJob: Job? = null
    private var socketReaderJob: Job? = null
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var lastTabletSeenAtElapsed: Long = 0L
    @Volatile private var connectedEndpoint: InetSocketAddress? = null
    private val discoveredTabletRecords = linkedMapOf<String, TabletCandidateRecord>()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _selectedCourtCode.value = prefs?.getString(KEY_SELECTED_COURT_CODE, null)
        ensureMulticastLock()
        discoveryJob = scope.launch {
            runDiscoveryLoop()
        }
    }

    fun cycleSelectedTablet() {
        val tablets = _discoveredTablets.value
        if (tablets.isEmpty()) return
        val currentIndex = tablets.indexOfFirst { it.courtCode == _selectedCourtCode.value }
        val nextIndex = if (currentIndex < 0 || currentIndex + 1 >= tablets.size) 0 else currentIndex + 1
        val next = tablets[nextIndex]
        selectTablet(next.courtCode)
    }

    fun selectTablet(courtCode: String) {
        _selectedCourtCode.value = courtCode
        prefs?.edit()?.putString(KEY_SELECTED_COURT_CODE, courtCode)?.apply()
        val endpoint = synchronized(discoveredTabletRecords) {
            discoveredTabletRecords[courtCode]?.endpoint
        }
        if (endpoint != null) {
            connect(endpoint)
        }
    }

    fun sendCommand(commandPath: String): Boolean {
        val socket = activeSocket
        if (socket == null) {
            val endpoint = connectedEndpoint ?: return false
            WearSyncLog.debug(TAG) { "No active tablet socket, sending one-shot command" }
            scope.launch {
                sendOneShotCommand(endpoint, commandPath)
            }
            return true
        }
        scope.launch {
            try {
                WearSyncLog.debug(TAG) { "Sending tablet command over active socket" }
                PrintWriter(socket.getOutputStream(), true).println(
                    listOf(COMMAND_PROTOCOL, commandPath, System.currentTimeMillis().toString()).joinToString("|")
                )
            } catch (error: Exception) {
                WearSyncLog.warn(TAG, "Unable to send watch-tablet command", "Unable to send watch-tablet command: $commandPath", error)
                disconnect()
            }
        }
        return true
    }

    private fun sendOneShotCommand(endpoint: InetSocketAddress, commandPath: String): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(endpoint, SOCKET_TIMEOUT_MS)
                PrintWriter(socket.getOutputStream(), true).println(
                    listOf(COMMAND_PROTOCOL, commandPath, System.currentTimeMillis().toString()).joinToString("|")
                )
            }
            markTabletSeen()
            _discoveryStatus.value = DiscoveryStatus.Found
            connect(endpoint)
            true
        } catch (error: Exception) {
            WearSyncLog.warn(TAG, "Unable to send one-shot tablet command", "Unable to send one-shot tablet command: $commandPath", error)
            false
        }

    private suspend fun runDiscoveryLoop() {
        try {
            ensureMulticastLock()
            DatagramSocket(DISCOVERY_PORT).use { socket ->
                socket.broadcast = true
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val buffer = ByteArray(1024)
                while (true) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val payload = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                        val candidate = payload.toTabletCandidate(packet) ?: continue
                        observeTabletCandidate(candidate)
                        WearSyncLog.debug(TAG) {
                            "Discovered tablet fallback court ${candidate.courtCode} at ${candidate.endpoint.address.hostAddress}:${candidate.endpoint.port}"
                        }
                        if (connectedEndpoint == candidate.endpoint && activeSocket != null) {
                            markTabletSeen()
                            continue
                        }
                        val selectedCourtCode = _selectedCourtCode.value
                        when {
                            selectedCourtCode == null -> selectTablet(candidate.courtCode)
                            selectedCourtCode == candidate.courtCode -> connect(candidate.endpoint)
                        }
                    } catch (_: SocketTimeoutException) {
                        pruneStaleDiscoveredTablets()
                        updateConnectionWithGrace()
                    }
                }
            }
        } catch (error: Exception) {
            WearSyncLog.warn(TAG, "Watch-tablet discovery loop stopped", "Watch-tablet discovery loop stopped", error)
        }
    }

    private fun ensureMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifiManager = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        val lock = wifiManager.createMulticastLock("$TAG-lock").apply {
            setReferenceCounted(false)
        }
        runCatching { lock.acquire() }
            .onSuccess {
                multicastLock = lock
                WearSyncLog.debug(TAG) { "Acquired multicast lock for tablet discovery" }
            }
            .onFailure { error ->
                WearSyncLog.warn(TAG, "Unable to acquire multicast lock for tablet discovery", "Unable to acquire multicast lock for tablet discovery", error)
            }
    }

    private fun connect(endpoint: InetSocketAddress) {
        if (socketReaderJob?.isActive == true && connectedEndpoint == endpoint) return
        connectedEndpoint = endpoint
        socketReaderJob?.cancel()
        socketReaderJob = scope.launch {
            try {
                Socket().use { socket ->
                    socket.connect(endpoint, SOCKET_TIMEOUT_MS)
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    activeSocket = socket
                    markTabletSeen()
                    _discoveryStatus.value = DiscoveryStatus.Connected
                    WearSyncLog.debug(TAG) { "Connected watch to tablet fallback" }
                    BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).use { reader ->
                        while (true) {
                            val line = try {
                                reader.readLine()
                            } catch (_: SocketTimeoutException) {
                                updateConnectionWithGrace()
                                continue
                            } ?: break
                            val state = line.toTabletScoreState() ?: continue
                            markTabletSeen()
                            _tabletScoreState.value = state
                            WearSyncLog.debug(TAG) { "Received tablet fallback state" }
                        }
                    }
                }
            } catch (error: Exception) {
                WearSyncLog.warn(TAG, "Watch-tablet fallback connection lost", "Watch-tablet fallback connection lost", error)
            } finally {
                disconnect()
            }
        }
    }

    private fun disconnect() {
        activeSocket?.let { runCatching { it.close() } }
        activeSocket = null
        if (!_tabletConnected.value) {
            _discoveryStatus.value = DiscoveryStatus.Searching
        }
        updateConnectionWithGrace()
    }

    private fun markTabletSeen() {
        lastTabletSeenAtElapsed = SystemClock.elapsedRealtime()
        _tabletConnected.value = true
    }

    private fun updateConnectionWithGrace() {
        val connected =
            SystemClock.elapsedRealtime() - lastTabletSeenAtElapsed <= CONNECTION_GRACE_MS
        _tabletConnected.value = connected
        if (!connected && activeSocket == null) {
            _discoveryStatus.value = if (_discoveredTablets.value.isEmpty()) {
                DiscoveryStatus.Searching
            } else {
                DiscoveryStatus.Found
            }
        }
    }

    private fun observeTabletCandidate(candidate: TabletCandidate) {
        synchronized(discoveredTabletRecords) {
            discoveredTabletRecords[candidate.courtCode] = TabletCandidateRecord(
                courtCode = candidate.courtCode,
                endpoint = candidate.endpoint,
                lastSeenAtElapsed = SystemClock.elapsedRealtime()
            )
            publishDiscoveredTabletsLocked()
        }
        if (_discoveryStatus.value != DiscoveryStatus.Connected) {
            _discoveryStatus.value = DiscoveryStatus.Found
        }
    }

    private fun pruneStaleDiscoveredTablets() {
        synchronized(discoveredTabletRecords) {
            val now = SystemClock.elapsedRealtime()
            val iterator = discoveredTabletRecords.entries.iterator()
            var changed = false
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.lastSeenAtElapsed > DISCOVERY_STALE_MS) {
                    iterator.remove()
                    changed = true
                }
            }
            if (changed) {
                publishDiscoveredTabletsLocked()
            }
        }
    }

    private fun publishDiscoveredTabletsLocked() {
        _discoveredTablets.value = discoveredTabletRecords.values
            .sortedBy { it.courtCode }
            .map { TabletCandidate(courtCode = it.courtCode, endpoint = it.endpoint) }
    }

    private fun String.toTabletCandidate(packet: DatagramPacket): TabletCandidate? {
        val fields = split("|")
        if (fields.size < 2 || fields[0] != DISCOVERY_PROTOCOL) return null
        val port = fields[1].toIntOrNull() ?: return null
        val courtCode = fields.getOrNull(2)?.fromWireField().orEmpty().ifBlank { "----" }
        return TabletCandidate(
            courtCode = courtCode,
            endpoint = InetSocketAddress(packet.address, port)
        )
    }

    private fun String.toTabletScoreState(): PhoneScoreState? {
        val fields = split("|")
        if (fields.size < 14 || fields[0] != STATE_PROTOCOL) return null
        return PhoneScoreState(
            sourceNodeId = connectedEndpoint?.address?.hostAddress.orEmpty(),
            sourceRole = ConnectedAndroidRole.Tablet,
            teamAScore = fields[5].toIntOrNull() ?: return null,
            teamBScore = fields[6].toIntOrNull() ?: return null,
            servingTeam = when (fields[7]) {
                WearSyncContract.TEAM_B -> Team.B
                else -> Team.A
            },
            serverNumber = fields[8].toIntOrNull() ?: return null,
            teamAName = fields[9].fromWireField(),
            teamBName = fields[10].fromWireField(),
            scoreCall = fields[11].fromWireField(),
            spokenScoreCall = fields[12].fromWireField(),
            voiceAnnouncementMode = VoiceAnnouncementMode.fromWireValue(fields[13]),
            matchActive = fields[2].toBooleanStrictOrNull() ?: false,
            canUndo = fields[3].toBooleanStrictOrNull() ?: false,
            updatedAt = fields[1].toLongOrNull() ?: return null
        )
    }

    private fun String.fromWireField(): String =
        replace("%7C", "|")
            .replace("%25", "%")
}
