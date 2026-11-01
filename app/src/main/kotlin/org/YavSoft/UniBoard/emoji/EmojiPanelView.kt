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

package org.YavSoft.UniBoard.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.core.dp
import org.YavSoft.UniBoard.core.sp
import org.YavSoft.UniBoard.font.FontResolver
import org.YavSoft.UniBoard.ui.theme.KeyboardTheme
import org.YavSoft.UniBoard.ui.theme.KeyboardThemes
import kotlin.math.abs
import kotlin.math.max

/**
 * Emoji 面板：分类标签 + 网格，点击直接送到输入框。
 *
 * 半屏样式：顶部圆角 + 拖拽条（下拉关闭），分类标签可横向滑动，
 * 单元格按下有轻微放大反馈。
 */
class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onPick(emoji: String)
        fun onClose()
    }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardThemes.resolve(context, Prefs(context))
        set(value) {
            field = value
            invalidate()
        }

    private val fontResolver by lazy { FontResolver.get(context) }
    private val baseTypeface: Typeface = Typeface.DEFAULT

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.sp(12f)
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.sp(26f)
    }

    private val tmpRect = RectF()
    private val chipRects = ArrayList<RectF>()
    private var groupIndex = 0
    private var scrollY = 0f
    private var chipScrollX = 0f
    private var downY = 0f
    private var downX = 0f
    private var dragging = false
    private var chipDragging = false
    private var handleDragging = false
    private var handleStartY = 0f
    private var pressed = -1

    private val groups get() = EmojiData.GROUPS
    private val items: List<String> get() = groups[groupIndex].items

    private val handleHeight: Float get() = context.dp(22f)
    private val chipBarHeight: Float get() = context.dp(38f)
    private val cellSize: Float get() = context.dp(46f)
    private val columns: Int get() = max(5, (width / cellSize).toInt())

    /** 网格整体水平居中，避免右侧留白 */
    private fun gridLeft(): Float {
        val used = columns * cellSize
        return ((width - used) / 2f).coerceAtLeast(0f)
    }
    private val gridTop: Float get() = handleHeight + chipBarHeight + context.dp(2f)

    override fun onDraw(canvas: Canvas) {
        // 半屏圆角面板（底部两角在屏幕外，所以加一个半径的余量）
        fillPaint.color = theme.keyboardBackground
        val r = context.dp(18f)
        tmpRect.set(0f, 0f, width.toFloat(), height.toFloat() + r)
        canvas.drawRoundRect(tmpRect, r, r, fillPaint)

        drawHandle(canvas)
        drawChips(canvas)
        drawGrid(canvas)
    }

    /** 顶部拖拽条 */
    private fun drawHandle(canvas: Canvas) {
        fillPaint.color = theme.hintText
        val w = context.dp(38f)
        val h = context.dp(4f)
        tmpRect.set((width - w) / 2f, context.dp(8f), (width + w) / 2f, context.dp(8f) + h)
        canvas.drawRoundRect(tmpRect, h / 2f, h / 2f, fillPaint)
    }

    private fun chipWidth(name: String): Float = chipPaint.measureText(name) + context.dp(22f)

    private fun maxChipScroll(): Float {
        var total = context.dp(8f)
        for (g in groups) total += chipWidth(g.name) + context.dp(6f)
        return max(0f, total - width + context.dp(8f))
    }

    private fun drawChips(canvas: Canvas) {
        chipRects.clear()
        val fm = chipPaint.fontMetrics
        val top = handleHeight + context.dp(4f)
        val bottom = handleHeight + chipBarHeight - context.dp(4f)
        val baseline = (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f
        var x = context.dp(8f) - chipScrollX
        canvas.save()
        canvas.clipRect(0f, handleHeight, width.toFloat(), gridTop)
        for (i in groups.indices) {
            val name = groups[i].name
            val w = chipWidth(name)
            tmpRect.set(x, top, x + w, bottom)
            x += w + context.dp(6f)
            if (tmpRect.right < 0f || tmpRect.left > width) continue
            val selected = i == groupIndex
            fillPaint.color = if (selected) theme.accent else theme.keyBackground
            canvas.drawRoundRect(tmpRect, context.dp(13f), context.dp(13f), fillPaint)
            chipPaint.color = if (selected) theme.onAccent else theme.textSecondary
            canvas.drawText(name, tmpRect.centerX(), baseline, chipPaint)
            chipRects.add(RectF(tmpRect))
        }
        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas) {
        val cols = columns
        val cell = cellSize
        canvas.save()
        canvas.clipRect(0f, gridTop, width.toFloat(), height.toFloat())
        val fm = emojiPaint.fontMetrics
        for (i in items.indices) {
            val row = i / cols
            val col = i % cols
            val cx = gridLeft() + col * cell + cell / 2f
            val cy = gridTop + row * cell + cell / 2f - scrollY
            if (cy + cell < gridTop || cy - cell > height) continue
            if (i == pressed) {
                // 按下时向外扩一点，做出“被顶起来”的观感
                val pad = cell / 2f - context.dp(2f)
                tmpRect.set(cx - pad, cy - pad, cx + pad, cy + pad)
                fillPaint.color = theme.candidateSelectedBackground
                canvas.drawRoundRect(tmpRect, context.dp(11f), context.dp(11f), fillPaint)
            }
            val e = items[i]
            val w = fontResolver.measure(e, emojiPaint, baseTypeface)
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            emojiPaint.color = theme.textPrimary
            fontResolver.draw(canvas, e, cx - w / 2f, baseline, emojiPaint, baseTypeface)
        }
        canvas.restore()
    }

    private fun maxScroll(): Float {
        if (items.isEmpty()) return 0f
        val rows = (items.size + columns - 1) / columns
        return max(0f, rows * cellSize - (height - gridTop))
    }

    private fun hitCell(x: Float, y: Float): Int {
        if (y < gridTop) return -1
        val col = ((x - gridLeft()) / cellSize).toInt()
        if (col < 0 || col >= columns) return -1
        val localY = y + scrollY - gridTop
        val row = (localY / cellSize).toInt()
        val index = row * columns + col
        return if (index in items.indices) index else -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downX = event.x
                dragging = false
                chipDragging = false
                if (event.y < handleHeight) {
                    handleDragging = true
                    handleStartY = event.y
                    return true
                }
                if (event.y < gridTop) {
                    chipDragging = true
                    return true
                }
                pressed = hitCell(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (handleDragging) {
                    translationY = (event.y - handleStartY).coerceAtLeast(0f)
                    return true
                }
                if (chipDragging) {
                    chipScrollX = (chipScrollX - (event.x - downX)).coerceIn(0f, maxChipScroll())
                    downX = event.x
                    invalidate()
                    return true
                }
                if (!dragging && abs(event.y - downY) > context.dp(6f)) {
                    dragging = true
                    pressed = -1
                }
                if (dragging) {
                    scrollY = (scrollY - (event.y - downY)).coerceIn(0f, maxScroll())
                    downY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (handleDragging) {
                    handleDragging = false
                    if (translationY > context.dp(56f)) {
                        listener?.onClose()
                    } else {
                        animate().translationY(0f).setDuration(120).start()
                    }
                    return true
                }
                if (chipDragging) {
                    chipDragging = false
                    if (abs(event.x - downX) < context.dp(6f)) {
                        var chipHit = -1
                        for (i in chipRects.indices) {
                            if (chipRects[i].contains(event.x, event.y)) {
                                chipHit = i
                                break
                            }
                        }
                        if (chipHit >= 0 && chipHit != groupIndex) {
                            groupIndex = chipHit
                            scrollY = 0f
                            invalidate()
                        }
                    }
                    return true
                }
                if (!dragging) {
                    if (pressed >= 0 && pressed < items.size) {
                        listener?.onPick(items[pressed])
                    }
                }
                pressed = -1
                dragging = false
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                dragging = false
                chipDragging = false
                handleDragging = false
                translationY = 0f
                invalidate()
            }
        }
        return true
    }

    fun animateIn() {
        animate().cancel()
        alpha = 0f
        translationY = context.dp(14f)
        animate().alpha(1f).translationY(0f).setDuration(170)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun animateOut() {
        animate().cancel()
        animate().alpha(0f).translationY(context.dp(10f)).setDuration(120).start()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
