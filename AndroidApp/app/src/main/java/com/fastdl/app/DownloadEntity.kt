package com.fastdl.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val filename: String,
    val status: String = "DOWNLOADING", // "PENDING", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val filePath: String = "",
    val isYouTube: Boolean = false
)
