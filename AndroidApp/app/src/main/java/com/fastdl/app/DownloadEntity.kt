package com.fastdl.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val filename: String,
    val status: String, // "PENDING", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val totalBytes: Long,
    val downloadedBytes: Long,
    val filePath: String,
    val isYouTube: Boolean = false
)
