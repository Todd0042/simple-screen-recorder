package com.example.simplescreenrecorder.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.simplescreenrecorder.data.CaptureMode
import com.example.simplescreenrecorder.data.RecordingPreferences
import com.example.simplescreenrecorder.service.RecordingStateHolder
import com.example.simplescreenrecorder.service.ScreenRecordService

class ProjectionPromptActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, intent)
        } else {
            Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT).show()
        }
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (RecordingStateHolder.isRecording.value) {
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(stopIntent)
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            return
        }

        val prefs = RecordingPreferences(this)
        val config = prefs.loadConfig()
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val projectionConfig = when (config.captureMode) {
                CaptureMode.FULL_SCREEN -> MediaProjectionConfig.createConfigForDefaultDisplay()
                CaptureMode.SINGLE_APP -> MediaProjectionConfig.createConfigForUserChoice()
            }
            projectionManager.createScreenCaptureIntent(projectionConfig)
        } else {
            projectionManager.createScreenCaptureIntent()
        }

        projectionLauncher.launch(captureIntent)
    }
}
