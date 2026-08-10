package com.fastdl.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

class DownloadEngine(private val client: OkHttpClient) {

    suspend fun downloadFileMultiPart(url: String, targetFile: File, parts: Int = 8) = withContext(Dispatchers.IO) {
        // 1. Get File Size (HEAD Request)
        val headRequest = Request.Builder().url(url).head().build()
        val response = client.newCall(headRequest).execute()
        
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
        val acceptRanges = response.header("Accept-Ranges") == "bytes"

        if (contentLength > 0 && acceptRanges) {
            // Allocate space for the target file
            val randomAccessFile = RandomAccessFile(targetFile, "rw")
            randomAccessFile.setLength(contentLength)
            randomAccessFile.close()

            val chunkSize = contentLength / parts
            val deferredParts = (0 until parts).map { index ->
                async {
                    val startByte = index * chunkSize
                    // Ensure the last chunk grabs all remaining bytes
                    val endByte = if (index == parts - 1) contentLength - 1 else (startByte + chunkSize - 1)
                    
                    downloadChunk(url, targetFile, startByte, endByte, index)
                }
            }
            
            // Await all chunks to complete
            deferredParts.awaitAll()
        } else {
            // Fallback to single thread download if server doesn't support ranges
            downloadSingleThread(url, targetFile)
        }
    }

    private fun downloadChunk(url: String, file: File, start: Long, end: Long, partIndex: Int) {
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
                    // TODO: Report progress via Callback/Flow
                }
            }
        }
    }

    private fun downloadSingleThread(url: String, file: File) {
        // Basic single stream download fallback
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().body?.byteStream()?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
