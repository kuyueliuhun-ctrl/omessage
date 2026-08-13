package com.opencode.notify.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.opencode.notify.R
import com.opencode.notify.model.PermissionRequest
import com.opencode.notify.model.QuestionRequest
import com.opencode.notify.net.AppJson
import com.opencode.notify.ui.MainActivity
import com.opencode.notify.ui.QuestionActivity

object NotificationHelper {

    const val CHANNEL_STATUS = "opencode_status"
    const val CHANNEL_PERMISSION = "opencode_permission"
    const val CHANNEL_QUESTION = "opencode_question"
    const val CHANNEL_COMPLETION = "opencode_completion"
    const val CHANNEL_ERROR = "opencode_error"

    const val ID_STATUS = 1
    private const val ID_COMPLETION = 2
    private const val ID_ERROR = 3
    private const val ID_PERMISSION_BASE = 1000
    private const val ID_QUESTION_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "连接状态", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PERMISSION, "权限请求", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUESTION, "问题", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COMPLETION, "执行完成", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ERROR, "执行失败", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )
    }

    fun buildStatusNotification(context: Context, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("OpenCode 监控")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showCompletion(context: Context, sessionId: String?) {
        val text = if (sessionId.isNullOrBlank()) "任务执行完成" else "任务执行完成 ($sessionId)"
        val n = NotificationCompat.Builder(context, CHANNEL_COMPLETION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("执行完成")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notify(context, ID_COMPLETION, n)
    }

    fun showFailure(context: Context, sessionId: String?, message: String?) {
        val detail = buildString {
            append("opencode 执行失败")
            if (!sessionId.isNullOrBlank()) append("\n会话: $sessionId")
            if (!message.isNullOrBlank()) append("\n$message")
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ERROR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("执行失败")
            .setContentText(message ?: "执行出错")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notify(context, ID_ERROR, n)
    }

    fun showPermission(
        context: Context,
        req: PermissionRequest,
        baseUrl: String,
        username: String,
        password: String,
    ) {
        val text = buildString {
            append(req.title)
            if (req.patterns.isNotEmpty()) append("\n\n").append(req.patterns.joinToString("\n"))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PERMISSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("权限请求 · ${req.permission}")
            .setContentText(req.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction(context, req, baseUrl, username, password, "once", "允许一次"))
            .addAction(replyAction(context, req, baseUrl, username, password, "always", "总是允许"))
            .addAction(replyAction(context, req, baseUrl, username, password, "reject", "拒绝"))

        val id = ID_PERMISSION_BASE + (req.permissionId.hashCode() and 0x7fffffff) % 100000
        notify(context, id, builder.build())
    }

    fun showQuestion(
        context: Context,
        q: QuestionRequest,
        baseUrl: String,
        username: String,
        password: String,
    ) {
        val first = q.questions.firstOrNull()
        val title = "opencode 提问" + (first?.header?.let { " · $it" } ?: "")
        val text = first?.question ?: "AI 需要你的回答"
        val fullScreenIntent = questionPendingIntent(context, q, baseUrl, username, password)

        val builder = NotificationCompat.Builder(context, CHANNEL_QUESTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(fullScreenIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(fullScreenIntent, true)

        val id = ID_QUESTION_BASE + (q.requestId.hashCode() and 0x7fffffff) % 100000
        notify(context, id, builder.build())
    }

    private fun replyAction(
        context: Context,
        req: PermissionRequest,
        baseUrl: String,
        username: String,
        password: String,
        response: String,
        label: String,
    ): NotificationCompat.Action {
        val intent = Intent(context, PermissionActionReceiver::class.java).apply {
            putExtra(Extras.BASE_URL, baseUrl)
            putExtra(Extras.USERNAME, username)
            putExtra(Extras.PASSWORD, password)
            putExtra(Extras.SESSION_ID, req.sessionId)
            putExtra(Extras.PERMISSION_ID, req.permissionId)
            putExtra(Extras.RESPONSE, response)
        }
        val requestCode = (req.permissionId.hashCode() and 0x7fffffff) * 10 + response.hashCode()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    private fun questionPendingIntent(
        context: Context,
        q: QuestionRequest,
        baseUrl: String,
        username: String,
        password: String,
    ): PendingIntent {
        val intent = Intent(context, QuestionActivity::class.java).apply {
            putExtra(Extras.REQUEST_ID, q.requestId)
            putExtra(Extras.QUESTIONS_JSON, AppJson.encodeToString(q))
            putExtra(Extras.BASE_URL, baseUrl)
            putExtra(Extras.USERNAME, username)
            putExtra(Extras.PASSWORD, password)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            q.requestId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // notification permission not granted
        }
    }
}
