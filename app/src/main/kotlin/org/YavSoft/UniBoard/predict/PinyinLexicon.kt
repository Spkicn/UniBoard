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

package org.YavSoft.UniBoard.predict

import java.io.BufferedReader

/**
 * 拼音词库索引。
 *
 * 数据格式（assets/lexicon/zh_pinyin.txt）：
 *   pinyin<TAB>词:词频,词:词频,...
 * 每个拼音串一行，行内按词频降序。
 */
class PinyinLexicon {

    data class Word(val text: String, val freq: Int)

    private var keys: Array<String> = emptyArray()
    private var buckets: Array<List<Word>> = emptyArray()

    val size: Int get() = keys.size
    val isLoaded: Boolean get() = keys.isNotEmpty()

    fun load(reader: BufferedReader) {
        val keyList = ArrayList<String>(70000)
        val bucketList = ArrayList<List<Word>>(70000)
        reader.forEachLine { line ->
            if (line.isEmpty()) return@forEachLine
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEachLine
            val py = line.substring(0, tab)
            val payload = line.substring(tab + 1)
            if (payload.isEmpty()) return@forEachLine
            val words = ArrayList<Word>(8)
            var start = 0
            while (start < payload.length) {
                var end = payload.indexOf(',', start)
                if (end < 0) end = payload.length
                val item = payload.substring(start, end)
                val sep = item.lastIndexOf(':')
                if (sep > 0) {
                    val w = item.substring(0, sep)
                    val f = item.substring(sep + 1).toIntOrNull() ?: 0
                    words.add(Word(w, f))
                }
                start = end + 1
            }
            if (words.isNotEmpty()) {
                keyList.add(py)
                bucketList.add(words)
            }
        }
        keys = keyList.toTypedArray()
        buckets = bucketList.toTypedArray()
    }

    /** 精确拼音匹配 */
    fun exact(pinyin: String): List<Word> {
        if (keys.isEmpty()) return emptyList()
        val idx = lowerBound(pinyin)
        return if (idx < keys.size && keys[idx] == pinyin) buckets[idx] else emptyList()
    }

    /** 前缀匹配：输入未完整时补全更长的拼音组，如 "zhongg" -> "zhongguo" 组 */
    fun prefix(pinyin: String, maxKeys: Int = 320, perKey: Int = 2): List<Word> {
        if (keys.isEmpty() || pinyin.isEmpty()) return emptyList()
        val from = lowerBound(pinyin)
        if (from >= keys.size || !keys[from].startsWith(pinyin)) return emptyList()
        val out = ArrayList<Word>(perKey * 24)
        var i = from
        var scanned = 0
        while (i < keys.size && scanned < maxKeys) {
            if (!keys[i].startsWith(pinyin)) break
            val bucket = buckets[i]
            var n = 0
            while (n < bucket.size && n < perKey) {
                out.add(bucket[n])
                n++
            }
            i++
            scanned++
        }
        return out
    }

    fun contains(pinyin: String): Boolean {
        if (keys.isEmpty()) return false
        val idx = lowerBound(pinyin)
        return idx < keys.size && keys[idx] == pinyin
    }

    private fun lowerBound(target: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < target) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
