package com.opencode.notify.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencode.notify.model.QuestionItem
import com.opencode.notify.model.QuestionRequest
import com.opencode.notify.net.AppJson
import com.opencode.notify.net.OpencodeApi
import com.opencode.notify.notify.Extras
import com.opencode.notify.ui.theme.OpenCodeNotifyTheme
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class QuestionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val requestId = intent.getStringExtra(Extras.REQUEST_ID)
        val questionsJson = intent.getStringExtra(Extras.QUESTIONS_JSON)
        val baseUrl = intent.getStringExtra(Extras.BASE_URL) ?: ""
        val username = intent.getStringExtra(Extras.USERNAME) ?: "opencode"
        val password = intent.getStringExtra(Extras.PASSWORD) ?: ""

        val request = runCatching {
            AppJson.decodeFromString<QuestionRequest>(questionsJson.orEmpty())
        }.getOrNull()

        if (request == null || requestId == null) {
            finish()
            return
        }

        setContent {
            OpenCodeNotifyTheme {
                QuestionScreen(request, baseUrl, username, password, onDone = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionScreen(
    request: QuestionRequest,
    baseUrl: String,
    username: String,
    password: String,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selected = remember { request.questions.map { mutableStateListOf<String>() } }
    val customText = remember { request.questions.map { mutableStateOf("") } }
    val submitting = remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("回答问题") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            request.questions.forEachIndexed { index, question ->
                QuestionBlock(index, question, selected[index], customText[index])
                Spacer(Modifier.height(20.dp))
            }

            Button(
                onClick = {
                    val answers = request.questions.mapIndexed { i, q ->
                        val custom = customText[i].value.trim()
                        if (custom.isNotEmpty()) listOf(custom) else selected[i].toList()
                    }
                    submitting.value = true
                    scope.launch {
                        OpencodeApi(baseUrl, username, password)
                            .replyQuestion(request.sessionId, request.requestId, answers)
                        submitting.value = false
                        onDone()
                    }
                },
                enabled = !submitting.value,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (submitting.value) "提交中..." else "提交答案") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        OpencodeApi(baseUrl, username, password)
                            .rejectQuestion(request.sessionId, request.requestId)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("跳过 / 拒绝回答") }
        }
    }
}

@Composable
private fun QuestionBlock(
    index: Int,
    question: QuestionItem,
    selected: SnapshotStateList<String>,
    customText: MutableState<String>,
) {
    Text(
        text = question.header ?: "问题 ${index + 1}",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(4.dp))
    Text(text = question.question, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))

    if (question.options.isNotEmpty()) {
        question.options.forEach { label ->
            val checked = selected.contains(label)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (question.multiple) {
                            if (checked) selected.remove(label) else selected.add(label)
                        } else {
                            selected.clear()
                            selected.add(label)
                        }
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = checked, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }

    if (question.custom) {
        OutlinedTextField(
            value = customText.value,
            onValueChange = { customText.value = it },
            label = { Text("自定义回答") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
