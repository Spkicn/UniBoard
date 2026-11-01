/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证第 3 版
 * 或（你选择的）任何更新版本所规定的条款，重新分发和/或修改它。
 */

package org.YavSoft.UniBoard.predict

import android.content.Context
import java.io.File

/**
 * 用户词典：AI 生成、手动导入、自建词条都落在这里。
 *
 * 存储格式（filesDir/user_lexicon.tsv，UTF-8，Tab 分隔）：
 *   word<TAB>pinyin<TAB>weight<TAB>source
 *
 * 导出给别人的文件也用同一套格式，导入回来能直接还原。
 */
class UserLexicon(context: Context) {

    data class Entry(
        val word: String,
        /** 全拼，可为空（英文词或用户没填） */
        val pinyin: String = "",
        val weight: Int = 100,
        val source: String = "user"
    )

    private val file = File(context.applicationContext.filesDir, "user_lexicon.tsv")
    private val entries = LinkedHashMap<String, Entry>()

    /** 拼音 -> 词条，按权重降序 */
    private var pinyinIndex: Map<String, List<PinyinLexicon.Word>> = emptyMap()
    private var initialsIndex: Map<String, List<PinyinLexicon.Word>> = emptyMap()
    private var englishIndex: Map<String, Int> = emptyMap()

    val size: Int get() = entries.size
    val isLoaded: Boolean get() = true

    fun load() {
        entries.clear()
        if (!file.exists()) {
            rebuildIndexes()
            return
        }
        runCatching {
            file.forEachLine { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachLine
                val parts = line.split('\t')
                val word = parts.getOrNull(0)?.trim().orEmpty()
                if (word.isEmpty()) return@forEachLine
                val py = parts.getOrNull(1)?.trim().orEmpty().lowercase()
                val weight = parts.getOrNull(2)?.toIntOrNull() ?: 100
                val source = parts.getOrNull(3)?.trim().orEmpty().ifEmpty { "user" }
                entries[word] = Entry(word, py, weight, source)
            }
        }
        rebuildIndexes()
    }

    fun all(): List<Entry> = entries.values.toList()

    fun add(entry: Entry) {
        entries[entry.word] = entry
        rebuildIndexes()
    }

    fun addAll(list: List<Entry>): Int {
        var added = 0
        for (e in list) {
            if (e.word.isBlank()) continue
            entries[e.word] = e
            added++
        }
        rebuildIndexes()
        return added
    }

    fun contains(word: String): Boolean = entries.containsKey(word)

    /** 命中已有词条时提升权重，让它更靠前 */
    fun bump(word: String, delta: Int = 5) {
        val e = entries[word] ?: return
        entries[word] = e.copy(weight = (e.weight + delta).coerceAtMost(2000))
        rebuildIndexes()
    }

    fun remove(word: String) {
        entries.remove(word)
        rebuildIndexes()
    }

    fun clear() {
        entries.clear()
        rebuildIndexes()
    }

    fun save() {
        runCatching {
            file.bufferedWriter().use { w ->
                w.write("# UniBoard 用户词典 v1\n")
                w.write("# word\tpinyin\tweight\tsource\n")
                for (e in entries.values) {
                    w.write(e.word + "\t" + e.pinyin + "\t" + e.weight + "\t" + e.source + "\n")
                }
            }
        }
    }

    /** 导出为可分享的文本 */
    fun exportText(): String {
        val sb = StringBuilder(entries.size * 24 + 64)
        sb.append("# UniBoard 用户词典 v1\n")
        sb.append("# word\tpinyin\tweight\tsource\n")
        for (e in entries.values) {
            sb.append(e.word).append('\t').append(e.pinyin).append('\t')
                .append(e.weight).append('\t').append(e.source).append('\n')
        }
        return sb.toString()
    }

    // ---- 查询接口，供 PredictionEngine 使用 ----

    fun exact(pinyin: String): List<PinyinLexicon.Word> = pinyinIndex[pinyin].orEmpty()

    fun prefix(pinyin: String, limit: Int = 24): List<PinyinLexicon.Word> {
        if (pinyin.isEmpty()) return emptyList()
        val out = ArrayList<PinyinLexicon.Word>(limit)
        for ((py, words) in pinyinIndex) {
            if (py.startsWith(pinyin)) {
                out.addAll(words.take(2))
                if (out.size >= limit) break
            }
        }
        return out
    }

    fun exactInitials(initials: String): List<PinyinLexicon.Word> =
        initialsIndex[initials].orEmpty()

    fun englishPrefix(prefix: String, limit: Int = 16): List<EnglishLexicon.Word> {
        if (prefix.isEmpty()) return emptyList()
        val out = ArrayList<EnglishLexicon.Word>(limit)
        for ((w, f) in englishIndex) {
            if (w.startsWith(prefix)) {
                out.add(EnglishLexicon.Word(w, f))
                if (out.size >= limit) break
            }
        }
        return out
    }

    private fun rebuildIndexes() {
        val pyMap = HashMap<String, MutableList<PinyinLexicon.Word>>()
        val iniMap = HashMap<String, MutableList<PinyinLexicon.Word>>()
        val enMap = HashMap<String, Int>()
        for (e in entries.values) {
            val py = e.pinyin.lowercase().filter { it in 'a'..'z' }
            if (py.isNotEmpty()) {
                pyMap.getOrPut(py) { ArrayList(4) }.add(PinyinLexicon.Word(e.word, e.weight))
                // 简拼：每个音节首字母（按空格 / 撇号切分，没有分隔就跳过）
                val syllables = e.pinyin.lowercase().split(' ', '\'', '　').filter { it.isNotBlank() }
                if (syllables.size > 1) {
                    val ini = syllables.joinToString("") { it.first().toString() }
                    if (ini.length in 2..8) {
                        iniMap.getOrPut(ini) { ArrayList(4) }
                            .add(PinyinLexicon.Word(e.word, e.weight))
                    }
                }
            } else if (e.word.all { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' }) {
                enMap[e.word.lowercase()] = e.weight
            }
        }
        pinyinIndex = pyMap.mapValues { (_, v) -> v.sortedByDescending { it.freq } }
        initialsIndex = iniMap.mapValues { (_, v) -> v.sortedByDescending { it.freq } }
        englishIndex = enMap
    }
}
