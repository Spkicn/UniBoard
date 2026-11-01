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

/** 键帽的视觉分类，决定底色与文字颜色 */
enum class KeyStyle {
    /** 普通字符键：近白底 */
    CHAR,
    /** 功能键：删除、切换、符号页等，底色更深 */
    FUNCTION,
    /** 主要动作键（回车）：使用强调色 */
    ACTION,
    /** 空格：宽键，可显示当前语言 */
    SPACE
}

/** 按键触发的语义动作 */
sealed interface KeyAction {
    /** 插入文本。[popups] 为长按可选字符（重音、变体等） */
    data class Insert(val text: String, val popups: List<String> = emptyList()) : KeyAction

    data object Backspace : KeyAction

    data object Shift : KeyAction

    /** 发送一个物理键盘按键（PC 键盘用） */
    data class SendKey(val keyCode: Int) : KeyAction

    /** 长按 Shift：锁定大写，直到再点一次 */
    data object ShiftLock : KeyAction

    data object Enter : KeyAction

    data object Space : KeyAction

    /** 切换到指定布局 */
    data class Goto(val id: String) : KeyAction

    /** 调出系统输入法选择器 */
    data object SwitchIme : KeyAction

    data object Settings : KeyAction

    /** 打开/关闭 Unicode 码点输入面板 */
    data object ToggleUnicode : KeyAction

    /** 打开表情与符号面板 */
    data object Emoji : KeyAction

    /** 中文/英文输入模式切换 */
    data object Language : KeyAction

    /** 一次提交组合区文本（用于候选） */
    data object CommitComposing : KeyAction

    /** 切换到 Unicode 直输键盘 */
    data object ModeUnicode : KeyAction

    /** 切换到中文拼音键盘 */
    data object ModePinyin : KeyAction

    /** 切换到英文键盘 */
    data object ModeLatin : KeyAction

    /** 打开 Emoji 面板 */
    data object EmojiPanel : KeyAction

    /** AI 润色当前输入 */
    data object AiPolish : KeyAction

    /** 把输入框内容交给 AI 联想下一句 */
    data object AiSuggest : KeyAction
}

/** 单个按键定义 */
data class Key(
    val action: KeyAction,
    val label: String = "",
    val iconRes: Int = 0,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.CHAR,
    /** 键帽右下角的小提示文字 */
    val hint: String? = null,
    /** 长按是否连续触发（如退格） */
    val repeatable: Boolean = false,
    /** 是否吃右侧（用于 RTL 布局，暂留） */
    val trailingIcon: Boolean = false,
    /**
     * 长按触发的动作（可选）。
     * 用于「单击插入文本、长按打开面板」这类双行为键，例如 Unicode 盘上的 U+ / 0x。
     */
    val longPressAction: KeyAction? = null
)

/** 一行按键 */
data class KeyRow(
    val keys: List<Key>,
    /** 左右留白，按整行宽度的比例计算（0~1） */
    val leftInset: Float = 0f,
    val rightInset: Float = 0f
) {
    val totalWeight: Float get() = keys.sumOf { it.weight.toDouble() }.toFloat()
}

/** 一套键盘布局 */
data class KeyboardLayout(
    val id: String,
    val rows: List<KeyRow>,
    /** 该布局下部保留的高度（行数），用于不同布局间高度一致 */
    val rowCount: Float = rows.size.toFloat()
)
