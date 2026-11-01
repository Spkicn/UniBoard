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

package org.YavSoft.UniBoard.core

import android.content.Context
import android.util.TypedValue
import kotlin.math.roundToInt

/** dp 转像素（Float） */
fun Context.dp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

/** dp 转像素（Int） */
fun Context.dpInt(value: Float): Int =
    (resources.displayMetrics.density * value).roundToInt()

/** sp 转像素 */
fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

/** 按亮度判断是否深色，用于图标/文字反色 */
fun isColorDark(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b) < 128
}
