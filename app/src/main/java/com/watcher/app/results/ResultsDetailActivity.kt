package com.watcher.app.results

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.watcher.app.R

/**
 * Detailed view of a single test result with charts and metrics.
 * Uses MPAndroidChart for visualizations.
 *
 * Shows:
 * - Detection rate over trials (line chart)
 * - Confidence distribution (bar chart)
 * - Evasion metrics if comparison available
 */
class ResultsDetailActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvDetectionRate: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvGrade: TextView
    private lateinit var tvEvasionRate: TextView
    private lateinit var tvPersona: TextView
    private lateinit var tvPattern: TextView
    private lateinit var tvTrials: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results_detail)

        tvTitle = findViewById(R.id.tv_detail_title)
        tvDetectionRate = findViewById(R.id.tv_detail_detection_rate)
        tvConfidence = findViewById(R.id.tv_detail_confidence)
        tvGrade = findViewById(R.id.tv_detail_grade)
        tvEvasionRate = findViewById(R.id.tv_detail_evasion)
        tvPersona = findViewById(R.id.tv_detail_persona)
        tvPattern = findViewById(R.id.tv_detail_pattern)
        tvTrials = findViewById(R.id.tv_detail_trials)

        val testId = intent.getStringExtra("test_id") ?: "Unknown"
        tvTitle.text = "Test $testId"

        // In a real implementation, load the JSON file and parse the TestRun
        // For now, this is a placeholder showing the structure
        loadResult(testId)
    }

    private fun loadResult(testId: String) {
        // TODO: Load from internal storage
        // val dir = File(filesDir, "Watcher/runs")
        // val file = File(dir, "test_$testId.json")
        // val run = TestRunSerializer.fromJson(file.readText())
        // Populate views with run data
    }
}
