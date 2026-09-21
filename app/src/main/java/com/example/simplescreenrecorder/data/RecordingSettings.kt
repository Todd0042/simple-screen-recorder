package com.example.simplescreenrecorder.data

enum class QualityProfile(
    val displayName: String,
    val description: String,
    val scaleFactor: Float,
    val bitrateMbps: Int,
    val fps: Int
) {
    TINY("Tiny", "0.25x Resolution, 2 Mbps (Minimal storage)", 0.25f, 2, 30),
    SMALL("Small", "0.50x Resolution, 4 Mbps (Compact size)", 0.50f, 4, 30),
    MEDIUM("Medium", "0.75x Resolution, 8 Mbps (Balanced)", 0.75f, 8, 30),
    LARGE("Large", "1.00x Native, 14 Mbps (High quality)", 1.00f, 14, 60),
    DEFAULT("Default", "1.00x Native, 10 Mbps (Standard)", 1.00f, 10, 30),
    CUSTOM("Custom", "User defined resolution and bitrate", 1.00f, 10, 30)
}

enum class CaptureMode(
    val displayName: String,
    val description: String
) {
    FULL_SCREEN("Entire Screen", "Pre-selects Entire Screen by default in the system dialogue"),
    SINGLE_APP("Single App", "Pre-selects Single App by default in the system dialogue")
}

data class RecordingConfig(
    val profile: QualityProfile = QualityProfile.DEFAULT,
    val captureMode: CaptureMode = CaptureMode.FULL_SCREEN,
    val customScaleFactor: Float = 1.00f,
    val customBitrateMbps: Int = 10,
    val customFps: Int = 30,
    val recordAudio: Boolean = false,
    val recordSystemAudio: Boolean = true,
    val recordMicAudio: Boolean = false
) {
    val effectiveScaleFactor: Float
        get() = if (profile == QualityProfile.CUSTOM) customScaleFactor else profile.scaleFactor

    val effectiveBitrateMbps: Int
        get() = if (profile == QualityProfile.CUSTOM) customBitrateMbps else profile.bitrateMbps

    val effectiveFps: Int
        get() = if (profile == QualityProfile.CUSTOM) customFps else profile.fps
}
