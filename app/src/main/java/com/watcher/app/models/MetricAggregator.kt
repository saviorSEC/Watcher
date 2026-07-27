package com.watcher.app.models

import com.watcher.app.detection.DetectionResult
import kotlin.math.sqrt

/**
 * Computes aggregate metrics from a list of trial results.
 */
object MetricAggregator {

    fun aggregate(
        detectionResults: List<DetectionResult>,
        trials: Int
    ): AggregateMetrics {
        val faceCounts = detectionResults.map { it.faceCount }
        val confidences = detectionResults
            .filter { it.faceCount > 0 }
            .flatMap { it.detections.map { d -> d.confidence.toDouble() } }
        val inferenceTimes = detectionResults.map { it.inferenceTimeMs }
        val bboxAreas = detectionResults
            .filter { it.faceCount > 0 }
            .flatMap { r -> r.detections.map { d -> d.boundingBox.width() * d.boundingBox.height() } }

        val detectionCount = faceCounts.count { it > 0 }
        val detectionRate = if (trials > 0) detectionCount.toDouble() / trials else 0.0

        return AggregateMetrics(
            detectionRate = detectionRate,
            totalTrials = trials,
            detectionCount = detectionCount,
            meanConfidence = if (confidences.isEmpty()) 0.0 else confidences.sum() / confidences.size,
            maxConfidence = if (confidences.isEmpty()) 0.0 else confidences.maxOrNull() ?: 0.0,
            minConfidence = if (confidences.isEmpty()) 0.0 else confidences.minOrNull() ?: 0.0,
            meanInferenceTimeMs = if (inferenceTimes.isEmpty()) 0.0 else inferenceTimes.sum() / inferenceTimes.size,
            bboxAreaMean = if (bboxAreas.isEmpty()) 0.0 else bboxAreas.sum() / bboxAreas.size,
            bboxAreaStd = if (bboxAreas.size < 2) 0.0 else {
                val mean = bboxAreas.sum() / bboxAreas.size
                sqrt(bboxAreas.map { (it - mean) * (it - mean) }.sum() / (bboxAreas.size - 1))
            },
            grade = AggregateMetrics.grade(1.0 - detectionRate)
        )
    }

    /**
     * Compare baseline and pattern test results to compute evasion metrics.
     */
    fun compare(baseline: AggregateMetrics, test: AggregateMetrics): EvasionMetrics {
        val evasionRate = if (baseline.detectionRate > 0) {
            1.0 - (test.detectionRate / baseline.detectionRate)
        } else {
            0.0
        }

        val confidenceSuppression = if (baseline.meanConfidence > 0) {
            1.0 - (test.meanConfidence / baseline.meanConfidence)
        } else {
            0.0
        }

        return EvasionMetrics(
            evasionRate = evasionRate,
            confidenceSuppression = confidenceSuppression,
            grade = AggregateMetrics.grade(evasionRate),
            baselineDr = baseline.detectionRate,
            testDr = test.detectionRate,
            baselineConf = baseline.meanConfidence,
            testConf = test.meanConfidence
        )
    }
}
