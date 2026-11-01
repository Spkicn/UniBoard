# UniBoard

[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3ddc84.svg)](#构建)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.24-7f52ff.svg)](https://kotlinlang.org)

一个以 Unicode 为核心的 Android 输入法。默认键盘不是 QWERTY，而是**直输盘**——
数字、常用符号加一个 `U+` 键，按下就把字符送上屏；需要中文或英文时点底部按钮切换。

自由软件，以 GPL-3.0-or-later 发布。

## 为什么做这个

手机输入法大多把「输入法」等同于「拼音/英文输入」。但 Unicode 有三万多个已分配字符：
箭头、数学符号、注音、彝文、CJK 扩展区、各种图形符号。想输入这些字符，通常得开网页去查码点。

UniBoard 把这件事变简单：默认键盘就是一张字符表，`U+` 键可以直接按码点取任意字符，
输入 `4E0` 会列出 `4E00–4E0F` 区间的字符，字母误输（`O`→`0`、`I`→`1`）也会自动纠正。

## 功能

**输入**

- 默认 Unicode 直输键盘：数字、标点、箭头数学符号、`U+` 码点入口
- 中文全拼与简拼（输入 `zg` 出「中国」），内置约 8.7 万词条、1.8 万组简拼索引
- 英文前缀联想与拼写纠错（编辑距离 + QWERTY 相邻键位）
- 中文模糊音纠错（`zh/z`、`n/l`、`an/ang`）
- 用户词频与上下文学习，全部本地存储
- 词库导入：每行一词，可带权重

**Unicode**

- 码点解析支持 `4E00`、`U+4E00`、`0x4E00`、`&#x4E00;`、`&#19968;`
- 码点容错与前端联想
- 字符名检索（搜 `SNOWMAN`、`HEART`）、33 个区块浏览、最近使用
- 逐字符字形检测，回退链：基准字体 → 导入字体 → 内置 GNU Unifont → 系统字体

**面板**

- Emoji 面板：7 分类约 700 个表情
- 剪贴板面板：读取当前剪贴板、收藏常用片段
- Unicode 面板：码点、名称、区块三种取字方式

**定制**

- 主题：跟随系统 / 浅色 / 深色，8 色主题色板
- 输入偏好脚本：字面替换、正则替换、`{date}` `{time}` `{clip}` 变量
- 键盘高度、候选数量、长按备选、空格滑动手势、音效、震动强度

**AI（可选）**

- 默认对接 DeepSeek，也可换成任何 OpenAI 兼容接口
- 点候选栏星形键 = 润色当前输入；长按 = 联想后续短语
- 内置提示词要求模型「只输出结果、不要解释」
- 关闭后应用完全不联网

## 构建

要求 JDK 17、Android SDK 34。

```bash
git clone https://github.com/tiZAesi/UniBoard.git
cd UniBoard
./gradlew :app:assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。也可以直接用 Android Studio 打开工程。

最小支持 Android 8.0（API 26），编译 SDK 34，Kotlin 1.9.24，AGP 8.5.2。

## 词库

中文词库由脚本生成，源数据不随仓库分发：

```bash
pip install pypinyin
python3 tools/gen_lexicon.py
```

脚本默认从 `tools/lexicon/` 读取 jieba 词典与英文词表，缺失时会提示下载地址。

## 项目结构

```
app/src/main/kotlin/org/YavSoft/UniBoard/
├── ime/       输入法服务，按键分发与模式切换
├── keyboard/  自绘键盘、候选栏、布局定义
├── predict/   词库索引、联想、纠错、用户学习
├── unicode/   码点解析、Unicode 面板、最近记录
├── emoji/     表情数据与面板
├── clip/      剪贴板仓库与面板
├── script/    输入偏好脚本引擎
├── ai/        DeepSeek / OpenAI 兼容客户端
├── font/      字形覆盖检测与字体回退
├── core/      偏好设置
└── ui/        设置页、关于页、许可证页
tools/gen_lexicon.py   词库生成脚本
tools/check_code.py    静态自检（资源引用 / 括号 / 许可证头）
```

## 隐私

- 不收集设备标识，无统计、无广告、无第三方 SDK
- `INTERNET` 权限只为用户主动触发的 AI 请求存在
- 词频、剪贴板收藏、脚本、最近字符全部写在应用私有目录
- 唯一读取的系统敏感信息是剪贴板，且只在你主动打开剪贴板面板时读取

## 已知限制

- 系统自 Android 10 起不再开放剪贴板历史，因此剪贴板面板只能读「当前内容 + 用户手动收藏」
- 输入法无法控制目标应用的字体，字形回退只作用于本应用自己的候选栏与面板
- 手写、语音、滑行输入尚未实现

## 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。提交前请跑一次静态自检：

```bash
python3 tools/check_code.py
```

## 许可证

本项目以 [GNU 通用公共许可证第 3 版或更高版本](LICENSE) 发布。

```
Copyright (C) 2026 UniBoard contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

再分发二进制时须同时提供对应源代码，或给出书面获取途径。

## 致谢与数据来源

| 项目 | 用途 | 许可 |
|---|---|---|
| [jieba](https://github.com/fxsjy/jieba) | 中文词典与词频 | MIT |
| [google-10000-english](https://github.com/first20hours/google-10000-english) | 英文词表 | 公共领域 |
| [GNU Unifont](https://unifoundry.com/unifont/) | 生僻字兜底字体 | SIL OFL 1.1 |
| [pypinyin](https://github.com/mozillazg/python-pinyin) | 词库拼音生成 | MIT |

应用图标由本项目自行设计，不含任何第三方商标元素。

## 相关链接

- 仓库：<https://github.com/tiZAesi/UniBoard>
- 问题反馈：<https://github.com/tiZAesi/UniBoard/issues>
