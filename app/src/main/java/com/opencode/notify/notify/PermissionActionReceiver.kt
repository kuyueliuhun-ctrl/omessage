package com.opencode.notify.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.opencode.notify.LogStore
import com.opencode.notify.net.OpencodeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PermissionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val baseUrl = extras.getString(Extras.BASE_URL) ?: return
        val username = extras.getString(Extras.USERNAME) ?: "opencode"
        val password = extras.getString(Extras.PASSWORD) ?: ""
        val sessionId = extras.getString(Extras.SESSION_ID) ?: return
        val permissionId = extras.getString(Extras.PERMISSION_ID) ?: return
        val response = extras.getString(Extras.RESPONSE) ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ok = OpencodeApi(baseUrl, username, password)
                    .replyPermission(sessionId, permissionId, response)
                LogStore.add(if (ok) "已回复权限($response)" else "权限回复失败")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
