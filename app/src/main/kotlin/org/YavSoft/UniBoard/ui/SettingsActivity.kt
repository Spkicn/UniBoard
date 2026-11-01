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

package org.YavSoft.UniBoard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import org.YavSoft.UniBoard.R
import org.YavSoft.UniBoard.ai.AiClient
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.core.dpInt
import org.YavSoft.UniBoard.script.ScriptEngine
import org.YavSoft.UniBoard.ui.theme.KeyboardThemes

/** 设置子页面：按分区展示，避免主页过长 */
class SettingsActivity : SettingsBase() {

    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_APPEARANCE = "appearance"
        const val SECTION_KEYBOARD = "keyboard"
        const val SECTION_INPUT = "input"
        const val SECTION_AI = "ai"
        const val SECTION_SCRIPT = "script"
        const val SECTION_LEXICON = "lexicon"
        const val SECTION_PRIVACY = "privacy"

        fun start(context: Context, section: String) {
            context.startActivity(
                Intent(context, SettingsActivity::class.java)
                    .putExtra(EXTRA_SECTION, section)
            )
        }
    }

    private var sectionId: String = SECTION_APPEARANCE
    private var script: ScriptEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        sectionId = intent?.getStringExtra(EXTRA_SECTION) ?: SECTION_APPEARANCE
        super.onCreate(savedInstanceState)
    }

    override fun build() {
        when (sectionId) {
            SECTION_KEYBOARD -> buildKeyboard()
            SECTION_INPUT -> buildInput()
            SECTION_AI -> buildAi()
            SECTION_SCRIPT -> buildScript()
            SECTION_LEXICON -> buildLexicon()
            SECTION_PRIVACY -> buildPrivacy()
            else -> buildAppearance()
        }
    }
    private fun buildAppearance() {
        title("外观与主题")
        subtitle("主题色应用于回车键、候选高亮与面板强调元素。")
        section("动态取色")
        val c0 = card()
        toggle(c0, "跟随系统取色", "Android 12 及以上可用，跟随系统动态取色", prefs.dynamicColor) {
            prefs.dynamicColor = it
            KeyboardThemes.invalidate()
            recreate()
        }
        section("主题模式")
        val c1 = card()
        themeModeRow(c1)
        section("主题色")
        val c2 = card()
        colorPalette(c2, prefs.accentColor) {
            prefs.accentColor = it
            KeyboardThemes.invalidate()
            recreate()
        }
        action(c2, "恢复默认品牌色") {
            prefs.accentColor = 0
            KeyboardThemes.invalidate()
            recreate()
        }
        section("反馈")
        val c3 = card()
        toggle(c3, "按键动画", "按下时缩放并回弹", prefs.keyAnimation) {
            prefs.keyAnimation = it
        }
        toggle(c3, "按键音", "按下时播放提示音", prefs.soundEnabled) {
            prefs.soundEnabled = it
        }
        slider(c3, "按键音音量", 0f, 100f, 5f, prefs.soundVolume.toFloat()) {
            prefs.soundVolume = it.toInt()
        }
        choice(c3, listOf("自动（跟随系统触摸音效）", "仅系统音效", "仅合成音"), prefs.soundMode) {
            prefs.soundMode = it
        }
        toggle(c3, "震动反馈", null, prefs.hapticEnabled) { prefs.hapticEnabled = it }
        slider(c3, "震动强度", 8f, 30f, 1f, prefs.hapticStrength.toFloat()) {
            prefs.hapticStrength = it.toInt()
        }

        section("字体")
        val c4 = card()
        val fontResolver = org.YavSoft.UniBoard.font.FontResolver.get(this)
        val fontStatus = TextView(this).apply {
            text = fontResolver.builtinStatus()
            textSize = 12f
            setTextColor(0xFF757575.toInt())
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(4f))
        }
        c4.addView(fontStatus)
        toggle(c4, "生僻字回退", "系统缺少字形时改用内置 GNU Unifont", prefs.fontFallback) {
            prefs.fontFallback = it
            fontResolver.invalidate()
        }
        choice(
            c4,
            listOf("仅在系统缺少字形时使用内置字体", "所有非 ASCII 字符均使用内置字体"),
            prefs.builtinFontScope
        ) {
            prefs.builtinFontScope = it
            fontResolver.invalidate()
            fontStatus.text = fontResolver.builtinStatus()
            toast("设置已更新，下次输入时按新范围选择字体")
        }
        action(c4, "重新加载内置字体") {
            fontStatus.text = fontResolver.reloadBuiltin()
            toast(fontStatus.text.toString())
        }
        action(c4, "导入外部字体") {
            runCatching { fontPicker.launch(arrayOf("font/*", "*/*")) }
                .onFailure { toast("未找到可用的文件选择器") }
        }
        action(c4, "恢复默认字体") {
            prefs.importedFontPath = null
            fontResolver.invalidate()
            fontStatus.text = fontResolver.reloadBuiltin()
            toast("已恢复默认字体")
        }
    }

    private fun themeModeRow(parent: LinearLayout) {
        val names = listOf("跟随系统", "浅色", "深色")
        choice(parent, names, prefs.themeMode.coerceIn(0, 2)) { index ->
            prefs.themeMode = index
            KeyboardThemes.invalidate()
            recreate()
        }
    }

    private fun buildKeyboard() {
        title("键盘布局与手感")
        subtitle("默认使用 Unicode 直输盘，通过输入十六进制码点选择字符。")
        section("默认输入模式")
        val c1 = card()
        choice(c1, listOf("Unicode 直输", "中文拼音", "英文"), prefs.defaultMode.coerceIn(0, 2)) {
            prefs.defaultMode = it
            recreate()
        }
        section("键盘尺寸")
        val c2 = card()
        slider(c2, "键盘高度", 0.26f, 0.46f, 0.02f, prefs.keyboardHeightPercent) {
            prefs.keyboardHeightPercent = it
        }
        slider(c2, "按键圆角", 4f, 18f, 1f, prefs.keyRadiusDp) {
            prefs.keyRadiusDp = it
            KeyboardThemes.invalidate()
        }
        section("面板")
        val c4 = card()
        slider(
            c4, "面板高度", 0.32f, 0.66f, 0.02f, prefs.panelHeightPercent,
            display = { Math.round(it * 100).toString() + "%" }
        ) {
            prefs.panelHeightPercent = it
        }
        c4.addView(TextView(this).apply {
            text = "三个面板共用同一高度；打开面板时自动收起键盘。"
            textSize = 12f
            setPadding(dpInt(16f), 0, dpInt(16f), dpInt(12f))
        })
        section("尺寸")
        val c5 = card()
        slider(c5, "键盘行高", 42f, 68f, 2f, prefs.keyRowHeightDp) {
            prefs.keyRowHeightDp = it
            KeyboardThemes.invalidate()
        }
        slider(c5, "候选栏高度", 36f, 60f, 2f, prefs.candidateBarHeightDp) {
            prefs.candidateBarHeightDp = it
            KeyboardThemes.invalidate()
        }
        slider(
            c5, "键盘不透明度", 40f, 100f, 5f, prefs.keyboardAlpha.toFloat(),
            display = { Math.round(it).toString() + "%" }
        ) {
            prefs.keyboardAlpha = it.toInt()
            KeyboardThemes.invalidate()
        }
        slider(c5, "候选词字号", 13f, 24f, 1f, prefs.candidateTextSize) {
            prefs.candidateTextSize = it
            KeyboardThemes.invalidate()
        }
        slider(c5, "按键字号", 13f, 28f, 1f, prefs.keyTextSize) {
            prefs.keyTextSize = it
            KeyboardThemes.invalidate()
        }
        slider(
            c5, "长按响应时间", 250f, 900f, 50f, prefs.longPressMs.toFloat(),
            display = { Math.round(it).toString() + " ms" }
        ) {
            prefs.longPressMs = it.toInt()
        }
        toggle(c5, "PC 键盘", "语言键循环中增加 PC 键盘（Esc / Tab / Ctrl / 方向键）", prefs.pcKeyboardEnabled) {
            prefs.pcKeyboardEnabled = it
        }
        toggle(c5, "悬浮外观", "键盘左右留白并设置圆角，形成浮层效果", prefs.floatKeyboard) {
            prefs.floatKeyboard = it
        }
        section("安全输入")
        val c7 = card()
        toggle(c7, "密码框保护", "密码字段不参与联想与学习，剪贴板历史不记录其内容", prefs.secureInputEnabled) {
            prefs.secureInputEnabled = it
        }
        c7.addView(TextView(this).apply {
            text = "仅在系统将输入框标记为密码字段时生效；相关选项另见「隐私与安全」页。"
            textSize = 12f
            setPadding(dpInt(16f), 0, dpInt(16f), dpInt(12f))
        })
        section("工具栏按钮")
        val c6 = card()
        toggle(c6, "语言 / 中英切换", "", prefs.toolLanguage) { prefs.toolLanguage = it }
        toggle(c6, "Unicode 面板", "", prefs.toolUnicode) { prefs.toolUnicode = it }
        toggle(c6, "Emoji 面板", "", prefs.toolEmoji) { prefs.toolEmoji = it }
        toggle(c6, "剪贴板面板", "", prefs.toolClipboard) { prefs.toolClipboard = it }
        toggle(c6, "AI 润色", "需先在「AI 助手」中启用", prefs.toolAi) { prefs.toolAi = it }
        toggle(c6, "文本编辑面板", "光标移动 / 全选 / 复制粘贴", prefs.toolEdit) { prefs.toolEdit = it }
        c6.addView(TextView(this).apply {
            text = "最右侧的 × 键用于关闭键盘；长按该键进入本设置页。"
            textSize = 12f
            setPadding(dpInt(16f), dpInt(10f), dpInt(16f), dpInt(12f))
        })
        section("按键行为")
        val c3 = card()
        toggle(c3, "字母行常驻数字", "拼音与英文键盘顶部增加数字行", prefs.numberRow) {
            prefs.numberRow = it
        }
        toggle(c3, "长按弹出备选字符", "长按字母键可选取重音字符", prefs.popupOnLongPress) {
            prefs.popupOnLongPress = it
        }
        toggle(c3, "空格滑动移动光标", "按住空格后左右滑动定位光标", prefs.swipeToDelete) {
            prefs.swipeToDelete = it
        }
        toggle(c3, "双击空格插入句号", "", prefs.doubleSpacePeriod) {
            prefs.doubleSpacePeriod = it
        }
        toggle(c3, "句首自动大写", "英文句号后自动切换为大写", prefs.autoCapitalize) {
            prefs.autoCapitalize = it
        }
    }

    private fun buildInput() {
        title("输入与候选")
        subtitle("纠错仅在无完全匹配时触发。")
        section("编码纠错")
        val c1 = card()
        toggle(c1, "自动纠错", "基于编辑距离与相邻键位纠正", prefs.autoCorrection) {
            prefs.autoCorrection = it
        }
        toggle(c1, "中文模糊音", "zh / z、n / l、an / ang", prefs.fuzzyPinyin) {
            prefs.fuzzyPinyin = it
        }
        toggle(c1, "简拼输入", "如 zg → 中国", prefs.initialsEnabled) {
            prefs.initialsEnabled = it
        }
        section("候选")
        val c2 = card()
        slider(c2, "候选词数量", 4f, 12f, 1f, prefs.candidateCount.toFloat()) {
            prefs.candidateCount = it.toInt()
        }
        toggle(c2, "联想词", "提交候选后提示后续词语", prefs.nextWordPredict) {
            prefs.nextWordPredict = it
        }
        toggle(c2, "学习输入习惯", "记录本机词频与上下文", prefs.learningEnabled) {
            prefs.learningEnabled = it
        }
        section("Unicode 码点输入")
        val cu = card()
        toggle(cu, "完整码点自动上屏", "输入满 4 至 6 位十六进制码点后直接上屏，无需按空格确认", prefs.unicodeAutoCommit) {
            prefs.unicodeAutoCommit = it
        }
        toggle(cu, "显示编码与字符名", "候选栏提示 U+4E00 · CJK UNIFIED IDEOGRAPH-4E00", prefs.showCodeHint) {
            prefs.showCodeHint = it
        }
        slider(cu, "近似编码候选数量", 0f, 24f, 1f, prefs.unicodeNearLimit.toFloat()) {
            prefs.unicodeNearLimit = it.toInt()
        }
        cu.addView(TextView(this).apply {
            text = "输入码先进入候选栏：输入 4E00 可出「一」，输入 4E0 则列出 4E00–4E0F 等近似码点。"
            textSize = 12f
            setPadding(dpInt(16f), 0, dpInt(16f), dpInt(12f))
        })
        section("关于词库")
        val c3 = card()
        c3.addView(TextView(this).apply {
            text = "内置中文、简拼与英文词库；具体数量随版本变化，可在「词典与数据」页查看。"
            textSize = 13f
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
        })
    }

    private fun buildAi() {
        title("AI 助手")
        subtitle("支持 OpenAI 兼容接口，内置 DeepSeek 预设。")

        section("隐私")
        val c0 = card()
        c0.addView(TextView(this).apply {
            text = "仅在主动触发「润色 / 联想 / 生成词条」时，当前文本会发送至已配置的接口；密钥仅保存在本机，不写入日志。将接口配置为本机或局域网地址（如 Ollama）时，数据不会离开本地网络。"
            textSize = 13f
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
        })

        section("服务商")
        val c0b = card()
        val presets = org.YavSoft.UniBoard.ai.AiClient.PRESETS
        choice(c0b, presets.map { it.label }, presets.indexOfFirst { it.id == prefs.aiProvider }
            .coerceAtLeast(0)) { idx ->
            val preset = presets[idx]
            prefs.aiProvider = preset.id
            if (preset.id != "custom") {
                prefs.aiEndpoint = preset.endpoint
                prefs.aiModel = preset.model
                prefs.aiEnabled = true
            }
            recreate()
        }
        c0b.addView(TextView(this).apply {
            text = "选择预设将自动填充对应接口与模型；如需接入其他服务，请选择「自定义」并手动填写 OpenAI 兼容接口。"
            textSize = 12f
            setPadding(dpInt(16f), 0, dpInt(16f), dpInt(12f))
        })

        section("连接")
        val c1 = card()
        toggle(c1, "启用 AI", null, prefs.aiEnabled) { prefs.aiEnabled = it }
        textField(c1, "API Key", prefs.aiApiKey) { prefs.aiApiKey = it.trim() }
        textField(c1, "接口地址", prefs.aiEndpoint) { prefs.aiEndpoint = it.trim() }
        textField(c1, "模型名", prefs.aiModel) { prefs.aiModel = it.trim() }

        section("提示词")
        val c2 = card()
        c2.addView(TextView(this).apply {
            text = "内置提示词要求模型仅返回结果、不作解释；温度越低输出越确定。"
            textSize = 12f
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), 0)
        })
        textField(c2, "自定义润色提示词（留空使用内置）", prefs.aiPolishPrompt, multiline = true) {
            prefs.aiPolishPrompt = it
        }
        slider(c2, "温度", 0f, 1f, 0.1f, prefs.aiTemperature / 100f) {
            prefs.aiTemperature = Math.round(it * 100)
        }
        slider(c2, "超时（秒）", 10f, 120f, 5f, prefs.aiTimeoutSec.toFloat()) {
            prefs.aiTimeoutSec = it.toInt()
        }

        section("自检")
        val c3 = card()
        action(c3, "测试连接") { testAi() }
    }

    private fun testAi() {
        toast("正在测试连接…")
        AiClient(prefs).polish("你好 世界", object : AiClient.Callback {
            override fun onResult(text: String) {
                toast("连接成功：" + text.take(24))
            }

            override fun onError(message: String) {
                toast("连接失败：" + message)
            }
        })
    }

    private fun buildScript() {
        title("输入偏好脚本")
        subtitle("每行一条规则，左侧为匹配内容，右侧为替换内容。")
        section("开关")
        val c1 = card()
        toggle(c1, "启用脚本", "提交前改写文本", prefs.scriptEnabled) {
            prefs.scriptEnabled = it
        }

        val engine = script ?: ScriptEngine(this).also { script = it }
        section("规则")
        val c2 = card()
        val field = android.widget.EditText(this).apply {
            setText(engine.rawText())
            textSize = 13f
            setSingleLine(false)
            minLines = 10
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setHorizontallyScrolling(false)
        }
        c2.addView(field, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpInt(260f)
        ))
        action(c2, "保存脚本") {
            engine.save(field.text.toString())
            prefs.scriptEnabled = true
            toast("已保存，立即生效")
        }
        action(c2, "恢复默认示例") {
            engine.save(ScriptEngine.DEFAULT_RULES)
            field.setText(ScriptEngine.DEFAULT_RULES)
            toast("已恢复默认示例")
        }

        section("可用变量")
        val c3 = card()
        c3.addView(TextView(this).apply {
            text = "{date} {time} {datetime} {clip}"
            textSize = 13f
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
        })
    }

    private val lexiconPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importLexicon(uri)
    }

    private fun buildLexicon() {
        title("词典与数据")
        val lex = org.YavSoft.UniBoard.predict.UserLexicon(this).apply { load() }
        val info = TextView(this).apply {
            text = "用户词典当前 ${lex.size} 条。AI 生成与导入的词条会进入此处，并在候选中优先出现。"
            textSize = 13f
            setTextColor(0xFF757575.toInt())
            setPadding(dpInt(16f), dpInt(14f), dpInt(16f), dpInt(6f))
        }

        section("用户词典")
        val c1 = card()
        c1.addView(info)
        action(c1, "AI 生成词条") {
            startActivity(Intent(this, AiLexiconActivity::class.java))
        }
        action(c1, "管理词典（搜索 / 删除）") {
            startActivity(Intent(this, LexiconManagerActivity::class.java))
        }
        action(c1, "导入词典文件") {
            runCatching { lexiconPicker.launch(arrayOf("text/*", "*/*")) }
                .onFailure { toast("未找到可用的文件选择器") }
        }
        action(c1, "导出词典到文件") {
            runCatching { exportPicker.launch("uniboard-lexicon.txt") }
                .onFailure { toast("未找到可用的文件选择器") }
        }
        action(c1, "分享词典") { shareLexicon() }
        action(c1, "清空用户词典") {
            confirm("确定清空全部用户词典词条？此操作不可撤销。") { clearUserLexicon() }
        }

        section("学习")
        val c2 = card()
        toggle(c2, "记录输入习惯", "本机词频与上下文，用于候选排序", prefs.learningEnabled) {
            prefs.learningEnabled = it
        }
        action(c2, "清除学习记录") { clearLearned() }

        section("剪贴板")
        val c3 = card()
        action(c3, "清空剪贴板收藏") {
            confirm("确定清空全部剪贴板收藏？") {
                org.YavSoft.UniBoard.clip.ClipStore(this).clear()
                toast("已清空剪贴板收藏")
            }
        }
        action(c3, "导出剪贴板历史") {
            runCatching { clipExportPicker.launch("uniboard-clipboard.txt") }
                .onFailure { toast("未找到可用的文件选择器") }
        }
        slider(
            c3, "历史保留条数", 0f, 2000f, 100f, prefs.clipboardLimit.toFloat(),
            display = { if (it <= 0f) "不限" else Math.round(it).toString() + " 条" }
        ) {
            prefs.clipboardLimit = it.toInt()
        }
        slider(
            c3, "只保留最近", 0f, 90f, 1f, prefs.clipboardKeepDays.toFloat(),
            display = { if (it <= 0f) "永久" else Math.round(it).toString() + " 天" }
        ) {
            prefs.clipboardKeepDays = it.toInt()
        }
        c3.addView(TextView(this).apply {
            text = "剪贴板历史保存在应用私有目录（filesDir/clip_history.txt），不会上传；可通过上方「导出剪贴板历史」另存。"
            textSize = 12f
            setPadding(dpInt(16f), dpInt(8f), dpInt(16f), dpInt(12f))
        })
        val quick = org.YavSoft.UniBoard.clip.QuickPhrases(this)
        action(c3, "添加常用语（" + quick.list().size + " 条）") { addQuickPhrase() }
        action(c3, "清空常用语") {
            confirm("确定清空全部常用语？") {
                org.YavSoft.UniBoard.clip.QuickPhrases(this).clear()
                toast("已清空常用语")
                recreate()
            }
        }
        action(c3, "清空剪贴板历史") {
            confirm("确定清空剪贴板历史？") {
                org.YavSoft.UniBoard.clip.ClipStore(this).clearHistory()
                toast("已清空剪贴板历史")
            }
        }
    }

    private fun buildPrivacy() {
        title("隐私与安全")
        subtitle("隐私模式会停用可能将内容带出本机的功能；词典导入与手动加词不受限制。")
        section("隐私模式")
        val c1 = card()
        toggle(c1, "开启隐私模式", "停用 AI（本地模型除外）、输入学习、自动收录与剪贴板记录", prefs.privacyMode) {
            prefs.privacyMode = it
        }
        toggle(c1, "隐私模式下允许本地模型", "仅本机或局域网地址的接口", prefs.allowLocalAiInPrivate) {
            prefs.allowLocalAiInPrivate = it
        }
        slider(
            c1, "剪贴板自动清理", 0f, 120f, 5f, prefs.privacyPurgeMinutes.toFloat(),
            display = { if (it <= 0f) "不清理" else Math.round(it).toString() + " 分钟" }
        ) {
            prefs.privacyPurgeMinutes = it.toInt()
        }

        section("安全输入")
        val c2 = card()
        toggle(c2, "密码框保护", "密码字段不参与联想与学习，剪贴板历史不记录其内容", prefs.secureInputEnabled) {
            prefs.secureInputEnabled = it
        }

        section("数据")
        val c3 = card()
        action(c3, "清除学习记录") { clearLearned() }
        action(c3, "清空剪贴板历史") {
            confirm("确定清空剪贴板历史？") {
                org.YavSoft.UniBoard.clip.ClipStore(this).clearHistory()
                toast("已清空剪贴板历史")
            }
        }
    }

    private val fontPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val dir = java.io.File(filesDir, "fonts").apply { mkdirs() }
            val target = java.io.File(dir, "imported_font")
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.importedFontPath = target.absolutePath
            val fr = org.YavSoft.UniBoard.font.FontResolver.get(this)
            fr.invalidate()
            toast(fr.reloadBuiltin())
            recreate()
        }.onFailure { toast("导入失败：" + (it.message ?: "")) }
    }

    private val clipExportPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val store = org.YavSoft.UniBoard.clip.ClipStore(this)
            contentResolver.openOutputStream(uri)?.use {
                it.write(store.exportHistory().toByteArray(Charsets.UTF_8))
            }
            toast("剪贴板历史已导出")
        }.onFailure { toast("导出失败：" + (it.message ?: "")) }
    }

    private val exportPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val lex = org.YavSoft.UniBoard.predict.UserLexicon(this).apply { load() }
            contentResolver.openOutputStream(uri)?.use {
                it.write(lex.exportText().toByteArray(Charsets.UTF_8))
            }
            toast("已导出 " + lex.size + " 条")
        }.onFailure { toast("导出失败：" + (it.message ?: "")) }
    }

    private fun shareLexicon() {
        runCatching {
            val lex = org.YavSoft.UniBoard.predict.UserLexicon(this).apply { load() }
            if (lex.size == 0) {
                toast("用户词典为空")
                return
            }
            val dir = java.io.File(cacheDir, "share").apply { mkdirs() }
            val f = java.io.File(dir, "uniboard-lexicon.txt")
            f.writeText(lex.exportText())
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, packageName + ".fileprovider", f
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "UniBoard 用户词典")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "分享词典"))
        }.onFailure { toast("分享失败：" + (it.message ?: "")) }
    }

    /** 添加一条常用语（在剪贴板面板里可以一键插入） */
    private fun addQuickPhrase() {
        val input = android.widget.EditText(this).apply {
            hint = "例如：收货地址、邮箱、自我介绍"
            textSize = 15f
            setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
        }
        android.app.AlertDialog.Builder(this, R.style.Theme_UniBoard_Dialog)
            .setTitle("添加常用语")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val text = input.text?.toString().orEmpty().trim()
                if (text.isEmpty()) {
                    toast("内容不能为空")
                } else {
                    org.YavSoft.UniBoard.clip.QuickPhrases(this).add(text)
                    toast("已添加常用语")
                    recreate()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearUserLexicon() {
        runCatching {
            val lex = org.YavSoft.UniBoard.predict.UserLexicon(this)
            lex.load()
            lex.clear()
            lex.save()
            toast("已清空用户词典")
            recreate()
        }.onFailure { toast("清空失败") }
    }

    private fun importLexicon(uri: android.net.Uri) {
        runCatching {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            val entries = ArrayList<org.YavSoft.UniBoard.predict.UserLexicon.Entry>()
            for (line in text.lineSequence()) {
                val s = line.trim()
                if (s.isEmpty() || s.startsWith("#")) continue
                val parts = s.split('\t')
                val word = parts[0].trim()
                if (word.isEmpty() || word.length > 16) continue
                val py = parts.getOrNull(1).orEmpty().trim().lowercase()
                    .filter { it in 'a'..'z' || it == ' ' || it == '\'' }
                val w = parts.getOrNull(2)?.trim()?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
                entries.add(org.YavSoft.UniBoard.predict.UserLexicon.Entry(word, py, w, "import"))
                if (entries.size >= 20000) break
            }
            if (entries.isEmpty()) throw IllegalStateException("未发现可导入的词条")
            val lex = org.YavSoft.UniBoard.predict.UserLexicon(this)
            lex.load()
            val added = lex.addAll(entries)
            lex.save()
            toast("已导入 $added 条")
            recreate()
        }.onFailure { toast("导入失败：" + (it.message ?: "格式不正确")) }
    }

    private fun clearLearned() {
        runCatching { java.io.File(filesDir, "user_history.tsv").delete() }
        toast("已清除学习记录")
    }
}