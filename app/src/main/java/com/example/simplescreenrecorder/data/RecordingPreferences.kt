package com.example.simplescreenrecorder.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecordingPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<RecordingConfig> = _configFlow.asStateFlow()

    fun loadConfig(): RecordingConfig {
        val profileName = prefs.getString(KEY_PROFILE, QualityProfile.DEFAULT.name) ?: QualityProfile.DEFAULT.name
        val profile = try {
            QualityProfile.valueOf(profileName)
        } catch (e: Exception) {
            QualityProfile.DEFAULT
        }

        val modeName = prefs.getString(KEY_CAPTURE_MODE, CaptureMode.FULL_SCREEN.name) ?: CaptureMode.FULL_SCREEN.name
        val captureMode = try {
            CaptureMode.valueOf(modeName)
        } catch (e: Exception) {
            // Handle legacy preference migration if needed
            if (modeName == "USER_CHOICE") CaptureMode.SINGLE_APP else CaptureMode.FULL_SCREEN
        }

        return RecordingConfig(
            profile = profile,
            captureMode = captureMode,
            customScaleFactor = prefs.getFloat(KEY_CUSTOM_SCALE, 1.0f),
            customBitrateMbps = prefs.getInt(KEY_CUSTOM_BITRATE, 10),
            customFps = prefs.getInt(KEY_CUSTOM_FPS, 30),
            recordAudio = prefs.getBoolean(KEY_RECORD_AUDIO, false),
            recordSystemAudio = prefs.getBoolean(KEY_RECORD_SYSTEM_AUDIO, true),
            recordMicAudio = prefs.getBoolean(KEY_RECORD_MIC_AUDIO, false)
        )
    }

    fun saveConfig(config: RecordingConfig) {
        prefs.edit()
            .putString(KEY_PROFILE, config.profile.name)
            .putString(KEY_CAPTURE_MODE, config.captureMode.name)
            .putFloat(KEY_CUSTOM_SCALE, config.customScaleFactor)
            .putInt(KEY_CUSTOM_BITRATE, config.customBitrateMbps)
            .putInt(KEY_CUSTOM_FPS, config.customFps)
            .putBoolean(KEY_RECORD_AUDIO, config.recordAudio)
            .putBoolean(KEY_RECORD_SYSTEM_AUDIO, config.recordSystemAudio)
            .putBoolean(KEY_RECORD_MIC_AUDIO, config.recordMicAudio)
            .apply()

        _configFlow.value = config
    }

    companion object {
        private const val PREFS_NAME = "screen_recorder_prefs"
        private const val KEY_PROFILE = "key_profile"
        private const val KEY_CAPTURE_MODE = "key_capture_mode"
        private const val KEY_CUSTOM_SCALE = "key_custom_scale"
        private const val KEY_CUSTOM_BITRATE = "key_custom_bitrate"
        private const val KEY_CUSTOM_FPS = "key_custom_fps"
        private const val KEY_RECORD_AUDIO = "key_record_audio"
        private const val KEY_RECORD_SYSTEM_AUDIO = "key_record_system_audio"
        private const val KEY_RECORD_MIC_AUDIO = "key_record_mic_audio"
    }
}
