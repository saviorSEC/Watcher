package com.watcher.app.models

import com.google.gson.GsonBuilder
import com.watcher.app.detection.DetectionResult
import com.watcher.app.detection.RecognitionResult

/**
 * Represents a complete test run — either a baseline or a pattern test.
 * Serialized to JSON for result export.
 */

data class TestRun(
    val id: String,
    val timestamp: Long,
    val personaName: String,
    val patternFileName: String?,
    val isBaseline: Boolean,
    val cameraType: String,
    val detectorModel: String,
    val settings: TestSettings,
    val trials: List<Trial>,
    val aggregate: AggregateMetrics
)

data class TestSettings(
    val totalTrials: Int,
    val distanceCm: Int,
    val lightingCondition: String,
    val angleDegrees: Int,
    val patternOverlayMode: String,
    val patternAlpha: Float
)

data class Trial(
    val trialNumber: Int,
    val timestamp: Long,
    val faceDetected: Boolean,
    val faceCount: Int,
    val confidence: Float,
    val boundingBoxArea: Float?,
    val inferenceTimeMs: Double,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val headYaw: Float?,
    val headRoll: Float?,
    val smileProb: Float?,
    val embeddingL2Norm: Float?
)

data class AggregateMetrics(
    val detectionRate: Double,
    val totalTrials: Int,
    val detectionCount: Int,
    val meanConfidence: Double,
    val maxConfidence: Double,
    val minConfidence: Double,
    val meanInferenceTimeMs: Double,
    val bboxAreaMean: Double,
    val bboxAreaStd: Double,
    val grade: String
) {
    companion object {
        fun grade(evasionRate: Double): String {
            return when {
                evasionRate > 0.95 -> "S"
                evasionRate > 0.80 -> "A"
                evasionRate > 0.60 -> "B"
                evasionRate > 0.40 -> "C"
                evasionRate > 0.20 -> "D"
                else -> "F"
            }
        }
    }
}

data class TestComparison(
    val testId: String,
    val baseline: TestRun?,
    val patternTest: TestRun,
    val perDetector: Map<String, EvasionMetrics>,
    val overall: EvasionMetrics
)

data class EvasionMetrics(
    val evasionRate: Double,
    val confidenceSuppression: Double,
    val grade: String,
    val baselineDr: Double,
    val testDr: Double,
    val baselineConf: Double,
    val testConf: Double
)

// JSON serialization helper
object TestRunSerializer {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun toJson(run: TestRun): String = gson.toJson(run)
    fun toJson(comparison: TestComparison): String = gson.toJson(comparison)
    fun fromJson(json: String): TestRun = gson.fromJson(json, TestRun::class.java)
}
