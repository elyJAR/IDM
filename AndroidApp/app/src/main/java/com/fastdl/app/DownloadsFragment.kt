package com.fastdl.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadsFragment : Fragment() {

    private lateinit var adapter: DownloadAdapter
    private lateinit var db: AppDatabase
    private lateinit var emptyStateTextView: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_downloads, container, false)

        val context = requireContext()
        db = AppDatabase.getDatabase(context)
        adapter = DownloadAdapter()

        recyclerView = view.findViewById(R.id.recyclerView)
        emptyStateTextView = view.findViewById(R.id.emptyStateTextView)
        val fab = view.findViewById<FloatingActionButton>(R.id.addDownloadFab)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            showAddDownloadDialog()
        }

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

        return view
    }

    private fun showAddDownloadDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            hint = "https://www.youtube.com/watch?v=... or direct link"
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(context)
            .setTitle("Add New Download")
            .setMessage("Paste a YouTube URL or direct file download link:")
            .setView(input)
            .setPositiveButton("Download") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    (activity as? MainActivity)?.processDownloadUrl(url, null, null)
                } else {
                    Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
