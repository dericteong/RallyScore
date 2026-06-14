package com.courtside.pickleball.wear

import android.content.Context
import android.util.Log
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.WearSyncContract
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhoneScoreState(
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

    private val _phoneScoreState = MutableStateFlow<PhoneScoreState?>(null)
    val phoneScoreState: StateFlow<PhoneScoreState?> = _phoneScoreState.asStateFlow()

    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    private var appContext: Context? = null
    private var initialized = false

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
                val connected = nodes.isNotEmpty()
                _phoneConnected.value = connected
                Log.d(TAG, "Connected phone nodes: ${nodes.size}")
            }
            .addOnFailureListener { error ->
                _phoneConnected.value = false
                Log.w(TAG, "Unable to refresh connected phone nodes", error)
            }
    }

    fun refreshPhoneState() {
        refreshConnectedNodes()
        refreshLatestScoreState()
    }

    fun handlePeerConnected(peer: Node) {
        Log.d(TAG, "Phone peer connected: ${peer.displayName}")
        _phoneConnected.value = true
        refreshLatestScoreState()
    }

    fun handlePeerDisconnected(peer: Node) {
        Log.d(TAG, "Phone peer disconnected: ${peer.displayName}")
        refreshConnectedNodes()
    }

    fun sendCommand(context: Context, commandPath: String) {
        val appContext = context.applicationContext
        Wearable.getNodeClient(appContext).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No phone node available for command: $commandPath")
                    _phoneConnected.value = false
                    return@addOnSuccessListener
                }

                nodes.forEach { node ->
                    Wearable.getMessageClient(appContext)
                        .sendMessage(node.id, commandPath, ByteArray(0))
                        .addOnSuccessListener {
                            Log.d(TAG, "Sent command $commandPath to ${node.displayName}")
                        }
                        .addOnFailureListener { error ->
                            _phoneConnected.value = false
                            Log.w(TAG, "Failed command $commandPath to ${node.displayName}", error)
                        }
                }
            }
            .addOnFailureListener { error ->
                _phoneConnected.value = false
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
                    dataItems.forEach { item ->
                        if (item.uri.path == WearSyncContract.SCORE_STATE_PATH) {
                            updatePhoneScoreState(DataMapItem.fromDataItem(item).dataMap)
                        }
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
        val state = PhoneScoreState(
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
        _phoneScoreState.value = state
        Log.d(TAG, "Phone score state updated: ${state.scoreCall}")
    }

    private fun String?.toTeam(): Team =
        when (this) {
            WearSyncContract.TEAM_B -> Team.B
            else -> Team.A
        }
}
