package com.example.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.domain.model.ScheduledState

object LauncherIconManager {

    private const val ALIAS_DEFAULT = "com.example.LauncherDefault"
    private const val ALIAS_SERVICE = "com.example.LauncherService"
    private const val ALIAS_OUTAGE = "com.example.LauncherOutage"

    fun syncLauncherIcon(
        context: Context,
        dynamicEnabled: Boolean,
        primaryHomeState: ScheduledState?
    ) {
        val targetAlias = when {
            !dynamicEnabled -> ALIAS_DEFAULT
            primaryHomeState == ScheduledState.SERVICE_SCHEDULED -> ALIAS_SERVICE
            primaryHomeState == ScheduledState.OUTAGE_SCHEDULED -> ALIAS_OUTAGE
            else -> ALIAS_DEFAULT
        }

        val pm = context.packageManager
        val aliases = listOf(ALIAS_DEFAULT, ALIAS_SERVICE, ALIAS_OUTAGE)
        runCatching {
            aliases.forEach { aliasName ->
                val cn = ComponentName(context.packageName, aliasName)
                val desiredState = if (aliasName == targetAlias) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                if (pm.getComponentEnabledSetting(cn) != desiredState) {
                    pm.setComponentEnabledSetting(
                        cn,
                        desiredState,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}
