/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 * 本程序依 GPL-3.0-or-later 授权。
 */

package org.YavSoft.UniBoard.clip

import android.content.Context
import java.io.File

/** 常用语：手机上高频输入的整句（地址、邮箱、自我介绍等），在剪贴板面板里一键插入 */
class QuickPhrases(context: Context) {

    private val file = File(context.applicationContext.filesDir, "quick_phrases.txt")
    private val items = ArrayList<String>()

    init { load() }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            file.readLines().forEach { line ->
                val t = line.replace("\\n", "\n")
                if (t.isNotBlank()) items.add(t)
            }
        }
    }

    fun list(): List<String> = items.toList()

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        items.remove(t)
        items.add(0, t)
        persist()
    }

    fun remove(text: String) {
        if (items.remove(text)) persist()
    }

    fun clear() {
        items.clear()
        persist()
    }

    private fun persist() {
        runCatching {
            file.writeText(items.joinToString("\n") { it.replace("\n", "\\n") })
        }
    }
}
