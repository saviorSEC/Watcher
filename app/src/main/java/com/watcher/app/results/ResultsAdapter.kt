package com.watcher.app.results

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.watcher.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ResultsAdapter(
    private val onItemClick: (File) -> Unit = {},
    private val onDeleteClick: (File) -> Unit = {},
    private val onShareClick: (File) -> Unit = {}
) : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    private val items = mutableListOf<File>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val fileSizeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun submitList(files: List<File>) {
        items.clear()
        items.addAll(files)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        holder.bind(file)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_result_name)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_result_date)
        private val tvType: TextView = itemView.findViewById(R.id.tv_result_type)
        private val tvSize: TextView = itemView.findViewById(R.id.tv_result_size)
        private val btnShare: ImageButton = itemView.findViewById(R.id.btn_share_result)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_result)

        fun bind(file: File) {
            tvName.text = file.name

            // File type badge
            val type = when {
                file.name.endsWith(".json") -> "JSON"
                file.name.endsWith(".html") -> "HTML"
                else -> "FILE"
            }
            tvType.text = type

            // Date
            tvDate.text = dateFormat.format(Date(file.lastModified()))

            // Size
            tvSize.text = formatFileSize(file.length())

            // Click to open
            itemView.setOnClickListener { onItemClick(file) }

            btnShare.setOnClickListener { onShareClick(file) }
            btnDelete.setOnClickListener { onDeleteClick(file) }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
                else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            }
        }
    }
}
