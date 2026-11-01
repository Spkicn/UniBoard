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

package org.YavSoft.UniBoard.predict

/** 一次预测产出的候选 */
data class Candidate(
    /** 实际提交的文本 */
    val text: String,
    /** 候选栏显示的文本（默认等于 text） */
    val label: String = text,
    /** 附注（如拼音、来源说明） */
    val hint: String? = null,
    /** 是否为纠错结果 */
    val corrected: Boolean = false,
    /** 是否来自用户词库 */
    val userWord: Boolean = false,
    /** 排序权重（内部使用） */
    val score: Double = 0.0
)

/** 一次预测的完整结果 */
data class PredictionResult(
    val candidates: List<Candidate>,
    /** 发生纠错时，原始输入串 */
    val correctedFrom: String? = null
) {
    val isEmpty: Boolean get() = candidates.isEmpty()
    companion object { val EMPTY = PredictionResult(emptyList()) }
}
