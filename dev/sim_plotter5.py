# -*- coding: utf-8 -*-
"""CityPlotter 全流程复演（与 CityPlotter.java 逐字段同构：含 BO3Group 组间距、rot 传参）。

Java 侧已改为 SavedData 增量贪心（生成触发序）；本模拟器保留固定序全域贪心——
两者是同一贪心算法在不同遍历序下的实例，城市形态/密度/完整性等价（拼缝实验 A 真值即此）。
历史教训：曾用 REGION=16 光锥独立模拟拼接，实测 61% chunk 归属不一致=城市碎片化，已废。
"""
import sys, math, collections
import sim_otg_city as S

# (root, fixedRot, surfaceY, minY, maxY, freq, group, groupFreq) —— biome 全 underneath（起点/分支全过）
DEFS = [
    ("citygrid", -1, False, 50, 50, 0, None, 0),
    ("single_skyscraper_spawner_city", 0, True, 65, 75, 0, None, 0),
    ("access_duct", 0, True, 241, 255, 0, "access_duct", 10),
]

def resolve_y(surfaceY, minY, maxY):
    if not surfaceY:
        return minY
    # 近似真实地形：穹顶完整 → highest=250 → startY=251
    sy = 251
    return sy if minY <= sy <= maxY else -1

MINSIZE = {}
def min_size(root):
    if root not in MINSIZE:
        eng = S.Engine(S.JRandom(0), minimum_size=True)
        out = eng.calc(root, 0, 0, 0, 0)
        ch = set((n.cx, n.cz) for n in out[0]) if out else {(0, 0)}
        cxs = [c[0] for c in ch]; czs = [c[1] for c in ch]
        MINSIZE[root] = (-min(czs), max(cxs), max(czs), -min(cxs))
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

def try_plot(defn, cx, cz, seed, rnd, plotted, occupied, times, groups, stats, pieces_all):
    root, fr, surf, y0, y1, freq, group, gfreq = defn
    ms = min_size(root)
    sl = ms[1] + ms[3] + 1; sw = ms[0] + ms[2] + 1
    blocked = lambda a, b: (a, b) in plotted or (a, b) in occupied
    for pass_ in range(1, 5):
        left, right, top, bottom = scan_area(pass_, cx, cz, fr, sl, sw, blocked)
        aL = left + right + 1; aW = top + bottom + 1
        fNS = sl <= aL and sw <= aW
        fEW = sl <= aW and sw <= aL
        fits = (fNS or fEW) if fr < 0 else (fNS if fr in (0, 2) else fEW)
        if not fits:
            stats[root + ":nofit_p%d" % pass_] += 1
            continue
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
        ccx = bbX + math.floor((ms[3]+ms[1]+1 if rot in (0, 2) else ms[0]+ms[2]+1) / 2.0)
        ccz = bbZ + math.floor((ms[0]+ms[2]+1 if rot in (0, 2) else ms[3]+ms[1]+1) / 2.0)
        # BO3Group 组间距（Java allowedByFrequency；access_duct:10 靠它稀疏）
        if group is not None and gfreq > 0:
            hit = False
            for (gx, gz, gr) in groups.get(group, []):
                if math.floor(math.sqrt((ccx-gx)**2 + (ccz-gz)**2)) <= max(gfreq, gr):
                    hit = True; break
            if hit:
                stats[root + ":groupfreq"] += 1
                return False
        sy = resolve_y(surf, y0, y1)
        if sy >= 0:
            rng2 = S.random_for_coords(scx * 16 + 8, 0, scz * 16 + 7, seed)
            eng = S.Engine(rng2, env_occupied=lambda a, b: (a, b) in occupied)
            out = eng.calc(root, scx * 16, sy, scz * 16, rot)
            if out is not None and out[0]:
                chunks = set((n.cx, n.cz) for n in out[0])
                occupied |= chunks; plotted |= chunks
                times[root] += 1
                stats[root] += 1
                if group is not None and gfreq > 0:
                    groups.setdefault(group, []).append((ccx, ccz, gfreq))
                pieces_all.append((root, scx, scz, len(out[0]), chunks))
                return True
            stats[root + ":expandfail"] += 1
        else:
            stats[root + ":yfail"] += 1
        return False
    return False

def plot_rect(seed, min_cx, max_cx, min_cz, max_cz):
    plotted = set(); occupied = set()
    times = collections.Counter()
    stats = collections.Counter()
    groups = {}
    pieces_all = []
    for cz in range(min_cz, max_cz + 1):
        for cx in range(min_cx, max_cx + 1):
            if (cx, cz) in plotted: continue
            cands = sorted(DEFS, key=lambda d: (times[d[0]],
                    -(lambda m: (m[1]+m[3]+1)*(m[0]+m[2]+1))(min_size(d[0]))))
            rnd = S.random_for_coords(cx * 16 + 8, 1, cz * 16 + 7, seed)
            covered = False
            for d in cands:
                if try_plot(d, cx, cz, seed, rnd, plotted, occupied, times, groups, stats, pieces_all) \
                        and (cx, cz) in occupied:
                    covered = True; break
            if covered: continue
            plotted.add((cx, cz))
    return pieces_all, stats

def plot_region(seed, min_c, max_c):
    return plot_rect(seed, min_c, max_c, min_c, max_c)

if __name__ == "__main__":
    print("minimumSize:", {d[0]: min_size(d[0]) for d in DEFS})
    pieces, stats = plot_region(20260914, -40, 40)
    print("stats:", dict(stats))
    per = collections.Counter(p[0] for p in pieces)
    print("structures:", dict(per))
    cities = [p for p in pieces if p[0] == "citygrid"]
    for c in cities[:6]:
        print("  city at", c[1], c[2], "pieces", c[3])
