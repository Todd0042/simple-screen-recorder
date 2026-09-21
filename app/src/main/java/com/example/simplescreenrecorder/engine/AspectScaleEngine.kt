package com.example.simplescreenrecorder.engine

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.simplescreenrecorder.data.RecordingConfig
import kotlin.math.roundToInt

data class ScreenMetrics(
    val nativeWidth: Int,
    val nativeHeight: Int,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val aspectRatio: Float,
    val aspectRatioFormatted: String,
    val densityDpi: Int,
    val bitrateBps: Int,
    val fps: Int
)

object AspectScaleEngine {

    fun computeMetrics(context: Context, config: RecordingConfig): ScreenMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val nativeWidth: Int
        val nativeHeight: Int
        val densityDpi: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            val bounds = metrics.bounds
            nativeWidth = bounds.width()
            nativeHeight = bounds.height()
            densityDpi = context.resources.configuration.densityDpi
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            nativeWidth = displayMetrics.widthPixels
            nativeHeight = displayMetrics.heightPixels
            densityDpi = displayMetrics.densityDpi
        }

        val rawAspectRatio = nativeWidth.toFloat() / nativeHeight.toFloat()
        val aspectRatioFormatted = formatAspectRatio(nativeWidth, nativeHeight)

        val scale = config.effectiveScaleFactor.coerceIn(0.1f, 1.0f)
        
        var targetWidth = (nativeWidth * scale).roundToInt()
        var targetHeight = (nativeHeight * scale).roundToInt()

        // Enforce even dimensions (divisible by 2) for H.264 / AVC video codecs
        if (targetWidth % 2 != 0) targetWidth -= 1
        if (targetHeight % 2 != 0) targetHeight -= 1

        // Ensure bounds are non-zero
        targetWidth = maxOf(targetWidth, 16)
        targetHeight = maxOf(targetHeight, 16)

        val bitrateBps = config.effectiveBitrateMbps * 1_000_000

        return ScreenMetrics(
            nativeWidth = nativeWidth,
            nativeHeight = nativeHeight,
            scaledWidth = targetWidth,
            scaledHeight = targetHeight,
            aspectRatio = rawAspectRatio,
            aspectRatioFormatted = aspectRatioFormatted,
            densityDpi = densityDpi,
            bitrateBps = bitrateBps,
            fps = config.effectiveFps
        )
    }

    private fun formatAspectRatio(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "16:9"
        val gcd = gcd(width, height)
        val simpleW = width / gcd
        val simpleH = height / gcd
        
        // Common ratio check or floating point approximation
        val ratio = width.toFloat() / height.toFloat()
        return when {
            kotlin.math.abs(ratio - (16f / 9f)) < 0.05 -> "16:9"
            kotlin.math.abs(ratio - (9f / 16f)) < 0.05 -> "9:16"
            kotlin.math.abs(ratio - (19.5f / 9f)) < 0.05 -> "19.5:9"
            kotlin.math.abs(ratio - (9f / 19.5f)) < 0.05 -> "9:19.5"
            kotlin.math.abs(ratio - (20f / 9f)) < 0.05 -> "20:9"
            kotlin.math.abs(ratio - (9f / 20f)) < 0.05 -> "9:20"
            else -> "$simpleW:$simpleH"
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }
}
