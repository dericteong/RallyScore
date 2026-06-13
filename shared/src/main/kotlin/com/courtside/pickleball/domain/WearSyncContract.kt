package com.courtside.pickleball.domain

object WearSyncContract {
    const val COMMAND_A_WON_RALLY = "/rallyscore/command/a_won_rally"
    const val COMMAND_B_WON_RALLY = "/rallyscore/command/b_won_rally"
    const val COMMAND_UNDO = "/rallyscore/command/undo"

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

    const val TEAM_A = "A"
    const val TEAM_B = "B"
}
