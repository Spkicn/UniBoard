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

import org.YavSoft.UniBoard.R

/**
 * 内置键盘布局集合。
 *
 * 一共四类：
 * - [unicodePad]：默认的 Unicode 直输盘，数字/符号/U+ 入口，按键直接上屏
 * - [buildLatin]：拼音与英文共用的 QWERTY 全键盘，可挂数字行
 * - [symbols] / [symbolsMore]：两页标点与符号
 * - [numpad]：纯数字输入框使用的九宫格
 *
 * 键帽图形、配色与动画全部由项目自绘，不依赖第三方键盘资源。
 */
object Layouts {

    const val LATIN = "latin"
    const val LATIN_UPPER = "latin_upper"
    const val SYMBOLS = "symbols"
    const val SYMBOLS_MORE = "symbols_more"
    const val NUMPAD = "numpad"
    const val UNICODE_PAD = "unicode_pad"
    const val PC = "pc"

    /** 长按备选字符表（拉丁字母的变体与重音形式） */
    private val ACCENTS: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā", "ă", "ą"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ĕ", "ė", "ę", "ě"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "ĭ", "į"),
        "o" to listOf("ò", "ó", "ô", "ö", "œ", "õ", "ø", "ō", "ŏ"),
        "u" to listOf("ù", "ú", "û", "ü", "ū", "ŭ", "ů", "ű", "ų"),
        "c" to listOf("ç", "ć", "č", "ĉ", "ċ"),
        "n" to listOf("ñ", "ń", "ň", "ņ"),
        "s" to listOf("ß", "ś", "š", "ş", "ș"),
        "y" to listOf("ý", "ÿ", "ŷ"),
        "z" to listOf("ź", "ž", "ż"),
        "l" to listOf("ł", "ļ", "ľ"),
        "g" to listOf("ğ", "ģ", "ĝ"),
        "r" to listOf("ř", "ŕ"),
        "t" to listOf("ţ", "ť"),
        "d" to listOf("ď", "đ"),
        "k" to listOf("ķ"),
        "h" to listOf("ĥ"),
        "w" to listOf("ŵ"),
        "b" to listOf("þ")
    )

    private fun c(text: String, weight: Float = 1f): Key =
        Key(action = KeyAction.Insert(text, ACCENTS[text.lowercase()] ?: emptyList()),
            label = text, weight = weight)

    private fun f(text: String, style: KeyStyle = KeyStyle.FUNCTION, weight: Float = 1f): Key =
        Key(action = KeyAction.Insert(text), label = text, weight = weight, style = style)

    private fun icon(action: KeyAction, res: Int, style: KeyStyle = KeyStyle.FUNCTION, weight: Float = 1f): Key =
        Key(action = action, iconRes = res, weight = weight, style = style)

    /** 主字母布局 */
    fun buildLatin(upper: Boolean, chinese: Boolean, numberRow: Boolean): KeyboardLayout {
        val rows = mutableListOf<KeyRow>()

        if (numberRow) {
            rows += KeyRow(
                listOf(
                    f("1"), f("2"), f("3"), f("4"), f("5"),
                    f("6"), f("7"), f("8"), f("9"), f("0")
                )
            )
        }

        val letter: (String) -> String = { if (upper) it.uppercase() else it }

        rows += KeyRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { c(letter(it)) })
        rows += KeyRow(
            keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { c(letter(it)) },
            leftInset = 0.03f,
            rightInset = 0.03f
        )
        rows += KeyRow(
            keys = listOf(
                icon(KeyAction.Shift, R.drawable.ic_shift, KeyStyle.FUNCTION, 1.4f)
                    .copy(longPressAction = KeyAction.ShiftLock)
            ) + listOf("z", "x", "c", "v", "b", "n", "m").map { c(letter(it)) } +
                listOf(
                    icon(KeyAction.Backspace, R.drawable.ic_backspace, KeyStyle.FUNCTION, 1.4f)
                        .copy(repeatable = true)
                )
        )

        val comma = if (chinese) "，" else ","
        val period = if (chinese) "。" else "."
        rows += KeyRow(
            listOf(
                Key(KeyAction.Goto(SYMBOLS), label = "?123", weight = 1.4f, style = KeyStyle.FUNCTION),
                icon(KeyAction.SwitchIme, R.drawable.ic_globe, KeyStyle.FUNCTION, 1.0f),
                f(comma, KeyStyle.FUNCTION, 1.0f),
                Key(KeyAction.Space, label = "", weight = 3.1f, style = KeyStyle.SPACE),
                f(period, KeyStyle.FUNCTION, 1.0f),
                icon(KeyAction.Enter, R.drawable.ic_enter, KeyStyle.ACTION, 1.4f)
            )
        )

        return KeyboardLayout(id = if (upper) LATIN_UPPER else LATIN, rows = rows)
    }

    /**
     * PC 键盘：一排功能键 + 三排字母 + 一排方向/编辑键，横屏也能用。
     * 所有按键发送 KeyEvent，不经过编码组合。
     */
    fun pcLayout(chinese: Boolean): KeyboardLayout {
        fun k(label: String, code: Int, w: Float = 1f, style: KeyStyle = KeyStyle.CHAR) =
            Key(KeyAction.SendKey(code), label = label, weight = w, style = style)

        fun letter(ch: String) = k(ch, android.view.KeyEvent.KEYCODE_A + (ch[0] - 'A'))

        val row1 = KeyRow(
            listOf(
                k("Esc", android.view.KeyEvent.KEYCODE_ESCAPE, 1.3f, KeyStyle.FUNCTION),
                k("Tab", android.view.KeyEvent.KEYCODE_TAB, 1.3f, KeyStyle.FUNCTION),
                k("Ctrl", android.view.KeyEvent.KEYCODE_CTRL_LEFT, 1.3f, KeyStyle.FUNCTION),
                k("Alt", android.view.KeyEvent.KEYCODE_ALT_LEFT, 1.3f, KeyStyle.FUNCTION),
                k("Shift", android.view.KeyEvent.KEYCODE_SHIFT_LEFT, 1.5f, KeyStyle.FUNCTION),
                Key(KeyAction.Space, label = "", weight = 3.4f, style = KeyStyle.SPACE)
            )
        )
        val row2 = KeyRow("QWERTYUIOP".map { letter(it.toString()) })
        val row3 = KeyRow(
            keys = "ASDFGHJKL".map { letter(it.toString()) },
            leftInset = 0.02f,
            rightInset = 0.02f
        )
        val row4 = KeyRow(
            keys = listOf(
                k("⇧", android.view.KeyEvent.KEYCODE_SHIFT_LEFT, 1.3f, KeyStyle.FUNCTION),
                letter("Z"), letter("X"), letter("C"), letter("V"), letter("B"), letter("N"), letter("M"),
                k("⌫", android.view.KeyEvent.KEYCODE_DEL, 1.3f, KeyStyle.FUNCTION)
            )
        )
        val row5 = KeyRow(
            listOf(
                Key(KeyAction.Language, label = if (chinese) "中" else "EN",
                    weight = 1.2f, style = KeyStyle.FUNCTION),
                k("←", android.view.KeyEvent.KEYCODE_DPAD_LEFT, 1.1f, KeyStyle.FUNCTION),
                k("↑", android.view.KeyEvent.KEYCODE_DPAD_UP, 1.1f, KeyStyle.FUNCTION),
                k("↓", android.view.KeyEvent.KEYCODE_DPAD_DOWN, 1.1f, KeyStyle.FUNCTION),
                k("→", android.view.KeyEvent.KEYCODE_DPAD_RIGHT, 1.1f, KeyStyle.FUNCTION),
                k("F1", android.view.KeyEvent.KEYCODE_F1, 1f, KeyStyle.FUNCTION),
                k("F2", android.view.KeyEvent.KEYCODE_F2, 1f, KeyStyle.FUNCTION),
                k("⌫", android.view.KeyEvent.KEYCODE_DEL, 1.2f, KeyStyle.FUNCTION),
                Key(KeyAction.Enter, iconRes = R.drawable.ic_enter, weight = 1.4f, style = KeyStyle.ACTION)
            )
        )
        return KeyboardLayout(PC, listOf(row1, row2, row3, row4, row5))
    }

    /** 常用符号页 */
    fun symbols(chinese: Boolean): KeyboardLayout {
        val comma = if (chinese) "，" else ","
        val period = if (chinese) "。" else "."
        val rows = listOf(
            KeyRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { f(it) }),
            KeyRow(listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { f(it) }),
            KeyRow(
                listOf(
                    f("*"), f("\""), f("'"), f(":"), f(";"), f("!"), f("?"),
                    icon(KeyAction.Backspace, R.drawable.ic_backspace, KeyStyle.FUNCTION, 1.4f)
                        .copy(repeatable = true)
                )
            ),
            KeyRow(
                listOf(
                    Key(KeyAction.Goto(LATIN), label = "ABC", weight = 1.4f, style = KeyStyle.FUNCTION),
                    Key(KeyAction.Goto(SYMBOLS_MORE), label = "=\\<", weight = 1.4f, style = KeyStyle.FUNCTION),
                    f(comma, KeyStyle.FUNCTION, 1.0f),
                    Key(KeyAction.Space, weight = 2.6f, style = KeyStyle.SPACE),
                    f(period, KeyStyle.FUNCTION, 1.0f),
                    icon(KeyAction.Enter, R.drawable.ic_enter, KeyStyle.ACTION, 1.4f)
                )
            )
        )
        return KeyboardLayout(SYMBOLS, rows)
    }

    /** 扩展符号页：数学、货币、括号、箭头与标点 */
    fun symbolsMore(chinese: Boolean): KeyboardLayout {
        val rows = listOf(
            KeyRow(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "§").map { f(it) }),
            KeyRow(listOf("€", "£", "¥", "¢", "°", "=", "≠", "≈", "∞", "±").map { f(it) }),
            KeyRow(
                listOf(
                    f("\u00AB"), f("\u00BB"), f("\u2018"), f("\u2019"), f("\u201C"), f("\u201D"),
                    f("\u2026"), f("\u2014"),
                    icon(KeyAction.Backspace, R.drawable.ic_backspace, KeyStyle.FUNCTION, 1.4f)
                        .copy(repeatable = true)
                )
            ),
            KeyRow(
                listOf(
                    Key(KeyAction.Goto(LATIN), label = "ABC", weight = 1.4f, style = KeyStyle.FUNCTION),
                    Key(KeyAction.Goto(SYMBOLS), label = "?123", weight = 1.4f, style = KeyStyle.FUNCTION),
                    Key(KeyAction.Space, weight = 2.6f, style = KeyStyle.SPACE),
                    f("←", KeyStyle.FUNCTION, 1.0f),
                    f("→", KeyStyle.FUNCTION, 1.0f),
                    icon(KeyAction.Enter, R.drawable.ic_enter, KeyStyle.ACTION, 1.4f)
                )
            )
        )
        return KeyboardLayout(SYMBOLS_MORE, rows)
    }

    /** 数字键盘（数字输入的输入框会自动使用） */
    fun numpad(): KeyboardLayout {
        val rows = listOf(
            KeyRow(listOf("1", "2", "3").map { f(it, KeyStyle.CHAR) }),
            KeyRow(listOf("4", "5", "6").map { f(it, KeyStyle.CHAR) }),
            KeyRow(listOf("7", "8", "9").map { f(it, KeyStyle.CHAR) }),
            KeyRow(
                listOf(
                    f(".", KeyStyle.FUNCTION),
                    f("0"),
                    icon(KeyAction.Backspace, R.drawable.ic_backspace, KeyStyle.FUNCTION)
                        .copy(repeatable = true)
                )
            ),
            KeyRow(
                listOf(
                    Key(KeyAction.Goto(LATIN), label = "ABC", style = KeyStyle.FUNCTION, weight = 2f),
                    icon(KeyAction.Enter, R.drawable.ic_enter, KeyStyle.ACTION, 2f)
                )
            )
        )
        return KeyboardLayout(NUMPAD, rows)
    }

    /**
     * 默认键盘：Unicode 直输盘。
     *
     * 布局按「敲码点」重新组织：
     * - 第一行：0–9
     * - 第二行：U+ 键 + A–F 十六进制字母 + 0x 键（左右分置）
     * - 第三行：基础标点，中英文逗号、句号各占一个键
     * - 第四行：语言、退格、空格、回车
     *
     * U+ / 0x 键单击直接把前缀送进输入框，长按才打开 Unicode 面板，
     * 于是「U+4E00」「0x4E00」这类码点可以一路按键盘敲出来。
     */
    fun unicodePad(modeLabel: String, chinese: Boolean): KeyboardLayout {
        fun k(ch: String, w: Float = 1f, style: KeyStyle = KeyStyle.CHAR) =
            Key(KeyAction.Insert(ch), label = ch, weight = w, style = style)

        // 第一行：十进制数字 0–9
        val row1 = KeyRow("0123456789".map { k(it.toString()) })

        // 第二行：U+ / A–F / 0x
        val row2 = KeyRow(
            listOf(
                Key(
                    action = KeyAction.Insert("U+"),
                    label = "U+",
                    weight = 1.3f,
                    style = KeyStyle.ACTION,
                    longPressAction = KeyAction.ToggleUnicode
                )
            ) + listOf("A", "B", "C", "D", "E", "F").map { k(it) } + listOf(
                Key(
                    action = KeyAction.Insert("0x"),
                    label = "0x",
                    weight = 1.3f,
                    style = KeyStyle.ACTION,
                    longPressAction = KeyAction.ToggleUnicode
                )
            )
        )

        // 第三行：基础标点。逗号与句号两键共用中英文写法，长按可挑另一种
        val comma = if (chinese) "，" else ","
        val period = if (chinese) "。" else "."
        val row3 = KeyRow(
            listOf(
                Key(KeyAction.Insert(comma, listOf("，", ",", "、")), label = comma),
                Key(KeyAction.Insert(period, listOf("。", ".", "…")), label = period),
                k("?"), k("!"), k(":"), k(";"), k("'"), k("\""), k("("), k(")")
            )
        )

        // 第四行：功能键
        // 与其它键盘一致：语言 / 符号 / 空格 / 退格 / 回车
        val row4 = KeyRow(
            listOf(
                Key(KeyAction.Language, label = modeLabel, weight = 1.1f, style = KeyStyle.FUNCTION),
                Key(KeyAction.Goto(SYMBOLS), label = "?123", weight = 1.3f, style = KeyStyle.FUNCTION),
                Key(KeyAction.Space, weight = 2.8f, style = KeyStyle.SPACE),
                Key(
                    KeyAction.Backspace, iconRes = R.drawable.ic_backspace, weight = 1.3f,
                    style = KeyStyle.FUNCTION, repeatable = true
                ),
                Key(KeyAction.Enter, iconRes = R.drawable.ic_enter, weight = 1.4f, style = KeyStyle.ACTION)
            )
        )
        return KeyboardLayout(UNICODE_PAD, listOf(row1, row2, row3, row4))
    }
}
