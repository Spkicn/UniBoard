/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证第 3 版
 * 或（你选择的）任何更新版本所规定的条款，重新分发和/或修改它。
 *
 * 本程序基于"希望它有用"的目的分发，但不提供任何担保，
 * 也不承诺适用于特定用途。完整条款见项目根目录的 LICENSE 文件。
 */

package org.YavSoft.UniBoard.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃落盘。
 *
 * 真机上「打开就闪退」时，用户拿不到 logcat，问题就变成猜谜。
 * 这里把堆栈同时写到三处，任何一个渠道能取到就够：
 *
 * 1. logcat（tag `UniBoardCrash`，debug 包可直接 `adb logcat -s UniBoardCrash`）
 * 2. 应用外部私有目录 `Android/data/<包名>/files/`（连电脑或用文件管理器可拷出）
 * 3. 内部 `files/crash/`（兜底，外部存储不可用时）
 */
object CrashLog {

    private const val TAG = "UniBoardCrash"

    /** 记录一次崩溃，返回落盘文件（可能为 null，但 logcat 一定有） */
    fun save(context: Context, where: String, t: Throwable): File? {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val body = buildString {
            append("time      : ").append(stamp).append('\n')
            append("where     : ").append(where).append('\n')
            append("device    : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("android   : ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("package   : ").append(context.packageName).append('\n')
            append("exception : ").append(t.javaClass.name)
            append(": ").append(t.message).append("\n\n")
            append(Log.getStackTraceString(t))
        }
        Log.e(TAG, body)

        val dirs = ArrayList<File>(2)
        runCatching { context.getExternalFilesDir(null)?.let { dirs.add(it) } }
        dirs.add(File(context.filesDir, "crash"))

        for (dir in dirs) {
            val usable = runCatching { dir.exists() || dir.mkdirs() }.getOrDefault(false)
            if (!usable) continue
            val file = runCatching {
                File(dir, "crash-" + System.currentTimeMillis() + ".txt").apply { writeText(body) }
            }.getOrNull()
            if (file != null) {
                Log.e(TAG, "saved to " + file.absolutePath)
                return file
            }
        }
        return null
    }

    /** 最近一次崩溃记录（用于下次启动时把上次的堆栈显示出来） */
    fun latest(context: Context): File? {
        val dirs = ArrayList<File>(2)
        runCatching { context.getExternalFilesDir(null)?.let { dirs.add(it) } }
        dirs.add(File(context.filesDir, "crash"))

        var best: File? = null
        for (dir in dirs) {
            val files = runCatching {
                dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") }
            }.getOrNull() ?: continue
            for (f in files) {
                val current = best
                if (current == null || f.lastModified() > current.lastModified()) best = f
            }
        }
        return best
    }

    /** 把可读的堆栈文本整理成适合直接显示在界面上的样子 */
    fun readableText(t: Throwable): String =
        t.javaClass.name + (t.message?.let { ": $it" } ?: "") + "\n\n" + Log.getStackTraceString(t)
}
