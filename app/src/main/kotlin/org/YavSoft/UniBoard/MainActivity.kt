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

package org.YavSoft.UniBoard

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import org.YavSoft.UniBoard.core.dpInt
import org.YavSoft.UniBoard.ui.AboutActivity
import org.YavSoft.UniBoard.ui.SettingsActivity
import org.YavSoft.UniBoard.ui.SettingsIndex
import org.YavSoft.UniBoard.ui.SettingsBase

/** 主页：输入法启用引导 + 各设置分区入口 */
class MainActivity : SettingsBase() {

    override fun build() {
        title(getString(R.string.app_name), 26f)
        subtitle("以 Unicode 为核心的输入法。")

        buildSearch()
        buildStatus()
        section("设置")
        buildEntries()
        buildFooter()
    }

    private lateinit var searchResults: LinearLayout

    /** 设置搜索：按关键字找设置项，点条目跳到对应分区 */
    private fun buildSearch() {
        section("设置搜索")
        val box = card()
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dpInt(12f), dpInt(6f), dpInt(8f), dpInt(6f))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpInt(20f).toFloat()
                setColor(0xFFF2F2F2.toInt())
                setStroke(dpInt(1f), 0xFFE0E0E0.toInt())
            }
        }
        searchRow.addView(android.widget.ImageView(this).apply {
            setImageResource(org.YavSoft.UniBoard.R.drawable.ic_search)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF9E9E9E.toInt())
            setPadding(dpInt(8f), dpInt(8f), dpInt(8f), dpInt(8f))
            layoutParams = LinearLayout.LayoutParams(dpInt(34f), dpInt(34f))
        })
        val input = android.widget.EditText(this).apply {
            hint = "搜索设置项，例如字体、透明度、隐私、候选"
            textSize = 15f
            setSingleLine(true)
            setTextColor(0xFF212121.toInt())
            setHintTextColor(0xFF9E9E9E.toInt())
            setBackgroundColor(0x00000000)
            setPadding(0, dpInt(10f), 0, dpInt(10f))
        }
        searchRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val clear = TextView(this).apply {
            text = "×"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF9E9E9E.toInt())
            setPadding(dpInt(10f), dpInt(4f), dpInt(10f), dpInt(4f))
            visibility = android.view.View.GONE
            setOnClickListener { input.setText("") }
        }
        searchRow.addView(clear, LinearLayout.LayoutParams(dpInt(34f), dpInt(34f)))
        box.addView(searchRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dpInt(10f); rightMargin = dpInt(10f)
            topMargin = dpInt(10f); bottomMargin = dpInt(6f)
        })
        searchResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(searchResults)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                clear.visibility = if (s.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                renderSearch(s?.toString().orEmpty())
            }
        })
    }

    private fun renderSearch(query: String) {
        searchResults.removeAllViews()
        val q = query.trim()
        if (q.isEmpty()) return
        val hits = SettingsIndex.search(q)
        if (hits.isEmpty()) {
            searchResults.addView(TextView(this).apply {
                text = "未找到匹配的设置项"
                textSize = 13f
                setTextColor(0xFF9E9E9E.toInt())
                setPadding(dpInt(16f), dpInt(6f), dpInt(16f), dpInt(14f))
            })
            return
        }
        for (item in hits) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
                isClickable = true
                setOnClickListener {
                    SettingsActivity.start(this@MainActivity, item.section)
                }
            }
            row.addView(TextView(this).apply {
                text = item.label
                textSize = 15f
                setTextColor(0xFF212121.toInt())
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 18f
                setTextColor(0xFF9E9E9E.toInt())
            })
            searchResults.addView(row)
            searchResults.addView(android.view.View(this).apply {
                setBackgroundColor(0xFFEEEEEE.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            })
        }
    }

    private fun buildStatus() {
        val box = card()
        val state = when {
            isImeSelected() -> "已就绪：UniBoard 为当前输入法"
            isImeEnabled() -> "已启用，但系统当前使用其他输入法"
            else -> "尚未启用 UniBoard"
        }
        box.addView(TextView(this).apply {
            text = state
            textSize = 16f
            setTextColor(0xFF212121.toInt())
            setPadding(dpInt(16f), dpInt(16f), dpInt(16f), dpInt(4f))
        })
        box.addView(TextView(this).apply {
            text = "请先在系统设置中启用本输入法，再将其设为当前输入法。"
            textSize = 12.5f
            setTextColor(0xFF757575.toInt())
            setPadding(dpInt(16f), 0, dpInt(16f), dpInt(8f))
        })
        when {
            !isImeEnabled() -> action(box, "前往系统设置启用") { openImeSettings() }
            !isImeSelected() -> action(box, "选择输入法") { showImePicker() }
            else -> action(box, "打开输入法设置") { openImeSettings() }
        }
    }

    private fun buildEntries() {
        entry("外观与主题", "主题色调、按键动画与触感反馈") {
            SettingsActivity.start(this, SettingsActivity.SECTION_APPEARANCE)
        }
        entry("键盘布局与手感", "键盘尺寸、按键行为与面板布局") {
            SettingsActivity.start(this, SettingsActivity.SECTION_KEYBOARD)
        }
        entry("输入与候选", "输入模式、编码纠错、候选与联想") {
            SettingsActivity.start(this, SettingsActivity.SECTION_INPUT)
        }
        entry("AI 助手", "文本润色与联想（可选，需自行配置接口）") {
            SettingsActivity.start(this, SettingsActivity.SECTION_AI)
        }
        entry("隐私与安全", "隐私模式、密码框保护与数据清理") {
            SettingsActivity.start(this, SettingsActivity.SECTION_PRIVACY)
        }
        entry("输入偏好脚本", "自定义文本替换规则") {
            SettingsActivity.start(this, SettingsActivity.SECTION_SCRIPT)
        }
        entry("词典与数据", "词条生成、导入导出与清空") {
            SettingsActivity.start(this, SettingsActivity.SECTION_LEXICON)
        }
    }

    private fun buildFooter() {
        card().also { box ->
            action(box, "关于与许可证") {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        }
        container.addView(TextView(this).apply {
            text = "UniBoard · GPL-3.0-or-later · 不收集任何用户数据"
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dpInt(20f), 0, 0)
        })
    }

    private fun entry(name: String, desc: String, onClick: () -> Unit) {
        val box = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpInt(16f), dpInt(15f), dpInt(16f), dpInt(15f))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            // MD3 的点击涟漪，取主题里的 selectableItemBackground
            val tv = android.util.TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                foreground = androidx.appcompat.content.res.AppCompatResources.getDrawable(
                    this@MainActivity, tv.resourceId
                )
            }
        }
        row.addView(TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(0xFF212121.toInt())
        })
        row.addView(TextView(this).apply {
            text = desc
            textSize = 12.5f
            setTextColor(0xFF757575.toInt())
            setPadding(0, dpInt(3f), 0, 0)
        })
        box.addView(row)
    }

    private fun isImeEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        return !enabled.isNullOrEmpty() && enabled.contains(packageName)
    }

    private fun isImeSelected(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return current != null && current.startsWith(packageName)
    }

    private fun openImeSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }.onFailure { toast("无法打开输入法设置页，请手动前往系统设置") }
    }

    private fun showImePicker() {
        val imm = getSystemService(InputMethodManager::class.java)
        val ok = runCatching {
            InputMethodManager::class.java.getMethod("showInputMethodPicker").invoke(imm)
        }.isSuccess
        if (!ok) openImeSettings()
    }
}
