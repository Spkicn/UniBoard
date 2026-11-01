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

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.NestedScrollView
import org.YavSoft.UniBoard.R
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.core.dpInt

/**
 * 设置类页面的公共骨架。
 *
 * 视觉上刻意做成「低版本 Android 设置页」的样子：
 * 纯色底、整行列表、1px 分隔线、左侧标题右侧开关，不用大圆角卡片和浮动按钮。
 */
abstract class SettingsBase : AppCompatActivity() {

    protected lateinit var prefs: Prefs
    protected lateinit var container: LinearLayout

    private lateinit var titleView: TextView
    private var titleSet = false

    private fun attr(attr: Int, fallback: Int): Int =
        com.google.android.material.color.MaterialColors.getColor(this, attr, fallback)

    private fun colorText(): Int = attr(com.google.android.material.R.attr.colorOnSurface, 0xFF212121.toInt())
    private fun colorTextDim(): Int = attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF757575.toInt())
    private fun colorDivider(): Int = attr(com.google.android.material.R.attr.colorOutlineVariant, 0xFFE0E0E0.toInt())
    private fun colorSurface(): Int = attr(com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF.toInt())
    private fun colorAccent(): Int {
        val custom = prefs.accentColor
        return if (custom != 0) custom
        else attr(com.google.android.material.R.attr.colorPrimary, getColor(R.color.brand_blue))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 真机「一打开就闪退」时，用户和开发者都看不到任何信息，只能靠猜。
        // 所以这里把整个建界面的过程包起来：出异常不把进程弄死，
        // 而是把异常原样画到屏幕上，同时落一份日志（见 CrashLog）。
        try {
            prefs = Prefs(this)
            installContent()
        } catch (t: Throwable) {
            org.YavSoft.UniBoard.core.CrashLog.save(
                this, "SettingsBase.onCreate:" + javaClass.simpleName, t
            )
            showCrashScreen(t)
        }
    }

    /** 出异常时的界面：直接显示类型、消息和完整调用栈，可长按复制 */
    private fun showCrashScreen(t: Throwable) {
        runCatching {
            val text = TextView(this).apply {
                this.text = buildString {
                    append("UniBoard 启动失败\n\n")
                    append(t.javaClass.name).append(": ").append(t.message).append("\n\n")
                    append(android.util.Log.getStackTraceString(t))
                }
                textSize = 11f
                setTextColor(0xFFB00020.toInt())
                setPadding(dpInt(16f), dpInt(48f), dpInt(16f), dpInt(16f))
                setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE
            }
            val scroll = NestedScrollView(this).apply { isFillViewport = true }
            scroll.addView(text)
            setContentView(scroll)
        }
    }

    private fun installContent() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorSurface())
        }

        // 顶栏：surface 底、无阴影、底部一条细线，标题左对齐
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorSurface())
            setPadding(dpInt(4f), 0, dpInt(16f), 0)
        }
        val back = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            imageTintList = android.content.res.ColorStateList.valueOf(colorText())
            val pad = dpInt(10f)
            setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(dpInt(44f), dpInt(44f))
        }
        titleView = TextView(this).apply {
            textSize = 20f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL
            )
            setTextColor(colorText())
            setSingleLine(true)
            setPadding(dpInt(12f), 0, 0, 0)
        }
        bar.addView(back)
        bar.addView(titleView, LinearLayout.LayoutParams(0, dpInt(56f), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        root.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpInt(56f)
        ))
        root.addView(View(this).apply {
            setBackgroundColor(colorDivider())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        val scroll = NestedScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(attr(android.R.attr.colorBackground, 0xFFF5F5F5.toInt()))
        }
        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(container)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        build()
    }

    protected abstract fun build()

    protected fun colorOf(attr: Int): Int = colorText()

    /** 首个 title 作为标题栏文字 */
    protected fun title(text: String, size: Float = 24f) {
        if (!titleSet) {
            titleSet = true
            titleView.text = text
            return
        }
        container.addView(TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(colorText())
            setPadding(dpInt(16f), dpInt(20f), dpInt(16f), dpInt(6f))
        })
    }

    /** 说明文字：旧版设置页里就是一行小灰字，不套卡片 */
    protected fun subtitle(text: String) {
        container.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(colorTextDim())
            setPadding(dpInt(16f), dpInt(10f), dpInt(16f), dpInt(10f))
            setLineSpacing(dpInt(2f).toFloat(), 1f)
        })
    }

    /** 分节标题：小号、强调色、左对齐 */
    protected fun section(text: String) {
        container.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(colorAccent())
            setPadding(dpInt(16f), dpInt(20f), dpInt(16f), dpInt(6f))
        })
    }

    /** 列表容器：白底，行间用 1px 分隔线 */
    protected fun card(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorSurface())
        }
        container.addView(box, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return box
    }

    protected fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply {
            setBackgroundColor(colorDivider())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply {
                leftMargin = dpInt(16f)
                rightMargin = dpInt(16f)
            }
        })
    }

    /** 开关行：左边标题（可带一行灰字说明），右边 SwitchCompat */
    protected fun toggle(
        parent: LinearLayout,
        name: String,
        desc: String?,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this@SettingsBase).apply {
            text = name
            textSize = 16f
            setTextColor(colorText())
        })
        if (!desc.isNullOrBlank()) {
            texts.addView(TextView(this@SettingsBase).apply {
                text = desc
                textSize = 12.5f
                setTextColor(colorTextDim())
                setPadding(0, dpInt(2f), 0, 0)
            })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        parent.addView(row)
        divider(parent)
    }

    /** 整行按钮：左文字 + 右箭头 */
    protected fun action(parent: LinearLayout, name: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dpInt(16f), dpInt(15f), dpInt(16f), dpInt(15f))
            setOnClickListener { onClick() }
            val tv = android.util.TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                setBackgroundResource(tv.resourceId)
            }
        }
        row.addView(TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(colorText())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 20f
            setTextColor(colorTextDim())
        })
        parent.addView(row)
        divider(parent)
    }

    /** 滑块行：标题 + 数值 + SeekBar */
    protected fun slider(
        parent: LinearLayout,
        name: String,
        from: Float,
        to: Float,
        step: Float,
        initial: Float,
        display: ((Float) -> String)? = null,
        onChange: (Float) -> Unit
    ) {
        val fmt: (Float) -> String = display ?: ::formatValue
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), 0)
        }
        header.addView(TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(colorText())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val valueLabel = TextView(this).apply {
            text = fmt(initial)
            textSize = 13f
            setTextColor(colorAccent())
        }
        header.addView(valueLabel)
        parent.addView(header)

        val steps = if (step > 0f) Math.round((to - from) / step) else 0
        val seek = SeekBar(this).apply {
            max = steps
            progress = if (step > 0f) Math.round((initial - from) / step) else 0
            setPadding(dpInt(14f), dpInt(8f), dpInt(14f), dpInt(8f))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = if (step > 0f) from + progress * step else from
                    valueLabel.text = fmt(v)
                    onChange(v.coerceIn(from, to))
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        parent.addView(seek, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        divider(parent)
    }

    private fun formatValue(v: Float): String =
        if (v >= 10f && v % 1f == 0f) v.toInt().toString() else String.format("%.2f", v)

    /** 单选：点一行 → 弹出单选列表（旧版设置页的做法） */
    protected fun choice(
        parent: LinearLayout,
        options: List<String>,
        selected: Int,
        onPick: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dpInt(16f), dpInt(15f), dpInt(16f), dpInt(15f))
        }
        row.addView(TextView(this).apply {
            text = options.getOrElse(selected) { "" }
            textSize = 16f
            setTextColor(colorText())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "▾"
            textSize = 14f
            setTextColor(colorTextDim())
        })
        row.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(options.getOrElse(selected) { "" })
                .setSingleChoiceItems(options.toTypedArray(), selected) { dialog, which ->
                    onPick(which)
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        parent.addView(row)
        divider(parent)
    }

    protected fun textField(
        parent: LinearLayout,
        hint: String,
        initial: String,
        multiline: Boolean = false,
        onChange: (String) -> Unit
    ) {
        val et = EditText(this).apply {
            this.hint = hint
            setText(initial)
            textSize = 14f
            setTextColor(colorText())
            setHintTextColor(colorTextDim())
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setBackgroundColor(0x00000000)
            if (multiline) {
                setSingleLine(false)
                maxLines = 10
                gravity = Gravity.TOP
            }
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
        }
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                onChange(s?.toString().orEmpty())
            }
        })
        parent.addView(et, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        divider(parent)
    }

    /** 主题色板 */
    protected fun colorPalette(parent: LinearLayout, current: Int, onPick: (Int) -> Unit) {
        val colors = intArrayOf(
            0xFF4285F4.toInt(), 0xFFEA4335.toInt(), 0xFFFBBC05.toInt(),
            0xFF34A853.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
            0xFFFF7043.toInt(), 0xFF607D8B.toInt()
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpInt(16f), dpInt(14f), dpInt(16f), dpInt(14f))
        }
        for (c in colors) {
            val dot = TextView(this).apply {
                text = if (c == current) "✓" else ""
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(c)
                    if (c == current) setStroke(dpInt(2f), 0xFF212121.toInt())
                }
            }
            dot.layoutParams = LinearLayout.LayoutParams(dpInt(34f), dpInt(34f)).apply {
                rightMargin = dpInt(12f)
            }
            dot.setOnClickListener { onPick(c) }
            row.addView(dot)
        }
        parent.addView(row)
        divider(parent)
    }

    /** 危险操作二次确认 */
    protected fun confirm(message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> onYes() }
            .setNegativeButton("取消", null)
            .show()
    }

    protected fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    protected fun isNight(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
}
