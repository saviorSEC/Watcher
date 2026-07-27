package com.watcher.app.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.watcher.app.R

/**
 * Settings for configuring test parameters.
 *
 * - Detector model selection (ML Kit Accuracy vs Speed)
 * - Default trials per test
 * - Save frames toggle
 * - Auto-export toggle
 * - Camera selection
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    // Detector speed/accuracy selector
    private lateinit var detectorGroup: RadioGroup
    private lateinit var rbFast: RadioButton
    private lateinit var rbAccurate: RadioButton

    // Camera selector
    private lateinit var cameraGroup: RadioGroup
    private lateinit var rbFront: RadioButton
    private lateinit var rbBack: RadioButton

    // Trials slider
    private lateinit var trialSlider: SeekBar
    private lateinit var tvTrialValue: TextView

    // Toggles
    private lateinit var switchSaveFrames: Switch
    private lateinit var switchAutoExport: Switch

    // Persona management
    private lateinit var etDefaultPersona: EditText

    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("watcher_prefs", MODE_PRIVATE)

        bindViews()
        loadSettings()
        setupListeners()
    }

    private fun bindViews() {
        detectorGroup = findViewById(R.id.radio_detector_group)
        rbFast = findViewById(R.id.radio_detector_fast)
        rbAccurate = findViewById(R.id.radio_detector_accurate)

        cameraGroup = findViewById(R.id.radio_camera_group)
        rbFront = findViewById(R.id.radio_camera_front)
        rbBack = findViewById(R.id.radio_camera_back)

        trialSlider = findViewById(R.id.slider_trials)
        tvTrialValue = findViewById(R.id.tv_trials_value)

        switchSaveFrames = findViewById(R.id.switch_save_frames)
        switchAutoExport = findViewById(R.id.switch_auto_export)
        etDefaultPersona = findViewById(R.id.et_default_persona)

        btnSave = findViewById(R.id.btn_save_settings)
        btnBack = findViewById(R.id.btn_back_settings)
    }

    private fun loadSettings() {
        // Detector mode
        val fastDetector = prefs.getBoolean("fast_detector", true)
        if (fastDetector) rbFast.isChecked = true else rbAccurate.isChecked = true

        // Camera
        val frontCamera = prefs.getBoolean("front_camera", false)
        if (frontCamera) rbFront.isChecked = true else rbBack.isChecked = true

        // Trials
        val trials = prefs.getInt("default_trials", 50)
        trialSlider.progress = trials / 10
        tvTrialValue.text = "$trials"

        // Toggles
        switchSaveFrames.isChecked = prefs.getBoolean("save_frames", false)
        switchAutoExport.isChecked = prefs.getBoolean("auto_export", true)
        etDefaultPersona.setText(prefs.getString("default_persona", ""))
    }

    private fun setupListeners() {
        trialSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress * 10
                tvTrialValue.text = "$value"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putBoolean("fast_detector", rbFast.isChecked)
            putBoolean("front_camera", rbFront.isChecked)
            putInt("default_trials", trialSlider.progress * 10)
            putBoolean("save_frames", switchSaveFrames.isChecked)
            putBoolean("auto_export", switchAutoExport.isChecked)
            putString("default_persona", etDefaultPersona.text.toString())
            apply()
        }
    }
}
