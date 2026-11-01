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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.YavSoft.UniBoard.R
import org.YavSoft.UniBoard.core.AboutInfo
import org.YavSoft.UniBoard.core.dpInt

/** 关于页：版本、仓库、许可证、第三方组件。文案尽量短。 */
class AboutActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    private fun attr(a: Int, fallback: Int): Int =
        com.google.android.material.color.MaterialColors.getColor(this, a, fallback)

    private fun colorText() = attr(com.google.android.material.R.attr.colorOnSurface, 0xFF212121.toInt())
    private fun colorDim() = attr(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF757575.toInt())
    private fun colorDivider() = attr(com.google.android.material.R.attr.colorOutlineVariant, 0xFFE0E0E0.toInt())
    private fun colorSurface() = attr(com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF.toInt())
    private fun colorAccent() = attr(com.google.android.material.R.attr.colorPrimary, getColor(R.color.brand_blue))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorSurface())
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorSurface())
            setPadding(dpInt(4f), 0, dpInt(16f), 0)
        }
        bar.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            imageTintList = android.content.res.ColorStateList.valueOf(colorText())
            val pad = dpInt(10f)
            setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(dpInt(44f), dpInt(44f))
        })
        bar.addView(TextView(this).apply {
            text = "关于"
            textSize = 20f
            setTextColor(colorText())
            setPadding(dpInt(12f), 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dpInt(56f), 1f))
        root.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpInt(56f)
        ))
        root.addView(android.view.View(this).apply {
            setBackgroundColor(colorDivider())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        val scroll = ScrollView(this)
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        // 头部
        column.addView(TextView(this).apply {
            text = "UniBoard"
            textSize = 22f
            setTextColor(colorText())
            setPadding(dpInt(16f), dpInt(20f), dpInt(16f), dpInt(2f))
        })
        column.addView(TextView(this).apply {
            text = "版本 " + versionName() + " · " + AboutInfo.LICENSE_NAME
            textSize = 12.5f
            setTextColor(colorDim())
            setPadding(dpInt(16f), 0, dpInt(16f), dpInt(14f))
        })

        head("仓库")
        entry("打开项目主页") { openUrl(AboutInfo.REPO_URL) }
        entry("复制仓库地址") { copy(AboutInfo.REPO_URL) }
        entry("反馈问题") { openUrl(AboutInfo.ISSUE_URL) }

        head("许可证")
        body("GPL-3.0-or-later。许可证允许自由使用、修改与再分发；再分发时须公开源代码并以相同许可证发布。")
        entry("查看许可证全文") {
            startActivity(Intent(this, LicenseActivity::class.java))
        }

        head("隐私与数据")
        body(
            "UniBoard 不需要账号，不集成统计、广告或崩溃上报 SDK。\n\n" +
                "本地数据均位于应用私有目录，卸载后随应用数据一并删除：\n" +
                "· user_lexicon.tsv：用户词典，含 AI 生成、导入与自动收录的词条\n" +
                "· user_history.tsv：输入习惯，含本机词频与上下文，可在设置中关闭或清除\n" +
                "· 剪贴板历史与收藏：保留条数与天数可配置，支持清空与导出\n" +
                "· uniboard_settings.xml：偏好设置\n" +
                "· fonts/imported_font：仅在手动导入字体后存在\n\n" +
                "默认不联网。仅当用户主动触发 AI 润色、AI 联想或 AI 生成词条时，当前编辑框的文本会发送至用户自行配置的接口；密钥仅保存在本机，不写入日志。将接口配置为本机或局域网地址（如 Ollama）时，数据不会离开本地网络。\n\n" +
                "Android 10 起系统不再向应用开放剪贴板历史，应用只能读取当前一条；剪贴板历史由本应用在打开面板时自行记录，可关闭（隐私模式）或按时间自动清理。\n\n" +
                "隐私模式下关闭 AI（可保留本地模型）、输入学习、自动收录与剪贴板记录，并支持按分钟自动清理历史；词典导入与手动加词不受影响。\n\n" +
                "仅申请 INTERNET 权限，用于用户主动触发的 AI 请求；不申请存储、位置、通讯录、短信等权限。"
        )

        head("第三方组件")
        body(
            "AndroidX / Material Components（Apache-2.0）\n" +
                "jieba（MIT）：中文分词\n" +
                "wordfreq（Apache-2.0）：英文词频数据\n" +
                "GNU Unifont（SIL OFL 1.1）：内置字体\n" +
                "系统 ICU：字符名称与类别"
        )
    }

    private fun head(label: String) {
        column.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(colorAccent())
            setPadding(dpInt(16f), dpInt(20f), dpInt(16f), dpInt(6f))
        })
    }

    private fun body(label: String) {
        column.addView(TextView(this).apply {
            text = label
            textSize = 13.5f
            setTextColor(colorDim())
            setPadding(dpInt(16f), dpInt(2f), dpInt(16f), dpInt(12f))
            setLineSpacing(dpInt(4f).toFloat(), 1f)
        })
    }

    /** 列表行：左文字 + 右箭头 */
    private fun entry(label: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setBackgroundColor(colorSurface())
            setPadding(dpInt(16f), dpInt(15f), dpInt(16f), dpInt(15f))
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(colorText())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 20f
            setTextColor(colorDim())
        })
        column.addView(row)
        column.addView(android.view.View(this).apply {
            setBackgroundColor(colorDivider())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply {
                leftMargin = dpInt(16f)
                rightMargin = dpInt(16f)
            }
        })
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "未找到可用的浏览器，请手动访问 " + url, Toast.LENGTH_LONG).show()
        }
    }

    private fun copy(value: String) {
        runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("UniBoard", value))
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    }.getOrDefault("")
}