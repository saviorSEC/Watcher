package com.watcher.app.results

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

/**
 * Shows all exported test results and allows sharing/opening them.
 */
class ResultsActivity : AppCompatActivity() {

    private lateinit var resultsRecycler: RecyclerView
    private lateinit var adapter: ResultsAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: Button
    private lateinit var exporter: ReportExporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        resultsRecycler = findViewById(R.id.results_list)
        tvEmpty = findViewById(R.id.tv_empty_results)
        btnBack = findViewById(R.id.btn_back_results)
        exporter = ReportExporter(this)

        btnBack.setOnClickListener { finish() }

        adapter = ResultsAdapter(
            onItemClick = { file -> openFile(file) },
            onDeleteClick = { file -> deleteFile(file) },
            onShareClick = { file -> shareFile(file) }
        )

        resultsRecycler.layoutManager = LinearLayoutManager(this)
        resultsRecycler.adapter = adapter

        loadResults()
    }

    private fun loadResults() {
        val files = exporter.listExports()
        if (files.isEmpty()) {
            tvEmpty.visibility = TextView.VISIBLE
            resultsRecycler.visibility = TextView.GONE
        } else {
            tvEmpty.visibility = TextView.GONE
            resultsRecycler.visibility = TextView.VISIBLE
            adapter.submitList(files)
        }
    }

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open with"))
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share report"))
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

    private fun getMimeType(file: File): String {
        return when {
            file.name.endsWith(".html") -> "text/html"
            file.name.endsWith(".json") -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
