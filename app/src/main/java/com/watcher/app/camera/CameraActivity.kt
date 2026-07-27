package com.watcher.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.watcher.app.R
import com.watcher.app.detection.FaceDetector
import kotlinx.coroutines.*

/**
 * Quick Scan mode -- live camera with real-time face detection overlay.
 *
 * Handles camera permission, FaceDetector init, and lifecycle gracefully
 * so it never crashes with a blank screen -- always shows a status message.
 */
class CameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Watcher.CameraActivity"
        private const val SCAN_INTERVAL_MS = 150L
    }

    private var previewView: PreviewView? = null
    private var overlayView: View? = null
    private var cameraManager: CameraManager? = null
    private var faceDetector: FaceDetector? = null

    private var tvStatus: TextView? = null
    private var tvFaceCount: TextView? = null
    private var tvConfidence: TextView? = null
    private var tvInferenceTime: TextView? = null
    private var btnFlipCamera: ImageButton? = null
    private var btnBack: ImageButton? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastAnalysisTime = 0L
    private var initError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        Log.i(TAG, "CameraActivity onCreate")

        previewView = findViewById(R.id.camera_preview)
        overlayView = findViewById(R.id.detection_overlay)
        tvStatus = findViewById(R.id.tv_status)
        tvFaceCount = findViewById(R.id.tv_face_count)
        tvConfidence = findViewById(R.id.tv_confidence)
        tvInferenceTime = findViewById(R.id.tv_inference_time)
        btnFlipCamera = findViewById(R.id.btn_flip_camera)
        btnBack = findViewById(R.id.btn_back)

        btnBack?.setOnClickListener { finish() }
        btnFlipCamera?.setOnClickListener {
            cameraManager?.let { m ->
                previewView?.let { m.flipCamera(it) }
            }
        }

        // Show initializing status
        tvStatus?.text = "INITIALIZING"
        tvStatus?.setTextColor(Color.parseColor("#CCCC00"))

        // Kick off async init
        scope.launch {
            initializeCamera()
        }
    }

    private suspend fun initializeCamera() {
        // Check permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            withContext(Dispatchers.Main) {
                initError = "Camera permission not granted"
                tvStatus?.text = "NO PERMISSION"
                tvStatus?.setTextColor(Color.parseColor("#CC0000"))
                Toast.makeText(this@CameraActivity,
                    "Camera permission required", Toast.LENGTH_LONG).show()
            }
            return
        }

        // Initialize face detector on background thread
        try {
            Log.i(TAG, "Initializing FaceDetector...")
            val detector = FaceDetector(FaceDetector.defaultOptions())
            faceDetector = detector
            Log.i(TAG, "FaceDetector initialized")
        } catch (e: Exception) {
            Log.e(TAG, "FaceDetector init failed", e)
            withContext(Dispatchers.Main) {
                initError = "FaceDetector: ${e.message}"
                tvStatus?.text = "DETECTOR FAILED"
                tvStatus?.setTextColor(Color.parseColor("#CC0000"))
                Toast.makeText(this@CameraActivity,
                    "Face detection model failed to load: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
            return
        }

        // Initialize camera manager on main thread
        withContext(Dispatchers.Main) {
            try {
                Log.i(TAG, "Initializing CameraManager...")
                cameraManager = CameraManager(this@CameraActivity)
                cameraManager?.onFrame = { bitmap ->
                    analyzeFrame(bitmap)
                }

                previewView?.let { preview ->
                    cameraManager?.startCamera(preview, useFrontCamera = false)
                }

                tvStatus?.text = "READY"
                tvStatus?.setTextColor(Color.parseColor("#00FF88"))
                Log.i(TAG, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera init failed", e)
                initError = "Camera: ${e.message}"
                tvStatus?.text = "CAMERA FAILED"
                tvStatus?.setTextColor(Color.parseColor("#CC0000"))
                Toast.makeText(this@CameraActivity,
                    "Camera failed: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun analyzeFrame(bitmap: Bitmap) {
        val detector = faceDetector ?: return
        if (initError != null) return

        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < SCAN_INTERVAL_MS) return
        lastAnalysisTime = now

        scope.launch {
            try {
                val result = detector.detect(bitmap)

                withContext(Dispatchers.Main) {
                    val faceCount = result.faceCount
                    val conf = result.detections.firstOrNull()?.confidence ?: 0f

                    tvStatus?.text = if (faceCount > 0) "FACE DETECTED" else "NO FACE"
                    tvStatus?.setTextColor(
                        if (faceCount > 0) Color.parseColor("#00FF88")
                        else Color.parseColor("#FF4444")
                    )
                    tvFaceCount?.text = "Faces: $faceCount"
                    tvConfidence?.text = "Conf: ${"%.2f".format(conf)}"
                    tvInferenceTime?.text = "${"%.0f".format(result.inferenceTimeMs)}ms"

                    drawDetectionOverlay(result.detections.map { it.boundingBox })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Analysis error: ${e.message}")
            }
        }
    }

    private fun drawDetectionOverlay(boxes: List<Rect>) {
        val ov = overlayView ?: return
        val pv = previewView ?: return

        if (ov.width <= 0 || ov.height <= 0) return
        if (pv.width <= 0 || pv.height <= 0) return

        ov.post {
            try {
                val canvas = Canvas()
                val bitmap = Bitmap.createBitmap(
                    ov.width, ov.height,
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

                val scaleX = ov.width.toFloat() / pv.width.toFloat()
                val scaleY = ov.height.toFloat() / pv.height.toFloat()

                for (box in boxes) {
                    val scaled = Rect(
                        (box.left * scaleX).toInt(),
                        (box.top * scaleY).toInt(),
                        (box.right * scaleX).toInt(),
                        (box.bottom * scaleY).toInt()
                    )
                    canvas.drawRect(scaled, paint)
                }

                ov.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "Overlay draw error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cameraManager?.shutdown()
        faceDetector?.close()
    }
}
