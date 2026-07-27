package com.watcher.app.results

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.watcher.app.R
import com.watcher.app.export.ReportExporter
import java.io.File

class ResultsActivity : AppCompatActivity() {

    companion object { private const val TAG = "Watcher.ResultsActivity" }

    private var resultsRecycler: RecyclerView? = null
    private var adapter: ResultsAdapter? = null
    private var tvEmpty: TextView? = null
    private var btnBack: Button? = null
    private var exporter: ReportExporter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_results)
            Log.i(TAG, "Layout inflated")
        } catch (e: Exception) {
            Log.e(TAG, "Layout inflation failed", e)
            showError("Layout error: ${e.message}")
            return
        }

        try {
            resultsRecycler = findViewById(R.id.results_list)
            tvEmpty = findViewById(R.id.tv_empty_results)
            btnBack = findViewById(R.id.btn_back_results)
        } catch (e: Exception) {
            Log.e(TAG, "findViewById failed", e)
            showError("View error: ${e.message}")
            return
        }

        exporter = ReportExporter(this)

        btnBack?.setOnClickListener { finish() }

        adapter = ResultsAdapter(
            onItemClick = { file -> openFile(file) },
            onDeleteClick = { file -> deleteFile(file) },
            onShareClick = { file -> shareFile(file) }
        )

        resultsRecycler?.layoutManager = LinearLayoutManager(this)
        resultsRecycler?.adapter = adapter

        loadResults()
    }

    private fun loadResults() {
        try {
            val files = exporter?.listExports() ?: emptyList()
            if (files.isEmpty()) {
                tvEmpty?.visibility = TextView.VISIBLE
                resultsRecycler?.visibility = TextView.GONE
            } else {
                tvEmpty?.visibility = TextView.GONE
                resultsRecycler?.visibility = TextView.VISIBLE
                adapter?.submitList(files)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadResults failed", e)
            tvEmpty?.text = "Error loading results"
            tvEmpty?.visibility = TextView.VISIBLE
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Log.e(TAG, "openFile failed", e)
            Toast.makeText(this, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share report"))
        } catch (e: Exception) {
            Log.e(TAG, "shareFile failed", e)
            Toast.makeText(this, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                file.delete()
                loadResults()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getMimeType(file: File): String = when {
        file.name.endsWith(".html") -> "text/html"
        file.name.endsWith(".json") -> "application/json"
        else -> "application/octet-stream"
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
}
