package com.example.simplescreenrecorder.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.simplescreenrecorder.R
import com.example.simplescreenrecorder.ui.ProjectionPromptActivity

class ScreenRecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRecording = RecordingStateHolder.isRecording.value

        if (isRecording) {
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(stopIntent)
            updateTileState()
        } else {
            val promptIntent = Intent(this, ProjectionPromptActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    promptIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(promptIntent)
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRecording = RecordingStateHolder.isRecording.value

        if (isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Recording..."
            tile.subtitle = "Tap to stop"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.icon = Icon.createWithResource(this, android.R.drawable.ic_media_pause)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Screen Record"
            tile.subtitle = "Tap to record"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.icon = Icon.createWithResource(this, android.R.drawable.ic_menu_camera)
            }
        }
        tile.updateTile()
    }

    companion object {
        fun updateTileState(context: Context, isRecording: Boolean) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, ScreenRecordTileService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
