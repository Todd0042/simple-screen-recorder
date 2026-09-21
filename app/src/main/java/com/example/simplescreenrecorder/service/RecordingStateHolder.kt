package com.example.simplescreenrecorder.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecordingStateHolder {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _lastSavedFilePath = MutableStateFlow<String?>(null)
    val lastSavedFilePath: StateFlow<String?> = _lastSavedFilePath.asStateFlow()

    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
        if (!recording) {
            _elapsedSeconds.value = 0L
        }
    }

    fun setElapsedSeconds(seconds: Long) {
        _elapsedSeconds.value = seconds
    }

    fun setLastSavedFilePath(path: String) {
        _lastSavedFilePath.value = path
    }
}
