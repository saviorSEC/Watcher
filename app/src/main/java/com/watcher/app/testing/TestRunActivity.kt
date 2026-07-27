package com.watcher.app.testing

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.watcher.app.R
import com.watcher.app.camera.CameraManager
import com.watcher.app.detection.FaceDetector
import com.watcher.app.detection.FaceNetEmbedder
import com.watcher.app.detection.PatternOverlay
import com.watcher.app.export.ReportExporter
import com.watcher.app.models.*
import kotlinx.coroutines.*

/**
 * Structured test session — runs N trials and compares baseline vs pattern.
 *
 * Flow:
 * 1. Configure session (persona name, trials, pattern)
 * 2. Run baseline (no pattern)
 * 3. Run pattern test (with shirt/garment pattern)
 * 4. Compare and report
 */
class TestRunActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Watcher.TestRunActivity"
        private const val DEFAULT_TRIALS = 50
    }

    // UI
    private lateinit var previewView: PreviewView
    private lateinit var tvPhase: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvFaceCount: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvTrialCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStart: Button
    private lateinit var btnSelectPattern: Button
    private lateinit var btnFlipCamera: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var tvPatternInfo: TextView
    private lateinit var personaInput: EditText
    private lateinit var trialCountInput: EditText
    private lateinit var layoutConfig: View
    private lateinit var layoutTestRunning: View

    // Core
    private lateinit var cameraManager: CameraManager
    private lateinit var faceDetector: FaceDetector
    private var embedder: FaceNetEmbedder? = null
    private lateinit var patternOverlay: PatternOverlay
    private lateinit var testSession: TestSession
    private lateinit var reportExporter: ReportExporter
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // State
    private var selectedPatternUri: Uri? = null
    private var baselineRun: TestRun? = null
    private var isRunningTest = false
    private var currentPhase = Phase.CONFIG

    enum class Phase {
        CONFIG,       // Setting up params
        BASELINE,     // Running baseline (no pattern)
        PATTERN_TEST, // Running with pattern
        RESULTS       // Showing results
    }

    private val patternPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedPatternUri = it
            val loaded = patternOverlay.loadPattern(contentResolver, it)
            tvPatternInfo.text = if (loaded) {
                "Pattern loaded ✓"
            } else {
                "Failed to load pattern"
            }
            tvPatternInfo.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_run)

        bindViews()
        initializeCore()

        btnSelectPattern.setOnClickListener {
            patternPickerLauncher.launch("image/*")
        }

        btnStart.setOnClickListener {
            if (currentPhase == Phase.CONFIG) {
                startBaselinePhase()
            } else if (currentPhase == Phase.BASELINE) {
                // Already running baseline, or completed
            } else if (currentPhase == Phase.RESULTS) {
                exportAndFinish()
            }
        }

        btnFlipCamera.setOnClickListener { cameraManager.flipCamera(previewView) }
        btnBack.setOnClickListener { finish() }
    }

    private fun bindViews() {
        previewView = findViewById(R.id.test_camera_preview)
        tvPhase = findViewById(R.id.tv_test_phase)
        tvProgress = findViewById(R.id.tv_test_progress)
        tvStatus = findViewById(R.id.tv_test_status)
        tvFaceCount = findViewById(R.id.tv_test_face_count)
        tvConfidence = findViewById(R.id.tv_test_confidence)
        tvTrialCount = findViewById(R.id.tv_trial_count)
        progressBar = findViewById(R.id.test_progress_bar)
        btnStart = findViewById(R.id.btn_test_action)
        btnSelectPattern = findViewById(R.id.btn_select_pattern)
        btnFlipCamera = findViewById(R.id.btn_flip_camera)
        btnBack = findViewById(R.id.btn_back)
        tvPatternInfo = findViewById(R.id.tv_pattern_info)
        personaInput = findViewById(R.id.input_persona_name)
        trialCountInput = findViewById(R.id.input_trial_count)
        layoutConfig = findViewById(R.id.layout_config)
        layoutTestRunning = findViewById(R.id.layout_test_running)
    }

    private fun initializeCore() {
        try {
            faceDetector = FaceDetector(FaceDetector.defaultOptions())
        } catch (e: Exception) {
            Log.e(TAG, "FaceDetector init failed", e)
            Toast.makeText(this, "Face detection failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try {
            embedder = FaceNetEmbedder(this)
        } catch (e: Exception) {
            Log.w(TAG, "FaceNet not available")
        }
        patternOverlay = PatternOverlay()
        testSession = TestSession(faceDetector, embedder, patternOverlay)
        reportExporter = ReportExporter(this)

        cameraManager = CameraManager(this)
        cameraManager.onFrame = { bitmap ->
            if (isRunningTest) {
                processTestFrame(bitmap)
            }
        }
        try {
            cameraManager.startCamera(previewView, useFrontCamera = false)
        } catch (e: Exception) {
            Log.e(TAG, "Camera start failed", e)
            Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startBaselinePhase() {
        val persona = personaInput.text.toString().ifBlank { "default" }
        val trials = trialCountInput.text.toString().toIntOrNull() ?: DEFAULT_TRIALS

        currentPhase = Phase.BASELINE
        isRunningTest = true

        testSession.configure(TestSession.SessionConfig(
            personaName = persona,
            totalTrials = trials,
            isBaseline = true,
            patternFileName = null
        ))

        layoutConfig.visibility = View.GONE
        layoutTestRunning.visibility = View.VISIBLE
        tvPhase.text = "BASELINE — No Pattern"
        tvPhase.setTextColor(Color.parseColor("#58A6FF"))
        btnStart.text = "Running..."
        btnStart.isEnabled = false
        trialCountInput.setText("")

        progressBar.max = trials
        progressBar.progress = 0

        Log.i(TAG, "Starting baseline: $persona, $trials trials")
    }

    private fun processTestFrame(bitmap: Bitmap) {
        scope.launch {
            try {
                val trial = testSession.recordTrial(bitmap)

                withContext(Dispatchers.Main) {
                    updateUI(trial)

                    if (testSession.isComplete) {
                        onPhaseComplete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Frame processing error: ${e.message}")
            }
        }
    }

    private fun updateUI(trial: com.watcher.app.models.Trial) {
        tvTrialCount.text = "Trial ${trial.trialNumber}/${testSession.totalTrials}"
        tvStatus.text = if (trial.faceDetected) "DETECTED" else "NOT DETECTED"
        tvStatus.setTextColor(
            if (trial.faceDetected) Color.parseColor("#00FF88")
            else Color.parseColor("#FF4444")
        )
        tvFaceCount.text = "Faces: ${trial.faceCount}"
        tvConfidence.text = "Conf: ${"%.2f".format(trial.confidence)}"
        tvProgress.text = "${"%.0f".format(testSession.progress * 100)}%"
        progressBar.progress = trial.trialNumber
    }

    private fun onPhaseComplete() {
        if (currentPhase == Phase.BASELINE) {
            // Baseline done — save and prompt for pattern test
            baselineRun = testSession.buildTestRun()
            baselineRun?.let { reportExporter.exportJson(it) }
            Log.i(TAG, "Baseline complete: ${testSession.getAggregate().detectionRate}")

            AlertDialog.Builder(this)
                .setTitle("Baseline Complete")
                .setMessage("Detection Rate: ${"%.1f".format(testSession.getAggregate().detectionRate * 100)}%\n\nNow run the pattern test. Have the subject put on the shirt/pattern.")
                .setPositiveButton("Start Pattern Test") { _, _ -> startPatternPhase() }
                .setNegativeButton("Cancel") { _, _ -> finishTest() }
                .setCancelable(false)
                .show()

        } else if (currentPhase == Phase.PATTERN_TEST) {
            // Pattern test done — show results
            val patternRun = testSession.buildTestRun()
            val comparison = baselineRun?.let { baseline ->
                val agg = MetricAggregator.compare(baseline.aggregate, patternRun.aggregate)
                TestComparison(
                    testId = patternRun.id,
                    baseline = baseline,
                    patternTest = patternRun,
                    perDetector = mapOf("mlkit_face" to agg),
                    overall = agg
                )
            }

            tvPhase.text = "RESULTS"
            tvPhase.setTextColor(Color.parseColor("#FFD700"))
            btnStart.text = "Export Report"
            btnStart.isEnabled = true
            currentPhase = Phase.RESULTS
            isRunningTest = false

            if (comparison != null) {
                reportExporter.exportComparisonJson(comparison)
                reportExporter.exportHtmlReport(comparison)
                showResultDialog(comparison)
            }
        }
    }

    private fun startPatternPhase() {
        if (selectedPatternUri == null) {
            // No pattern selected — offer to pick one or run without
            AlertDialog.Builder(this)
                .setTitle("No Pattern Selected")
                .setMessage("You can test with or without a pattern overlay.")
                .setPositiveButton("Select Pattern") { _, _ ->
                    patternPickerLauncher.launch("image/*")
                }
                .setNeutralButton("Test Without Pattern") { _, _ ->
                    patternOverlay.clearPattern()
                    doPatternPhase(null)
                }
                .setNegativeButton("Cancel") { _, _ -> finishTest() }
                .show()
            return
        }

        val patternFileName = selectedPatternUri?.lastPathSegment ?: "pattern"
        val path = selectedPatternUri.toString()
        patternOverlay.loadPattern(contentResolver, selectedPatternUri!!)
        doPatternPhase(patternFileName)
    }

    private fun doPatternPhase(patternFileName: String?) {
        currentPhase = Phase.PATTERN_TEST
        isRunningTest = true
        testSession.reset()

        val persona = personaInput.text.toString().ifBlank { "default" }
        val trials = trialCountInput.text.toString().toIntOrNull() ?: DEFAULT_TRIALS

        testSession.configure(TestSession.SessionConfig(
            personaName = persona,
            totalTrials = trials,
            isBaseline = false,
            patternFileName = patternFileName,
            patternOverlayMode = "full_frame",
            patternAlpha = 0.6f
        ))

        tvPhase.text = "PATTERN TEST"
        tvPhase.setTextColor(Color.parseColor("#E94560"))
        progressBar.progress = 0
        btnStart.text = "Running..."
        btnStart.isEnabled = false

        Log.i(TAG, "Starting pattern test: $persona, $trials trials, pattern=$patternFileName")
    }

    private fun showResultDialog(comparison: TestComparison) {
        val ev = comparison.overall
        val grade = ev.grade
        val gradeColor = when (grade) {
            "S" -> "#FFD700"
            "A" -> "#00CC00"
            "B" -> "#66CC00"
            "C" -> "#CCCC00"
            "D" -> "#CC6600"
            "F" -> "#CC0000"
            else -> "#C9D1D9"
        }

        val message = """
            Grade: $grade
            
            Evasion Rate: ${"%.1f".format(ev.evasionRate * 100)}%
            Baseline DR: ${"%.1f".format(ev.baselineDr * 100)}%
            Pattern Test DR: ${"%.1f".format(ev.testDr * 100)}%
            Confidence Suppression: ${"%.1f".format(ev.confidenceSuppression * 100)}%
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Test Complete")
            .setMessage(message)
            .setPositiveButton("Export Report") { _, _ -> exportAndFinish() }
            .setNegativeButton("Close") { _, _ -> finishTest() }
            .setCancelable(false)
            .show()
    }

    private fun exportAndFinish() {
        Toast.makeText(this, "Report saved to Documents/Watcher/", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun finishTest() {
        isRunningTest = false
        currentPhase = Phase.RESULTS
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cameraManager.shutdown()
        faceDetector.close()
        embedder?.close()
    }
}
