package com.fastdl.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLDecoder

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

        requestStoragePermissions()
        handleIntent(intent)
    }

    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_completed -> {
                CoroutineScope(Dispatchers.IO).launch {
                    db.downloadDao().deleteCompletedDownloads()
                }
                Toast.makeText(this, "Cleared completed downloads", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_clear_cache -> {
                android.webkit.WebStorage.getInstance().deleteAllData()
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                Toast.makeText(this, "Cleared browser cache & cookies", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_settings -> {
                AlertDialog.Builder(this)
                    .setTitle("Download Settings")
                    .setMessage("• Threads per download: 8 (Multi-part Enabled)\n• Storage: Public Downloads/FastDL\n• Extension Handoff: Active")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("About FastDL Engine")
                    .setMessage("FastDL v1.0.0-beta\nHigh-Performance Multi-threaded Downloader & Extension Bridge Engine.")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "fastdl" && data.host == "download") {
            try {
                val rawUrl = data.getQueryParameter("url")
                val rawCookie = data.getQueryParameter("cookie")
                val rawFilename = data.getQueryParameter("filename")

                val downloadUrl = if (!rawUrl.isNullOrEmpty()) URLDecoder.decode(rawUrl, "UTF-8") else null
                val cookie = if (!rawCookie.isNullOrEmpty()) URLDecoder.decode(rawCookie, "UTF-8") else null
                val filename = if (!rawFilename.isNullOrEmpty()) URLDecoder.decode(rawFilename, "UTF-8") else null

                if (!downloadUrl.isNullOrEmpty()) {
                    Log.i("FastDL", "Received Extension Intent -> URL: $downloadUrl")
                    processDownloadUrl(downloadUrl, cookie, filename)
                }
            } catch (e: Exception) {
                Log.e("FastDL", "Error parsing extension intent: ${e.message}", e)
            } finally {
                intent.data = null // Clear data so it's not processed twice
            }
        }
    }

    fun processDownloadUrl(url: String, cookie: String?, customFilename: String?) {
        val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")
        val filename = customFilename?.ifEmpty { null } ?: if (isYouTube) "Fetching Title..." else url.substringAfterLast("/").take(30).ifEmpty { "download_file" }

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
            val insertedId = db.downloadDao().insertDownload(downloadEntity).toInt()

            val serviceIntent = Intent(this@MainActivity, DownloadService::class.java).apply {
                putExtra("DOWNLOAD_ID", insertedId)
                putExtra("TYPE", if (isYouTube) "YOUTUBE" else "STANDARD")
                putExtra("URL", url)
                putExtra("COOKIE", cookie)
                putExtra("FILENAME", filename)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show()

        // Switch to Downloads tab so user sees live progress
        switchTab(downloadsFragment, isBrowser = false)
    }
}
