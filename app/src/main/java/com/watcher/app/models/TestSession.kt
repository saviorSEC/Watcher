package com.watcher.app.models

import android.util.Log
import com.watcher.app.detection.DetectionResult
import com.watcher.app.detection.FaceDetector
import com.watcher.app.detection.FaceNetEmbedder
import com.watcher.app.detection.PatternOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages a complete test session lifecycle.
 *
 * Handles trial execution, metric collection, and aggregation
 * for both baseline and pattern-test modes.
 */
class TestSession(
    private val faceDetector: FaceDetector,
    private val embedder: FaceNetEmbedder?,
    private val patternOverlay: PatternOverlay?
) {

    companion object {
        private const val TAG = "Watcher.TestSession"
    }

    data class SessionConfig(
        val personaName: String = "default",
        val totalTrials: Int = 50,
        val isBaseline: Boolean = true,
        val patternFileName: String? = null,
        val distanceCm: Int = 100,
        val lightingCondition: String = "ambient",
        val angleDegrees: Int = 0,
        val patternOverlayMode: String = "full_frame",
        val patternAlpha: Float = 0.6f,
        val detectorModel: String = "mlkit_face"
    )

    private var config = SessionConfig()
    private val results = mutableListOf<DetectionResult>()
    private var currentTrial = 0
    private var isRunning = false
    private var baselineAggregate: AggregateMetrics? = null

    fun configure(cfg: SessionConfig) {
        config = cfg
        Log.i(TAG, "Session configured: ${cfg.totalTrials} trials, baseline=${cfg.isBaseline}")
    }

    suspend fun recordTrial(bitmap: android.graphics.Bitmap): Trial {
        currentTrial++
        val frameToAnalyze = if (patternOverlay?.hasPattern() == true && !config.isBaseline) {
            // Apply pattern overlay to frame before detection
            patternOverlay.applyOverlay(bitmap, emptyList())
        } else {
            bitmap
        }

        val detectionResult = faceDetector.detect(frameToAnalyze)
        results.add(detectionResult)

        val face = detectionResult.detections.firstOrNull()

        // Compute embedding if embedder available and face detected
        var embeddingNorm: Float? = null
        if (embedder != null && face != null) {
            try {
                val faceCrop = Bitmap.createBitmap(
                    frameToAnalyze,
                    face.boundingBox.left.coerceAtLeast(0),
                    face.boundingBox.top.coerceAtLeast(0),
                    face.boundingBox.width().coerceAtMost(frameToAnalyze.width - face.boundingBox.left),
                    face.boundingBox.height().coerceAtMost(frameToAnalyze.height - face.boundingBox.top)
                )
                val emb = embedder.getEmbedding(faceCrop)
                embeddingNorm = emb?.let { sqrt(it.sumOf { v -> (v * v).toDouble() }).toFloat() }
            } catch (e: Exception) {
                Log.w(TAG, "Embedding extraction failed for trial $currentTrial")
            }
        }

        return Trial(
            trialNumber = currentTrial,
            timestamp = detectionResult.timestamp,
            faceDetected = detectionResult.faceCount > 0,
            faceCount = detectionResult.faceCount,
            confidence = face?.confidence ?: 0f,
            boundingBoxArea = face?.let { it.boundingBox.width() * it.boundingBox.height() }?.toFloat(),
            inferenceTimeMs = detectionResult.inferenceTimeMs,
            leftEyeOpen = face?.leftEyeOpenProbability,
            rightEyeOpen = face?.rightEyeOpenProbability,
            headYaw = face?.headEulerAngleY,
            headRoll = face?.headEulerAngleZ,
            smileProb = face?.smilingProbability,
            embeddingL2Norm = embeddingNorm
        )
    }

    fun getAggregate(): AggregateMetrics {
        return MetricAggregator.aggregate(results, config.totalTrials)
    }

    fun setBaseline(aggregate: AggregateMetrics) {
        baselineAggregate = aggregate
        Log.i(TAG, "Baseline set: DR=${aggregate.detectionRate}, Conf=${aggregate.meanConfidence}")
    }

    fun compareWithBaseline(): EvasionMetrics? {
        val baseline = baselineAggregate ?: return null
        val test = getAggregate()
        return MetricAggregator.compare(baseline, test)
    }

    fun getResults(): List<DetectionResult> = results.toList()

    fun buildTestRun(): TestRun {
        val agg = getAggregate()
        return TestRun(
            id = UUID.randomUUID().toString().take(8),
            timestamp = System.currentTimeMillis(),
            personaName = config.personaName,
            patternFileName = config.patternFileName,
            isBaseline = config.isBaseline,
            cameraType = "android_phone",
            detectorModel = config.detectorModel,
            settings = TestSettings(
                totalTrials = config.totalTrials,
                distanceCm = config.distanceCm,
                lightingCondition = config.lightingCondition,
                angleDegrees = config.angleDegrees,
                patternOverlayMode = config.patternOverlayMode,
                patternAlpha = config.patternAlpha
            ),
            trials = results.mapIndexed { idx, dr ->
                val face = dr.detections.firstOrNull()
                Trial(
                    trialNumber = idx + 1,
                    timestamp = dr.timestamp,
                    faceDetected = dr.faceCount > 0,
                    faceCount = dr.faceCount,
                    confidence = face?.confidence ?: 0f,
                    boundingBoxArea = face?.let { it.boundingBox.width() * it.boundingBox.height() }?.toFloat(),
                    inferenceTimeMs = dr.inferenceTimeMs,
                    leftEyeOpen = face?.leftEyeOpenProbability,
                    rightEyeOpen = face?.rightEyeOpenProbability,
                    headYaw = face?.headEulerAngleY,
                    headRoll = face?.headEulerAngleZ,
                    smileProb = face?.smilingProbability,
                    embeddingL2Norm = null
                )
            },
            aggregate = agg
        )
    }

    val progress: Float
        get() = if (config.totalTrials > 0) {
            currentTrial.toFloat() / config.totalTrials
        } else 0f

    val isComplete: Boolean
        get() = currentTrial >= config.totalTrials

    val currentTrialNumber: Int get() = currentTrial
    val totalTrials: Int get() = config.totalTrials

    fun reset() {
        results.clear()
        currentTrial = 0
    }

    private fun sqrt(value: Double): Float = kotlin.math.sqrt(value).toFloat()
}
