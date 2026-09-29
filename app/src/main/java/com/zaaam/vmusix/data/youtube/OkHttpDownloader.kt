package com.zaaam.vmusix.data.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Downloader NewPipeExtractor di atas OkHttp shared. Timeouts eksplisit untuk jaringan lambat. */
@Singleton
class OkHttpDownloader @Inject constructor() : Downloader() {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val builder = Request.Builder().url(request.url())
        var hasUA = false
        for ((k, v) in request.headers()) {
            if (k.equals("User-Agent", ignoreCase = true)) hasUA = true
            for (value in v) {
                try {
                    builder.addHeader(k, value)
                } catch (_: Exception) {
                }
            }
        }
        if (!hasUA) {
            builder.addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; OPPO A60) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36",
            )
        }
        val body = request.dataToSend()?.toRequestBody()
        when (request.httpMethod()) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody())
            "HEAD" -> builder.head()
            else -> builder.method(request.httpMethod(), body)
        }
        client.newCall(builder.build()).execute().use { resp ->
            val headers = mutableMapOf<String, MutableList<String>>()
            for ((k, v) in resp.headers) {
                headers.getOrPut(k) { mutableListOf() }.add(v)
            }
            val respBody = resp.body?.string() ?: ""
            return Response(
                resp.code,
                resp.message,
                headers,
                respBody,
                resp.request.url.toString(),
            )
        }
    }
}
