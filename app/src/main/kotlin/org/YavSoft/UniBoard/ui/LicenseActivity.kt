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

package org.YavSoft.UniBoard.ui

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.YavSoft.UniBoard.core.dpInt

/** 许可证全文：在应用内直接展示随包分发的 GPL 与 OFL 文本 */
class LicenseActivity : AppCompatActivity() {

    companion object {
        const val GPL_FILE = "licenses/gpl-3.0.txt"
        const val OFL_FILE = "licenses/unifont-ofl.txt"
    }

    private lateinit var text: TextView
    private lateinit var gplButton: MaterialButton
    private lateinit var oflButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpInt(14f), dpInt(14f), dpInt(14f), dpInt(10f))
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gplButton = button("GPL-3.0") { show(GPL_FILE) }
        oflButton = button("字体 OFL") { show(OFL_FILE) }
        bar.addView(gplButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(oflButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)

        val scroll = ScrollView(this)
        text = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, dpInt(12f), 0, dpInt(20f))
        }
        scroll.addView(text)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        show(GPL_FILE)
    }

    private fun button(label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            textSize = 13f
            setOnClickListener { onClick() }
        }

    private fun show(assetPath: String) {
        text.text = runCatching {
            assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrElse { "无法读取文件 " + assetPath }
    }
}
