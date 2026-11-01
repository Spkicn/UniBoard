/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证第 3 版
 * 或（你选择的）任何更新版本所规定的条款，重新分发和/或修改它。
 */

package org.YavSoft.UniBoard

import android.app.Application
import com.google.android.material.color.DynamicColors
import org.YavSoft.UniBoard.core.Prefs

/** 应用入口：按设置决定是否套用 Material You 动态取色 */
class UniBoardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Prefs(this).dynamicColor) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }
}
