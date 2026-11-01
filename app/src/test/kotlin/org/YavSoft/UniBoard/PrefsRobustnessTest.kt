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

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.ui.SettingsActivity

/**
 * 设置项的健壮性测试。
 *
 * `SharedPreferences.getXxx(key, def)` 在「存的类型与读的类型不一致」时
 * **不返回默认值，而是抛 ClassCastException** —— 而 `Prefs(this).dynamicColor`
 * 正是 `UniBoardApp.onCreate()` 的第一行，抛在这里等于界面一行都没建，
 * 进程直接死，表现就是「一打开就闪退」。
 *
 * 这种残渣很常见：旧版本把某个开关从 String 改成 Boolean、换机时
 * `allowBackup=true` 把老设置还原回来、或者用户从旧版升级上来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 30, 34])
class PrefsRobustnessTest {

    private lateinit var sp: SharedPreferences

    @Before
    fun setUp() {
        val ctx = RuntimeEnvironment.getApplication() as Context
        sp = ctx.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        sp.edit().clear().commit()
    }

    @After
    fun tearDown() {
        sp.edit().clear().commit()
    }

    /** 先证明框架本身确实会抛：这是被修掉的那种崩溃的机制，不是猜测 */
    @Test
    fun rawSharedPreferencesThrowsOnTypeMismatch() {
        sp.edit().putString("dynamic_color", "true").commit()
        var thrown: Throwable? = null
        try {
            sp.getBoolean("dynamic_color", true)
        } catch (t: Throwable) {
            thrown = t
        }
        assertNotNull("SharedPreferences 对类型不符应当抛异常", thrown)
    }

    /** 走 Prefs 就不许抛，而且要老老实实回落到默认值 */
    @Test
    fun prefsFallsBackToDefault() {
        sp.edit().putString("dynamic_color", "true").commit()
        sp.edit().putInt("kb_height_percent", 12345).commit()
        sp.edit().putString("keyboard_alpha", "opaque").commit()

        val ctx = RuntimeEnvironment.getApplication() as Context
        val prefs = Prefs(ctx)

        assertTrue("读坏值时必须回落到默认值 true", prefs.dynamicColor)
        assertFalse("不能把 12345 当成键盘高度", prefs.keyboardHeightPercent == 12345f)
    }

    /** 读到坏值后应当把它删掉，下一次读就是干净的 */
    @Test
    fun badKeyIsDroppedAfterRead() {
        sp.edit().putString("dynamic_color", "true").commit()
        val ctx = RuntimeEnvironment.getApplication() as Context
        Prefs(ctx).dynamicColor
        assertNull("坏掉的 key 应当被删掉", sp.getString("dynamic_color", null))
    }

    /** 所有 key 都投毒之后，首页与设置页仍然要能起来 */
    @Test
    fun activitiesSurvivePoisonedPrefs() {
        val poison = arrayOf(
            "dynamic_color", "theme_mode", "kb_height_percent", "keyboard_alpha",
            "candidate_bar_height", "key_row_height", "key_radius_dp", "panel_height_percent",
            "candidate_count", "unicode_near_limit", "long_press_ms", "ai_key",
            "ai_endpoint", "ai_model", "ai_provider", "imported_font_path",
            "clipboard_limit", "privacy_purge_minutes", "accent_color", "default_mode"
        )
        val editor = sp.edit()
        poison.forEachIndexed { idx, key ->
            when (idx % 4) {
                0 -> editor.putInt(key, 12345)
                1 -> editor.putFloat(key, 1.5f)
                2 -> editor.putString(key, "not-the-right-type")
                else -> editor.putBoolean(key, true)
            }
        }
        editor.commit()

        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(main)
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertNotNull(settings)
    }
}
