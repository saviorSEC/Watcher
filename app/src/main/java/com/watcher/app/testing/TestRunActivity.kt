package com.watcher.app.testing

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.watcher.app.R

class TestRunActivity : AppCompatActivity() {
    companion object { private const val TAG = "Watcher.TestRunActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate start")
        setContentView(R.layout.activity_test_run)
        findViewById<TextView>(R.id.tv_test_message)
        Log.i(TAG, "onCreate done")
    }
}
