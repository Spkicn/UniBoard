/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 * 本程序依 GPL-3.0-or-later 授权。
 */

package org.YavSoft.UniBoard.ui

/**
 * 设置项索引：给「设置搜索」用。
 * 每项记住它属于哪个分区，点搜索结果直接跳到对应页面。
 */
object SettingsIndex {

    data class Item(
        val label: String,
        val section: String,
        val keywords: String = ""
    )

    private fun appearance(label: String, kw: String = "") =
        Item(label, SettingsActivity.SECTION_APPEARANCE, kw)

    private fun keyboard(label: String, kw: String = "") =
        Item(label, SettingsActivity.SECTION_KEYBOARD, kw)

    private fun input(label: String, kw: String = "") =
        Item(label, SettingsActivity.SECTION_INPUT, kw)

    private fun ai(label: String, kw: String = "") =
        Item(label, SettingsActivity.SECTION_AI, kw)

    private fun script(label: String, kw: String = "") =
        Item(label, SettingsActivity.SECTION_SCRIPT, kw)

    private fun lexicon(label: String, kw: String = "") =
        Item(label, SettingsActivity.SECTION_LEXICON, kw)

    private fun privacy(label: String, kw: String = "") =
        Item(label, SettingsActivity.SECTION_PRIVACY, kw)

    val ITEMS: List<Item> = listOf(
        // 外观与主题
        appearance("跟随系统取色", "动态取色 material you 壁纸 主题色"),
        appearance("主题模式", "浅色 深色 跟随系统 夜间"),
        appearance("主题色", "颜色 强调色 品牌色 accent"),
        appearance("恢复默认品牌色", "重置 颜色 accent"),
        appearance("按键动画", "动效 动画 缩放 回弹"),
        appearance("按键音", "声音 音效 按键声"),
        appearance("按键音音量", "音量 声音"),
        appearance("音效方式", "自动 系统音效 合成音 sound"),
        appearance("震动反馈", "震动 触感 触觉 haptic"),
        appearance("震动强度", "震动 强度"),
        appearance("生僻字回退", "字体 回退 unifont 缺字形"),
        appearance("内置字体适用范围", "字体 适用范围 unicode 非 ascii 缺字形"),
        appearance("重新加载内置字体", "字体 重载 unifont"),
        appearance("导入外部字体", "字体 ttf otf ttc"),
        appearance("恢复默认字体", "字体 重置"),

        // 键盘布局与手感
        keyboard("默认输入模式", "unicode 直输 拼音 英文 码点"),
        keyboard("键盘高度", "尺寸 高度 height"),
        keyboard("按键圆角", "尺寸 圆角 radius"),
        keyboard("面板高度", "面板 半屏 panel"),
        keyboard("键盘行高", "尺寸 行高 行距"),
        keyboard("候选栏高度", "尺寸 候选栏"),
        keyboard("键盘不透明度", "透明 不透明度 alpha"),
        keyboard("候选词字号", "字体大小 候选 字号"),
        keyboard("按键字号", "字体大小 按键 字号"),
        keyboard("长按响应时间", "长按 延迟 long press"),
        keyboard("PC 键盘", "电脑 物理键盘 esc tab ctrl 方向键"),
        keyboard("悬浮外观", "悬浮 浮层 float"),
        keyboard("密码框保护", "安全 密码 secure"),
        keyboard("语言 / 中英切换", "语言 中英 切换 工具栏"),
        keyboard("Unicode 面板", "工具栏 unicode 码点"),
        keyboard("Emoji 面板", "工具栏 表情 emoji"),
        keyboard("剪贴板面板", "工具栏 剪贴板 clipboard"),
        keyboard("AI 润色", "工具栏 ai 润色"),
        keyboard("文本编辑面板", "工具栏 光标 全选 复制 粘贴"),
        keyboard("字母行常驻数字", "数字行 顶部 数字"),
        keyboard("长按弹出备选字符", "重音 备选 长按 popup"),
        keyboard("空格滑动移动光标", "空格 光标 滑动"),
        keyboard("双击空格插入句号", "句号 双击空格"),
        keyboard("句首自动大写", "大写 句首 自动 capitalize"),

        // 输入与候选
        input("自动纠错", "纠错 拼写 correction"),
        input("中文模糊音", "模糊音 zh z n l an ang"),
        input("简拼输入", "简拼 zg 首字母"),
        input("候选词数量", "候选 数量"),
        input("联想词", "联想 下个词 predict"),
        input("学习输入习惯", "学习 词频 上下文 记录"),
        input("完整码点自动上屏", "unicode 码点 上屏 自动提交"),
        input("显示编码与字符名", "编码 字符名 code hint"),
        input("近似编码候选数量", "近似码 容错 候选"),

        // AI 助手
        ai("服务商", "预设 preset deepseek openai kimi 智谱 ollama 本地"),
        ai("启用 AI", "ai 开关"),
        ai("API Key", "密钥 key token"),
        ai("接口地址", "endpoint url 本地 局域网 ollama"),
        ai("模型名", "model 模型"),
        ai("自定义润色提示词", "提示词 prompt 润色"),
        ai("温度", "temperature 随机性"),
        ai("超时", "timeout 超时时间"),
        ai("测试连接", "测试 连通 自检"),

        // 输入偏好脚本
        script("启用脚本", "脚本 开关 script"),
        script("保存脚本", "脚本 保存 应用"),
        script("恢复默认示例", "脚本 默认 示例 重置"),
        script("可用变量", "脚本 变量 date time datetime clip"),

        // 词典与数据
        lexicon("用户词典", "词典 词条 lexicon"),
        lexicon("AI 生成词条", "词典 生成 ai"),
        lexicon("管理词典", "词典 删除 搜索 管理"),
        lexicon("导入词典文件", "导入 词典 import"),
        lexicon("导出词典到文件", "导出 词典 export"),
        lexicon("分享词典", "分享 词典 share"),
        lexicon("清空用户词典", "清空 词典 重置"),
        lexicon("记录输入习惯", "学习 词频 上下文"),
        lexicon("清除学习记录", "学习 清除 重置"),
        lexicon("清空剪贴板收藏", "剪贴板 收藏 清空"),
        lexicon("导出剪贴板历史", "剪贴板 导出 export"),
        lexicon("历史保留条数", "剪贴板 上限 条数"),
        lexicon("只保留最近", "剪贴板 清理 天数 保留"),
        lexicon("添加常用语", "常用语 短语 快捷 quick"),
        lexicon("清空常用语", "常用语 清空"),
        lexicon("清空剪贴板历史", "剪贴板 历史 清空"),

        // 隐私与安全
        privacy("开启隐私模式", "隐私 禁用 ai 学习 剪贴板"),
        privacy("隐私模式下允许本地模型", "隐私 本地 ollama 局域网"),
        privacy("剪贴板自动清理", "隐私 清理 分钟 purge"),
        privacy("密码框保护", "安全 密码 secure"),
        privacy("清除学习记录", "学习 清除 重置"),
        privacy("清空剪贴板历史", "剪贴板 历史 清空")
    )

    fun search(query: String): List<Item> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return ITEMS.filter {
            it.label.contains(q, true) || it.keywords.contains(q, true)
        }.take(40)
    }
}