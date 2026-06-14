package com.courtside.pickleball.sync

import android.content.Context
import android.util.Log
import com.courtside.pickleball.domain.GameState
import com.courtside.pickleball.domain.Team
import com.courtside.pickleball.domain.VoiceAnnouncementMode
import com.courtside.pickleball.domain.WearSyncContract
import com.courtside.pickleball.domain.displayValue
import com.courtside.pickleball.domain.spokenScoreCall
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object RallyScorePhoneHub {
    private const val TAG = "RallyScorePhoneHub"

    val store = ScoreboardStore()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _watchConnected = MutableStateFlow(false)
    private val _voiceAnnouncementMode = MutableStateFlow(VoiceAnnouncementMode.PhoneOnly)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()
    val voiceAnnouncementMode: StateFlow<VoiceAnnouncementMode> = _voiceAnnouncementMode.asStateFlow()

    private var appContext: Context? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        TabletDisplaySync.initialize(context.applicationContext)
        refreshConnectedNodes()
        TabletDisplaySync.startListener()
        TabletDisplaySync.startBroadcaster(
            stateProvider = { store.state.value },
            matchActiveProvider = { store.matchActive.value }
        )
        publishScoreState(store.state.value)

        scope.launch {
            store.state.collect { state ->
                publishScoreState(state)
            }
        }
        scope.launch {
            store.matchActive.collect {
                publishScoreState(store.state.value)
            }
        }
        scope.launch {
            _voiceAnnouncementMode.collect {
                publishScoreState(store.state.value)
            }
        }
    }

    fun setVoiceAnnouncementMode(mode: VoiceAnnouncementMode) {
        _voiceAnnouncementMode.value = mode
    }

    fun refreshConnectedNodes() {
        val context = appContext ?: return
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                val connected = nodes.isNotEmpty()
                _watchConnected.value = connected
                Log.d(TAG, "Connected Wear nodes: ${nodes.size}")
            }
            .addOnFailureListener { error ->
                _watchConnected.value = false
                Log.w(TAG, "Unable to refresh Wear nodes", error)
            }
    }

    fun handleWatchCommand(path: String) {
        if (!store.matchActive.value) {
            Log.w(TAG, "Ignored watch command while no phone match is active: $path")
            publishScoreState(store.state.value)
            return
        }

        val next = when (path) {
            WearSyncContract.COMMAND_A_WON_RALLY -> {
                Log.d(TAG, "Watch command: A_WON_RALLY")
                store.recordRallyWinner(Team.A)
            }
            WearSyncContract.COMMAND_B_WON_RALLY -> {
                Log.d(TAG, "Watch command: B_WON_RALLY")
                store.recordRallyWinner(Team.B)
            }
            WearSyncContract.COMMAND_UNDO -> {
                Log.d(TAG, "Watch command: UNDO")
                store.undo()
            }
            else -> {
                Log.w(TAG, "Ignored unknown watch command: $path")
                return
            }
        }
        publishScoreState(next)
    }

    private fun publishScoreState(state: GameState) {
        val context = appContext ?: return
        val request = PutDataMapRequest.create(WearSyncContract.SCORE_STATE_PATH).apply {
            dataMap.putInt(WearSyncContract.KEY_TEAM_A_SCORE, state.teamAScore)
            dataMap.putInt(WearSyncContract.KEY_TEAM_B_SCORE, state.teamBScore)
            dataMap.putString(WearSyncContract.KEY_SERVING_TEAM, state.servingTeam.toWireValue())
            dataMap.putInt(WearSyncContract.KEY_SERVER_NUMBER, state.serverNumber.displayValue)
            dataMap.putString(WearSyncContract.KEY_TEAM_A_NAME, state.settings.teamAName)
            dataMap.putString(WearSyncContract.KEY_TEAM_B_NAME, state.settings.teamBName)
            dataMap.putString(WearSyncContract.KEY_SCORE_CALL, state.scoreCall)
            dataMap.putString(WearSyncContract.KEY_SPOKEN_SCORE_CALL, state.spokenScoreCall())
            dataMap.putLong(WearSyncContract.KEY_UPDATED_AT, System.currentTimeMillis())
            dataMap.putBoolean(WearSyncContract.KEY_MATCH_ACTIVE, store.matchActive.value)
            dataMap.putBoolean(WearSyncContract.KEY_CAN_UNDO, store.canUndo())
            dataMap.putString(WearSyncContract.KEY_VOICE_MODE, _voiceAnnouncementMode.value.wireValue)
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener {
                Log.d(TAG, "Published score state: ${state.scoreCall}")
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to publish score state", error)
            }
    }

    private fun Team.toWireValue(): String = when (this) {
        Team.A -> WearSyncContract.TEAM_A
        Team.B -> WearSyncContract.TEAM_B
    }

}
