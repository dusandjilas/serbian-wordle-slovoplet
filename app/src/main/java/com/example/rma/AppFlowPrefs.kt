package com.example.rma

import android.content.Context

object AppFlowPrefs {
    private const val PREFS = "app_flow_prefs"
    private const val KEY_FIRST_GAME_FINISHED = "first_game_finished"

    fun isFirstGameFinished(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_GAME_FINISHED, false)
    }

    fun setFirstGameFinished(context: Context, finished: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIRST_GAME_FINISHED, finished)
            .apply()
    }
}
