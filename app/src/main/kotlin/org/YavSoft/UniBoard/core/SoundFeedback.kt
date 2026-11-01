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
import android.media.ToneGenerator
import android.provider.Settings
import android.media.AudioManager

/**
 * 按键音反馈。
 *
 * 使用系统按键音效，不打包音频资源，也不申请任何权限。
 * 用户可以通过系统设置中的“触摸声音”统一控制音效偏好。
 */
object SoundFeedback {

    @Volatile
    private var audioManager: AudioManager? = null

    @Volatile
    private var tone: ToneGenerator? = null

    private fun am(context: Context): AudioManager? = audioManager ?: run {
        val m = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = m
        m
    }

    /** 系统「触摸音效」开关；关掉时 AudioManager.playSoundEffect 是完全静音的 */
    private fun systemSoundOn(context: Context): Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1
    }.getOrDefault(true)

    private fun tone(context: Context): ToneGenerator? = tone ?: run {
        val t = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        }.getOrNull()
        tone = t
        t
    }

    /**
     * 按键音。
     *
     * @param volume 0–100
     * @param mode 0=自动（系统音效开则用系统音，否则合成音） 1=只用系统音效 2=只用合成音
     */
    fun playKey(context: Context, volume: Int = 60, mode: Int = 0) {
        val v = (volume.coerceIn(0, 100)) / 100f
        if (v <= 0f) return
        val useSystem = when (mode) {
            1 -> true
            2 -> false
            else -> systemSoundOn(context)
        }
        if (useSystem) {
            val ok = runCatching {
                am(context)?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, v)
            }.isSuccess
            if (ok && mode == 1) return
            if (ok && systemSoundOn(context)) return
        }
        // 兜底：直接合成一声短提示音，不依赖系统触摸音效开关
        runCatching {
            tone(context)?.startTone(ToneGenerator.TONE_PROP_BEEP, 28)
        }
    }

    fun playDelete(context: Context, volume: Int = 60, mode: Int = 0) {
        val v = (volume.coerceIn(0, 100)) / 100f
        if (v <= 0f) return
        val useSystem = when (mode) {
            1 -> true
            2 -> false
            else -> systemSoundOn(context)
        }
        if (useSystem) {
            val ok = runCatching {
                am(context)?.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, v)
            }.isSuccess
            if (ok && systemSoundOn(context)) return
        }
        runCatching { tone(context)?.startTone(ToneGenerator.TONE_PROP_BEEP2, 25) }
    }
}
