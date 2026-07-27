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

class TestRunActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Watcher.TestRunActivity"
        private const val DEFAULT_TRIALS = 50
    }

    private var previewView: PreviewView? = null
    private var tvPhase: TextView? = null
    private var tvProgress: TextView? = null
    private var tvStatus: TextView? = null
    private var tvFaceCount: TextView? = null
    private var tvConfidence: TextView? = null
    private var tvTrialCount: TextView? = null
    private var progressBar: ProgressBar? = null
    private var btnStart: Button? = null
    private var btnSelectPattern: Button? = null
    private var btnFlipCamera: ImageButton? = null
    private var btnBack: ImageButton? = null
    private var tvPatternInfo: TextView? = null
    private var personaInput: EditText? = null
    private var trialCountInput: EditText? = null
    private var layoutConfig: View? = null
    private var layoutTestRunning: View? = null

    private var cameraManager: CameraManager? = null
    private var faceDetector: FaceDetector? = null
    private var embedder: FaceNetEmbedder? = null
    private var patternOverlay: PatternOverlay? = null
    private var testSession: TestSession? = null
    private var reportExporter: ReportExporter? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var selectedPatternUri: Uri? = null
    private var baselineRun: TestRun? = null
    private var isRunningTest = false
    private var currentPhase = Phase.CONFIG

    enum class Phase { CONFIG, BASELINE, PATTERN_TEST, RESULTS }

    private val patternPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedPatternUri = it
            patternOverlay?.let { po ->
                if (po.loadPattern(contentResolver, it)) {
                    tvPatternInfo?.text = "Pattern loaded"
                    tvPatternInfo?.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_test_run)
            Log.i(TAG, "Layout inflated")
        } catch (e: Exception) {
            Log.e(TAG, "Layout inflation failed", e)
            showError("Layout error: ${e.message}")
            return
        }

        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "findViewById failed", e)
            showError("View error: ${e.message}")
            return
        }

        try {
            faceDetector = FaceDetector(FaceDetector.defaultOptions())
        } catch (e: Exception) {
            Log.e(TAG, "FaceDetector init failed", e)
            showError("Face detection failed: ${e.message}")
            return
        }

        try { embedder = FaceNetEmbedder(this) } catch (e: Exception) { Log.w(TAG, "FaceNet not available") }
        patternOverlay = PatternOverlay()
        testSession = TestSession(faceDetector!!, embedder, patternOverlay)
        reportExporter = ReportExporter(this)

        try {
            cameraManager = CameraManager(this)
            cameraManager?.onFrame = { bitmap ->
                if (isRunningTest) { processTestFrame(bitmap) }
            }
            cameraManager?.startCamera(previewView!!, false)
        } catch (e: Exception) {
            Log.e(TAG, "Camera init failed", e)
            showError("Camera failed: ${e.message}")
            return
        }

        btnSelectPattern?.setOnClickListener { patternPickerLauncher.launch("image/*") }
        btnFlipCamera?.setOnClickListener { cameraManager?.flipCamera(previewView!!) }
        btnBack?.setOnClickListener { finish() }
        btnStart?.setOnClickListener { handleStartClick() }
    }

    private fun handleStartClick() {
        if (currentPhase == Phase.CONFIG) {
            val persona = personaInput?.text?.toString()?.ifBlank { "default" } ?: "default"
            val trials = trialCountInput?.text?.toString()?.toIntOrNull() ?: DEFAULT_TRIALS
            currentPhase = Phase.BASELINE
            isRunningTest = true

            testSession?.configure(TestSession.SessionConfig(
                personaName = persona, totalTrials = trials, isBaseline = true
            ))

            layoutConfig?.visibility = View.GONE
            layoutTestRunning?.visibility = View.VISIBLE
            tvPhase?.text = "BASELINE"
            tvPhase?.setTextColor(Color.parseColor("#58A6FF"))
            btnStart?.text = "Running..."
            btnStart?.isEnabled = false
            progressBar?.max = trials
            progressBar?.progress = 0
            Log.i(TAG, "Baseline started: $persona, $trials trials")
        }
    }

    private fun processTestFrame(bitmap: Bitmap) {
        scope.launch {
            try {
                val trial = testSession?.recordTrial(bitmap) ?: return@launch
                withContext(Dispatchers.Main) {
                    tvTrialCount?.text = "Trial ${trial.trialNumber}/${testSession?.totalTrials}"
                    tvStatus?.text = if (trial.faceDetected) "DETECTED" else "NOT DETECTED"
                    tvStatus?.setTextColor(
                        if (trial.faceDetected) Color.parseColor("#00FF88") else Color.parseColor("#FF4444")
                    )
                    tvFaceCount?.text = "Faces: ${trial.faceCount}"
                    tvConfidence?.text = "Conf: ${"%.2f".format(trial.confidence)}"
                    tvProgress?.text = "${"%.0f".format((testSession?.progress ?: 0f) * 100)}%"
                    progressBar?.progress = trial.trialNumber

                    if (testSession?.isComplete == true) {
                        onPhaseComplete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Frame error: ${e.message}")
            }
        }
    }

    private fun onPhaseComplete() {
        isRunningTest = false
        if (currentPhase == Phase.BASELINE) {
            baselineRun = testSession?.buildTestRun()
            baselineRun?.let { reportExporter?.exportJson(it) }
            val dr = testSession?.getAggregate()?.detectionRate ?: 0.0
            AlertDialog.Builder(this)
                .setTitle("Baseline Complete")
                .setMessage("Detection Rate: ${"%.1f".format(dr * 100)}%\n\nSubject can now put on the garment.")
                .setPositiveButton("Start Pattern Test") { _, _ -> startPatternPhase() }
                .setNegativeButton("Cancel") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun startPatternPhase() {
        if (selectedPatternUri == null) {
            AlertDialog.Builder(this)
                .setTitle("No Pattern Selected")
                .setMessage("Test without a pattern image?")
                .setPositiveButton("Select Pattern") { _, _ -> patternPickerLauncher.launch("image/*") }
                .setNeutralButton("Test Without") { _, _ -> doPatternPhase(null) }
                .setNegativeButton("Cancel") { _, _ -> finish() }
                .show()
            return
        }
        val name = selectedPatternUri?.lastPathSegment ?: "pattern"
        patternOverlay?.loadPattern(contentResolver, selectedPatternUri!!)
        doPatternPhase(name)
    }

    private fun doPatternPhase(name: String?) {
        currentPhase = Phase.PATTERN_TEST
        isRunningTest = true
        testSession?.reset()
        val persona = personaInput?.text?.toString()?.ifBlank { "default" } ?: "default"
        val trials = trialCountInput?.text?.toString()?.toIntOrNull() ?: DEFAULT_TRIALS
        testSession?.configure(TestSession.SessionConfig(
            personaName = persona, totalTrials = trials, isBaseline = false,
            patternFileName = name
        ))
        tvPhase?.text = "PATTERN TEST"
        tvPhase?.setTextColor(Color.parseColor("#E94560"))
        progressBar?.progress = 0
        btnStart?.text = "Running..."
        btnStart?.isEnabled = false
    }

    private fun showError(msg: String) {
        Log.e(TAG, "Fatal: $msg")
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cameraManager?.shutdown()
        faceDetector?.close()
        embedder?.close()
    }
}
