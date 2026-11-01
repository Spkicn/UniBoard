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

package org.YavSoft.UniBoard.font

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import org.YavSoft.UniBoard.core.Prefs
import java.io.File

/**
 * 字形覆盖检测与字体回退。
 *
 * 输入法无法决定目标应用如何渲染文字，但可以在自身的候选栏、Unicode
 * 字符面板、预览区里为每个码点挑选真正含有该字形的字体，从而保证生僻字
 * 不会被渲染成“口口”豆腐块。
 *
 * 回退顺序：被请求的基准字体 → 用户导入字体 → 内置兜底字体（GNU Unifont）
 * → 系统字体表中首个含该字形的字体。
 */
class FontResolver private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: FontResolver? = null

        fun get(context: Context): FontResolver {
            return instance ?: synchronized(this) {
                instance ?: FontResolver(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context
    private val prefs = Prefs(context)
    private val probe = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cache = HashMap<Int, Typeface>(64)

    @Volatile
    private var fallbackChain: List<Typeface>? = null

    /** 内置兜底字体（GNU Unifont） */
    private var builtin: Typeface? = null

    /** 预加载字体，建议在后台线程调用 */
    fun preload() {
        ensureLoaded()
    }

    private fun ensureLoaded(): List<Typeface> {
        fallbackChain?.let { return it }
        synchronized(this) {
            fallbackChain?.let { return it }
            val list = ArrayList<Typeface>(8)
            builtin = runCatching {
                Typeface.createFromAsset(appContext.assets, "fonts/unifont.otf")
            }.getOrNull()
            builtin?.let { list.add(it) }

            val imported = prefs.importedFontPath
            if (!imported.isNullOrEmpty()) {
                runCatching { Typeface.createFromFile(File(imported)) }.getOrNull()?.let { list.add(it) }
            }
            list.addAll(loadSystemFonts())
            fallbackChain = list
            return list
        }
    }

    private fun loadSystemFonts(): List<Typeface> {
        val dir = File("/system/fonts")
        val files = dir.listFiles() ?: return emptyList()
        val candidates = files.asSequence()
            .filter { it.isFile && it.length() > 32 * 1024 }
            .map { file ->
                val n = file.name.lowercase()
                val priority = when {
                    n.contains("emoji") -> 4
                    n.contains("cjk") || n.contains("han") -> 3
                    n.contains("symbol") -> 2
                    n.contains("noto") || n.contains("droid") -> 1
                    else -> 0
                }
                file to priority
            }
            .sortedWith(compareByDescending<Pair<File, Int>> { it.second }.thenByDescending { it.first.length() })
            .take(12)
            .map { it.first }
        val out = ArrayList<Typeface>(candidates.count())
        for (f in candidates) {
            runCatching { Typeface.createFromFile(f) }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    /** 为指定码点挑选可用字体 */
    fun typefaceFor(codePoint: Int, base: Typeface = Typeface.DEFAULT): Typeface {
        if (!prefs.fontFallback) return base
        cache[codePoint]?.let { return it }
        // 用户选择「全部非 ASCII 都用内置字体」时，只要内置字体有该字形就直接用
        if (prefs.builtinFontScope == 1 && codePoint > 0x7F) {
            ensureLoaded()
            val b = builtin
            if (b != null && hasGlyph(b, codePoint)) {
                cache[codePoint] = b
                return b
            }
        }
        val result: Typeface
        if (hasGlyph(base, codePoint)) {
            result = base
        } else {
            var found: Typeface? = null
            for (tf in ensureLoaded()) {
                if (hasGlyph(tf, codePoint)) {
                    found = tf
                    break
                }
            }
            result = found ?: base
        }
        cache[codePoint] = result
        return result
    }

    /** 判断某个字体是否含有该码点的字形 */
    fun hasGlyph(typeface: Typeface, codePoint: Int): Boolean {
        probe.typeface = typeface
        val s = String(Character.toChars(codePoint))
        return runCatching { probe.hasGlyph(s) }.getOrDefault(false)
    }

    /** 某字符能否在本机正常显示（复用 typefaceFor 的缓存） */
    fun isRenderable(codePoint: Int, base: Typeface = Typeface.DEFAULT): Boolean {
        if (hasGlyph(base, codePoint)) return true
        return typefaceFor(codePoint, base) !== base
    }

    /** 内置字体状态：用于设置页展示 */
    fun builtinStatus(): String {
        ensureLoaded()
        val tf = builtin ?: return "内置字体未加载（assets/fonts/unifont.otf 缺失或无法解析）"
        return "内置字体已加载：" + (tf.javaClass.simpleName) + " · " + fallbackChain?.size + " 个回退字体"
    }

    /** 重新加载内置字体与回退链 */
    fun reloadBuiltin(): String {
        invalidate()
        ensureLoaded()
        val tf = builtin
        return if (tf != null) "内置字体已重新加载" else "内置字体加载失败：assets/fonts/unifont.otf"
    }

    /** 内置兜底字体的 Typeface（可能为 null） */
    fun builtinTypeface(): Typeface? {
        ensureLoaded()
        return builtin
    }

    /** 一段使用同一字体的文本 */
    data class Run(val text: String, val typeface: Typeface)

    /** 按字形可用性把文本切成若干段 */
    fun segment(text: String, base: Typeface = Typeface.DEFAULT): List<Run> {
        if (text.isEmpty()) return emptyList()
        if (isAsciiOnly(text)) return listOf(Run(text, base))
        val out = ArrayList<Run>(3)
        var i = 0
        var currentTf: Typeface? = null
        val sb = StringBuilder()
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            val tf = typefaceFor(cp, base)
            if (currentTf == null) {
                currentTf = tf
            } else if (currentTf !== tf) {
                out.add(Run(sb.toString(), currentTf))
                sb.setLength(0)
                currentTf = tf
            }
            sb.appendRange(text, i, i + len)
            i += len
        }
        if (sb.isNotEmpty() && currentTf != null) out.add(Run(sb.toString(), currentTf!!))
        return out
    }

    /** 以回退链绘制文本，返回实际宽度 */
    fun draw(canvas: Canvas, text: String, x: Float, baseline: Float, paint: Paint, base: Typeface): Float {
        var cx = x
        val old = paint.typeface
        for (run in segment(text, base)) {
            paint.typeface = run.typeface
            canvas.drawText(run.text, cx, baseline, paint)
            cx += paint.measureText(run.text)
        }
        paint.typeface = old
        return cx - x
    }

    /** 以回退链测量文本宽度 */
    fun measure(text: String, paint: Paint, base: Typeface): Float {
        var w = 0f
        val old = paint.typeface
        for (run in segment(text, base)) {
            paint.typeface = run.typeface
            w += paint.measureText(run.text)
        }
        paint.typeface = old
        return w
    }

    private fun isAsciiOnly(text: String): Boolean {
        for (ch in text) {
            if (ch.code >= 0x80) return false
        }
        return true
    }

    /** 清理缓存（切换导入字体后调用） */
    fun invalidate() {
        synchronized(this) {
            cache.clear()
            fallbackChain = null
        }
    }
}
