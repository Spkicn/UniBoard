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

package org.YavSoft.UniBoard.unicode

import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/** 最近使用过的自定义字符，仅保存在应用私有目录 */
class RecentChars(context: Context) {

    private val file = File(context.filesDir, "recent_chars.txt")
    private val items = LinkedHashSet<Int>()
    private val io = Executors.newSingleThreadExecutor()

    fun load() {
        if (!file.exists()) return
        runCatching {
            file.readLines().forEach { line ->
                line.trim().toIntOrNull()?.let { items.add(it) }
            }
        }
    }

    fun add(codePoint: Int) {
        if (codePoint <= 0) return
        items.remove(codePoint)
        val list = items.toMutableList()
        list.add(0, codePoint)
        items.clear()
        items.addAll(list.take(MAX))
        save()
    }

    fun list(limit: Int = 40): List<Int> = items.take(limit)

    private fun save() {
        val snapshot = items.joinToString("\n")
        io.execute {
            runCatching { file.writeText(snapshot) }
        }
    }

    private companion object {
        const val MAX = 120
    }
}
