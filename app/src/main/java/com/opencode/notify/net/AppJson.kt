package com.opencode.notify.net

import kotlinx.serialization.json.Json

object AppJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    inline fun <reified T> encodeToString(value: T): String = instance.encodeToString(value)

    inline fun <reified T> decodeFromString(text: String): T = instance.decodeFromString(text)
}
