package com.fastdl.app

import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import java.io.File

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

    suspend fun downloadOptimizedYouTubeVideo(
        url: String,
        outputDir: File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {

        // Normalize mobile YouTube URLs (m.youtube.com -> www.youtube.com)
        val cleanUrl = url.replace("m.youtube.com", "www.youtube.com")

        // 1. Extract Streams using ServiceList factory method
        val extractor = ServiceList.YouTube.getStreamExtractor(cleanUrl)
        extractor.fetchPage()

        // 2. Select Optimal Video Codec (AV1 > VP9 > H.264 > Progressive)
        val videoOnlyStreams = extractor.videoOnlyStreams
        val optimalVideo = videoOnlyStreams.find { it.format?.name?.contains("AV1", ignoreCase = true) == true }
            ?: videoOnlyStreams.find { it.format?.name?.contains("VP9", ignoreCase = true) == true }
            ?: videoOnlyStreams.firstOrNull()
            ?: extractor.videoStreams.firstOrNull()

        // 3. Select Optimal Audio Codec
        val audioStreams = extractor.audioStreams
        val optimalAudio = audioStreams.find { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
            ?: audioStreams.firstOrNull()

        if (optimalVideo != null && optimalAudio != null) {
            val videoFile = File(outputDir, "temp_video_${System.currentTimeMillis()}.webm")
            val audioFile = File(outputDir, "temp_audio_${System.currentTimeMillis()}.webm")
            val finalOutputFile = File(outputDir, "YouTube_${System.currentTimeMillis()}.mkv")

            // 4. Download Video and Audio Streams separately using our multi-part engine
            downloadEngine.downloadFileMultiPart(optimalVideo.content, videoFile, onProgress = onProgress)
            downloadEngine.downloadFileMultiPart(optimalAudio.content, audioFile)

            // 5. Mux using FFmpegKit without re-encoding (Instant merge, zero quality loss)
            val command = "-y -i ${videoFile.absolutePath} -i ${audioFile.absolutePath} -c copy ${finalOutputFile.absolutePath}"
            val session = FFmpegKit.execute(command)

            if (session.returnCode.isValueSuccess) {
                videoFile.delete()
                audioFile.delete()
                onProgress?.invoke(finalOutputFile.length(), finalOutputFile.length())
            } else {
                throw Exception("FFmpeg Muxing failed: ${session.failStackTrace}")
            }
        } else if (optimalVideo != null) {
            // Progressive video fallback (contains both audio and video in 1 stream)
            val finalOutputFile = File(outputDir, "YouTube_${System.currentTimeMillis()}.mp4")
            downloadEngine.downloadFileMultiPart(optimalVideo.content, finalOutputFile, onProgress = onProgress)
        } else {
            throw Exception("Could not extract optimal streams from YouTube URL")
        }
    }
}
