package com.fastdl.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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
        val downloadButton = view.findViewById<Button>(R.id.startDownloadBtn)

        titleText.text = "Select Quality & Format"

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
