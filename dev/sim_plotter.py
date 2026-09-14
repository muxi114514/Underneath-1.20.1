# -*- coding: utf-8 -*-
"""CityPlotter.plotRegion 的同构复刻：验证城市群涌现分布（间距/不重叠/填缝）。"""
import sys, math, collections
import sim_otg_city as S

DEFS = [
    # (root, fixedRot(-1=random), y)  —— biome 简化全允许；spawner surfaceY 固定 66（观感验证足够）
    ("citygrid", -1, 50),
    ("single_skyscraper_spawner_city", 0, 66),
]

MINSIZE = {}
def min_size(root):
    if root not in MINSIZE:
        eng = S.Engine(S.JRandom(0), minimum_size=True)
        out = eng.calc(root, 0, 0, 0, 0)
        ch = set((n.cx, n.cz) for n in out[0]) if out else {(0, 0)}
        cxs = [c[0] for c in ch]; czs = [c[1] for c in ch]
        MINSIZE[root] = (-min(czs), max(cxs), max(czs), -min(cxs))  # top right bottom left
    return MINSIZE[root]

def x_sat(fr, span, sl, sw):
    if fr < 0: return span >= sw and span >= sl
    return span >= sw if fr in (0, 2) else span >= sl

def z_sat(fr, span, sl, sw):
    if fr < 0: return span >= sw and span >= sl
    return span >= sl if fr in (0, 2) else span >= sw

def scan_area(pass_, cx, cz, fr, sl, sw, blocked):
    left = right = top = bottom = 0
    leftF = pass_ in (2, 4); rightF = pass_ in (1, 3)
    topF = pass_ in (1, 2); bottomF = pass_ in (3, 4)
    scan = 0
    while not (leftF and rightF and topF and bottomF):
        scan += 1
        if not rightF and x_sat(fr, right + left + 1, sl, sw): rightF = True
        if not rightF:
            for i in range(-top, bottom + 1):
                if blocked(cx + scan, cz + i): rightF = True; break
            if not rightF: right += 1
        if not leftF and x_sat(fr, right + left + 1, sl, sw): leftF = True
        if not leftF:
            for i in range(-top, bottom + 1):
                if blocked(cx - scan, cz + i): leftF = True; break
            if not leftF: left += 1
        if not bottomF and z_sat(fr, bottom + top + 1, sl, sw): bottomF = True
        if not bottomF:
            for i in range(-left, right + 1):
                if blocked(cx + i, cz + scan): bottomF = True; break
            if not bottomF: bottom += 1
        if not topF and z_sat(fr, bottom + top + 1, sl, sw): topF = True
        if not topF:
            for i in range(-left, right + 1):
                if blocked(cx + i, cz - scan): topF = True; break
            if not topF: top += 1
    return left, right, top, bottom

def plot_region(seed, min_cx, max_cx, min_cz, max_cz):
    plotted = set(); occupied = set()
    times = collections.Counter()
    pieces_all = []
    stats = collections.Counter()
    for cz in range(min_cz, max_cz + 1):
        for cx in range(min_cx, max_cx + 1):
            ck = (cx, cz)
            if ck in plotted: continue
            cands = sorted(DEFS, key=lambda d: (times[d[0]],
                    -(lambda m: (m[1]+m[3]+1)*(m[0]+m[2]+1))(min_size(d[0]))))
            rnd = S.random_for_coords(cx * 16 + 8, 1, cz * 16 + 7, seed)
            covered = False
            for root, fr, y0 in cands:
                ms = min_size(root)
                sl = ms[1] + ms[3] + 1; sw = ms[0] + ms[2] + 1
                blocked = lambda a, b: (a, b) in plotted or (a, b) in occupied
                ok = False
                for pass_ in range(1, 5):
                    left, right, top, bottom = scan_area(pass_, cx, cz, fr, sl, sw, blocked)
                    aL = left + right + 1; aW = top + bottom + 1
                    fNS = sl <= aL and sw <= aW
                    fEW = sl <= aW and sw <= aL
                    fits = (fNS or fEW) if fr < 0 else (fNS if fr in (0, 2) else fEW)
                    if not fits: continue
                    if fr >= 0: rot = fr
                    else:
                        rot = 0 if rnd.next_boolean() else 2
                        if fNS and fEW: rot = rnd.next_int(4)
                        elif fEW: rot = 3 if rnd.next_boolean() else 1
                    lenR = sl if rot in (0, 2) else sw
                    widR = sw if rot in (0, 2) else sl
                    bbX = math.floor(cx - left + ((left + right + 1) / 2.0) - (lenR / 2.0))
                    if bbX > cx: bbX = cx
                    elif bbX + lenR < cx: bbX = cx - lenR + 1
                    bbZ = math.floor(cz - top + ((top + bottom + 1) / 2.0) - (widR / 2.0))
                    if bbZ > cz: bbZ = cz
                    elif bbZ + widR < cz: bbZ = cz - widR + 1
                    scx = bbX + (ms[3] if rot == 0 else ms[2] if rot == 3 else ms[1] if rot == 2 else ms[0])
                    scz = bbZ + (ms[0] if rot == 0 else ms[3] if rot == 3 else ms[2] if rot == 2 else ms[1])
                    rng2 = S.random_for_coords(scx * 16 + 8, 0, scz * 16 + 7, seed)
                    eng = S.Engine(rng2, env_occupied=lambda a, b: (a, b) in occupied)
                    out = eng.calc(root, scx * 16, y0, scz * 16, rot)
                    if out is not None and out[0]:
                        chunks = set((n.cx, n.cz) for n in out[0])
                        occupied |= chunks; plotted |= chunks
                        times[root] += 1
                        stats[root] += 1
                        pieces_all.append((root, scx, scz, rot, len(out[0]), chunks))
                        ok = True
                    else:
                        stats[root + ":fail"] += 1
                    break
                if ok and ck in occupied:
                    covered = True; break
            if not covered:
                plotted.add(ck)
    return pieces_all, stats, occupied

if __name__ == "__main__":
    seed = 20260914
    R = 48
    pieces, stats, occupied = plot_region(seed, -R, R, -R, R)
    print("stats:", dict(stats))
    cities = [p for p in pieces if p[0] == "citygrid"]
    print("%d citygrids:" % len(cities))
    for c in cities:
        print("   at chunk(%d,%d) rot=%d pieces=%d span=%d chunks" % (c[1], c[2], c[3], c[4], len(c[5])))
    # 城市间最小间距
    if len(cities) > 1:
        dmin = 1e9
        for i in range(len(cities)):
            for j in range(i + 1, len(cities)):
                d = math.hypot(cities[i][1] - cities[j][1], cities[i][2] - cities[j][2])
                dmin = min(dmin, d)
        print("min start distance between cities: %.1f chunks" % dmin)
    # 重叠校验
    seen = {}
    overlap = 0
    for p in pieces:
        for c in p[5]:
            if c in seen: overlap += 1
            seen[c] = p[0]
    print("chunk overlaps between structures:", overlap)
    # ASCII 地图（2 chunk/字符 降采样）
    city_chunks = {}
    for p in pieces:
        for c in p[5]:
            city_chunks[c] = "#" if p[0] == "citygrid" else "s"
    print("map (2ch/px, # city, s spawner, . empty):")
    for z in range(-R, R + 1, 2):
        row = ""
        for x in range(-R, R + 1, 2):
            cell = "."
            for dx in (0, 1):
                for dz in (0, 1):
                    v = city_chunks.get((x + dx, z + dz))
                    if v == "#": cell = "#"
                    elif v == "s" and cell == ".": cell = "s"
            row += cell
        print(row)
