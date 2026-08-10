package com.fastdl.app

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkHttpRequest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class DownloaderImpl private constructor(private val client: OkHttpClient) : Downloader() {

    companion object {
        @Volatile
        private var instance: DownloaderImpl? = null

        fun getInstance(client: OkHttpClient): DownloaderImpl {
            return instance ?: synchronized(this) {
                instance ?: DownloaderImpl(client).also { instance = it }
            }
        }
    }

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = OkHttpRequest.Builder().url(url)

        headers.forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        // Standard User-Agent for YouTube requests
        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        if (httpMethod == "POST") {
            val body = (dataToSend ?: ByteArray(0)).toRequestBody(null)
            builder.post(body)
        } else {
            builder.get()
        }

        val okHttpResponse = client.newCall(builder.build()).execute()
        val responseBodyString = okHttpResponse.body?.string() ?: ""
        val responseHeaders = okHttpResponse.headers.toMultimap()

        return Response(
            okHttpResponse.code,
            okHttpResponse.message,
            responseHeaders,
            responseBodyString,
            okHttpResponse.request.url.toString()
        )
    }
}
