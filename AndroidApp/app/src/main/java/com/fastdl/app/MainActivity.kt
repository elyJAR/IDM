package com.fastdl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: DownloadAdapter
    private lateinit var db: AppDatabase
    private lateinit var emptyStateTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)
        adapter = DownloadAdapter()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        emptyStateTextView = findViewById(R.id.emptyStateTextView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val fab = findViewById<FloatingActionButton>(R.id.addDownloadFab)
        fab.setOnClickListener {
            showAddDownloadDialog()
        }

        // Observe the database and update the UI automatically
        CoroutineScope(Dispatchers.Main).launch {
            db.downloadDao().getAllDownloads().collectLatest { downloads ->
                adapter.submitList(downloads)
                if (downloads.isEmpty()) {
                    emptyStateTextView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyStateTextView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }

        handleIntent(intent)
    }

    private fun showAddDownloadDialog() {
        val input = EditText(this).apply {
            hint = "https://www.youtube.com/watch?v=... or direct link"
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Add New Download")
            .setMessage("Paste a YouTube URL or direct file download link:")
            .setView(input)
            .setPositiveButton("Download") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    processDownloadUrl(url, null, null)
                } else {
                    Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun processDownloadUrl(url: String, cookie: String?, customFilename: String?) {
        val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")
        val filename = customFilename ?: if (isYouTube) "YouTube_Video.mkv" else url.substringAfterLast("/").take(30)

        // Insert into database to show up instantly in RecyclerView
        CoroutineScope(Dispatchers.IO).launch {
            val downloadEntity = DownloadEntity(
                url = url,
                filename = filename,
                downloadedBytes = 0,
                totalBytes = 0,
                status = "DOWNLOADING"
            )
            db.downloadDao().insertDownload(downloadEntity)
        }

        Toast.makeText(this, "Starting download: $filename", Toast.LENGTH_SHORT).show()

        if (isYouTube) {
            startYouTubeDownload(url)
        } else {
            startStandardDownload(url, cookie, filename)
        }
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
