package com.courtside.pickleball.domain

object WearSyncContract {
    const val COMMAND_A_WON_RALLY = "/rallyscore/command/a_won_rally"
    const val COMMAND_B_WON_RALLY = "/rallyscore/command/b_won_rally"
    const val COMMAND_UNDO = "/rallyscore/command/undo"
    const val COMMAND_END_MATCH = "/rallyscore/command/end_match"
    const val COMMAND_START_MATCH_TEAM_A = "/rallyscore/command/start_match_team_a"
    const val COMMAND_START_MATCH_TEAM_B = "/rallyscore/command/start_match_team_b"

    const val SCORE_STATE_PATH = "/rallyscore/score_state"

    const val KEY_TEAM_A_SCORE = "team_a_score"
    const val KEY_TEAM_B_SCORE = "team_b_score"
    const val KEY_SERVING_TEAM = "serving_team"
    const val KEY_SERVER_NUMBER = "server_number"
    const val KEY_TEAM_A_NAME = "team_a_name"
    const val KEY_TEAM_B_NAME = "team_b_name"
    const val KEY_SCORE_CALL = "score_call"
    const val KEY_SPOKEN_SCORE_CALL = "spoken_score_call"
    const val KEY_UPDATED_AT = "updated_at"
    const val KEY_MATCH_ACTIVE = "match_active"
    const val KEY_CAN_UNDO = "can_undo"
    const val KEY_VOICE_MODE = "voice_mode"

    const val TEAM_A = "A"
    const val TEAM_B = "B"

    const val VOICE_OFF = "off"
    const val VOICE_PHONE_ONLY = "phone_only"
    const val VOICE_WATCH_ONLY = "watch_only"
    const val VOICE_TABLET_ONLY = "tablet_only"
    const val VOICE_WATCH_THEN_PHONE = "watch_then_phone"
    const val VOICE_WATCH_THEN_TABLET = "watch_then_tablet"
    const val VOICE_PHONE_THEN_TABLET = "phone_then_tablet"
}

enum class VoiceAnnouncementMode(val wireValue: String) {
    Off(WearSyncContract.VOICE_OFF),
    PhoneOnly(WearSyncContract.VOICE_PHONE_ONLY),
    WatchOnly(WearSyncContract.VOICE_WATCH_ONLY),
    TabletOnly(WearSyncContract.VOICE_TABLET_ONLY),
    WatchThenPhone(WearSyncContract.VOICE_WATCH_THEN_PHONE),
    WatchThenTablet(WearSyncContract.VOICE_WATCH_THEN_TABLET),
    PhoneThenTablet(WearSyncContract.VOICE_PHONE_THEN_TABLET);

    companion object {
        fun fromWireValue(value: String?): VoiceAnnouncementMode =
            values().firstOrNull { it.wireValue == value } ?: WatchThenPhone
    }
}
