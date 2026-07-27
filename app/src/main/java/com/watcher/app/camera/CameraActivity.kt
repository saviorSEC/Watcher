package com.watcher.app.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.watcher.app.R
import com.watcher.app.detection.FaceDetector
import com.watcher.app.detection.FaceNetEmbedder
import com.watcher.app.detection.PatternOverlay
import com.watcher.app.detection.SingleFaceResult
import kotlinx.coroutines.*

/**
 * Quick Scan mode — live camera with real-time face detection overlay.
 *
 * Shows:
 * - Camera feed with detection boxes drawn in real-time
 * - Detection status (face found / not found)
 * - Number of faces and confidence score
 * - Live bounding box overlay
 */
class CameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Watcher.CameraActivity"
        private const val SCAN_INTERVAL_MS = 100L // 10 fps analysis
    }

    private lateinit var previewView: PreviewView
    private lateinit var cameraManager: CameraManager
    private lateinit var faceDetector: FaceDetector
    private var embedder: FaceNetEmbedder? = null
    private var patternOverlay: PatternOverlay? = null

    private lateinit var overlayView: View
    private lateinit var tvStatus: TextView
    private lateinit var tvFaceCount: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvInferenceTime: TextView
    private lateinit var btnFlipCamera: ImageButton
    private lateinit var btnBack: ImageButton

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastAnalysisTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.camera_preview)
        overlayView = findViewById(R.id.detection_overlay)
        tvStatus = findViewById(R.id.tv_status)
        tvFaceCount = findViewById(R.id.tv_face_count)
        tvConfidence = findViewById(R.id.tv_confidence)
        tvInferenceTime = findViewById(R.id.tv_inference_time)
        btnFlipCamera = findViewById(R.id.btn_flip_camera)
        btnBack = findViewById(R.id.btn_back)

        // Initialize face detector
        faceDetector = FaceDetector(FaceDetector.accurateOptions())

        // Try to load FaceNet embedder (optional — model file may not be present)
        try {
            embedder = FaceNetEmbedder(this)
        } catch (e: Exception) {
            Log.w(TAG, "FaceNet embedder not available: ${e.message}")
        }

        // Initialize camera manager
        cameraManager = CameraManager(this)

        // Set up frame callback
        cameraManager.onFrame = { bitmap ->
            analyzeFrame(bitmap)
        }

        // Start camera
        cameraManager.startCamera(previewView, useFrontCamera = false)

        btnFlipCamera.setOnClickListener {
            cameraManager.flipCamera(previewView)
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun analyzeFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < SCAN_INTERVAL_MS) return
        lastAnalysisTime = now

        scope.launch {
            try {
                val result = faceDetector.detect(bitmap)

                withContext(Dispatchers.Main) {
                    val faceCount = result.faceCount
                    val conf = result.detections.firstOrNull()?.confidence ?: 0f

                    tvStatus.text = if (faceCount > 0) "FACE DETECTED" else "NO FACE"
                    tvStatus.setTextColor(
                        if (faceCount > 0) Color.parseColor("#00FF88")
                        else Color.parseColor("#FF4444")
                    )
                    tvFaceCount.text = "Faces: $faceCount"
                    tvConfidence.text = "Conf: ${"%.2f".format(conf)}"
                    tvInferenceTime.text = "${"%.0f".format(result.inferenceTimeMs)}ms"

                    // Draw overlay boxes
                    drawDetectionOverlay(result.detections.map { it.boundingBox })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Analysis error: ${e.message}")
            }
        }
    }

    private fun drawDetectionOverlay(boxes: List<Rect>) {
        overlayView.post {
            val canvas = Canvas()
            val bitmap = Bitmap.createBitmap(
                overlayView.width, overlayView.height,
                Bitmap.Config.ARGB_8888
            )
            canvas.setBitmap(bitmap)
            canvas.drawColor(Color.TRANSPARENT)

            val paint = Paint().apply {
                color = Color.parseColor("#00FF88")
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
            }

            // Scale bounding boxes from camera resolution to view size
            val scaleX = overlayView.width.toFloat() / previewView.width.toFloat()
            val scaleY = overlayView.height.toFloat() / previewView.height.toFloat()

            for (box in boxes) {
                val scaled = Rect(
                    (box.left * scaleX).toInt(),
                    (box.top * scaleY).toInt(),
                    (box.right * scaleX).toInt(),
                    (box.bottom * scaleY).toInt()
                )
                canvas.drawRect(scaled, paint)
            }

            overlayView.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cameraManager.shutdown()
        faceDetector.close()
        embedder?.close()
    }
}
