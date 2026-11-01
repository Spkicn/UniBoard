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

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import org.YavSoft.UniBoard.MainActivity
import org.YavSoft.UniBoard.ai.AiClient
import org.YavSoft.UniBoard.R
import org.YavSoft.UniBoard.core.InputMode
import org.YavSoft.UniBoard.unicode.CodeInputEngine
import org.YavSoft.UniBoard.core.Prefs
import org.YavSoft.UniBoard.core.dpInt
import org.YavSoft.UniBoard.font.FontResolver
import org.YavSoft.UniBoard.keyboard.CandidateBar
import org.YavSoft.UniBoard.keyboard.KeyAction
import org.YavSoft.UniBoard.keyboard.KeyboardView
import org.YavSoft.UniBoard.keyboard.Layouts
import org.YavSoft.UniBoard.predict.Candidate
import org.YavSoft.UniBoard.predict.PredictionEngine
import org.YavSoft.UniBoard.ui.theme.KeyboardTheme
import org.YavSoft.UniBoard.ui.theme.KeyboardThemes

/**
 * UniBoard 输入法服务。
 *
 * 负责创建输入视图、把按键动作翻译成文本提交，并在候选栏与本地面板之间
 * 协调联想、纠错与 Unicode 输入。
 */
class UniBoardImeService : InputMethodService(),
    KeyboardView.Listener,
    CandidateBar.Listener {

    private lateinit var prefs: Prefs
    private lateinit var engine: PredictionEngine
    private lateinit var composer: TextComposer
    private lateinit var script: org.YavSoft.UniBoard.script.ScriptEngine
    private lateinit var theme: KeyboardTheme

    private var rootView: LinearLayout? = null
    private var columnView: LinearLayout? = null
    private var keyboardView: KeyboardView? = null
    private var candidateBar: CandidateBar? = null
    private var unicodePanel: org.YavSoft.UniBoard.unicode.UnicodePanelView? = null
    private var clipboardPanel: org.YavSoft.UniBoard.clip.ClipboardPanelView? = null
    private var emojiPanel: org.YavSoft.UniBoard.emoji.EmojiPanelView? = null
    private var editPanel: org.YavSoft.UniBoard.ui.TextEditPanelView? = null
    private var ai: AiClient? = null

    private var mode: InputMode = InputMode.PINYIN
    private var layoutId: String = Layouts.LATIN
    private var shiftOn: Boolean = false
    /** 是否切到了 PC 键盘 */
    private var pcActive = false
    /** 大写锁定（长按 Shift 进入） */
    private var shiftLocked: Boolean = false
    private var numberOnly: Boolean = false
    private var currentCandidates: List<Candidate> = emptyList()
    private var lastWord: String? = null
    private var correctedHint: String? = null
    /** Unicode 模式下的编码/字符名提示 */
    private var codeNote: String? = null
    /** 哪个面板的搜索框正在接收键盘输入：null / "unicode" / "clip" */
    private var searchTarget: String? = null
    /** 当前编辑框是密码框：不联想、不学习、不留剪贴板 */
    private var secureInput = false
    private var lexiconDirty = false
    private val aiAsked = HashSet<String>()

    private val isChineseMode: Boolean
        get() = mode == InputMode.PINYIN

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        theme = KeyboardThemes.resolve(this, prefs)
        engine = PredictionEngine(this, prefs)
        engine.initializeAsync()
        composer = TextComposer { currentInputConnection }
        script = org.YavSoft.UniBoard.script.ScriptEngine(this)
        mode = if (prefs.chineseMode) InputMode.PINYIN else InputMode.LATIN
        Thread { runCatching { FontResolver.get(this).preload() } }.start()
    }

    override fun onCreateInputView(): View {
        theme = KeyboardThemes.resolve(this, prefs)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.keyboardBackground)
        }

        val bar = CandidateBar(this).apply {
            listener = this@UniBoardImeService
            this.theme = this@UniBoardImeService.theme
            visibleTools = enabledTools()
            aiReady = AiClient.isConfigured(prefs)
            onDismiss = { hideCandidateBar() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpInt(theme.candidateBarHeightDp)
            )
        }
        val kb = KeyboardView(this).apply {
            listener = this@UniBoardImeService
            prefs = this@UniBoardImeService.prefs
            this.theme = this@UniBoardImeService.theme
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        column.addView(bar)
        column.addView(kb)
        root.addView(column, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        candidateBar = bar
        keyboardView = kb
        rootView = root
        columnView = column
        refreshLayout()
        updateCandidates()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composer.reset()
        resolveMode(attribute)
        lastWord = null
        correctedHint = null
        shiftOn = false
        shiftLocked = false
        lastWasSpace = false
        layoutId = Layouts.LATIN
        refreshLayout()
        updateCandidates()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        theme = KeyboardThemes.resolve(this, prefs)
        if (prefs.privacyMode) {
            runCatching {
                org.YavSoft.UniBoard.clip.ClipStore(this)
                    .purgeOlderThan(prefs.privacyPurgeMinutes)
            }
        }
        engine.reloadUserLexicon()
        applyTheme()
        refreshLayout()
        updateCandidates()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && closeTopPanel()) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && closeTopPanel()) return true
        return super.onKeyUp(keyCode, event)
    }

    /** 有面板打开时，返回键先关面板；返回是否消费了按键 */
    private fun closeTopPanel(): Boolean {
        var handled = false
        if (editPanel?.visibility == View.VISIBLE) { hideEditPanel(); handled = true }
        if (emojiPanel?.visibility == View.VISIBLE) { hideEmojiPanel(); handled = true }
        if (clipboardPanel?.visibility == View.VISIBLE) { hideClipboardPanel(); handled = true }
        if (unicodePanel?.visibility == View.VISIBLE) { hideUnicodePanel(); handled = true }
        return handled
    }

    override fun onFinishInput() {
        flushLexicon()
        engine.flush()
        super.onFinishInput()
    }

    private fun resolveMode(info: EditorInfo?) {
        val type = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val cls = type and InputType.TYPE_MASK_CLASS
        numberOnly = cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        if (numberOnly) {
            mode = InputMode.NUMBER
            return
        }
        val variation = type and InputType.TYPE_MASK_VARIATION
        val latinOnly = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        // 只把真正的密码框当作安全输入；NO_SUGGESTIONS 在普通输入框里也很常见，不能据此判定
        secureInput = prefs.secureInputEnabled && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        mode = when {
            latinOnly -> InputMode.LATIN
            else -> when (prefs.defaultMode) {
                Prefs.MODE_PINYIN -> InputMode.PINYIN
                Prefs.MODE_LATIN -> InputMode.LATIN
                else -> InputMode.UNICODE
            }
        }
    }

    /** 按设置过滤工具栏按钮 */
    private fun enabledTools(): List<CandidateBar.Tool> {
        val list = ArrayList<CandidateBar.Tool>(6)
        if (prefs.toolLanguage) list.add(CandidateBar.Tool.LANGUAGE)
        if (prefs.toolUnicode) list.add(CandidateBar.Tool.UNICODE)
        if (prefs.toolEmoji) list.add(CandidateBar.Tool.EMOJI)
        if (prefs.toolClipboard) list.add(CandidateBar.Tool.CLIPBOARD)
        if (prefs.toolAi && prefs.aiEnabled) list.add(CandidateBar.Tool.AI)
        if (prefs.toolEdit) list.add(CandidateBar.Tool.EDIT)
        // 关闭键盘始终可用（设置入口改成长按这个按钮）
        list.add(CandidateBar.Tool.CLOSE)
        return list
    }

    private fun applyTheme() {
        val column = columnView ?: return
        // 悬浮外观：左右留白 + 圆角，配合透明窗口做出浮层观感
        val floatMode = prefs.floatKeyboard
        (column.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            val h = if (floatMode) dpInt(14f) else 0
            val bottom = if (floatMode) dpInt(10f) else 0
            lp.setMargins(h, 0, h, bottom)
            column.layoutParams = lp
        }
        if (floatMode) {
            column.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpInt(20f).toFloat()
                setColor(theme.keyboardBackground)
            }
        } else {
            column.setBackgroundColor(theme.keyboardBackground)
        }
        candidateBar?.visibleTools = enabledTools()
        candidateBar?.aiReady = AiClient.isConfigured(prefs)
        // 防御：layoutParams 为空时不要把它置成 null（否则候选栏会变成 0 高，看起来像“消失了”）
        candidateBar?.let { bar ->
            val lp = bar.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpInt(theme.candidateBarHeightDp)
                )
            lp.height = dpInt(theme.candidateBarHeightDp)
            bar.layoutParams = lp
        }
        candidateBar?.theme = theme
        keyboardView?.theme = theme
        unicodePanel?.theme = theme
        clipboardPanel?.theme = theme
        emojiPanel?.theme = theme
        editPanel?.theme = theme
    }

    private fun refreshLayout() {
        val kb = keyboardView ?: return
        val layout = when {
            searchTarget != null -> Layouts.buildLatin(upper = false, chinese = false, numberRow = false)
            pcActive -> Layouts.pcLayout(isChineseMode)
            numberOnly -> Layouts.numpad()
            mode == InputMode.UNICODE -> Layouts.unicodePad(modeLabel(), isChineseMode)
            layoutId == Layouts.SYMBOLS -> Layouts.symbols(isChineseMode)
            layoutId == Layouts.SYMBOLS_MORE -> Layouts.symbolsMore(isChineseMode)
            shiftOn -> Layouts.buildLatin(upper = true, chinese = isChineseMode, numberRow = prefs.numberRow)
            else -> Layouts.buildLatin(upper = false, chinese = isChineseMode, numberRow = prefs.numberRow)
        }
        kb.setLayout(layout)
        kb.shiftLocked = shiftOn
        kb.spaceHint = when {
            numberOnly -> null
            pcActive -> "PC 键盘"
            mode == InputMode.UNICODE -> "Unicode"
            isChineseMode -> "拼音输入"
            else -> "English"
        }
        candidateBar?.languageLabel = when {
            pcActive -> "PC"
            mode == InputMode.UNICODE -> "Uni"
            isChineseMode -> "中"
            else -> "EN"
        }
    }

    /** 点候选栏叉号：收起整条候选栏，键盘上移 */
    private fun hideCandidateBar() {
        candidateBar?.visibility = View.GONE
        currentCandidates = emptyList()
    }

    private fun showCandidateBar() {
        val bar = candidateBar ?: return
        if (bar.visibility != View.VISIBLE) bar.visibility = View.VISIBLE
    }

    private fun updateCandidates() {
        val composing = composer.composing
        if (secureInput) {
            // 密码框：不给任何联想，避免通过输入法侧信道泄露
            currentCandidates = emptyList()
            candidateBar?.candidates = emptyList()
            candidateBar?.statusHint = null
            candidateBar?.toolsVisible = true
            return
        }

        // Unicode 码点输入：和拼音一样走候选，不直接上屏
        if (mode == InputMode.UNICODE && composing.isNotEmpty()) {
            val res = CodeInputEngine.predict(
                composing,
                limit = prefs.candidateCount,
                nearLimit = prefs.unicodeNearLimit
            )
            currentCandidates = res.candidates
            correctedHint = res.correctedFrom?.let { "已按近似码纠正：$it" }
            codeNote = if (prefs.showCodeHint) res.note else res.canonical
            val bar = candidateBar ?: return
            bar.candidates = currentCandidates
            bar.statusHint = buildHint(composing)
            bar.toolsVisible = currentCandidates.isEmpty()
            showCandidateBar()
            return
        }
        codeNote = null

        val result = if (composing.isNotEmpty()) {
            engine.predict(composing, mode, prefs.candidateCount)
        } else {
            null
        }
        if (result != null) {
            currentCandidates = result.candidates
            correctedHint = result.correctedFrom?.let { "已按模糊音纠正：$it" }
        } else {
            val predictOn = prefs.nextWordPredict
            currentCandidates = if (predictOn && lastWord != null) engine.suggestNext(lastWord!!) else emptyList()
            correctedHint = null
        }
        val bar = candidateBar ?: return
        bar.candidates = currentCandidates
        bar.statusHint = buildHint(composing)
        bar.toolsVisible = currentCandidates.isEmpty()
    }

    /**
     * 提交后学习：词典里已有的词加权，没有的自动收录。
     * 隐私模式、密码框、关闭学习时都不写盘。
     */
    private fun learnWord(word: String) {
        if (prefs.privacyMode || secureInput || !prefs.learningEnabled) return
        val w = word.trim()
        if (w.length < 2 || w.length > 16) return
        if (w.any { !it.isLetter() && !it.isDigit() }) return
        val lex = engine.userLexicon
        if (lex.contains(w)) {
            lex.bump(w)
        } else if (prefs.autoCollectWords) {
            lex.add(org.YavSoft.UniBoard.predict.UserLexicon.Entry(w, "", 120, "auto"))
        } else {
            return
        }
        lexiconDirty = true
    }

    private fun flushLexicon() {
        if (!lexiconDirty) return
        runCatching { engine.userLexicon.save() }
        lexiconDirty = false
    }

    /** 候选太少时，让 AI 临时补几条（不写入词典） */
    private fun maybeAiFill(composing: String) {
        if (!prefs.aiAutoGenerate || secureInput) return
        if (prefs.privacyMode && !(prefs.allowLocalAiInPrivate && AiClient.isLocalEndpoint(prefs.aiEndpoint))) return
        if (!AiClient.isConfigured(prefs)) return
        if (composing.length < 2 || currentCandidates.size >= 3) return
        if (sameAsLastAi(composing)) return
        AiClient(prefs).complete(
            "你是输入法词库助手。仅输出词语，每行一个，不作解释、不加编号、不加标点。",
            "给出 5 个拼音以 \"" + composing + "\" 开头、或与之强相关的常用中文词（2 至 4 字）。",
            object : AiClient.Callback {
            override fun onResult(text: String) {
                if (composer.composing != composing) return
                val words = text.lineSequence().map { it.trim().trim('-', '*', '·', ' ') }
                    .filter { it.isNotEmpty() && it.length in 2..8 && it.any { c -> c.code > 127 } }
                    .distinct().take(5).toList()
                if (words.isEmpty()) return
                val extra = words.mapIndexed { i, w ->
                    org.YavSoft.UniBoard.predict.Candidate(
                        text = w, score = 5.0 - i, corrected = false
                    )
                }
                currentCandidates = (currentCandidates + extra).distinctBy { it.text }
                candidateBar?.candidates = currentCandidates
            }

            override fun onError(message: String) = Unit
            })
    }

    private fun sameAsLastAi(composing: String): Boolean {
        if (aiAsked.contains(composing)) return true
        if (aiAsked.size > 60) aiAsked.clear()
        aiAsked.add(composing)
        return false
    }

    /** 输入码凑成完整码点时按设置自动上屏 */
    private fun autoCommitCode() {
        if (!prefs.unicodeAutoCommit) return
        if (mode != InputMode.UNICODE) return
        val composing = composer.composing
        if (composing.isEmpty()) return
        val res = CodeInputEngine.predict(composing, limit = 1, nearLimit = 0)
        if (!res.complete) return
        val top = res.candidates.firstOrNull() ?: return
        if (top.corrected) return
        composer.commit(top.text)
        codeNote = null
        updateCandidates()
    }

    /** 候选栏提示：只在「正在输入但还没有候选」时显示当前组合串，其余情况不占地方 */
    private fun buildHint(composing: String): String? =
        if (composing.isNotEmpty() && currentCandidates.isEmpty()) composing else null

    override fun onKey(action: KeyAction) {
        when (action) {
            is KeyAction.Insert -> handleInsert(action.text)
            KeyAction.Backspace -> handleBackspace()
            KeyAction.Space -> handleSpace()
            KeyAction.Enter -> handleEnter()
            KeyAction.Shift -> toggleShift()
            KeyAction.ShiftLock -> {
                shiftOn = true
                shiftLocked = true
                refreshLayout()
            }
            is KeyAction.Goto -> handleGoto(action.id)
            KeyAction.SwitchIme -> showInputMethodPicker()
            KeyAction.Settings -> openSettings()
            KeyAction.ToggleUnicode -> openUnicodePanel()
            KeyAction.Emoji -> openEmojiPanel()
            KeyAction.Language -> toggleLanguage()
            KeyAction.CommitComposing -> commitFirstCandidate()
            is KeyAction.SendKey -> sendPcKey(action.keyCode)
            KeyAction.ModeUnicode -> setMode(InputMode.UNICODE)
            KeyAction.ModePinyin -> setMode(InputMode.PINYIN)
            KeyAction.ModeLatin -> setMode(InputMode.LATIN)
            KeyAction.EmojiPanel -> openEmojiPanel()
            KeyAction.AiPolish -> aiPolish()
            KeyAction.AiSuggest -> aiSuggest()
        }
    }

    private fun handleInsert(text: String) {
        if (text.isEmpty()) return
        when (searchTarget) {
            "unicode" -> { unicodePanel?.appendText(text); return }
            "clip" -> { clipboardPanel?.appendText(text); return }
        }
        if (numberOnly) {
            composer.commit(text)
            return
        }
        // Unicode 盘：十六进制位和 U+/0x 前缀进入组合串，走候选链路（和拼音同一套逻辑）
        if (mode == InputMode.UNICODE && CodeInputEngine.isCodeInput(text)) {
            composer.append(text)
            updateCandidates()
            autoCommitCode()
            return
        }
        if (mode == InputMode.UNICODE) {
            // 标点等非编码键：先把当前码点对应的字上屏，再接标点
            if (composer.isComposing) commitFirstCandidate()
            commitDirect(text)
            return
        }
        val asciiLetter = text.length == 1 && text[0].code < 128 && text[0].isLetter()
        if (asciiLetter) {
            val ch = if (isChineseMode) text[0].lowercaseChar() else text[0]
            composer.append(ch)
            if (shiftOn && !shiftLocked) {
                shiftOn = false
                refreshLayout()
            }
            updateCandidates()
            return
        }
        commitDirect(text)
    }

    private fun commitDirect(text: String) {
        if (text.isEmpty()) return
        val out = script.apply(text)
        if (out.isEmpty()) return
        lastWasSpace = false
        // 英文输入：句末标点后自动切回大写首字母
        if (prefs.autoCapitalize && mode == InputMode.LATIN &&
            out.trim().let { it == "." || it == "!" || it == "?" || it == "。" }
        ) {
            shiftOn = true
            refreshLayout()
        }
        learnWord(out)
        val needSpace = mode == InputMode.LATIN && out.last().isLetterOrDigit()
        composer.commit(if (needSpace) "$out " else out)
        if (out.isNotBlank()) {
            if (prefs.learningEnabled) engine.noteCommit(out.trim(), lastWord)
            lastWord = out.trim()
        }
        updateCandidates()
    }

    private fun handleBackspace() {
        when (searchTarget) {
            "unicode" -> { unicodePanel?.backspace(); return }
            "clip" -> { clipboardPanel?.backspace(); return }
        }
        composer.backspace()
        lastWord = null
        correctedHint = null
        updateCandidates()
    }

    private var lastSpaceAt = 0L
    private var lastWasSpace = false

    private fun handleSpace() {
        if (composer.isComposing) {
            if (currentCandidates.isNotEmpty()) {
                commitCandidate(0)
            } else {
                // 没有候选时把输入码原样留下，避免吞掉用户打的字符
                composer.commit(composer.composing)
                updateCandidates()
            }
            return
        }
        val now = System.currentTimeMillis()
        if (prefs.doubleSpacePeriod && lastWasSpace && now - lastSpaceAt < 600) {
            // 双击空格：把上一个空格换成句号
            currentInputConnection?.deleteSurroundingText(1, 0)
            composer.commit("。")
            lastWasSpace = false
            lastWord = null
            updateCandidates()
            return
        }
        composer.commit(" ")
        lastWasSpace = true
        lastSpaceAt = now
        lastWord = null
        updateCandidates()
    }

    private fun handleEnter() {
        if (composer.isComposing) {
            if (currentCandidates.isNotEmpty()) {
                commitCandidate(0)
            } else {
                composer.finishComposing()
            }
            return
        }
        if (!sendDefaultEditorAction(true)) {
            val ic = currentInputConnection ?: return
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun toggleShift() {
        if (shiftLocked) {
            // 锁定状态下点一下：解除锁定
            shiftLocked = false
            shiftOn = false
        } else {
            shiftOn = !shiftOn
        }
        refreshLayout()
    }

    private fun handleGoto(id: String) {
        if (layoutId == id) {
            layoutId = Layouts.LATIN
        } else {
            layoutId = id
        }
        shiftOn = false
        refreshLayout()
    }

    /** PC 键盘：把按键当作物理键盘事件发出去 */
    private fun sendPcKey(code: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun toggleLanguage() {
        if (pcActive) {
            pcActive = false
            setMode(InputMode.UNICODE)
            return
        }
        if (prefs.pcKeyboardEnabled && mode == InputMode.LATIN) {
            pcActive = true
            refreshLayout()
            updateCandidates()
            return
        }
        val next = when (mode) {
            InputMode.UNICODE -> InputMode.PINYIN
            InputMode.PINYIN -> InputMode.LATIN
            else -> InputMode.UNICODE
        }
        setMode(next)
    }

    private fun setMode(next: InputMode) {
        composer.finishComposing()
        mode = next
        prefs.defaultMode = when (next) {
            InputMode.PINYIN -> Prefs.MODE_PINYIN
            InputMode.LATIN -> Prefs.MODE_LATIN
            else -> Prefs.MODE_UNICODE
        }
        prefs.chineseMode = next == InputMode.PINYIN
        layoutId = Layouts.LATIN
        shiftOn = false
        lastWord = null
        refreshLayout()
        updateCandidates()
    }

    /** 底部切换键显示"下一个模式"，点一下即可切过去 */
    private fun modeLabel(): String = when (mode) {
        InputMode.UNICODE -> "中"
        InputMode.PINYIN -> "EN"
        else -> "U+"
    }

    override fun onCandidate(index: Int) {
        commitCandidate(index)
    }

    private fun commitCandidate(index: Int) {
        val c = currentCandidates.getOrNull(index) ?: return
        val word = c.text
        if (prefs.learningEnabled && !prefs.privacyMode && !secureInput) {
            engine.noteCommit(word, lastWord)
            learnWord(word)
        }
        if (mode == InputMode.LATIN) {
            composer.commit("$word ")
        } else {
            composer.commit(word)
        }
        lastWord = word
        correctedHint = null
        currentCandidates = engine.suggestNext(word)
        candidateBar?.candidates = currentCandidates
        candidateBar?.statusHint = buildHint("")
    }

    private fun commitFirstCandidate() {
        if (currentCandidates.isNotEmpty()) commitCandidate(0) else handleEnter()
    }

    override fun onMoveCursor(offset: Int) {
        composer.moveCursor(offset)
    }

    override fun onToolClick(tool: CandidateBar.Tool) {
        when (tool) {
            CandidateBar.Tool.LANGUAGE -> toggleLanguage()
            CandidateBar.Tool.UNICODE -> openUnicodePanel()
            CandidateBar.Tool.CLIPBOARD -> openClipboardPanel()
            CandidateBar.Tool.EMOJI -> openEmojiPanel()
            CandidateBar.Tool.EDIT -> openEditPanel()
            CandidateBar.Tool.AI -> {
                if (!AiClient.isConfigured(prefs)) {
                    toast("请先在「设置 → AI 助手」中启用并配置接口（本地模型可留空密钥）")
                } else {
                    aiPolish()
                }
            }
            CandidateBar.Tool.CLOSE -> requestHideSelf(0)
        }
    }

    override fun onToolLongClick(tool: CandidateBar.Tool) {
        if (tool == CandidateBar.Tool.CLOSE) {
            openSettings()
            return
        }
        if (tool == CandidateBar.Tool.AI) aiSuggest()
    }

    /** 快捷切换输入法：优先切到下一个，其次拉起系统选择器，最后回到设置页 */
    private fun showInputMethodPicker() {
        val switched = runCatching { switchToNextInputMethod(false) }.getOrDefault(false)
        if (switched) return
        val reflected = runCatching {
            val imm = getSystemService(InputMethodManager::class.java)
            val m = InputMethodManager::class.java.getMethod("showInputMethodPicker")
            m.invoke(imm)
        }.isSuccess
        if (!reflected) {
            runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun openSettings() {
        // 先收起键盘，避免 Activity 被输入法窗口挡在后面，看起来像“没反应”
        runCatching { requestHideSelf(0) }
        val ok = runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }.isSuccess
        if (!ok) {
            // 部分 ROM 禁止输入法后台启动 Activity，退一步直接开设置页
            val ok2 = runCatching {
                startActivity(
                    Intent(this, org.YavSoft.UniBoard.ui.SettingsActivity::class.java)
                        .putExtra("section", "appearance")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (!ok2) toast("无法打开设置页，请从桌面图标进入 UniBoard")
        }
    }

    private fun openUnicodePanel() {
        val root = rootView ?: return
        hideClipboardPanel()
        hideEditPanel()
        var panel = unicodePanel
        if (panel == null) {
            panel = org.YavSoft.UniBoard.unicode.UnicodePanelView(this).apply {
                this.theme = this@UniBoardImeService.theme
                listener = object : org.YavSoft.UniBoard.unicode.UnicodePanelView.Listener {
                    override fun onPick(text: String) {
                        commitDirect(text)
                        hideUnicodePanel()
                    }
                    override fun onClose() {
                        hideUnicodePanel()
                    }

                    override fun onSearchFocus(active: Boolean) {
                        searchTarget = if (active) "unicode" else null
                        syncChromeVisibility()
                        refreshLayout()
                        updateCandidates()
                    }
                }
            }
            root.addView(panel, 0, panelParams())
            unicodePanel = panel
        }
        panel.layoutParams = panelParams()
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        panel.animateIn()
        syncChromeVisibility()
    }

    private fun hideUnicodePanel() {
        if (searchTarget == "unicode") searchTarget = null
        unicodePanel?.let {
            it.searchActive = false
            it.animateOut()
            it.visibility = View.GONE
        }
        syncChromeVisibility()
    }

    private fun openClipboardPanel() {
        val root = rootView ?: return
        hideUnicodePanel()
        hideEditPanel()
        if (prefs.clipboardKeepDays > 0) {
            runCatching {
                org.YavSoft.UniBoard.clip.ClipStore(this)
                    .purgeOlderThan(prefs.clipboardKeepDays * 24 * 60)
            }
        }
        var panel = clipboardPanel
        if (panel == null) {
            panel = org.YavSoft.UniBoard.clip.ClipboardPanelView(this).apply {
                this.theme = this@UniBoardImeService.theme
                listener = object : org.YavSoft.UniBoard.clip.ClipboardPanelView.Listener {
                    override fun onPaste(text: String) {
                        commitDirect(text)
                        hideClipboardPanel()
                    }

                    override fun onClose() {
                        hideClipboardPanel()
                    }

                    override fun onSearchFocus(active: Boolean) {
                        searchTarget = if (active) "clip" else null
                        syncChromeVisibility()
                        refreshLayout()
                    }
                }
            }
            root.addView(panel, 0, panelParams())
            clipboardPanel = panel
        }
        panel.refresh()
        panel.layoutParams = panelParams()
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        panel.animateIn()
        syncChromeVisibility()
    }

    private fun hideClipboardPanel() {
        if (searchTarget == "clip") searchTarget = null
        clipboardPanel?.let {
            it.animateOut()
            it.visibility = View.GONE
        }
        syncChromeVisibility()
    }

    private fun openEmojiPanel() {
        val root = rootView ?: return
        hideUnicodePanel()
        hideClipboardPanel()
        var panel = emojiPanel
        if (panel == null) {
            panel = org.YavSoft.UniBoard.emoji.EmojiPanelView(this).apply {
                this.theme = this@UniBoardImeService.theme
                listener = object : org.YavSoft.UniBoard.emoji.EmojiPanelView.Listener {
                    override fun onPick(emoji: String) {
                        commitDirect(emoji)
                    }

                    override fun onClose() {
                        hideEmojiPanel()
                    }
                }
            }
            root.addView(panel, 0, panelParams())
            emojiPanel = panel
        }
        panel.layoutParams = panelParams()
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        panel.animateIn()
        syncChromeVisibility()
    }

    private fun openEditPanel() {
        val root = rootView ?: return
        hideUnicodePanel()
        hideClipboardPanel()
        hideEmojiPanel()
        var panel = editPanel
        if (panel == null) {
            panel = org.YavSoft.UniBoard.ui.TextEditPanelView(this).apply {
                theme = this@UniBoardImeService.theme
                listener = object : org.YavSoft.UniBoard.ui.TextEditPanelView.Listener {
                    override fun onMoveCursor(offset: Int) = moveCursorBy(offset)
                    override fun onSelectAll() = contextAction(android.R.id.selectAll)
                    override fun onCopy() = contextAction(android.R.id.copy)
                    override fun onCut() = contextAction(android.R.id.cut)
                    override fun onPaste() = contextAction(android.R.id.paste)
                    override fun onDeleteForward() {
                        currentInputConnection?.deleteSurroundingText(0, 1)
                    }
                    override fun onLeftClick() {
                        // 左键：在光标处点一下（多数编辑器响应 DPAD_CENTER）
                        val ic = currentInputConnection ?: return
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
                    }
                    override fun onRightClick() = showEditMenu()
                    override fun onClose() = hideEditPanel()
                }
            }
            root.addView(panel, 0, panelParams())
            editPanel = panel
        }
        panel.theme = theme
        panel.layoutParams = panelParams()
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        panel.animateIn()
        syncChromeVisibility()
    }

    private fun hideEditPanel() {
        editPanel?.let {
            it.animateOut()
            it.visibility = View.GONE
        }
        syncChromeVisibility()
    }

    /** 右键菜单：复制 / 剪切 / 粘贴 / 全选 */
    private fun showEditMenu() {
        val items = arrayOf("复制", "剪切", "粘贴", "全选")
        android.app.AlertDialog.Builder(this, R.style.Theme_UniBoard_Dialog)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> contextAction(android.R.id.copy)
                    1 -> contextAction(android.R.id.cut)
                    2 -> contextAction(android.R.id.paste)
                    3 -> contextAction(android.R.id.selectAll)
                }
            }
            .show()
    }

    private fun moveCursorBy(offset: Int) {
        val ic = currentInputConnection ?: return
        val code = if (offset < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun contextAction(id: Int) {
        val ic = currentInputConnection ?: return
        runCatching { ic.performContextMenuAction(id) }
    }

    private fun hideEmojiPanel() {
        emojiPanel?.let {
            it.animateOut()
            it.visibility = View.GONE
        }
        syncChromeVisibility()
    }

    /** 面板高度：按设置里的比例取屏幕高度，默认半屏 */
    private fun panelHeightPx(): Int {
        val h = (resources.displayMetrics.heightPixels * prefs.panelHeightPercent).toInt()
        return h.coerceAtLeast(dpInt(220f))
    }

    private fun panelParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelHeightPx())

    /** 面板打开时收起键盘与候选栏，关闭时恢复 */
    private fun syncChromeVisibility() {
        val anyPanel = listOf(unicodePanel, clipboardPanel, emojiPanel, editPanel)
            .any { it?.visibility == View.VISIBLE }
        // 搜索框聚焦时要留着键盘输入，其余情况面板一开就收起键盘
        val hideChrome = anyPanel && searchTarget == null
        columnView?.visibility = if (hideChrome) View.GONE else View.VISIBLE
        // 搜索时面板留在上半部分、键盘在下面；普通面板模式下面板独占窗口
        if (anyPanel) {
            val lp = unicodePanel?.layoutParams as? LinearLayout.LayoutParams
            if (lp != null) {
                lp.height = if (searchTarget != null) {
                    (panelHeightPx() * 0.62f).toInt()
                } else {
                    panelHeightPx()
                }
                unicodePanel?.layoutParams = lp
            }
        }
    }

    private fun aiClient(): AiClient = ai ?: AiClient(prefs).also { ai = it }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /** AI 润色：优先处理选中文字，其次当前组合串，最后取光标前一段 */
    private fun privacyBlocked(): Boolean {
        if (!prefs.privacyMode) return false
        // 本地/局域网模型不出公网，隐私模式下允许放行
        if (prefs.allowLocalAiInPrivate && AiClient.isLocalEndpoint(prefs.aiEndpoint)) return false
        toast("隐私模式已开启，AI 已停用（可在设置中允许本地模型）")
        return true
    }

    private fun aiPolish() {
        if (privacyBlocked()) return
        val ic = currentInputConnection ?: return
        val selected = runCatching { ic.getSelectedText(0)?.toString() }.getOrNull()
        val source = when {
            !selected.isNullOrBlank() -> selected
            composer.composing.isNotBlank() -> composer.composing
            else -> ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        }
        if (source.isBlank()) {
            toast("没有可润色的文本")
            return
        }
        toast("正在润色…")
        aiClient().polish(source, object : AiClient.Callback {
            override fun onResult(text: String) {
                if (text.isBlank()) {
                    toast("AI 未返回内容")
                    return
                }
                // 先给用户看一眼，确认后再替换，避免直接改坏原文
                android.app.AlertDialog.Builder(this@UniBoardImeService, R.style.Theme_UniBoard_Dialog)
                    .setTitle("AI 润色结果")
                    .setMessage(text)
                    .setPositiveButton("替换") { _, _ ->
                        val target = currentInputConnection ?: return@setPositiveButton
                        if (!selected.isNullOrBlank()) {
                            target.commitText(text, 1)
                        } else {
                            target.deleteSurroundingText(source.length, 0)
                            target.commitText(text, 1)
                        }
                        composer.reset()
                        updateCandidates()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            override fun onError(message: String) {
                toast("润色失败：" + message)
            }
        })
    }

    /** AI 联想：把结果直接放进候选栏 */
    private fun aiSuggest() {
        if (privacyBlocked()) return
        val ic = currentInputConnection ?: return
        val context = ic.getTextBeforeCursor(120, 0)?.toString().orEmpty()
        if (context.isBlank()) {
            toast("请先输入内容")
            return
        }
        toast("正在联想…")
        aiClient().suggest(context, object : AiClient.Callback {
            override fun onResult(text: String) {
                val parts = text.split('|', '\n', '\r')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(5)
                if (parts.isEmpty()) {
                    toast("未获得联想结果")
                    return
                }
                currentCandidates = parts.map { Candidate(it) }
                candidateBar?.candidates = currentCandidates
                candidateBar?.statusHint = "AI 联想"
            }

            override fun onError(message: String) {
                toast("联想失败：" + message)
            }
        })
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        engine.flush()
        super.onDestroy()
    }
}
