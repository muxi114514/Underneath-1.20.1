# -*- coding: utf-8 -*-
# 自绘血液/血肉全套贴图（替换 BOP 素材，彻底原创）：
#   blood_still(16x16x20帧) / blood_flow(32x32x16帧) / blood_underwater / blood_bucket
#   flesh / flesh_alt / porous_flesh(各 16x16x3帧脉动)
# 手法：value-noise + 色阶量化（块状有机纹理）；改色板/种子重跑即可调风格
import os, json, math, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "underneath", "textures")

# ---------------- 色板 ----------------
BLOOD = [(67, 7, 9), (90, 10, 14), (122, 16, 19), (147, 24, 28), (168, 38, 42)]   # 暗→亮
FLESH_BASE = [(94, 27, 24), (124, 40, 35), (160, 58, 52), (190, 85, 76)]
FLESH_VEIN = (196, 96, 84)
PORE = (58, 15, 13)

def hash01(ix, iy, it, seed):
    h = (ix * 374761393 + iy * 668265263 + it * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65536.0

def smooth(t):
    return t * t * (3 - 2 * t)

def vnoise(x, y, t, seed, px, py, pt):
    """周期化三维 value-noise（px/py/pt=格点周期，保证平铺+循环动画）"""
    ix, iy, it = int(math.floor(x)), int(math.floor(y)), int(math.floor(t))
    fx, fy, ft = x - ix, y - iy, t - it
    def g(dx, dy, dt):
        return hash01((ix + dx) % px, (iy + dy) % py, (it + dt) % pt, seed)
    sx, sy, st = smooth(fx), smooth(fy), smooth(ft)
    def lerp(a, b, s):
        return a + (b - a) * s
    c00 = lerp(g(0, 0, 0), g(1, 0, 0), sx)
    c10 = lerp(g(0, 1, 0), g(1, 1, 0), sx)
    c01 = lerp(g(0, 0, 1), g(1, 0, 1), sx)
    c11 = lerp(g(0, 1, 1), g(1, 1, 1), sx)
    return lerp(lerp(c00, c10, sy), lerp(c01, c11, sy), st)

def fbm(x, y, t, seed, px, py, pt, octaves=2):
    v, amp, tot = 0.0, 1.0, 0.0
    for o in range(octaves):
        m = 1 << o
        v += vnoise(x * m, y * m, t, seed + o * 7, px * m, py * m, pt) * amp
        tot += amp
        amp *= 0.5
    return v / tot

def quant(v, palette):
    i = min(int(v * len(palette)), len(palette) - 1)
    return palette[i]

def save(img, rel, mcmeta=None):
    path = os.path.join(TEX, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    if mcmeta is not None:
        with open(path + ".mcmeta", "w", encoding="utf-8") as f:
            json.dump(mcmeta, f, indent=2)
    print("painted:", rel)

# ---------------- 血液·静止（16x16 x20 帧，缓涡）----------------
def blood_still():
    F, S = 20, 16
    img = Image.new("RGB", (S, S * F))
    for f in range(F):
        for y in range(S):
            for x in range(S):
                n = fbm(x / 4.0, y / 4.0, f / 5.0, 11, 4, 4, 4, 2)
                # 轻微亮斑漂移
                n = min(max(n * 1.15 - 0.05, 0.0), 0.999)
                img.putpixel((x, y + f * S), quant(n, BLOOD))
    save(img, "block/blood_still.png", {"animation": {"frametime": 3}})

# ---------------- 血液·流动（32x32 x16 帧，向下流条纹）----------------
def blood_flow():
    F, S = 16, 32
    img = Image.new("RGB", (S, S * F))
    for f in range(F):
        for y in range(S):
            for x in range(S):
                # y 频率低于 x → 纵向拉长条纹；域随帧向下平移 → 流动
                n = fbm(x / 4.0, (y + f * 2.0) / 8.0, f / 8.0, 23, 8, 4, 2, 2)
                n = min(max(n * 1.15 - 0.05, 0.0), 0.999)
                img.putpixel((x, y + f * S), quant(n, BLOOD))
    save(img, "block/blood_flow.png", {"animation": {"frametime": 2}})

# ---------------- 水下覆盖（16x16 RGBA 暗红雾面）----------------
def blood_underwater():
    S = 16
    img = Image.new("RGBA", (S, S))
    for y in range(S):
        for x in range(S):
            n = fbm(x / 4.0, y / 4.0, 0, 31, 4, 4, 1, 2)
            r, g, b = quant(min(n * 0.9, 0.999), BLOOD)
            img.putpixel((x, y), (r, g, b, 235))
    save(img, "block/blood_underwater.png")

# ---------------- 血肉（16x16 x3 帧脉动 + 筋络）----------------
def flesh_frames(seed, pores=False):
    F, S = 3, 16
    img = Image.new("RGB", (S, S * F))
    rng = random.Random(seed)
    pore_pts = [(rng.randrange(1, 14), rng.randrange(1, 14)) for _ in range(5)] if pores else []
    for f in range(F):
        pulse = [1.0, 1.06, 0.94][f]      # 三帧亮度脉动
        for y in range(S):
            for x in range(S):
                base = fbm(x / 4.0, y / 4.0, 0, seed, 4, 4, 1, 2)
                col = quant(min(max(base * pulse, 0.0), 0.999), FLESH_BASE)
                # 筋络：低频噪声的等值线（|n-0.5| 窄带）→ 连贯蜿蜒亮纹，而非散点
                vein = vnoise(x / 4.0, y / 4.0, 0, seed + 99, 4, 4, 1)
                if abs(vein - 0.5) < 0.035:
                    col = FLESH_VEIN if f == 1 else tuple(int(c * 0.92) for c in FLESH_VEIN)
                img.putpixel((x, y + f * S), col)
        # 蚀孔（多孔血肉）：深孔 + 1px 暗边
        for (px_, py_) in pore_pts:
            for dy in range(-1, 2):
                for dx in range(-1, 2):
                    xx, yy = (px_ + dx) % S, (py_ + dy) % S
                    if abs(dx) + abs(dy) <= 1:
                        img.putpixel((xx, yy + f * S), PORE)
                    else:
                        r, g, b = img.getpixel((xx, yy + f * S))
                        img.putpixel((xx, yy + f * S), (int(r * 0.75), int(g * 0.75), int(b * 0.75)))
    return img

FLESH_MCMETA = {"animation": {"frametime": 16, "interpolate": True, "frames": [0, 1, 0, 2, 0, 1, 2, 1]}}

# ---------------- 血桶（16x16 物品，手绘像素形）----------------
BUCKET_ART = [
    "................",
    "................",
    "....hhh..hhh....",
    "...h....... h...".replace(" ", "."),
    "...h........h...",
    "..OOOOOOOOOOOO..",
    "..ObbbbbbbbbbO..",
    "..OwwwwwwwwwlO..",
    "...OwwwwwwwlO...",
    "...OwwwwwwwlO...",
    "....OwwwwwlO....",
    "....OwwwwwlO....",
    ".....OwwwlO.....",
    ".....OOOOOO.....",
    "................",
    "................",
]
BUCKET_COLORS = {"h": (120, 120, 120), "O": (70, 70, 70), "w": (150, 150, 150),
                 "l": (105, 105, 105), "b": (122, 16, 19)}

def blood_bucket():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(BUCKET_ART):
        for x, ch in enumerate(row):
            if ch in BUCKET_COLORS:
                img.putpixel((x, y), BUCKET_COLORS[ch] + (255,))
    # 血面点两粒高光
    img.putpixel((5, 6), (147, 24, 28, 255))
    img.putpixel((9, 6), (168, 38, 42, 255))
    save(img, "item/blood_bucket.png")

# ---------------- 预览拼图（8x 放大，发给用户过目）----------------
def preview():
    tiles = [
        ("blood_still 帧1", os.path.join(TEX, "block", "blood_still.png"), (0, 0, 16, 16)),
        ("blood_flow 帧1", os.path.join(TEX, "block", "blood_flow.png"), (0, 0, 32, 32)),
        ("underwater", os.path.join(TEX, "block", "blood_underwater.png"), None),
        ("flesh 帧1", os.path.join(TEX, "block", "flesh.png"), (0, 0, 16, 16)),
        ("flesh_alt 帧1", os.path.join(TEX, "block", "flesh_alt.png"), (0, 0, 16, 16)),
        ("porous 帧1", os.path.join(TEX, "block", "porous_flesh.png"), (0, 0, 16, 16)),
        ("bucket", os.path.join(TEX, "item", "blood_bucket.png"), None),
    ]
    Z = 8
    imgs = []
    for name, path, crop in tiles:
        im = Image.open(path).convert("RGBA")
        if crop:
            im = im.crop(crop)
        imgs.append(im.resize((im.width * Z, im.height * Z), Image.NEAREST))
    total_w = sum(im.width for im in imgs) + 16 * (len(imgs) + 1)
    total_h = max(im.height for im in imgs) + 40
    out = Image.new("RGBA", (total_w, total_h), (30, 30, 30, 255))
    x0 = 16
    for im in imgs:
        out.paste(im, (x0, 20), im)
        x0 += im.width + 16
    out.save(os.path.join(ROOT, "dev", "texture_preview.png"))
    print("preview -> dev/texture_preview.png")

blood_still()
blood_flow()
blood_underwater()
save(flesh_frames(41), "block/flesh.png", FLESH_MCMETA)
save(flesh_frames(87), "block/flesh_alt.png", FLESH_MCMETA)
save(flesh_frames(41, pores=True), "block/porous_flesh.png", FLESH_MCMETA)
blood_bucket()
preview()
print("全部重画完成（覆盖 BOP 素材）")
