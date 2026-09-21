package com.example.simplescreenrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.simplescreenrecorder.MainActivity
import com.example.simplescreenrecorder.data.RecordingPreferences
import com.example.simplescreenrecorder.engine.AspectScaleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileDescriptor

class ScreenRecordService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentVideoUri: Uri? = null
    private var fileDescriptor: FileDescriptor? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var startTimeMillis: Long = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system")
            stopScreenRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Parcelable>(EXTRA_RESULT_DATA) as? Intent
                }

                if (resultCode != 0 && resultData != null) {
                    startScreenRecording(resultCode, resultData)
                } else {
                    Log.e(TAG, "Invalid projection result code or data")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopScreenRecording()
            }
        }
        return START_NOT_STICKY
    }

    private fun startScreenRecording(resultCode: Int, resultData: Intent) {
        if (RecordingStateHolder.isRecording.value) return

        val prefs = RecordingPreferences(this)
        val config = prefs.loadConfig()
        val metrics = AspectScaleEngine.computeMetrics(this, config)

        val shouldRecordAudio = config.recordAudio && (config.recordSystemAudio || config.recordMicAudio)

        Log.d(TAG, "Starting recording. Resolution: ${metrics.scaledWidth}x${metrics.scaledHeight}, Audio: $shouldRecordAudio (System: ${config.recordSystemAudio}, Mic: ${config.recordMicAudio})")

        // 1. Post Foreground Service Notification BEFORE initializing MediaProjection (Required on API 34+)
        val notification = buildForegroundNotification("00:00")
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (shouldRecordAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            type
        } else {
            0
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Setup Output MediaStore entry in Movies/captures/
        val contentValues = ContentValues().apply {
            val fileName = "Recording_${System.currentTimeMillis()}.mp4"
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/captures")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        currentVideoUri = uri

        val pfd = resolver.openFileDescriptor(uri, "rw")
        if (pfd == null) {
            Log.e(TAG, "Failed to open FileDescriptor for URI")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        fileDescriptor = pfd.fileDescriptor

        // 3. Configure MediaRecorder
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            if (shouldRecordAudio) {
                if (config.recordMicAudio) {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                } else {
                    recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                }
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(fileDescriptor)
            recorder.setVideoSize(metrics.scaledWidth, metrics.scaledHeight)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (shouldRecordAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
            }
            recorder.setVideoEncodingBitRate(metrics.bitrateBps)
            recorder.setVideoFrameRate(metrics.fps)

            recorder.prepare()
            mediaRecorder = recorder
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder preparation failed", e)
            pfd.close()
            resolver.delete(uri, null, null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // 4. Create MediaProjection & VirtualDisplay
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.e(TAG, "MediaProjection is null")
            pfd.close()
            resolver.delete(uri, null, null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        mediaProjection = projection
        projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        virtualDisplay = projection.createVirtualDisplay(
            "SimpleScreenRecorder",
            metrics.scaledWidth,
            metrics.scaledHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            null
        )

        // 5. Start MediaRecorder
        try {
            recorder.start()
            RecordingStateHolder.setRecording(true)
            ScreenRecordTileService.updateTileState(this, true)
            startTimeMillis = System.currentTimeMillis()
            startTimer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            stopScreenRecording()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000
                RecordingStateHolder.setElapsedSeconds(elapsed)
                val formattedTime = String.format("%02d:%02d", elapsed / 60, elapsed % 60)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(formattedTime))
                delay(1000)
            }
        }
    }

    private fun stopScreenRecording() {
        if (!RecordingStateHolder.isRecording.value && mediaRecorder == null) return

        timerJob?.cancel()
        timerJob = null

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        // Finalize MediaStore entry
        currentVideoUri?.let { uri ->
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            contentResolver.update(uri, values, null, null)
            RecordingStateHolder.setLastSavedFilePath(uri.toString())
            Log.d(TAG, "Saved recording to MediaStore: $uri")
        }

        RecordingStateHolder.setRecording(false)
        ScreenRecordTileService.updateTileState(this, false)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildForegroundNotification(timerText: String): Notification {
        createNotificationChannel()

        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Recording Screen • $timerText")
            .setContentText("Tap to stop recording")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(stopPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Recording",
                stopPendingIntent
            )
            .setCustomBigContentView(null)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows screen recording status and quick stop control"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenRecording()
    }

    companion object {
        const val ACTION_START = "com.example.simplescreenrecorder.action.START"
        const val ACTION_STOP = "com.example.simplescreenrecorder.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "screen_recording_pill_channel"
        private const val NOTIFICATION_ID = 9901
        private const val TAG = "ScreenRecordService"
    }
}
