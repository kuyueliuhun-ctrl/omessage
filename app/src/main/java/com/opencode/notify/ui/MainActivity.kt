package com.opencode.notify.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.opencode.notify.ConnectionStatus
import com.opencode.notify.LogStore
import com.opencode.notify.config.ServerConfig
import com.opencode.notify.config.ServerConfigRepository
import com.opencode.notify.notify.NotificationHelper
import com.opencode.notify.service.OpencodeListenerService
import com.opencode.notify.ui.theme.OpenCodeNotifyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repo by lazy { ServerConfigRepository(applicationContext) }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            OpenCodeNotifyTheme {
                MainScreen(repo, onConnect = ::startListener, onDisconnect = ::stopListener)
            }
        }
    }

    private fun startListener(config: ServerConfig) {
        val intent = Intent(this, OpencodeListenerService::class.java)
            .setAction(OpencodeListenerService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopListener() {
        stopService(Intent(this, OpencodeListenerService::class.java))
    }
}

@Composable
fun MainScreen(
    repo: ServerConfigRepository,
    onConnect: (ServerConfig) -> Unit,
    onDisconnect: () -> Unit,
) {
    val status by LogStore.status.collectAsState()
    val logs by LogStore.entries.collectAsState()
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("4096") }
    var username by remember { mutableStateOf("opencode") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val c = repo.config.first()
        host = c.host
        port = c.port.toString()
        username = c.username
        password = c.password
    }

    Scaffold(topBar = { TopAppBar(title = { Text("OpenCode 通知") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item { StatusCard(status) }
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("服务器配置", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("主机 (IP 或域名)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码 (可选)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = {
                        val cfg = ServerConfig(
                            host = host,
                            port = port.toIntOrNull() ?: 4096,
                            username = username,
                            password = password,
                            enabled = true,
                        )
                        scope.launch { repo.update(cfg) }
                        onConnect(cfg)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存并连接") }
            }
            item {
                OutlinedButton(
                    onClick = { onDisconnect() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("断开连接") }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("事件日志", style = MaterialTheme.typography.titleMedium) }
            items(logs.reversed()) { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatusCard(status: ConnectionStatus) {
    val (text, color) = when (status) {
        ConnectionStatus.DISCONNECTED -> "未连接" to Color.Gray
        ConnectionStatus.CONNECTING -> "连接中..." to Color(0xFFFFA000)
        ConnectionStatus.CONNECTED -> "已连接" to Color(0xFF2E7D32)
        ConnectionStatus.ERROR -> "连接断开 (自动重连中)" to Color(0xFFC62828)
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}
