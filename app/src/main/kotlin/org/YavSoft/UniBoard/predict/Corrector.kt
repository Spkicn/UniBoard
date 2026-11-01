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

import kotlin.math.abs

/** 中文拼音模糊音纠错：生成等价输入变体 */
object FuzzyPinyin {

    private val RULES: List<Pair<String, String>> = listOf(
        "zh" to "z", "ch" to "c", "sh" to "s",
        "iang" to "ian", "uang" to "uan", "iong" to "ion",
        "ang" to "an", "eng" to "en", "ing" to "in",
        "ian" to "ie", "uan" to "un",
        "n" to "l", "f" to "h", "r" to "l", "h" to "w"
    )

    /**
     * 生成输入串的所有模糊音变体（含原串，原串在首位）。
     * 变体数量有上限，避免组合爆炸。
     */
    fun variants(input: String, max: Int = 40): List<String> {
        if (input.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>(16)
        seen.add(input)
        val queue = ArrayDeque<String>()
        queue.addLast(input)
        while (queue.isNotEmpty() && seen.size < max) {
            val cur = queue.removeFirst()
            for ((a, b) in RULES) {
                var idx = cur.indexOf(a)
                while (idx >= 0 && seen.size < max) {
                    val next = cur.substring(0, idx) + b + cur.substring(idx + a.length)
                    if (seen.add(next)) queue.addLast(next)
                    idx = cur.indexOf(a, idx + 1)
                }
            }
        }
        return seen.toList()
    }
}

/** 英文拼写纠错：编辑距离 + 相邻键位判断 */
class LatinCorrector(private val lexicon: EnglishLexicon) {

    private var wordCache: List<EnglishLexicon.Word>? = null

    private fun words(): List<EnglishLexicon.Word> {
        wordCache?.let { return it }
        val list = lexicon.allWords(12000)
        wordCache = list
        return list
    }

    /**
     * 对输入串做纠错。仅在正常联想无结果时调用。
     * @param input 用户输入的原始串
     */
    fun correct(input: String, limit: Int = 6): List<EnglishLexicon.Word> {
        val src = input.lowercase()
        if (src.length < 2) return emptyList()
        val all = words()
        if (all.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<EnglishLexicon.Word, Double>>(32)
        for (w in all) {
            if (w.text == src) continue
            val d = distance(src, w.text, 2)
            if (d > 2) continue
            var score = w.freq.toDouble()
            when (d) {
                1 -> score += 6000.0
                2 -> score *= 0.25
            }
            if (d == 1 && isNeighborTypo(src, w.text)) score += 9000.0
            if (abs(w.text.length - src.length) == 0) score += 300.0
            scored.add(w to score)
        }
        if (scored.isEmpty()) return emptyList()
        return scored.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /** 判断两串是否为一次相邻键位误触 */
    private fun isNeighborTypo(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = -1
        for (i in a.indices) {
            if (a[i] != b[i]) {
                if (diff >= 0) return false
                diff = i
            }
        }
        if (diff < 0) return false
        val neighbors = NEIGHBORS[a[diff]] ?: return false
        return neighbors.indexOf(b[diff]) >= 0
    }

    /** 带上限的编辑距离 */
    private fun distance(a: String, b: String, max: Int): Int {
        if (abs(a.length - b.length) > max) return max + 1
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = prev[j] + 1
                val ins = cur[j - 1] + 1
                if (ins < v) v = ins
                val sub = prev[j - 1] + cost
                if (sub < v) v = sub
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }

    private companion object {
        /** QWERTY 相邻键 */
        val NEIGHBORS: Map<Char, String> = mapOf(
            'q' to "wa", 'w' to "qeas", 'e' to "wrsd", 'r' to "etdf",
            't' to "ryfg", 'y' to "tugh", 'u' to "yihj", 'i' to "uojk",
            'o' to "ipkl", 'p' to "ol",
            'a' to "qwsz", 's' to "awedxz", 'd' to "serfcx", 'f' to "drtgvc",
            'g' to "ftyhbv", 'h' to "gyujnb", 'j' to "huikmn", 'k' to "jiolm",
            'l' to "kop",
            'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb",
            'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
        )
    }
}
