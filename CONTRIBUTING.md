# 参与贡献

感谢你愿意改进 UniBoard。项目以 GPL-3.0-or-later 发布，提交贡献即表示你同意以同一许可证授权你的代码。

## 提 Issue 之前

- 先搜索现有 issue，避免重复
- 说清楚：设备型号、Android 版本、输入法版本（设置 → 关于里能看到）
- 崩溃请附上 `adb logcat` 里与本应用相关的堆栈

## 提 PR 的流程

1. Fork 仓库并从 `main` 拉新分支，例如 `feat/emoji-search`
2. 保持改动聚焦，一个 PR 只做一件事
3. 新增 Kotlin 文件必须保留文件头的 GPL 声明
4. 提交前跑一次静态自检：

```bash
python3 tools/check_code.py
```

它会检查资源引用是否存在、括号是否配平、每个源文件是否带许可证头。

5. 提交信息用「类型: 摘要」的格式，例如 `fix: 修复候选栏在深色主题下的高亮偏差`

## 代码风格

- 注释用中文，命名用英文
- 界面元素尽量自绘（Canvas），避免引入体积大的 UI 依赖
- 不引入未经审计的网络库或统计 SDK
- 新增设置项要同时在设置页给出说明文字

## 词库

中文词库由 `tools/gen_lexicon.py` 生成，依赖 `pypinyin`：

```bash
pip install pypinyin
python3 tools/gen_lexicon.py
```

脚本会从 `tools/lexicon/` 读取 jieba 词典与英文词表（这两个源文件不随仓库分发，运行脚本时会提示下载地址）。
