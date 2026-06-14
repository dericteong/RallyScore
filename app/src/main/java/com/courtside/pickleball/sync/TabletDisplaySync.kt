package com.courtside.pickleball.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.displayValue
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
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
    val matchActive: Boolean,
    val updatedAt: Long
) {
    fun servingName(): String = when (servingTeam) {
        Team.A -> teamAName
        Team.B -> teamBName
    }
}

object TabletDisplaySync {
    private const val TAG = "TabletDisplaySync"
    private const val PORT = 45454
    private const val TABLET_TCP_PORT = 45455
    private const val PROTOCOL = "RALLYSCORE_TABLET_V1"
    private const val TABLET_HELLO_PROTOCOL = "RALLYSCORE_TABLET_HELLO_V1"
    private const val BROADCAST_INTERVAL_MS = 1_000L
    private const val TABLET_HELLO_INTERVAL_MS = 2_000L
    private const val STALE_REMOTE_STATE_MS = 5_000L
    private const val STALE_TABLET_ADDRESS_MS = 30_000L
    private const val LISTEN_TIMEOUT_MS = 1_000
    private const val MAX_PACKET_BYTES = 2_048

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _remoteDisplayState = MutableStateFlow<TabletDisplayState?>(null)
    val remoteDisplayState: StateFlow<TabletDisplayState?> = _remoteDisplayState.asStateFlow()

    private var appContext: Context? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listenerJob: Job? = null
    private var broadcasterJob: Job? = null
    private var tabletHelloJob: Job? = null
    private var tabletTcpServerJob: Job? = null
    private val tabletEndpoints = ConcurrentHashMap<InetSocketAddress, Long>()
    private val tabletTcpEndpoints = ConcurrentHashMap<InetSocketAddress, Long>()

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
        matchActiveProvider: () -> Boolean
    ) {
        if (broadcasterJob?.isActive == true) return
        broadcasterJob = scope.launch {
            runBroadcaster(stateProvider, matchActiveProvider)
        }
    }

    fun setTabletDisplayAvailable(available: Boolean) {
        if (available) {
            startTabletHelloBroadcaster()
            startTabletTcpServer()
        } else {
            tabletHelloJob?.cancel()
            tabletHelloJob = null
            tabletTcpServerJob?.cancel()
            tabletTcpServerJob = null
        }
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
                        clearStaleRemoteState()
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

                    val state = payload.toTabletDisplayState() ?: continue
                    _remoteDisplayState.value = state
                    Log.d(TAG, "Received tablet score snapshot: ${state.scoreCall}")
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
        matchActiveProvider: () -> Boolean
    ) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (currentCoroutineContext().isActive) {
                    if (!matchActiveProvider()) {
                        delay(BROADCAST_INTERVAL_MS)
                        continue
                    }

                    val state = stateProvider()
                    val payload = state.toTabletDisplayPayload(matchActive = true)
                    val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                    val targets = broadcastEndpoints() + currentTabletEndpoints()
                    targets.forEach { endpoint ->
                        val packet = DatagramPacket(bytes, bytes.size, endpoint.address, endpoint.port)
                        socket.send(packet)
                    }
                    publishToTabletTcpEndpoints(payload)
                    Log.d(TAG, "Published tablet score snapshot: ${state.scoreCall} to ${targets.size} targets")
                    delay(BROADCAST_INTERVAL_MS)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Tablet display broadcaster stopped", error)
        }
    }

    private fun startTabletHelloBroadcaster() {
        if (tabletHelloJob?.isActive == true) return
        tabletHelloJob = scope.launch {
            runTabletHelloBroadcaster()
        }
    }

    private fun startTabletTcpServer() {
        if (tabletTcpServerJob?.isActive == true) return
        tabletTcpServerJob = scope.launch {
            runTabletTcpServer()
        }
    }

    private suspend fun runTabletHelloBroadcaster() {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 500
                val helloPayload = "$TABLET_HELLO_PROTOCOL|$TABLET_TCP_PORT"
                val bytes = helloPayload.toByteArray(StandardCharsets.UTF_8)
                while (currentCoroutineContext().isActive) {
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
                        clearStaleRemoteState()
                        continue
                    }

                    socket.use {
                        it.soTimeout = LISTEN_TIMEOUT_MS
                        val payload = it.getInputStream()
                            .bufferedReader(StandardCharsets.UTF_8)
                            .readLine()
                            ?: return@use
                        val state = payload.toTabletDisplayState() ?: return@use
                        _remoteDisplayState.value = state
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
                clearStaleRemoteState()
                continue
            }

            val payload = String(
                packet.data,
                packet.offset,
                packet.length,
                StandardCharsets.UTF_8
            )
            val state = payload.toTabletDisplayState() ?: continue
            _remoteDisplayState.value = state
            Log.d(TAG, "Received tablet score snapshot on hello socket: ${state.scoreCall}")
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

    private fun String.toTabletTcpPort(): Int? =
        split("|").getOrNull(1)?.toIntOrNull()

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

    private fun broadcastEndpoints(): List<InetSocketAddress> =
        broadcastAddresses().map { address -> InetSocketAddress(address, PORT) }

    private fun clearStaleRemoteState() {
        val current = _remoteDisplayState.value ?: return
        val ageMs = System.currentTimeMillis() - current.updatedAt
        if (ageMs > STALE_REMOTE_STATE_MS) {
            _remoteDisplayState.value = null
            Log.d(TAG, "Cleared stale tablet score snapshot")
        }
    }

    private fun GameState.toTabletDisplayPayload(matchActive: Boolean): String =
        listOf(
            PROTOCOL,
            System.currentTimeMillis().toString(),
            matchActive.toString(),
            settings.teamAName.toWireField(),
            settings.teamBName.toWireField(),
            teamAScore.toString(),
            teamBScore.toString(),
            servingTeam.toWireValue(),
            serverNumber.displayValue.toString(),
            scoreCall.toWireField()
        ).joinToString("|")

    private fun String.toTabletDisplayState(): TabletDisplayState? {
        val fields = split("|")
        if (fields.size != 10 || fields[0] != PROTOCOL) return null

        return TabletDisplayState(
            updatedAt = fields[1].toLongOrNull() ?: return null,
            matchActive = fields[2].toBooleanStrictOrNull() ?: return null,
            teamAName = fields[3].fromWireField(),
            teamBName = fields[4].fromWireField(),
            teamAScore = fields[5].toIntOrNull() ?: return null,
            teamBScore = fields[6].toIntOrNull() ?: return null,
            servingTeam = fields[7].toTeam(),
            serverNumber = fields[8].toIntOrNull() ?: return null,
            scoreCall = fields[9].fromWireField()
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
