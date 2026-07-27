package com.watcher.app.detection

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import java.io.InputStream

/**
 * Adversarial pattern overlay system.
 *
 * Loads pattern images from the phone's storage and applies them
 * to camera frames for testing. Supports both full-frame overlay
 * and face-region-only overlay (for testing pattern placement).
 */
class PatternOverlay {

    companion object {
        private const val TAG = "Watcher.PatternOverlay"
        private const val DEFAULT_ALPHA = 0.6f
    }

    private var patternBitmap: Bitmap? = null
    private var overlayMode: OverlayMode = OverlayMode.FULL_FRAME
    private var alpha: Float = DEFAULT_ALPHA

    enum class OverlayMode {
        FULL_FRAME,   // Pattern covers entire frame
        FACE_REGION,  // Pattern overlaid on detected face area only
        UPPER_BODY,   // Pattern overlaid on upper body region
        CUSTOM_REGION // User-selected region
    }

    /**
     * Load a pattern image from a URI (e.g., user picks from gallery).
     * Supported formats: PNG, JPG, WEBP
     */
    fun loadPattern(contentResolver: ContentResolver, uri: Uri): Boolean {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            patternBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            Log.i(TAG, "Pattern loaded: ${patternBitmap?.width}x${patternBitmap?.height}")
            patternBitmap != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load pattern: ${e.message}")
            false
        }
    }

    /**
     * Load a pattern from a file path.
     */
    fun loadPattern(path: String): Boolean {
        return try {
            patternBitmap = BitmapFactory.decodeFile(path)
            Log.i(TAG, "Pattern loaded from file: $path")
            patternBitmap != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load pattern from $path: ${e.message}")
            false
        }
    }

    /**
     * Load a pattern from a Bitmap directly.
     */
    fun loadPattern(bitmap: Bitmap) {
        patternBitmap = bitmap
        Log.i(TAG, "Pattern loaded from Bitmap: ${bitmap.width}x${bitmap.height}")
    }

    /**
     * Apply the pattern overlay onto a camera frame.
     *
     * @param frame Original camera frame (will be modified in-place)
     * @param detections Current face detections (for face-region overlay)
     * @return Modified frame with pattern applied
     */
    fun applyOverlay(frame: Bitmap, detections: List<SingleFaceResult>): Bitmap {
        val pattern = patternBitmap ?: return frame

        return when (overlayMode) {
            OverlayMode.FULL_FRAME -> applyFullFrame(frame, pattern)
            OverlayMode.FACE_REGION -> applyFaceRegion(frame, pattern, detections)
            OverlayMode.UPPER_BODY -> applyUpperBody(frame, pattern, detections)
            OverlayMode.CUSTOM_REGION -> applyFullFrame(frame, pattern)
        }
    }

    private fun applyFullFrame(frame: Bitmap, pattern: Bitmap): Bitmap {
        val result = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)

        // Scale pattern to frame size
        val scaled = Bitmap.createScaledBitmap(pattern, frame.width, frame.height, true)
        val paint = android.graphics.Paint().apply {
            this.alpha = (alpha * 255).toInt()
            isAntiAlias = true
            isFilterBitmap = true
        }

        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return result
    }

    private fun applyFaceRegion(
        frame: Bitmap,
        pattern: Bitmap,
        detections: List<SingleFaceResult>
    ): Bitmap {
        val result = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            this.alpha = (alpha * 255).toInt()
            isAntiAlias = true
            isFilterBitmap = true
        }

        for (face in detections) {
            val box = face.boundingBox
            // Expand the face region slightly to cover more area
            val expandedWidth = (box.width() * 1.5f).toInt()
            val expandedHeight = (box.height() * 2.0f).toInt()
            val cx = box.centerX()
            val cy = box.centerY()
            val scaled = Bitmap.createScaledBitmap(pattern, expandedWidth, expandedHeight, true)
            canvas.drawBitmap(scaled, cx - expandedWidth / 2f, cy - expandedHeight / 2f, paint)
        }

        return result
    }

    private fun applyUpperBody(
        frame: Bitmap,
        pattern: Bitmap,
        detections: List<SingleFaceResult>
    ): Bitmap {
        val result = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            this.alpha = (alpha * 255).toInt()
            isAntiAlias = true
            isFilterBitmap = true
        }

        for (face in detections) {
            val box = face.boundingBox
            // Upper body: expand face area downward to cover chest area
            val bodyWidth = (box.width() * 3.0f).toInt()
            val bodyHeight = (box.height() * 5.0f).toInt()
            val cx = box.centerX()
            val top = box.top - box.height() * 0.5f

            val scaled = Bitmap.createScaledBitmap(pattern, bodyWidth, bodyHeight, true)
            canvas.drawBitmap(scaled, cx - bodyWidth / 2f, top, paint)
        }

        return result
    }

    fun clearPattern() {
        patternBitmap = null
    }

    fun hasPattern(): Boolean = patternBitmap != null

    fun setOverlayMode(mode: OverlayMode) {
        overlayMode = mode
    }

    fun setAlpha(a: Float) {
        alpha = a.coerceIn(0.1f, 1.0f)
    }

    fun getPatternBitmap(): Bitmap? = patternBitmap
}
