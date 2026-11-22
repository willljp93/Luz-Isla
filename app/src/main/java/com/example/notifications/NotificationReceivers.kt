package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ScheduleNotificationManager.EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(ScheduleNotificationManager.EXTRA_BODY) ?: return
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

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdateHelper.refreshAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BootAndTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ScheduleNotificationManager.rescheduleUpcomingNotifications(context)
                    WidgetUpdateHelper.refreshAllWidgets(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
