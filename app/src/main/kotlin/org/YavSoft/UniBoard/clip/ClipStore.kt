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

package org.YavSoft.UniBoard.clip

import android.content.Context
import android.content.ClipboardManager
import java.io.File
import java.util.concurrent.Executors

/**
 * 剪贴板片段仓库。
 *
 * Android 10 起系统不再向普通应用开放剪贴板历史，因此这里只提供
 * “当前剪贴板”读取 + 用户主动收藏的片段，全部保存在私有目录。
 */
class ClipStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(context.filesDir, "clip_snippets.txt")
    private val historyFile = File(context.filesDir, "clip_history.txt")
    /** 历史条目：文本 + 时间戳 */
    data class Item(val text: String, val ts: Long)
    private val history = ArrayList<Item>()
    private val io = Executors.newSingleThreadExecutor()
    private val items = ArrayList<String>()

    init {
        load()
        loadHistory()
    }

    private fun loadHistory() {
        if (!historyFile.exists()) return
        runCatching {
            historyFile.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                // 新格式：ts<TAB>text；老格式没有时间戳
                val tab = line.indexOf('\t')
                val item = if (tab > 0 && line.substring(0, tab).toLongOrNull() != null) {
                    Item(line.substring(tab + 1).replace("\\n", "\n"), line.substring(0, tab).toLong())
                } else {
                    Item(line.replace("\\n", "\n"), 0L)
                }
                if (item.text.isNotBlank()) history.add(item)
            }
        }
    }

    /** 最近使用过的剪贴板内容，最新在前 */
    fun recent(): List<Item> = history.toList()

    /** 记录一条历史（自动去重、带上时间戳，超过上限丢弃最旧） */
    fun pushHistory(text: String) {
        if (text.isBlank()) return
        history.removeAll { it.text == text }
        history.add(0, Item(text, System.currentTimeMillis()))
        val limit = runCatching { org.YavSoft.UniBoard.core.Prefs(appContext).clipboardLimit }
            .getOrDefault(500)
        if (limit > 0) {
            while (history.size > limit) history.removeAt(history.size - 1)
        }
        persistHistory()
    }

    /** 用过一次就把它顶到最前面 */
    fun touch(text: String) {
        val idx = history.indexOfFirst { it.text == text }
        if (idx <= 0) return
        val item = history.removeAt(idx).copy(ts = System.currentTimeMillis())
        history.add(0, item)
        persistHistory()
    }

    fun removeRecent(text: String) {
        if (history.removeAll { it.text == text }) persistHistory()
    }

    fun clearHistory() {
        history.clear()
        persistHistory()
    }

    /** 删除早于 minutes 分钟的历史；minutes<=0 不处理 */
    fun purgeOlderThan(minutes: Int) {
        if (minutes <= 0) return
        val cutoff = System.currentTimeMillis() - minutes * 60_000L
        val removed = history.removeAll { it.ts in 1..cutoff }
        if (removed) persistHistory()
    }

    /** 导出历史为可读文本 */
    fun exportHistory(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val sb = StringBuilder()
        for (it in history) {
            sb.append(if (it.ts > 0) fmt.format(java.util.Date(it.ts)) else "—").append('\t')
            sb.append(it.text.replace("\n", " ")).append('\n')
        }
        return sb.toString()
    }

    private fun persistHistory() {
        val snapshot = history.joinToString("\n") { it.ts.toString() + "\t" + it.text.replace("\n", "\\n") }
        io.execute { runCatching { historyFile.writeText(snapshot) } }
    }

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
        if (text.isBlank()) return
        items.remove(text)
        items.add(0, text)
        while (items.size > MAX) items.removeAt(items.size - 1)
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
        val snapshot = items.joinToString("\n") { it.replace("\n", "\\n") }
        io.execute { runCatching { file.writeText(snapshot) } }
    }

    /** 读取系统当前剪贴板文本，受系统权限限制可能为空 */
    fun readCurrent(): String? {
        return runCatching {
            val cm = appContext.getSystemService(ClipboardManager::class.java) ?: return null
            if (!cm.hasPrimaryClip()) return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0)?.coerceToText(appContext)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private companion object {
        const val MAX = 60
        const val HISTORY_MAX = 20
    }
}
