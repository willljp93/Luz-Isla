package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PrimaryFavoriteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdateHelper.refreshAllWidgets(context)
                WidgetRefreshWorker.ensureScheduled(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdateHelper.ACTION_WIDGET_REFRESH) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    WidgetUpdateHelper.refreshAllWidgets(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

class ConfigurableCircuitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdateHelper.refreshAllWidgets(context)
                WidgetRefreshWorker.ensureScheduled(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                UserPreferencesRepository(context).removeWidgetCircuits(appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdateHelper.ACTION_WIDGET_REFRESH) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    WidgetUpdateHelper.refreshAllWidgets(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        WidgetUpdateHelper.refreshAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "luz_isla_widget_refresh"

        fun ensureScheduled(context: Context) {
            val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
