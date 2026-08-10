package com.fastdl.app

import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import java.io.File

class YouTubeDownloadManager(private val downloadEngine: DownloadEngine) {

    suspend fun downloadOptimizedYouTubeVideo(url: String, outputDir: File) = withContext(Dispatchers.IO) {
        
        // 1. Extract Streams using ServiceList factory method
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        // 2. Select Optimal Video Codec (Lowest size without losing quality -> AV1 > VP9 > H.264)
        val videoStreams = extractor.videoOnlyStreams
        val optimalVideo = videoStreams.find { it.format?.name?.contains("AV1", ignoreCase = true) == true }
            ?: videoStreams.find { it.format?.name?.contains("VP9", ignoreCase = true) == true }
            ?: videoStreams.firstOrNull()

        // 3. Select Optimal Audio Codec (Opus is highly compressed)
        val audioStreams = extractor.audioStreams
        val optimalAudio = audioStreams.find { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
            ?: audioStreams.firstOrNull()

        if (optimalVideo != null && optimalAudio != null) {
            val videoFile = File(outputDir, "temp_video.webm")
            val audioFile = File(outputDir, "temp_audio.webm")
            val finalOutputFile = File(outputDir, "final_youtube_download.mkv")

            // 4. Download Video and Audio Streams separately using our multi-part engine
            downloadEngine.downloadFileMultiPart(optimalVideo.content, videoFile)
            downloadEngine.downloadFileMultiPart(optimalAudio.content, audioFile)

            // 5. Mux using FFmpegKit without re-encoding (Instant merge, zero quality loss)
            val command = "-y -i ${videoFile.absolutePath} -i ${audioFile.absolutePath} -c copy ${finalOutputFile.absolutePath}"
            val session = FFmpegKit.execute(command)

            if (session.returnCode.isValueSuccess) {
                // Muxing successful, clean up temporary streams
                videoFile.delete()
                audioFile.delete()
            } else {
                throw Exception("FFmpeg Muxing failed: ${session.failStackTrace}")
            }
        } else {
            throw Exception("Could not extract optimal streams from YouTube URL")
        }
    }
}
