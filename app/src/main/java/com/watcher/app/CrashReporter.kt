package com.watcher.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Catches uncaught exceptions and writes them to a file on the device
 * so the user can share the crash log without needing adb.
 */
class CrashReporter(private val context: Context) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "Watcher.CrashReporter"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

        fun install(context: Context) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current !is CrashReporter) {
                Thread.setDefaultUncaughtExceptionHandler(CrashReporter(context))
                Log.i(TAG, "CrashReporter installed")
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val logDir = File(context.filesDir, "Watcher/crashes")
        logDir.mkdirs()

        val timestamp = DATE_FORMAT.format(Date())
        val crashFile = File(logDir, "crash_$timestamp.txt")

        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println("=== CRASH REPORT ===")
            pw.println("Time: $timestamp")
            pw.println("Thread: ${thread.name}")
            pw.println("Device: ${android.os.Build.MODEL} (${android.os.Build.VERSION.SDK_INT})")
            pw.println()
            throwable.printStackTrace(pw)
            pw.flush()

            crashFile.writeText(sw.toString())
            Log.e(TAG, "Crash written to ${crashFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        }

        // Get the original handler to let the OS handle the crash normally
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev != null && prev != this) {
            prev.uncaughtException(thread, throwable)
        }
    }
}
