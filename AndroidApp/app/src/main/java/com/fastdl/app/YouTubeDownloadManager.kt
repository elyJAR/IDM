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
    val audioTracksCount: Int = 1,
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
            val distinctAudioTracks = extractor.audioStreams.distinctBy { it.audioTrackName ?: it.audioLocale?.displayName }.size
            return@withContext VideoInfo(title = rawTitle, audioTracksCount = Math.max(1, distinctAudioTracks))
        } catch (e: Exception) {
            return@withContext VideoInfo(title = "YouTube Video")
        }
    }

    suspend fun downloadOptimizedYouTubeVideo(
        url: String,
        outputDir: File,
        onProgress: ((downloaded: Long, total: Long, speed: String) -> Unit)? = null
    ): Pair<File, String> = withContext(Dispatchers.IO) {

        val cleanUrl = url.replace("m.youtube.com", "www.youtube.com")
        val extractor = ServiceList.YouTube.getStreamExtractor(cleanUrl)
        extractor.fetchPage()

        val rawTitle = extractor.name ?: "YouTube_Video"
        val safeTitle = rawTitle.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(50)

        // 1. Select Optimal Video Codec (AV1 > VP9 > H.264)
        val videoOnlyStreams = extractor.videoOnlyStreams
        val optimalVideo = videoOnlyStreams.find { it.format?.name?.contains("AV1", ignoreCase = true) == true }
            ?: videoOnlyStreams.find { it.format?.name?.contains("VP9", ignoreCase = true) == true }
            ?: videoOnlyStreams.firstOrNull()
            ?: extractor.videoStreams.firstOrNull()

        // 2. Select Audio Streams (Multi-language Audio Track Support)
        val audioStreams = extractor.audioStreams
        val originalAudio = audioStreams.find { it.audioTrackType == AudioTrackType.ORIGINAL }
            ?: audioStreams.find { it.audioTrackName?.contains("original", ignoreCase = true) == true }
            ?: audioStreams.find { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
            ?: audioStreams.firstOrNull()

        // Gather secondary dubbed audio tracks if present
        val secondaryAudio = audioStreams
            .filter { it != originalAudio && it.audioTrackName != null }
            .distinctBy { it.audioTrackName }
            .take(2) // Max 2 additional audio tracks to save space

        if (optimalVideo != null && originalAudio != null) {
            val videoFile = File(outputDir, "temp_video_${System.currentTimeMillis()}.webm")
            val primaryAudioFile = File(outputDir, "temp_audio_primary_${System.currentTimeMillis()}.webm")
            val finalOutputFile = File(outputDir, "$safeTitle.mkv")

            // Download video and primary audio
            downloadEngine.downloadFileMultiPart(optimalVideo.content, videoFile, onProgress = onProgress)
            downloadEngine.downloadFileMultiPart(originalAudio.content, primaryAudioFile)

            val tempAudioFiles = mutableListOf(primaryAudioFile)

            // Download secondary language audio streams if multi-audio is detected
            secondaryAudio.forEachIndexed { idx, secStream ->
                val secAudioFile = File(outputDir, "temp_audio_sec_${idx}_${System.currentTimeMillis()}.webm")
                try {
                    downloadEngine.downloadFileMultiPart(secStream.content, secAudioFile)
                    tempAudioFiles.add(secAudioFile)
                } catch (e: Exception) {
                    // Ignore secondary audio download failure
                }
            }

            // Build FFmpeg command to mux video + all audio tracks into MKV container
            val inputs = StringBuilder("-y -i \"${videoFile.absolutePath}\"")
            val maps = StringBuilder("-map 0:v")

            tempAudioFiles.forEachIndexed { idx, audioF ->
                inputs.append(" -i \"${audioF.absolutePath}\"")
                maps.append(" -map ${idx + 1}:a")
            }

            val command = "$inputs $maps -c copy \"${finalOutputFile.absolutePath}\""
            val session = FFmpegKit.execute(command)

            if (session.returnCode.isValueSuccess) {
                videoFile.delete()
                tempAudioFiles.forEach { it.delete() }
                onProgress?.invoke(finalOutputFile.length(), finalOutputFile.length(), "0 KB/s")
                return@withContext Pair(finalOutputFile, "$safeTitle.mkv")
            } else {
                throw Exception("FFmpeg Multi-Audio Muxing failed: ${session.failStackTrace}")
            }
        } else if (optimalVideo != null) {
            val finalOutputFile = File(outputDir, "$safeTitle.mp4")
            downloadEngine.downloadFileMultiPart(optimalVideo.content, finalOutputFile, onProgress = onProgress)
            return@withContext Pair(finalOutputFile, "$safeTitle.mp4")
        } else {
            throw Exception("Could not extract video streams from YouTube URL")
        }
    }
}
