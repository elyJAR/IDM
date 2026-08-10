package com.fastdl.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class QualitySelectionBottomSheet : BottomSheetDialogFragment() {

    private var videoUrl: String? = null
    private var onQualitySelectedListener: ((quality: String, isAudioOnly: Boolean) -> Unit)? = null

    companion object {
        fun newInstance(url: String): QualitySelectionBottomSheet {
            val fragment = QualitySelectionBottomSheet()
            val args = Bundle().apply {
                putString("URL", url)
            }
            fragment.arguments = args
            return fragment
        }
    }

    fun setOnQualitySelectedListener(listener: (quality: String, isAudioOnly: Boolean) -> Unit) {
        onQualitySelectedListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        videoUrl = arguments?.getString("URL")
        val view = inflater.inflate(R.layout.bottom_sheet_quality, container, false)

        val titleText = view.findViewById<TextView>(R.id.dialogTitleText)
        val radioGroup = view.findViewById<RadioGroup>(R.id.qualityRadioGroup)
        val radio1080 = view.findViewById<RadioButton>(R.id.radio1080)
        val radio720 = view.findViewById<RadioButton>(R.id.radio720)
        val radio480 = view.findViewById<RadioButton>(R.id.radio480)
        val radioAudio = view.findViewById<RadioButton>(R.id.radioAudio)
        val downloadButton = view.findViewById<Button>(R.id.startDownloadBtn)

        videoUrl?.let { url ->
            CoroutineScope(Dispatchers.IO).launch {
                val client = OkHttpClient()
                val manager = YouTubeDownloadManager(DownloadEngine(client), client)
                val info = manager.fetchVideoInfo(url)

                withContext(Dispatchers.Main) {
                    val audioBadge = if (info.audioTracksCount > 1) " 🌐 (${info.audioTracksCount} Audio Tracks)" else ""
                    titleText.text = "${info.title}$audioBadge"
                    radio1080.text = "1080p Full HD (AV1/VP9) ~ ${info.size1080p}"
                    radio720.text = "720p HD (Compact Size) ~ ${info.size720p}"
                    radio480.text = "480p SD (Data Saver) ~ ${info.size480p}"
                    radioAudio.text = "Audio Only (MP3 / Opus) ~ ${info.sizeAudio}"
                }
            }
        }

        downloadButton.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val (quality, isAudio) = when (selectedId) {
                R.id.radio1080 -> "1080p" to false
                R.id.radio720 -> "720p" to false
                R.id.radio480 -> "480p" to false
                R.id.radioAudio -> "Audio (Opus/MP3)" to true
                else -> "Best" to false
            }
            onQualitySelectedListener?.invoke(quality, isAudio)
            dismiss()
        }

        return view
    }
}
