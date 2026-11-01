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

package org.YavSoft.UniBoard.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import org.YavSoft.UniBoard.core.Prefs

/**
 * 键盘配色与尺寸。参考现代输入法的克制配色：底色浅一档，键帽近白，
 * 功能键再深一档，强调色只用在回车等关键动作上。
 */
data class KeyboardTheme(
    @ColorInt val keyboardBackground: Int,
    @ColorInt val keyBackground: Int,
    @ColorInt val keyPressedBackground: Int,
    @ColorInt val funcKeyBackground: Int,
    @ColorInt val funcKeyPressedBackground: Int,
    @ColorInt val accent: Int,
    @ColorInt val accentPressed: Int,
    @ColorInt val onAccent: Int,
    @ColorInt val textPrimary: Int,
    @ColorInt val textSecondary: Int,
    @ColorInt val hintText: Int,
    @ColorInt val divider: Int,
    @ColorInt val candidateBarBackground: Int,
    @ColorInt val candidateSelectedBackground: Int,
    @ColorInt val popupBackground: Int,
    @ColorInt val popupPressedBackground: Int,
    @ColorInt val shadow: Int,
    val isDark: Boolean,
    val keyRadiusDp: Float = 10.5f,
    val keyGapDp: Float = 3.4f,
    val keyElevationDp: Float = 1.6f,
    val keyTextSizeSp: Float = 19f,
    val funcTextSizeSp: Float = 12.5f,
    val hintTextSizeSp: Float = 10f,
    val candidateTextSizeSp: Float = 17f,
    val popupTextSizeSp: Float = 20f,
    val rowHeightDp: Float = 52f,
    val candidateBarHeightDp: Float = 46f
) {
    /** 四色强调调色板，用于候选栏选中、符号分组等装饰 */
    val brandPalette: IntArray
        get() = intArrayOf(
            0xFF4285F4.toInt(),
            0xFFEA4335.toInt(),
            0xFFFBBC05.toInt(),
            0xFF34A853.toInt()
        )
}

/**
 * 主题解析入口。
 *
 * 按系统深浅色与用户主题色生成整套键盘配色，并按明暗两套结果缓存；
 * 主题相关设置变化后调用 [invalidate] 清掉缓存即可。
 */
object KeyboardThemes {

    private var cachedLight: KeyboardTheme? = null
    private var cachedDark: KeyboardTheme? = null
    private var cachedDynLight: KeyboardTheme? = null
    private var cachedDynDark: KeyboardTheme? = null

    fun resolve(context: Context, prefs: Prefs): KeyboardTheme {
        val dark = when (prefs.themeMode) {
            Prefs.THEME_LIGHT -> false
            Prefs.THEME_DARK -> true
            else -> (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        // 动态取色（Material You）：从系统取色，跟着壁纸/系统主题走
        if (prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cachedDyn = if (dark) cachedDynDark else cachedDynLight
            if (cachedDyn != null) return cachedDyn
            val dyn = dynamicTheme(context, dark)
            if (dyn != null) {
                val userAccent = prefs.accentColor
                val result = (if (userAccent != 0) dyn.copy(
                    accent = userAccent,
                    accentPressed = shade(userAccent, if (dark) 1.28f else 0.82f)
                ) else dyn).copy(
                    keyRadiusDp = prefs.keyRadiusDp,
                    rowHeightDp = prefs.keyRowHeightDp,
                    candidateBarHeightDp = prefs.candidateBarHeightDp
                ).let { applyPreferences(it, prefs) }
                if (dark) cachedDynDark = result else cachedDynLight = result
                return result
            }
        }

        // 注意：深色主题要读 cachedDark，之前这里写反了，导致深浅切换时拿到另一套缓存
        val cached = if (dark) cachedDark else cachedLight
        if (cached != null) return cached
        val base = if (dark) darkTheme(prefs) else lightTheme(prefs)
        val accent = prefs.accentColor
        val theme = (if (accent != 0) base.copy(
            accent = accent,
            accentPressed = shade(accent, if (dark) 1.28f else 0.82f)
        ) else base).copy(
            keyRadiusDp = prefs.keyRadiusDp,
            rowHeightDp = prefs.keyRowHeightDp,
            candidateBarHeightDp = prefs.candidateBarHeightDp
        ).let { applyPreferences(it, prefs) }
        if (dark) cachedDark = theme else cachedLight = theme
        return theme
    }

    /** 按比例调整亮度，用于生成按下态颜色 */
    private fun shade(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = ((color shr 16) and 0xFF)
        val g = ((color shr 8) and 0xFF)
        val b = color and 0xFF
        fun clamp(v: Int) = v.coerceIn(0, 255)
        return (a shl 24) or
            (clamp((r * factor).toInt()) shl 16) or
            (clamp((g * factor).toInt()) shl 8) or
            clamp((b * factor).toInt())
    }

    /** 主题设置变化后清除缓存 */
    fun invalidate() {
        cachedLight = null
        cachedDark = null
        cachedDynLight = null
        cachedDynDark = null
    }

    /**
     * 从系统 Material You 调色板取色（Android 12+）。
     *
     * 只依赖系统一定存在的色阶（accent1_200/500、neutral1_10），
     * 其余深浅层次用与黑/白混合算出来，避免不同 ROM 缺色导致整块回退。
     */
    private fun dynamicTheme(context: Context, dark: Boolean): KeyboardTheme? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        fun c(id: Int): Int? = runCatching { context.getColor(id) }.getOrNull()

        val accent = c(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_500)
            ?: return null
        val onAccent = if (dark) 0xFF1B1B1F.toInt() else 0xFFFFFFFF.toInt()
        val accentPressed = shade(accent, if (dark) 1.25f else 0.82f)

        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF1B1B1F.toInt()

        val kbBg: Int
        val keyBg: Int
        val keyPressed: Int
        val funcBg: Int
        val funcPressed: Int
        val textPrimary: Int
        val textSecondary: Int
        val hintText: Int

        if (dark) {
            val base = c(android.R.color.system_neutral1_10) ?: 0xFF131316.toInt()
            kbBg = base
            keyBg = ColorUtils.blendARGB(base, white, 0.10f)
            keyPressed = ColorUtils.blendARGB(base, white, 0.18f)
            funcBg = ColorUtils.blendARGB(base, white, 0.06f)
            funcPressed = ColorUtils.blendARGB(base, white, 0.14f)
            textPrimary = ColorUtils.blendARGB(white, base, 0.14f)
            textSecondary = ColorUtils.blendARGB(white, base, 0.34f)
            hintText = ColorUtils.blendARGB(white, base, 0.50f)
        } else {
            val base = c(android.R.color.system_neutral1_100) ?: white
            kbBg = ColorUtils.blendARGB(base, black, 0.07f)
            keyBg = base
            keyPressed = ColorUtils.blendARGB(base, black, 0.14f)
            funcBg = ColorUtils.blendARGB(base, black, 0.10f)
            funcPressed = ColorUtils.blendARGB(base, black, 0.19f)
            textPrimary = black
            textSecondary = ColorUtils.blendARGB(black, white, 0.35f)
            hintText = ColorUtils.blendARGB(black, white, 0.50f)
        }

        return KeyboardTheme(
            keyboardBackground = kbBg,
            keyBackground = keyBg,
            keyPressedBackground = keyPressed,
            funcKeyBackground = funcBg,
            funcKeyPressedBackground = funcPressed,
            accent = accent,
            accentPressed = accentPressed,
            onAccent = onAccent,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            hintText = hintText,
            divider = if (dark) 0x1AFFFFFF else 0x1A000000,
            candidateBarBackground = kbBg,
            candidateSelectedBackground = ColorUtils.setAlphaComponent(accent, if (dark) 0x33 else 0x22),
            popupBackground = ColorUtils.blendARGB(kbBg, white, if (dark) 0.14f else 0f),
            popupPressedBackground = accent,
            shadow = if (dark) 0x55000000 else 0x22000000,
            isDark = dark
        )
    }

    /** 统一叠加用户偏好：字号、透明度 */
    private fun applyPreferences(theme: KeyboardTheme, prefs: Prefs): KeyboardTheme {
        var t = theme.copy(
            candidateTextSizeSp = prefs.candidateTextSize,
            keyTextSizeSp = prefs.keyTextSize
        )
        val percent = prefs.keyboardAlpha
        if (percent < 100) {
            val a = (255 * percent / 100).coerceIn(0, 255)
            fun withAlpha(c: Int): Int = (c and 0x00FFFFFF) or (a shl 24)
            t = t.copy(
                keyboardBackground = withAlpha(t.keyboardBackground),
                candidateBarBackground = withAlpha(t.candidateBarBackground)
            )
        }
        return t
    }

    private fun lightTheme(prefs: Prefs): KeyboardTheme = KeyboardTheme(
        keyboardBackground = 0xFFE7E9EE.toInt(),
        keyBackground = 0xFFFFFFFF.toInt(),
        keyPressedBackground = 0xFFD2D6DE.toInt(),
        funcKeyBackground = 0xFFD3D7DF.toInt(),
        funcKeyPressedBackground = 0xFFB8BEC9.toInt(),
        accent = 0xFF1A73E8.toInt(),
        accentPressed = 0xFF1557B0.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        textPrimary = 0xFF1B1C1E.toInt(),
        textSecondary = 0xFF44474E.toInt(),
        hintText = 0xFF7C8189.toInt(),
        divider = 0x1A000000,
        candidateBarBackground = 0xFFE7E9EE.toInt(),
        candidateSelectedBackground = 0x141A73E8,
        popupBackground = 0xFFFDFDFF.toInt(),
        popupPressedBackground = 0xFF1A73E8.toInt(),
        shadow = 0x22000000,
        isDark = false,
        keyTextSizeSp = if (prefs.chineseMode) 19f else 19f
    )

    private fun darkTheme(prefs: Prefs): KeyboardTheme = KeyboardTheme(
        keyboardBackground = 0xFF141518.toInt(),
        keyBackground = 0xFF33363C.toInt(),
        keyPressedBackground = 0xFF4A4E56.toInt(),
        funcKeyBackground = 0xFF26292E.toInt(),
        funcKeyPressedBackground = 0xFF3C4048.toInt(),
        accent = 0xFF8AB4F8.toInt(),
        accentPressed = 0xFFAECBFA.toInt(),
        onAccent = 0xFF14213A.toInt(),
        textPrimary = 0xFFE7E8EA.toInt(),
        textSecondary = 0xFFC3C7CE.toInt(),
        hintText = 0xFF8E939B.toInt(),
        divider = 0x1AFFFFFF,
        candidateBarBackground = 0xFF141518.toInt(),
        candidateSelectedBackground = 0x338AB4F8,
        popupBackground = 0xFF3A3D44.toInt(),
        popupPressedBackground = 0xFF8AB4F8.toInt(),
        shadow = 0x55000000,
        isDark = true,
        keyTextSizeSp = 19f
    )
}
