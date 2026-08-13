package com.opencode.notify.model

import kotlinx.serialization.Serializable

@Serializable
data class PermissionItem(
    val id: String,
    val sessionID: String,
    val permission: String,
    val patterns: List<String> = emptyList(),
    val always: List<String> = emptyList(),
) {
    fun toRequest(): PermissionRequest {
        val title = buildString {
            append(permission)
            if (patterns.isNotEmpty()) append(": ").append(patterns.joinToString(" "))
        }
        return PermissionRequest(
            permissionId = id,
            sessionId = sessionID,
            permission = permission,
            title = title,
            patterns = patterns,
        )
    }
}

@Serializable
data class QuestionOptionApi(
    val label: String,
    val description: String = "",
)

@Serializable
data class QuestionInfoApi(
    val question: String,
    val header: String = "",
    val options: List<QuestionOptionApi> = emptyList(),
    val multiple: Boolean = false,
    val custom: Boolean = true,
)

@Serializable
data class QuestionApi(
    val id: String,
    val sessionID: String,
    val questions: List<QuestionInfoApi> = emptyList(),
) {
    fun toRequest(): QuestionRequest = QuestionRequest(
        requestId = id,
        sessionId = sessionID,
        questions = questions.map { q ->
            QuestionItem(
                question = q.question,
                header = q.header.ifBlank { null },
                options = q.options.map { it.label },
                multiple = q.multiple,
                custom = q.custom,
            )
        },
    )
}

@Serializable
data class SessionStatusItem(
    val type: String = "idle",
    val message: String? = null,
    val attempt: Int? = null,
)
