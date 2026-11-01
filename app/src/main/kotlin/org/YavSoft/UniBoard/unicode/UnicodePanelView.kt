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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.YavSoft.UniBoard.R
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.core.dp
import org.YavSoft.UniBoard.core.sp
import org.YavSoft.UniBoard.font.FontResolver
import org.YavSoft.UniBoard.ui.theme.KeyboardTheme
import org.YavSoft.UniBoard.ui.theme.KeyboardThemes
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Unicode 字符面板：搜索、区块浏览、码点解析与最近使用。
 *
 * 面板本身不弹系统键盘，输入由主键盘路由进来（见 UniBoardImeService）。
 * 每个字符都用字体回退链绘制，保证生僻字也能显示出真实字形。
 */
class UnicodePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onPick(text: String)
        fun onClose()
        /** 搜索框获得/失去焦点时通知输入法：需要弹出或收起主键盘 */
        fun onSearchFocus(active: Boolean) {}
    }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardThemes.resolve(context, Prefs(context))
        set(value) {
            field = value
            invalidate()
        }

    private val fontResolver by lazy { FontResolver.get(context) }
    private val recents = RecentChars(context).apply { load() }
    private val worker = Executors.newSingleThreadExecutor()
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    private var query: String = ""
    private var results: List<Int> = emptyList()
    private var blockIndex: Int = -1
    private var searching: Boolean = false
    private var statusText: String = ""

    private var scrollY = 0f
    private var touchDownY = 0f
    private var dragging = false
    private var pressedCell = -1
    private var pressProgress = 0f
    private var handleDragging = false
    private var handleStartY = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = context.sp(15f)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = context.sp(23f)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = context.sp(12f)
    }
    private val baseTypeface: Typeface = Typeface.DEFAULT
    private val tmpRect = RectF()
    // 缓存键是 (resId shl 32) or color，是 Long
    private val drawableCache = HashMap<Long, android.graphics.drawable.Drawable>(4)

    /** 顶部拖拽条区域高度 */
    private val handleHeight: Float get() = context.dp(22f)
    private val searchTop: Float get() = handleHeight + context.dp(4f)
    private val searchHeight: Float get() = context.dp(38f)
    private val statusTop: Float get() = searchTop + searchHeight + context.dp(4f)
    private val statusHeight: Float get() = context.dp(24f)
    private val gridTop: Float get() = statusTop + statusHeight
    private val cellSize: Float get() = context.dp(44f)
    private val columns: Int get() = max(1, (width / cellSize).toInt())

    private val closeRect = RectF()
    private val searchRect = RectF()

    /** 搜索框是否处于输入状态（由输入法路由按键进来） */
    var searchActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        isClickable = true
        refreshBlock()
    }

    /** 主键盘把字符路由进来 */
    fun appendText(text: String) {
        if (text.isEmpty()) return
        query += text
        onQueryChanged()
    }

    fun backspace() {
        if (query.isEmpty()) return
        query = query.dropLast(1)
        onQueryChanged()
    }

    fun clearQuery() {
        if (query.isEmpty()) return
        query = ""
        onQueryChanged()
    }

    private fun onQueryChanged() {
        scrollY = 0f
        if (query.isBlank()) {
            searching = false
            refreshBlock()
            invalidate()
            return
        }
        if (looksLikeCodePoint(query)) {
            val cp = UnicodeRepository.parseCodePoint(query)
            if (cp != null) {
                showSingle(cp, false)
                return
            }
            val fixed = UnicodeRepository.correctCodePoint(query)
            if (fixed != null) {
                showSingle(fixed, true)
                return
            }
        }
        val prefixList = UnicodeRepository.suggestByPrefix(query, 240)
        if (prefixList.isNotEmpty()) {
            searching = false
            blockIndex = -1
            results = prefixList
            statusText = "码点联想 " + query.uppercase() + "…"
            invalidate()
            return
        }
        searching = true
        statusText = "正在检索字符名…"
        invalidate()
        val q = query
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        val task = Runnable {
            worker.execute {
                val found = UnicodeRepository.searchByName(q, 160)
                post {
                    if (query != q) return@post
                    searching = false
                    blockIndex = -1
                    results = found
                    statusText = if (found.isEmpty()) "未找到匹配的字符" else "找到 ${found.size} 个字符"
                    invalidate()
                }
            }
        }
        pendingSearch = task
        searchHandler.postDelayed(task, 220)
    }


    /** 判断输入是否像码点：带前缀，或全十六进制且含数字 */
    private fun looksLikeCodePoint(raw: String): Boolean {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return false
        if (t.startsWith("u+") || t.startsWith("0x") || t.startsWith("&#")) return true
        if (t.length > 6) return false
        if (!t.all { it in "0123456789abcdef" }) return false
        return t.any { it.isDigit() }
    }

    private fun showSingle(codePoint: Int, corrected: Boolean) {
        searching = false
        blockIndex = -1
        results = listOf(codePoint)
        val prefix = if (corrected) "已纠正为 " else ""
        statusText = prefix + buildPreview(codePoint)
        invalidate()
    }

    private fun refreshBlock() {
        if (query.isNotEmpty()) return
        if (blockIndex < 0) {
            results = recents.list(96)
            statusText = if (results.isEmpty()) "最近使用记录为空，可切换左侧区块浏览" else "最近使用"
        } else {
            val block = UnicodeRepository.BLOCKS[blockIndex]
            results = UnicodeRepository.charsInBlock(block)
            statusText = block.name + "  " +
                UnicodeRepository.hexLabel(block.start) + "–" + UnicodeRepository.hexLabel(block.end)
        }
        scrollY = 0f
        invalidate()
    }

    private fun buildPreview(codePoint: Int): String {
        val name = UnicodeRepository.nameOf(codePoint)
        val cat = UnicodeRepository.categoryName(codePoint)
        val ch = UnicodeRepository.charOf(codePoint)
        val renderable = fontResolver.isRenderable(codePoint)
        val tail = if (renderable) "" else "  · 当前字体无法显示"
        return "$ch  ${UnicodeRepository.hexLabel(codePoint)}  $cat  $name$tail"
    }

    private fun maxScroll(): Float {
        if (results.isEmpty()) return 0f
        val rows = ceil(results.size / columns.toFloat()).toInt()
        val contentHeight = rows * cellSize
        val viewHeight = height - gridTop
        return max(0f, contentHeight - viewHeight)
    }

    override fun onDraw(canvas: Canvas) {
        // 半屏圆角面板 + 顶部拖拽条
        fillPaint.color = theme.keyboardBackground
        val r = context.dp(18f)
        tmpRect.set(0f, 0f, width.toFloat(), height.toFloat() + r)
        canvas.drawRoundRect(tmpRect, r, r, fillPaint)
        fillPaint.color = theme.hintText
        val hw = context.dp(38f)
        val hh = context.dp(4f)
        tmpRect.set((width - hw) / 2f, context.dp(8f), (width + hw) / 2f, context.dp(8f) + hh)
        canvas.drawRoundRect(tmpRect, hh / 2f, hh / 2f, fillPaint)
        drawSearchRow(canvas)
        drawStatusRow(canvas)
        drawGrid(canvas)
    }

    private fun drawSearchRow(canvas: Canvas) {
        val left = context.dp(8f)
        val right = width - context.dp(46f)
        tmpRect.set(left, searchTop, right, searchTop + searchHeight)
        searchRect.set(tmpRect)
        fillPaint.color = theme.keyBackground
        val r = context.dp(10f)
        canvas.drawRoundRect(tmpRect, r, r, fillPaint)
        linePaint.color = if (searchActive) theme.accent else theme.divider
        canvas.drawRoundRect(tmpRect, r, r, linePaint)
        if (searchActive) {
            // 光标
            val cx = left + context.dp(10f) + (if (query.isEmpty()) 0f
            else fontResolver.measure(query, textPaint, baseTypeface)) + context.dp(1f)
            canvas.drawRect(cx, tmpRect.top + context.dp(9f), cx + context.dp(1.6f),
                tmpRect.bottom - context.dp(9f), linePaint)
        }

        val textX = left + context.dp(10f)
        textPaint.color = theme.textPrimary
        val fm = textPaint.fontMetrics
        val baseline = tmpRect.centerY() - (fm.ascent + fm.descent) / 2f
        if (query.isEmpty()) {
            textPaint.color = theme.hintText
            canvas.drawText("输入码点 4E00 或字符名 SNOWMAN", textX, baseline, textPaint)
        } else {
            fontResolver.draw(canvas, query, textX, baseline, textPaint, baseTypeface)
        }

        closeRect.set(right + context.dp(4f), searchTop, width - context.dp(6f), searchTop + searchHeight)
        val d = iconDrawable(R.drawable.ic_close)
        if (d != null) {
            val size = context.dp(18f)
            val cx = closeRect.centerX()
            val cy = closeRect.centerY()
            d.setBounds(
                (cx - size / 2f).toInt(), (cy - size / 2f).toInt(),
                (cx + size / 2f).toInt(), (cy + size / 2f).toInt()
            )
            d.draw(canvas)
        }
    }

    private fun drawStatusRow(canvas: Canvas) {
        val btn = context.dp(26f)
        prevRect.set(context.dp(2f), statusTop, context.dp(2f) + btn, statusTop + statusHeight)
        nextRect.set(width - btn - context.dp(2f), statusTop, width - context.dp(2f), statusTop + statusHeight)
        val fm = smallPaint.fontMetrics
        val baseline = statusTop + statusHeight / 2f - (fm.ascent + fm.descent) / 2f

        smallPaint.color = theme.textSecondary
        smallPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("\u25C0", prevRect.centerX(), baseline, smallPaint)
        canvas.drawText("\u25B6", nextRect.centerX(), baseline, smallPaint)
        smallPaint.textAlign = Paint.Align.LEFT

        smallPaint.color = theme.hintText
        val indicator = if (searching) "\u68C0\u7D22\u4E2D\u2026" else statusText
        canvas.drawText(indicator, prevRect.right + context.dp(4f), baseline, smallPaint)

        val blockName = if (blockIndex >= 0) UnicodeRepository.BLOCKS[blockIndex].name else ""
        if (blockName.isNotEmpty()) {
            smallPaint.color = theme.textSecondary
            val w = smallPaint.measureText(blockName)
            canvas.drawText(blockName, nextRect.left - context.dp(4f) - w, baseline, smallPaint)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        if (results.isEmpty()) return
        val cols = columns
        val cell = cellSize
        canvas.save()
        canvas.clipRect(0f, gridTop, width.toFloat(), height.toFloat())
        val fm = glyphPaint.fontMetrics
        for (i in results.indices) {
            val row = i / cols
            val col = i % cols
            val x = col * cell
            val y = gridTop + row * cell - scrollY
            if (y + cell < gridTop || y > height) continue
            if (i == pressedCell) {
                tmpRect.set(
                    x + context.dp(2f), y + context.dp(2f),
                    x + cell - context.dp(2f), y + cell - context.dp(2f)
                )
                fillPaint.color = theme.candidateSelectedBackground
                canvas.drawRoundRect(tmpRect, context.dp(8f), context.dp(8f), fillPaint)
            }
            val s = UnicodeRepository.charOf(results[i])
            if (s.isEmpty()) continue
            val w = fontResolver.measure(s, glyphPaint, baseTypeface)
            val baseline = y + cell / 2f - (fm.ascent + fm.descent) / 2f
            glyphPaint.color = theme.textPrimary
            fontResolver.draw(canvas, s, x + (cell - w) / 2f, baseline, glyphPaint, baseTypeface)
        }
        canvas.restore()
    }

    private fun iconDrawable(resId: Int): android.graphics.drawable.Drawable? {
        val color = theme.textSecondary
        val key = (resId.toLong() shl 32) or (color.toLong() and 0xFFFFFFFFL)
        drawableCache[key]?.let { return it }
        val d = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
        DrawableCompat.setTint(d, color)
        drawableCache[key] = d
        return d
    }

    private fun cellIndexAt(y: Float): Int {
        val localY = y + scrollY - gridTop
        if (localY < 0f) return -1
        val row = (localY / cellSize).toInt()
        return row
    }

    private fun hitCell(x: Float, y: Float): Int {
        if (y < gridTop) return -1
        val cols = columns
        val localY = y + scrollY - gridTop
        val row = (localY / cellSize).toInt()
        val col = (x / cellSize).toInt()
        if (col < 0 || col >= cols) return -1
        val index = row * cols + col
        return if (index in results.indices) index else -1
    }

    private val prevRect = RectF()
    private val nextRect = RectF()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y
                dragging = false
                if (event.y < handleHeight) {
                    handleDragging = true
                    handleStartY = event.y
                    return true
                }
                pressedCell = hitCell(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (handleDragging) {
                    translationY = (event.y - handleStartY).coerceAtLeast(0f)
                    return true
                }
                if (!dragging && abs(event.y - touchDownY) > context.dp(6f)) {
                    dragging = true
                    pressedCell = -1
                }
                if (dragging) {
                    scrollY -= event.y - touchDownY
                    scrollY = scrollY.coerceIn(0f, maxScroll())
                    touchDownY = event.y
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
                if (!dragging) {
                    if (closeRect.contains(event.x, event.y)) {
                        listener?.onClose()
                    } else if (prevRect.contains(event.x, event.y)) {
                        switchBlock(-1)
                    } else if (nextRect.contains(event.x, event.y)) {
                        switchBlock(1)
                    } else if (pressedCell >= 0) {
                        val cp = results[pressedCell]
                        recents.add(cp)
                        listener?.onPick(UnicodeRepository.charOf(cp))
                    }
                }
                pressedCell = -1
                dragging = false
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedCell = -1
                dragging = false
                handleDragging = false
                translationY = 0f
                invalidate()
            }
        }
        return true
    }

    private fun switchBlock(delta: Int) {
        val n = UnicodeRepository.BLOCKS.size
        blockIndex = if (blockIndex < 0) {
            if (delta > 0) 0 else n - 1
        } else {
            (blockIndex + delta + n) % n
        }
        query = ""
        refreshBlock()
    }

    /** 面板出现动画 */
    fun animateIn() {
        animate().cancel()
        alpha = 0f
        translationY = context.dp(14f)
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(170)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun animateOut() {
        animate().cancel()
        animate()
            .alpha(0f)
            .translationY(context.dp(10f))
            .setDuration(120)
            .start()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
