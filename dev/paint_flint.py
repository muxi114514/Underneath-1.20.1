# -*- coding: utf-8 -*-
# 血焰打火石+血焰贴图（调色管线）：
#   blood_flint_and_steel = 原版 flint_and_steel 按亮度分区调色
#       暗区(燧石)→回响碎片梯度、亮区(钢)→辛西纳石梯度（色板从两物品贴图现场采样）
#   blood_fire_0/1 = 原版火焰色相→血红（H→0 附近、V×0.88）
import io, os, json, zipfile, colorsys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "underneath", "textures")
CLIENT_JAR = r"D:\relink\114514\.minecraft\versions\supersecret1.20.1updateRL-1.70\supersecret1.20.1updateRL-1.70.jar"
BN_JAR = r"D:\relink\114514\.minecraft\versions\supersecret1.20.1updateRL-1.70\mods\BetterNether-20.0.12.jar"

def read_tex(jar, path):
    with zipfile.ZipFile(jar) as z:
        return Image.open(io.BytesIO(z.read(path))).convert("RGBA")

def read_meta(jar, path):
    with zipfile.ZipFile(jar) as z:
        try:
            return z.read(path).decode("utf-8")
        except KeyError:
            return None

def luminance(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]

def gradient_from(img, n=6):
    """从贴图采样不透明像素，按亮度排序取 n 级梯度色板。"""
    px = [p for p in img.getdata() if p[3] > 128]
    px.sort(key=luminance)
    step = max(1, len(px) // n)
    pal = []
    for i in range(n):
        seg = px[i * step:(i + 1) * step] or px[-step:]
        r = sum(p[0] for p in seg) // len(seg)
        g = sum(p[1] for p in seg) // len(seg)
        b = sum(p[2] for p in seg) // len(seg)
        pal.append((r, g, b))
    return pal

def remap(src, dark_pal, light_pal, threshold=110):
    out = Image.new("RGBA", src.size)
    dark_lums = [40, 160]     # 暗区亮度归一范围
    light_lums = [110, 255]
    for y in range(src.height):
        for x in range(src.width):
            p = src.getpixel((x, y))
            if p[3] <= 8:
                out.putpixel((x, y), (0, 0, 0, 0))
                continue
            lum = luminance(p)
            if lum < threshold:
                pal, lo, hi = dark_pal, 0, threshold
            else:
                pal, lo, hi = light_pal, threshold, 255
            t = min(max((lum - lo) / max(1, hi - lo), 0.0), 0.999)
            out.putpixel((x, y), pal[int(t * len(pal))] + (p[3],))
    return out

def save(img, rel, meta_text=None):
    path = os.path.join(TEX, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    if meta_text is not None:
        with open(path + ".mcmeta", "w", encoding="utf-8") as f:
            f.write(meta_text)
    print("painted:", rel)

# ---- 血焰打火石 ----
fas = read_tex(CLIENT_JAR, "assets/minecraft/textures/item/flint_and_steel.png")
echo = read_tex(CLIENT_JAR, "assets/minecraft/textures/item/echo_shard.png")
cinc = read_tex(BN_JAR, "assets/betternether/textures/item/cincinnasite.png")
save(remap(fas, gradient_from(echo), gradient_from(cinc)), "item/blood_flint_and_steel.png")

# ---- 血焰（火焰色相→血红）----
def bloodify(img):
    out = Image.new("RGBA", img.size)
    for y in range(img.height):
        for x in range(img.width):
            p = img.getpixel((x, y))
            if p[3] <= 8:
                out.putpixel((x, y), (0, 0, 0, 0))
                continue
            h, s, v = colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)
            h = (h * 0.15) % 1.0              # 橙黄(0.05-0.15)压向红(≈0-0.02)
            s = min(1.0, s * 1.05)
            v = v * (0.88 + 0.12 * v)         # 暗部压暗、白热芯保留（火焰层次）
            r, g, b = colorsys.hsv_to_rgb(h, s, v)
            out.putpixel((x, y), (int(r * 255), int(g * 255), int(b * 255), p[3]))
    return out

for name in ["fire_0", "fire_1"]:
    img = read_tex(CLIENT_JAR, "assets/minecraft/textures/block/%s.png" % name)
    meta = read_meta(CLIENT_JAR, "assets/minecraft/textures/block/%s.png.mcmeta" % name)
    save(bloodify(img), "block/blood_%s.png" % name, meta or json.dumps({"animation": {}}))
