/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证第 3 版
 * 或（你选择的）任何更新版本所规定的条款，重新分发和/或修改它。
 */

package org.YavSoft.UniBoard.ui

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import org.YavSoft.UniBoard.ai.AiClient
import org.YavSoft.UniBoard.core.dpInt
import org.YavSoft.UniBoard.predict.UserLexicon

/**
 * AI 生成词条页：选主题 → 生成 → 勾选 → 存入用户词典。
 */
class AiLexiconActivity : SettingsBase() {

    private var topic: String = ""
    private var count: Int = 60

    private lateinit var status: TextView
    private lateinit var resultBox: LinearLayout

    private data class Row(val word: String, val pinyin: String, val weight: Int, val box: AppCompatCheckBox)

    private val rows = ArrayList<Row>()

    override fun build() {
        title("AI 生成词条")
        subtitle("指定主题后，AI 按「词语 / 拼音 / 权重」格式生成词条；勾选并保存后写入用户词典，立即参与候选。")

        section("生成什么")
        val c1 = card()
        textField(c1, "主题，例如：医学检验、游戏术语、粤语常用词", "") { topic = it }
        slider(c1, "生成数量", 20f, 120f, 10f, count.toFloat()) { count = it.toInt() }
        action(c1, "开始生成") { generate() }

        status = TextView(this).apply {
            text = if (org.YavSoft.UniBoard.ai.AiClient.isConfigured(prefs)) "" else "请先在「AI 助手」中启用 AI 并配置接口（本地模型可留空密钥）。"
            textSize = 13f
            setTextColor(0xFF757575.toInt())
            setPadding(dpInt(16f), dpInt(10f), dpInt(16f), dpInt(6f))
        }
        container.addView(status)

        section("结果")
        val c2 = card()
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c2.addView(resultBox)
        val empty = TextView(this).apply {
            text = "暂无结果。"
            textSize = 13f
            setTextColor(0xFF757575.toInt())
            setPadding(dpInt(16f), dpInt(14f), dpInt(16f), dpInt(16f))
        }
        resultBox.addView(empty)

        section("保存")
        action(c2, "全选") { rows.forEach { it.box.isChecked = true } }
        action(c2, "保存勾选词条") { saveSelected() }
        action(c2, "查看当前词典") { showDictInfo() }
        toggle(c2, "允许单字词", "关闭后生成的词条至少包含 2 个字", prefs.allowSingleCharWords) {
            prefs.allowSingleCharWords = it
        }
    }

    private fun generate() {
        val t = topic.trim()
        if (t.isEmpty()) {
            toast("请先填写主题")
            return
        }
        if (!org.YavSoft.UniBoard.ai.AiClient.isConfigured(prefs)) {
            toast("请先在「AI 助手」中启用并配置接口（本地模型可留空密钥）")
            return
        }
        status.text = "正在生成…"
        val n = count.coerceIn(10, 120)
        val system = "你是输入法词典编纂助手。不得臆造词条，生成内容须与前文保持一致。仅输出词条，每行一条，格式：" +
            "词语<TAB>拼音音节(用空格分隔)<TAB>权重(1-100 的整数)。" +
            "不要编号、不要解释、不要空行、不要表头。英文词拼音留空。"
        val singleRule = if (prefs.allowSingleCharWords) "" else "不要生成单字词（每个词至少 2 个汉字）。"
        val user = "主题：$t。请生成正好 $n 条常用词条，按使用频率从高到低排列。" +
            "$singleRule 每行一条，严格用 Tab 分隔「词语、拼音、权重」，不要表头、不要编号、不要解释。"
        AiClient(prefs).complete(system, user, object : AiClient.Callback {
            override fun onResult(text: String) {
                val parsed = parse(text)
                if (parsed.isEmpty()) {
                    status.text = "未解析到有效词条，请更换主题后重试"
                    return
                }
                showResults(parsed)
                status.text = "已生成 ${parsed.size} 条，勾选后保存。"
            }

            override fun onError(message: String) {
                status.text = "生成失败：$message"
            }
        })
    }

    private fun parse(raw: String): List<UserLexicon.Entry> {
        val out = ArrayList<UserLexicon.Entry>()
        for (line in raw.lineSequence()) {
            val s = line.trim().trim('-', '*', '·', ' ')
            if (s.isEmpty() || s.startsWith("#")) continue
            val parts = s.split('\t', '|', ',').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            val word = parts[0].trim('"', '\'', ' ')
            if (word.isEmpty() || word.length > 12) continue
            if (!prefs.allowSingleCharWords && word.length < 2) continue
            val pinyin = parts.getOrNull(1).orEmpty()
                .lowercase()
                .filter { it in 'a'..'z' || it == ' ' || it == '\'' }
                .trim()
            val weight = parts.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(1, 100)
                ?: 70
            out.add(UserLexicon.Entry(word, pinyin, weight, "ai"))
            if (out.size >= 120) break
        }
        return out
    }

    private fun showResults(list: List<UserLexicon.Entry>) {
        rows.clear()
        resultBox.removeAllViews()
        for (e in list) {
            val box = AppCompatCheckBox(this).apply {
                isChecked = true
                text = buildString {
                    append(e.word)
                    if (e.pinyin.isNotEmpty()) append("  ·  ").append(e.pinyin)
                    append("   ").append(e.weight)
                }
                textSize = 15f
                setTextColor(0xFF212121.toInt())
                setPadding(dpInt(12f), dpInt(6f), dpInt(16f), dpInt(6f))
            }
            resultBox.addView(box, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_VERTICAL })
            rows.add(Row(e.word, e.pinyin, e.weight, box))
        }
    }

    private fun saveSelected() {
        if (rows.isEmpty()) {
            toast("请先点击「开始生成」")
            return
        }
        val picked = rows.filter { it.box.isChecked }
            .map { UserLexicon.Entry(it.word, it.pinyin, it.weight, "ai:" + topic.trim().take(12)) }
        if (picked.isEmpty()) {
            toast("未勾选任何词条")
            return
        }
        val lex = UserLexicon(this)
        lex.load()
        val added = lex.addAll(picked)
        lex.save()
        toast("已保存 $added 条到用户词典")
        status.text = "已保存 $added 条，输入拼音即可在候选中看到。"
    }

    private fun showDictInfo() {
        val lex = UserLexicon(this)
        lex.load()
        val sb = StringBuilder("当前用户词典：${lex.size} 条\n\n")
        lex.all().takeLast(20).forEach { sb.append("· ").append(it.word).append('\n') }
        if (lex.size > 20) sb.append("…")
        status.text = sb.toString()
    }
}
