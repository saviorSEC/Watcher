package com.watcher.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Manages CameraX lifecycle and provides frame capture callback.
 */
class CameraManager(private val lifecycleOwner: LifecycleOwner) {

    companion object {
        private const val TAG = "Watcher.Camera"
        private const val TARGET_ASPECT_RATIO = AspectRatio.RATIO_4_3
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    // Callback for frame analysis
    var onFrame: ((Bitmap) -> Unit)? = null

    /**
     * Start camera preview on a PreviewView.
     * @param previewView The view to display camera feed
     * @param useFrontCamera If true, use front-facing camera
     */
    fun startCamera(previewView: PreviewView, useFrontCamera: Boolean = false) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            currentCameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Preview
            preview = Preview.Builder()
                .setTargetAspectRatio(TARGET_ASPECT_RATIO)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // Image analysis for face detection
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(TARGET_ASPECT_RATIO)
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            // Bind to lifecycle
            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    currentCameraSelector,
                    preview,
                    imageAnalysis
                )
                Log.i(TAG, "Camera started: ${if (useFrontCamera) "front" else "back"}")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    /**
     * Process a single frame from ImageAnalysis.
     * Converts YUV_420_888 to Bitmap and passes to callback.
     */
    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = yuvToBitmap(imageProxy)
        bitmap?.let { bmp ->
            onFrame?.invoke(bmp)
        }
        imageProxy.close()
    }

    /**
     * Convert YUV_420_888 ImageProxy to a Bitmap.
     */
    private fun yuvToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer: ByteBuffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val yuvImage = YuvImage(bytes, ImageFormat.NV21,
                imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 85, out)
            val jpegBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Frame conversion failed: ${e.message}")
            null
        }
    }

    /**
     * Switch between front and back cameras.
     */
    fun flipCamera(previewView: PreviewView) {
        val useFront = currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA
        startCamera(previewView, useFront)
    }

    fun isFrontCamera(): Boolean {
        return currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
    }

    /**
     * Release camera resources.
     */
    fun shutdown() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        Log.i(TAG, "Camera shut down")
    }
}
