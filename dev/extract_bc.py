# -*- coding: utf-8 -*-
# P2 起步：解析 OTG .bc 群系文件 → 地形/方块/颜色/矿物/液体/结构/刷怪 实施清单。
# 纯分析产出 dev/bc_inventory.tsv + 控制台摘要；改方块去向在 BlockMapper，不在此。
import os, re, sys, glob, collections
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BC_DIR = r"C:\Users\ADMINI~1\AppData\Local\Temp\claude\E--EnigmaticEchoes-1-20-1\5710878f-5c04-475a-a8eb-6fa2bbe6de75\scratchpad\un_extract\assets\worldpacker\Underneath\WorldBiomes"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 1.12→1.20 方块去向（与 BlockMapper 拍板一致，仅为清单标注，不产数据）
BLOCK_HINT = {
    "eaglemixins:deepslate": "minecraft:deepslate",
    "quark:basalt": "minecraft:basalt(或 smooth_basalt)",
    "srparasites:parasiterubble": "epca:infested_* 虫染碎石",
    "srparasites:infestedrubble": "epca:infested_*",
    "defiledlands:stone_defiled_decoration:3": "defiledlands:同物(包内)",
    "biomesoplenty:blood": "underneath:blood(自建流体)",
    "biomesoplenty:flesh": "underneath:flesh(自建)",
    "stationary_water": "minecraft:water",
    "stationary_lava": "minecraft:lava",
    "obsidian": "minecraft:obsidian",
    "contenttweaker:deepslate_coal_ore": "minecraft:deepslate_coal_ore",
    "contenttweaker:deepslate_iron_ore": "minecraft:deepslate_iron_ore",
    "contenttweaker:deepslate_gold_ore": "minecraft:deepslate_gold_ore",
    "contenttweaker:deepslate_redstone_ore": "minecraft:deepslate_redstone_ore",
    "contenttweaker:deepslate_diamond_ore": "minecraft:deepslate_diamond_ore",
    "contenttweaker:deepslate_lapis_ore": "minecraft:deepslate_lapis_ore",
    "contenttweaker:deepslate_thorium_ore": "epca:infested_heavy_emerald(虫染重矿)",
    "contenttweaker:deepslate_lithium_ore": "epca:infested_heavy_redstone",
    "contenttweaker:deepslate_lead_ore": "epca:infested_heavy_lapis",
    "contenttweaker:deepslate_boron_ore": "epca:infested_heavy_iron",
    "contenttweaker:deepslate_crystal_ore": "simple_difficulty:deepslate_heart_crystal_ore",
    "scalinghealth:crystalore": "simple_difficulty:heart_crystal_ore",
    "quark:crystal:1": "待定(quark 染色水晶/省略)",
}

def hint(tok):
    return BLOCK_HINT.get(tok.strip().lower(), "")

SCALAR = ["BiomeSize","BiomeRarity","BiomeColor","BiomeHeight","BiomeVolatility",
          "Volatility1","Volatility2","VolatilityWeight1","VolatilityWeight2",
          "MaxAverageHeight","MaxAverageDepth","BiomeTemperature","BiomeWetness",
          "StoneBlock","SurfaceBlock","GroundBlock","WaterBlock","IceBlock","CooledLavaBlock",
          "SkyColor","WaterColor","GrassColor","FoliageColor","FogColor","FogDensity",
          "WaterLevelMax","ReplaceToBiomeName","BiomeExtends"]

def parse_bc(path):
    d = {"_file": os.path.basename(path)[:-3]}
    lists = collections.defaultdict(list)
    for raw in open(path, encoding="utf-8", errors="replace"):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        m = re.match(r'^([A-Za-z0-9_]+)\s*:\s*(.*)$', line)
        if m and m.group(1) in SCALAR:
            d[m.group(1)] = m.group(2).strip()
            continue
        mf = re.match(r'^(Ore|Liquid|Tree|Sapling|CustomStructure|CustomObject|Boulder|Vein|UndergroundLake|Well|Grass|Plant|SurfaceAndGroundControl|ReplacedBlocks)\b\s*[:(]?(.*)$', line)
        if mf:
            lists[mf.group(1)].append(line)
    d["_lists"] = lists
    return d

def main():
    files = sorted(glob.glob(os.path.join(BC_DIR, "*.bc")))
    top = [parse_bc(f) for f in files]
    fromimg = sorted(glob.glob(os.path.join(BC_DIR, "Fromimage", "*.bc")))
    fi = [parse_bc(f) for f in fromimg]

    out = os.path.join(ROOT, "dev", "bc_inventory.tsv")
    cols = ["_file","BiomeSize","BiomeRarity","BiomeHeight","BiomeVolatility",
            "Volatility1","Volatility2","VolatilityWeight1","VolatilityWeight2",
            "MaxAverageHeight","MaxAverageDepth","StoneBlock","SurfaceBlock","GroundBlock",
            "WaterBlock","IceBlock","BiomeTemperature","BiomeWetness",
            "SkyColor","WaterColor","GrassColor","FoliageColor","FogColor","FogDensity"]
    with open(out, "w", encoding="utf-8") as f:
        f.write("\t".join(cols) + "\tOre数\tLiquid数\tTree数\tCustomStructure\n")
        for d in top + fi:
            row = [d.get(c, "") for c in cols]
            L = d["_lists"]
            row += [str(len(L["Ore"])), str(len(L["Liquid"])), str(len(L["Tree"])),
                    ";".join(re.sub(r'^CustomStructure\(', '', x)[:30] for x in L["CustomStructure"])]
            f.write("\t".join(row) + "\n")
    print("TSV →", out)

    print("\n================= 12 个顶层主群系 =================")
    for d in top:
        L = d["_lists"]
        print(f"\n## {d['_file']}  (size={d.get('BiomeSize')} rarity={d.get('BiomeRarity')} color={d.get('BiomeColor')})")
        print(f"   地形: H={d.get('BiomeHeight')} V={d.get('BiomeVolatility')} "
              f"vol1/2={d.get('Volatility1')}/{d.get('Volatility2')} "
              f"w1/2={d.get('VolatilityWeight1')}/{d.get('VolatilityWeight2')} "
              f"maxH/D={d.get('MaxAverageHeight')}/{d.get('MaxAverageDepth')}")
        print(f"   方块: Stone={d.get('StoneBlock')} Surf={d.get('SurfaceBlock')} Ground={d.get('GroundBlock')}")
        print(f"         Water={d.get('WaterBlock')} Ice={d.get('IceBlock')}")
        print(f"   颜色: Sky={d.get('SkyColor')} Fog={d.get('FogColor')}(dens={d.get('FogDensity')}) "
              f"Grass={d.get('GrassColor')} Foliage={d.get('FoliageColor')} Water={d.get('WaterColor')}")
        print(f"   温湿: T={d.get('BiomeTemperature')} W={d.get('BiomeWetness')}")
        if L["SurfaceAndGroundControl"]:
            print("   表层梯度 SurfaceAndGroundControl:")
            print("     " + L["SurfaceAndGroundControl"][0][:200])
        if L["ReplacedBlocks"]:
            print("   ReplacedBlocks: " + L["ReplacedBlocks"][0][:150])
        if L["Ore"]:
            print(f"   矿物 {len(L['Ore'])} 条:")
            for o in L["Ore"]:
                mm = re.match(r'Ore\(([^,]+),', o)
                tok = mm.group(1) if mm else o
                print(f"     {o[:70]:70s} → {hint(tok)}")
        if L["Liquid"]:
            for lq in L["Liquid"]:
                mm = re.match(r'Liquid\(([^,]+),', lq)
                print(f"   液体: {lq[:60]:60s} → {hint(mm.group(1) if mm else '')}")

    print("\n================= Fromimage 变体（map.png 分区用）=================")
    # 按去掉 R-* 后缀归组，只看是否与主群系同参
    seen = {}
    for d in fi:
        base = re.sub(r'\s+R-(ONE|TWO|THREE|FOUR|FIVE)$', '', d["_file"])
        key = (d.get("StoneBlock"), d.get("SurfaceBlock"), d.get("BiomeHeight"), d.get("SkyColor"))
        seen.setdefault(base, set()).add(key)
    for base, keys in sorted(seen.items()):
        print(f"   {base:32s} 变体参数组数={len(keys)}  Stone/Surf/H/Sky={'同' if len(keys)==1 else '异'}")

    # 汇总：全群系 distinct 方块 token（Stone/Surf/Ground/Ore/Liquid/SurfaceControl）
    print("\n================= 全 .bc 出现的 distinct 方块 token =================")
    toks = collections.Counter()
    for d in top + fi:
        for k in ("StoneBlock","SurfaceBlock","GroundBlock","WaterBlock","IceBlock","CooledLavaBlock"):
            if d.get(k):
                toks[d[k].strip().lower()] += 1
        for o in d["_lists"]["Ore"]:
            mm = re.match(r'Ore\(([^,]+),', o)
            if mm: toks[mm.group(1).strip().lower()] += 1
        for lq in d["_lists"]["Liquid"]:
            mm = re.match(r'Liquid\(([^,]+),', lq)
            if mm: toks[mm.group(1).strip().lower()] += 1
    for tok, c in toks.most_common():
        print(f"   {c:4d}  {tok:45s} → {hint(tok) or '??(需确认)'}")

main()
