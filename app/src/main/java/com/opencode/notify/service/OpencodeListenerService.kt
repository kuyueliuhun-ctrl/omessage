package com.opencode.notify.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.opencode.notify.ConnectionStatus
import com.opencode.notify.LogStore
import com.opencode.notify.config.ServerConfigRepository
import com.opencode.notify.model.PermissionItem
import com.opencode.notify.model.QuestionApi
import com.opencode.notify.model.SessionStatusItem
import com.opencode.notify.net.OpencodeApi
import com.opencode.notify.notify.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OpencodeListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var running = false

    private var baseUrl = ""
    private var username = "opencode"
    private var password = ""
    private var api: OpencodeApi? = null

    private val seenPermissions = mutableSetOf<String>()
    private val seenQuestions = mutableSetOf<String>()
    private val lastStatus = mutableMapOf<String, String>()

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
                pollLoop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        LogStore.setStatus(ConnectionStatus.DISCONNECTED)
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        var backoff = 1000L
        while (running) {
            val ok = pollOnce()
            if (ok) {
                backoff = 1000L
                if (LogStore.status.value != ConnectionStatus.CONNECTED) {
                    LogStore.setStatus(ConnectionStatus.CONNECTED)
                    LogStore.add("已连接 opencode")
                    updateStatusNotification("已连接 opencode")
                }
                delay(POLL_INTERVAL_MS)
            } else {
                LogStore.setStatus(ConnectionStatus.ERROR)
                updateStatusNotification("连接断开，${backoff / 1000}s 后重连")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun pollOnce(): Boolean {
        val a = api ?: return false
        return try {
            processPermissions(a.listPermissions())
            processQuestions(a.listQuestions())
            processStatuses(a.listSessionStatus())
            true
        } catch (e: Exception) {
            LogStore.add("轮询失败: ${e.message}")
            false
        }
    }

    private fun processPermissions(items: List<PermissionItem>) {
        val currentIds = items.map { it.id }.toHashSet()
        seenPermissions.retainAll(currentIds)
        for (item in items) {
            if (!seenPermissions.add(item.id)) continue
            if (item.permission == "question") {
                scope.launch { api?.replyPermission(item.sessionID, item.id, "once") }
                LogStore.add("自动允许提问权限")
                continue
            }
            val req = item.toRequest()
            LogStore.add("权限请求[${req.permission}]: ${req.title}")
            NotificationHelper.showPermission(this, req, baseUrl, username, password)
        }
    }

    private fun processQuestions(items: List<QuestionApi>) {
        val currentIds = items.map { it.id }.toHashSet()
        seenQuestions.retainAll(currentIds)
        for (item in items) {
            if (!seenQuestions.add(item.id)) continue
            val q = item.toRequest()
            LogStore.add("问题抛出: " + q.questions.joinToString(" | ") { it.question })
            NotificationHelper.showQuestion(this, q, baseUrl, username, password)
        }
    }

    private fun processStatuses(statuses: Map<String, SessionStatusItem>) {
        for ((sessionId, status) in statuses) {
            val prev = lastStatus[sessionId]
            val curr = status.type
            when {
                prev == "busy" && curr == "idle" -> {
                    LogStore.add("执行完成 ($sessionId)")
                    NotificationHelper.showCompletion(this, sessionId)
                }
                prev == "busy" && curr == "retry" -> {
                    LogStore.add("执行失败 ($sessionId): ${status.message.orEmpty()}")
                    NotificationHelper.showFailure(this, sessionId, status.message)
                }
            }
            lastStatus[sessionId] = curr
        }
        lastStatus.keys.retainAll(statuses.keys)
    }

    private fun updateStatusNotification(text: String) {
        NotificationManagerCompat.from(this).notify(
            NotificationHelper.ID_STATUS,
            NotificationHelper.buildStatusNotification(this, text),
        )
    }

    companion object {
        const val ACTION_START = "com.opencode.notify.action.START"
        const val ACTION_STOP = "com.opencode.notify.action.STOP"
        private const val POLL_INTERVAL_MS = 1500L
    }
}
