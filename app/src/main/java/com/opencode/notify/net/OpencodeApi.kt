package com.opencode.notify.net

import android.util.Base64
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    fun health(): Boolean = runCatching {
        execute(build("/global/health").build())?.isSuccessful == true
    }.getOrDefault(false)

    fun replyPermission(sessionId: String, permissionId: String, response: String): Boolean {
        val candidates = listOf(
            "/session/$sessionId/permissions/$permissionId" to """{"response":"$response"}""",
            "/api/permission/$permissionId/reply" to """{"reply":"$response"}""",
            "/permission/$permissionId/reply" to """{"reply":"$response"}""",
        )
        return candidates.any { (path, body) -> post(path, body) }
    }

    fun replyQuestion(requestId: String, answers: List<List<String>>): Boolean {
        val body = AppJson.encodeToString(QuestionReplyPayload(answers))
        val candidates = listOf(
            "/api/question/$requestId/reply",
            "/question/$requestId/reply",
        )
        return candidates.any { post(it, body) }
    }

    fun rejectQuestion(requestId: String): Boolean {
        val candidates = listOf(
            "/api/question/$requestId/reject",
            "/question/$requestId/reject",
        )
        return candidates.any { postEmpty(it) }
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
