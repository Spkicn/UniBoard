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

package org.YavSoft.UniBoard.script

import android.content.Context
import org.YavSoft.UniBoard.core.Prefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 输入偏好脚本。
 *
 * 规则写法（每行一条，`#` 开头为注释）：
 *   hh => 哈哈                字面替换
 *   /(\\d{4})-(\\d{2})/ => $1年$2月   正则替换，支持 $1..$9
 *
 * 替换内容可使用变量：{date} {time} {datetime} {clip}
 */
class ScriptEngine(private val context: Context) {

    private data class Rule(val trigger: String, val regex: Regex?, val literal: String, val replacement: String)

    private val rules = ArrayList<Rule>(32)
    private val prefs = Prefs(context)
    private val file = File(context.filesDir, "user_scripts.txt")

    init {
        load()
    }

    /** 原始脚本文本，供设置页编辑 */
    fun rawText(): String {
        return runCatching {
            if (file.exists()) file.readText() else DEFAULT_RULES
        }.getOrDefault(DEFAULT_RULES)
    }

    fun save(text: String) {
        runCatching { file.writeText(text) }
        load()
    }

    fun load() {
        rules.clear()
        parse(DEFAULT_RULES)
        if (file.exists()) {
            runCatching { file.readText() }.getOrNull()?.let { parse(it) }
        }
    }

    private fun parse(src: String) {
        for (line in src.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val sep = t.indexOf("=>")
            if (sep <= 0) continue
            val left = t.substring(0, sep).trim()
            val right = t.substring(sep + 2).trim()
            if (left.isEmpty()) continue
            if (left.length >= 2 && left.startsWith("/") && left.endsWith("/")) {
                val body = left.substring(1, left.length - 1)
                val re = runCatching { Regex(body) }.getOrNull() ?: continue
                rules.add(Rule(body, re, "", right))
            } else {
                rules.add(Rule(left, null, left, right))
            }
        }
    }

    /** 提交前应用规则；未开启脚本时原样返回 */
    fun apply(text: String): String {
        if (!prefs.scriptEnabled || text.isEmpty()) return text
        var out = text
        for (rule in rules) {
            val re = rule.regex
            out = if (re != null) {
                runCatching { re.replace(out, expandVars(rule.replacement)) }.getOrDefault(out)
            } else if (out == rule.literal) {
                expandVars(rule.replacement)
            } else {
                out
            }
        }
        return out
    }

    private fun expandVars(s: String): String {
        if (s.indexOf('{') < 0) return s
        val now = Date()
        var out = s
        if (out.contains("{date}")) {
            out = out.replace("{date}", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now))
        }
        if (out.contains("{time}")) {
            out = out.replace("{time}", SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
        }
        if (out.contains("{datetime}")) {
            out = out.replace("{datetime}", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(now))
        }
        if (out.contains("{clip}")) {
            val clip = org.YavSoft.UniBoard.clip.ClipStore(context).readCurrent().orEmpty()
            out = out.replace("{clip}", clip)
        }
        return out
    }

    companion object {
        /** 内置示例规则，用户保存的脚本会追加在它们之后 */
        val DEFAULT_RULES: String = listOf(
            "# UniBoard 输入偏好脚本",
            "# 每行一条规则，左侧匹配、右侧替换；# 开头为注释",
            "# 字面替换：整段文本完全相等时才替换",
            "hh => 哈哈",
            "xixi => 嘻嘻",
            "lol => 😂",
            ":) => 🙂",
            "(y) => 👍",
            "# 正则替换：/模式/ => 替换，可用 " + "\$1" + ".." + "\$9" + " 引用分组",
            "/^([0-9]{4})([0-9]{2})([0-9]{2})\$/ => " + "\$1" + "-" + "\$2" + "-" + "\$3",
            "# 变量：{date} {time} {datetime} {clip}",
            "riqi => {date}",
            "shijian => {time}"
        ).joinToString("\n")
    }
}
