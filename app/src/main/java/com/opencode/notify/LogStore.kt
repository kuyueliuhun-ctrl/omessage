package com.opencode.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

object LogStore {
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun add(entry: String) {
        val stamp = fmt.format(Date())
        _entries.update { (it + "[$stamp] $entry").takeLast(300) }
    }

    fun setStatus(status: ConnectionStatus) {
        _status.value = status
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
