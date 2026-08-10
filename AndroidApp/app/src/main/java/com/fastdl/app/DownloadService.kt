package com.fastdl.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
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
        val type = intent?.getStringExtra("TYPE")
        val url = intent?.getStringExtra("URL")
        val filename = intent?.getStringExtra("FILENAME") ?: "download_file"

        val notification = NotificationCompat.Builder(this, "FASTDL_CHANNEL")
            .setContentTitle("FastDL is active")
            .setContentText("Downloading: $filename")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()

        startForeground(1, notification)

        if (url != null) {
            val outputDir = getExternalFilesDir(null) ?: filesDir
            
            scope.launch {
                try {
                    var lastReportTime = 0L

                    if (type == "YOUTUBE") {
                        Log.i("FastDL", "Starting YouTube Download...")
                        youtubeManager.downloadOptimizedYouTubeVideo(url, outputDir) { downloaded, total ->
                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 300 || downloaded == total) {
                                lastReportTime = now
                                val status = if (total > 0 && downloaded >= total) "COMPLETED" else "DOWNLOADING"
                                scope.launch {
                                    db.downloadDao().updateProgressByUrl(url, downloaded, total, status)
                                }
                            }
                        }
                        db.downloadDao().updateProgressByUrl(url, 100L, 100L, "COMPLETED")
                        Log.i("FastDL", "YouTube Download Complete!")
                    } else {
                        Log.i("FastDL", "Starting Standard Download...")
                        val targetFile = File(outputDir, filename)
                        
                        downloadEngine.downloadFileMultiPart(url, targetFile) { downloaded, total ->
                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 300 || downloaded == total) {
                                lastReportTime = now
                                val status = if (total > 0 && downloaded >= total) "COMPLETED" else "DOWNLOADING"
                                scope.launch {
                                    db.downloadDao().updateProgressByUrl(url, downloaded, total, status)
                                }
                            }
                        }
                        
                        db.downloadDao().updateProgressByUrl(url, targetFile.length(), targetFile.length(), "COMPLETED")
                        Log.i("FastDL", "Standard Download Complete!")
                    }
                } catch (e: Exception) {
                    Log.e("FastDL", "Download failed: ${e.message}", e)
                    db.downloadDao().updateProgressByUrl(url, 0L, 0L, "FAILED")
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
