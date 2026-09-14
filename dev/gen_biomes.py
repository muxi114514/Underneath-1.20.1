# -*- coding: utf-8 -*-
# P2②④⑤：从 OTG .bc 生成 biome JSON + 矿物/血泉/植被 worldgen 数据包 + lang 合并。
# 改群系/矿物/植被认知改本生成器重跑，勿手改产出 JSON。
# 要点：①biome features 数组全局统一顺序（乱序=1.18+ feature-order-cycle 崩溃）
#      ②City 系对象(Destructor/ChestFiller/Sewer/Lattice/portal 除外)依赖 L2 城市 → P3 再接
#      ③ore targets/spring valid_blocks 是解析期硬校验：epca/defiledlands 缺席环境维度拒载（仅服务 1.70）
import os, re, json, sys, collections
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BC_DIR = r"C:\Users\ADMINI~1\AppData\Local\Temp\claude\E--EnigmaticEchoes-1-20-1\5710878f-5c04-475a-a8eb-6fa2bbe6de75\scratchpad\un_extract\assets\worldpacker\Underneath\WorldBiomes"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
WG = os.path.join(RES, "data", "underneath", "worldgen")

NAMES = {
    "Underneath":        ("underneath",        "Underneath",        "地渊"),
    "Underneath Edge":   ("underneath_edge",   "Underneath Edge",   "地渊边缘"),
    "Underneath Hole":   ("underneath_hole",   "Underneath Hole",   "地渊空洞"),
    "Underneath Pillar": ("underneath_pillar", "Underneath Pillar", "地渊石柱"),
    "Corrupted":         ("corrupted",         "Corrupted",         "腐化之地"),
    "Corrupted Edge":    ("corrupted_edge",    "Corrupted Edge",    "腐化边缘"),
    "Corrupted Pillar":  ("corrupted_pillar",  "Corrupted Pillar",  "腐化石柱"),
    "Defiled Caverns":   ("defiled_caverns",   "Defiled Caverns",   "污秽洞窟"),
    "Underforest":       ("underforest",       "Underforest",       "地底森林"),
    "Underspikes":       ("underspikes",       "Underspikes",       "地底尖刺"),
    "Underspikes Edge":  ("underspikes_edge",  "Underspikes Edge",  "尖刺边缘"),
    "Underspikes Pillar":("underspikes_pillar","Underspikes Pillar","尖刺石柱"),
    # Fromimage 系（map.png 中央遗迹区专用，L2 遗迹结构 P3 接线）
    "Fromimage/Ancient Remnants":         ("ancient_remnants",      "Ancient Remnants",      "远古遗迹"),
    "Fromimage/Ancient Remnants R-ONE":   ("ancient_remnants_r1",   "Ancient Remnants I",    "远古遗迹·壹"),
    "Fromimage/Ancient Remnants R-TWO":   ("ancient_remnants_r2",   "Ancient Remnants II",   "远古遗迹·贰"),
    "Fromimage/Ancient Remnants R-THREE": ("ancient_remnants_r3",   "Ancient Remnants III",  "远古遗迹·叁"),
    "Fromimage/Ancient Remnants R-FOUR":  ("ancient_remnants_r4",   "Ancient Remnants IV",   "远古遗迹·肆"),
    "Fromimage/Ancient Remnants R-FIVE":  ("ancient_remnants_r5",   "Ancient Remnants V",    "远古遗迹·伍"),
    "Fromimage/Remnants Edge":            ("remnants_edge",         "Remnants Edge",         "遗迹边缘"),
    "Fromimage/Remnants Wall":            ("remnants_wall",         "Remnants Wall",         "遗迹之墙"),
    "Fromimage/Remnants Outter Edge":     ("remnants_outer_edge",   "Remnants Outer Edge",   "遗迹外缘"),
}

# ---------- 1.12 → 1.20 矿物/源方块映射（拍板见 CLAUDE.md）----------
ORE_BLOCK = {
    "contenttweaker:deepslate_lead_ore":     "epca:infested_heavy_lapis",
    "contenttweaker:deepslate_thorium_ore":  "epca:infested_heavy_emerald",
    "contenttweaker:deepslate_boron_ore":    "epca:infested_heavy_iron",
    "contenttweaker:deepslate_lithium_ore":  "epca:infested_heavy_redstone",
    "quark:crystal:1":                       "quark:orange_corundum",
    "quark:crystal:7":                       "quark:white_corundum",
    "contenttweaker:deepslate_crystal_ore":  "simple_difficulty:deepslate_heart_crystal_ore",
    "scalinghealth:crystalore":              "simple_difficulty:heart_crystal_ore",
    "contenttweaker:deepslate_coal_ore":     "minecraft:deepslate_coal_ore",
    "contenttweaker:deepslate_iron_ore":     "minecraft:deepslate_iron_ore",
    "contenttweaker:deepslate_gold_ore":     "minecraft:deepslate_gold_ore",
    "contenttweaker:deepslate_redstone_ore": "minecraft:deepslate_redstone_ore",
    "contenttweaker:deepslate_diamond_ore":  "minecraft:deepslate_diamond_ore",
    "contenttweaker:deepslate_lapis_ore":    "minecraft:deepslate_lapis_ore",
    "srparasites:parasiterubbledense:1":     "epca:infested_infested_stone",
    # Remnants 系 .bc 用 1.12 裸矿名 → 按拍板统一 deepslate 系
    "stone:1":                               "minecraft:granite",
    "stone:3":                               "minecraft:diorite",
    "stone:5":                               "minecraft:andesite",
    "coal_ore":                              "minecraft:deepslate_coal_ore",
    "iron_ore":                              "minecraft:deepslate_iron_ore",
    "gold_ore":                              "minecraft:deepslate_gold_ore",
    "redstone_ore":                          "minecraft:deepslate_redstone_ore",
    "diamond_ore":                           "minecraft:deepslate_diamond_ore",
    "lapis_ore":                             "minecraft:deepslate_lapis_ore",
    "gravel":                                "minecraft:gravel",
    "defiledlands:gravel_defiled":           "defiledlands:gravel_defiled",
    "defiledlands:hephaestite_ore":          "defiledlands:hephaestite_ore",
    "defiledlands:umbrium_ore":              "defiledlands:umbrium_ore",
    "defiledlands:scarlite_ore":             "defiledlands:scarlite_ore",
}
SOURCE_BLOCK = {
    "stone":                                  "minecraft:deepslate",  # 1.12 行写 STONE 而世界石=deepslate（原版疑似死行，移植取意图）
    "eaglemixins:deepslate":                  "minecraft:deepslate",
    "srparasites:infestedrubble":             "epca:infested_cobblestone",
    "quark:basalt":                           "minecraft:smooth_basalt",
    "defiledlands:stone_defiled_decoration:3":"defiledlands:stone_defiled",
}

# ---------- 植被/对象散布表（bo_scatter）----------
# 元组第 9 位 mode（缺省 legacy）：
#   legacy        = 行级参数（P2 既有特征，观感已实测，P4 再迁移忠实模式）
#   tree          = OTG Tree() 资源忠实语义：行级 attempts/chance；Y/旋转/SourceBlocks/BlockCheck 全从对象头取
#   custom_object = OTG CustomObject() 资源：全对象头（Frequency/Rarity/Y/旋转/检查），行级参数仅注释性
# 城市系/ashen/beckon 参数=原 .bc Tree/CustomObject 行 + BO3 头逐项核对（2026-09-14 全量普查）。
DEAD_BIG = [{"name": f"dt_bpdead_big_{i:02d}", "chance": 10} for i in range(1, 21)]
DEAD_TALL = [{"name": f"dt_bpdead_tall_{i:02d}", "chance": 10} for i in range(1, 21)]
DESTRUCTOR_MAIN = [{"name": "citydestructor01", "chance": 100}] + \
                  [{"name": f"citydestructor{i:02d}", "chance": 100} for i in range(4, 9)]
VEG = {
    # id: (objects, attempts, anchor, minY, maxY, yOffset, randomRot, on_blocks[, mode])
    # ashen 灰烬石（重加·带依附检查）：头 Freq20/R100/randomY/rotate + SourceBlocks:Solid MaxOutside40 dontPlace
    # ——40% 以上悬空即整体不放，彻底解决先前"悬空/乱埋"问题
    "ashen_rocks_floor":   ([{"name": "ashen_rock_floor", "chance": 100}], 0, "random", 0, 10, 0, True, [], "custom_object"),
    "ashen_rocks_ceiling": ([{"name": "ashen_rock_ceiling", "chance": 100}], 0, "random", 100, 200, 0, True, [], "custom_object"),
    "ashen_rocks_pillar":  ([{"name": "ashen_rock_pillar", "chance": 100}], 0, "random", 8, 200, 0, True, [], "custom_object"),
    "dead_trees_big_underforest":  (DEAD_BIG, 10, "floor", 63, 80, -1, False, ["minecraft:grass_block", "minecraft:dirt"]),
    "dead_trees_tall_underforest": (DEAD_TALL, 10, "floor", 63, 80, -1, False, ["minecraft:grass_block", "minecraft:dirt"]),
    "dead_trees_big_defiled":  (DEAD_BIG, 10, "floor", 63, 80, -1, False, ["defiledlands:grass_defiled", "defiledlands:dirt_defiled"]),
    "dead_trees_tall_defiled": (DEAD_TALL, 10, "floor", 63, 80, -1, False, ["defiledlands:grass_defiled", "defiledlands:dirt_defiled"]),
    "under_stalagmites": ([{"name": "under_stalagmite_01", "chance": 50}, {"name": "under_stalagmite_02", "chance": 50},
                           {"name": "under_stalagmite_03", "chance": 100}, {"name": "basalt_speleothem", "chance": 50}],
                          50, "floor", 63, 73, 0, True, []),
    "capilarry_nets": ([{"name": f"capilarry_net_{i:02d}", "chance": 50} for i in range(2, 6)],
                       50, "floor", 60, 80, 0, False, ["epca:infested_stone"]),
    "invested_veins": ([{"name": f"invested_veins_{i:02d}", "chance": 50} for i in range(4, 7)],
                       50, "floor", 60, 80, 0, False, ["epca:infested_stone"]),
    # beckon：头 Freq1/R80/Y10-25/SourceBlocks:AIR MaxOutside0 + BlockCheck（原挂 Corrupted+Corrupted Edge）
    "beckon_spawn": ([{"name": "underneath_beckon_spawn", "chance": 100}], 0, "random", 10, 25, 0, False, [], "custom_object"),
    # 传送门废墟：用户拍板【只出现在最底层空腔】，主腔不放。portal_ruins_high 已从各群系移除(见 _STRIP)。
    # min_y 是 floor 扫描的下界(y>min_y)：腔底地板顶面≈Y15-17，下界须 ≤14 才扫得到——曾设 18 导致
    # 只有噪声凸起处命中="传送门只有几个"（三测实锤 bug）。起点仍掷 12-30 落腔内空气段。
    "portal_ruins_low":  ([{"name": "portal_overworld_low", "chance": 40}], 1, "floor", 12, 30, 0, True, []),
    "portal_ruins_high": ([{"name": "portal_overworld_high", "chance": 35}], 3, "floor", 68, 120, 0, True, []),
    # ---- 城市废墟系（原 .bc Tree 行忠实移植；对象头 SourceBlocks 保证只作用于城市楼体/路面）----
    # 02/03=路面剥蚀(SourceBlocks 灰混凝土 dontPlace)；01=全城市材料 MaxOutside0(整体嵌入楼群才放)、04-08=路面系
    "city_destructor_roads": ([{"name": "citydestructor02", "chance": 100}, {"name": "citydestructor03", "chance": 100}],
                              10, "random", 70, 255, 0, False, [], "tree"),
    "city_destructor_main":  (DESTRUCTOR_MAIN, 25, "random", 50, 255, 0, False, [], "tree"),
    "city_destructor_main_dense": (DESTRUCTOR_MAIN, 30, "random", 50, 255, 0, False, [], "tree"),  # 仅 Underneath 主群系
    "city_sewergap": ([{"name": "citysewergap", "chance": 15}], 50, "random", 50, 80, 0, True, [], "tree"),
    # 宝箱/刷怪填充件：SourceBlocks:AIR MaxOutside0(全嵌空腔) + BlockCheck 脚下城市楼板（各 Y 层分件）
    # attempts=原版 85/85/85/25 的 2 倍（2026-09-14 用户拍板"笼箱还是太少"，翻倍）
    "city_chestfiller_top":    ([{"name": "citychestfiller_top", "chance": 100}], 170, "random", 200, 255, 0, False, [], "tree"),
    "city_chestfiller_higher": ([{"name": "citychestfiller_higher", "chance": 100}], 170, "random", 150, 200, 0, False, [], "tree"),
    "city_chestfiller_upper":  ([{"name": "citychestfiller_upper", "chance": 100}], 170, "random", 80, 150, 0, False, [], "tree"),
    "city_chestfiller_lower":  ([{"name": "citychestfiller_lower", "chance": 40}], 50, "random", 45, 60, 0, False, [], "tree"),
    # City_Lattice：CustomObject 行(Underneath+Corrupted)——各件头 Freq200/R100/Y100-250 + BlockCheck
    # 脚下(Stand)/头顶(Hang)混凝土——只贴城市楼体生长
    "city_lattice": ([{"name": f"city_latticestand_{m}m", "chance": 100} for m in range(4, 13)]
                     + [{"name": f"city_latticehang_{m}m", "chance": 100} for m in range(4, 13)],
                     0, "random", 100, 250, 0, False, [], "custom_object"),
    # access_duct 已移出散布：它是 CustomStructure（穹顶顶面 highestSolidBlock 241-255），走 CityPlotter
    # 强子对撞机水晶板（Ancient Remnants 招牌地貌）：.bc 实证 Tree(50, CrystalPlate-1..16 各 100)
    # 仅挂主遗迹+R-ONE（R-TWO~FIVE 原版无此行）；对象头 randomY 84 固定=tree 模式自取
    "remnants_crystal_plates": ([{"name": f"crystalplate-{i}", "chance": 100} for i in range(1, 17)],
                                50, "random", 84, 84, 0, False, [], "tree"),
}
# 城市废墟系挂载（.bc 逐文件实证 2026-09-14）：destructor/sewergap=12 城市群系（Underneath 主群系
# destructor 主组 attempts=30 其余 25）；chestfiller=12+Remnants Outter Edge；lattice=Underneath+Corrupted
_CITY_RUIN_25 = ["city_destructor_roads", "city_destructor_main", "city_sewergap",
                 "city_chestfiller_top", "city_chestfiller_higher", "city_chestfiller_upper", "city_chestfiller_lower"]
_CITY_RUIN_30 = ["city_destructor_roads", "city_destructor_main_dense", "city_sewergap",
                 "city_chestfiller_top", "city_chestfiller_higher", "city_chestfiller_upper", "city_chestfiller_lower"]
_CHESTFILLER = ["city_chestfiller_top", "city_chestfiller_higher", "city_chestfiller_upper", "city_chestfiller_lower"]
VEG_BY_BIOME = {
    "underneath":        ["ashen_rocks_floor", "ashen_rocks_ceiling", "portal_ruins_low", "city_lattice"] + _CITY_RUIN_30,
    "corrupted":         ["ashen_rocks_floor", "ashen_rocks_ceiling", "beckon_spawn", "portal_ruins_low", "city_lattice"] + _CITY_RUIN_25,
    "underforest":       ["ashen_rocks_floor", "ashen_rocks_ceiling", "dead_trees_big_underforest", "dead_trees_tall_underforest", "portal_ruins_low"] + _CITY_RUIN_25,
    "defiled_caverns":   ["ashen_rocks_floor", "ashen_rocks_ceiling", "dead_trees_big_defiled", "dead_trees_tall_defiled", "portal_ruins_low"] + _CITY_RUIN_25,
    "underspikes":       ["ashen_rocks_floor", "ashen_rocks_ceiling", "under_stalagmites", "capilarry_nets", "invested_veins", "portal_ruins_low"] + _CITY_RUIN_25,
    "underneath_edge":   ["ashen_rocks_pillar", "portal_ruins_low"] + _CITY_RUIN_25,
    "underneath_hole":   ["ashen_rocks_floor", "ashen_rocks_ceiling", "portal_ruins_low"] + _CITY_RUIN_25,
    "underneath_pillar": ["ashen_rocks_pillar", "portal_ruins_low"] + _CITY_RUIN_25,
    "corrupted_edge":    ["ashen_rocks_pillar", "beckon_spawn", "portal_ruins_low"] + _CITY_RUIN_25,
    "corrupted_pillar":  ["ashen_rocks_pillar", "portal_ruins_low"] + _CITY_RUIN_25,
    "underspikes_edge":  ["ashen_rocks_pillar", "portal_ruins_low"] + _CITY_RUIN_25,
    "underspikes_pillar":["ashen_rocks_pillar", "portal_ruins_low"] + _CITY_RUIN_25,
    # Remnants 系（.bc 全行复核 2026-09-14）：主+R1 有水晶板 Tree 行（R2-5 无）；全系有
    # portal Tree 行；Edge/Wall 只有 Sapling 行=零散布；Outter Edge=chestfiller+pillar 无 portal
    "ancient_remnants":    ["ashen_rocks_floor", "ashen_rocks_ceiling", "remnants_crystal_plates", "portal_ruins_low"],
    "ancient_remnants_r1": ["ashen_rocks_floor", "ashen_rocks_ceiling", "remnants_crystal_plates", "portal_ruins_low"],
    "ancient_remnants_r2": ["ashen_rocks_floor", "ashen_rocks_ceiling", "portal_ruins_low"],
    "ancient_remnants_r3": ["ashen_rocks_floor", "ashen_rocks_ceiling", "portal_ruins_low"],
    "ancient_remnants_r4": ["ashen_rocks_floor", "ashen_rocks_ceiling", "portal_ruins_low"],
    "ancient_remnants_r5": ["ashen_rocks_floor", "ashen_rocks_ceiling", "portal_ruins_low"],
    "remnants_edge":       [],
    "remnants_wall":       [],
    "remnants_outer_edge": ["ashen_rocks_pillar"] + _CHESTFILLER,
}
# 全局特征顺序（同步槽内先后：所有 biome 必须按此相对顺序引用，防 feature-order-cycle）
VEG_ORDER = ["ashen_rocks_floor", "ashen_rocks_ceiling", "ashen_rocks_pillar",
             "dead_trees_big_underforest", "dead_trees_tall_underforest",
             "dead_trees_big_defiled", "dead_trees_tall_defiled",
             "under_stalagmites", "capilarry_nets", "invested_veins",
             "beckon_spawn", "remnants_crystal_plates", "portal_ruins_low", "portal_ruins_high",
             "city_destructor_roads", "city_destructor_main", "city_destructor_main_dense",
             "city_sewergap", "city_chestfiller_top", "city_chestfiller_higher",
             "city_chestfiller_upper", "city_chestfiller_lower", "city_lattice"]

# portal_ruins_high：用户拍板废弃传送门只在最底层，主腔不放（2026-09-13）。
# ashen 三件已带 OTG 依附检查（SourceBlocks:Solid MaxOutside40 dontPlace）重加（2026-09-14）。
_STRIP = {"portal_ruins_high"}
VEG_BY_BIOME = {b: [f for f in fs if f not in _STRIP] for b, fs in VEG_BY_BIOME.items()}

def hexint(s, fallback=0):
    m = re.match(r'#?([0-9a-fA-F]{6})', (s or "").strip())
    return int(m.group(1), 16) if m else fallback

def darken(c, f=0.5):
    r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    return (int(r * f) << 16) | (int(g * f) << 8) | int(b * f)

def parse_bc(path):
    d = {"ores": [], "liquids": []}
    for raw in open(path, encoding="utf-8", errors="replace"):
        line = raw.strip()
        if line.startswith("#") or not line:
            continue
        m = re.match(r'^([A-Za-z0-9]+)\s*:\s*(.*?)\s*$', line)
        if m:
            d.setdefault(m.group(1), m.group(2))
        mo = re.match(r'^Ore\(([^)]*)\)', line)
        if mo:
            d["ores"].append([p.strip() for p in mo.group(1).split(",")])
        ml = re.match(r'^Liquid\(([^)]*)\)', line)
        if ml:
            d["liquids"].append([p.strip() for p in ml.group(1).split(",")])
    return d

def write_json(relpath, obj):
    p = os.path.join(WG, relpath)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)

def ore_feature(parts):
    """Ore(block,size,freq,rarity,minY,maxY,src...) → (特征id, configured, placed)。"""
    tok = parts[0].lower()
    block = ORE_BLOCK.get(tok)
    if block is None:
        return None
    size, freq, rarity = int(parts[1]), int(parts[2]), float(parts[3])
    y0, y1 = int(parts[4]), min(int(parts[5]), 255)
    targets = []
    for s in parts[6:]:
        mapped = SOURCE_BLOCK.get(s.lower())
        if mapped and mapped not in [t["target"]["block"] for t in targets]:
            targets.append({"target": {"predicate_type": "minecraft:block_match", "block": mapped},
                            "state": {"Name": block}})
    if not targets:
        targets = [{"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:deepslate"},
                    "state": {"Name": block}}]
    count = max(1, round(freq * rarity / 100.0))
    fid = "ore_" + block.split(":")[1]
    cfg = {"type": "minecraft:ore",
           "config": {"size": size, "discard_chance_on_air_exposure": 0.0, "targets": targets}}
    placed = {"feature": "underneath:" + fid, "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range",
         "height": {"type": "minecraft:uniform",
                    "min_inclusive": {"absolute": y0}, "max_inclusive": {"absolute": y1}}},
        {"type": "minecraft:biome"}]}
    return fid, cfg, placed

def main():
    biomes = {rid: parse_bc(os.path.join(BC_DIR, fname + ".bc"))
              for fname, (rid, _, _) in NAMES.items()}

    # ---------- 矿物（全局去重 + 固定顺序）----------
    ore_registry = {}          # fid -> (cfg, placed)
    ores_by_biome = {}         # rid -> [fid...]
    for rid, bc in biomes.items():
        fids = []
        for parts in bc["ores"]:
            r = ore_feature(parts)
            if r is None:
                print("  !! 未映射矿物:", parts[0], "(", rid, ")")
                continue
            fid, cfg, placed = r
            prev = ore_registry.get(fid)
            if prev is not None and prev != (cfg, placed):
                print("  !! 同名矿物参数不一致:", fid, "(", rid, ") 沿用首见")
            ore_registry.setdefault(fid, (cfg, placed))
            if fid not in fids:
                fids.append(fid)
        ores_by_biome[rid] = fids
    ore_order = sorted(ore_registry)   # 全局固定顺序
    for fid, (cfg, placed) in ore_registry.items():
        write_json(os.path.join("configured_feature", fid + ".json"), cfg)
        write_json(os.path.join("placed_feature", fid + ".json"), placed)

    # ---------- 血泉（全群系同三带；valid_blocks 取全部源块并集）----------
    # (lo,hi,count)：底层腔 8-60；**主腔地板 64-100 count40（城市所在层，无建筑处渗血=图三观感）**；穹顶带 140-255
    springs = []
    for lo, hi, count in ((8, 60, 20), (64, 100, 40), (140, 255, 20)):
        fid = f"spring_blood_{lo}_{hi}"
        springs.append(fid)
        write_json(os.path.join("configured_feature", fid + ".json"), {
            "type": "minecraft:spring_feature",
            "config": {"state": {"Name": "underneath:blood"}, "rock_count": 4, "hole_count": 1,
                       "requires_block_below": True,
                       "valid_blocks": ["minecraft:deepslate", "minecraft:smooth_basalt",
                                        "defiledlands:stone_defiled"]}})
        write_json(os.path.join("placed_feature", fid + ".json"), {
            "feature": "underneath:" + fid, "placement": [
                {"type": "minecraft:count", "count": count},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range",
                 "height": {"type": "minecraft:uniform",
                            "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
                {"type": "minecraft:biome"}]})

    # ---------- ReplacedBlocks（OTG .bc 原行忠实：Y10-50 血抽干+全域岩浆→玄武岩，raw_generation 步）----------
    write_json(os.path.join("configured_feature", "replaced_blocks.json"),
               {"type": "underneath:replaced_blocks", "config": {}})
    write_json(os.path.join("placed_feature", "replaced_blocks.json"),
               {"feature": "underneath:replaced_blocks", "placement": []})

    # ---------- 植被/对象散布（bo_scatter）----------
    for fid, spec in VEG.items():
        objs, attempts, anchor, y0, y1, yoff, rot, on = spec[:8]
        mode = spec[8] if len(spec) > 8 else "legacy"
        cfg = {"objects": objs, "attempts": attempts, "anchor": anchor,
               "min_y": y0, "max_y": y1, "y_offset": yoff,
               "random_rotation": rot, "on_blocks": on}
        if mode != "legacy":
            cfg["mode"] = mode
        write_json(os.path.join("configured_feature", fid + ".json"),
                   {"type": "underneath:bo_scatter", "config": cfg})
        write_json(os.path.join("placed_feature", fid + ".json"), {
            "feature": "underneath:" + fid,
            "placement": [{"type": "minecraft:biome"}]})

    # ---------- biome JSON ----------
    for fname, (rid, en, zh) in NAMES.items():
        bc = biomes[rid]
        water = hexint(bc.get("WaterColor"), 0x817318)
        feats = [[] for _ in range(10)]
        feats[0] = ["underneath:replaced_blocks"]
        feats[6] = ["underneath:" + f for f in ore_order if f in ores_by_biome[rid]]
        feats[8] = ["underneath:" + f for f in springs] if bc["liquids"] else []
        feats[9] = ["underneath:" + f for f in VEG_ORDER if f in VEG_BY_BIOME[rid]]
        biome = {
            "temperature": float(bc.get("BiomeTemperature", 1.2)),
            "downfall": float(bc.get("BiomeWetness", 0.0)),
            "has_precipitation": False,
            "effects": {
                "sky_color": hexint(bc.get("SkyColor"), 0x2F0000),
                "fog_color": hexint(bc.get("FogColor"), 0x000000),
                "water_color": water,
                "water_fog_color": darken(water, 0.4),
                "grass_color": hexint(bc.get("GrassColor"), 0x340202),
                "foliage_color": hexint(bc.get("FoliageColor"), 0xA60000),
                "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000,
                               "block_search_extent": 8, "offset": 2.0}
            },
            "spawners": {"monster": [], "creature": [], "ambient": [], "axolotls": [],
                         "underground_water_creature": [], "water_creature": [], "water_ambient": [], "misc": []},
            "spawn_costs": {},
            # OTG CaveRarity7/CaveFrequency40=1.12 式洞穴开启 → 原版洞穴雕刻器（P2① dripstone 占位时曾有、换自定义群系后丢失=地形观感回退主因之一）
            # 原版 WorldConfig.ini：CaveRarity7/Freq40/CaveMinAltitude8/**CaveMaxAltitude255**（洞穴切穿穹顶
            # =破顶洞来源；vanilla cave 只到 y180 够不到穹顶）、RavineRarity0=无峡谷 → 自定义全高度 carver
            "carvers": {"air": ["underneath:cave"]},
            "features": feats
        }
        write_json(os.path.join("biome", rid + ".json"), biome)
    print("biome ×%d, 矿物 ×%d, 血泉 ×%d, 散布 ×%d" % (len(NAMES), len(ore_registry), len(springs), len(VEG)))

    # ---------- lang 合并 ----------
    for lang, col in (("en_us", 1), ("zh_cn", 2)):
        p = os.path.join(RES, "assets", "underneath", "lang", lang + ".json")
        d = json.load(open(p, encoding="utf-8")) if os.path.exists(p) else {}
        for fname, tup in NAMES.items():
            d["biome.underneath." + tup[0]] = tup[col]
        with open(p, "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=2, sort_keys=True)
    print("lang 已合并")

main()
