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
}
