package com.watcher.app.detection

import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Structured result from a single face detection pass.
 * Serializable for JSON export of trial results.
 */
data class DetectionResult(
    val timestamp: Long,
    val faceCount: Int,
    val detections: List<SingleFaceResult>,
    val inferenceTimeMs: Double
)

data class SingleFaceResult(
    val boundingBox: Rect,
    val confidence: Float,
    val landmarks: Map<Int, LandmarkPoint>,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val headEulerAngleY: Float?,
    val headEulerAngleZ: Float?,
    val smilingProbability: Float?,
    val trackingId: Int?
)

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Result including embedding vector from FaceNet/ArcFace.
 */
data class RecognitionResult(
    val detected: Boolean,
    val confidence: Float,
    val boundingBox: Rect?,
    val embedding: FloatArray?,
    val embeddingL2Norm: Float?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecognitionResult) return false
        return detected == other.detected &&
                confidence == other.confidence &&
                boundingBox == other.boundingBox &&
                embeddingL2Norm == other.embeddingL2Norm
    }

    override fun hashCode(): Int {
        var result = detected.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + (boundingBox?.hashCode() ?: 0)
        result = 31 * result + (embeddingL2Norm?.hashCode() ?: 0)
        return result
    }

    fun embeddingToList(): List<Float>? {
        return embedding?.toList()
    }
}
