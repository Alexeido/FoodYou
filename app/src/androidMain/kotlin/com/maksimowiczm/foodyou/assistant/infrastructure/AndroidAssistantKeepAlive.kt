package com.maksimowiczm.foodyou.assistant.infrastructure

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.maksimowiczm.foodyou.assistant.domain.AssistantKeepAlive

internal class AndroidAssistantKeepAlive(private val context: Context) : AssistantKeepAlive {
    override fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, AssistantKeepAliveService::class.java),
        )
    }

    override fun stop() {
        context.stopService(Intent(context, AssistantKeepAliveService::class.java))
    }
}
