package com.fastdl.app

import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.io.File

data class VideoInfo(
    val title: String,
    val size1080p: String = "45 MB",
    val size720p: String = "25 MB",
    val size480p: String = "12 MB",
    val sizeAudio: String = "3.5 MB"
)

class YouTubeDownloadManager(
    private val downloadEngine: DownloadEngine,
    private val client: OkHttpClient
) {

    init {
        try {
            NewPipe.init(DownloaderImpl.getInstance(client))
        } catch (e: Exception) {
            // Already initialized
        }
    }

    suspend fun fetchVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.replace("m.youtube.com", "www.youtube.com")
            val extractor = ServiceList.YouTube.getStreamExtractor(cleanUrl)
            extractor.fetchPage()
            val rawTitle = extractor.name ?: "YouTube_Video"
            return@withContext VideoInfo(title = rawTitle)
        } catch (e: Exception) {
            return@withContext VideoInfo(title = "YouTube Video")
        }
    }

    suspend fun downloadOptimizedYouTubeVideo(
        url: String,
        outputDir: File,
        onTitleExtracted: ((String) -> Unit)? = null,
        onProgress: ((downloaded: Long, total: Long, speed: String) -> Unit)? = null
    ): Pair<File, String> = withContext(Dispatchers.IO) {

        val cleanUrl = url.replace("m.youtube.com", "www.youtube.com")
        val extractor = ServiceList.YouTube.getStreamExtractor(cleanUrl)
        extractor.fetchPage()

        val rawTitle = extractor.name ?: "YouTube_Video"
        val safeTitle = rawTitle.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().take(50)
        val realFilename = "$safeTitle.mp4"

        // Report real video title back immediately so DB and UI update right away
        onTitleExtracted?.invoke(realFilename)

        // 1. Select Optimal Compact Video Stream (AV1/VP9 for minimal file size)
        val videoOnlyStreams = extractor.videoOnlyStreams
        val optimalVideo = videoOnlyStreams.find { it.format?.name?.contains("AV1", ignoreCase = true) == true && it.height <= 1080 }
            ?: videoOnlyStreams.find { it.format?.name?.contains("VP9", ignoreCase = true) == true && it.height <= 1080 }
            ?: videoOnlyStreams.find { it.height <= 1080 }
            ?: videoOnlyStreams.firstOrNull()
            ?: extractor.videoStreams.firstOrNull()

        // 2. Select 1 Single Primary Audio Stream (Original language)
        val audioStreams = extractor.audioStreams
        val primaryAudio = audioStreams.find { it.audioTrackType == AudioTrackType.ORIGINAL }
            ?: audioStreams.find { it.audioTrackName?.contains("original", ignoreCase = true) == true }
            ?: audioStreams.find { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
            ?: audioStreams.firstOrNull()

        if (optimalVideo != null && primaryAudio != null) {
            val videoFile = File(outputDir, "temp_video_${System.currentTimeMillis()}.webm")
            val audioFile = File(outputDir, "temp_audio_${System.currentTimeMillis()}.webm")
            val finalOutputFile = File(outputDir, realFilename)

            try {
                // Download video and single primary audio stream
                downloadEngine.downloadFileMultiPart(optimalVideo.content, videoFile, onProgress = onProgress)
                downloadEngine.downloadFileMultiPart(primaryAudio.content, audioFile)

                // Mux using FFmpegKit into clean MP4/MKV
                val command = "-y -i \"${videoFile.absolutePath}\" -i \"${audioFile.absolutePath}\" -c copy \"${finalOutputFile.absolutePath}\""
                val session = FFmpegKit.execute(command)

                if (session.returnCode.isValueSuccess) {
                    onProgress?.invoke(finalOutputFile.length(), finalOutputFile.length(), "0 KB/s")
                    return@withContext Pair(finalOutputFile, realFilename)
                } else {
                    throw Exception("FFmpeg Muxing failed: ${session.failStackTrace}")
                }
            } finally {
                // Clean up temp files immediately to free up phone storage
                if (videoFile.exists()) videoFile.delete()
                if (audioFile.exists()) audioFile.delete()
            }
        } else if (optimalVideo != null) {
            val finalOutputFile = File(outputDir, realFilename)
            downloadEngine.downloadFileMultiPart(optimalVideo.content, finalOutputFile, onProgress = onProgress)
            return@withContext Pair(finalOutputFile, realFilename)
        } else {
            throw Exception("Could not extract video streams from YouTube URL")
        }
    }
}
