package com.fastdl.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY id DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun updateProgress(id: Int, bytes: Long)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, status = :status, speed = :speed WHERE url = :url")
    suspend fun updateProgressByUrl(url: String, downloaded: Long, total: Long, status: String, speed: String = "0 KB/s")

    @Query("UPDATE downloads SET filePath = :filePath WHERE url = :url")
    suspend fun updateFilePathByUrl(url: String, filePath: String)

    @Query("UPDATE downloads SET filename = :filename WHERE url = :url")
    suspend fun updateFilenameByUrl(url: String, filename: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Int)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun deleteCompletedDownloads()
}
