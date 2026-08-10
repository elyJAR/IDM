package com.fastdl.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class DownloadAdapter : ListAdapter<DownloadEntity, DownloadAdapter.DownloadViewHolder>(DiffCallback) {

    class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val filenameText: TextView = itemView.findViewById(R.id.filenameText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val progressText: TextView = itemView.findViewById(R.id.progressText)

        fun bind(download: DownloadEntity) {
            filenameText.text = download.filename
            statusText.text = download.status
            
            if (download.totalBytes > 0) {
                progressBar.max = 100
                val progressPercent = ((download.downloadedBytes.toDouble() / download.totalBytes) * 100).toInt()
                progressBar.progress = progressPercent
                
                val downloadedMB = download.downloadedBytes / (1024 * 1024)
                val totalMB = download.totalBytes / (1024 * 1024)
                progressText.text = "${downloadedMB}MB / ${totalMB}MB"
            } else {
                progressBar.isIndeterminate = true
                progressText.text = "Computing size..."
            }
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
