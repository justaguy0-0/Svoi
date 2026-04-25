package com.example.svoi.data

import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

object ImageDownloadProgress {
    private val flows = ConcurrentHashMap<String, MutableStateFlow<Float>>()

    fun flowFor(url: String): MutableStateFlow<Float> =
        flows.getOrPut(url) { MutableStateFlow(0f) }

    fun update(url: String, progress: Float) {
        flows[url]?.value = progress
    }

    fun release(url: String) {
        flows.remove(url)
    }
}

class ImageProgressInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val body = response.body ?: return response
        val contentLength = body.contentLength()
        if (contentLength <= 0L) return response
        val url = request.url.toString()
        val tracked = object : ForwardingSource(body.source()) {
            private var totalRead = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val n = super.read(sink, byteCount)
                if (n > 0L) {
                    totalRead += n
                    ImageDownloadProgress.update(
                        url,
                        (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                    )
                }
                return n
            }
        }
        val newBody = object : ResponseBody() {
            private val buffered = tracked.buffer()
            override fun contentType(): MediaType? = body.contentType()
            override fun contentLength(): Long = contentLength
            override fun source(): BufferedSource = buffered
        }
        return response.newBuilder().body(newBody).build()
    }
}