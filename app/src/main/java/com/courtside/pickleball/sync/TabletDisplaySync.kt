package com.courtside.pickleball.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
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
    val teamAName: String,
    val teamBName: String,
    val teamAScore: Int,
    val teamBScore: Int,
    val servingTeam: Team,
    val serverNumber: Int,
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

enum class TabletConnectionState {
    Searching,
    Reconnecting,
    Connected
}

enum class TabletCommand(val wireValue: String) {
    TeamAWonRally("TABLET_ME_WON_RALLY"),
    TeamBWonRally("TABLET_OPP_WON_RALLY"),
    Undo("TABLET_UNDO"),
    EndMatch("TABLET_END_MATCH");

    companion object {
        fun fromWireValue(value: String): TabletCommand? =
            entries.firstOrNull { it.wireValue == value }
    }
}

object TabletDisplaySync {
    private const val TAG = "TabletDisplaySync"
    private const val PREFS_NAME = "rallyscore_tablet_display_sync"
    private const val KEY_LAST_PHONE_HOST = "last_phone_host"
    private const val KEY_LAST_PHONE_PORT = "last_phone_port"
    private const val PORT = 45454
    private const val TABLET_TCP_PORT = 45455
    private const val PHONE_WS_PORT = 45456
    private const val PROTOCOL = "RALLYSCORE_TABLET_V1"
    private const val TABLET_COMMAND_PROTOCOL = "RALLYSCORE_TABLET_COMMAND_V1"
    private const val TABLET_HELLO_PROTOCOL = "RALLYSCORE_TABLET_HELLO_V1"
    private const val PHONE_WS_PROTOCOL = "RALLYSCORE_PHONE_WS_V1"
    private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val BROADCAST_INTERVAL_MS = 1_000L
    private const val TABLET_HELLO_INTERVAL_MS = 2_000L
    private const val TABLET_SUBNET_SCAN_INTERVAL_MS = 15_000L
    private const val TABLET_SUBNET_SCAN_TIMEOUT_MS = 80
    private const val STALE_REMOTE_STATE_MS = 5_000L
    private const val WEBSOCKET_READ_TIMEOUT_MS = 4_000
    private const val STALE_TABLET_ADDRESS_MS = 30_000L
    private const val LISTEN_TIMEOUT_MS = 1_000
    private const val MAX_WS_RECONNECT_ATTEMPTS = 5
    private const val MAX_PACKET_BYTES = 2_048

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _remoteDisplayState = MutableStateFlow<TabletDisplayState?>(null)
    val remoteDisplayState: StateFlow<TabletDisplayState?> = _remoteDisplayState.asStateFlow()
    private val _connectionState = MutableStateFlow(TabletConnectionState.Searching)
    val connectionState: StateFlow<TabletConnectionState> = _connectionState.asStateFlow()

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
    @Volatile private var tabletCommandHandler: ((TabletCommand) -> Unit)? = null
    @Volatile private var latestPhonePayload: String? = null
    @Volatile private var lastRemoteSnapshotReceivedAt: Long = 0L
    private val webSocketClients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val tabletEndpoints = ConcurrentHashMap<InetSocketAddress, Long>()
    private val tabletTcpEndpoints = ConcurrentHashMap<InetSocketAddress, Long>()

    private data class LocalIpv4Network(
        val address: Inet4Address,
        val prefixLength: Int
    )

    fun initialize(context: Context) {
        appContext = context.applicationContext
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
        voiceModeProvider: () -> VoiceAnnouncementMode = { VoiceAnnouncementMode.PhoneOnly },
        onTabletCommand: (TabletCommand) -> Unit = {}
    ) {
        tabletCommandHandler = onTabletCommand
        if (broadcasterJob?.isActive == true) return
        startPhoneWebSocketServer()
        broadcasterJob = scope.launch {
            runBroadcaster(stateProvider, matchActiveProvider, canUndoProvider, voiceModeProvider)
        }
    }

    fun sendTabletCommand(command: TabletCommand) {
        val socket = connectedPhoneWebSocket
        if (!tabletDisplayAvailable || !phoneWebSocketConnected || socket == null) {
            Log.w(TAG, "Unable to send tablet command while phone WebSocket is disconnected: ${command.wireValue}")
            if (tabletDisplayAvailable) {
                connectToRememberedPhoneWebSocket()
                connectToGatewayPhoneWebSocket()
            }
            setConnectionState(
                if (_remoteDisplayState.value == null) {
                    TabletConnectionState.Searching
                } else {
                    TabletConnectionState.Reconnecting
                }
            )
            return
        }

        scope.launch {
            try {
                socket.writeWebSocketTextFrame(command.toWirePayload())
                Log.d(TAG, "Sent tablet command: ${command.wireValue}")
            } catch (error: Exception) {
                Log.w(TAG, "Failed to send tablet command: ${command.wireValue}", error)
                phoneWebSocketConnected = false
                connectedPhoneWebSocket = null
                setConnectionState(TabletConnectionState.Reconnecting)
            }
        }
    }

    fun setTabletDisplayAvailable(available: Boolean) {
        tabletDisplayAvailable = available
        if (available) {
            stopLocalHubPublisher()
            setConnectionState(TabletConnectionState.Searching)
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
            setConnectionState(TabletConnectionState.Searching)
        }
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
                Log.d(TAG, "Tablet display listener started on UDP $PORT")
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
                        if (tabletDisplayAvailable) {
                            Log.d(TAG, "UDP discovered phone WebSocket availability at ${packet.address.hostAddress}")
                            connectToPhoneWebSocket(packet.address, payload.toPhoneWebSocketPort())
                        }
                        continue
                    }

                    val state = payload.toTabletDisplayState() ?: continue
                    rememberPhoneEndpoint(packet.address, PHONE_WS_PORT)
                    updateRemoteDisplayState(state, "UDP")
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Tablet display listener stopped", error)
        } finally {
            releaseMulticastLock()
        }
    }

    private suspend fun runBroadcaster(
        stateProvider: () -> GameState,
        matchActiveProvider: () -> Boolean,
        canUndoProvider: () -> Boolean,
        voiceModeProvider: () -> VoiceAnnouncementMode
    ) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (currentCoroutineContext().isActive) {
                    val state = stateProvider()
                    val payload = state.toTabletDisplayPayload(
                        matchActive = matchActiveProvider(),
                        canUndo = canUndoProvider(),
                        voiceAnnouncementMode = voiceModeProvider()
                    )
                    latestPhonePayload = payload
                    val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                    broadcastPhoneWebSocketAvailability(socket)
                    val targets = broadcastEndpoints() + currentTabletEndpoints()
                    targets.forEach { endpoint ->
                        val packet = DatagramPacket(bytes, bytes.size, endpoint.address, endpoint.port)
                        socket.send(packet)
                    }
                    publishToTabletTcpEndpoints(payload)
                    publishToWebSocketClients(payload)
                    Log.d(TAG, "Published tablet score snapshot: ${state.scoreCall} to ${targets.size} targets")
                    delay(BROADCAST_INTERVAL_MS)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Tablet display broadcaster stopped", error)
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
        val gateway = gatewayAddresses().firstOrNull()
        if (gateway == null) {
            Log.d(TAG, "No gateway address available for phone discovery")
            return
        }
        Log.d(TAG, "Probing gateway for phone tablet WebSocket at ${gateway.hostAddress}:$PHONE_WS_PORT")
        connectToPhoneWebSocket(gateway, PHONE_WS_PORT)
    }

    private fun connectToRememberedPhoneWebSocket() {
        if (phoneWebSocketConnected) return
        val endpoint = rememberedPhoneEndpoint()
        if (endpoint == null) {
            Log.d(TAG, "No remembered phone endpoint available for reconnection")
            return
        }
        Log.d(TAG, "Attempting reconnection to remembered phone at ${endpoint.address.hostAddress}:${endpoint.port}")
        if (!hasFreshRemoteSnapshot()) {
            setConnectionState(TabletConnectionState.Reconnecting)
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
                Log.d(TAG, "Phone tablet WebSocket server started on $PHONE_WS_PORT")
                while (currentCoroutineContext().isActive) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (_: SocketTimeoutException) {
                        continue
                    }

                    scope.launch {
                        acceptWebSocketClient(socket)
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Phone tablet WebSocket server stopped", error)
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
            setConnectionState(TabletConnectionState.Connected)
            latestPhonePayload?.let { payload ->
                socket.writeWebSocketTextFrame(payload)
            }
            Log.d(TAG, "Tablet WebSocket client connected: ${socket.inetAddress.hostAddress}")
            while (currentCoroutineContext().isActive) {
                val payload = readWebSocketTextFrame(socket) ?: break
                val command = payload.toTabletCommand() ?: continue
                Log.d(TAG, "Received tablet command: ${command.wireValue}")
                tabletCommandHandler?.invoke(command)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to accept tablet WebSocket client: ${error.message}")
        } finally {
            webSocketClients -= socket
            if (webSocketClients.isEmpty()) {
                setConnectionState(TabletConnectionState.Reconnecting)
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
        val endpoint = InetSocketAddress(address, port ?: PHONE_WS_PORT)
        if (phoneWebSocketConnected) {
            Log.d(TAG, "Skipping phone WebSocket probe at ${endpoint.address.hostAddress}:${endpoint.port}; phone socket already connected")
            return
        }
        if (connectedPhoneWebSocketEndpoint == endpoint && tabletWebSocketClientJob?.isActive == true) {
            Log.d(TAG, "Already connecting to phone WebSocket at ${endpoint.address.hostAddress}:${endpoint.port}; skipping")
            return
        }

        Log.d(TAG, "Initiating phone WebSocket connection to ${endpoint.address.hostAddress}:${endpoint.port}")
        connectedPhoneWebSocketEndpoint = endpoint
        if (!phoneWebSocketConnected && !hasFreshRemoteSnapshot()) {
            setConnectionState(
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
                    phoneWebSocketConnected = true
                    connectedPhoneWebSocket = socket
                    consecutiveFailures = 0
                    rememberPhoneEndpoint(endpoint.address, endpoint.port)
                    setConnectionState(TabletConnectionState.Connected)
                    Log.d(TAG, "Connected to phone tablet WebSocket at ${endpoint.address.hostAddress}:${endpoint.port}")
                    while (currentCoroutineContext().isActive && tabletDisplayAvailable) {
                        val payload = try {
                            readWebSocketTextFrame(socket)
                        } catch (_: SocketTimeoutException) {
                            Log.w(TAG, "Phone tablet WebSocket heartbeat timed out at ${endpoint.address.hostAddress}:${endpoint.port}")
                            break
                        } ?: break
                        val state = payload.toTabletDisplayState() ?: continue
                        updateRemoteDisplayState(state, "WebSocket")
                    }
                }
            } catch (error: Exception) {
                consecutiveFailures++
                Log.w(
                    TAG,
                    "Phone tablet WebSocket unavailable at ${endpoint.address.hostAddress}:${endpoint.port}" +
                        " (attempt $consecutiveFailures/$MAX_WS_RECONNECT_ATTEMPTS): ${error.message}"
                )
                markStaleRemoteState()
                if (consecutiveFailures >= MAX_WS_RECONNECT_ATTEMPTS) {
                    Log.w(TAG, "Exceeded max WebSocket reconnect attempts; yielding to discovery")
                    break
                }
                delay(TABLET_HELLO_INTERVAL_MS)
            } finally {
                phoneWebSocketConnected = false
                if (connectedPhoneWebSocket == activeSocket) {
                    connectedPhoneWebSocket = null
                }
                if (consecutiveFailures < MAX_WS_RECONNECT_ATTEMPTS) {
                    markStaleRemoteState()
                }
            }
        }
    }

    private suspend fun runTabletSubnetScanner() {
        while (currentCoroutineContext().isActive && tabletDisplayAvailable) {
            if (!phoneWebSocketConnected) {
                Log.d(TAG, "Starting tablet subnet discovery scan")
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
                Log.d(TAG, "Discovered phone tablet WebSocket at gateway ${gateway.hostAddress}:$PHONE_WS_PORT")
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
                Log.d(TAG, "Discovered phone tablet WebSocket by subnet scan at ${candidate.hostAddress}:$PHONE_WS_PORT")
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
                    Log.d(TAG, "Broadcast tablet display hello")
                    receiveScoreSnapshotsOnHelloSocket(socket, TABLET_HELLO_INTERVAL_MS)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Tablet display hello broadcaster stopped", error)
        }
    }

    private suspend fun runTabletTcpServer() {
        try {
            ServerSocket(TABLET_TCP_PORT).use { serverSocket ->
                serverSocket.soTimeout = LISTEN_TIMEOUT_MS
                Log.d(TAG, "Tablet display TCP server started on $TABLET_TCP_PORT")
                while (currentCoroutineContext().isActive) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (_: SocketTimeoutException) {
                        markStaleRemoteState()
                        continue
                    }

                    socket.use {
                        it.soTimeout = LISTEN_TIMEOUT_MS
                        val payload = it.getInputStream()
                            .bufferedReader(StandardCharsets.UTF_8)
                            .readLine()
                            ?: return@use
                        val state = payload.toTabletDisplayState() ?: return@use
                        updateRemoteDisplayState(state, "TCP")
                        Log.d(TAG, "Received tablet score snapshot over TCP: ${state.scoreCall}")
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Tablet display TCP server stopped", error)
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
            updateRemoteDisplayState(state, "hello socket")
        }
    }

    private fun rememberTabletEndpoint(address: InetAddress, udpPort: Int, tcpPort: Int?) {
        val endpoint = InetSocketAddress(address, udpPort)
        val previous = tabletEndpoints.put(endpoint, System.currentTimeMillis())
        if (previous == null) {
            Log.d(TAG, "Discovered tablet display UDP at ${address.hostAddress}:$udpPort")
        }

        if (tcpPort != null) {
            val tcpEndpoint = InetSocketAddress(address, tcpPort)
            val previousTcp = tabletTcpEndpoints.put(tcpEndpoint, System.currentTimeMillis())
            if (previousTcp == null) {
                Log.d(TAG, "Discovered tablet display TCP at ${address.hostAddress}:$tcpPort")
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
        currentTabletTcpEndpoints().forEach { endpoint ->
            try {
                java.net.Socket().use { socket ->
                    socket.connect(endpoint, LISTEN_TIMEOUT_MS)
                    socket.getOutputStream().write("$payload\n".toByteArray(StandardCharsets.UTF_8))
                    socket.getOutputStream().flush()
                }
                Log.d(TAG, "Published tablet score snapshot over TCP to ${endpoint.address.hostAddress}:${endpoint.port}")
            } catch (error: Exception) {
                tabletTcpEndpoints.remove(endpoint)
                Log.w(
                    TAG,
                    "Removed unreachable tablet TCP endpoint ${endpoint.address.hostAddress}:${endpoint.port}: ${error.message}"
                )
            }
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
        lastRemoteSnapshotReceivedAt = System.currentTimeMillis()
        _remoteDisplayState.value = state
        setConnectionState(TabletConnectionState.Connected)
        if (tabletDisplayAvailable && !phoneWebSocketConnected) {
            connectToRememberedPhoneWebSocket()
            connectToGatewayPhoneWebSocket()
        }
        Log.d(TAG, "Received tablet score snapshot over $source: ${state.scoreCall}")
    }

    private fun setConnectionState(state: TabletConnectionState) {
        if (_connectionState.value != state) {
            val previous = _connectionState.value
            _connectionState.value = state
            Log.d(TAG, "Tablet connection state: $previous -> $state")
        }
    }

    private fun String.toTabletTcpPort(): Int? =
        split("|").getOrNull(1)?.toIntOrNull()

    private fun String.toPhoneWebSocketPort(): Int? =
        split("|").getOrNull(1)?.toIntOrNull()

    private fun TabletCommand.toWirePayload(): String =
        listOf(
            TABLET_COMMAND_PROTOCOL,
            wireValue,
            System.currentTimeMillis().toString()
        ).joinToString("|")

    private fun String.toTabletCommand(): TabletCommand? {
        val fields = split("|")
        if (fields.size < 2 || fields[0] != TABLET_COMMAND_PROTOCOL) return null
        return TabletCommand.fromWireValue(fields[1])
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
        if (_remoteDisplayState.value == null) return
        val ageMs = System.currentTimeMillis() - lastRemoteSnapshotReceivedAt
        if (ageMs > STALE_REMOTE_STATE_MS) {
            setConnectionState(TabletConnectionState.Reconnecting)
            Log.d(TAG, "Tablet score snapshot is stale (age ${ageMs}ms); keeping last score while reconnecting")
        }
    }

    private fun hasFreshRemoteSnapshot(): Boolean =
        _remoteDisplayState.value != null &&
            System.currentTimeMillis() - lastRemoteSnapshotReceivedAt <= STALE_REMOTE_STATE_MS

    private fun rememberPhoneEndpoint(address: InetAddress, port: Int) {
        val host = address.hostAddress ?: return
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PHONE_HOST, host)
            .putInt(KEY_LAST_PHONE_PORT, port)
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

    private fun GameState.toTabletDisplayPayload(
        matchActive: Boolean,
        canUndo: Boolean,
        voiceAnnouncementMode: VoiceAnnouncementMode
    ): String =
        listOf(
            PROTOCOL,
            System.currentTimeMillis().toString(),
            matchActive.toString(),
            canUndo.toString(),
            settings.teamAName.toWireField(),
            settings.teamBName.toWireField(),
            teamAScore.toString(),
            teamBScore.toString(),
            servingTeam.toWireValue(),
            serverNumber.displayValue.toString(),
            scoreCall.toWireField(),
            spokenScoreCall().toWireField(),
            voiceAnnouncementMode.wireValue
        ).joinToString("|")

    private fun String.toTabletDisplayState(): TabletDisplayState? {
        val fields = split("|")
        if ((fields.size != 10 && fields.size != 11 && fields.size != 13) || fields[0] != PROTOCOL) return null
        val hasCanUndo = fields.size == 11 || fields.size == 13
        val hasVoiceFields = fields.size == 13
        val offset = if (hasCanUndo) 1 else 0
        val scoreCall = fields[9 + offset].fromWireField()

        return TabletDisplayState(
            updatedAt = fields[1].toLongOrNull() ?: return null,
            matchActive = fields[2].toBooleanStrictOrNull() ?: return null,
            canUndo = if (hasCanUndo) fields[3].toBooleanStrictOrNull() ?: false else false,
            teamAName = fields[3 + offset].fromWireField(),
            teamBName = fields[4 + offset].fromWireField(),
            teamAScore = fields[5 + offset].toIntOrNull() ?: return null,
            teamBScore = fields[6 + offset].toIntOrNull() ?: return null,
            servingTeam = fields[7 + offset].toTeam(),
            serverNumber = fields[8 + offset].toIntOrNull() ?: return null,
            scoreCall = scoreCall,
            spokenScoreCall = if (hasVoiceFields) fields[10 + offset].fromWireField() else scoreCall,
            voiceAnnouncementMode = VoiceAnnouncementMode.fromWireValue(
                if (hasVoiceFields) fields[11 + offset] else null
            )
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
}
