package com.fastdl.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class BrowserFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var videoSnifferFab: FloatingActionButton
    private var detectedMediaUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_browser, container, false)

        webView = view.findViewById(R.id.webView)
        urlEditText = view.findViewById(R.id.urlEditText)
        videoSnifferFab = view.findViewById(R.id.videoSnifferFab)
        val goButton = view.findViewById<Button>(R.id.goButton)

        val btnYoutube = view.findViewById<TextView>(R.id.btnYoutube)
        val btnInstagram = view.findViewById<TextView>(R.id.btnInstagram)
        val btnFacebook = view.findViewById<TextView>(R.id.btnFacebook)
        val btnTikTok = view.findViewById<TextView>(R.id.btnTikTok)

        setupWebView()

        goButton.setOnClickListener {
            loadUserUrl(urlEditText.text.toString())
        }

        btnYoutube.setOnClickListener { loadUserUrl("https://m.youtube.com") }
        btnInstagram.setOnClickListener { loadUserUrl("https://www.instagram.com") }
        btnFacebook.setOnClickListener { loadUserUrl("https://m.facebook.com") }
        btnTikTok.setOnClickListener { loadUserUrl("https://www.tiktok.com") }

        videoSnifferFab.setOnClickListener {
            detectedMediaUrl?.let { url ->
                showQualityDialog(url)
            }
        }

        // Default home page
        loadUserUrl("https://m.youtube.com")

        return view
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    urlEditText.setText(it)
                    checkUrlForMedia(it)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (url != null) {
                    checkUrlForMedia(url)
                }
                return false
            }
        }
    }

    private fun loadUserUrl(input: String) {
        var finalUrl = input.trim()
        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            finalUrl = if (finalUrl.contains(".")) "https://$finalUrl" else "https://www.google.com/search?q=$finalUrl"
        }
        urlEditText.setText(finalUrl)
        webView.loadUrl(finalUrl)
    }

    private fun checkUrlForMedia(url: String) {
        val isMedia = url.contains("youtube.com/watch") ||
                url.contains("youtu.be") ||
                url.endsWith(".mp4") ||
                url.endsWith(".m3u8") ||
                url.endsWith(".webm") ||
                url.endsWith(".mp3")

        if (isMedia) {
            detectedMediaUrl = url
            videoSnifferFab.visibility = View.VISIBLE
        }
    }

    private fun showQualityDialog(url: String) {
        val bottomSheet = QualitySelectionBottomSheet.newInstance(url)
        bottomSheet.setOnQualitySelectedListener { quality, isAudio ->
            (activity as? MainActivity)?.processDownloadUrl(url, null, null)
        }
        bottomSheet.show(parentFragmentManager, "QualityBottomSheet")
    }
}
