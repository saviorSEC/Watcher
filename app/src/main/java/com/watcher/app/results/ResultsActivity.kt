package com.watcher.app.results

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.watcher.app.R

class ResultsActivity : AppCompatActivity() {
    companion object { private const val TAG = "Watcher.ResultsActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate start")
        setContentView(R.layout.activity_results)
        findViewById<TextView>(R.id.tv_results_message)
        Log.i(TAG, "onCreate done")
    }
}
