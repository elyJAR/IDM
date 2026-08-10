package com.fastdl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: DownloadAdapter
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)
        adapter = DownloadAdapter()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Observe the database and update the UI automatically
        CoroutineScope(Dispatchers.Main).launch {
            db.downloadDao().getAllDownloads().collectLatest { downloads ->
                adapter.submitList(downloads)
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data: Uri? = intent.data
            if (data?.scheme == "fastdl" && data.host == "download") {
                val downloadUrl = data.getQueryParameter("url")
                val cookie = data.getQueryParameter("cookie")
                val filename = data.getQueryParameter("filename")

                Log.i("FastDL", "Intercepted Download: $downloadUrl")
                Toast.makeText(this, "Starting download: $filename", Toast.LENGTH_LONG).show()

                // If it's a YouTube URL, we route it to our YouTube Extractor
                if (downloadUrl != null && (downloadUrl.contains("youtube.com") || downloadUrl.contains("youtu.be"))) {
                    startYouTubeDownload(downloadUrl)
                } else {
                    // Otherwise, start standard multi-part download
                    startStandardDownload(downloadUrl, cookie, filename)
                }
            }
        }
    }

    private fun startYouTubeDownload(url: String) {
        Log.d("FastDL", "Dispatching to YouTubeExtractor for: $url")
        val serviceIntent = Intent(this, DownloadService::class.java).apply {
            putExtra("TYPE", "YOUTUBE")
            putExtra("URL", url)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun startStandardDownload(url: String?, cookie: String?, filename: String?) {
        Log.d("FastDL", "Dispatching standard download: $url")
        val serviceIntent = Intent(this, DownloadService::class.java).apply {
            putExtra("TYPE", "STANDARD")
            putExtra("URL", url)
            putExtra("FILENAME", filename)
            // Passing cookie if needed for authenticated downloads
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
