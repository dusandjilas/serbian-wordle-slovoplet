package com.example.rma

import android.content.Context

data class AudioSettings(
    val musicEnabled: Boolean,
    val effectsEnabled: Boolean,
    val musicVolume: Float,
    val effectsVolume: Float
)

class AudioSettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): AudioSettings {
        return AudioSettings(
            musicEnabled = prefs.getBoolean(KEY_MUSIC_ENABLED, true),
            effectsEnabled = prefs.getBoolean(KEY_EFFECTS_ENABLED, true),
            musicVolume = prefs.getFloat(KEY_MUSIC_VOLUME, 0.7f),
            effectsVolume = prefs.getFloat(KEY_EFFECTS_VOLUME, 0.8f)
        )
    }

    fun saveSettings(settings: AudioSettings) {
        prefs.edit()
            .putBoolean(KEY_MUSIC_ENABLED, settings.musicEnabled)
            .putBoolean(KEY_EFFECTS_ENABLED, settings.effectsEnabled)
            .putFloat(KEY_MUSIC_VOLUME, settings.musicVolume)
            .putFloat(KEY_EFFECTS_VOLUME, settings.effectsVolume)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "audio_settings"
        private const val KEY_MUSIC_ENABLED = "music_enabled"
        private const val KEY_EFFECTS_ENABLED = "effects_enabled"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_EFFECTS_VOLUME = "effects_volume"
    }
}
