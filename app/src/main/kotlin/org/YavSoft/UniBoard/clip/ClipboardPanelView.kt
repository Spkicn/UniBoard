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

package org.YavSoft.UniBoard.clip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.YavSoft.UniBoard.R
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.core.dp
import org.YavSoft.UniBoard.core.sp
import org.YavSoft.UniBoard.font.FontResolver
import org.YavSoft.UniBoard.ui.theme.KeyboardTheme
import org.YavSoft.UniBoard.ui.theme.KeyboardThemes
import kotlin.math.abs
import kotlin.math.max

/**
 * 剪贴板面板。
 *
 * 半屏圆角，顶部拖拽条；列表分四组：常用语 / 系统剪贴板 / 收藏 / 最近使用。
 * 顶部搜索框可点击聚焦（键盘输入由输入法路由进来），按内容过滤。
 */
class ClipboardPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onPaste(text: String)
        fun onClose()
        fun onSearchFocus(active: Boolean) {}
    }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardThemes.resolve(context, Prefs(context))
        set(value) {
            field = value
            invalidate()
        }

    private val store = ClipStore(context)
    private val phrases = QuickPhrases(context)
    private val fontResolver by lazy { FontResolver.get(context) }
    private val baseTypeface: Typeface = Typeface.DEFAULT

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f)
    }

    private val tmpRect = RectF()
    private val closeRect = RectF()
    private val searchRect = RectF()

    private var currentClip: String? = null
    private var saved: List<String> = emptyList()
    private var recent: List<ClipStore.Item> = emptyList()
    private var quick: List<String> = emptyList()

    var searchActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var query: String = ""
    private var scrollY = 0f
    private var downY = 0f
    private var dragging = false
    private var pressedRow = -1
    private var longPressRow = -1
    private var handleDragging = false
    private var handleStartY = 0f

    private data class Row(
        val text: String,
        val meta: String,
        val kind: Int,        // 0=常用语 1=当前剪贴板 2=收藏 3=历史
        val groupStart: Boolean
    )

    private val rows = ArrayList<Row>()
    private val drawableCache = HashMap<Long, android.graphics.drawable.Drawable>(4)

    private val handleHeight: Float get() = context.dp(22f)
    private val headerHeight: Float get() = context.dp(38f)
    private val searchHeight: Float get() = context.dp(42f)
    private val groupHeight: Float get() = context.dp(26f)
    private val rowHeight: Float get() = context.dp(58f)
    private val listTop: Float get() = handleHeight + headerHeight + searchHeight + context.dp(2f)

    private fun dt(ts: Long): String =
        if (ts <= 0) "" else java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))

    fun refresh() {
        val privacy = runCatching { Prefs(context).privacyMode }.getOrDefault(false)
        currentClip = store.readCurrent()
        if (!privacy) currentClip?.let { store.pushHistory(it) }
        saved = store.list()
        recent = if (privacy) emptyList() else store.recent()
        quick = phrases.list()
        rebuild()
        scrollY = 0f
        pressedRow = -1
        invalidate()
    }

    /** 键盘把字符路由进来 */
    fun appendText(text: String) {
        query += text
        rebuild()
        invalidate()
    }

    fun backspace() {
        if (query.isEmpty()) return
        query = query.dropLast(1)
        rebuild()
        invalidate()
    }

    fun clearQuery() {
        if (query.isEmpty()) return
        query = ""
        rebuild()
        invalidate()
    }

    private fun matches(text: String): Boolean =
        query.isEmpty() || text.contains(query, ignoreCase = true)

    private fun rebuild() {
        rows.clear()

        fun addGroup(kind: Int, list: List<Pair<String, String>>) {
            var first = true
            for ((text, meta) in list) {
                if (!matches(text)) continue
                rows.add(Row(text, meta, kind, first))
                first = false
            }
        }

        addGroup(0, quick.map { it to "常用语" })
        addGroup(1, listOfNotNull(currentClip?.let { it to "系统剪贴板" }))
        addGroup(2, saved.map { it to "收藏" })
        addGroup(3, recent.map {
            val meta = listOfNotNull(dt(it.ts).takeIf { d -> d.isNotEmpty() }, "最近使用")
                .joinToString(" · ")
            it.text to meta
        })
    }

    private fun totalHeight(): Float {
        var h = 0f
        for (r in rows) {
            if (r.groupStart) h += groupHeight
            h += rowHeight
        }
        return h
    }

    private fun maxScroll(): Float = max(0f, totalHeight() - (height - listTop))

    override fun onDraw(canvas: Canvas) {
        val r = context.dp(18f)
        tmpRect.set(0f, 0f, width.toFloat(), height.toFloat() + r)
        fillPaint.color = theme.keyboardBackground
        canvas.drawRoundRect(tmpRect, r, r, fillPaint)

        // 拖拽条
        fillPaint.color = theme.hintText
        val hw = context.dp(38f)
        val hh = context.dp(4f)
        tmpRect.set((width - hw) / 2f, context.dp(8f), (width + hw) / 2f, context.dp(8f) + hh)
        canvas.drawRoundRect(tmpRect, hh / 2f, hh / 2f, fillPaint)

        drawHeader(canvas)
        drawSearch(canvas)
        drawList(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        textPaint.textSize = context.sp(15f)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = theme.textPrimary
        textPaint.textAlign = Paint.Align.LEFT
        val fm = textPaint.fontMetrics
        val top = handleHeight
        canvas.drawText(
            "剪贴板", context.dp(16f),
            top + headerHeight / 2f - (fm.ascent + fm.descent) / 2f, textPaint
        )
        textPaint.typeface = Typeface.DEFAULT

        closeRect.set(
            width - context.dp(44f), top + context.dp(4f),
            width - context.dp(8f), top + headerHeight - context.dp(4f)
        )
        val d = iconDrawable(R.drawable.ic_close)
        if (d != null) {
            val size = context.dp(17f)
            d.setBounds(
                (closeRect.centerX() - size / 2f).toInt(), (closeRect.centerY() - size / 2f).toInt(),
                (closeRect.centerX() + size / 2f).toInt(), (closeRect.centerY() + size / 2f).toInt()
            )
            d.draw(canvas)
        }
    }

    private fun drawSearch(canvas: Canvas) {
        val top = handleHeight + headerHeight
        searchRect.set(context.dp(12f), top, width - context.dp(12f), top + searchHeight - context.dp(6f))
        fillPaint.color = theme.keyBackground
        canvas.drawRoundRect(searchRect, context.dp(18f), context.dp(18f), fillPaint)
        strokePaint.color = if (searchActive) theme.accent else theme.divider
        canvas.drawRoundRect(searchRect, context.dp(18f), context.dp(18f), strokePaint)

        smallPaint.textSize = context.sp(13f)
        smallPaint.textAlign = Paint.Align.LEFT
        val fm = smallPaint.fontMetrics
        val baseline = searchRect.centerY() - (fm.ascent + fm.descent) / 2f
        val x = searchRect.left + context.dp(14f)
        if (query.isEmpty()) {
            smallPaint.color = theme.hintText
            canvas.drawText("搜索剪贴板内容", x, baseline, smallPaint)
        } else {
            smallPaint.color = theme.textPrimary
            val w = fontResolver.measure(query, smallPaint, baseTypeface)
            fontResolver.draw(canvas, query, x, baseline, smallPaint, baseTypeface)
            strokePaint.color = theme.accent
            canvas.drawLine(
                x + w + context.dp(1f), searchRect.top + context.dp(10f),
                x + w + context.dp(1f), searchRect.bottom - context.dp(10f), strokePaint
            )
        }
    }

    private fun drawList(canvas: Canvas) {
        if (rows.isEmpty()) {
            smallPaint.color = theme.hintText
            smallPaint.textSize = context.sp(13f)
            smallPaint.textAlign = Paint.Align.CENTER
            val hint = if (query.isEmpty()) "暂无内容，复制文本后将在此显示"
            else "未找到与「" + query + "」匹配的内容"
            canvas.drawText(hint, width / 2f, listTop + context.dp(28f), smallPaint)
            smallPaint.textAlign = Paint.Align.LEFT
            return
        }

        canvas.save()
        canvas.clipRect(0f, listTop, width.toFloat(), height.toFloat())
        var y = listTop - scrollY
        for (i in rows.indices) {
            val row = rows[i]
            if (row.groupStart) {
                smallPaint.textSize = context.sp(11f)
                smallPaint.color = theme.hintText
                smallPaint.textAlign = Paint.Align.LEFT
                val label = when (row.kind) {
                    0 -> "常用语"
                    1 -> "系统剪贴板"
                    2 -> "收藏"
                    else -> "最近使用"
                }
                canvas.drawText(label, context.dp(16f), y + groupHeight - context.dp(8f), smallPaint)
                y += groupHeight
            }
            if (y + rowHeight > listTop - rowHeight && y < height) {
                drawRow(canvas, i, y)
            }
            y += rowHeight
        }
        canvas.restore()
    }

    private fun drawRow(canvas: Canvas, index: Int, top: Float) {
        val row = rows[index]
        tmpRect.set(
            context.dp(10f), top + context.dp(3f),
            width - context.dp(10f), top + rowHeight - context.dp(3f)
        )
        fillPaint.color = when {
            index == pressedRow -> theme.candidateSelectedBackground
            row.kind == 1 -> theme.keyPressedBackground
            else -> theme.keyBackground
        }
        canvas.drawRoundRect(tmpRect, context.dp(12f), context.dp(12f), fillPaint)

        textPaint.textSize = context.sp(14f)
        textPaint.color = theme.textPrimary
        textPaint.textAlign = Paint.Align.LEFT
        val fm = textPaint.fontMetrics
        val baseline = tmpRect.top + context.dp(22f) - (fm.ascent + fm.descent) / 2f
        val maxWidth = tmpRect.width() - context.dp(30f)
        val single = row.text.replace("\n", " ")
        val shown = android.text.TextUtils.ellipsize(
            single, android.text.TextPaint(textPaint), maxWidth, android.text.TextUtils.TruncateAt.END
        ).toString()
        fontResolver.draw(canvas, shown, tmpRect.left + context.dp(12f), baseline, textPaint, baseTypeface)

        smallPaint.textSize = context.sp(11f)
        smallPaint.color = theme.hintText
        smallPaint.textAlign = Paint.Align.LEFT
        val metaBaseline = tmpRect.bottom - context.dp(12f) -
            (smallPaint.fontMetrics.ascent + smallPaint.fontMetrics.descent) / 2f
        canvas.drawText(
            row.meta + " · " + row.text.length + " 字",
            tmpRect.left + context.dp(12f), metaBaseline, smallPaint
        )

        if (row.kind == 2) {
            smallPaint.color = theme.accent
            smallPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("★", tmpRect.right - context.dp(12f), metaBaseline, smallPaint)
            smallPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun hitRow(y: Float): Int {
        if (y < listTop) return -1
        val local = y + scrollY - listTop
        var acc = 0f
        for (i in rows.indices) {
            if (rows[i].groupStart) acc += groupHeight
            if (local >= acc && local < acc + rowHeight) return i
            acc += rowHeight
        }
        return -1
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        val index = longPressRow
        longPressRow = -1
        if (index < 0) return@Runnable
        val row = rows.getOrNull(index) ?: return@Runnable
        when (row.kind) {
            0 -> phrases.remove(row.text)   // 常用语：长按删除
            2 -> store.remove(row.text)     // 收藏：长按取消收藏
            else -> store.add(row.text)     // 当前剪贴板 / 历史：长按收藏
        }
        runCatching {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        refresh()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                dragging = false
                handler.removeCallbacks(longPressRunnable)
                if (event.y < handleHeight) {
                    handleDragging = true
                    handleStartY = event.y
                    return true
                }
                if (closeRect.contains(event.x, event.y)) {
                    listener?.onClose()
                    return true
                }
                if (searchRect.contains(event.x, event.y)) {
                    searchActive = true
                    listener?.onSearchFocus(true)
                    return true
                }
                pressedRow = hitRow(event.y)
                if (pressedRow >= 0) {
                    longPressRow = pressedRow
                    handler.postDelayed(longPressRunnable, 420)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (handleDragging) {
                    translationY = (event.y - handleStartY).coerceAtLeast(0f)
                    return true
                }
                if (!dragging && abs(event.y - downY) > context.dp(6f)) {
                    dragging = true
                    pressedRow = -1
                    longPressRow = -1
                    handler.removeCallbacks(longPressRunnable)
                }
                if (dragging) {
                    scrollY = (scrollY - (event.y - downY)).coerceIn(0f, maxScroll())
                    downY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                longPressRow = -1
                if (handleDragging) {
                    handleDragging = false
                    if (translationY > context.dp(56f)) listener?.onClose()
                    else animate().translationY(0f).setDuration(120).start()
                    return true
                }
                if (!dragging) {
                    if (closeRect.contains(event.x, event.y)) {
                        listener?.onClose()
                    } else if (searchRect.contains(event.x, event.y)) {
                        searchActive = true
                        listener?.onSearchFocus(true)
                    } else {
                        val index = hitRow(event.y)
                        if (index >= 0 && index == pressedRow) {
                            val row = rows[index]
                            if (row.text.isNotEmpty()) {
                                if (row.kind == 3) store.touch(row.text)
                                listener?.onPaste(row.text)
                            }
                        }
                    }
                }
                pressedRow = -1
                dragging = false
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                pressedRow = -1
                longPressRow = -1
                dragging = false
                handleDragging = false
                translationY = 0f
                invalidate()
                return true
            }
        }
        return true
    }

    private fun iconDrawable(resId: Int): android.graphics.drawable.Drawable? {
        val key = (resId.toLong() shl 32) or (theme.textSecondary.toLong() and 0xFFFFFFFFL)
        drawableCache[key]?.let { return it }
        val d = androidx.core.content.ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
        androidx.core.graphics.drawable.DrawableCompat.setTint(d, theme.textSecondary)
        drawableCache[key] = d
        return d
    }

    fun animateIn() {
        animate().cancel()
        alpha = 0f
        translationY = context.dp(14f)
        animate().alpha(1f).translationY(0f).setDuration(170).start()
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
