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

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.YavSoft.UniBoard.core.InputMode
import org.YavSoft.UniBoard.core.Prefs
import java.util.concurrent.Executors
import kotlin.math.log10

/**
 * 统一的联想与纠错引擎。
 *
 * - 中文：全拼词库精确/前缀匹配 + 模糊音纠错 + 用户词频学习
 * - 英文：前缀树联想 + 编辑距离/相邻键纠错
 * - 上下文：提交后记录二元组，用于下一词预测
 */
class PredictionEngine(context: Context, private val prefs: Prefs) {

    private val appContext = context.applicationContext
    private val zh = PinyinLexicon()
    private val zhInitials = PinyinLexicon()
    private val en = EnglishLexicon()
    /** 用户词典：AI 生成 / 导入 / 自建词条 */
    val userLexicon = UserLexicon(appContext)
    private val history = UserHistory(appContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadLock = Any()

    @Volatile
    var ready: Boolean = false
        private set

    private var started = false
    private var corrector: LatinCorrector? = null

    /** 异步加载词库（首次调用有效），完成后在主线程回调 */
    fun initializeAsync(onReady: () -> Unit = {}) {
        synchronized(loadLock) {
            if (started) return
            started = true
        }
        executor.execute {
            runCatching {
                appContext.assets.open("lexicon/zh_pinyin.txt").bufferedReader().use { zh.load(it) }
                runCatching {
                    appContext.assets.open("lexicon/zh_initials.txt").bufferedReader()
                        .use { zhInitials.load(it) }
                }
                appContext.assets.open("lexicon/en_words.txt").bufferedReader().use { en.load(it) }
                userLexicon.load()
                history.load()
                corrector = LatinCorrector(en)
            }
            ready = zh.isLoaded || en.isLoaded
            mainHandler.post { onReady() }
        }
    }

    /** 输入过程中的预测 */
    fun predict(input: String, mode: InputMode, limit: Int = 30): PredictionResult {
        if (!ready || input.isEmpty()) return PredictionResult.EMPTY
        return when (mode) {
            InputMode.PINYIN -> predictPinyin(input, limit)
            InputMode.LATIN -> predictLatin(input, limit)
            InputMode.NUMBER, InputMode.UNICODE -> PredictionResult.EMPTY
        }
    }

    /** 基于上一个词的后续词联想 */
    fun suggestNext(prev: String, limit: Int = 8): List<Candidate> {
        if (!ready || prev.isEmpty()) return emptyList()
        return history.nextWords(prev, limit).map {
            Candidate(it, score = history.freqOf(it).toDouble())
        }
    }

    /** 重新读取用户词典（在设置页改过词典后调用） */
    fun reloadUserLexicon() {
        runCatching { userLexicon.load() }
    }

    /** 记录一次成功提交，用于用户词频与上下文学习 */
    fun noteCommit(word: String, prev: String?) {
        history.record(word, prev)
    }

    /** 用户手动加入词条 */
    fun addUserWord(word: String) {
        history.addWord(word)
    }

    fun userFreq(word: String): Int = history.freqOf(word)

    /** 落盘用户词频（输入法隐藏时调用） */
    fun flush() {
        history.save()
    }

    fun clearUserData() {
        history.clear()
    }

    private fun predictPinyin(input: String, limit: Int): PredictionResult {
        val py = normalizePinyin(input)
        if (py.isEmpty()) return PredictionResult.EMPTY
        val pool = LinkedHashMap<String, Candidate>(64)

        fun offer(word: String, freq: Int, exact: Boolean, corrected: Boolean, bonus: Double = 0.0) {
            if (word.isEmpty()) return
            val user = history.freqOf(word)
            var score = log10(freq.toDouble() + 10.0) * 10.0 + user * 22.0
            if (exact) score += 26.0
            score += word.length * 1.5 + bonus + DailyWords.bonus(word)
            val old = pool[word]
            if (old == null || old.score < score) {
                pool[word] = Candidate(
                    text = word,
                    score = score,
                    corrected = corrected,
                    userWord = user > 0
                )
            }
        }

        // 0) 用户词典优先（AI 生成 / 自建词条命中权重最高）
        userLexicon.exact(py).forEach { offer(it.text, it.freq, true, false, 30.0) }
        userLexicon.prefix(py).forEach { offer(it.text, it.freq, false, false, 8.0) }
        if (prefs.initialsEnabled) {
            userLexicon.exactInitials(py).forEach { offer(it.text, it.freq, false, false, 10.0) }
        }

        // 1) 完整拼音精确命中：「zhongguo」-> 中国
        zh.exact(py).forEach { offer(it.text, it.freq, true, false) }

        // 2) 完整拼音前缀补全：输入没打完时给出后续候选
        if (pool.size < limit) {
            zh.prefix(py).forEach { offer(it.text, it.freq, false, false) }
        }

        // 3) 简拼：精确 + 前缀。「zg」-> 中国；「z」-> 这个/自己/中国…
        if (prefs.initialsEnabled && pool.size < limit) {
            zhInitials.exact(py).forEach { offer(it.text, it.freq, false, false, 6.0) }
            zhInitials.prefix(py, maxKeys = 60, perKey = 2)
                .forEach { offer(it.text, it.freq, false, false, -4.0) }
        }

        // 4) 双段组合：woai + beijing -> 我爱北京（只在直接命中不足时兜底）
        if (pool.size < limit) {
            combineTwoSegments(py, limit) { w, f -> offer(w, f, false, false, -8.0) }
        }

        // 5) 模糊音纠错：只在上面都没结果时才做，避免干扰正常输入
        var correctedFrom: String? = null
        if (pool.isEmpty() && prefs.fuzzyPinyin) {
            for (v in FuzzyPinyin.variants(py)) {
                if (v == py) continue
                var hit = false
                zh.exact(v).forEach {
                    offer(it.text, it.freq, false, true)
                    hit = true
                }
                if (!hit) {
                    zh.prefix(v, maxKeys = 30).forEach {
                        offer(it.text, it.freq, false, true)
                        hit = true
                    }
                }
                if (!hit) {
                    zhInitials.exact(v).forEach {
                        offer(it.text, it.freq, false, true)
                        hit = true
                    }
                }
                if (hit) correctedFrom = py
                if (pool.size >= limit * 2) break
            }
        }

        val sorted = pool.values.sortedByDescending { it.score }.take(limit)
        return PredictionResult(sorted, correctedFrom)
    }

    /**
     * 把输入拆成两段拼音分别查词再拼成短语。
     *
     * 这是不引入完整分词模型的前提下最划算的一步：
     * 「woaibeijing」「nihao」这类整串拼音往往查不到整词，
     * 但拆成「wo」+「ai」、「ni」+「hao」都能命中。
     */
    private fun combineTwoSegments(py: String, limit: Int, emit: (String, Int) -> Unit) {
        if (py.length < 4) return
        var produced = 0

        // 两段：woai + beijing
        val upper = minOf(py.length - 2, 7)
        for (cut in 2..upper) {
            if (produced >= limit) break
            val left = zh.exact(py.substring(0, cut)).take(2)
            if (left.isEmpty()) continue
            val right = zh.exact(py.substring(cut)).take(2)
            if (right.isEmpty()) continue
            for (l in left) {
                for (r in right) {
                    if (produced >= limit) break
                    emit(l.text + r.text, minOf(l.freq, r.freq) / 3)
                    produced++
                }
            }
        }

        // 两段「全拼 + 简拼」混排：zhongguo + zg、wo + bzd 这类
        for (cut in 2..minOf(py.length - 2, 7)) {
            if (produced >= limit) break
            val head = py.substring(0, cut)
            val tail = py.substring(cut)
            val leftExact = zh.exact(head).take(1).firstOrNull()
            val rightIni = zhInitials.exact(tail).take(1).firstOrNull()
            if (leftExact != null && rightIni != null) {
                emit(leftExact.text + rightIni.text, minOf(leftExact.freq, rightIni.freq) / 4)
                produced++
            }
            val leftIni = zhInitials.exact(head).take(1).firstOrNull()
            val rightExact = zh.exact(tail).take(1).firstOrNull()
            if (leftIni != null && rightExact != null && produced < limit) {
                emit(leftIni.text + rightExact.text, minOf(leftIni.freq, rightExact.freq) / 4)
                produced++
            }
        }

        // 三段：wo + ai + ni（只在两段也没凑够时再试，控制开销）
        if (produced > 0 || py.length < 6) return
        val maxA = minOf(py.length - 4, 5)
        for (a in 2..maxA) {
            if (produced >= limit) break
            val w1 = zh.exact(py.substring(0, a)).take(1).firstOrNull() ?: continue
            val maxB = minOf(py.length - a - 2, a + 6)
            for (b in a + 2..maxB) {
                if (produced >= limit) break
                val w2 = zh.exact(py.substring(a, b)).take(1).firstOrNull() ?: continue
                val w3 = zh.exact(py.substring(b)).take(1).firstOrNull() ?: continue
                emit(w1.text + w2.text + w3.text, minOf(w1.freq, w2.freq, w3.freq) / 5)
                produced++
            }
        }
    }

    /** 归一化拼音输入：小写、ü→v、去掉分隔符和声调数字 */
    private fun normalizePinyin(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input.lowercase()) {
            when {
                ch in 'a'..'z' -> sb.append(ch)
                ch == 'ü' -> sb.append('v')
                else -> Unit // 空格、撇号、声调数字等一律忽略
            }
        }
        return sb.toString()
    }

    private fun predictLatin(input: String, limit: Int): PredictionResult {
        val s = input.lowercase()
        val capitalize = input.isNotEmpty() && input[0].isUpperCase()
        val pool = LinkedHashMap<String, Candidate>(32)

        fun offer(word: String, freq: Int, exact: Boolean, corrected: Boolean) {
            val user = history.freqOf(word)
            var score = log10(freq.toDouble() + 10.0) * 10.0 + user * 25.0
            if (exact) score += 30.0
            val label = if (capitalize) word.replaceFirstChar { it.uppercaseChar() } else word
            val old = pool[word]
            if (old == null || old.score < score) {
                pool[word] = Candidate(
                    text = word,
                    label = label,
                    score = score,
                    corrected = corrected,
                    userWord = user > 0
                )
            }
        }

        userLexicon.englishPrefix(s).forEach { offer(it.text, it.freq * 3, it.text == s, false) }
        en.prefix(s, limit * 2).forEach { offer(it.text, it.freq, it.text == s, false) }

        var correctedFrom: String? = null
        if (pool.isEmpty() && s.length >= 3 && prefs.autoCorrection) {
            val fixes = corrector?.correct(s, limit) ?: emptyList()
            if (fixes.isNotEmpty()) {
                correctedFrom = s
                fixes.forEach { offer(it.text, it.freq, false, true) }
            }
        }

        val sorted = pool.values.sortedByDescending { it.score }.take(limit)
        return PredictionResult(sorted, correctedFrom)
    }
}
