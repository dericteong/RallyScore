package com.courtside.pickleball.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService

class WearDataLayerListenerService : WearableListenerService() {
    override fun onCreate() {
        super.onCreate()
        WearPhoneSync.initialize(applicationContext)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        WearSyncLog.debug("WearDataLayerListener") { "Data changed from phone" }
        WearPhoneSync.handleDataEvents(dataEvents)
    }

    override fun onPeerConnected(peer: Node) {
        WearPhoneSync.handlePeerConnected(peer)
    }

    override fun onPeerDisconnected(peer: Node) {
        WearPhoneSync.handlePeerDisconnected(peer)
    }
}
