package com.courtside.pickleball.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearListenerService : WearableListenerService() {
    override fun onCreate() {
        super.onCreate()
        RallyScorePhoneHub.initialize(applicationContext)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        SyncLog.debug("PhoneWearListener") { "Message received from Wear" }
        RallyScorePhoneHub.handleWatchCommand(messageEvent.path)
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        SyncLog.debug("PhoneWearListener") { "Wear peer connected" }
        RallyScorePhoneHub.refreshConnectedNodes()
    }

    override fun onPeerDisconnected(peer: com.google.android.gms.wearable.Node) {
        SyncLog.debug("PhoneWearListener") { "Wear peer disconnected" }
        RallyScorePhoneHub.refreshConnectedNodes()
    }
}
