package com.courtside.pickleball.sync

import android.content.Context
import android.net.wifi.WifiManager
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.MAX_MATCH_SCORE
import com.courtside.pickleball.domain.ScoringFormat
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import com.courtside.pickleball.player.PlayerRepository
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TabletDisplayState(
    val hostId: String,
    val sessionId: String,
    val myTeamOnTop: Boolean,
    val watchConnected: Boolean,
    val teamAName: String,
    val teamBName: String,
    val teamACourtOrderedName: String,
    val teamBCourtOrderedName: String,
    val teamAPlayer1: String,
    val teamAPlayer2: String,
    val teamBPlayer1: String,
    val teamBPlayer2: String,
    val teamAScore: Int,
    val teamBScore: Int,
    val servingTeam: Team,
    val serverNumber: Int,
    val servingPlayerName: String,
    val scoreCall: String,
    val spokenScoreCall: String,
    val voiceAnnouncementMode: VoiceAnnouncementMode,
    val matchActive: Boolean,
    val canUndo: Boolean,
    val updatedAt: Long
) {
    fun servingName(): String = when (servingTeam) {
        Team.A -> teamAName
        Team.B -> teamBName
    }
}

data class TabletPhoneCandidate(
    val hostId: String,
    val courtCode: String,
    val label: String,
    val matchActive: Boolean,
    val updatedAt: Long
)

enum class TabletConnectionState {
    Searching,
    Reconnecting,
    Connected
}

enum class TabletCommand(val wireValue: String) {
    TeamAWonRally("TABLET_ME_WON_RALLY"),
    TeamBWonRally("TABLET_OPP_WON_RALLY"),
    Undo("TABLET_UNDO"),
    AdjustTeamAScoreDown("TABLET_ADJUST_TEAM_A_SCORE_DOWN"),
    AdjustTeamAScoreUp("TABLET_ADJUST_TEAM_A_SCORE_UP"),
    AdjustTeamBScoreDown("TABLET_ADJUST_TEAM_B_SCORE_DOWN"),
    AdjustTeamBScoreUp("TABLET_ADJUST_TEAM_B_SCORE_UP"),
    SetServingTeamA("TABLET_SET_SERVING_TEAM_A"),
    SetServingTeamB("TABLET_SET_SERVING_TEAM_B"),
    SetServerOne("TABLET_SET_SERVER_ONE"),
    SetServerTwo("TABLET_SET_SERVER_TWO"),
    StartMatch("TABLET_START_MATCH"),
    ResumeMatch("TABLET_RESUME_MATCH"),
    EndMatch("TABLET_END_MATCH");

    companion object {
        fun fromWireValue(value: String): TabletCommand? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class TabletCommandMessage(
    val command: TabletCommand,
    val sessionId: String?,
    val timestamp: String?,
    val mac: String?,
    val args: List<String>
)

data class TabletSetupPayload(
    val teamAName: String,
    val teamBName: String,
    val teamAPlayer1: String,
    val teamAPlayer2: String,
    val teamBPlayer1: String,
    val teamBPlayer2: String,
    val scoringFormat: ScoringFormat,
    val startingTeam: Team?,
    val myTeamOnTop: Boolean
)

object TabletDisplaySync {
    private const val TAG = "TabletDisplaySync"
    private const val PREFS_NAME = "rallyscore_tablet_display_sync"
    private const val KEY_LAST_PHONE_HOST = "last_phone_host"
    private const val KEY_LAST_PHONE_PORT = "last_phone_port"
    private const val KEY_PAIRED_PHONE_HOST_ID = "paired_phone_host_id"
    private const val KEY_PAIRED_PHONE_SESSION_ID = "paired_phone_session_id"
    private const val PORT = 45454
    private const val TABLET_TCP_PORT = 45455
    private const val PHONE_WS_PORT = 45456
    private const val PROTOCOL = "RALLYSCORE_TABLET_V1"
    private const val TABLET_COMMAND_PROTOCOL = "RALLYSCORE_TABLET_COMMAND_V1"
    private const val TABLET_HELLO_PROTOCOL = "RALLYSCORE_TABLET_HELLO_V1"
    private const val PHONE_WS_PROTOCOL = "RALLYSCORE_PHONE_WS_V1"
    private const val TABLET_WELCOME_PROTOCOL = "RALLYSCORE_TABLET_WELCOME_V1"
    private const val MAX_NAME_FIELD_LENGTH = 60
    private const val MAX_WEBSOCKET_CONNECTIONS_PER_MINUTE = 30
    private const val MAX_TCP_PUSHES_PER_MINUTE = 150
    private const val RATE_LIMIT_WINDOW_MS = 60_000L
    private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val BROADCAST_INTERVAL_MS = 1_000L
    // Used instead of BROADCAST_INTERVAL_MS whenever there's no active match, no tablet
    // currently connected, and no tablet has been heard from recently - avoids spinning the UDP
    // broadcaster at 1 Hz forever just because the phone app (or the Wear listener service) is
    // running with nobody around to see it. A freshly opened tablet still discovers the phone
    // within a few seconds, which is fine for a one-time pairing action.
    private const val IDLE_BROADCAST_INTERVAL_MS = 5_000L
    private const val TABLET_HELLO_INTERVAL_MS = 2_000L
    private const val TABLET_SUBNET_SCAN_INTERVAL_MS = 15_000L
    private const val TABLET_SUBNET_SCAN_TIMEOUT_MS = 80
    private const val STALE_REMOTE_STATE_MS = 5_000L
    private const val STALE_DISCOVERED_PHONE_MS = 10_000L
    // Must comfortably exceed IDLE_BROADCAST_INTERVAL_MS: this is the tablet client's read
    // timeout while connected, and the phone now sometimes waits a full idle interval between
    // snapshots. A timeout too close to (or shorter than) that interval makes a perfectly healthy
    // idle connection look dead - the tablet would tear it down and reconnect every cycle.
    private const val WEBSOCKET_READ_TIMEOUT_MS = 8_000
    private const val STALE_TABLET_ADDRESS_MS = 30_000L
    private const val LISTEN_TIMEOUT_MS = 1_000
    private const val MAX_WS_RECONNECT_ATTEMPTS = 5
    private const val MAX_PACKET_BYTES = 2_048

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _remoteDisplayState = MutableStateFlow<TabletDisplayState?>(null)
    val remoteDisplayState: StateFlow<TabletDisplayState?> = _remoteDisplayState.asStateFlow()
    private val _hostConnectionState = MutableStateFlow(TabletConnectionState.Searching)
    val hostConnectionState: StateFlow<TabletConnectionState> = _hostConnectionState.asStateFlow()
    private val _clientConnectionState = MutableStateFlow(TabletConnectionState.Searching)
    val clientConnectionState: StateFlow<TabletConnectionState> = _clientConnectionState.asStateFlow()
    private val _discoveredPhones = MutableStateFlow<List<TabletPhoneCandidate>>(emptyList())
    val discoveredPhones: StateFlow<List<TabletPhoneCandidate>> = _discoveredPhones.asStateFlow()
    private val _pairedPhoneHost = MutableStateFlow<String?>(null)
    val pairedPhoneHost: StateFlow<String?> = _pairedPhoneHost.asStateFlow()
    @Volatile private var myTeamOnTopForSync: Boolean = true
    @Volatile private var pendingTabletSetupCommand: Pair<TabletCommand, TabletSetupPayload>? = null

    private var appContext: Context? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listenerJob: Job? = null
    private var broadcasterJob: Job? = null
    private var phoneWebSocketServerJob: Job? = null
    private var tabletHelloJob: Job? = null
    private var tabletTcpServerJob: Job? = null
    private var tabletWebSocketClientJob: Job? = null
    private var tabletSubnetScanJob: Job? = null
    @Volatile private var tabletDisplayAvailable = false
    @Volatile private var phoneWebSocketConnected = false
    @Volatile private var connectedPhoneWebSocketEndpoint: InetSocketAddress? = null
    @Volatile private var connectedPhoneWebSocket: Socket? = null
    @Volatile private var tabletCommandHandler: ((TabletCommandMessage) -> Unit)? = null
    @Volatile private var phoneHostIdProvider: (() -> String)? = null
    @Volatile private var phoneSessionIdProvider: (() -> String)? = null
    @Volatile private var latestPhonePayload: String? = null
    @Volatile private var lastRemoteSnapshotReceivedAt: Long = 0L
    @Volatile private var lastLearnedPlayerNamesKey: String? = null
    @Volatile private var pairedPhoneHostId: String? = null
    @Volatile private var pairedPhoneSessionId: String? = null
    private val webSocketClients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val tabletEndpoints = ConcurrentHashMap<InetSocketAddress, Long>()
    private val tabletTcpEndpoints = ConcurrentHashMap<InetSocketAddress, Long>()
    private val discoveredPhonesByHostId = ConcurrentHashMap<String, DiscoveredPhone>()
    private val connectionSecrets = ConcurrentHashMap<Socket, String>()
    // While the connection is stable this only sees one connection per pairing session, but a
    // disconnected tablet retries through several independent, concurrent loops (hello
    // broadcaster, subnet scanner, gateway probe, the reconnect loop's own internal retries) -
    // during real connection trouble that can legitimately exceed 10 attempts/minute, which
    // caused a self-inflicted lockout (rate-limited reconnect attempts prevented recovery from
    // the very instability the retries were trying to fix). 30/min still makes brute-forcing the
    // 4-char court code take hours instead of being instant.
    private val webSocketConnectionRateLimiter = RateLimiter(MAX_WEBSOCKET_CONNECTIONS_PER_MINUTE, RATE_LIMIT_WINDOW_MS)

    // The paired phone opens a brand-new short-lived TCP connection for every broadcast tick
    // (once per BROADCAST_INTERVAL_MS), so this needs enough headroom over that legitimate rate.
    private val tcpConnectionRateLimiter = RateLimiter(MAX_TCP_PUSHES_PER_MINUTE, RATE_LIMIT_WINDOW_MS)
    @Volatile private var activeConnectionSecret: String? = null

    private data class DiscoveredPhone(
        val hostId: String,
        val sessionId: String,
        val state: TabletDisplayState,
        val address: InetAddress,
        val port: Int,
        val lastSeenAt: Long
    )

    private data class LocalIpv4Network(
        val address: Inet4Address,
        val prefixLength: Int
    )

    fun initialize(context: Context) {
        appContext = context.applicationContext
        pairedPhoneHostId = rememberedPairedPhoneHostId()
        _pairedPhoneHost.value = pairedPhoneHostId
        pairedPhoneSessionId = rememberedPairedPhoneSessionId()
    }

    fun startListener() {
        if (listenerJob?.isActive == true) return
        listenerJob = scope.launch {
            runListener()
        }
    }

    fun startBroadcaster(
        stateProvider: () -> GameState,
        matchActiveProvider: () -> Boolean,
        canUndoProvider: () -> Boolean = { false },
        watchConnectedProvider: () -> Boolean = { false },
        voiceModeProvider: () -> VoiceAnnouncementMode = { VoiceAnnouncementMode.PhoneOnly },
        hostIdProvider: () -> String,
        sessionIdProvider: () -> String,
        onTabletCommand: (TabletCommandMessage) -> Unit = {}
    ) {
        tabletCommandHandler = onTabletCommand
        phoneHostIdProvider = hostIdProvider
        phoneSessionIdProvider = sessionIdProvider
        if (broadcasterJob?.isActive == true) return
        startPhoneWebSocketServer()
        broadcasterJob = scope.launch {
            runBroadcaster(
                stateProvider = stateProvider,
                matchActiveProvider = matchActiveProvider,
                canUndoProvider = canUndoProvider,
                watchConnectedProvider = watchConnectedProvider,
                voiceModeProvider = voiceModeProvider,
                hostIdProvider = hostIdProvider,
                sessionIdProvider = sessionIdProvider
            )
        }
    }

    fun sendTabletCommand(command: TabletCommand) {
        val socket = connectedPhoneWebSocket
        val sessionId = pairedPhoneSessionId ?: _remoteDisplayState.value?.sessionId
        if (!tabletDisplayAvailable || !phoneWebSocketConnected || socket == null) {
            SyncLog.warn(
                TAG,
                "Unable to send tablet command while phone connection is unavailable",
                "Unable to send tablet command while phone WebSocket is disconnected: ${command.wireValue}"
            )
            if (tabletDisplayAvailable) {
                connectToRememberedPhoneWebSocket()
                connectToGatewayPhoneWebSocket()
            }
            setClientConnectionState(
                if (_remoteDisplayState.value == null) {
                    TabletConnectionState.Searching
                } else {
                    TabletConnectionState.Reconnecting
                }
            )
            return
        }
        if (sessionId.isNullOrBlank()) {
            SyncLog.warn(
                TAG,
                "Unable to send tablet command without an active paired session",
                "Unable to send tablet command without a paired phone session: ${command.wireValue}"
            )
            return
        }

        scope.launch {
            try {
                socket.writeWebSocketTextFrame(command.toWirePayload(sessionId, activeConnectionSecret))
                SyncLog.debug(TAG) { "Sent tablet command: ${command.wireValue}" }
            } catch (error: Exception) {
                SyncLog.warn(TAG, "Failed to send tablet command", "Failed to send tablet command: ${command.wireValue}", error)
                phoneWebSocketConnected = false
                connectedPhoneWebSocket = null
                setClientConnectionState(TabletConnectionState.Reconnecting)
            }
        }
    }

    fun sendTabletSetupCommand(
        hostId: String?,
        command: TabletCommand,
        payload: TabletSetupPayload
    ): Boolean {
        if (command != TabletCommand.StartMatch && command != TabletCommand.ResumeMatch) return false
        if (hostId != null && pairedPhoneHostId != hostId) {
            pairToDiscoveredPhone(hostId)
        }
        pendingTabletSetupCommand = command to payload
        flushPendingTabletCommand()
        if (!phoneWebSocketConnected) {
            connectToPairedDiscoveredPhoneWebSocket()
            connectToRememberedPhoneWebSocket()
            connectToGatewayPhoneWebSocket()
        }
        return true
    }

    fun setTabletDisplayAvailable(available: Boolean) {
        tabletDisplayAvailable = available
        if (available) {
            stopLocalHubPublisher()
            setClientConnectionState(TabletConnectionState.Searching)
            startListener()
            connectToRememberedPhoneWebSocket()
            connectToGatewayPhoneWebSocket()
            startTabletHelloBroadcaster()
            startTabletTcpServer()
            startTabletSubnetScanner()
        } else {
            tabletHelloJob?.cancel()
            tabletHelloJob = null
            tabletTcpServerJob?.cancel()
            tabletTcpServerJob = null
            tabletWebSocketClientJob?.cancel()
            tabletWebSocketClientJob = null
            tabletSubnetScanJob?.cancel()
            tabletSubnetScanJob = null
            phoneWebSocketConnected = false
            connectedPhoneWebSocket = null
            connectedPhoneWebSocketEndpoint = null
            setHostConnectionState(TabletConnectionState.Searching)
        }
    }

    fun forgetPairedPhone() {
        pairedPhoneHostId = null
        _pairedPhoneHost.value = null
        pairedPhoneSessionId = null
        connectedPhoneWebSocket?.closeQuietly()
        connectedPhoneWebSocket = null
        connectedPhoneWebSocketEndpoint = null
        phoneWebSocketConnected = false
        _remoteDisplayState.value = null
        lastRemoteSnapshotReceivedAt = 0L
        _discoveredPhones.value = emptyList()
        discoveredPhonesByHostId.clear()

        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(KEY_PAIRED_PHONE_HOST_ID)
            ?.remove(KEY_PAIRED_PHONE_SESSION_ID)
            ?.remove(KEY_LAST_PHONE_HOST)
            ?.remove(KEY_LAST_PHONE_PORT)
            ?.apply()

        setHostConnectionState(TabletConnectionState.Searching)
        SyncLog.debug(TAG) { "Forgot paired phone identity and reset tablet discovery" }

        if (tabletDisplayAvailable) {
            startTabletHelloBroadcaster()
            startTabletTcpServer()
            startTabletSubnetScanner()
        }
    }

    fun setMyTeamOnTop(myTeamOnTop: Boolean) {
        myTeamOnTopForSync = myTeamOnTop
    }

    private fun flushPendingTabletCommand() {
        val (command, setupPayload) = pendingTabletSetupCommand ?: return
        val socket = connectedPhoneWebSocket
        if (!tabletDisplayAvailable || !phoneWebSocketConnected || socket == null) {
            return
        }

        scope.launch {
            try {
                // Built at flush time (not enqueue time) so the payload is signed with the
                // live connection's secret - the phone rejects unsigned Start/Resume commands.
                val sessionId = pairedPhoneSessionId ?: _remoteDisplayState.value?.sessionId.orEmpty()
                socket.writeWebSocketTextFrame(command.toWirePayload(sessionId, setupPayload, activeConnectionSecret))
                pendingTabletSetupCommand = null
                SyncLog.debug(TAG) { "Sent pending tablet setup command" }
            } catch (error: Exception) {
                SyncLog.warn(TAG, "Failed to send pending tablet setup command", "Failed to send pending tablet setup command", error)
                phoneWebSocketConnected = false
                connectedPhoneWebSocket = null
                setClientConnectionState(TabletConnectionState.Reconnecting)
            }
        }
    }

    fun pairToDiscoveredPhone(hostId: String): Boolean {
        val candidate = discoveredPhonesByHostId[hostId] ?: return false
        rememberPairedPhoneIdentity(candidate.hostId, candidate.sessionId)
        rememberPhoneEndpoint(candidate.address, candidate.port)
        updateRemoteDisplayState(candidate.state, "manual-pair")
        // A tap here is a deliberate user request to retry right now, so it must not be
        // swallowed by connectToPhoneWebSocket's dedup guard (which exists to stop the
        // automatic hello broadcaster/subnet scanner/gateway probe from racing each other on
        // the same endpoint). Tear down any stuck reconnect job first so the tap always
        // launches a genuinely fresh attempt instead of silently no-opping while the old job
        // sits in its retry backoff.
        tabletWebSocketClientJob?.cancel()
        tabletWebSocketClientJob = null
        connectedPhoneWebSocket?.let { runCatching { it.close() } }
        connectedPhoneWebSocket = null
        connectedPhoneWebSocketEndpoint = null
        phoneWebSocketConnected = false
        connectToPhoneWebSocket(candidate.address, candidate.port)
        SyncLog.debug(TAG) { "Manually paired tablet to discovered phone host" }
        return true
    }

    private fun stopLocalHubPublisher() {
        broadcasterJob?.cancel()
        broadcasterJob = null
        phoneWebSocketServerJob?.cancel()
        phoneWebSocketServerJob = null
        latestPhonePayload = null
        closeWebSocketClients()
    }

    private suspend fun runListener() {
        try {
            acquireMulticastLock()
            DatagramSocket(PORT).use { socket ->
                socket.broadcast = true
                socket.soTimeout = LISTEN_TIMEOUT_MS
                val buffer = ByteArray(MAX_PACKET_BYTES)
                SyncLog.debug(TAG) { "Tablet display listener started" }
                while (currentCoroutineContext().isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        markStaleRemoteState()
                        continue
                    }

                    val payload = String(
                        packet.data,
                        packet.offset,
                        packet.length,
                        StandardCharsets.UTF_8
                    )
                    if (payload.startsWith(TABLET_HELLO_PROTOCOL)) {
                        rememberTabletEndpoint(packet.address, packet.port, payload.toTabletTcpPort())
                        continue
                    }
                    if (payload.startsWith(PHONE_WS_PROTOCOL)) {
                        if (tabletDisplayAvailable && !pairedPhoneHostId.isNullOrBlank()) {
                            SyncLog.debug(TAG) { "UDP discovered phone WebSocket availability" }
                            connectToPhoneWebSocket(packet.address, payload.toPhoneWebSocketPort())
                        }
                        continue
                    }

                    val state = payload.toTabletDisplayState() ?: continue
                    registerDiscoveredPhone(state, packet.address, PHONE_WS_PORT)
                    updateRemoteDisplayState(state, "UDP")
                }
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Tablet display listener stopped", "Tablet display listener stopped", error)
        } finally {
            releaseMulticastLock()
        }
    }

    private suspend fun runBroadcaster(
        stateProvider: () -> GameState,
        matchActiveProvider: () -> Boolean,
        canUndoProvider: () -> Boolean,
        watchConnectedProvider: () -> Boolean,
        voiceModeProvider: () -> VoiceAnnouncementMode,
        hostIdProvider: () -> String,
        sessionIdProvider: () -> String
    ) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (currentCoroutineContext().isActive) {
                    val state = stateProvider()
                    val matchActive = matchActiveProvider()
                    val payload = state.toTabletDisplayPayload(
                        matchActive = matchActive,
                        canUndo = canUndoProvider(),
                        watchConnected = watchConnectedProvider(),
                        voiceAnnouncementMode = voiceModeProvider(),
                        hostId = hostIdProvider(),
                        sessionId = sessionIdProvider()
                    )
                    latestPhonePayload = payload
                    val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                    broadcastPhoneWebSocketAvailability(socket)
                    val recentTabletEndpoints = currentTabletEndpoints()
                    val targets = broadcastEndpoints() + recentTabletEndpoints
                    targets.forEach { endpoint ->
                        val packet = DatagramPacket(bytes, bytes.size, endpoint.address, endpoint.port)
                        socket.send(packet)
                    }
                    publishToTabletTcpEndpoints(payload)
                    publishToWebSocketClients(payload)
                    SyncLog.debug(TAG) { "Published tablet score snapshot to ${targets.size} targets" }
                    val idle = !matchActive &&
                        webSocketClients.isEmpty() &&
                        recentTabletEndpoints.isEmpty() &&
                        currentTabletTcpEndpoints().isEmpty()
                    delay(if (idle) IDLE_BROADCAST_INTERVAL_MS else BROADCAST_INTERVAL_MS)
                }
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Tablet display broadcaster stopped", "Tablet display broadcaster stopped", error)
        }
    }

    private fun startPhoneWebSocketServer() {
        if (phoneWebSocketServerJob?.isActive == true) return
        phoneWebSocketServerJob = scope.launch {
            runPhoneWebSocketServer()
        }
    }

    private fun startTabletHelloBroadcaster() {
        if (tabletHelloJob?.isActive == true) return
        tabletHelloJob = scope.launch {
            runTabletHelloBroadcaster()
        }
    }

    private fun connectToGatewayPhoneWebSocket() {
        if (phoneWebSocketConnected) return
        if (pairedPhoneHostId != null && pairedDiscoveredPhoneEndpoint() != null) {
            SyncLog.debug(TAG) { "Skipping generic gateway probe because paired endpoint has been rediscovered" }
            return
        }
        val gateway = gatewayAddresses().firstOrNull()
        if (gateway == null) {
            SyncLog.debug(TAG) { "No gateway address available for phone discovery" }
            return
        }
        SyncLog.debug(TAG) { "Probing gateway for phone tablet WebSocket" }
        connectToPhoneWebSocket(gateway, PHONE_WS_PORT)
    }

    private fun connectToRememberedPhoneWebSocket() {
        if (phoneWebSocketConnected) return
        val endpoint = rememberedPhoneEndpoint()
        if (endpoint == null) {
            SyncLog.debug(TAG) { "No remembered phone endpoint available for reconnection" }
            return
        }
        SyncLog.debug(TAG) { "Attempting reconnection to remembered phone endpoint" }
        if (!hasFreshRemoteSnapshot()) {
            setClientConnectionState(TabletConnectionState.Reconnecting)
        }
        connectToPhoneWebSocket(endpoint.address, endpoint.port)
    }

    private fun startTabletTcpServer() {
        if (tabletTcpServerJob?.isActive == true) return
        tabletTcpServerJob = scope.launch {
            runTabletTcpServer()
        }
    }

    private fun startTabletSubnetScanner() {
        if (tabletSubnetScanJob?.isActive == true) return
        tabletSubnetScanJob = scope.launch {
            runTabletSubnetScanner()
        }
    }

    private suspend fun runPhoneWebSocketServer() {
        try {
            ServerSocket(PHONE_WS_PORT).use { serverSocket ->
                serverSocket.soTimeout = LISTEN_TIMEOUT_MS
                SyncLog.debug(TAG) { "Phone tablet WebSocket server started" }
                while (currentCoroutineContext().isActive) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (_: SocketTimeoutException) {
                        continue
                    }

                    val remoteKey = socket.inetAddress?.hostAddress ?: "unknown"
                    if (!webSocketConnectionRateLimiter.allow(remoteKey)) {
                        SyncLog.warn(
                            TAG,
                            "Rate limited a WebSocket connection attempt",
                            "Rate limited WebSocket connection attempt from $remoteKey"
                        )
                        socket.closeQuietly()
                        continue
                    }

                    scope.launch {
                        acceptWebSocketClient(socket)
                    }
                }
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Phone tablet WebSocket server stopped", "Phone tablet WebSocket server stopped", error)
        } finally {
            closeWebSocketClients()
        }
    }

    private suspend fun acceptWebSocketClient(socket: Socket) {
        try {
            socket.soTimeout = LISTEN_TIMEOUT_MS
            val key = readWebSocketHandshakeKey(socket) ?: return socket.close()
            val response = buildWebSocketHandshakeResponse(key)
            socket.getOutputStream().write(response.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
            socket.soTimeout = 0
            webSocketClients += socket
            val connectionSecret = MessageAuthenticator.newSecret()
            connectionSecrets[socket] = connectionSecret
            socket.writeWebSocketTextFrame("$TABLET_WELCOME_PROTOCOL|$connectionSecret")
            setHostConnectionState(TabletConnectionState.Connected)
            latestPhonePayload?.let { payload ->
                socket.writeWebSocketTextFrame(payload)
            }
            SyncLog.debug(TAG) { "Tablet WebSocket client connected" }
            while (currentCoroutineContext().isActive) {
                val payload = readWebSocketTextFrame(socket) ?: break
                val commandMessage = payload.toTabletCommandMessage() ?: continue
                val expectedSessionId = phoneSessionIdProvider?.invoke()
                // Start/Resume are exempt from the session check (a fresh tablet has no session
                // yet) but never from the HMAC: without this, any unauthenticated LAN client
                // could reset a live match by sending StartMatch.
                val requiresActiveSession = commandMessage.command != TabletCommand.StartMatch &&
                    commandMessage.command != TabletCommand.ResumeMatch
                val sessionMatches = !requiresActiveSession ||
                    (!expectedSessionId.isNullOrBlank() && commandMessage.sessionId == expectedSessionId)
                val authenticated = commandMessage.verify(connectionSecret)
                if (!sessionMatches || !authenticated) {
                    SyncLog.warn(
                        TAG,
                        "Ignored unauthenticated or mismatched tablet command",
                        "Ignored tablet command: ${commandMessage.command.wireValue} " +
                            "sessionMatches=$sessionMatches authenticated=$authenticated"
                    )
                    continue
                }
                SyncLog.debug(TAG) { "Received tablet command: ${commandMessage.command.wireValue}" }
                tabletCommandHandler?.invoke(commandMessage)
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Unable to accept tablet WebSocket client", "Unable to accept tablet WebSocket client: ${error.message}", error)
        } finally {
            webSocketClients -= socket
            connectionSecrets.remove(socket)
            if (webSocketClients.isEmpty()) {
                setHostConnectionState(TabletConnectionState.Reconnecting)
            }
            socket.closeQuietly()
        }
    }

    private fun readWebSocketHandshakeKey(socket: Socket): String? {
        val input = socket.getInputStream()
        var key: String? = null
        while (true) {
            val line = input.readHttpLine() ?: return null
            if (line.isEmpty()) break
            if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                key = line.substringAfter(":").trim()
            }
        }
        return key
    }

    private fun buildWebSocketHandshakeResponse(key: String): String {
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + WEBSOCKET_GUID).toByteArray(StandardCharsets.US_ASCII))
        )
        return "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: $accept\r\n\r\n"
    }

    private fun broadcastPhoneWebSocketAvailability(socket: DatagramSocket) {
        val payload = "$PHONE_WS_PROTOCOL|$PHONE_WS_PORT"
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        broadcastEndpoints().forEach { endpoint ->
            val packet = DatagramPacket(bytes, bytes.size, endpoint.address, endpoint.port)
            socket.send(packet)
        }
    }

    private fun connectToPhoneWebSocket(address: InetAddress, port: Int?) {
        if (pairedPhoneHostId.isNullOrBlank()) {
            SyncLog.debug(TAG) { "Skipping phone WebSocket probe until a phone has been explicitly paired" }
            return
        }
        val endpoint = InetSocketAddress(address, port ?: PHONE_WS_PORT)
        if (!shouldProbePhoneEndpoint(endpoint)) {
            SyncLog.debug(TAG) { "Skipping phone WebSocket probe; not a preferred endpoint for paired court" }
            return
        }
        if (phoneWebSocketConnected) {
            SyncLog.debug(TAG) { "Skipping phone WebSocket probe; phone socket already connected" }
            return
        }
        if (connectedPhoneWebSocketEndpoint == endpoint && tabletWebSocketClientJob?.isActive == true) {
            SyncLog.debug(TAG) { "Already connecting to phone WebSocket; skipping duplicate attempt" }
            return
        }

        SyncLog.debug(TAG) { "Initiating phone WebSocket connection" }
        connectedPhoneWebSocketEndpoint = endpoint
        if (!phoneWebSocketConnected && !hasFreshRemoteSnapshot()) {
            setClientConnectionState(
                if (_remoteDisplayState.value == null) {
                    TabletConnectionState.Searching
                } else {
                    TabletConnectionState.Reconnecting
                }
            )
        }
        tabletWebSocketClientJob?.cancel()
        tabletWebSocketClientJob = scope.launch {
            runTabletWebSocketClient(endpoint)
        }
    }

    private suspend fun runTabletWebSocketClient(endpoint: InetSocketAddress) {
        var consecutiveFailures = 0
        while (currentCoroutineContext().isActive && tabletDisplayAvailable) {
            var activeSocket: Socket? = null
            var completedNormally = false
            try {
                Socket().use { socket ->
                    activeSocket = socket
                    socket.connect(endpoint, LISTEN_TIMEOUT_MS)
                    socket.soTimeout = LISTEN_TIMEOUT_MS
                    writeWebSocketClientHandshake(socket, endpoint.hostString)
                    if (!readWebSocketClientHandshake(socket)) {
                        throw IllegalStateException("Invalid WebSocket handshake")
                    }
                    socket.soTimeout = WEBSOCKET_READ_TIMEOUT_MS
                    activeConnectionSecret = runCatching { readWebSocketTextFrame(socket)?.toWelcomeSecret() }
                        .getOrNull()
                    phoneWebSocketConnected = true
                    connectedPhoneWebSocket = socket
                    consecutiveFailures = 0
                    rememberPhoneEndpoint(endpoint.address, endpoint.port)
                    setClientConnectionState(TabletConnectionState.Connected)
                    SyncLog.debug(TAG) { "Connected to phone tablet WebSocket" }
                    flushPendingTabletCommand()
                    while (currentCoroutineContext().isActive && tabletDisplayAvailable) {
                        val payload = try {
                            readWebSocketTextFrame(socket)
                        } catch (_: SocketTimeoutException) {
                            SyncLog.warn(
                                TAG,
                                "Phone tablet WebSocket heartbeat timed out",
                                "Phone tablet WebSocket heartbeat timed out at ${endpoint.address.hostAddress}:${endpoint.port}"
                            )
                            break
                        } ?: break
                        val state = payload.toTabletDisplayState() ?: continue
                        val pairedHost = pairedPhoneHostId
                        if (!pairedHost.isNullOrBlank() && state.hostId != pairedHost) {
                            SyncLog.warn(
                                TAG,
                                "Closing phone WebSocket because paired host did not match",
                                "Closing phone WebSocket at ${endpoint.address.hostAddress}:${endpoint.port}; paired host is $pairedHost but stream reported ${state.hostId}"
                            )
                            phoneWebSocketConnected = false
                            connectedPhoneWebSocket = null
                            setClientConnectionState(TabletConnectionState.Reconnecting)
                            break
                        }
                        registerDiscoveredPhone(state, endpoint.address, endpoint.port)
                        updateRemoteDisplayState(state, "WebSocket")
                    }
                    completedNormally = true
                }
            } catch (error: Exception) {
                consecutiveFailures++
                SyncLog.warn(
                    TAG,
                    "Phone tablet WebSocket unavailable",
                    "Phone tablet WebSocket unavailable at ${endpoint.address.hostAddress}:${endpoint.port}" +
                        " (attempt $consecutiveFailures/$MAX_WS_RECONNECT_ATTEMPTS): ${error.message}",
                    error
                )
                markStaleRemoteState()
                if (consecutiveFailures >= MAX_WS_RECONNECT_ATTEMPTS) {
                    SyncLog.warn(TAG, "Exceeded max WebSocket reconnect attempts; yielding to discovery")
                    break
                }
                delay(TABLET_HELLO_INTERVAL_MS)
            } finally {
                phoneWebSocketConnected = false
                activeConnectionSecret = null
                if (connectedPhoneWebSocket == activeSocket) {
                    connectedPhoneWebSocket = null
                }
                if (!completedNormally && consecutiveFailures < MAX_WS_RECONNECT_ATTEMPTS) {
                    markStaleRemoteState()
                }
            }
        }
    }

    private suspend fun runTabletSubnetScanner() {
        while (currentCoroutineContext().isActive && tabletDisplayAvailable) {
            if (!phoneWebSocketConnected) {
                SyncLog.debug(TAG) { "Starting tablet subnet discovery scan" }
                connectToRememberedPhoneWebSocket()
                scanLocalSubnetForPhoneWebSocket()
            }
            delay(TABLET_SUBNET_SCAN_INTERVAL_MS)
        }
    }

    private fun scanLocalSubnetForPhoneWebSocket() {
        val gateways = gatewayAddresses().toSet()
        gateways.forEach { gateway ->
            if (phoneWebSocketConnected || !tabletDisplayAvailable) return
            if (canOpenPhoneWebSocket(gateway)) {
                SyncLog.debug(TAG) { "Discovered phone tablet WebSocket at gateway" }
                connectToPhoneWebSocket(gateway, PHONE_WS_PORT)
                return
            }
        }

        localIpv4Networks().forEach { network ->
            if (phoneWebSocketConnected || !tabletDisplayAvailable) return
            scanLocalIpv4SubnetForPhoneWebSocket(network, gateways)
        }
    }

    private fun scanLocalIpv4SubnetForPhoneWebSocket(
        network: LocalIpv4Network,
        gateways: Set<InetAddress>
    ) {
        val addressBytes = network.address.address
        if (addressBytes.size != 4) return

        val prefix = byteArrayOf(addressBytes[0], addressBytes[1], addressBytes[2])
        for (host in 1..254) {
            if (phoneWebSocketConnected || !tabletDisplayAvailable) return

            val candidate = InetAddress.getByAddress(prefix + host.toByte())
            if (candidate == network.address || candidate in gateways) continue
            if (canOpenPhoneWebSocket(candidate)) {
                SyncLog.debug(TAG) { "Discovered phone tablet WebSocket by subnet scan" }
                connectToPhoneWebSocket(candidate, PHONE_WS_PORT)
                return
            }
        }
    }

    private fun canOpenPhoneWebSocket(address: InetAddress): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, PHONE_WS_PORT), TABLET_SUBNET_SCAN_TIMEOUT_MS)
            }
            true
        } catch (_: Exception) {
            false
        }

    private fun writeWebSocketClientHandshake(socket: Socket, host: String) {
        val key = Base64.getEncoder().encodeToString("RallyScoreTablet".toByteArray(StandardCharsets.US_ASCII))
        val request = "GET /rallyscore-tablet HTTP/1.1\r\n" +
            "Host: $host:$PHONE_WS_PORT\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\n" +
            "Sec-WebSocket-Version: 13\r\n\r\n"
        socket.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
        socket.getOutputStream().flush()
    }

    private fun readWebSocketClientHandshake(socket: Socket): Boolean {
        val input = socket.getInputStream()
        val status = input.readHttpLine() ?: return false
        while (input.readHttpLine()?.isNotEmpty() == true) {
            // Drain headers.
        }
        return status.contains("101")
    }

    private suspend fun runTabletHelloBroadcaster() {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 500
                val helloPayload = "$TABLET_HELLO_PROTOCOL|$TABLET_TCP_PORT"
                val bytes = helloPayload.toByteArray(StandardCharsets.UTF_8)
                while (currentCoroutineContext().isActive) {
                    connectToGatewayPhoneWebSocket()
                    broadcastEndpoints().forEach { endpoint ->
                        val packet = DatagramPacket(bytes, bytes.size, endpoint.address, endpoint.port)
                        socket.send(packet)
                    }
                    SyncLog.debug(TAG) { "Broadcast tablet display hello" }
                    receiveScoreSnapshotsOnHelloSocket(socket, TABLET_HELLO_INTERVAL_MS)
                }
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Tablet display hello broadcaster stopped", "Tablet display hello broadcaster stopped", error)
        }
    }

    private suspend fun runTabletTcpServer() {
        try {
            ServerSocket(TABLET_TCP_PORT).use { serverSocket ->
                serverSocket.soTimeout = LISTEN_TIMEOUT_MS
                SyncLog.debug(TAG) { "Tablet display TCP server started" }
                while (currentCoroutineContext().isActive) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (_: SocketTimeoutException) {
                        markStaleRemoteState()
                        continue
                    }

                    val remoteKey = socket.inetAddress?.hostAddress ?: "unknown"
                    if (!tcpConnectionRateLimiter.allow(remoteKey)) {
                        SyncLog.warn(
                            TAG,
                            "Rate limited a tablet TCP connection attempt",
                            "Rate limited tablet TCP connection attempt from $remoteKey"
                        )
                        socket.closeQuietly()
                        continue
                    }

                    socket.use {
                        it.soTimeout = LISTEN_TIMEOUT_MS
                        val payload = it.getInputStream()
                            .bufferedReader(StandardCharsets.UTF_8)
                            .readLine()
                            ?: return@use
                        val state = payload.toTabletDisplayState() ?: return@use
                        registerDiscoveredPhone(state, socket.inetAddress, PHONE_WS_PORT)
                        updateRemoteDisplayState(state, "TCP")
                        SyncLog.debug(TAG) { "Received tablet score snapshot over TCP" }
                    }
                }
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Tablet display TCP server stopped", "Tablet display TCP server stopped", error)
        }
    }

    private suspend fun receiveScoreSnapshotsOnHelloSocket(
        socket: DatagramSocket,
        listenWindowMs: Long
    ) {
        val stopAt = System.currentTimeMillis() + listenWindowMs
        val buffer = ByteArray(MAX_PACKET_BYTES)
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < stopAt) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                markStaleRemoteState()
                continue
            }

            val payload = String(
                packet.data,
                packet.offset,
                packet.length,
                StandardCharsets.UTF_8
            )
            val state = payload.toTabletDisplayState() ?: continue
            registerDiscoveredPhone(state, socket.inetAddress, PHONE_WS_PORT)
            updateRemoteDisplayState(state, "hello socket")
        }
    }

    private fun rememberTabletEndpoint(address: InetAddress, udpPort: Int, tcpPort: Int?) {
        val endpoint = InetSocketAddress(address, udpPort)
        val previous = tabletEndpoints.put(endpoint, System.currentTimeMillis())
        if (previous == null) {
            SyncLog.debug(TAG) { "Discovered tablet display UDP endpoint" }
        }

        if (tcpPort != null) {
            val tcpEndpoint = InetSocketAddress(address, tcpPort)
            val previousTcp = tabletTcpEndpoints.put(tcpEndpoint, System.currentTimeMillis())
            if (previousTcp == null) {
                SyncLog.debug(TAG) { "Discovered tablet display TCP endpoint" }
            }
        }
    }

    private fun currentTabletEndpoints(): List<InetSocketAddress> {
        val now = System.currentTimeMillis()
        tabletEndpoints.entries.removeIf { (_, lastSeen) ->
            now - lastSeen > STALE_TABLET_ADDRESS_MS
        }
        return tabletEndpoints.keys().toList()
    }

    private fun currentTabletTcpEndpoints(): List<InetSocketAddress> {
        val now = System.currentTimeMillis()
        tabletTcpEndpoints.entries.removeIf { (_, lastSeen) ->
            now - lastSeen > STALE_TABLET_ADDRESS_MS
        }
        return tabletTcpEndpoints.keys().toList()
    }

    private fun publishToTabletTcpEndpoints(payload: String) {
        val endpoints = currentTabletTcpEndpoints()
        var deliveredToAny = false
        endpoints.forEach { endpoint ->
            try {
                java.net.Socket().use { socket ->
                    socket.connect(endpoint, LISTEN_TIMEOUT_MS)
                    socket.getOutputStream().write("$payload\n".toByteArray(StandardCharsets.UTF_8))
                    socket.getOutputStream().flush()
                }
                deliveredToAny = true
                SyncLog.debug(TAG) { "Published tablet score snapshot over TCP" }
            } catch (error: Exception) {
                tabletTcpEndpoints.remove(endpoint)
                SyncLog.warn(
                    TAG,
                    "Removed unreachable tablet TCP endpoint",
                    "Removed unreachable tablet TCP endpoint ${endpoint.address.hostAddress}:${endpoint.port}: ${error.message}",
                    error
                )
            }
        }
        // The phone's own "tablet connected" status previously only reflected the WebSocket
        // accept path, so a pairing that only ever worked over this TCP push fallback left the
        // phone's status pill stuck on "Searching"/"Reconnecting" even while delivery was
        // actually succeeding (the tablet's own status pill only checks "did I get a fresh
        // snapshot", regardless of transport, so it showed Connected while the phone didn't).
        if (deliveredToAny) {
            setHostConnectionState(TabletConnectionState.Connected)
        } else if (webSocketClients.isEmpty() && endpoints.isNotEmpty()) {
            setHostConnectionState(TabletConnectionState.Reconnecting)
        }
    }

    private fun publishToWebSocketClients(payload: String) {
        val staleClients = mutableListOf<Socket>()
        val clients = synchronized(webSocketClients) {
            webSocketClients.toList()
        }
        clients.forEach { socket ->
            try {
                socket.writeWebSocketTextFrame(payload)
            } catch (_: Exception) {
                staleClients += socket
            }
        }

        staleClients.forEach { socket ->
            webSocketClients -= socket
            socket.closeQuietly()
        }
    }

    private fun Socket.writeWebSocketTextFrame(payload: String) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        synchronized(this) {
            val output = getOutputStream()
            output.write(0x81)
            when {
                bytes.size <= 125 -> output.write(bytes.size)
                bytes.size <= 65_535 -> {
                    output.write(126)
                    output.write((bytes.size shr 8) and 0xFF)
                    output.write(bytes.size and 0xFF)
                }
                else -> error("Tablet display payload too large")
            }
            output.write(bytes)
            output.flush()
        }
    }

    private fun readWebSocketTextFrame(socket: Socket): String? {
        val input = socket.getInputStream()
        val first = input.read()
        if (first == -1) return null
        val opcode = first and 0x0F
        if (opcode == 0x8) return null
        if (opcode != 0x1) return null

        val second = input.read()
        if (second == -1) return null
        val masked = second and 0x80 != 0
        var length = second and 0x7F
        if (length == 126) {
            length = (input.read() shl 8) or input.read()
        } else if (length == 127) {
            repeat(8) { input.read() }
            return null
        }

        val mask = if (masked) ByteArray(4).also { input.readFully(it) } else null
        val payload = ByteArray(length)
        input.readFully(payload)
        if (mask != null) {
            payload.indices.forEach { index ->
                payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
            }
        }
        return String(payload, StandardCharsets.UTF_8)
    }

    private fun java.io.InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read == -1) throw java.io.EOFException()
            offset += read
        }
    }

    private fun java.io.InputStream.readHttpLine(): String? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val value = read()
            if (value == -1) return null
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().toString(StandardCharsets.US_ASCII)
    }

    private fun closeWebSocketClients() {
        webSocketClients.forEach { it.closeQuietly() }
        webSocketClients.clear()
    }

    private fun Socket.closeQuietly() {
        try {
            close()
        } catch (_: Exception) {
        }
    }

    private fun updateRemoteDisplayState(state: TabletDisplayState, source: String) {
        val pairedHost = pairedPhoneHostId
        if (pairedHost == null) {
            setClientConnectionState(TabletConnectionState.Searching)
            SyncLog.debug(TAG) { "Observed phone snapshot while tablet is not yet paired" }
            return
        }
        if (state.hostId.isNotBlank() && pairedHost != state.hostId) {
            SyncLog.warn(
                TAG,
                "Ignored tablet score snapshot from non-paired host",
                "Ignored tablet score snapshot from non-paired host ${state.hostId}; paired host is $pairedHost"
            )
            return
        }
        if (state.sessionId.isNotBlank() && pairedPhoneSessionId != state.sessionId) {
            rememberPairedPhoneSession(state.sessionId)
        }
        lastRemoteSnapshotReceivedAt = System.currentTimeMillis()
        _remoteDisplayState.value = state
        setClientConnectionState(TabletConnectionState.Connected)
        learnPlayerNamesIfChanged(state)
        if (tabletDisplayAvailable && !phoneWebSocketConnected) {
            connectToPairedDiscoveredPhoneWebSocket()
            connectToRememberedPhoneWebSocket()
            connectToGatewayPhoneWebSocket()
        }
        SyncLog.debug(TAG) { "Received tablet score snapshot over $source" }
    }

    /**
     * Lets a passively-displaying tablet learn the paired phone's player names for its own
     * autocomplete list, without writing to SharedPreferences on every broadcast tick.
     */
    private fun learnPlayerNamesIfChanged(state: TabletDisplayState) {
        if (!state.matchActive) return
        val names = listOf(state.teamAPlayer1, state.teamAPlayer2, state.teamBPlayer1, state.teamBPlayer2)
        if (names.all { it.isBlank() }) return
        val key = "${state.hostId}|${state.sessionId}|${names.joinToString("|")}"
        if (key == lastLearnedPlayerNamesKey) return
        lastLearnedPlayerNamesKey = key
        PlayerRepository.markPlayersPlayed(names)
    }

    private fun setHostConnectionState(state: TabletConnectionState) {
        if (_hostConnectionState.value != state) {
            val previous = _hostConnectionState.value
            _hostConnectionState.value = state
            SyncLog.debug(TAG) { "Tablet host connection state: $previous -> $state" }
        }
    }

    private fun setClientConnectionState(state: TabletConnectionState) {
        if (_clientConnectionState.value != state) {
            val previous = _clientConnectionState.value
            _clientConnectionState.value = state
            SyncLog.debug(TAG) { "Tablet client connection state: $previous -> $state" }
        }
    }

    private fun String.toTabletTcpPort(): Int? =
        split("|").getOrNull(1)?.toIntOrNull()

    private fun String.toPhoneWebSocketPort(): Int? =
        split("|").getOrNull(1)?.toIntOrNull()

    private fun TabletCommand.toWirePayload(sessionId: String, secret: String?): String {
        val timestamp = System.currentTimeMillis().toString()
        val mac = secret?.let {
            MessageAuthenticator.sign(it, listOf(wireValue, sessionId, timestamp).joinToString("|"))
        }.orEmpty()
        return listOf(
            TABLET_COMMAND_PROTOCOL,
            wireValue,
            sessionId.toWireField(),
            timestamp,
            mac.toWireField()
        ).joinToString("|")
    }

    private fun TabletCommand.toWirePayload(sessionId: String, payload: TabletSetupPayload, secret: String?): String {
        val timestamp = System.currentTimeMillis().toString()
        val payloadFields = listOf(
            payload.teamAName.toWireField(),
            payload.teamBName.toWireField(),
            payload.teamAPlayer1.toWireField(),
            payload.teamAPlayer2.toWireField(),
            payload.teamBPlayer1.toWireField(),
            payload.teamBPlayer2.toWireField(),
            payload.scoringFormat.name.toWireField(),
            (payload.startingTeam?.toWireValue() ?: "").toWireField(),
            payload.myTeamOnTop.toString()
        )
        val mac = secret?.let {
            MessageAuthenticator.sign(it, (listOf(wireValue, sessionId, timestamp) + payloadFields).joinToString("|"))
        }.orEmpty()
        return (
            listOf(TABLET_COMMAND_PROTOCOL, wireValue, sessionId.toWireField(), timestamp) +
                payloadFields +
                listOf(mac.toWireField())
            ).joinToString("|")
    }

    private fun String.toTabletCommandMessage(): TabletCommandMessage? {
        val fields = split("|")
        if (fields.size < 5 || fields[0] != TABLET_COMMAND_PROTOCOL) return null
        val command = TabletCommand.fromWireValue(fields[1]) ?: return null
        return TabletCommandMessage(
            command = command,
            sessionId = fields.getOrNull(2)?.fromWireField(),
            timestamp = fields.getOrNull(3),
            mac = fields.last().fromWireField(),
            args = fields.drop(4).dropLast(1)
        )
    }

    /** Verifies the message's MAC was produced with [secret]; args/sessionId/timestamp are the raw wire fields. */
    private fun TabletCommandMessage.verify(secret: String): Boolean {
        val mac = mac ?: return false
        val timestampValue = timestamp ?: return false
        val signedMessage = (listOf(command.wireValue, sessionId.orEmpty(), timestampValue) + args).joinToString("|")
        return MessageAuthenticator.matches(MessageAuthenticator.sign(secret, signedMessage), mac)
    }

    private fun String.toWelcomeSecret(): String? {
        val fields = split("|")
        if (fields.size < 2 || fields[0] != TABLET_WELCOME_PROTOCOL) return null
        return fields[1]
    }

    private fun acquireMulticastLock() {
        val context = appContext ?: return
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java) ?: return
        multicastLock = wifiManager.createMulticastLock("RallyScoreTabletDisplay").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        multicastLock = null
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        addresses += InetAddress.getByName("255.255.255.255")
        addresses += interfaceBroadcastAddresses()

        val context = appContext
        val wifiManager = context?.applicationContext?.getSystemService(WifiManager::class.java)
        val dhcpInfo = wifiManager?.dhcpInfo
        if (dhcpInfo != null) {
            val broadcast = dhcpInfo.ipAddress and dhcpInfo.netmask or dhcpInfo.netmask.inv()
            val bytes = byteArrayOf(
                (broadcast and 0xFF).toByte(),
                (broadcast shr 8 and 0xFF).toByte(),
                (broadcast shr 16 and 0xFF).toByte(),
                (broadcast shr 24 and 0xFF).toByte()
            )
            addresses += InetAddress.getByAddress(bytes)
        }

        return addresses.toList()
    }

    private fun gatewayAddresses(): List<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        val context = appContext
        val wifiManager = context?.applicationContext?.getSystemService(WifiManager::class.java)
        val gateway = wifiManager?.dhcpInfo?.gateway
        if (gateway != null && gateway != 0) {
            val bytes = byteArrayOf(
                (gateway and 0xFF).toByte(),
                (gateway shr 8 and 0xFF).toByte(),
                (gateway shr 16 and 0xFF).toByte(),
                (gateway shr 24 and 0xFF).toByte()
            )
            addresses += InetAddress.getByAddress(bytes)
        }
        return addresses.toList()
    }

    private fun broadcastEndpoints(): List<InetSocketAddress> =
        broadcastAddresses().map { address -> InetSocketAddress(address, PORT) }

    private fun interfaceBroadcastAddresses(): List<InetAddress> =
        networkInterfaces()
            .flatMap { networkInterface ->
                networkInterface.interfaceAddresses
                    .mapNotNull { interfaceAddress ->
                        interfaceAddress.broadcast
                    }
            }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }

    private fun localIpv4Networks(): List<LocalIpv4Network> =
        networkInterfaces()
            .flatMap { networkInterface ->
                networkInterface.interfaceAddresses.mapNotNull { interfaceAddress ->
                    val address = interfaceAddress.address as? Inet4Address ?: return@mapNotNull null
                    if (address.isLoopbackAddress || address.isLinkLocalAddress) return@mapNotNull null
                    LocalIpv4Network(
                        address = address,
                        prefixLength = interfaceAddress.networkPrefixLength.toInt()
                    )
                }
            }
            .filter { it.prefixLength in 24..30 }

    private fun networkInterfaces(): List<NetworkInterface> =
        try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { networkInterface ->
                    runCatching {
                        networkInterface.isUp &&
                            !networkInterface.isLoopback &&
                            !networkInterface.isVirtual
                    }.getOrDefault(false)
                }
        } catch (_: Exception) {
            emptyList()
        }

    private fun markStaleRemoteState() {
        pruneDiscoveredPhones()
        if (_remoteDisplayState.value == null) return
        if (phoneWebSocketConnected) return
        val ageMs = System.currentTimeMillis() - lastRemoteSnapshotReceivedAt
        if (ageMs > STALE_REMOTE_STATE_MS) {
            setClientConnectionState(TabletConnectionState.Reconnecting)
            SyncLog.debug(TAG) { "Tablet score snapshot is stale; keeping last score while reconnecting" }
        }
    }

    private fun hasFreshRemoteSnapshot(): Boolean =
        _remoteDisplayState.value != null &&
            System.currentTimeMillis() - lastRemoteSnapshotReceivedAt <= STALE_REMOTE_STATE_MS

    private fun registerDiscoveredPhone(state: TabletDisplayState, address: InetAddress, port: Int) {
        if (state.hostId.isBlank()) return
        val now = System.currentTimeMillis()
        discoveredPhonesByHostId[state.hostId] = DiscoveredPhone(
            hostId = state.hostId,
            sessionId = state.sessionId,
            state = state,
            address = address,
            port = port,
            lastSeenAt = now
        )
        publishDiscoveredPhones(now)
    }

    private fun pruneDiscoveredPhones() {
        publishDiscoveredPhones(System.currentTimeMillis())
    }

    private fun publishDiscoveredPhones(now: Long) {
        val staleHostIds = discoveredPhonesByHostId.values
            .filter { now - it.lastSeenAt > STALE_DISCOVERED_PHONE_MS }
            .map { it.hostId }
        staleHostIds.forEach(discoveredPhonesByHostId::remove)
        _discoveredPhones.value = discoveredPhonesByHostId.values
            .sortedWith(
                compareByDescending<DiscoveredPhone> { it.state.matchActive }
                    .thenBy { it.hostId.toCourtCode() }
                    .thenBy { it.hostId }
            )
            .map { candidate ->
                TabletPhoneCandidate(
                    hostId = candidate.hostId,
                    courtCode = candidate.hostId.toCourtCode(),
                    label = "${candidate.state.teamACourtOrderedName} vs ${candidate.state.teamBCourtOrderedName}",
                    matchActive = candidate.state.matchActive,
                    updatedAt = candidate.state.updatedAt
                )
            }
    }

    private fun pairedDiscoveredPhoneEndpoint(): InetSocketAddress? {
        val pairedHost = pairedPhoneHostId ?: return null
        val candidate = discoveredPhonesByHostId[pairedHost] ?: return null
        return InetSocketAddress(candidate.address, candidate.port)
    }

    private fun connectToPairedDiscoveredPhoneWebSocket() {
        val endpoint = pairedDiscoveredPhoneEndpoint() ?: return
        SyncLog.debug(TAG) { "Attempting reconnection to rediscovered paired endpoint" }
        connectToPhoneWebSocket(endpoint.address, endpoint.port)
    }

    private fun shouldProbePhoneEndpoint(endpoint: InetSocketAddress): Boolean {
        val rememberedEndpoint = rememberedPhoneEndpoint()
        if (rememberedEndpoint != null && rememberedEndpoint == endpoint) return true
        val discoveredEndpoint = pairedDiscoveredPhoneEndpoint()
        if (discoveredEndpoint != null && discoveredEndpoint == endpoint) return true
        return rememberedEndpoint == null && discoveredEndpoint == null
    }

    private fun rememberPhoneEndpoint(address: InetAddress, port: Int) {
        val host = address.hostAddress ?: return
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PHONE_HOST, host)
            .putInt(KEY_LAST_PHONE_PORT, port)
            .apply()
    }

    private fun rememberPairedPhoneIdentity(hostId: String, sessionId: String) {
        pairedPhoneHostId = hostId
        _pairedPhoneHost.value = hostId
        pairedPhoneSessionId = sessionId
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PAIRED_PHONE_HOST_ID, hostId)
            .putString(KEY_PAIRED_PHONE_SESSION_ID, sessionId)
            .apply()
        SyncLog.debug(TAG) { "Paired tablet to phone host and session" }
    }

    private fun rememberPairedPhoneSession(sessionId: String) {
        pairedPhoneSessionId = sessionId
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PAIRED_PHONE_SESSION_ID, sessionId)
            .apply()
    }

    private fun rememberedPhoneEndpoint(): InetSocketAddress? {
        val context = appContext ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val host = prefs.getString(KEY_LAST_PHONE_HOST, null) ?: return null
        val port = prefs.getInt(KEY_LAST_PHONE_PORT, PHONE_WS_PORT)
        val address = try {
            InetAddress.getByName(host)
        } catch (_: Exception) {
            return null
        }
        return InetSocketAddress(address, port)
    }

    private fun rememberedPairedPhoneHostId(): String? =
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_PAIRED_PHONE_HOST_ID, null)

    private fun rememberedPairedPhoneSessionId(): String? =
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_PAIRED_PHONE_SESSION_ID, null)

    private fun GameState.toTabletDisplayPayload(
        matchActive: Boolean,
        canUndo: Boolean,
        watchConnected: Boolean,
        voiceAnnouncementMode: VoiceAnnouncementMode,
        hostId: String,
        sessionId: String
    ): String =
        listOf(
            PROTOCOL,
            System.currentTimeMillis().toString(),
            matchActive.toString(),
            canUndo.toString(),
            hostId.toWireField(),
            sessionId.toWireField(),
            myTeamOnTopForSync.toString(),
            watchConnected.toString(),
            settings.teamAName.toWireField(),
            settings.teamBName.toWireField(),
            teamAScore.toString(),
            teamBScore.toString(),
            servingTeam.toWireValue(),
            serverNumber.displayValue.toString(),
            scoreCall.toWireField(),
            spokenScoreCall().toWireField(),
            voiceAnnouncementMode.wireValue,
            servingPlayerName().toWireField(),
            courtOrderedTeamName(Team.A).toWireField(),
            courtOrderedTeamName(Team.B).toWireField(),
            settings.teamAPlayer1.toWireField(),
            settings.teamAPlayer2.toWireField(),
            settings.teamBPlayer1.toWireField(),
            settings.teamBPlayer2.toWireField()
        ).joinToString("|")

    private fun String.toTabletDisplayState(): TabletDisplayState? {
        val fields = split("|")
        if ((fields.size != 10 && fields.size != 11 && fields.size != 13 && fields.size != 14 && fields.size != 16 && fields.size != 18 && fields.size != 19 && fields.size != 20 && fields.size != 24) || fields[0] != PROTOCOL) return null
        val hasPlayerNames = fields.size == 24
        val hasCanUndo = fields.size == 11 || fields.size == 13 || fields.size == 14 || fields.size == 16 || fields.size == 18 || fields.size == 19 || fields.size == 20 || hasPlayerNames
        val hasVoiceFields = fields.size == 13 || fields.size == 14 || fields.size == 16 || fields.size == 18 || fields.size == 19 || fields.size == 20 || hasPlayerNames
        val hasServingPlayerName = fields.size == 14 || fields.size == 16 || fields.size == 18 || fields.size == 19 || fields.size == 20 || hasPlayerNames
        val hasCourtNames = fields.size == 16 || fields.size == 18 || fields.size == 19 || fields.size == 20 || hasPlayerNames
        val hasIdentityFields = fields.size == 18 || fields.size == 19 || fields.size == 20 || hasPlayerNames
        val hasMyTeamOnTop = fields.size == 19 || fields.size == 20 || hasPlayerNames
        val hasWatchConnected = fields.size == 20 || hasPlayerNames
        val offset = if (hasCanUndo) 1 else 0
        val identityOffset = if (hasIdentityFields) 2 else 0
        val sideOffset = if (hasMyTeamOnTop) 1 else 0
        val watchOffset = if (hasWatchConnected) 1 else 0
        val scoreCall = fields[9 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped()

        return TabletDisplayState(
            updatedAt = fields[1].toLongOrNull() ?: return null,
            matchActive = fields[2].toBooleanStrictOrNull() ?: return null,
            canUndo = if (hasCanUndo) fields[3].toBooleanStrictOrNull() ?: false else false,
            hostId = if (hasIdentityFields) fields[3 + offset].fromWireFieldCapped() else "",
            sessionId = if (hasIdentityFields) fields[4 + offset].fromWireFieldCapped() else "",
            myTeamOnTop = if (hasMyTeamOnTop) {
                fields[3 + offset + identityOffset].toBooleanStrictOrNull() ?: true
            } else {
                true
            },
            watchConnected = if (hasWatchConnected) {
                fields[3 + offset + identityOffset + sideOffset].toBooleanStrictOrNull() ?: false
            } else {
                false
            },
            teamAName = fields[3 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped(),
            teamBName = fields[4 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped(),
            teamACourtOrderedName = if (hasCourtNames) fields[13 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped() else fields[3 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped(),
            teamBCourtOrderedName = if (hasCourtNames) fields[14 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped() else fields[4 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped(),
            teamAPlayer1 = if (hasPlayerNames) fields[fields.size - 4].fromWireFieldCapped() else "",
            teamAPlayer2 = if (hasPlayerNames) fields[fields.size - 3].fromWireFieldCapped() else "",
            teamBPlayer1 = if (hasPlayerNames) fields[fields.size - 2].fromWireFieldCapped() else "",
            teamBPlayer2 = if (hasPlayerNames) fields[fields.size - 1].fromWireFieldCapped() else "",
            teamAScore = fields[5 + offset + identityOffset + sideOffset + watchOffset].toIntOrNull()?.coerceIn(0, MAX_MATCH_SCORE) ?: return null,
            teamBScore = fields[6 + offset + identityOffset + sideOffset + watchOffset].toIntOrNull()?.coerceIn(0, MAX_MATCH_SCORE) ?: return null,
            servingTeam = fields[7 + offset + identityOffset + sideOffset + watchOffset].toTeam(),
            serverNumber = fields[8 + offset + identityOffset + sideOffset + watchOffset].toIntOrNull()?.coerceIn(1, 2) ?: return null,
            scoreCall = scoreCall,
            spokenScoreCall = if (hasVoiceFields) fields[10 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped() else scoreCall,
            voiceAnnouncementMode = VoiceAnnouncementMode.fromWireValue(
                if (hasVoiceFields) fields[11 + offset + identityOffset + sideOffset + watchOffset] else null
            ),
            servingPlayerName = if (hasServingPlayerName) fields[12 + offset + identityOffset + sideOffset + watchOffset].fromWireFieldCapped() else ""
        )
    }

    private fun Team.toWireValue(): String = when (this) {
        Team.A -> "A"
        Team.B -> "B"
    }

    private fun String.toTeam(): Team = when (this) {
        "B" -> Team.B
        else -> Team.A
    }

    private fun String.toWireField(): String =
        replace("%", "%25")
            .replace("|", "%7C")
            .replace("\n", " ")
            .replace("\r", " ")

    private fun String.fromWireField(): String =
        replace("%7C", "|")
            .replace("%25", "%")

    /** Decodes a wire field and caps its length so a malformed/oversized peer payload can't grow unbounded. */
    private fun String.fromWireFieldCapped(): String =
        fromWireField().take(MAX_NAME_FIELD_LENGTH)

    private fun String.toCourtCode(): String =
        filter { it.isLetterOrDigit() }
            .takeLast(4)
            .uppercase()
            .ifBlank { "0000" }
}
