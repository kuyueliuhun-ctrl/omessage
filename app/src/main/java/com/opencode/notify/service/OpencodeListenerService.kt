package com.opencode.notify.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationManagerCompat
import com.opencode.notify.ConnectionStatus
import com.opencode.notify.LogStore
import com.opencode.notify.config.ServerConfigRepository
import com.opencode.notify.model.EventTypes
import com.opencode.notify.model.parseEvent
import com.opencode.notify.model.sessionId
import com.opencode.notify.model.toErrorMessage
import com.opencode.notify.model.toPermissionRequest
import com.opencode.notify.model.toQuestionRequest
import com.opencode.notify.net.OpencodeApi
import com.opencode.notify.notify.NotificationHelper
import com.opencode.notify.sse.OpencodeEventSource
import com.opencode.notify.sse.SseListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OpencodeListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var source: OpencodeEventSource? = null
    private var backoffMs = 1000L

    @Volatile
    private var running = false

    private var baseUrl = ""
    private var username = "opencode"
    private var password = ""
    private var api: OpencodeApi? = null

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
                NotificationHelper.buildStatusNotification(this, "正在连接 opencode..."),
            )
            LogStore.setStatus(ConnectionStatus.CONNECTING)
            scope.launch {
                val cfg = ServerConfigRepository(this@OpencodeListenerService).current()
                baseUrl = cfg.baseUrl
                username = cfg.username
                password = cfg.password
                api = OpencodeApi(baseUrl, username, password)
                connect()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        source?.stop()
        source = null
        scope.cancel()
        LogStore.setStatus(ConnectionStatus.DISCONNECTED)
        super.onDestroy()
    }

    private fun connect() {
        if (!running) return
        val url = "$baseUrl/global/event"
        val authHeader = if (password.isNotBlank()) {
            "Basic " + Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } else {
            null
        }
        LogStore.add("连接 $url ...")
        updateStatusNotification("连接 $url ...")

        source = OpencodeEventSource(url, authHeader).also { src ->
            src.start(object : SseListener {
                override fun onOpen() {
                    backoffMs = 1000L
                    LogStore.setStatus(ConnectionStatus.CONNECTED)
                    LogStore.add("已连接")
                    updateStatusNotification("已连接 opencode")
                }

                override fun onMessage(data: String) = dispatch(data)

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

    private fun dispatch(data: String) {
        val event = parseEvent(data) ?: return
        when {
            event.type == EventTypes.SESSION_IDLE -> {
                val sid = event.sessionId()
                LogStore.add("执行完成" + (sid?.let { " ($it)" } ?: ""))
                NotificationHelper.showCompletion(this, sid)
            }

            event.type == EventTypes.SESSION_ERROR -> {
                val msg = event.toErrorMessage()
                LogStore.add("执行失败: ${msg ?: "未知错误"}")
                NotificationHelper.showFailure(this, event.sessionId(), msg)
            }

            EventTypes.isPermission(event.type) -> {
                val req = event.toPermissionRequest() ?: return
                if (req.permission == "question") {
                    scope.launch { api?.replyPermission(req.sessionId, req.permissionId, "once") }
                    LogStore.add("自动允许提问权限")
                    return
                }
                LogStore.add("权限请求[${req.permission}]: ${req.title}")
                NotificationHelper.showPermission(this, req, baseUrl, username, password)
            }

            event.type == EventTypes.QUESTION_ASKED -> {
                val q = event.toQuestionRequest() ?: return
                LogStore.add("问题抛出: " + q.questions.joinToString(" | ") { it.question })
                NotificationHelper.showQuestion(this, q, baseUrl, username, password)
            }
        }
    }

    companion object {
        const val ACTION_START = "com.opencode.notify.action.START"
        const val ACTION_STOP = "com.opencode.notify.action.STOP"
    }
}
