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

/** 输入法当前的编码模式 */
enum class InputMode {
    /** 中文全拼 */
    PINYIN,
    /** 拉丁文/英文直接输入 */
    LATIN,
    /** 纯数字键盘 */
    UNICODE,
    NUMBER
}
