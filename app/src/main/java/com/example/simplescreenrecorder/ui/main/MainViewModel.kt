package com.example.simplescreenrecorder.ui.main

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplescreenrecorder.data.CaptureMode
import com.example.simplescreenrecorder.data.QualityProfile
import com.example.simplescreenrecorder.data.RecordingConfig
import com.example.simplescreenrecorder.data.RecordingPreferences
import com.example.simplescreenrecorder.engine.AspectScaleEngine
import com.example.simplescreenrecorder.engine.ScreenMetrics
import com.example.simplescreenrecorder.service.RecordingStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordingItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateAdded: Long
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = RecordingPreferences(application)

    val configFlow: StateFlow<RecordingConfig> = preferences.configFlow
    val isRecording: StateFlow<Boolean> = RecordingStateHolder.isRecording
    val elapsedSeconds: StateFlow<Long> = RecordingStateHolder.elapsedSeconds

    private val _screenMetrics = MutableStateFlow(
        AspectScaleEngine.computeMetrics(application, configFlow.value)
    )
    val screenMetrics: StateFlow<ScreenMetrics> = _screenMetrics.asStateFlow()

    private val _recentRecordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recentRecordings: StateFlow<List<RecordingItem>> = _recentRecordings.asStateFlow()

    init {
        updateMetrics()
        loadRecentRecordings()

        viewModelScope.launch {
            RecordingStateHolder.lastSavedFilePath.collect {
                loadRecentRecordings()
            }
        }
    }

    fun updateConfig(newConfig: RecordingConfig) {
        preferences.saveConfig(newConfig)
        updateMetrics()
    }

    fun selectProfile(profile: QualityProfile) {
        val current = configFlow.value
        updateConfig(current.copy(profile = profile))
    }

    fun setCaptureMode(mode: CaptureMode) {
        val current = configFlow.value
        updateConfig(current.copy(captureMode = mode))
    }

    fun setCustomScale(scale: Float) {
        val current = configFlow.value
        updateConfig(current.copy(profile = QualityProfile.CUSTOM, customScaleFactor = scale))
    }

    fun setCustomBitrate(bitrateMbps: Int) {
        val current = configFlow.value
        updateConfig(current.copy(profile = QualityProfile.CUSTOM, customBitrateMbps = bitrateMbps))
    }

    fun setCustomFps(fps: Int) {
        val current = configFlow.value
        updateConfig(current.copy(profile = QualityProfile.CUSTOM, customFps = fps))
    }

    fun setRecordAudio(enabled: Boolean) {
        val current = configFlow.value
        updateConfig(current.copy(recordAudio = enabled))
    }

    fun setRecordSystemAudio(enabled: Boolean) {
        val current = configFlow.value
        updateConfig(current.copy(recordSystemAudio = enabled))
    }

    fun setRecordMicAudio(enabled: Boolean) {
        val current = configFlow.value
        updateConfig(current.copy(recordMicAudio = enabled))
    }

    fun updateMetrics() {
        val context = getApplication<Application>()
        _screenMetrics.value = AspectScaleEngine.computeMetrics(context, configFlow.value)
    }

    fun loadRecentRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<RecordingItem>()
            val context = getApplication<Application>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("Movies/captures%")
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Recording"
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        list.add(RecordingItem(id, uri, name, size, date))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _recentRecordings.value = list
        }
    }
}
