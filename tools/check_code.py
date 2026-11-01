#!/usr/bin/env python3
"""UniBoard 静态自检：资源引用、括号配平、编码问题。"""
import os, re, sys, io

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main")

def load_resources():
    res = {"string": set(), "color": set(), "style": set(), "dimen": set(),
           "drawable": set(), "layout": set(), "mipmap": set(), "xml": set(), "anim": set()}
    values = os.path.join(APP, "res", "values")
    if os.path.isdir(values):
        for fn in os.listdir(values):
            if not fn.endswith(".xml"):
                continue
            txt = io.open(os.path.join(values, fn), encoding="utf-8").read()
            for kind in ("string", "color", "style", "dimen"):
                for m in re.finditer(r'<%s\s+name="([^"]+)"' % kind, txt):
                    res[kind].add(m.group(1))
    for kind in ("drawable", "layout", "anim", "xml", "mipmap"):
        d = os.path.join(APP, "res", kind)
        if os.path.isdir(d):
            for fn in os.listdir(d):
                res[kind].add(fn.split(".")[0])
    # mipmap-anydpi-v26 等子目录
    rd = os.path.join(APP, "res")
    for sub in os.listdir(rd):
        if sub.startswith("mipmap-"):
            for fn in os.listdir(os.path.join(rd, sub)):
                res["mipmap"].add(fn.split(".")[0])
    return res

def kt_files():
    out = []
    for base, _, files in os.walk(os.path.join(APP, "kotlin")):
        for f in files:
            if f.endswith(".kt"):
                out.append(os.path.join(base, f))
    return sorted(out)

def strip_noise(src):
    """去掉注释与字符串内容，保留长度结构，便于括号配平检查。"""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i+1] == '/':
            while i < n and src[i] != '\n':
                out.append(' ')
                i += 1
        elif c == '/' and i + 1 < n and src[i+1] == '*':
            out.append('  ')
            i += 2
            while i + 1 < n and not (src[i] == '*' and src[i+1] == '/'):
                out.append('\n' if src[i] == '\n' else ' ')
                i += 1
            out.append('  ')
            i += 2
        elif c == '"':
            if i + 2 < n and src[i+1] == '"' and src[i+2] == '"':
                out.append('   ')
                i += 3
                while i + 2 < n and not (src[i] == '"' and src[i+1] == '"' and src[i+2] == '"'):
                    out.append('\n' if src[i] == '\n' else ' ')
                    i += 1
                out.append('   ')
                i += 3
            else:
                out.append(' ')
                i += 1
                while i < n and src[i] != '"':
                    if src[i] == '\\':
                        out.append('  ')
                        i += 2
                        continue
                    out.append('\n' if src[i] == '\n' else ' ')
                    i += 1
                out.append(' ')
                i += 1
        elif c == "'":
            out.append(' ')
            i += 1
            while i < n and src[i] != "'":
                if src[i] == '\\':
                    out.append('  ')
                    i += 2
                    continue
                out.append(' ')
                i += 1
            out.append(' ')
            i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)

def main():
    res = load_resources()
    problems = []
    for path in kt_files():
        rel = os.path.relpath(path, ROOT)
        src = io.open(path, encoding="utf-8").read()
        # 1) 资源引用
        for m in re.finditer(r'\bR\.(string|color|style|dimen|drawable|layout|mipmap|xml|anim)\.([A-Za-z0-9_]+)', src):
            kind, name = m.group(1), m.group(2)
            if name not in res.get(kind, set()):
                problems.append(f"{rel}: 未定义资源 R.{kind}.{name}")
        # 2) 括号配平
        clean = strip_noise(src)
        for open_c, close_c, label in (('{', '}', '花括号'), ('(', ')', '圆括号'), ('[', ']', '方括号')):
            if clean.count(open_c) != clean.count(close_c):
                problems.append(f"{rel}: {label}不配平 {clean.count(open_c)} vs {clean.count(close_c)}")
        # 3) 文件必须带 GPL 声明
        if "GNU 通用公共许可证" not in src:
            problems.append(f"{rel}: 缺少 GPL 版权声明头")
    # 4) values 目录内的资源重名（会导致 aapt 报 duplicate resource）
    seen_names = {}
    values_dir = os.path.join(APP, "res", "values")
    if os.path.isdir(values_dir):
        for fn in sorted(os.listdir(values_dir)):
            if not fn.endswith(".xml"):
                continue
            txt = io.open(os.path.join(values_dir, fn), encoding="utf-8").read()
            for kind in ("string", "color", "style", "dimen", "bool", "integer"):
                for m in re.finditer(r'<%s\s+name="([^"]+)"' % kind, txt):
                    key = kind + "/" + m.group(1)
                    if key in seen_names:
                        problems.append(
                            f"资源重复定义 {key}：{seen_names[key]} 与 {fn}")
                    else:
                        seen_names[key] = fn

    # 5) 去重输出
    seen = set()
    for p in problems:
        if p not in seen:
            seen.add(p)
            print(p)
    print(f"\n检查完成，共 {len(seen)} 个问题")
    return 1 if seen else 0

sys.exit(main())
