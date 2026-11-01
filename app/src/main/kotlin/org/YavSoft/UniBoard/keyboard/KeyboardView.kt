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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import org.YavSoft.UniBoard.R
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.core.SoundFeedback
import org.YavSoft.UniBoard.core.dp
import org.YavSoft.UniBoard.core.sp
import org.YavSoft.UniBoard.ui.theme.KeyboardTheme
import org.YavSoft.UniBoard.ui.theme.KeyboardThemes
import kotlin.math.abs
import kotlin.math.min

/**
 * 自绘键盘视图。
 *
 * 所有键帽、文字、图标均由 Canvas 绘制，以便完全控制圆角、阴影、
 * 按压弹性动画与长按备选弹层，行为参照主流输入法的交互习惯。
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        /** 按键动作 */
        fun onKey(action: KeyAction)
        /** 空格左右滑动移动光标，offset 为 ±1 */
        fun onMoveCursor(offset: Int)
    }

    var listener: Listener? = null

    var prefs: Prefs = Prefs(context)
        set(value) {
            field = value
            theme = KeyboardThemes.resolve(context, value)
            longPressDelay = value.longPressMs.toLong()
        }

    var theme: KeyboardTheme = KeyboardThemes.resolve(context, Prefs(context))
        set(value) {
            field = value
            rebuildPaints()
            invalidate()
        }

    /** 空格键上显示的语言提示 */
    var spaceHint: String? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Shift 是否处于锁定（大写）状态 */
    var shiftLocked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val drawableCache = HashMap<Long, Drawable>(16)
    private val popupRect = RectF()
    private val tmpRect = RectF()

    private class Hit(val key: Key, val rect: RectF, val rowIndex: Int)
    private var hits: List<Hit> = emptyList()
    private var layoutRef: KeyboardLayout? = null

    /** 布局切换时的入场进度（0→1），用于行错位淡入动画 */
    private var layoutProgress = 1f
    private var layoutAnimator: ValueAnimator? = null

    private class PressedState(val hit: Hit) {
        var progress = 0f
        var longPressFired = false
        var popupShown = false
        var popupIndex = 0
        var popupProgress = 0f
        var popupItemWidth = 0f
        var swiped = false
        var downX = 0f
        var lastX = 0f
    }

    private var pressed: PressedState? = null
    private var pressAnimator: ValueAnimator? = null
    private var popupAnimator: ValueAnimator? = null
    private var longPressDelay = ViewConfiguration.getLongPressTimeout().toLong()

    init {
        isClickable = true
        isFocusable = false
        rebuildPaints()
    }

    private fun rebuildPaints() {
        textPaint.textSize = context.sp(theme.keyTextSizeSp)
        hintPaint.textSize = context.sp(11f)
        hintPaint.color = theme.hintText
    }

    /** 设置布局（切换字母/符号页时调用） */
    fun setLayout(layout: KeyboardLayout) {
        layoutRef = layout
        pressed = null
        computeGeometry()
        requestLayout()
        animateLayoutIn()
    }

    /** 切换布局时，各行自上而下依次滑入，避免整块键盘硬闪 */
    private fun animateLayoutIn() {
        layoutAnimator?.cancel()
        if (!prefs.keyAnimation) {
            layoutProgress = 1f
            invalidate()
            return
        }
        layoutProgress = 1f
        // 整体淡入，比逐行位移更顺滑，也不会在频繁切换时闪
        alpha = 0.82f
        translationY = context.dp(3f)
        animate().cancel()
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(130)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    fun currentLayoutId(): String? = layoutRef?.id

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val rows = layoutRef?.rows?.size ?: 4
        val desired = (rows * context.dp(theme.rowHeightDp) + context.dp(2f)).toInt()
        val h = if (mode == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else desired
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
    }

    private fun computeGeometry() {
        val layout = layoutRef
        if (layout == null || width == 0 || height == 0) {
            hits = emptyList()
            return
        }
        val rows = layout.rows
        if (rows.isEmpty()) {
            hits = emptyList()
            return
        }
        val padH = context.dp(3.5f)
        val padV = context.dp(1.5f)
        val rowHeight = (height - padV * 2f) / rows.size
        val gap = context.dp(theme.keyGapDp)
        val result = ArrayList<Hit>(40)
        for (r in rows.indices) {
            val row = rows[r]
            val avail = width - padH * 2f
            val total = row.totalWeight
            if (total <= 0f) continue
            val insetL = avail * row.leftInset
            val insetR = avail * row.rightInset
            val usable = avail - insetL - insetR
            var x = padH + insetL
            val top = padV + r * rowHeight
            for (key in row.keys) {
                val kw = usable * (key.weight / total)
                val rect = RectF(x, top, x + kw, top + rowHeight)
                rect.inset(gap / 2f, gap / 2f)
                result.add(Hit(key, rect, r))
                x += kw
            }
        }
        hits = result
    }

    override fun onDraw(canvas: Canvas) {
        val p = pressed
        for (hit in hits) {
            val press = if (p != null && p.hit === hit) p.progress else 0f
            drawKey(canvas, hit, press)
        }
        if (p != null && p.popupShown) drawPopup(canvas, p)
    }

    private fun drawKey(canvas: Canvas, hit: Hit, press: Float) {
        val key = hit.key
        val rect = hit.rect
        val scale = 1f - 0.045f * press.coerceIn(0f, 1.2f)
        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())
        val radius = context.dp(theme.keyRadiusDp)

        if (theme.keyElevationDp > 0f) {
            shadowPaint.color = theme.shadow
            tmpRect.set(rect)
            tmpRect.offset(0f, context.dp(theme.keyElevationDp))
            canvas.drawRoundRect(tmpRect, radius, radius, shadowPaint)
        }

        fillPaint.color = keyFill(key, press)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        if (key.iconRes != 0) {
            drawKeyIcon(canvas, key, rect)
        } else if (key.label.isNotEmpty()) {
            textPaint.color = keyLabelColor(key)
            textPaint.textSize = context.sp(
                if (key.style == KeyStyle.CHAR) theme.keyTextSizeSp else theme.funcTextSizeSp
            )
            val fm = textPaint.fontMetrics
            val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(key.label, rect.centerX(), baseline, textPaint)
        }

        if (key.style == KeyStyle.SPACE) {
            val hint = spaceHint
            if (!hint.isNullOrEmpty()) {
                hintPaint.color = theme.hintText
                hintPaint.textSize = context.sp(theme.hintTextSizeSp)
                val fm = hintPaint.fontMetrics
                canvas.drawText(
                    hint, rect.centerX(),
                    rect.centerY() - (fm.ascent + fm.descent) / 2f, hintPaint
                )
            }
        }
        canvas.restore()
    }

    private fun keyFill(key: Key, press: Float): Int = when (key.style) {
        KeyStyle.CHAR, KeyStyle.SPACE ->
            ColorUtils.blendARGB(theme.keyBackground, theme.keyPressedBackground, press.coerceIn(0f, 1f))
        KeyStyle.FUNCTION ->
            ColorUtils.blendARGB(theme.funcKeyBackground, theme.funcKeyPressedBackground, press.coerceIn(0f, 1f))
        KeyStyle.ACTION ->
            ColorUtils.blendARGB(theme.accent, theme.accentPressed, press.coerceIn(0f, 1f))
    }

    private fun keyLabelColor(key: Key): Int = when (key.style) {
        KeyStyle.CHAR, KeyStyle.SPACE -> theme.textPrimary
        KeyStyle.FUNCTION -> theme.textSecondary
        KeyStyle.ACTION -> theme.onAccent
    }

    private fun drawKeyIcon(canvas: Canvas, key: Key, rect: RectF) {
        val color = when (key.style) {
            KeyStyle.ACTION -> theme.onAccent
            KeyStyle.FUNCTION -> theme.textSecondary
            else -> theme.textPrimary
        }
        val d = iconDrawable(key.iconRes, color)
        val size = context.dp(
            if (key.style == KeyStyle.CHAR) 22f else 20f
        )
        val cx = rect.centerX()
        val cy = rect.centerY()
        d.setBounds(
            (cx - size / 2f).toInt(), (cy - size / 2f).toInt(),
            (cx + size / 2f).toInt(), (cy + size / 2f).toInt()
        )
        d.draw(canvas)
    }

    private fun iconDrawable(resId: Int, color: Int): Drawable {
        val cacheKey = (resId.toLong() shl 32) or (color.toLong() and 0xFFFFFFFFL)
        drawableCache[cacheKey]?.let { return it }
        val d = ContextCompat.getDrawable(context, resId)?.mutate() ?: return emptyDrawable()
        DrawableCompat.setTint(d, color)
        drawableCache[cacheKey] = d
        return d
    }

    private fun emptyDrawable(): Drawable = android.graphics.drawable.ColorDrawable(0)

    private fun popupItems(key: Key): List<String> {
        // popups 属于 KeyAction.Insert，不是 Key 自身的属性
        val popups = (key.action as? KeyAction.Insert)?.popups ?: return emptyList()
        if (popups.isEmpty()) return emptyList()
        return listOf(key.label) + popups
    }

    private fun layoutPopup(p: PressedState) {
        val items = popupItems(p.hit.key)
        if (items.isEmpty()) {
            popupRect.setEmpty()
            return
        }
        val margin = context.dp(5f)
        val maxWidth = width - margin * 2f
        val itemW = min(context.dp(42f), maxWidth / items.size)
        val totalW = itemW * items.size
        val h = context.dp(46f)
        var left = p.hit.rect.centerX() - totalW / 2f
        val maxLeft = (width - totalW - margin).coerceAtLeast(margin)
        left = left.coerceIn(margin, maxLeft)
        var top = p.hit.rect.top - h - context.dp(6f)
        if (top < 0f) top = 0f
        popupRect.set(left, top, left + totalW, top + h)
        p.popupItemWidth = itemW
    }

    private fun drawPopup(canvas: Canvas, p: PressedState) {
        val items = popupItems(p.hit.key)
        if (items.isEmpty() || p.popupProgress <= 0f) return
        val prog = p.popupProgress
        val pivotX = p.hit.rect.centerX()
        val pivotY = popupRect.bottom
        canvas.save()
        val s = 0.82f + 0.18f * prog
        canvas.scale(s, s, pivotX, pivotY)
        val radius = context.dp(10f)
        fillPaint.color = theme.popupBackground
        canvas.drawRoundRect(popupRect, radius, radius, fillPaint)

        val selLeft = popupRect.left + p.popupIndex * p.popupItemWidth
        tmpRect.set(
            selLeft + context.dp(3f), popupRect.top + context.dp(3f),
            selLeft + p.popupItemWidth - context.dp(3f), popupRect.bottom - context.dp(3f)
        )
        fillPaint.color = theme.popupPressedBackground
        canvas.drawRoundRect(tmpRect, context.dp(8f), context.dp(8f), fillPaint)

        textPaint.textSize = context.sp(theme.popupTextSizeSp)
        val fm = textPaint.fontMetrics
        val baseline = popupRect.centerY() - (fm.ascent + fm.descent) / 2f
        for (i in items.indices) {
            textPaint.color = if (i == p.popupIndex) theme.onAccent else theme.textPrimary
            val x = popupRect.left + p.popupItemWidth * i + p.popupItemWidth / 2f
            canvas.drawText(items[i], x, baseline, textPaint)
        }
        canvas.restore()
    }

    private fun hitTest(x: Float, y: Float): Hit? {
        for (hit in hits) {
            if (hit.rect.contains(x, y)) return hit
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }
        return true
    }

    private fun handleDown(event: MotionEvent): Boolean {
        val hit = hitTest(event.x, event.y) ?: return false
        pressAnimator?.cancel()
        val p = PressedState(hit)
        p.downX = event.x
        p.lastX = event.x
        pressed = p
        animatePress(p, true)
        feedback(hit.key)
        removeCallbacks(longPressRunnable)
        postDelayed(longPressRunnable, longPressDelay)
        invalidate()
        return true
    }

    private fun handleMove(event: MotionEvent) {
        val p = pressed ?: return
        if (p.popupShown) {
            val items = popupItems(p.hit.key)
            if (items.isEmpty() || p.popupItemWidth <= 0f) return
            val raw = ((event.x - popupRect.left) / p.popupItemWidth).toInt()
            val idx = raw.coerceIn(0, items.size - 1)
            if (idx != p.popupIndex) {
                p.popupIndex = idx
                feedback(p.hit.key)
                invalidate()
            }
            return
        }
        val key = p.hit.key
        if (key.action == KeyAction.Space && prefs.swipeToDelete) {
            if (!p.swiped && abs(event.x - p.downX) > context.dp(14f)) {
                p.swiped = true
                removeCallbacks(longPressRunnable)
                p.lastX = event.x
            }
            if (p.swiped) {
                val dx = event.x - p.lastX
                if (abs(dx) > context.dp(12f)) {
                    listener?.onMoveCursor(if (dx > 0) 1 else -1)
                    p.lastX = event.x
                }
                return
            }
        }
        if (!p.longPressFired && hitTest(event.x, event.y) !== p.hit) {
            handleCancel()
        }
    }

    private fun handleUp(event: MotionEvent) {
        val p = pressed ?: return
        removeCallbacks(longPressRunnable)
        stopRepeat()
        if (p.popupShown) {
            val items = popupItems(p.hit.key)
            val text = items.getOrNull(p.popupIndex)
            p.popupShown = false
            popupAnimator?.cancel()
            pressed = null
            invalidate()
            if (!text.isNullOrEmpty()) listener?.onKey(KeyAction.Insert(text))
            performClick()
            return
        }
        val inside = hitTest(event.x, event.y) === p.hit
        val swiped = p.swiped
        if (inside && !swiped) {
            listener?.onKey(p.hit.key.action)
            performClick()
        }
        releasePress(p)
    }

    /** 松手回弹：让键帽从按下状态弹回，而不是瞬间跳回 */
    private fun releasePress(p: PressedState) {
        pressAnimator?.cancel()
        if (!prefs.keyAnimation || p.progress <= 0.01f) {
            if (pressed === p) pressed = null
            invalidate()
            return
        }
        pressAnimator = ValueAnimator.ofFloat(p.progress, 0f).apply {
            duration = 115
            interpolator = OvershootInterpolator(1.9f)
            addUpdateListener {
                p.progress = it.animatedValue as Float
                if (pressed === p) invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (pressed === p) {
                        pressed = null
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    private fun handleCancel() {
        removeCallbacks(longPressRunnable)
        stopRepeat()
        val p = pressed
        if (p != null) {
            p.popupShown = false
            popupAnimator?.cancel()
        }
        pressed = null
        invalidate()
    }

    private val longPressRunnable = Runnable {
        val p = pressed ?: return@Runnable
        val key = p.hit.key
        // 双行为键（例如 Unicode 盘的 U+ / 0x）：单击插入文本，长按走这里
        val longAction = key.longPressAction
        if (longAction != null) {
            p.longPressFired = true
            pressed = null
            invalidate()
            feedback(key)
            listener?.onKey(longAction)
            return@Runnable
        }
        p.longPressFired = true
        if (popupItems(key).size > 1 && prefs.popupOnLongPress) {
            showPopup(p)
        } else if (key.repeatable) {
            startRepeat()
        }
    }

    private fun showPopup(p: PressedState) {
        p.popupShown = true
        p.popupIndex = 0
        p.popupProgress = 0f
        layoutPopup(p)
        popupAnimator?.cancel()
        popupAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 130
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                p.popupProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        feedback(p.hit.key)
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val p = pressed ?: return
            if (!p.longPressFired) return
            listener?.onKey(p.hit.key.action)
            postDelayed(this, 62)
        }
    }

    private fun startRepeat() {
        stopRepeat()
        postDelayed(repeatRunnable, 30)
    }

    private fun stopRepeat() {
        removeCallbacks(repeatRunnable)
    }

    private fun animatePress(p: PressedState, down: Boolean) {
        pressAnimator?.cancel()
        if (!down) {
            p.progress = 0f
            invalidate()
            return
        }
        if (!prefs.keyAnimation) {
            p.progress = 1f
            invalidate()
            return
        }
        pressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 90
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener {
                p.progress = it.animatedValue as Float
                if (pressed === p) invalidate()
            }
            start()
        }
    }

    private fun feedback(key: Key) {
        if (prefs.hapticEnabled) {
            vibrate(prefs.hapticStrength.coerceIn(6, 40).toLong())
        }
        if (prefs.soundEnabled) {
            SoundFeedback.playKey(context, prefs.soundVolume, prefs.soundMode)
        }
    }

    private fun vibrate(ms: Long) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        stopRepeat()
        pressAnimator?.cancel()
        popupAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
