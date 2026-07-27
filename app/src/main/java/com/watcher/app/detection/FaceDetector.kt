package com.watcher.app.detection

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps Google ML Kit Face Detection for on-device face detection.
 *
 * Runs directly on the phone — no internet, no server needed.
 * Uses Google Play Services (pre-installed on most Android phones).
 */
class FaceDetector {

    companion object {
        private const val TAG = "Watcher.FaceDetector"
    }

    private val detector: com.google.mlkit.vision.face.FaceDetector

    constructor(options: FaceDetectorOptions = defaultOptions()) {
        detector = FaceDetection.getClient(options)
        Log.i(TAG, "ML Kit FaceDetector initialized")
    }

    /**
     * Detect faces in a bitmap frame.
     * This is the main detection method called from the camera analyzer.
     */
    suspend fun detect(bitmap: Bitmap): DetectionResult = withContext(Dispatchers.Default) {
        val startTime = System.nanoTime()
        val image = InputImage.fromBitmap(bitmap, 0)

        val faces: List<Face> = try {
            detector.process(image)
                .addOnFailureListener { e ->
                    Log.w(TAG, "Detection failed: ${e.message}")
                }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Detection error", e)
            emptyList()
        }

        val inferenceTimeMs = (System.nanoTime() - startTime) / 1_000_000.0

        val results = faces.map { face ->
            val landmarks = mutableMapOf<Int, LandmarkPoint>()
            // Extract available landmarks
            face.landmark(FaceLandmark.LEFT_EYE)?.let {
                landmarks[FaceLandmark.LEFT_EYE] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.RIGHT_EYE)?.let {
                landmarks[FaceLandmark.RIGHT_EYE] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.NOSE_BASE)?.let {
                landmarks[FaceLandmark.NOSE_BASE] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.MOUTH_LEFT)?.let {
                landmarks[FaceLandmark.MOUTH_LEFT] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.MOUTH_RIGHT)?.let {
                landmarks[FaceLandmark.MOUTH_RIGHT] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.MOUTH_BOTTOM)?.let {
                landmarks[FaceLandmark.MOUTH_BOTTOM] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.CHIN)?.let {
                landmarks[FaceLandmark.CHIN] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.LEFT_EAR)?.let {
                landmarks[FaceLandmark.LEFT_EAR] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.RIGHT_EAR)?.let {
                landmarks[FaceLandmark.RIGHT_EAR] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }
            face.landmark(FaceLandmark.NOSE_TIP)?.let {
                landmarks[FaceLandmark.NOSE_TIP] = LandmarkPoint(it.position.x, it.position.y, it.position.z)
            }

            SingleFaceResult(
                boundingBox = face.boundingBox,
                confidence = face.smilingProbability ?: 1.0f,
                landmarks = landmarks,
                leftEyeOpenProbability = face.leftEyeOpenProbability,
                rightEyeOpenProbability = face.rightEyeOpenProbability,
                headEulerAngleY = face.headEulerAngleY,
                headEulerAngleZ = face.headEulerAngleZ,
                smilingProbability = face.smilingProbability,
                trackingId = face.trackingId
            )
        }

        DetectionResult(
            timestamp = System.currentTimeMillis(),
            faceCount = results.size,
            detections = results,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    /**
     * Simple check: is a face visible in this frame?
     * Returns just a boolean + confidence for real-time display.
     */
    suspend fun quickCheck(bitmap: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {
        val result = detect(bitmap)
        if (result.faceCount > 0 && result.detections.isNotEmpty()) {
            val first = result.detections.first()
            RecognitionResult(
                detected = true,
                confidence = first.confidence,
                boundingBox = first.boundingBox,
                embedding = null,
                embeddingL2Norm = null
            )
        } else {
            RecognitionResult(
                detected = false,
                confidence = 0f,
                boundingBox = null,
                embedding = null,
                embeddingL2Norm = null
            )
        }
    }

    fun close() {
        detector.close()
        Log.i(TAG, "FaceDetector closed")
    }

    companion object {
        fun defaultOptions(): FaceDetectorOptions {
            return FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build()
        }

        fun accurateOptions(): FaceDetectorOptions {
            return FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .setMinFaceSize(0.15f)
                .build()
        }
    }
}
