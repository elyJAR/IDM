package com.fastdl.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DownloadAdapter : ListAdapter<DownloadEntity, DownloadAdapter.DownloadViewHolder>(DiffCallback) {

    class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val filenameText: TextView = itemView.findViewById(R.id.filenameText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val progressText: TextView = itemView.findViewById(R.id.progressText)
        private val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)

        fun bind(download: DownloadEntity) {
            val context = itemView.context
            filenameText.text = download.filename
            statusText.text = download.status

            when (download.status) {
                "COMPLETED" -> statusText.setTextColor(android.graphics.Color.parseColor("#22c55e")) // Green
                "FAILED" -> statusText.setTextColor(android.graphics.Color.parseColor("#ef4444")) // Red
                "PAUSED" -> statusText.setTextColor(android.graphics.Color.parseColor("#eab308")) // Yellow
                else -> statusText.setTextColor(android.graphics.Color.parseColor("#3b82f6")) // Blue
            }

            if (download.totalBytes > 0) {
                progressBar.isIndeterminate = false
                progressBar.max = 100
                val progressPercent = ((download.downloadedBytes.toDouble() / download.totalBytes) * 100).toInt()
                progressBar.progress = progressPercent

                val downloadedMB = String.format("%.1f", download.downloadedBytes.toDouble() / (1024 * 1024))
                val totalMB = String.format("%.1f", download.totalBytes.toDouble() / (1024 * 1024))
                progressText.text = "${downloadedMB}MB / ${totalMB}MB ($progressPercent%)"
            } else {
                progressBar.isIndeterminate = true
                progressText.text = "Computing size..."
            }

            menuButton.setOnClickListener { view ->
                showPopupMenu(context, view, download)
            }
        }

        private fun showPopupMenu(context: Context, anchor: View, download: DownloadEntity) {
            val popup = PopupMenu(context, anchor)

            if (download.status == "DOWNLOADING") {
                popup.menu.add("Pause Download")
            } else if (download.status == "PAUSED") {
                popup.menu.add("Resume Download")
            }

            popup.menu.add("Copy Download Link")
            popup.menu.add("Open File")
            popup.menu.add("Delete")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.title) {
                    "Pause Download" -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(context)
                            db.downloadDao().updateStatus(download.id, "PAUSED")
                        }
                        Toast.makeText(context, "Download paused", Toast.LENGTH_SHORT).show()
                        true
                    }
                    "Resume Download" -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(context)
                            db.downloadDao().updateStatus(download.id, "DOWNLOADING")
                        }
                        val serviceIntent = Intent(context, DownloadService::class.java).apply {
                            putExtra("TYPE", if (download.isYouTube) "YOUTUBE" else "STANDARD")
                            putExtra("URL", download.url)
                            putExtra("FILENAME", download.filename)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Toast.makeText(context, "Resuming download...", Toast.LENGTH_SHORT).show()
                        true
                    }
                    "Copy Download Link" -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Download URL", download.url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        true
                    }
                    "Open File" -> {
                        val filePath = download.filePath.ifEmpty {
                            val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            File(File(publicDownloads, "FastDL"), download.filename).absolutePath
                        }
                        val file = File(filePath)
                        if (file.exists()) {
                            try {
                                val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open File With"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "File does not exist on disk: $filePath", Toast.LENGTH_LONG).show()
                        }
                        true
                    }
                    "Delete" -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(context)
                            db.downloadDao().deleteDownloadById(download.id)
                            if (download.filePath.isNotEmpty()) {
                                val file = File(download.filePath)
                                if (file.exists()) file.delete()
                            }
                        }
                        Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DownloadEntity>() {
            override fun areItemsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
