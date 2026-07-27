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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_camera)
            Log.i(TAG, "Layout inflated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Layout inflation failed", e)
            showErrorAndFinish("Layout error: ${e.message}")
            return
        }

        try {
            previewView = findViewById(R.id.camera_preview)
            overlayView = findViewById(R.id.detection_overlay)
            tvStatus = findViewById(R.id.tv_status)
            tvFaceCount = findViewById(R.id.tv_face_count)
            tvConfidence = findViewById(R.id.tv_confidence)
            tvInferenceTime = findViewById(R.id.tv_inference_time)
            btnFlipCamera = findViewById(R.id.btn_flip_camera)
            btnBack = findViewById(R.id.btn_back)
        } catch (e: Exception) {
            Log.e(TAG, "findViewById failed", e)
            showErrorAndFinish("View error: ${e.message}")
            return
        }

        btnBack?.setOnClickListener { finish() }
        btnFlipCamera?.setOnClickListener {
            cameraManager?.let { m -> previewView?.let { m.flipCamera(it) } }
        }

        tvStatus?.text = "INITIALIZING"
        tvStatus?.setTextColor(Color.parseColor("#CCCC00"))

        scope.launch { initializeCamera() }
    }

    private suspend fun initializeCamera() {
        // Permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            withContext(Dispatchers.Main) {
                tvStatus?.text = "NO PERMISSION"
                tvStatus?.setTextColor(Color.parseColor("#CC0000"))
                Toast.makeText(this@CameraActivity, "Camera permission required", Toast.LENGTH_LONG).show()
            }
            return
        }

        // Init FaceDetector
        try {
            Log.i(TAG, "Initializing FaceDetector...")
            faceDetector = FaceDetector(FaceDetector.defaultOptions())
            Log.i(TAG, "FaceDetector initialized")
        } catch (e: Exception) {
            Log.e(TAG, "FaceDetector init failed", e)
            withContext(Dispatchers.Main) {
                tvStatus?.text = "DETECTOR FAILED"
                tvStatus?.setTextColor(Color.parseColor("#CC0000"))
                showErrorDialog("FaceDetector failed: ${e.message}")
            }
            return
        }

        // Init camera on main thread
        withContext(Dispatchers.Main) {
            try {
                Log.i(TAG, "Initializing CameraManager...")
                cameraManager = CameraManager(this@CameraActivity)
                cameraManager?.onFrame = { bitmap -> analyzeFrame(bitmap) }
                previewView?.let { cameraManager?.startCamera(it, false) }
                tvStatus?.text = "READY"
                tvStatus?.setTextColor(Color.parseColor("#00FF88"))
                Log.i(TAG, "Camera started")
            } catch (e: Exception) {
                Log.e(TAG, "Camera init failed", e)
                tvStatus?.text = "CAMERA FAILED"
                tvStatus?.setTextColor(Color.parseColor("#CC0000"))
                showErrorDialog("Camera failed: ${e.message}")
            }
        }
    }

    private fun analyzeFrame(bitmap: Bitmap) {
        val detector = faceDetector ?: return
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
                val bitmap = Bitmap.createBitmap(ov.width, ov.height, Bitmap.Config.ARGB_8888)
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
                        (box.left * scaleX).toInt(), (box.top * scaleY).toInt(),
                        (box.right * scaleX).toInt(), (box.bottom * scaleY).toInt()
                    )
                    canvas.drawRect(scaled, paint)
                }
                ov.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "Overlay error: ${e.message}")
            }
        }
    }

    private fun showErrorDialog(msg: String) {
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
        }
    }

    private fun showErrorAndFinish(msg: String) {
        Log.e(TAG, msg)
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cameraManager?.shutdown()
        faceDetector?.close()
    }
}
