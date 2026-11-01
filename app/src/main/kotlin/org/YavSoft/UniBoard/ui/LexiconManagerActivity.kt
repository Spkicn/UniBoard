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
import org.YavSoft.UniBoard.core.dpInt
import org.YavSoft.UniBoard.predict.UserLexicon

/**
 * 用户词典管理：搜索、查看、逐条删除、清空。
 */
class LexiconManagerActivity : SettingsBase() {

    private lateinit var listBox: LinearLayout
    private lateinit var summary: TextView
    private var query: String = ""

    private val lexicon: UserLexicon by lazy { UserLexicon(this).apply { load() } }

    override fun build() {
        title("管理词典")
        summary = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF757575.toInt())
            setPadding(dpInt(16f), dpInt(14f), dpInt(16f), dpInt(6f))
        }

        section("搜索")
        val c1 = card()
        c1.addView(summary)
        textField(c1, "按词语或拼音过滤", "") { q ->
            query = q.trim()
            renderList()
        }

        section("词条")
        val c2 = card()
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c2.addView(listBox)

        section("批量")
        val c3 = card()
        action(c3, "清空全部词条") {
            confirm("确定清空全部 ${lexicon.size} 条词条？此操作不可撤销。") {
                lexicon.clear()
                lexicon.save()
                toast("已清空全部词条")
                renderList()
            }
        }

        renderList()
    }

    private fun renderList() {
        listBox.removeAllViews()
        val all = lexicon.all()
        val filtered = if (query.isEmpty()) all else all.filter {
            it.word.contains(query, true) || it.pinyin.contains(query, true)
        }
        summary.text = "共 ${all.size} 条" +
            if (query.isEmpty()) "" else "，匹配 ${filtered.size} 条"

        if (filtered.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = if (all.isEmpty()) "暂无词条。可通过「AI 生成词条」生成，或从文件导入。" else "未找到匹配的词条。"
                textSize = 13f
                setTextColor(0xFF757575.toInt())
                setPadding(dpInt(16f), dpInt(16f), dpInt(16f), dpInt(18f))
            })
            return
        }

        for (e in filtered.take(200)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpInt(16f), dpInt(10f), dpInt(8f), dpInt(10f))
            }
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this).apply {
                text = e.word
                textSize = 16f
                setTextColor(0xFF212121.toInt())
            })
            val meta = buildString {
                if (e.pinyin.isNotEmpty()) append(e.pinyin)
                if (isNotEmpty()) append("   ")
                append("权重 ").append(e.weight)
                if (e.source.isNotEmpty()) append("   ").append(e.source)
            }
            texts.addView(TextView(this).apply {
                text = meta
                textSize = 12f
                setTextColor(0xFF757575.toInt())
            })
            row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(TextView(this).apply {
                text = "删除"
                textSize = 14f
                setTextColor(0xFFD32F2F.toInt())
                setPadding(dpInt(10f), dpInt(8f), dpInt(10f), dpInt(8f))
                setOnClickListener {
                    confirm("删除词条「${e.word}」？") {
                        lexicon.remove(e.word)
                        lexicon.save()
                        renderList()
                    }
                }
            })
            listBox.addView(row)
            divider(listBox)
        }

        if (filtered.size > 200) {
            listBox.addView(TextView(this).apply {
                text = "仅显示前 200 条，可使用搜索缩小范围。"
                textSize = 12f
                setTextColor(0xFF9E9E9E.toInt())
                setPadding(dpInt(16f), dpInt(10f), dpInt(16f), dpInt(14f))
            })
        }
    }
}
