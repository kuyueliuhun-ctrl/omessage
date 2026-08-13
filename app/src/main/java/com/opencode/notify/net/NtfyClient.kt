package com.opencode.notify.net

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

interface NtfyListener {
    fun onOpen()
    fun onMessage(message: NtfyMessage)
    fun onFailure(error: Throwable?)
    fun onClosed()
}

data class NtfyMessage(
    val id: String,
    val event: String,
    val message: String,
    val title: String?,
)

class NtfyClient(private val url: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var running = false

    private var thread: Thread? = null

    fun connect(listener: NtfyListener) {
        running = true
        thread = Thread {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        listener.onFailure(IOException("HTTP ${resp.code}"))
                        return@use
                    }
                    listener.onOpen()
                    val source = resp.body?.source()
                        ?: run { listener.onFailure(IOException("empty body")); return@use }
                    while (running) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data.isNotEmpty()) {
                                parseMessage(data)?.let(listener::onMessage)
                            }
                        }
                    }
                }
                if (!running) listener.onClosed()
            } catch (e: Exception) {
                if (running) listener.onFailure(e)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun parseMessage(data: String): NtfyMessage? = runCatching {
        val obj = AppJson.instance.parseToJsonElement(data).jsonObject
        NtfyMessage(
            id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
            event = obj["event"]?.jsonPrimitive?.contentOrNull ?: "",
            message = obj["message"]?.jsonPrimitive?.contentOrNull ?: "",
            title = obj["title"]?.jsonPrimitive?.contentOrNull,
        )
    }.getOrNull()
}
