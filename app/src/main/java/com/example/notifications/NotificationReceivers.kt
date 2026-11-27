package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScheduleNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        lastDispatchJob = CoroutineScope(backgroundDispatcher).launch {
            try {
                handleAlarmTrigger(context, intent, nowMillisProvider())
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        @Volatile
        @VisibleForTesting
        var backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO

        @Volatile
        @VisibleForTesting
        var nowMillisProvider: () -> Long = { System.currentTimeMillis() }

        @Volatile
        @VisibleForTesting
        internal var lastDispatchJob: Job? = null

        @VisibleForTesting
        internal suspend fun awaitLastDispatch() {
            lastDispatchJob?.join()
        }

        @VisibleForTesting
        fun resetTestOverrides() {
            lastDispatchJob = null
            backgroundDispatcher = Dispatchers.IO
            nowMillisProvider = { System.currentTimeMillis() }
        }

        internal suspend fun handleAlarmTrigger(
            context: Context,
            intent: Intent,
            nowMillis: Long = System.currentTimeMillis()
        ) {
            val title = intent.getStringExtra(ScheduleNotificationManager.EXTRA_TITLE)
            val body = intent.getStringExtra(ScheduleNotificationManager.EXTRA_BODY)
            if (title != null && body != null) {
                val circuitCode = intent.getStringExtra(ScheduleNotificationManager.EXTRA_CIRCUIT_CODE)
                val notifId = intent.getIntExtra(ScheduleNotificationManager.EXTRA_NOTIF_ID, 2001)
                ScheduleNotificationManager.postNotification(
                    context = context,
                    notificationId = notifId,
                    channelId = ScheduleNotificationManager.CHANNEL_ALERTS,
                    title = title,
                    body = body,
                    circuitCode = circuitCode
                )
            }
            ScheduleNotificationManager.rescheduleUpcomingNotifications(
                context = context,
                nowMillis = nowMillis
            )
            WidgetUpdateHelper.refreshAllWidgets(context)
        }
    }
}

class BootAndTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            lastDispatchJob = CoroutineScope(backgroundDispatcher).launch {
                try {
                    handleSystemEvent(context, nowMillisProvider())
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    companion object {
        @Volatile
        @VisibleForTesting
        var backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO

        @Volatile
        @VisibleForTesting
        var nowMillisProvider: () -> Long = { System.currentTimeMillis() }

        @Volatile
        @VisibleForTesting
        internal var lastDispatchJob: Job? = null

        @VisibleForTesting
        internal suspend fun awaitLastDispatch() {
            lastDispatchJob?.join()
        }

        @VisibleForTesting
        fun resetTestOverrides() {
            lastDispatchJob = null
            backgroundDispatcher = Dispatchers.IO
            nowMillisProvider = { System.currentTimeMillis() }
        }

        internal suspend fun handleSystemEvent(
            context: Context,
            nowMillis: Long = System.currentTimeMillis()
        ) {
            ScheduleNotificationManager.rescheduleUpcomingNotifications(
                context = context,
                nowMillis = nowMillis
            )
            WidgetUpdateHelper.refreshAllWidgets(context)
        }
    }
}
