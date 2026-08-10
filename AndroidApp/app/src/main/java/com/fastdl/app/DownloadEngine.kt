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

    suspend fun downloadFileMultiPart(
        url: String,
        targetFile: File,
        parts: Int = 8,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        // 1. Get File Size (HEAD Request)
        val headRequest = Request.Builder().url(url).head().build()
        val response = client.newCall(headRequest).execute()
        
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
        val acceptRanges = response.header("Accept-Ranges") == "bytes"

        if (contentLength > 0 && acceptRanges) {
            onProgress?.invoke(0L, contentLength)

            // Allocate space for the target file
            val randomAccessFile = RandomAccessFile(targetFile, "rw")
            randomAccessFile.setLength(contentLength)
            randomAccessFile.close()

            val downloadedBytes = AtomicLong(0L)
            val chunkSize = contentLength / parts

            val deferredParts = (0 until parts).map { index ->
                async {
                    val startByte = index * chunkSize
                    val endByte = if (index == parts - 1) contentLength - 1 else (startByte + chunkSize - 1)
                    
                    downloadChunk(url, targetFile, startByte, endByte) { bytesRead ->
                        val currentTotal = downloadedBytes.addAndGet(bytesRead.toLong())
                        onProgress?.invoke(currentTotal, contentLength)
                    }
                }
            }
            
            // Await all chunks to complete
            deferredParts.awaitAll()
            onProgress?.invoke(contentLength, contentLength)
        } else {
            // Fallback to single thread download if server doesn't support ranges
            downloadSingleThread(url, targetFile, onProgress)
        }
    }

    private fun downloadChunk(
        url: String,
        file: File,
        start: Long,
        end: Long,
        onBytesRead: (Int) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$start-$end")
            .build()

        val response = client.newCall(request).execute()
        
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
        onProgress: ((downloaded: Long, total: Long) -> Unit)?
    ) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val contentLength = response.body?.contentLength() ?: 0L

        onProgress?.invoke(0L, contentLength)

        response.body?.byteStream()?.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var totalRead = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    onProgress?.invoke(totalRead, contentLength)
                }
                onProgress?.invoke(totalRead, if (contentLength > 0) contentLength else totalRead)
            }
        }
    }
}
