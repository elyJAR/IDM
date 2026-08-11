package com.fastdl.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

class DownloadEngine(private val client: OkHttpClient) {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun downloadFileMultiPart(
        url: String,
        targetFile: File,
        parts: Int = 8,
        cookie: String? = null,
        onProgress: ((downloaded: Long, total: Long, speed: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val headRequestBuilder = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", userAgent)

        if (!cookie.isNullOrEmpty()) {
            headRequestBuilder.header("Cookie", cookie)
        }

        val response = client.newCall(headRequestBuilder.build()).execute()
        
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
        val acceptRanges = response.header("Accept-Ranges") == "bytes"

        if (contentLength > 0 && acceptRanges) {
            onProgress?.invoke(0L, contentLength, "0 KB/s")

            val randomAccessFile = RandomAccessFile(targetFile, "rw")
            randomAccessFile.setLength(contentLength)
            randomAccessFile.close()

            val downloadedBytes = AtomicLong(0L)
            val chunkSize = contentLength / parts

            var lastCheckTime = System.currentTimeMillis()
            var lastCheckBytes = 0L

            val deferredParts = (0 until parts).map { index ->
                async {
                    val startByte = index * chunkSize
                    val endByte = if (index == parts - 1) contentLength - 1 else (startByte + chunkSize - 1)
                    
                    downloadChunk(url, targetFile, startByte, endByte, cookie) { bytesRead ->
                        val currentTotal = downloadedBytes.addAndGet(bytesRead.toLong())
                        
                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastCheckTime
                        if (timeDiff >= 500) {
                            val bytesDiff = currentTotal - lastCheckBytes
                            val speedBps = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0L
                            val speedFormatted = formatSpeed(speedBps)

                            lastCheckTime = now
                            lastCheckBytes = currentTotal

                            onProgress?.invoke(currentTotal, contentLength, speedFormatted)
                        }
                    }
                }
            }
            
            deferredParts.awaitAll()
            onProgress?.invoke(contentLength, contentLength, "0 KB/s")
        } else {
            downloadSingleThread(url, targetFile, cookie, onProgress)
        }
    }

    private fun downloadChunk(
        url: String,
        file: File,
        start: Long,
        end: Long,
        cookie: String?,
        onBytesRead: (Int) -> Unit
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$start-$end")
            .header("User-Agent", userAgent)

        if (!cookie.isNullOrEmpty()) {
            requestBuilder.header("Cookie", cookie)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        
        response.body?.byteStream()?.use { input ->
            RandomAccessFile(file, "rw").use { output ->
                output.seek(start)
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    onBytesRead(bytesRead)
                }
            }
        }
    }

    private fun downloadSingleThread(
        url: String,
        file: File,
        cookie: String?,
        onProgress: ((downloaded: Long, total: Long, speed: String) -> Unit)?
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)

        if (!cookie.isNullOrEmpty()) {
            requestBuilder.header("Cookie", cookie)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val contentLength = response.body?.contentLength() ?: 0L

        onProgress?.invoke(0L, contentLength, "0 KB/s")

        var lastCheckTime = System.currentTimeMillis()
        var lastCheckBytes = 0L

        response.body?.byteStream()?.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var totalRead = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    
                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastCheckTime
                    if (timeDiff >= 500) {
                        val bytesDiff = totalRead - lastCheckBytes
                        val speedBps = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0L
                        val speedFormatted = formatSpeed(speedBps)

                        lastCheckTime = now
                        lastCheckBytes = totalRead

                        onProgress?.invoke(totalRead, contentLength, speedFormatted)
                    }
                }
                onProgress?.invoke(totalRead, if (contentLength > 0) contentLength else totalRead, "0 KB/s")
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return if (bytesPerSec >= 1024 * 1024) {
            String.format("%.1f MB/s", bytesPerSec.toDouble() / (1024 * 1024))
        } else {
            String.format("%d KB/s", bytesPerSec / 1024)
        }
    }
}
