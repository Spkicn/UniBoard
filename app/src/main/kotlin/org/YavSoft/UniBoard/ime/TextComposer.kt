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

package org.YavSoft.UniBoard.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * 输入缓冲区与 InputConnection 的桥接。
 *
 * 组合中的串通过 setComposingText 写入编辑器（带下划线预览），
 * 提交时用 commitText 一次性替换，符合 Android 输入法规范。
 */
class TextComposer(private val connectionProvider: () -> InputConnection?) {

    private val buffer = StringBuilder()

    val composing: String get() = buffer.toString()
    val isComposing: Boolean get() = buffer.length > 0

    fun append(ch: Char) {
        buffer.append(ch)
        sync()
    }

    fun append(text: String) {
        buffer.append(text)
        sync()
    }

    fun dropLast(): Boolean {
        if (buffer.length == 0) return false
        buffer.deleteCharAt(buffer.length - 1)
        sync()
        return true
    }

    fun reset() {
        buffer.setLength(0)
    }

    /** 提交文本并替换当前组合串 */
    fun commit(text: String) {
        val ic = connectionProvider() ?: return
        if (text.isEmpty()) return
        ic.commitText(text, 1)
        buffer.setLength(0)
    }

    /** 仅结束组合状态，不插入内容 */
    fun finishComposing() {
        if (buffer.length == 0) return
        val ic = connectionProvider() ?: return
        ic.finishComposingText()
        buffer.setLength(0)
    }

    /** 退格：优先消耗组合串，否则删除编辑器内字符 */
    fun backspace(): Boolean {
        if (buffer.length > 0) {
            buffer.deleteCharAt(buffer.length - 1)
            sync()
            return true
        }
        val ic = connectionProvider() ?: return false
        val before = ic.getTextBeforeCursor(1, 0)
        if (!before.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
        return false
    }

    /** 左右移动光标（组合中时不移动） */
    fun moveCursor(offset: Int): Boolean {
        if (buffer.length > 0) return false
        val ic = connectionProvider() ?: return false
        val code = if (offset < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return true
    }

    private fun sync() {
        val ic = connectionProvider() ?: return
        if (buffer.length == 0) {
            ic.setComposingText("", 1)
        } else {
            ic.setComposingText(buffer.toString(), 1)
        }
    }
}
