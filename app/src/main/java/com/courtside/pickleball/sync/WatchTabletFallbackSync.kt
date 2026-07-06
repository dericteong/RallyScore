package com.courtside.pickleball.sync

import android.content.Context
import android.net.wifi.WifiManager
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.WearSyncContract
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object WatchTabletFallbackSync {
    private const val TAG = "WatchTabletFallback"
    private const val DISCOVERY_PROTOCOL = "RALLYSCORE_WATCH_TABLET_DISCOVERY_V1"
    private const val STATE_PROTOCOL = "RALLYSCORE_WATCH_TABLET_STATE_V1"
    private const val COMMAND_PROTOCOL = "RALLYSCORE_WATCH_TABLET_COMMAND_V1"
    private const val WELCOME_PROTOCOL = "RALLYSCORE_WATCH_TABLET_WELCOME_V1"
    private const val DISCOVERY_PORT = 45461
    private const val TCP_PORT = 45462
    private const val BROADCAST_INTERVAL_MS = 1_000L
    private const val CLIENT_TIMEOUT_MS = 1_000
    private const val MAX_CONNECTIONS_PER_MINUTE = 30
    private const val RATE_LIMIT_WINDOW_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientSockets = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val connectionSecrets = java.util.concurrent.ConcurrentHashMap<Socket, String>()
    private val connectionRateLimiter = RateLimiter(MAX_CONNECTIONS_PER_MINUTE, RATE_LIMIT_WINDOW_MS)
    private val _started = MutableStateFlow(false)
    private val _watchConnected = MutableStateFlow(false)
    val started = _started.asStateFlow()
    val watchConnected = _watchConnected.asStateFlow()

    private var appContext: Context? = null
    private var discoveryJob: Job? = null
    private var serverJob: Job? = null
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var stateProvider: (() -> GameState)? = null
    @Volatile private var matchActiveProvider: (() -> Boolean)? = null
    @Volatile private var canUndoProvider: (() -> Boolean)? = null
    @Volatile private var voiceModeProvider: (() -> VoiceAnnouncementMode)? = null
    @Volatile private var courtCodeProvider: (() -> String)? = null
    @Volatile private var commandHandler: ((String) -> Unit)? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        ensureMulticastLock()
    }

    fun start(
        stateProvider: () -> GameState,
        matchActiveProvider: () -> Boolean,
        canUndoProvider: () -> Boolean,
        voiceModeProvider: () -> VoiceAnnouncementMode,
        courtCodeProvider: () -> String,
        onWatchCommand: (String) -> Unit
    ) {
        this.stateProvider = stateProvider
        this.matchActiveProvider = matchActiveProvider
        this.canUndoProvider = canUndoProvider
        this.voiceModeProvider = voiceModeProvider
        this.courtCodeProvider = courtCodeProvider
        this.commandHandler = onWatchCommand
        if (discoveryJob?.isActive != true) {
            discoveryJob = scope.launch { runDiscoveryBroadcaster() }
        }
        if (serverJob?.isActive != true) {
            serverJob = scope.launch { runServer() }
        }
        _started.value = true
        _watchConnected.value = false
    }

    fun stop() {
        discoveryJob?.cancel()
        discoveryJob = null
        serverJob?.cancel()
        serverJob = null
        synchronized(clientSockets) {
            clientSockets.forEach { it.closeQuietly() }
            clientSockets.clear()
        }
        connectionSecrets.clear()
        _started.value = false
        _watchConnected.value = false
    }

    private suspend fun runDiscoveryBroadcaster() {
        try {
            ensureMulticastLock()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (true) {
                    val payload = listOf(
                        DISCOVERY_PROTOCOL,
                        TCP_PORT.toString(),
                        courtCodeProvider?.invoke().orEmpty().toWireField(),
                        System.currentTimeMillis().toString()
                    ).joinToString("|")
                    val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                    broadcastAddresses().forEach { address ->
                        runCatching {
                            socket.send(
                                DatagramPacket(
                                    bytes,
                                    bytes.size,
                                    address,
                                    DISCOVERY_PORT
                                )
                            )
                        }
                    }
                    SyncLog.debug(TAG) { "Broadcast watch-tablet discovery" }
                    delay(BROADCAST_INTERVAL_MS)
                }
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Watch-tablet discovery broadcaster stopped", "Watch-tablet discovery broadcaster stopped", error)
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
                SyncLog.debug(TAG) { "Acquired multicast lock for watch-tablet discovery" }
            }
            .onFailure { error ->
                SyncLog.warn(TAG, "Unable to acquire multicast lock for watch-tablet discovery", "Unable to acquire multicast lock for watch-tablet discovery", error)
            }
    }

    private suspend fun runServer() {
        try {
            ServerSocket(TCP_PORT).use { serverSocket ->
                serverSocket.soTimeout = CLIENT_TIMEOUT_MS
                SyncLog.debug(TAG) { "Watch-tablet TCP server started" }
                while (true) {
                    try {
                        val socket = serverSocket.accept()
                        val remoteKey = socket.inetAddress?.hostAddress ?: "unknown"
                        if (!connectionRateLimiter.allow(remoteKey)) {
                            SyncLog.warn(
                                TAG,
                                "Rate limited a watch-tablet connection attempt",
                                "Rate limited watch-tablet connection from $remoteKey"
                            )
                            socket.closeQuietly()
                            continue
                        }
                        connectionSecrets[socket] = MessageAuthenticator.newSecret()
                        clientSockets += socket
                        updateWatchConnectedState()
                        SyncLog.debug(TAG) { "Accepted watch-tablet client" }
                        scope.launch {
                            handleClient(socket)
                        }
                    } catch (_: SocketTimeoutException) {
                        publishStateToClients()
                    }
                }
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Watch-tablet TCP server stopped", "Watch-tablet TCP server stopped", error)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val secret = connectionSecrets[socket]
        try {
            socket.soTimeout = CLIENT_TIMEOUT_MS
            // Hand the per-connection secret to the watch once, directly over this socket,
            // before any state - it is never broadcast, so a LAN attacker who only sees the
            // UDP discovery packet cannot forge a signed command.
            if (secret != null) {
                sendLine(socket, "$WELCOME_PROTOCOL|$secret")
            }
            publishState(socket)
            BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = try {
                        reader.readLine()
                    } catch (_: SocketTimeoutException) {
                        publishState(socket)
                        continue
                    } ?: break

                    val commandPath = line.toVerifiedWatchCommandPath(secret)
                    if (commandPath == null) {
                        SyncLog.warn(
                            TAG,
                            "Ignored unauthenticated or malformed watch-tablet command",
                            "Ignored unauthenticated or malformed watch-tablet command"
                        )
                        continue
                    }
                    SyncLog.debug(TAG) { "Received watch-tablet command" }
                    commandHandler?.invoke(commandPath)
                    publishState(socket)
                }
            }
        } catch (error: Exception) {
            SyncLog.warn(TAG, "Watch-tablet client disconnected", "Watch-tablet client disconnected", error)
        } finally {
            clientSockets.remove(socket)
            connectionSecrets.remove(socket)
            socket.closeQuietly()
            updateWatchConnectedState()
        }
    }

    private fun updateWatchConnectedState() {
        _watchConnected.value = synchronized(clientSockets) {
            clientSockets.any { it.isConnected && !it.isClosed }
        }
    }

    private fun publishStateToClients() {
        synchronized(clientSockets) {
            clientSockets.toList().forEach { socket ->
                publishState(socket)
            }
        }
    }

    private fun publishState(socket: Socket) {
        val state = stateProvider?.invoke() ?: return
        val matchActive = matchActiveProvider?.invoke() ?: false
        val canUndo = canUndoProvider?.invoke() ?: false
        val voiceMode = voiceModeProvider?.invoke() ?: VoiceAnnouncementMode.TabletOnly
        val payload = state.toWatchTabletPayload(matchActive, canUndo, voiceMode)
        if (sendLine(socket, payload)) {
            SyncLog.debug(TAG) { "Published watch-tablet state" }
        } else {
            clientSockets.remove(socket)
            connectionSecrets.remove(socket)
            socket.closeQuietly()
            updateWatchConnectedState()
        }
    }

    /**
     * Writes one newline-terminated line and reports success. `PrintWriter` never throws on
     * write failure (it only sets an internal error flag), so callers must check the result to
     * detect and prune a dead connection.
     */
    private fun sendLine(socket: Socket, line: String): Boolean =
        try {
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(line)
            !writer.checkError()
        } catch (_: Exception) {
            false
        }

    private fun GameState.toWatchTabletPayload(
        matchActive: Boolean,
        canUndo: Boolean,
        voiceMode: VoiceAnnouncementMode
    ): String = listOf(
        STATE_PROTOCOL,
        System.currentTimeMillis().toString(),
        matchActive.toString(),
        canUndo.toString(),
        WearSyncContract.DEVICE_ROLE_TABLET,
        teamAScore.toString(),
        teamBScore.toString(),
        servingTeam.toWireValue(),
        serverNumber.displayValue.toString(),
        settings.teamAName.toWireField(),
        settings.teamBName.toWireField(),
        scoreCall.toWireField(),
        spokenScoreCall().toWireField(),
        voiceMode.wireValue
    ).joinToString("|")

    /**
     * Parses `COMMAND_PROTOCOL|commandPath|timestamp|mac` and returns the command path only if
     * the whitelist matches and the HMAC (over `commandPath|timestamp`, keyed by this
     * connection's [secret]) verifies. Without this an unauthenticated LAN client could inject
     * END_MATCH/START_MATCH into a tablet-hosted match.
     */
    private fun String.toVerifiedWatchCommandPath(secret: String?): String? {
        if (secret == null) return null
        val fields = split("|")
        if (fields.size < 4 || fields[0] != COMMAND_PROTOCOL) return null
        val commandPath = when (fields[1]) {
            WearSyncContract.COMMAND_A_WON_RALLY,
            WearSyncContract.COMMAND_B_WON_RALLY,
            WearSyncContract.COMMAND_UNDO,
            WearSyncContract.COMMAND_END_MATCH,
            WearSyncContract.COMMAND_START_MATCH_TEAM_A,
            WearSyncContract.COMMAND_START_MATCH_TEAM_B -> fields[1]
            else -> null
        } ?: return null
        val timestamp = fields[2]
        val mac = fields[3]
        val expected = MessageAuthenticator.sign(secret, "$commandPath|$timestamp")
        if (!MessageAuthenticator.matches(expected, mac)) return null
        return commandPath
    }

    private fun Team.toWireValue(): String = when (this) {
        Team.A -> WearSyncContract.TEAM_A
        Team.B -> WearSyncContract.TEAM_B
    }

    private fun String.toWireField(): String =
        replace("%", "%25")
            .replace("|", "%7C")
            .replace("\n", " ")
            .replace("\r", " ")

    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = mutableSetOf<InetAddress>()
        addresses += InetAddress.getByName("255.255.255.255")
        Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { network ->
            if (!network.isUp || network.isLoopback) return@forEach
            network.interfaceAddresses.orEmpty().forEach { interfaceAddress ->
                interfaceAddress.broadcast?.let(addresses::add)
            }
        }
        return addresses.toList()
    }

    private fun Socket.closeQuietly() {
        runCatching { close() }
    }
}
