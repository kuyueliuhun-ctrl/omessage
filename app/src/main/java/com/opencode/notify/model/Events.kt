package com.opencode.notify.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class OpencodeEvent(
    val type: String,
    val properties: JsonObject,
    val directory: String? = null,
)

@Serializable
data class PermissionRequest(
    val permissionId: String,
    val sessionId: String,
    val permission: String,
    val title: String,
    val patterns: List<String> = emptyList(),
)

@Serializable
data class QuestionRequest(
    val requestId: String,
    val sessionId: String,
    val questions: List<QuestionItem>,
)

@Serializable
data class QuestionItem(
    val question: String,
    val header: String? = null,
    val options: List<String> = emptyList(),
    val multiple: Boolean = false,
    val custom: Boolean = true,
)

object EventTypes {
    const val SERVER_CONNECTED = "server.connected"
    const val SERVER_HEARTBEAT = "server.heartbeat"
    const val SESSION_IDLE = "session.idle"
    const val SESSION_ERROR = "session.error"
    const val PERMISSION_UPDATED = "permission.updated"
    const val PERMISSION_ASKED = "permission.asked"
    const val QUESTION_ASKED = "question.asked"

    fun isPermission(type: String): Boolean =
        type == PERMISSION_UPDATED || type == PERMISSION_ASKED
}

fun parseEvent(data: String): OpencodeEvent? {
    val text = data.trim()
    if (text.isEmpty()) return null
    return try {
        val root = Json.parseToJsonElement(text).jsonObject
        var obj = root
        var directory: String? = null
        val payload = root["payload"]
        if (payload is JsonObject) {
            directory = root["directory"]?.jsonPrimitive?.contentOrNull
            obj = payload
        }
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val props = obj["properties"] as? JsonObject ?: obj
        OpencodeEvent(type, props, directory)
    } catch (e: Exception) {
        null
    }
}

fun OpencodeEvent.sessionId(): String? = properties["sessionID"]?.jsonPrimitive?.contentOrNull

fun OpencodeEvent.toPermissionRequest(): PermissionRequest? {
    val id = properties.str("id") ?: properties.str("requestID") ?: return null
    val sessionId = properties.str("sessionID") ?: return null
    val permission = properties.str("type") ?: properties.str("permission") ?: "unknown"
    val patterns = properties.strList("patterns") ?: properties.strList("pattern")
    val title = properties.str("title")
        ?: properties.str("description")
        ?: buildString {
            append(permission)
            if (patterns.isNotEmpty()) append(" ").append(patterns.joinToString(" "))
        }
    return PermissionRequest(id, sessionId, permission, title, patterns)
}

fun OpencodeEvent.toQuestionRequest(): QuestionRequest? {
    val id = properties.str("id") ?: properties.str("requestID") ?: return null
    val sessionId = properties.str("sessionID") ?: ""
    val qs = properties["questions"] as? JsonArray ?: return null
    val questions = qs.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val question = obj.str("question") ?: return@mapNotNull null
        val options = (obj["options"] as? JsonArray)?.mapNotNull { opt ->
            (opt as? JsonObject)?.str("label")
        } ?: emptyList()
        QuestionItem(
            question = question,
            header = obj.str("header"),
            options = options,
            multiple = obj.boolean("multiple"),
            custom = obj.boolean("custom") ?: true,
        )
    }
    if (questions.isEmpty()) return null
    return QuestionRequest(id, sessionId, questions)
}

fun OpencodeEvent.toErrorMessage(): String? {
    val error = properties["error"] as? JsonObject ?: return null
    val data = error["data"] as? JsonObject
    return data?.str("message") ?: error.str("message") ?: error.str("name")
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.strList(key: String): List<String>? = when (val v = this[key]) {
    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .filter { it.isNotBlank() }
        .ifEmpty { null }
    is JsonPrimitive -> v.contentOrNull?.let { listOf(it) }
    else -> null
}
