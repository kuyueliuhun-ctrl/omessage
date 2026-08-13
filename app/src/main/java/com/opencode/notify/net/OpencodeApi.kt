package com.opencode.notify.net

import android.util.Base64
import com.opencode.notify.model.PermissionItem
import com.opencode.notify.model.QuestionApi
import com.opencode.notify.model.SessionStatusItem
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class QuestionReplyPayload(val answers: List<List<String>>)

class OpencodeApi(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val authHeader: String? = if (password.isNotBlank()) {
        "Basic " + Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    } else {
        null
    }

    private val jsonBodyType = "application/json; charset=utf-8".toMediaType()

    fun listPermissions(): List<PermissionItem> {
        val body = get("/permission")
        return if (body.isBlank()) emptyList() else AppJson.decodeFromString(body)
    }

    fun listQuestions(): List<QuestionApi> {
        val body = get("/question")
        return if (body.isBlank()) emptyList() else AppJson.decodeFromString(body)
    }

    fun listSessionStatus(): Map<String, SessionStatusItem> {
        val body = get("/session/status")
        return if (body.isBlank()) emptyMap() else AppJson.decodeFromString(body)
    }

    fun replyPermission(sessionId: String, permissionId: String, response: String): Boolean {
        val candidates = listOf(
            "/api/session/$sessionId/permission/$permissionId/reply" to """{"reply":"$response"}""",
            "/permission/$permissionId/reply" to """{"reply":"$response"}""",
            "/session/$sessionId/permissions/$permissionId" to """{"response":"$response"}""",
        )
        return candidates.any { (path, body) -> post(path, body) }
    }

    fun replyQuestion(sessionId: String, requestId: String, answers: List<List<String>>): Boolean {
        val body = AppJson.encodeToString(QuestionReplyPayload(answers))
        val candidates = listOf(
            "/api/session/$sessionId/question/$requestId/reply",
            "/question/$requestId/reply",
        )
        return candidates.any { post(it, body) }
    }

    fun rejectQuestion(sessionId: String, requestId: String): Boolean {
        val candidates = listOf(
            "/api/session/$sessionId/question/$requestId/reject",
            "/question/$requestId/reject",
        )
        return candidates.any { postEmpty(it) }
    }

    private fun get(path: String): String {
        val request = build(path).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    private fun post(path: String, jsonBody: String): Boolean {
        val request = build(path).post(jsonBody.toRequestBody(jsonBodyType)).build()
        return runCatching { execute(request)?.isSuccessful == true }.getOrDefault(false)
    }

    private fun postEmpty(path: String): Boolean {
        val request = build(path).post("".toRequestBody(null)).build()
        return runCatching { execute(request)?.isSuccessful == true }.getOrDefault(false)
    }

    private fun build(path: String): Request.Builder {
        val b = Request.Builder().url(baseUrl + path)
        authHeader?.let { b.header("Authorization", it) }
        return b
    }

    private fun execute(request: Request) = client.newCall(request).execute().use { it }
}
