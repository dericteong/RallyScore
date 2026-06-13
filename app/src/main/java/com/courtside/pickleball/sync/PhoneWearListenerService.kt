package com.courtside.pickleball.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearListenerService : WearableListenerService() {
    override fun onCreate() {
        super.onCreate()
        RallyScorePhoneHub.initialize(applicationContext)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("PhoneWearListener", "Message received: ${messageEvent.path}")
        RallyScorePhoneHub.handleWatchCommand(messageEvent.path)
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("PhoneWearListener", "Wear peer connected: ${peer.displayName}")
        RallyScorePhoneHub.refreshConnectedNodes()
    }

    override fun onPeerDisconnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("PhoneWearListener", "Wear peer disconnected: ${peer.displayName}")
        RallyScorePhoneHub.refreshConnectedNodes()
    }
}
