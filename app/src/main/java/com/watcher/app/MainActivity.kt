package com.watcher.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.watcher.app.camera.CameraActivity
import com.watcher.app.results.ResultsActivity
import com.watcher.app.settings.SettingsActivity
import com.watcher.app.testing.TestRunActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.tv_version).text = "Watcher v1.0.1-diagnostic"

        findViewById<Button>(R.id.btn_quick_scan).setOnClickListener {
            // DIAGNOSTIC: try launching Settings instead
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_start_test).setOnClickListener {
            // DIAGNOSTIC: try launching Settings instead
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_view_results).setOnClickListener {
            // DIAGNOSTIC: try launching Settings instead
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
