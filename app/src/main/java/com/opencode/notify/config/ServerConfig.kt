package com.opencode.notify.config

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "opencode_notify_config")

const val DEFAULT_NTFY_URL = "https://ntfy.sh"

data class ServerConfig(
    val host: String = "",
    val port: Int = 4096,
    val username: String = "opencode",
    val password: String = "",
    val ntfyTopic: String = "",
    val enabled: Boolean = false,
) {
    val baseUrl: String
        get() {
            val h = host.trim().replace(Regex("^https?://"), "").trimEnd('/')
            if (h.isEmpty()) return ""
            return "http://$h:$port"
        }

    val ntfyUrl: String get() = DEFAULT_NTFY_URL
}

class ServerConfigRepository(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
        val ENABLED = booleanPreferencesKey("enabled")
    }

    val config: Flow<ServerConfig> = context.dataStore.data.map { p ->
        ServerConfig(
            host = p[Keys.HOST] ?: "",
            port = p[Keys.PORT] ?: 4096,
            username = p[Keys.USERNAME] ?: "opencode",
            password = p[Keys.PASSWORD] ?: "",
            ntfyTopic = p[Keys.NTFY_TOPIC] ?: "",
            enabled = p[Keys.ENABLED] ?: false,
        )
    }

    suspend fun current(): ServerConfig = config.first()

    suspend fun update(config: ServerConfig) {
        context.dataStore.edit { p ->
            p[Keys.HOST] = config.host
            p[Keys.PORT] = config.port
            p[Keys.USERNAME] = config.username
            p[Keys.PASSWORD] = config.password
            p[Keys.NTFY_TOPIC] = config.ntfyTopic
            p[Keys.ENABLED] = config.enabled
        }
    }
}
