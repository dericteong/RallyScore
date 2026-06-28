package com.courtside.pickleball.wear

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.WearSyncContract
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectedAndroidRole(val wireValue: String) {
    Phone(WearSyncContract.DEVICE_ROLE_PHONE),
    Tablet(WearSyncContract.DEVICE_ROLE_TABLET);

    companion object {
        fun fromWireValue(value: String?): ConnectedAndroidRole =
            entries.firstOrNull { it.wireValue == value } ?: Phone
    }
}

data class PhoneScoreState(
    val sourceNodeId: String,
    val sourceRole: ConnectedAndroidRole,
    val teamAScore: Int,
    val teamBScore: Int,
    val servingTeam: Team,
    val serverNumber: Int,
    val teamAName: String,
    val teamBName: String,
    val scoreCall: String,
    val spokenScoreCall: String,
    val voiceAnnouncementMode: VoiceAnnouncementMode,
    val matchActive: Boolean,
    val canUndo: Boolean,
    val updatedAt: Long
)

object WearPhoneSync {
    private const val TAG = "WearPhoneSync"
    private const val ConnectionGraceMs = 12_000L

    private val _phoneScoreState = MutableStateFlow<PhoneScoreState?>(null)
    val phoneScoreState: StateFlow<PhoneScoreState?> = _phoneScoreState.asStateFlow()

    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    private val statesByNodeId = linkedMapOf<String, PhoneScoreState>()
    private val connectedNodeIds = linkedSetOf<String>()
    private var appContext: Context? = null
    private var initialized = false
    @Volatile private var lastPhoneSeenAtElapsed: Long = 0L

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        refreshConnectedNodes()
        refreshLatestScoreState()
    }

    fun refreshConnectedNodes() {
        val context = appContext ?: return
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                connectedNodeIds.clear()
                connectedNodeIds.addAll(nodes.map { it.id })
                val connected = nodes.isNotEmpty()
                if (connected) {
                    markPhoneSeen()
                } else {
                    updatePhoneConnectedWithGrace("connected node refresh returned no nodes")
                }
                publishPreferredState()
                Log.d(TAG, "Connected phone nodes: ${nodes.size}")
            }
            .addOnFailureListener { error ->
                updatePhoneConnectedWithGrace("connected node refresh failed")
                Log.w(TAG, "Unable to refresh connected phone nodes", error)
            }
    }

    fun refreshPhoneState() {
        refreshConnectedNodes()
        refreshLatestScoreState()
    }

    fun handlePeerConnected(peer: Node) {
        Log.d(TAG, "Phone peer connected: ${peer.displayName}")
        connectedNodeIds += peer.id
        markPhoneSeen()
        refreshLatestScoreState()
    }

    fun handlePeerDisconnected(peer: Node) {
        Log.d(TAG, "Phone peer disconnected: ${peer.displayName}")
        connectedNodeIds -= peer.id
        statesByNodeId.remove(peer.id)
        publishPreferredState()
        refreshConnectedNodes()
    }

    fun sendCommand(context: Context, commandPath: String) {
        val appContext = context.applicationContext
        Wearable.getNodeClient(appContext).connectedNodes
            .addOnSuccessListener { nodes ->
                connectedNodeIds.clear()
                connectedNodeIds.addAll(nodes.map { it.id })
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No phone node available for command: $commandPath")
                    updatePhoneConnectedWithGrace("command send found no phone nodes")
                    return@addOnSuccessListener
                }

                val targetNode = resolvePreferredNode(nodes)
                if (targetNode == null) {
                    Log.w(TAG, "No preferred Android node resolved for command: $commandPath")
                    updatePhoneConnectedWithGrace("command send could not resolve preferred node")
                    return@addOnSuccessListener
                }

                Wearable.getMessageClient(appContext)
                    .sendMessage(targetNode.id, commandPath, ByteArray(0))
                    .addOnSuccessListener {
                        markPhoneSeen()
                        Log.d(TAG, "Sent command $commandPath to ${targetNode.displayName}")
                    }
                    .addOnFailureListener { error ->
                        updatePhoneConnectedWithGrace("command send failed for ${targetNode.displayName}")
                        Log.w(TAG, "Failed command $commandPath to ${targetNode.displayName}", error)
                    }
            }
            .addOnFailureListener { error ->
                updatePhoneConnectedWithGrace("unable to locate phone nodes for command")
                Log.w(TAG, "Unable to find phone nodes for command: $commandPath", error)
            }
    }

    fun handleDataEvents(events: DataEventBuffer) {
        events.forEach { event ->
            val item = event.dataItem
            if (item.uri.path == WearSyncContract.SCORE_STATE_PATH) {
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                updatePhoneScoreState(dataMap)
            }
        }
    }

    private fun refreshLatestScoreState() {
        val context = appContext ?: return
        Wearable.getDataClient(context).dataItems
            .addOnSuccessListener { dataItems ->
                try {
                    var latestDataMap: DataMap? = null
                    var latestUpdatedAt = Long.MIN_VALUE
                    dataItems.forEach { item ->
                        if (item.uri.path == WearSyncContract.SCORE_STATE_PATH) {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            val updatedAt = dataMap.getLong(WearSyncContract.KEY_UPDATED_AT)
                            if (latestDataMap == null || updatedAt >= latestUpdatedAt) {
                                latestDataMap = dataMap
                                latestUpdatedAt = updatedAt
                            }
                            val sourceNodeId = item.uri.host.orEmpty()
                            if (sourceNodeId.isNotBlank()) {
                                updatePhoneScoreState(dataMap, sourceNodeId)
                            }
                        }
                    }
                    if (latestDataMap == null) {
                        publishPreferredState()
                    }
                } finally {
                    dataItems.release()
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to refresh phone score state", error)
            }
    }

    private fun updatePhoneScoreState(dataMap: DataMap) {
        updatePhoneScoreState(dataMap, "")
    }

    private fun updatePhoneScoreState(dataMap: DataMap, sourceNodeId: String) {
        val state = PhoneScoreState(
            sourceNodeId = sourceNodeId,
            sourceRole = ConnectedAndroidRole.fromWireValue(
                dataMap.getString(WearSyncContract.KEY_DEVICE_ROLE)
            ),
            teamAScore = dataMap.getInt(WearSyncContract.KEY_TEAM_A_SCORE),
            teamBScore = dataMap.getInt(WearSyncContract.KEY_TEAM_B_SCORE),
            servingTeam = dataMap.getString(WearSyncContract.KEY_SERVING_TEAM).toTeam(),
            serverNumber = dataMap.getInt(WearSyncContract.KEY_SERVER_NUMBER),
            teamAName = dataMap.getString(WearSyncContract.KEY_TEAM_A_NAME).orEmpty(),
            teamBName = dataMap.getString(WearSyncContract.KEY_TEAM_B_NAME).orEmpty(),
            scoreCall = dataMap.getString(WearSyncContract.KEY_SCORE_CALL).orEmpty(),
            spokenScoreCall = dataMap.getString(WearSyncContract.KEY_SPOKEN_SCORE_CALL).orEmpty(),
            voiceAnnouncementMode = VoiceAnnouncementMode.fromWireValue(
                dataMap.getString(WearSyncContract.KEY_VOICE_MODE)
            ),
            matchActive = dataMap.getBoolean(WearSyncContract.KEY_MATCH_ACTIVE),
            canUndo = dataMap.getBoolean(WearSyncContract.KEY_CAN_UNDO),
            updatedAt = dataMap.getLong(WearSyncContract.KEY_UPDATED_AT)
        )
        val currentForNode = if (sourceNodeId.isNotBlank()) statesByNodeId[sourceNodeId] else null
        if (currentForNode != null && state.updatedAt < currentForNode.updatedAt) {
            Log.d(TAG, "Ignored stale Android score state update from $sourceNodeId: ${state.scoreCall} @${state.updatedAt}")
            return
        }
        if (sourceNodeId.isNotBlank()) {
            statesByNodeId[sourceNodeId] = state
        } else {
            val currentState = _phoneScoreState.value
            if (currentState != null && state.updatedAt < currentState.updatedAt) {
                Log.d(TAG, "Ignored stale Android score state update: ${state.scoreCall} @${state.updatedAt}")
                return
            }
            _phoneScoreState.value = state
        }
        markPhoneSeen()
        publishPreferredState()
        Log.d(TAG, "Android score state updated from ${state.sourceRole.name.lowercase()}: ${state.scoreCall}")
    }

    private fun publishPreferredState() {
        val preferredState = preferredState()
        _phoneScoreState.value = preferredState
    }

    private fun preferredState(): PhoneScoreState? {
        val connectedStates = statesByNodeId.values.filter {
            connectedNodeIds.isEmpty() || connectedNodeIds.contains(it.sourceNodeId)
        }
        if (connectedStates.isEmpty()) {
            return _phoneScoreState.value?.takeIf {
                connectedNodeIds.isEmpty() || connectedNodeIds.contains(it.sourceNodeId)
            }
        }

        return connectedStates.maxWithOrNull(
            compareBy<PhoneScoreState>(
                { rolePriority(it.sourceRole) },
                { it.updatedAt }
            )
        )
    }

    private fun rolePriority(role: ConnectedAndroidRole): Int = when (role) {
        ConnectedAndroidRole.Phone -> 2
        ConnectedAndroidRole.Tablet -> 1
    }

    private fun resolvePreferredNode(nodes: List<Node>): Node? {
        val preferredNodeId = preferredState()?.sourceNodeId?.takeIf { it.isNotBlank() }
        return when {
            preferredNodeId != null -> nodes.firstOrNull { it.id == preferredNodeId }
            nodes.size == 1 -> nodes.first()
            else -> null
        }
    }

    private fun markPhoneSeen() {
        lastPhoneSeenAtElapsed = SystemClock.elapsedRealtime()
        _phoneConnected.value = true
    }

    private fun updatePhoneConnectedWithGrace(reason: String) {
        val now = SystemClock.elapsedRealtime()
        val isFresh = now - lastPhoneSeenAtElapsed <= ConnectionGraceMs
        _phoneConnected.value = isFresh
        Log.d(TAG, "Phone connection grace check ($reason): connected=$isFresh")
    }

    private fun String?.toTeam(): Team =
        when (this) {
            WearSyncContract.TEAM_B -> Team.B
            else -> Team.A
        }
}
