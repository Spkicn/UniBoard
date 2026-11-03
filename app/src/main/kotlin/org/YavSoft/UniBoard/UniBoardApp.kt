/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证第 3 版
 * 或（你选择的）任何更新版本所规定的条款，重新分发和/或修改它。
 */

package org.YavSoft.UniBoard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.material.color.DynamicColors
import org.YavSoft.UniBoard.core.CrashLog
import org.YavSoft.UniBoard.core.Prefs

/** 应用入口：按设置决定是否套用 Material You 动态取色 */
class UniBoardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashCatcher()
        if (Prefs(this).dynamicColor) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }

    /**
     * 全局兜底：任何线程漏出来的异常都先落盘、再交给系统处理。
     *
     * 这一层不吞异常（吞了会留下半死的进程），只负责把现场留下来，
     * 让「打开就闪退」变成一份可以直接读的堆栈。
     */
    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val where = "uncaught@" + (thread?.name ?: "?")
                val file = CrashLog.save(this, where, error)
                notifyCrash(error, file?.absolutePath)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** 通知栏也放一份：不用连电脑就能看到堆栈 */
    private fun notifyCrash(error: Throwable, path: String?) = runCatching {
        val nm = getSystemService(NotificationManager::class.java) ?: return@runCatching
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "崩溃日志", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val body = buildString {
            append(CrashLog.readableText(error)).append("\n\n")
            append("日志文件：").append(path ?: "见 logcat（tag UniBoardCrash）")
        }
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_ai)
            .setContentTitle("UniBoard 崩溃了，点开看堆栈")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(8000)))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFY_ID, n) }
        Log.e(TAG, "crash notification posted")
    }.onFailure { Log.e(TAG, "notification failed: " + it) }

    private companion object {
        const val TAG = "UniBoardCrash"
        const val CHANNEL = "uniboard_crash"
        const val NOTIFY_ID = 0x0BAD
    }
}
