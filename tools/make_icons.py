#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从源图生成 UniBoard 全套启动图标资源。

产出（app/src/main/res/）：
  mipmap-*/ic_launcher.png               传统方形图标
  mipmap-*/ic_launcher_round.png         传统圆形图标
  mipmap-*/ic_launcher_foreground_img.png 自适应图标前景（108dp 画布，内容居中约占 72dp 安全区）
  mipmap-*/ic_launcher_mono.png          Monet（Material You）主题图标单色层
  values/ic_launcher_background.xml      自适应图标背景色（取自源图边角色）
用法：python3 tools/make_icons.py <源图> [项目根，默认由脚本位置推断]
"""
import sys
import pathlib
import statistics
from PIL import Image, ImageDraw, ImageFilter

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
SAFE = 0.72          # 自适应图标：折叠后可见区域占 108dp 画布的 72%
LEGACY_CONTENT = 0.88  # 传统图标：内容占比


def load_square(path: pathlib.Path, size: int = 1024) -> Image.Image:
    im = Image.open(path)
    im = im.convert("RGBA")
    w, h = im.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    im = im.crop((left, top, left + side, top + side))
    return im.resize((size, size), Image.LANCZOS)


def border_color(im: Image.Image, band: int = 8) -> tuple:
    """取四边一圈像素的均值作为背景色，并返回其标准差。"""
    px = im.load()
    w, h = im.size
    samples = []
    for x in range(w):
        for y in list(range(band)) + list(range(h - band, h)):
            samples.append(px[x, y])
    for y in range(h):
        for x in list(range(band)) + list(range(w - band, w)):
            samples.append(px[x, y])
    rgb = [(p[0], p[1], p[2]) for p in samples if p[3] > 128]
    if not rgb:
        return (18, 21, 27), 99.0
    mean = tuple(int(statistics.fmean(c[i] for c in rgb)) for i in range(3))
    std = statistics.fmean(statistics.pstdev(c[i] for c in rgb) for i in range(3))
    return mean, std


def luminance(im: Image.Image) -> Image.Image:
    return im.convert("L")


def subject_mask(im: Image.Image, bg: tuple, bg_std: float) -> Image.Image:
    """得到主体剪影的 L 蒙版（255=主体）。"""
    alpha = im.split()[3]
    a_hist = alpha.histogram()
    total = im.size[0] * im.size[1]
    transparent = sum(a_hist[:200])
    if transparent > total * 0.02:
        # 源图自带透明通道，直接用它
        return alpha.point(lambda v: 255 if v > 128 else 0)

    # 否则按“与边角色差异”抠出主体
    px = im.load()
    w, h = im.size
    tol = max(28, int(bg_std * 2.4))
    mask = Image.new("L", (w, h), 0)
    mpx = mask.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a * 255 < 96:
                continue
            d = max(abs(r - bg[0]), abs(g - bg[1]), abs(b - bg[2]))
            if d > tol:
                mpx[x, y] = 255
    # 闭运算：先膨胀后腐蚀，填补主体内部的小孔
    k = max(3, (w // 96) | 1)
    mask = mask.filter(ImageFilter.MaxFilter(k))
    mask = mask.filter(ImageFilter.MinFilter(k))
    return mask


def fit_center(src: Image.Image, canvas: int, ratio: float) -> Image.Image:
    box = max(1, int(round(canvas * ratio)))
    art = src.resize((box, box), Image.LANCZOS)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    off = (canvas - box) // 2
    out.alpha_composite(art, (off, off))
    return out


def round_crop(im: Image.Image) -> Image.Image:
    w, h = im.size
    mask = Image.new("L", (w * 4, h * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, w * 4 - 1, h * 4 - 1), fill=255)
    mask = mask.resize((w, h), Image.LANCZOS)
    out = im.copy()
    out.putalpha(mask)
    return out


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    src_path = pathlib.Path(sys.argv[1])
    default_root = pathlib.Path(__file__).resolve().parent.parent
    root = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else default_root
    res = root / "app/src/main/res"
    if not src_path.is_file():
        print(f"源图不存在：{src_path}")
        return 3

    src = load_square(src_path)
    bg, bg_std = border_color(src)
    alpha = src.split()[3]
    transparent = sum(alpha.histogram()[:200]) / (src.size[0] * src.size[1])
    print(f"源图：{src_path} -> 1024x1024，边角色 {bg}，边角标准差 {bg_std:.1f}，透明像素占比 {transparent:.1%}")

    mask = subject_mask(src, bg, bg_std)
    covered = sum(1 for v in mask.getdata() if v > 128) / (mask.size[0] * mask.size[1])
    print(f"主体剪影覆盖率：{covered:.1%}")
    mask_small = mask.resize((512, 512), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.6))

    report = []
    for name, scale in DENSITIES.items():
        d = res / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)

        # 自适应图标：108dp 画布
        fg_size = int(round(108 * scale))
        fg = fit_center(src, fg_size, SAFE)
        fg.save(d / "ic_launcher_foreground_img.png")

        # Monet 单色层：108dp 画布，取主体剪影
        mono = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
        m = mask_small.resize((int(round(fg_size * SAFE)),) * 2, Image.LANCZOS)
        white = Image.new("RGBA", m.size, (255, 255, 255, 255))
        white.putalpha(m)
        off = (fg_size - m.size[0]) // 2
        mono.alpha_composite(white, (off, off))
        mono.save(d / "ic_launcher_mono.png")

        # 传统图标：legacy 48dp
        lg_size = int(round(48 * scale))
        base = Image.new("RGBA", (lg_size, lg_size), bg + (255,))
        base.alpha_composite(fit_center(src, lg_size, LEGACY_CONTENT))
        base.convert("RGB").save(d / "ic_launcher.png")
        round_crop(base).save(d / "ic_launcher_round.png")
        report.append(f"  {name:7s} fg={fg_size:3d} mono={fg_size:3d} legacy={lg_size:3d}")

    (res / "values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f'    <color name="ic_launcher_background">#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}</color>\n'
        "</resources>\n",
        encoding="utf-8",
    )
    print("\n".join(report))
    print(f"背景色已写入 #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")

    # 预览：自适应图标折叠效果 + Monet 染色效果
    preview = Image.new("RGBA", (1024, 512), (245, 245, 245, 255))
    big = fit_center(src, 432, SAFE)
    card = Image.new("RGBA", (432, 432), bg + (255,))
    card.alpha_composite(big)
    circ = Image.new("L", (432 * 4, 432 * 4), 0)
    ImageDraw.Draw(circ).ellipse((0, 0, 432 * 4 - 1, 432 * 4 - 1), fill=255)
    card.putalpha(circ.resize((432, 432), Image.LANCZOS))
    preview.alpha_composite(card, (40, 40))
    tint = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    mono_big = mono.resize((432, 432), Image.LANCZOS)
    colored = Image.new("RGBA", (432, 432), (0x3F, 0x5A, 0x86, 255))
    colored.putalpha(mono_big.split()[3])
    tint.alpha_composite(colored)
    preview.alpha_composite(tint, (552, 40))
    out = root / "tools" / "icon_preview.png"
    preview.convert("RGB").save(out)
    print(f"预览图：{out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
