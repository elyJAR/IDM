package com.fastdl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var tabBrowser: TextView
    private lateinit var tabDownloads: TextView

    private val browserFragment = BrowserFragment()
    private val downloadsFragment = DownloadsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)

        tabBrowser = findViewById(R.id.tabBrowser)
        tabDownloads = findViewById(R.id.tabDownloads)

        tabBrowser.setOnClickListener {
            switchTab(browserFragment, isBrowser = true)
        }

        tabDownloads.setOnClickListener {
            switchTab(downloadsFragment, isBrowser = false)
        }

        // Set default fragment to Browser (VidMate Style)
        switchTab(browserFragment, isBrowser = true)

        handleIntent(intent)
    }

    private var currentTabIsBrowser = true

    private fun switchTab(fragment: Fragment, isBrowser: Boolean) {
        currentTabIsBrowser = isBrowser
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        if (isBrowser) {
            tabBrowser.setTextColor(android.graphics.Color.parseColor("#3b82f6"))
            tabDownloads.setTextColor(android.graphics.Color.parseColor("#94a3b8"))
        } else {
            tabBrowser.setTextColor(android.graphics.Color.parseColor("#94a3b8"))
            tabDownloads.setTextColor(android.graphics.Color.parseColor("#3b82f6"))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentTabIsBrowser && browserFragment.canGoBack()) {
            browserFragment.goBack()
        } else {
            super.onBackPressed()
        }
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

                if (downloadUrl != null) {
                    processDownloadUrl(downloadUrl, cookie, filename)
                }
            }
        }
    }

    fun processDownloadUrl(url: String, cookie: String?, customFilename: String?) {
        val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")
        val filename = customFilename ?: if (isYouTube) "YouTube_Video.mkv" else url.substringAfterLast("/").take(30)

        // Insert into database to show up instantly in RecyclerView
        CoroutineScope(Dispatchers.IO).launch {
            val downloadEntity = DownloadEntity(
                url = url,
                filename = filename,
                downloadedBytes = 0L,
                totalBytes = 0L,
                status = "DOWNLOADING",
                filePath = "",
                isYouTube = isYouTube
            )
            db.downloadDao().insertDownload(downloadEntity)
        }

        Toast.makeText(this, "Starting download: $filename", Toast.LENGTH_SHORT).show()

        if (isYouTube) {
            startYouTubeDownload(url)
        } else {
            startStandardDownload(url, cookie, filename)
        }

        // Switch to Downloads tab so user sees live progress
        switchTab(downloadsFragment, isBrowser = false)
    }

    private fun startYouTubeDownload(url: String) {
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

    private fun startStandardDownload(url: String, cookie: String?, filename: String) {
        val serviceIntent = Intent(this, DownloadService::class.java).apply {
            putExtra("TYPE", "STANDARD")
            putExtra("URL", url)
            putExtra("FILENAME", filename)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
