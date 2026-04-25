package com.example.svoi.data

import android.util.Log
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

/** Application-level interceptor: retries failed requests up to [maxRetries] times. */
class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastException: Exception? = null
        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || attempt == maxRetries) return response
                Log.w("RetryInterceptor", "attempt $attempt: HTTP ${response.code} for ${request.url.toString().takeLast(60)}, retrying")
                response.close()
            } catch (e: Exception) {
                lastException = e
                Log.w("RetryInterceptor", "attempt $attempt: ${e::class.simpleName}: ${e.message} for ${request.url.toString().takeLast(60)}")
                if (attempt == maxRetries) throw e
                Thread.sleep(1500L * (attempt + 1))
            }
            attempt++
        }
        throw lastException ?: IllegalStateException("RetryInterceptor: exhausted retries")
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
        val urlTail = url.takeLast(60)
        Log.d("ImageProgress", "download start: ${contentLength / 1024}KB  $urlTail")
        var lastLoggedPct = -1
        val downloadStart = System.currentTimeMillis()
        val tracked = object : ForwardingSource(body.source()) {
            private var totalRead = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val n = super.read(sink, byteCount)
                if (n > 0L) {
                    totalRead += n
                    val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                    ImageDownloadProgress.update(url, progress)
                    val pct = (progress * 100).toInt()
                    if (pct / 10 > lastLoggedPct / 10) {
                        lastLoggedPct = pct
                        val elapsed = System.currentTimeMillis() - downloadStart
                        Log.d("ImageProgress", "progress $pct% (${totalRead / 1024}/${contentLength / 1024}KB) in ${elapsed}ms  $urlTail")
                    }
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
