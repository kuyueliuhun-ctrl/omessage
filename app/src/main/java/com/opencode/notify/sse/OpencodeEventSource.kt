package com.opencode.notify.sse

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

interface SseListener {
    fun onOpen()
    fun onMessage(data: String)
    fun onFailure(error: Throwable?)
    fun onClosed()
}

class OpencodeEventSource(
    private val url: String,
    private val authHeader: String?,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val factory = EventSources.createFactory(client)
    private var source: EventSource? = null

    fun start(listener: SseListener) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .apply { authHeader?.let { header("Authorization", it) } }
            .build()

        source = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                if (!response.isSuccessful) {
                    listener.onFailure(IOException("HTTP ${response.code}"))
                    eventSource.cancel()
                    return
                }
                listener.onOpen()
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                listener.onMessage(data)
            }

            override fun onClosed(eventSource: EventSource) {
                listener.onClosed()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                listener.onFailure(t ?: IOException("SSE connection failed"))
            }
        })
    }

    fun stop() {
        source?.cancel()
        source = null
    }
}
