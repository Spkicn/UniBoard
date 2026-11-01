/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPL）第 3 版
 * 或（你选择的）任何更新版本所规定的条款重新分发和/或修改它。
 *
 * 本程序按“希望有用”的方式分发，但不提供任何担保；
 * 甚至不包含适销性或特定用途适用性的默示担保。
 * 详见 GNU 通用公共许可证。
 *
 * 你应当已随本程序收到 GNU 通用公共许可证的副本；
 * 如果没有，见 <https://www.gnu.org/licenses/>。
 */

package org.YavSoft.UniBoard.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局设置存储。所有读写都经过这里，避免散落的 key 常量。
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- 外观 ----
    /** 0=跟随系统 1=浅色 2=深色 */
    var themeMode: Int
        get() = sp.getInt(K_THEME_MODE, THEME_SYSTEM)
        set(v) = sp.edit().putInt(K_THEME_MODE, v).apply()

    /** 键盘高度占屏幕高度比例 */
    var keyboardHeightPercent: Float
        get() = sp.getFloat(K_KB_HEIGHT, 0.34f)
        set(v) = sp.edit().putFloat(K_KB_HEIGHT, v).apply()

    /** 跟随系统（Material You）动态取色 */
    var dynamicColor: Boolean
        get() = sp.getBoolean(K_DYNAMIC_COLOR, true)
        set(v) = sp.edit().putBoolean(K_DYNAMIC_COLOR, v).apply()

    /** 双击空格插入句号 */
    var doubleSpacePeriod: Boolean
        get() = sp.getBoolean(K_DOUBLE_SPACE, true)
        set(v) = sp.edit().putBoolean(K_DOUBLE_SPACE, v).apply()

    /** 英文句首自动大写 */
    var autoCapitalize: Boolean
        get() = sp.getBoolean(K_AUTO_CAPS, true)
        set(v) = sp.edit().putBoolean(K_AUTO_CAPS, v).apply()

    /** 悬浮外观：键盘左右留白 + 圆角，做成浮层观感 */
    var floatKeyboard: Boolean
        get() = sp.getBoolean("float_keyboard", false)
        set(v) = sp.edit().putBoolean("float_keyboard", v).apply()

    /** 密码框保护开关 */
    var secureInputEnabled: Boolean
        get() = sp.getBoolean("secure_input", true)
        set(v) = sp.edit().putBoolean("secure_input", v).apply()

    /** 剪贴板历史只保留最近 N 天，0 表示不按天清理 */
    var clipboardKeepDays: Int
        get() = sp.getInt("clipboard_keep_days", 0)
        set(v) = sp.edit().putInt("clipboard_keep_days", v.coerceIn(0, 90)).apply()

    /** 语言键循环里是否加入 PC 键盘 */
    var pcKeyboardEnabled: Boolean
        get() = sp.getBoolean("pc_keyboard", false)
        set(v) = sp.edit().putBoolean("pc_keyboard", v).apply()

    /** 键盘背景不透明度（40–100，100 为不透明） */
    var keyboardAlpha: Int
        get() = sp.getInt(K_KEY_ALPHA, 100)
        set(v) = sp.edit().putInt(K_KEY_ALPHA, v.coerceIn(40, 100)).apply()

    /** 候选词字号（sp） */
    var candidateTextSize: Float
        get() = sp.getFloat("candidate_text_size", 17f)
        set(v) = sp.edit().putFloat("candidate_text_size", v.coerceIn(13f, 24f)).apply()

    /** 按键字号（sp） */
    var keyTextSize: Float
        get() = sp.getFloat("key_text_size", 19f)
        set(v) = sp.edit().putFloat("key_text_size", v.coerceIn(13f, 28f)).apply()

    /** 长按触发时间（毫秒） */
    var longPressMs: Int
        get() = sp.getInt("long_press_ms", 400)
        set(v) = sp.edit().putInt("long_press_ms", v.coerceIn(250, 900)).apply()

    /** 键盘行高（dp） */
    var keyRowHeightDp: Float
        get() = sp.getFloat(K_ROW_HEIGHT, 52f)
        set(v) = sp.edit().putFloat(K_ROW_HEIGHT, v.coerceIn(42f, 68f)).apply()

    /** 候选栏高度（dp） */
    var candidateBarHeightDp: Float
        get() = sp.getFloat(K_BAR_HEIGHT, 46f)
        set(v) = sp.edit().putFloat(K_BAR_HEIGHT, v.coerceIn(36f, 60f)).apply()

    // ---- 工具栏按钮 ----
    var toolLanguage: Boolean
        get() = sp.getBoolean("tool_language", true)
        set(v) = sp.edit().putBoolean("tool_language", v).apply()
    var toolUnicode: Boolean
        get() = sp.getBoolean("tool_unicode", true)
        set(v) = sp.edit().putBoolean("tool_unicode", v).apply()
    var toolEmoji: Boolean
        get() = sp.getBoolean("tool_emoji", true)
        set(v) = sp.edit().putBoolean("tool_emoji", v).apply()
    var toolClipboard: Boolean
        get() = sp.getBoolean("tool_clipboard", true)
        set(v) = sp.edit().putBoolean("tool_clipboard", v).apply()
    var toolAi: Boolean
        get() = sp.getBoolean("tool_ai", true)
        set(v) = sp.edit().putBoolean("tool_ai", v).apply()
    var toolEdit: Boolean
        get() = sp.getBoolean("tool_edit", true)
        set(v) = sp.edit().putBoolean("tool_edit", v).apply()
    var toolSettings: Boolean
        get() = sp.getBoolean("tool_settings", true)
        set(v) = sp.edit().putBoolean("tool_settings", v).apply()

    /** 是否显示按键按下时的弹性动画 */
    var keyAnimation: Boolean
        get() = sp.getBoolean(K_KEY_ANIM, true)
        set(v) = sp.edit().putBoolean(K_KEY_ANIM, v).apply()


    // ---- 反馈 ----
    var soundEnabled: Boolean
        get() = sp.getBoolean(K_SOUND, false)
        set(v) = sp.edit().putBoolean(K_SOUND, v).apply()

    /** 按键音音量 0–100 */
    var soundVolume: Int
        get() = sp.getInt("sound_volume", 60)
        set(v) = sp.edit().putInt("sound_volume", v.coerceIn(0, 100)).apply()

    /** 0=自动 1=系统音效 2=合成音 */
    var soundMode: Int
        get() = sp.getInt("sound_mode", 0)
        set(v) = sp.edit().putInt("sound_mode", v.coerceIn(0, 2)).apply()

    /** 内置字体应用范围：0=仅系统缺字形时 1=所有非 ASCII */
    var builtinFontScope: Int
        get() = sp.getInt("builtin_font_scope", 0)
        set(v) = sp.edit().putInt("builtin_font_scope", v.coerceIn(0, 1)).apply()

    var hapticEnabled: Boolean
        get() = sp.getBoolean(K_HAPTIC, true)
        set(v) = sp.edit().putBoolean(K_HAPTIC, v).apply()

    /** 触觉反馈强度（毫秒），0 表示使用系统默认轻触 */
    var hapticStrength: Int
        get() = sp.getInt(K_HAPTIC_STRENGTH, 0)
        set(v) = sp.edit().putInt(K_HAPTIC_STRENGTH, v).apply()

    // ---- 输入行为 ----
    var popupOnLongPress: Boolean
        get() = sp.getBoolean(K_POPUP, true)
        set(v) = sp.edit().putBoolean(K_POPUP, v).apply()

    var swipeToDelete: Boolean
        get() = sp.getBoolean(K_SWIPE_DELETE, true)
        set(v) = sp.edit().putBoolean(K_SWIPE_DELETE, v).apply()

    var autoCorrection: Boolean
        get() = sp.getBoolean(K_AUTOCORRECT, true)
        set(v) = sp.edit().putBoolean(K_AUTOCORRECT, v).apply()

    /** 拼音模糊音容错（z/zh、n/l、前后鼻音等） */
    var fuzzyPinyin: Boolean
        get() = sp.getBoolean(K_FUZZY, true)
        set(v) = sp.edit().putBoolean(K_FUZZY, v).apply()

    /** 提交后是否显示下一词联想（候选栏空闲时的「联想词」） */
    var nextWordPredict: Boolean
        get() = sp.getBoolean(K_NEXT_WORD, true)
        set(v) = sp.edit().putBoolean(K_NEXT_WORD, v).apply()

    /** 是否记录用户词频/上下文，用于个性化排序 */
    var learningEnabled: Boolean
        get() = sp.getBoolean(K_LEARNING, true)
        set(v) = sp.edit().putBoolean(K_LEARNING, v).apply()

    /** 拼音简拼（首字母）输入 */
    var initialsEnabled: Boolean
        get() = sp.getBoolean(K_INITIALS, true)
        set(v) = sp.edit().putBoolean(K_INITIALS, v).apply()

    /** 拼音候选数量 */
    var candidateCount: Int
        get() = sp.getInt(K_CAND_COUNT, 8)
        set(v) = sp.edit().putInt(K_CAND_COUNT, v.coerceIn(4, 12)).apply()



    /** 是否在键盘顶部显示数字行 */
    var numberRow: Boolean
        get() = sp.getBoolean(K_NUMBER_ROW, false)
        set(v) = sp.edit().putBoolean(K_NUMBER_ROW, v).apply()

    // ---- Unicode 码点输入 ----
    /** 输入码完整（4~6 位十六进制）时立即上屏，不再等空格/回车 */
    var unicodeAutoCommit: Boolean
        get() = sp.getBoolean(K_UNICODE_AUTO, false)
        set(v) = sp.edit().putBoolean(K_UNICODE_AUTO, v).apply()

    /** 近似编码候选的数量上限（0 表示不显示近似码） */
    var unicodeNearLimit: Int
        get() = sp.getInt(K_UNICODE_NEAR, 10)
        set(v) = sp.edit().putInt(K_UNICODE_NEAR, v.coerceIn(0, 24)).apply()

    /** 候选栏是否显示编码/字符名提示 */
    var showCodeHint: Boolean
        get() = sp.getBoolean(K_CODE_HINT, true)
        set(v) = sp.edit().putBoolean(K_CODE_HINT, v).apply()

    // ---- 面板 ----
    /** 键帽圆角（dp） */
    var keyRadiusDp: Float
        get() = sp.getFloat(K_KEY_RADIUS, 10.5f)
        set(v) = sp.edit().putFloat(K_KEY_RADIUS, v.coerceIn(4f, 18f)).apply()

    /** 自动收录：提交的词不在词典里时自动加进用户词典 */
    var autoCollectWords: Boolean
        get() = sp.getBoolean("auto_collect_words", true)
        set(v) = sp.edit().putBoolean("auto_collect_words", v).apply()

    /** 候选太少时让 AI 临时补几条（不写入词典） */
    var aiAutoGenerate: Boolean
        get() = sp.getBoolean("ai_auto_generate", false)
        set(v) = sp.edit().putBoolean("ai_auto_generate", v).apply()

    /** AI 生成词典时是否允许单字词 */
    var allowSingleCharWords: Boolean
        get() = sp.getBoolean("allow_single_char_words", false)
        set(v) = sp.edit().putBoolean("allow_single_char_words", v).apply()

    /** 剪贴板历史条数上限，0 表示不限制 */
    var clipboardLimit: Int
        get() = sp.getInt("clipboard_limit", 500)
        set(v) = sp.edit().putInt("clipboard_limit", v.coerceIn(0, 100000)).apply()

    // ---- 隐私 ----
    /** 隐私模式：禁用 AI、学习、剪贴板历史等可能泄露信息的链路 */
    var privacyMode: Boolean
        get() = sp.getBoolean("privacy_mode", false)
        set(v) = sp.edit().putBoolean("privacy_mode", v).apply()

    /** 隐私模式下是否允许「本地/局域网」模型继续工作（不出公网） */
    var allowLocalAiInPrivate: Boolean
        get() = sp.getBoolean("allow_local_ai_private", true)
        set(v) = sp.edit().putBoolean("allow_local_ai_private", v).apply()

    /** 隐私模式下剪贴板历史的保留分钟数，0 表示不自动清理 */
    var privacyPurgeMinutes: Int
        get() = sp.getInt("privacy_purge_minutes", 10)
        set(v) = sp.edit().putInt("privacy_purge_minutes", v.coerceIn(0, 1440)).apply()

    /** 面板（Unicode / Emoji / 剪贴板）占屏幕高度的比例，默认半屏 */
    var panelHeightPercent: Float
        get() = sp.getFloat(K_PANEL_HEIGHT, 0.5f)
        set(v) = sp.edit().putFloat(K_PANEL_HEIGHT, v.coerceIn(0.32f, 0.66f)).apply()

    // ---- 字体 ----
    /** 生僻字智能字体回退 */
    var fontFallback: Boolean
        get() = sp.getBoolean(K_FONT_FALLBACK, true)
        set(v) = sp.edit().putBoolean(K_FONT_FALLBACK, v).apply()

    /** 用户导入的外部字体绝对路径（可空） */
    var importedFontPath: String?
        get() = sp.getString(K_FONT_PATH, null)
        set(v) = sp.edit().putString(K_FONT_PATH, v).apply()


    // ---- 输入状态 ----
    /** 上次使用的汉语/字母模式：true=中文拼音 */
    /** 主题强调色，0 表示使用默认品牌色 */
    var accentColor: Int
        get() = sp.getInt("accent_color", 0)
        set(v) = sp.edit().putInt("accent_color", v).apply()

    var scriptEnabled: Boolean
        get() = sp.getBoolean("script_enabled", true)
        set(v) = sp.edit().putBoolean("script_enabled", v).apply()

    var aiEnabled: Boolean
        get() = sp.getBoolean("ai_enabled", false)
        set(v) = sp.edit().putBoolean("ai_enabled", v).apply()

    var aiApiKey: String
        get() = sp.getString("ai_key", "").orEmpty()
        set(v) = sp.edit().putString("ai_key", v).apply()

    var aiEndpoint: String
        get() = sp.getString("ai_endpoint", "https://api.deepseek.com/chat/completions").orEmpty()
        set(v) = sp.edit().putString("ai_endpoint", v).apply()

    var aiModel: String
        get() = sp.getString("ai_model", "deepseek-chat").orEmpty()
        set(v) = sp.edit().putString("ai_model", v).apply()

    /** AI 服务商预设：deepseek / openai / moonshot / zhipu / custom */
    var aiProvider: String
        get() = sp.getString("ai_provider", "deepseek").orEmpty()
        set(v) = sp.edit().putString("ai_provider", v).apply()

    /** AI 请求超时（秒） */
    var aiTimeoutSec: Int
        get() = sp.getInt("ai_timeout", 30)
        set(v) = sp.edit().putInt("ai_timeout", v.coerceIn(10, 120)).apply()

    /** AI 温度：越低越稳定 */
    var aiTemperature: Int
        get() = sp.getInt("ai_temperature_pct", 30)
        set(v) = sp.edit().putInt("ai_temperature_pct", v.coerceIn(0, 100)).apply()

    var aiPolishPrompt: String
        get() = sp.getString("ai_polish_prompt", "").orEmpty()
        set(v) = sp.edit().putString("ai_polish_prompt", v).apply()

    /** 启动默认模式：0=Unicode 直输，1=拼音，2=英文 */
    var defaultMode: Int
        get() = sp.getInt(K_DEFAULT_MODE, MODE_UNICODE)
        set(v) = sp.edit().putInt(K_DEFAULT_MODE, v).apply()

    /** 兼容旧版本：拼音模式开关。新逻辑用 [defaultMode]，这里跟随其变化 */
    var chineseMode: Boolean
        get() = sp.getBoolean(K_CHINESE_MODE, true)
        set(v) = sp.edit().putBoolean(K_CHINESE_MODE, v).apply()


    companion object {
        const val NAME = "uniboard_settings"

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        private const val K_THEME_MODE = "theme_mode"
        private const val K_KB_HEIGHT = "kb_height_percent"
        private const val K_KEY_ANIM = "key_animation"
        private const val K_DYNAMIC_COLOR = "dynamic_color"
        private const val K_ROW_HEIGHT = "key_row_height"
        private const val K_KEY_ALPHA = "keyboard_alpha"
        private const val K_BAR_HEIGHT = "candidate_bar_height"
        private const val K_DOUBLE_SPACE = "double_space_period"
        private const val K_AUTO_CAPS = "auto_caps"
        private const val K_KEY_BORDERS = "key_borders"
        private const val K_SOUND = "sound"
        private const val K_HAPTIC = "haptic"
        private const val K_HAPTIC_STRENGTH = "haptic_strength"
        private const val K_POPUP = "popup"
        private const val K_SWIPE_DELETE = "swipe_delete"
        private const val K_AUTOCORRECT = "autocorrect"
        private const val K_FUZZY = "fuzzy_pinyin"
        private const val K_CAND_COUNT = "candidate_count"
        private const val K_EN_PREDICT = "en_predict"
        private const val K_ZH_PREDICT = "zh_predict"
        private const val K_NUMBER_ROW = "number_row"
        private const val K_FONT_FALLBACK = "font_fallback"
        private const val K_FONT_PATH = "imported_font_path"
        private const val K_BUILTIN_FONT = "builtin_font_available"
        private const val K_CHINESE_MODE = "chinese_mode"
        private const val K_ONBOARDED = "onboarded"
        private const val K_DEFAULT_MODE = "default_mode"
        private const val K_UNICODE_AUTO = "unicode_auto_commit"
        private const val K_UNICODE_NEAR = "unicode_near_limit"
        private const val K_CODE_HINT = "show_code_hint"
        private const val K_PANEL_HEIGHT = "panel_height_percent"
        private const val K_KEY_RADIUS = "key_radius_dp"
        private const val K_NEXT_WORD = "next_word_predict"
        private const val K_LEARNING = "learning_enabled"
        private const val K_INITIALS = "initials_enabled"

        const val MODE_UNICODE = 0
        const val MODE_PINYIN = 1
        const val MODE_LATIN = 2
    }
}
