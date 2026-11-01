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

import org.YavSoft.UniBoard.predict.Candidate

/**
 * 码点输入引擎：把「输入码」当成拼音一样的组合串来用。
 *
 * 与拼音链路保持一致：
 * - 输入过程中不直接上屏，而是走组合串（带下划线预览）
 * - 候选栏实时给出对应字符，空格/回车/点选即提交
 * - 输入码有误时做近似纠正（`O→0`、`I→1`、`S→5` …）
 * - 候选里附带「近似编码」的字符，例如 `4E0` 给出 `4E00–4E0F` 一整段
 */
object CodeInputEngine {

    data class Result(
        val candidates: List<Candidate>,
        /** 发生近似纠正时的原始输入，用于提示 */
        val correctedFrom: String?,
        /** 当前输入码的规范写法，例如 U+4E00 */
        val canonical: String? = null,
        /** 当前输入码对应的字符名，用于状态栏提示 */
        val note: String? = null,
        /** 输入码是否已经构成一个完整码点（用于「自动上屏」） */
        val complete: Boolean = false
    )

    private val PREFIXES = listOf("U+", "u+", "0x", "0X")

    /** 该按键是否应进入组合串（十六进制字符或码点前缀） */
    fun isCodeInput(text: String): Boolean {
        if (text.isEmpty()) return false
        if (PREFIXES.any { it == text }) return true
        if (text.length != 1) return false
        return text[0].isHexDigit()
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** 拆出前缀与主体 */
    private fun split(raw: String): Pair<String, String> {
        val s = raw.trim()
        for (p in PREFIXES) {
            if (s.startsWith(p)) return p to s.substring(p.length)
        }
        return "" to s
    }

    /**
     * 生成候选。
     *
     * @param raw 组合串原文（可能带 U+ / 0x 前缀）
     * @param limit 候选总数上限
     * @param nearLimit 近似编码候选的数量上限
     */
    fun predict(raw: String, limit: Int = 12, nearLimit: Int = 10): Result {
        val (prefix, body) = split(raw)
        if (body.isEmpty()) return Result(emptyList(), null)

        val ordered = LinkedHashMap<Int, Candidate>()
        fun add(cp: Int, score: Double, corrected: Boolean) {
            if (cp < 0 || cp > 0x10FFFF) return
            if (cp in 0xD800..0xDFFF) return
            if (cp < 0x20 || cp in 0x7F..0x9F) return          // 控制字符不上候选
            if (!runCatching { android.icu.lang.UCharacter.isDefined(cp) }.getOrDefault(true)) return
            if (ordered.containsKey(cp)) return
            val ch = UnicodeRepository.charOf(cp)
            if (ch.isEmpty()) return
            ordered[cp] = Candidate(
                text = ch,
                label = ch,
                hint = UnicodeRepository.hexLabel(cp),
                corrected = corrected,
                score = score
            )
        }

        // 1) 精确解析：4E00 / U+4E00 / 0x4e00
        val exact = runCatching { UnicodeRepository.parseCodePoint(raw) }.getOrNull()
            ?: runCatching { UnicodeRepository.parseCodePoint(prefix + body) }.getOrNull()
        exact?.let { add(it, 100.0, false) }

        // 2) 近似纠正：把 O/I/L/S/Z/G/T/B 当成数字
        val corrected = runCatching { UnicodeRepository.correctCodePoint(raw) }.getOrNull()
        val correctedFrom = if (corrected != null && corrected != exact) raw.trim() else null
        if (corrected != null && corrected != exact) add(corrected, 88.0, true)

        // 3) 近似编码：当前码作为前缀补齐，例如 4E0 -> 4E00..4E0F
        val near = LinkedHashSet<Int>()
        if (body.length < 6) {
            runCatching { UnicodeRepository.suggestByPrefix(body, nearLimit) }
                .getOrDefault(emptyList())
                .forEach { near.add(it) }
        }
        // 4) 数值邻域：完整码附近 ±1..±3，方便微调
        val anchor = exact ?: corrected
        if (anchor != null && near.size < nearLimit) {
            for (d in 1..3) {
                near.add(anchor + d)
                near.add(anchor - d)
            }
        }
        var rank = 0
        for (cp in near) {
            if (cp == exact || cp == corrected) continue
            if (rank >= nearLimit) break
            add(cp, 70.0 - rank * 0.5, false)
            rank++
        }

        val list = ordered.values.sortedByDescending { it.score }.take(limit)
        val primary = exact ?: corrected
        return Result(
            candidates = list,
            correctedFrom = correctedFrom,
            canonical = primary?.let { UnicodeRepository.hexLabel(it) },
            complete = exact != null,
            note = primary?.let { cp ->
                val name = UnicodeRepository.nameOf(cp)
                if (name.isEmpty()) UnicodeRepository.hexLabel(cp)
                else "${UnicodeRepository.hexLabel(cp)} · $name"
            }
        )
    }
}
