package com.watcher.app.camera

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.watcher.app.R

class CameraActivity : AppCompatActivity() {
    companion object { private const val TAG = "Watcher.CameraActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate start")
        setContentView(R.layout.activity_camera)
        findViewById<TextView>(R.id.tv_camera_message)
        Log.i(TAG, "onCreate done")
    }
}
