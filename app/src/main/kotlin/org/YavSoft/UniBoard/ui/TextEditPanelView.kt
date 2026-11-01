/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证第 3 版
 * 或（你选择的）任何更新版本所规定的条款，重新分发和/或修改它。
 */

package org.YavSoft.UniBoard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.core.dp
import org.YavSoft.UniBoard.core.dpInt
import org.YavSoft.UniBoard.core.sp
import org.YavSoft.UniBoard.ui.theme.KeyboardTheme
import org.YavSoft.UniBoard.ui.theme.KeyboardThemes

/**
 * 文本编辑面板。
 *
 * 外观与 Unicode / Emoji 面板保持一致：顶部圆角、拖拽条、同样的半屏高度。
 * 中间的触控区会画一个指针，拖动即可移动光标；单击 = 左键，长按 = 右键菜单。
 */
class TextEditPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onMoveCursor(offset: Int)
        fun onSelectAll()
        fun onCopy()
        fun onCut()
        fun onPaste()
        fun onDeleteForward()
        fun onLeftClick()
        fun onRightClick()
        fun onClose()
    }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardThemes.resolve(context, Prefs(context))
        set(value) {
            field = value
            applyTheme()
        }

    private val handleView: View
    private val header: LinearLayout
    private val padView: PadView
    private val keyButtons = ArrayList<TextView>()

    /** 拖拽条下拉关闭 */
    private var dragStartY = 0f
    private var dragging = false

    init {
        orientation = VERTICAL
        setPadding(context.dpInt(10f), context.dpInt(4f), context.dpInt(10f), context.dpInt(10f))

        handleView = View(context)
        addView(handleView, LayoutParams(LayoutParams.MATCH_PARENT, context.dpInt(18f)).apply {
            topMargin = context.dpInt(2f)
        })
        handleView.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartY = event.y
                        dragging = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (dragging) translationY = (event.y - dragStartY).coerceAtLeast(0f)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        dragging = false
                        if (translationY > context.dp(48f)) {
                            listener?.onClose()
                        } else {
                            animate().translationY(0f).setDuration(120).start()
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                        animate().translationY(0f).setDuration(120).start()
                        return true
                    }
                }
                return true
            }
        })

        header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "文本编辑"
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val close = TextView(context).apply {
            text = "关闭"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(context.dpInt(14f), context.dpInt(7f), context.dpInt(14f), context.dpInt(7f))
            setOnClickListener { listener?.onClose() }
        }
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, context.dpInt(32f)))

        padView = PadView(context)
        addView(padView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = context.dpInt(4f)
            bottomMargin = context.dpInt(6f)
        })

        addView(buildRow1(), LayoutParams(LayoutParams.MATCH_PARENT, context.dpInt(44f)))
        addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, context.dpInt(3f)))
        addView(buildRow2(), LayoutParams(LayoutParams.MATCH_PARENT, context.dpInt(44f)))

        applyTheme()
    }

    private fun buildRow1(): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        addKey(row, "◀ 左") { listener?.onMoveCursor(-1) }
        addKey(row, "右 ▶") { listener?.onMoveCursor(1) }
        addKey(row, "全选") { listener?.onSelectAll() }
        addKey(row, "删除") { listener?.onDeleteForward() }
        return row
    }

    private fun buildRow2(): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        addKey(row, "复制") { listener?.onCopy() }
        addKey(row, "剪切") { listener?.onCut() }
        addKey(row, "粘贴") { listener?.onPaste() }
        addKey(row, "菜单") { listener?.onRightClick() }
        return row
    }

    private fun addKey(parent: LinearLayout, label: String, onClick: () -> Unit) {
        val tv = TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onClick() }
        }
        parent.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(context.dpInt(3f), 0, context.dpInt(3f), 0)
        })
        keyButtons.add(tv)
    }

    private fun rounded(fill: Int, radius: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
            if (strokeColor != null) setStroke(context.dpInt(1f), strokeColor)
        }

    private fun applyTheme() {
        // 顶部圆角，底部两角在屏幕外
        val r = context.dpInt(18f).toFloat()
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(theme.keyboardBackground)
        }
        handleView.background = rounded(theme.hintText, context.dpInt(2f)).apply {
            setSize(context.dpInt(38f), context.dpInt(4f))
        }
        (header.getChildAt(0) as? TextView)?.setTextColor(theme.textPrimary)
        (header.getChildAt(1) as? TextView)?.apply {
            setTextColor(theme.accent)
            background = rounded(theme.keyBackground, context.dpInt(14f))
        }
        padView.updateTheme(theme)
        for (b in keyButtons) {
            b.setTextColor(theme.textPrimary)
            b.background = rounded(theme.funcKeyBackground, context.dpInt(12f))
        }
    }

    fun animateIn() {
        animate().cancel()
        alpha = 0f
        translationY = context.dpInt(14f).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(160).start()
    }

    fun animateOut() {
        animate().cancel()
        animate().alpha(0f).translationY(context.dpInt(10f).toFloat()).setDuration(120).start()
    }

    /**
     * 触控区：显示一个指针，拖动移动光标，单击左键，长按右键菜单。
     */
    private inner class PadView(c: Context) : View(c) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1f)
        }
        private val tmp = RectF()
        private var cursorX = -1f
        private var cursorY = -1f
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var theme = this@TextEditPanelView.theme

        fun updateTheme(t: KeyboardTheme) {
            theme = t
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val r = context.dp(12f)
            paint.color = theme.keyBackground
            tmp.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(tmp, r, r, paint)
            stroke.color = theme.divider
            canvas.drawRoundRect(tmp, r, r, stroke)

            // 提示文字
            paint.color = theme.hintText
            paint.textSize = context.sp(12f)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "拖动移动光标 · 单击左键 · 长按右键",
                width / 2f, height - context.dp(10f), paint
            )

            // 指针
            if (cursorX >= 0f) {
                paint.color = theme.accent
                canvas.drawCircle(cursorX, cursorY, context.dp(3f), paint)
                stroke.color = theme.accent
                canvas.drawLine(cursorX - context.dp(9f), cursorY, cursorX + context.dp(9f), cursorY, stroke)
                canvas.drawLine(cursorX, cursorY - context.dp(9f), cursorX, cursorY + context.dp(9f), stroke)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (cursorX < 0f) {
                        cursorX = width / 2f
                        cursorY = height / 2f
                    }
                    lastX = event.x
                    lastY = event.y
                    moved = false
                    postDelayed(rightClickRunnable, 520)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val stepX = context.dp(12f)
                    val stepY = context.dp(30f)
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (kotlin.math.abs(dx) > context.dp(4f) || kotlin.math.abs(dy) > context.dp(4f)) {
                        moved = true
                        removeCallbacks(rightClickRunnable)
                    }
                    if (kotlin.math.abs(dx) >= stepX) {
                        val times = (dx / stepX).toInt().coerceIn(-10, 10)
                        repeat(kotlin.math.abs(times)) {
                            listener?.onMoveCursor(if (times > 0) 1 else -1)
                        }
                        lastX = event.x
                    }
                    if (kotlin.math.abs(dy) >= stepY) {
                        val dir = if (dy > 0) 1 else -1
                        repeat(3) { listener?.onMoveCursor(dir) }
                        lastY = event.y
                    }
                    cursorX = (cursorX + dx * 0.35f).coerceIn(context.dp(8f), width - context.dp(8f))
                    cursorY = (cursorY + dy * 0.35f).coerceIn(context.dp(8f), height - context.dp(24f))
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(rightClickRunnable)
                    if (!moved) listener?.onLeftClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(rightClickRunnable)
                    return true
                }
            }
            return true
        }

        private val rightClickRunnable = Runnable {
            listener?.onRightClick()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
