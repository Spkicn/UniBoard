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

import android.icu.lang.UCharacter
import android.icu.lang.UCharacterCategory

/**
 * Unicode 数据访问层：码点解析、字符名、区块、分类与名称检索。
 *
 * 所有数据来自系统内置的 ICU 数据库，不依赖网络。
 */
object UnicodeRepository {

    /** 常用区块（名称、起始码点、结束码点） */
    data class Block(val name: String, val start: Int, val end: Int)

    val BLOCKS: List<Block> = listOf(
        Block("基本拉丁", 0x0020, 0x007E),
        Block("拉丁补充", 0x00A0, 0x00FF),
        Block("国际音标", 0x0250, 0x02AF),
        Block("希腊字母", 0x0391, 0x03C9),
        Block("西里尔字母", 0x0410, 0x044F),
        Block("希伯来文", 0x05D0, 0x05EA),
        Block("阿拉伯文", 0x0621, 0x064A),
        Block("天城文", 0x0905, 0x0939),
        Block("泰文", 0x0E01, 0x0E3A),
        Block("通用标点", 0x2010, 0x205E),
        Block("货币符号", 0x20A0, 0x20BF),
        Block("字母式符号", 0x2100, 0x214F),
        Block("箭头", 0x2190, 0x21FF),
        Block("数学运算符", 0x2200, 0x22FF),
        Block("制表符", 0x2500, 0x257F),
        Block("几何图形", 0x25A0, 0x25FF),
        Block("杂项符号", 0x2600, 0x26FF),
        Block("装饰符号", 0x2700, 0x27BF),
        Block("中日韩符号", 0x3000, 0x303F),
        Block("平假名", 0x3041, 0x3096),
        Block("片假名", 0x30A1, 0x30FA),
        Block("注音符号", 0x3105, 0x312F),
        Block("韩文兼容", 0x3131, 0x318E),
        Block("中日韩扩展A", 0x3400, 0x4DBF),
        Block("中日韩统一表意", 0x4E00, 0x9FA5),
        Block("彝文", 0xA000, 0xA48C),
        Block("韩文音节", 0xAC00, 0xD7A3),
        Block("兼容汉字", 0xF900, 0xFA6A),
        Block("数学字母数字", 0x1D400, 0x1D7FF),
        Block("表情符号", 0x1F300, 0x1F5FF),
        Block("表情脸谱", 0x1F600, 0x1F64F),
        Block("交通标志", 0x1F680, 0x1F6C5),
        Block("补充符号", 0x1F900, 0x1F9FF),
        Block("中日韩扩展B", 0x20000, 0x2A6D6)
    )

    /** 解析用户输入的码点写法，支持 4E00 / U+4E00 / 0x4E00 / &#x4E00; / &#19968; / 单字符 */
    fun parseCodePoint(raw: String): Int? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.length == 1 || (s.codePointCount(0, s.length) == 1 && s.codePointAt(0) > 0x7F)) {
            return s.codePointAt(0)
        }
        var body = s
        if (body.startsWith("&#x", true) || body.startsWith("&#X")) {
            body = body.removePrefix("&#x").removePrefix("&#X").removeSuffix(";")
            return body.toIntOrNull(16)
        }
        if (body.startsWith("&#")) {
            body = body.removePrefix("&#").removeSuffix(";")
            return body.toIntOrNull(10)
        }
        for (prefix in listOf("U+", "u+", "0x", "0X", "\\u", "\\\\u")) {
            if (body.startsWith(prefix)) {
                body = body.substring(prefix.length)
                break
            }
        }
        val v = body.toIntOrNull(16) ?: return null
        if (v < 0 || v > 0x10FFFF) return null
        if (v in 0xD800..0xDFFF) return null
        return v
    }

    /** 字符的 Unicode 名称，无名称时返回空串 */
    fun nameOf(codePoint: Int): String {
        if (codePoint < 0 || codePoint > 0x10FFFF) return ""
        return runCatching { UCharacter.getName(codePoint) }.getOrNull().orEmpty()
    }

    /** 通用类别名称（如“字母”“标点”） */
    fun categoryName(codePoint: Int): String {
        // UCharacterCategory 的常量在 Android 里是 byte，统一转成 Byte 再比较
        val cat = runCatching { UCharacter.getType(codePoint) }.getOrDefault(0).toByte()
        return when (cat) {
            UCharacterCategory.UPPERCASE_LETTER, UCharacterCategory.LOWERCASE_LETTER,
            UCharacterCategory.TITLECASE_LETTER, UCharacterCategory.MODIFIER_LETTER,
            UCharacterCategory.OTHER_LETTER -> "字母"
            UCharacterCategory.DECIMAL_DIGIT_NUMBER, UCharacterCategory.LETTER_NUMBER,
            UCharacterCategory.OTHER_NUMBER -> "数字"
            UCharacterCategory.SPACE_SEPARATOR, UCharacterCategory.LINE_SEPARATOR,
            UCharacterCategory.PARAGRAPH_SEPARATOR -> "空白"
            UCharacterCategory.CONTROL -> "控制符"
            UCharacterCategory.FORMAT -> "格式符"
            UCharacterCategory.PRIVATE_USE -> "私用区"
            UCharacterCategory.SURROGATE -> "代理项"
            UCharacterCategory.DASH_PUNCTUATION, UCharacterCategory.START_PUNCTUATION,
            UCharacterCategory.END_PUNCTUATION, UCharacterCategory.CONNECTOR_PUNCTUATION,
            UCharacterCategory.OTHER_PUNCTUATION, UCharacterCategory.INITIAL_PUNCTUATION,
            UCharacterCategory.FINAL_PUNCTUATION -> "标点"
            UCharacterCategory.MATH_SYMBOL, UCharacterCategory.CURRENCY_SYMBOL,
            UCharacterCategory.MODIFIER_SYMBOL, UCharacterCategory.OTHER_SYMBOL -> "符号"
            UCharacterCategory.UNASSIGNED -> "未分配"
            else -> "其他"
        }
    }

    /** 码点转字符串（正确处理代理对） */
    fun charOf(codePoint: Int): String {
        if (codePoint < 0 || codePoint > 0x10FFFF) return ""
        return String(Character.toChars(codePoint))
    }

    /** 十六进制展示，如 U+4E00 */
    fun hexLabel(codePoint: Int): String = "U+" + codePoint.toString(16).uppercase().padStart(4, '0')

    /** 找到码点所属的内置区块 */
    fun blockOf(codePoint: Int): Block? = BLOCKS.firstOrNull { codePoint in it.start..it.end }

    private val blockCache = HashMap<Int, List<Int>>(16)

    /** 列出某个区块内的有效码点（结果缓存，重复切换区块不再重复扫描） */
    fun charsInBlock(block: Block): List<Int> {
        blockCache[block.start]?.let { return it }
        val out = ArrayList<Int>(block.end - block.start + 1)
        for (cp in block.start..block.end) {
            if (cp in 0xD800..0xDFFF) continue
            if (!UCharacter.isDefined(cp)) continue
            out.add(cp)
        }
        blockCache[block.start] = out
        return out
    }

    /** 按 Unicode 名称检索字符（耗时，请在后台线程调用） */
    fun searchByName(query: String, limit: Int = 120): List<Int> {
        val q = query.trim().uppercase()
        if (q.length < 2) return emptyList()
        val out = ArrayList<Int>(limit)
        for (cp in 0x20..0xD7FF) {
            val name = nameOf(cp)
            if (name.isNotEmpty() && name.contains(q)) {
                out.add(cp)
                if (out.size >= limit) return out
            }
        }
        for (cp in 0xE000..0x2FFFF) {
            val name = nameOf(cp)
            if (name.isNotEmpty() && name.contains(q)) {
                out.add(cp)
                if (out.size >= limit) return out
            }
        }
        return out
    }

    /** 检索提示关键词 */
    val SAMPLE_QUERIES: List<String> = listOf(
        "SNOWMAN", "HEART", "ARROW", "MUSICAL", "GREEK", "CHESS", "STAR", "BOX"
    )

    /** 码点输入容错：把常见误输字母映射成数字后重解析 */
    fun correctCodePoint(raw: String): Int? {
        var s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        for (p in listOf("u+", "0x", "&#x", "&#", "\\u")) {
            if (s.startsWith(p)) s = s.substring(p.length)
        }
        s = s.replace("[", "").replace("]", "").replace(";", "").replace(" ", "")
        if (s.isEmpty()) return null
        val map = mapOf('o' to '0', 'q' to '0', 'i' to '1', 'l' to '1',
            'z' to '2', 's' to '5', 'g' to '6', 't' to '7', 'b' to '8')
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(map[c] ?: c)
        val hex = sb.toString()
        if (hex.isEmpty() || hex.length > 6) return null
        if (!hex.all { it in "0123456789abcdef" }) return null
        val v = hex.toIntOrNull(16) ?: return null
        if (v in 0xD800..0xDFFF || v > 0x10FFFF) return null
        return v
    }

    /**
     * 码点前缀联想：输入 `4E0` 就能看到 `4E00–4E0F` 区间的字符。
     *
     * 原实现把前缀当成「高位」再左移（`base shl (digits*4)`），三位前缀会移到
     * 0x4E0000 直接越界返回空，所以这里改成按目标码长（4/5/6 位）补齐低位。
     */
    fun suggestByPrefix(prefix: String, limit: Int = 240): List<Int> {
        val hex = prefix.trim().lowercase()
        if (hex.isEmpty() || hex.length > 5) return emptyList()
        if (!hex.all { it in "0123456789abcdef" }) return emptyList()
        val base = hex.toLong(16).toInt()
        val out = LinkedHashSet<Int>(minOf(limit, 256))
        for (target in intArrayOf(4, 5, 6)) {
            if (target < hex.length) continue
            val remain = target - hex.length
            if (remain > 3) continue
            val count = 1 shl (4 * remain)
            val start = base shl (4 * remain)
            for (i in 0 until count) {
                val cp = start + i
                if (cp > 0x10FFFF) break
                if (cp in 0xD800..0xDFFF) continue
                if (!UCharacter.isDefined(cp)) continue
                out.add(cp)
                if (out.size >= limit) return out.toList()
            }
        }
        return out.toList()
    }
}
