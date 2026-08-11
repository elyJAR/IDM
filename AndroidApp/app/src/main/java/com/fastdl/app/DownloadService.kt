package com.fastdl.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

class DownloadService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val client = OkHttpClient()
    private val downloadEngine = DownloadEngine(client)
    private val youtubeManager = YouTubeDownloadManager(downloadEngine, client)
    private lateinit var db: AppDatabase

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getIntExtra("DOWNLOAD_ID", -1) ?: -1
        val type = intent?.getStringExtra("TYPE")
        val url = intent?.getStringExtra("URL")
        val cookie = intent?.getStringExtra("COOKIE")
        val filename = intent?.getStringExtra("FILENAME") ?: "download_file"

        val notification = NotificationCompat.Builder(this, "FASTDL_CHANNEL")
            .setContentTitle("FastDL is active")
            .setContentText("Downloading: $filename")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()

        startForeground(1, notification)

        if (url != null) {
            // Save directly into the main public Downloads folder (/sdcard/Download/)
            val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            scope.launch {
                try {
                    var lastReportTime = 0L

                    if (type == "YOUTUBE") {
                        Log.i("FastDL", "Starting YouTube Download...")
                        val (finalFile, realTitle) = youtubeManager.downloadOptimizedYouTubeVideo(
                            url = url,
                            outputDir = outputDir,
                            onTitleExtracted = { title ->
                                scope.launch {
                                    if (downloadId != -1) db.downloadDao().updateFilenameById(downloadId, title)
                                    db.downloadDao().updateFilenameByUrl(url, title)
                                }
                            },
                            onProgress = { downloaded, total, speed ->
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 300 || downloaded == total) {
                                    lastReportTime = now
                                    val status = if (total > 0 && downloaded >= total) "COMPLETED" else "DOWNLOADING"
                                    scope.launch {
                                        if (downloadId != -1) db.downloadDao().updateProgressById(downloadId, downloaded, total, status, speed)
                                        db.downloadDao().updateProgressByUrl(url, downloaded, total, status, speed)
                                    }
                                }
                            }
                        )

                        if (downloadId != -1) db.downloadDao().updateFilePathById(downloadId, finalFile.absolutePath)
                        db.downloadDao().updateFilePathByUrl(url, finalFile.absolutePath)
                        db.downloadDao().updateProgressByUrl(url, finalFile.length(), finalFile.length(), "COMPLETED", "0 KB/s")

                        scanFileToMediaStore(finalFile.absolutePath)
                        Log.i("FastDL", "YouTube Download Complete: ${finalFile.absolutePath}")
                    } else {
                        Log.i("FastDL", "Starting Standard Download...")
                        val targetFile = File(outputDir, filename)
                        
                        if (downloadId != -1) db.downloadDao().updateFilePathById(downloadId, targetFile.absolutePath)
                        db.downloadDao().updateFilePathByUrl(url, targetFile.absolutePath)

                        downloadEngine.downloadFileMultiPart(
                            url = url,
                            targetFile = targetFile,
                            cookie = cookie,
                            onProgress = { downloaded, total, speed ->
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 300 || downloaded == total) {
                                    lastReportTime = now
                                    val status = if (total > 0 && downloaded >= total) "COMPLETED" else "DOWNLOADING"
                                    scope.launch {
                                        if (downloadId != -1) db.downloadDao().updateProgressById(downloadId, downloaded, total, status, speed)
                                        db.downloadDao().updateProgressByUrl(url, downloaded, total, status, speed)
                                    }
                                }
                            }
                        )

                        scanFileToMediaStore(targetFile.absolutePath)
                        Log.i("FastDL", "Standard Download Complete: ${targetFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.e("FastDL", "Download failed: ${e.message}", e)
                    if (downloadId != -1) db.downloadDao().updateProgressById(downloadId, 0L, 0L, "FAILED", "0 KB/s")
                    db.downloadDao().updateProgressByUrl(url, 0L, 0L, "FAILED", "0 KB/s")
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun scanFileToMediaStore(path: String) {
        try {
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(path),
                null
            ) { scannedPath, uri ->
                Log.i("FastDL", "Scanned $scannedPath to MediaStore -> $uri")
            }
        } catch (e: Exception) {
            Log.e("FastDL", "MediaScanner failed: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "FASTDL_CHANNEL",
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
