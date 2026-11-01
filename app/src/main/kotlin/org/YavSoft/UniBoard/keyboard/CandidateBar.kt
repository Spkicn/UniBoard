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

package org.YavSoft.UniBoard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import org.YavSoft.UniBoard.R
import org.YavSoft.UniBoard.core.dp
import org.YavSoft.UniBoard.core.sp
import org.YavSoft.UniBoard.font.FontResolver
import org.YavSoft.UniBoard.predict.Candidate
import org.YavSoft.UniBoard.ui.theme.KeyboardTheme
import org.YavSoft.UniBoard.ui.theme.KeyboardThemes
import org.YavSoft.UniBoard.core.Prefs
import kotlin.math.max

/**
 * 候选栏：横向候选列表 + 右侧快捷工具栏。
 *
 * 工具栏从左到右依次是语言/模式切换、Unicode 面板、Emoji 面板、剪贴板、
 * AI 助手、设置。AI 键单击触发润色、长按触发联想。
 *
 * 候选区支持横向拖动翻看，首项高亮，纠错来源的候选会用强调色标出。
 */
class CandidateBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onCandidate(index: Int)
        fun onToolClick(tool: Tool)
        /** 工具键长按，例如 AI 键长按触发联想 */
        fun onToolLongClick(tool: Tool) {}
    }

    enum class Tool(val iconRes: Int) {
        LANGUAGE(0),
        UNICODE(R.drawable.ic_unicode),
        EMOJI(R.drawable.ic_emoji),
        CLIPBOARD(R.drawable.ic_clipboard),
        AI(R.drawable.ic_star),
        EDIT(R.drawable.ic_edit),
        CLOSE(R.drawable.ic_keyboard_hide)
    }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardThemes.resolve(context, Prefs(context))
        set(value) {
            field = value
            textPaint.textSize = context.sp(value.candidateTextSizeSp)
            invalidate()
        }

    var languageLabel: String = "中"
        set(value) {
            field = value
            invalidate()
        }

    var statusHint: String? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 是否显示右侧工具栏。
     * 输入过程中（已有候选）把它关掉，候选就占满整条栏，不会被工具栏挤掉或遮挡。
     */
    /** 当前允许显示的工具栏按钮（由输入法按设置过滤） */
    var visibleTools: List<Tool> = Tool.values().toList()
        set(value) {
            field = value.ifEmpty { Tool.values().toList() }
            pressedTool = -1
            invalidate()
        }

    /** AI 是否已配置（未配置时按钮画成灰色，点击抖动提示） */
    var aiReady: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var shakeValue = 0f
    private var shakeAnimator: android.animation.ValueAnimator? = null

    /** 未配置时给 AI 按钮一个「不可用」的抖动弹一下 */
    private fun shakeAiTool() {
        shakeAnimator?.cancel()
        shakeAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            addUpdateListener {
                val p = it.animatedValue as Float
                shakeValue = kotlin.math.sin(p * Math.PI * 6).toFloat() * (1f - p)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    shakeValue = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    var toolsVisible: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            pressedTool = -1
            invalidate()
        }

    var candidates: List<Candidate> = emptyList()
        set(value) {
            field = value
            offsetX = 0f
            contentWidthCache = -1f
            pressedCandidate = -1
            pressedTool = -1
            invalidate()
        }

    private val fontResolver by lazy { FontResolver.get(context) }
    private val baseTypeface: Typeface = Typeface.DEFAULT

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = context.sp(theme.candidateTextSizeSp)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.sp(11f)
        textAlign = Paint.Align.LEFT
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmpRect = RectF()
    private val drawableCache = HashMap<Long, android.graphics.drawable.Drawable>(8)
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        if (pressedTool >= 0) {
            longPressFired = true
            val tools = visibleTools
            if (pressedTool < tools.size) listener?.onToolLongClick(tools[pressedTool])
            pressedTool = -1
            invalidate()
        }
    }

    private class CandRect(val index: Int, val left: Float, val right: Float)
    private val candRects = ArrayList<CandRect>(16)
    private val dismissRect = RectF()

    /** 点叉号时通知输入法收起候选栏 */
    var onDismiss: (() -> Unit)? = null

    private var offsetX = 0f
    private var contentWidthCache = -1f
    private var pressedCandidate = -1
    private var pressedTool = -1
    private var downX = 0f
    private var downY = 0f
    /** 上一次 ACTION_MOVE 的 x，用于计算滑动增量 */
    private var lastX = 0f
    private var dragging = false

    private val toolSize: Float get() = context.dp(30f)
    private val padStart: Float get() = context.dp(8f)

    private fun candidateAreaRight(): Float {
        val base = if (toolsVisible) width.toFloat() - toolSize * visibleTools.size else width.toFloat()
        // 候选非空时右侧留出叉号的位置
        return if (candidates.isNotEmpty()) base - context.dp(30f) else base - context.dp(6f)
    }

    override fun onDraw(canvas: Canvas) {
        fillPaint.color = theme.candidateBarBackground
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        if (candidates.isEmpty()) {
            // 空闲：工具栏铺满整条，左侧不留空白
            if (toolsVisible) drawTools(canvas, spread = true)
        } else {
            drawCandidates(canvas)
            if (toolsVisible) drawTools(canvas, spread = false)
        }
    }

    private fun drawCandidates(canvas: Canvas) {
        candRects.clear()
        val maxRight = candidateAreaRight()
        if (candidates.isEmpty()) return
        // 右侧叉号：收起推荐词栏
        dismissRect.set(
            width - context.dp(28f), context.dp(8f),
            width - context.dp(6f), height - context.dp(8f)
        )
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = context.sp(15f)
        textPaint.color = theme.hintText
        canvas.drawText(
            "×", dismissRect.centerX(),
            dismissRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f,
            textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = context.sp(theme.candidateTextSizeSp)
        textPaint.textAlign = Paint.Align.LEFT
        val fm = textPaint.fontMetrics
        val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
        val padH = context.dp(9f)
        var x = padStart - offsetX
        for (i in candidates.indices) {
            val c = candidates[i]
            val tw = fontResolver.measure(c.label, textPaint, baseTypeface)
            val w = tw + padH * 2f
            if (x + w < padStart - context.dp(2f)) {
                x += w
                continue
            }
            if (x > maxRight) break
            if (i == 0 || i == pressedCandidate) {
                tmpRect.set(x, context.dp(6f), x + w, height - context.dp(6f))
                fillPaint.color = if (i == 0) theme.candidateSelectedBackground
                else theme.keyPressedBackground
                val r = context.dp(9f)
                canvas.drawRoundRect(tmpRect, r, r, fillPaint)
            }
            textPaint.color = if (c.corrected) theme.accent else theme.textPrimary
            fontResolver.draw(canvas, c.label, x + padH, baseline, textPaint, baseTypeface)
            candRects.add(CandRect(i, x, x + w))
            x += w
        }
    }

    private fun drawTools(canvas: Canvas, spread: Boolean) {
        val tools = visibleTools
        val cell = if (spread) width.toFloat() / tools.size else toolSize
        val startX = width - tools.size * cell
        if (!spread && startX > 0f) {
            fillPaint.color = theme.divider
            canvas.drawRect(
                startX, height * 0.28f, startX + context.dp(0.8f), height * 0.72f, fillPaint
            )
        }
        val fm = textPaint.fontMetrics
        val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
        for (i in tools.indices) {
            val tool = tools[i]
            var cx = startX + i * cell + cell / 2f
            if (tool == Tool.AI && !aiReady) cx += shakeValue * context.dp(4f)
            val cy = height / 2f
            if (i == pressedTool) {
                tmpRect.set(cx - cell / 2f + context.dp(4f), context.dp(5f),
                    cx + cell / 2f - context.dp(4f), height - context.dp(5f))
                fillPaint.color = theme.keyPressedBackground
                canvas.drawRoundRect(tmpRect, context.dp(8f), context.dp(8f), fillPaint)
            }
            if (tool == Tool.LANGUAGE) {
                textPaint.color = theme.textSecondary
                textPaint.textSize = context.sp(14f)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText(languageLabel, cx, baseline, textPaint)
                textPaint.typeface = Typeface.DEFAULT
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = context.sp(theme.candidateTextSizeSp)
            } else {
                val tint = if (tool == Tool.AI && !aiReady) theme.hintText else null
                val d = iconDrawable(tool.iconRes, tint)
                if (d != null) {
                    val size = context.dp(19f)
                    d.setBounds(
                        (cx - size / 2f).toInt(), (cy - size / 2f).toInt(),
                        (cx + size / 2f).toInt(), (cy + size / 2f).toInt()
                    )
                    d.draw(canvas)
                }
            }
        }
    }

    private fun iconDrawable(resId: Int, tintOverride: Int? = null): android.graphics.drawable.Drawable? {
        val color = tintOverride ?: theme.textSecondary
        val key = (resId.toLong() shl 32) or (color.toLong() and 0xFFFFFFFFL)
        drawableCache[key]?.let { return it }
        val d = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
        DrawableCompat.setTint(d, color)
        drawableCache[key] = d
        return d
    }

    private fun toolAt(x: Float): Int {
        if (!toolsVisible) return -1
        val tools = visibleTools
        val cell = if (candidates.isEmpty()) width.toFloat() / tools.size else toolSize
        val startX = width - tools.size * cell
        if (x < startX) return -1
        val i = ((x - startX) / cell).toInt()
        return if (i in tools.indices) i else -1
    }

    private fun candidateAt(x: Float): Int {
        for (r in candRects) {
            if (x >= r.left && x <= r.right) return r.index
        }
        return -1
    }

    private fun contentWidth(): Float {
        if (contentWidthCache >= 0f) return contentWidthCache
        var w = padStart
        for (c in candidates) {
            w += fontResolver.measure(c.label, textPaint, baseTypeface) + context.dp(18f)
        }
        contentWidthCache = w
        return w
    }

    private fun clampOffset() {
        val maxOffset = max(0f, contentWidth() - candidateAreaRight())
        if (offsetX < 0f) offsetX = 0f
        if (offsetX > maxOffset) offsetX = maxOffset
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                lastX = event.x
                downY = event.y
                dragging = false
                val tool = toolAt(event.x)
                longPressFired = false
                longPressHandler.removeCallbacks(longPressRunnable)
                if (tool >= 0) {
                    pressedTool = tool
                    pressedCandidate = -1
                    longPressHandler.postDelayed(longPressRunnable, 480)
                } else {
                    pressedTool = -1
                    pressedCandidate = candidateAt(event.x)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (kotlin.math.abs(event.x - downX) > context.dp(6f)
                            || kotlin.math.abs(event.y - downY) > context.dp(10f))) {
                    dragging = true
                    pressedTool = -1
                    pressedCandidate = -1
                }
                if (dragging) {
                    offsetX -= event.x - lastX
                    clampOffset()
                    invalidate()
                }
                lastX = event.x
            }
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                if (!dragging && !longPressFired && candidates.isNotEmpty() &&
                    dismissRect.contains(event.x, event.y)
                ) {
                    onDismiss?.invoke()
                    pressedTool = -1
                    pressedCandidate = -1
                    invalidate()
                    return true
                }
                if (!dragging && !longPressFired) {
                    if (pressedTool >= 0) {
                        val tools = visibleTools
                        if (pressedTool < tools.size) {
                            val tool = tools[pressedTool]
                            if (tool == Tool.AI && !aiReady) shakeAiTool()
                            listener?.onToolClick(tool)
                        }
                    } else if (pressedCandidate >= 0) {
                        listener?.onCandidate(pressedCandidate)
                    }
                }
                pressedTool = -1
                pressedCandidate = -1
                dragging = false
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                pressedTool = -1
                pressedCandidate = -1
                dragging = false
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
