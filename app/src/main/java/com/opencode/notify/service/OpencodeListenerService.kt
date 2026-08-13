package com.opencode.notify.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.opencode.notify.ConnectionStatus
import com.opencode.notify.LogStore
import com.opencode.notify.config.DEFAULT_NTFY_URL
import com.opencode.notify.config.ServerConfigRepository
import com.opencode.notify.model.PermissionRequest
import com.opencode.notify.model.QuestionApi
import com.opencode.notify.net.AppJson
import com.opencode.notify.net.NtfyClient
import com.opencode.notify.net.NtfyListener
import com.opencode.notify.net.NtfyMessage
import com.opencode.notify.notify.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpencodeListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ntfy: NtfyClient? = null
    private var backoffMs = 1000L

    @Volatile
    private var running = false

    private var topic = ""
    private var opencodeBaseUrl = ""
    private var username = "opencode"
    private var password = ""

    private val seenIds = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            startForeground(
                NotificationHelper.ID_STATUS,
                NotificationHelper.buildStatusNotification(this, "正在订阅 ntfy..."),
            )
            LogStore.setStatus(ConnectionStatus.CONNECTING)
            scope.launch {
                val cfg = ServerConfigRepository(this@OpencodeListenerService).current()
                topic = cfg.ntfyTopic
                opencodeBaseUrl = cfg.baseUrl
                username = cfg.username
                password = cfg.password
                connect()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        ntfy?.stop()
        ntfy = null
        scope.cancel()
        LogStore.setStatus(ConnectionStatus.DISCONNECTED)
        super.onDestroy()
    }

    private fun connect() {
        if (!running) return
        if (topic.isBlank()) {
            LogStore.add("未配置 ntfy 主题，请在设置中填写")
            updateStatusNotification("未配置 ntfy 主题")
            return
        }
        val url = "$DEFAULT_NTFY_URL/$topic/sse"
        LogStore.add("订阅 ntfy: $url")
        updateStatusNotification("订阅 ntfy ...")

        ntfy = NtfyClient(url).also { client ->
            client.connect(object : NtfyListener {
                override fun onOpen() {
                    backoffMs = 1000L
                    LogStore.setStatus(ConnectionStatus.CONNECTED)
                    LogStore.add("已连接 ntfy")
                    updateStatusNotification("已连接 ntfy")
                }

                override fun onMessage(message: NtfyMessage) = dispatch(message)

                override fun onFailure(error: Throwable?) {
                    if (!running) return
                    LogStore.setStatus(ConnectionStatus.ERROR)
                    LogStore.add("连接断开: ${error?.message ?: "未知错误"}")
                    updateStatusNotification("连接断开，${backoffMs / 1000}s 后重连")
                    scheduleReconnect()
                }

                override fun onClosed() {
                    if (!running) LogStore.setStatus(ConnectionStatus.DISCONNECTED)
                }
            })
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            if (running) connect()
        }
    }

    private fun updateStatusNotification(text: String) {
        NotificationManagerCompat.from(this).notify(
            NotificationHelper.ID_STATUS,
            NotificationHelper.buildStatusNotification(this, text),
        )
    }

    private fun dispatch(message: NtfyMessage) {
        if (message.event != "message") return
        if (message.id.isNotBlank() && !seenIds.add(message.id)) return
        if (message.message.isBlank()) return

        val payload = runCatching {
            AppJson.instance.parseToJsonElement(message.message).jsonObject
        }.getOrNull() ?: return

        val type = payload["type"]?.jsonPrimitive?.contentOrNull ?: return

        when (type) {
            "permission.asked" -> handlePermission(payload)
            "question.asked" -> handleQuestion(message.message)
            "session.idle" -> handleIdle(payload)
            "session.error" -> handleError(payload)
        }
    }

    private fun handlePermission(payload: JsonObject) {
        val id = payload["id"]?.jsonPrimitive?.contentOrNull ?: return
        val sessionId = payload["sessionID"]?.jsonPrimitive?.contentOrNull ?: ""
        val permission = payload["permission"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val patterns = payload["patterns"]?.jsonArray?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()

        val req = PermissionRequest(
            permissionId = id,
            sessionId = sessionId,
            permission = permission,
            title = "权限请求 · $permission",
            patterns = patterns,
        )
        LogStore.add("权限请求[$permission]: ${patterns.joinToString(" ")}")
        NotificationHelper.showPermission(this, req, opencodeBaseUrl, username, password)
    }

    private fun handleQuestion(raw: String) {
        val q = runCatching {
            AppJson.decodeFromString<QuestionApi>(raw).toRequest()
        }.getOrNull() ?: return
        LogStore.add("问题抛出: " + q.questions.joinToString(" | ") { it.question })
        NotificationHelper.showQuestion(this, q, opencodeBaseUrl, username, password)
    }

    private fun handleIdle(payload: JsonObject) {
        val sessionId = payload["sessionID"]?.jsonPrimitive?.contentOrNull
        LogStore.add("执行完成" + (sessionId?.let { " ($it)" } ?: ""))
        NotificationHelper.showCompletion(this, sessionId)
    }

    private fun handleError(payload: JsonObject) {
        val sessionId = payload["sessionID"]?.jsonPrimitive?.contentOrNull
        val msg = payload["message"]?.jsonPrimitive?.contentOrNull
        LogStore.add("执行失败: ${msg ?: "未知错误"}")
        NotificationHelper.showFailure(this, sessionId, msg)
    }

    companion object {
        const val ACTION_START = "com.opencode.notify.action.START"
        const val ACTION_STOP = "com.opencode.notify.action.STOP"
    }
}
