package com.ghostnexora.vpn.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

object ProcessUtils {
    fun isMainProcess(context: Context): Boolean =
        currentProcessName(context) == context.packageName

    private fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }

        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager
            ?.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
            .orEmpty()
    }
}
