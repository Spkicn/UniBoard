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

import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/**
 * 用户词频与二元上下文学习。
 *
 * 全部数据只保存在应用私有目录，不上传、不联网，符合自由软件与隐私原则。
 */
class UserHistory(context: Context) {

    private val file = File(context.filesDir, "user_history.tsv")
    private val unigram = HashMap<String, Int>(512)
    private val bigram = HashMap<String, HashMap<String, Int>>(256)
    private val io = Executors.newSingleThreadExecutor()

    @Volatile
    private var dirty = false

    fun load() {
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val parts = line.split('\t')
                when {
                    parts.size == 2 -> {
                        val c = parts[1].toIntOrNull() ?: return@forEachLine
                        unigram[parts[0]] = c
                    }
                    parts.size == 3 -> {
                        val c = parts[2].toIntOrNull() ?: return@forEachLine
                        bigram.getOrPut(parts[0]) { HashMap(8) }[parts[1]] = c
                    }
                }
            }
        }
    }

    /** 记录一次提交，prev 为上一个词（可为 null） */
    fun record(word: String, prev: String?) {
        if (word.isEmpty()) return
        unigram[word] = (unigram[word] ?: 0) + 1
        if (!prev.isNullOrEmpty()) {
            val m = bigram.getOrPut(prev) { HashMap(8) }
            m[word] = (m[word] ?: 0) + 1
        }
        trim()
        dirty = true
    }

    fun freqOf(word: String): Int = unigram[word] ?: 0

    /** 基于上一个词预测后续词 */
    fun nextWords(prev: String, limit: Int = 8): List<String> {
        val m = bigram[prev] ?: return emptyList()
        return m.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /** 用户自定义词条（导入词库时使用） */
    fun addWord(word: String, weight: Int = 30) {
        if (word.isEmpty()) return
        unigram[word] = (unigram[word] ?: 0) + weight
        trim()
        dirty = true
    }

    /** 批量导入用户词库（每词计一次较高权重），导入后立即落盘 */
    fun importBulk(words: List<String>, weight: Int = 30) {
        if (words.isEmpty()) return
        for (w in words) {
            if (w.isEmpty()) continue
            unigram[w] = (unigram[w] ?: 0) + weight
        }
        trim()
        saveBlocking()
    }

    /** 已学习的词条数量 */
    fun size(): Int = unigram.size

    fun clear() {
        unigram.clear()
        bigram.clear()
        dirty = true
        saveBlocking()
    }

    private fun trim() {
        if (unigram.size > MAX_UNIGRAM) {
            val keep = unigram.entries.sortedByDescending { it.value }.take(MAX_UNIGRAM)
            unigram.clear()
            keep.forEach { (k, v) -> unigram[k] = v }
        }
        if (bigram.size > MAX_BIGRAM) {
            val keep = bigram.entries
                .sortedByDescending { e -> e.value.values.sum() }
                .take(MAX_BIGRAM)
            bigram.clear()
            keep.forEach { (k, v) -> bigram[k] = v }
        }
    }

    /** 异步落盘 */
    fun save() {
        if (!dirty) return
        dirty = false
        io.execute { runCatching { writeFile() } }
    }

    fun saveBlocking() {
        dirty = false
        runCatching { writeFile() }
    }

    private fun writeFile() {
        val sb = StringBuilder(8192)
        for ((k, v) in unigram) sb.append(k).append('\t').append(v).append('\n')
        for ((prev, m) in bigram) {
            for ((next, c) in m) {
                sb.append(prev).append('\t').append(next).append('\t').append(c).append('\n')
            }
        }
        file.writeText(sb.toString())
    }

    private companion object {
        const val MAX_UNIGRAM = 4000
        const val MAX_BIGRAM = 1500
    }
}
