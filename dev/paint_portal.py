# -*- coding: utf-8 -*-
# 深渊传送门门帘贴图（16x16x32 帧，暗红旋涡=WorldConfig PortalColor: darkred）
# 手法同 paint_textures.py（周期 value-noise），独立脚本避免触发血肉全套重画
import os, json, math
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "underneath", "textures")

# 暗红门帘色板（暗→亮），alpha 分层
PORTAL = [(24, 1, 3), (47, 2, 5), (74, 6, 9), (105, 12, 15), (140, 22, 24), (176, 40, 38)]

def hash01(ix, iy, it, seed):
    h = (ix * 374761393 + iy * 668265263 + it * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65536.0

def smooth(t):
    return t * t * (3 - 2 * t)

def vnoise(x, y, t, seed, px, py, pt):
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

def portal():
    F, S = 32, 16
    img = Image.new("RGBA", (S, S * F))
    for f in range(F):
        t = f / 8.0
        for y in range(S):
            for x in range(S):
                # 极坐标旋涡：角度随半径扭转+随时间旋转
                dx, dy = (x - 7.5) / 8.0, (y - 7.5) / 8.0
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx) + r * 2.2 - f * (2 * math.pi / F)
                sx = 2.0 + 1.5 * math.cos(a) * r * 2
                sy = 2.0 + 1.5 * math.sin(a) * r * 2
                n = vnoise(sx, sy, t, 23, 4, 4, 4)
                n2 = vnoise(x / 5.0, y / 5.0, t + 2, 57, 4, 4, 4)
                v = min(max(n * 0.7 + n2 * 0.45 - 0.08, 0.0), 0.999)
                cr, cg, cb = PORTAL[min(int(v * len(PORTAL)), len(PORTAL) - 1)]
                alpha = 165 + int(70 * v)
                img.putpixel((x, y + f * S), (cr, cg, cb, alpha))
    path = os.path.join(TEX, "block", "portal.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    with open(path + ".mcmeta", "w", encoding="utf-8") as fp:
        json.dump({"animation": {"frametime": 1}}, fp, indent=2)
    print("painted: block/portal.png (32f)")

portal()
