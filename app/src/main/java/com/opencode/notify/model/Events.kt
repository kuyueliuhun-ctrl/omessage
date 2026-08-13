package com.opencode.notify.model

import kotlinx.serialization.Serializable

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
