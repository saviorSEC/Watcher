package com.watcher.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.watcher.app.camera.CameraActivity
import com.watcher.app.results.ResultsActivity
import com.watcher.app.results.ResultsAdapter
import com.watcher.app.settings.SettingsActivity
import com.watcher.app.testing.TestRunActivity
import java.io.File

/**
 * Main dashboard — entry point for the Watcher app.
 *
 * Quick Scan: Opens camera with real-time detection overlay (no recording)
 * Start Test: Runs a structured test with baseline + pattern comparison
 * View Results: Shows past test results and exports
 */
class MainActivity : AppCompatActivity() {

    private lateinit var btnQuickScan: Button
    private lateinit var btnStartTest: Button
    private lateinit var btnViewResults: Button
    private lateinit var btnSettings: Button
    private lateinit var tvVersion: TextView
    private lateinit var recentResultsRecycler: RecyclerView
    private lateinit var resultsAdapter: ResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnQuickScan = findViewById(R.id.btn_quick_scan)
        btnStartTest = findViewById(R.id.btn_start_test)
        btnViewResults = findViewById(R.id.btn_view_results)
        btnSettings = findViewById(R.id.btn_settings)
        tvVersion = findViewById(R.id.tv_version)
        recentResultsRecycler = findViewById(R.id.recent_results_list)

        tvVersion.text = "Watcher v1.0.0"

        checkCameraPermission()

        btnQuickScan.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        btnStartTest.setOnClickListener {
            startActivity(Intent(this, TestRunActivity::class.java))
        }

        btnViewResults.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupRecentResultsList()
    }

    private fun setupRecentResultsList() {
        resultsAdapter = ResultsAdapter(
            onItemClick = { run ->
                val intent = Intent(this, ResultsActivity::class.java)
                intent.putExtra("test_id", run.id)
                startActivity(intent)
            }
        )
        recentResultsRecycler.layoutManager = LinearLayoutManager(this)
        recentResultsRecycler.adapter = resultsAdapter
    }

    override fun onResume() {
        super.onResume()
        refreshRecentResults()
    }

    private fun refreshRecentResults() {
        // Load recent runs from internal storage
        val dir = File(filesDir, "Watcher/runs")
        if (dir.exists()) {
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() }?.take(5)
            // Would parse JSON files here
            // resultsAdapter.submitList(parsedRuns)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Show warning but don't block — user can still use non-camera features
            btnQuickScan.isEnabled = false
            btnStartTest.isEnabled = false
            btnQuickScan.text = "Camera Permission Required"
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show rationale then request
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}
