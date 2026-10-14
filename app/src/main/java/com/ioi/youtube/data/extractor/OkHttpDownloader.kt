package com.ioi.youtube.data.extractor

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Адаптер, який дає NewPipe Extractor можливість робити HTTP-запити через OkHttp.
 */
@Singleton
class OkHttpDownloader @Inject constructor(
    private val client: OkHttpClient,
) : Downloader() {

    override fun execute(request: Request): Response {
        val mediaType = request.headers()["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull()
        val body: RequestBody? = request.dataToSend()?.let { bytes ->
            bytes.toRequestBody(mediaType)
        }

        val httpRequest = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .apply {
                request.headers().forEach { (name, values) ->
                    if (!name.equals("Content-Length", ignoreCase = true)) {
                        values.forEach { value -> addHeader(name, value) }
                    }
                }
            }
            .build()

        val httpResponse = try {
            client.newCall(httpRequest).execute()
        } catch (e: IOException) {
            throw IOException("NewPipe downloader: ${e.message}", e)
        }

        val responseBody = httpResponse.body?.string().orEmpty()
        val headers: Map<String, List<String>> = httpResponse.headers.toMultimap()

        return Response(
            httpResponse.code,
            httpResponse.message,
            headers,
            responseBody,
            httpResponse.request.url.toString(),
        )
    }
}
