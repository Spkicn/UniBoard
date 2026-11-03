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

package org.YavSoft.UniBoard

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.YavSoft.UniBoard.ime.UniBoardImeService
import org.YavSoft.UniBoard.ui.AboutActivity
import org.YavSoft.UniBoard.ui.AiLexiconActivity
import org.YavSoft.UniBoard.ui.LexiconManagerActivity
import org.YavSoft.UniBoard.ui.LicenseActivity
import org.YavSoft.UniBoard.ui.SettingsActivity

/**
 * 启动路径冒烟测试：把 Application / 各 Activity 真的创建一遍。
 *
 * 这套用例能抓住的是「资源缺失、主题链断、代码建 View 出错」这一类硬崩；
 * **抓不住** Android 14 那种「框架按调用方 targetSdk 决定放不放行」的
 * SecurityException —— Robolectric 把 Settings 系接口 shadow 成内存表，
 * 框架里的 targetSdk 校验根本不会执行。那一类由
 * `tools/check_code.py` 的静态规则兜（见该文件第 4 条规则）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 30, 34])
class LaunchSmokeTest {

    @Test
    fun applicationClassMatchesManifest() {
        val app: Application = RuntimeEnvironment.getApplication()
        assertNotNull(app)
        assertEquals("org.YavSoft.UniBoard.UniBoardApp", app.javaClass.name)
    }

    @Test
    fun mainActivityStarts() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertFalse("MainActivity 启动后不应该已经在结束", activity.isFinishing)
        assertNotNull(activity.window.decorView)
    }

    @Test
    fun settingsActivityStarts() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertNotNull(activity)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun secondaryActivitiesStart() {
        val classes = listOf(
            AboutActivity::class.java,
            LicenseActivity::class.java,
            LexiconManagerActivity::class.java,
            AiLexiconActivity::class.java
        )
        for (c in classes) {
            val activity = Robolectric.buildActivity(c).setup().get()
            assertNotNull(c.name + " 没有创建成功", activity)
        }
    }

    @Test
    fun imeServiceStarts() {
        val service = Robolectric.buildService(UniBoardImeService::class.java).create().get()
        assertNotNull(service)
    }
}
