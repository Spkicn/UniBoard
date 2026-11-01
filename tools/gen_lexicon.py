#!/usr/bin/env python3
# UniBoard 词库生成脚本
# Copyright (C) 2026 UniBoard contributors
# 本脚本是自由软件，依 GPL-3.0-or-later 授权。
#
# 输入：
#   tools/lexicon/jieba_dict.txt  —— jieba 词典（MIT 许可，见 NOTICE）
#   tools/lexicon/en_10k.txt      —— google-10000-english（公共领域）
# 输出：
#   app/src/main/assets/lexicon/zh_pinyin.txt  拼音 -> 候选词（按词频降序）
#   app/src/main/assets/lexicon/en_words.txt   英文词频表

import os
import sys

from pypinyin import Style, pinyin

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "lexicon")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "lexicon")

# 中文词条数量上限（按词频降序截取）
ZH_LIMIT = int(os.environ.get("ZH_LIMIT", "90000"))
# 同一拼音下最多保留的候选词数量
PER_PINYIN = 16
# 词长限制
MIN_LEN, MAX_LEN = 1, 6

CJK_START, CJK_END = 0x4E00, 0x9FFF


def is_cjk(ch: str) -> bool:
    return CJK_START <= ord(ch) <= CJK_END


def load_zh():
    path = os.path.join(SRC, "jieba_dict.txt")
    entries = []
    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            parts = line.split()
            if len(parts) < 2:
                continue
            word, freq = parts[0], parts[1]
            if not word or not freq.isdigit():
                continue
            if not all(is_cjk(c) for c in word):
                continue
            if not (MIN_LEN <= len(word) <= MAX_LEN):
                continue
            f0 = int(freq)
            if f0 <= 0:
                continue
            entries.append((word, f0))
    entries.sort(key=lambda x: -x[1])
    # 去重（保留最高词频）
    seen = {}
    for w, f0 in entries:
        if w not in seen:
            seen[w] = f0
    items = sorted(seen.items(), key=lambda x: -x[1])[:ZH_LIMIT]
    return items


def build_zh():
    items = load_zh()
    print(f"中文词条: {len(items)}")
    buckets = {}
    init_buckets = {}
    for word, freq in items:
        seq = pinyin(word, style=Style.NORMAL, heteronym=False, errors="ignore")
        if not seq or len(seq) != len(word):
            continue
        py = "".join(s[0] for s in seq if s)
        if not py or not py.isascii():
            continue
        buckets.setdefault(py, []).append((word, freq))
        init = "".join(c[0][0] for c in seq if c and c[0])
        if init and init.isascii() and 2 <= len(init) <= 6:
            init_buckets.setdefault(init, []).append((word, freq))

    os.makedirs(OUT, exist_ok=True)
    total = 0
    with open(os.path.join(OUT, "zh_pinyin.txt"), "w", encoding="utf-8") as f:
        for py in sorted(buckets):
            words = sorted(buckets[py], key=lambda x: -x[1])[:PER_PINYIN]
            total += len(words)
            f.write(py + "\t" + ",".join(f"{w}:{fr}" for w, fr in words) + "\n")
    print(f"拼音索引: {len(buckets)} 组 / {total} 词条")

    # 简拼索引：每个音节取首字母，例如「中国」-> zg
    init_total = 0
    with open(os.path.join(OUT, "zh_initials.txt"), "w", encoding="utf-8") as f:
        for ini in sorted(init_buckets):
            words = sorted(init_buckets[ini], key=lambda x: -x[1])[:PER_PINYIN]
            init_total += len(words)
            f.write(ini + "\t" + ",".join(f"{w}:{fr}" for w, fr in words) + "\n")
    print(f"简拼索引: {len(init_buckets)} 组 / {init_total} 词条")


def build_en():
    path = os.path.join(SRC, "en_10k.txt")
    words = []
    with open(path, encoding="utf-8", errors="ignore") as f:
        for i, line in enumerate(f):
            w = line.strip()
            if not w or not w.isascii() or not w.isalpha():
                continue
            words.append((w.lower(), max(1, 10000 - i)))
    seen = {}
    for w, fr in words:
        if w not in seen:
            seen[w] = fr
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "en_words.txt"), "w", encoding="utf-8") as f:
        for w, fr in sorted(seen.items(), key=lambda x: -x[1]):
            f.write(f"{w}\t{fr}\n")
    print(f"英文词表: {len(seen)}")


if __name__ == "__main__":
    build_en()
    build_zh()
    for name in os.listdir(OUT):
        p = os.path.join(OUT, name)
        print(name, os.path.getsize(p) // 1024, "KB")
