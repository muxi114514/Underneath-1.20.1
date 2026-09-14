# -*- coding: utf-8 -*-
# 生成 legacy_vanilla.json：1.12 Bukkit 系旧方块名(+meta) -> 1.20.1 blockstate 串
# 输入：dev/block_inventory.tsv（观测 token）+ OTG DefaultMaterial.java（真材料白名单）
# 输出：src/main/resources/data/underneath/block_map/legacy_vanilla.json
# 用法：python dev/gen_legacy_map.py   （在项目根运行或直接绝对路径运行均可）
import collections, json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TSV = os.path.join(ROOT, "dev", "block_inventory.tsv")
OTG_DM = r"E:\OpenTerrainGenerator-1.12-OTG-\common\src\main\java\com\pg85\otg\util\minecraft\defaults\DefaultMaterial.java"
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "underneath", "block_map", "legacy_vanilla.json")

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
          "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
FACING_IDX6 = {0: "down", 1: "up", 2: "north", 3: "south", 4: "west", 5: "east"}   # byIndex
HORIZ = ["south", "west", "north", "east"]     # getHorizontal(0..3)：床/南瓜/栅栏门/横幅旋转基准
STAIR_FACE = ["east", "west", "south", "north"]  # 楼梯 meta&3
DOOR_FACE = ["east", "south", "west", "north"]   # 门下半扇 meta&3
TRAP_FACE = ["north", "south", "west", "east"]   # 活板门 meta&3
HOOK_FACE = ["south", "west", "north", "east"]   # 绊线钩/中继器/比较器 meta&3
STEP_TYPES = ["smooth_stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab",
              "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab"]
WOOD_SP = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]
RAIL_SHAPE = ["north_south", "east_west", "ascending_east", "ascending_west",
              "ascending_north", "ascending_south", "south_east", "south_west",
              "north_west", "north_east"]

M = {}  # token(小写) -> 1.20.1 blockstate 串

def put(base, meta, target):
    key = base.lower() if meta is None else f"{base.lower()}:{meta}"
    M[key] = target

def simple(base, target):
    put(base, None, target)

def colored(base, fmt):
    put(base, None, fmt.format(COLORS[0]))
    for m in range(16):
        put(base, m, fmt.format(COLORS[m]))

def stairs(base, name):
    put(base, None, f"minecraft:{name}")
    for m in range(8):
        half = ",half=top" if m & 4 else ""
        put(base, m, f"minecraft:{name}[facing={STAIR_FACE[m & 3]}{half}]")

def slab(base, types, top_bit=8):
    put(base, None, f"minecraft:{types[0]}")
    for m in range(16):
        t = types[m & 7] if (m & 7) < len(types) else types[0]
        put(base, m, f"minecraft:{t}[type=top]" if m & top_bit else f"minecraft:{t}")

def double_slab(base, types):
    put(base, None, f"minecraft:{types[0]}[type=double]")
    for m in range(8):
        t = types[m & 7] if (m & 7) < len(types) else types[0]
        put(base, m, f"minecraft:{t}[type=double]")
    put(base, 8, "minecraft:smooth_stone")       # 无缝双台阶
    put(base, 9, "minecraft:smooth_sandstone")

def door(base, name):
    put(base, None, f"minecraft:{name}[half=lower,facing=east]")
    for m in range(16):
        if m < 8:
            openv = ",open=true" if m & 4 else ""
            put(base, m, f"minecraft:{name}[half=lower,facing={DOOR_FACE[m & 3]}{openv}]")
        else:
            hinge = "right" if m & 1 else "left"
            put(base, m, f"minecraft:{name}[half=upper,hinge={hinge}]")

def trapdoor(base, name):
    put(base, None, f"minecraft:{name}")
    for m in range(16):
        openv = ",open=true" if m & 4 else ""
        half = "top" if m & 8 else "bottom"
        put(base, m, f"minecraft:{name}[facing={TRAP_FACE[m & 3]},half={half}{openv}]")

def wall_facing(base, name, extra=""):
    """梯子/壁式箱炉牌类：meta 2..5 = N/S/W/E"""
    put(base, None, f"minecraft:{name}{extra}")
    for m in range(2, 6):
        put(base, m, f"minecraft:{name}[facing={FACING_IDX6[m]}]" if not extra
            else f"minecraft:{name}[facing={FACING_IDX6[m]}]")

def six_facing(base, name, mask=7, extra=""):
    """发射器/投掷器/侦测器/活塞类：meta&7 = byIndex 六向"""
    put(base, None, f"minecraft:{name}")
    for m in range(16):
        f = FACING_IDX6.get(m & mask, "north")
        put(base, m, f"minecraft:{name}[facing={f}{extra}]")

def button(base, name):
    put(base, None, f"minecraft:{name}")
    for m in range(16):
        d = m & 7
        if d == 0:
            put(base, m, f"minecraft:{name}[face=ceiling]")
        elif d == 5:
            put(base, m, f"minecraft:{name}[face=floor]")
        elif 1 <= d <= 4:
            face = {1: "east", 2: "west", 3: "south", 4: "north"}[d]
            put(base, m, f"minecraft:{name}[face=wall,facing={face}]")
        else:
            put(base, m, f"minecraft:{name}")

def variants(base, names):
    put(base, None, f"minecraft:{names[0]}")
    for i, n in enumerate(names):
        put(base, i, f"minecraft:{n}")

# ---------------- 逐族编码 ----------------
simple("AIR", "minecraft:air")
put("AIR", 0, "minecraft:air")

colored("CONCRETE", "minecraft:{}_concrete")
colored("CONCRETE_POWDER", "minecraft:{}_concrete_powder")
colored("STAINED_CLAY", "minecraft:{}_terracotta")
colored("STAINED_GLASS", "minecraft:{}_stained_glass")
colored("STAINED_GLASS_PANE", "minecraft:{}_stained_glass_pane")
colored("CARPET", "minecraft:{}_carpet")
colored("WOOL", "minecraft:{}_wool")

variants("STONE", ["stone", "granite", "polished_granite", "diorite", "polished_diorite",
                   "andesite", "polished_andesite"])
variants("DIRT", ["dirt", "coarse_dirt", "podzol"])
variants("SAND", ["sand", "red_sand"])
variants("SANDSTONE", ["sandstone", "chiseled_sandstone", "cut_sandstone"])
variants("RED_SANDSTONE", ["red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone"])
variants("SMOOTH_BRICK", ["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"])
variants("PRISMARINE", ["prismarine", "prismarine_bricks", "dark_prismarine"])
variants("COBBLE_WALL", ["cobblestone_wall", "mossy_cobblestone_wall"])
variants("QUARTZ_BLOCK", ["quartz_block", "chiseled_quartz_block", "quartz_pillar"])
put("QUARTZ_BLOCK", 3, "minecraft:quartz_pillar[axis=x]")
put("QUARTZ_BLOCK", 4, "minecraft:quartz_pillar[axis=z]")
variants("SPONGE", ["sponge", "wet_sponge"])
variants("LONG_GRASS", ["grass", "grass", "fern"])

for b, n in [("WOOD_STAIRS", "oak_stairs"), ("SPRUCE_WOOD_STAIRS", "spruce_stairs"),
             ("BIRCH_WOOD_STAIRS", "birch_stairs"), ("JUNGLE_WOOD_STAIRS", "jungle_stairs"),
             ("ACACIA_STAIRS", "acacia_stairs"), ("DARK_OAK_STAIRS", "dark_oak_stairs"),
             ("COBBLESTONE_STAIRS", "cobblestone_stairs"), ("BRICK_STAIRS", "brick_stairs"),
             ("SMOOTH_STAIRS", "stone_brick_stairs"), ("NETHER_BRICK_STAIRS", "nether_brick_stairs"),
             ("SANDSTONE_STAIRS", "sandstone_stairs"), ("RED_SANDSTONE_STAIRS", "red_sandstone_stairs"),
             ("QUARTZ_STAIRS", "quartz_stairs")]:
    stairs(b, n)

slab("STEP", STEP_TYPES)
slab("WOOD_STEP", [s + "_slab" for s in WOOD_SP])
slab("STONE_SLAB2", ["red_sandstone_slab"])
double_slab("DOUBLE_STEP", STEP_TYPES)
double_slab("WOOD_DOUBLE_STEP", [s + "_slab" for s in WOOD_SP])
# 1.12 现代 id 写法（Dregora 城市数据混用，如 minecraft:wooden_slab:5 ×4280——BlockMapper 剥前缀后落这些裸名键）
slab("wooden_slab", [s + "_slab" for s in WOOD_SP])
double_slab("double_wooden_slab", [s + "_slab" for s in WOOD_SP])
slab("stone_slab", STEP_TYPES)
slab("stone_slab2", ["red_sandstone_slab"])

# 釉陶（1.12 现代名 17 色×朝向 meta：byHorizontalIndex 0=south,1=west,2=north,3=east；silver→light_gray）
_GLZ_FACE = ["south", "west", "north", "east"]
for _c in COLORS + ["silver"]:
    _tc = ("light_gray" if _c == "silver" else _c) + "_glazed_terracotta"
    put(_c + "_glazed_terracotta", None, f"minecraft:{_tc}")
    for _m in range(4):
        put(_c + "_glazed_terracotta", _m, f"minecraft:{_tc}[facing={_GLZ_FACE[_m]}]")

for b, n in [("WOODEN_DOOR", "oak_door"), ("SPRUCE_DOOR", "spruce_door"), ("BIRCH_DOOR", "birch_door"),
             ("JUNGLE_DOOR", "jungle_door"), ("ACACIA_DOOR", "acacia_door"),
             ("DARK_OAK_DOOR", "dark_oak_door"), ("IRON_DOOR_BLOCK", "iron_door")]:
    door(b, n)

trapdoor("TRAP_DOOR", "oak_trapdoor")
trapdoor("IRON_TRAPDOOR", "iron_trapdoor")

wall_facing("LADDER", "ladder")
wall_facing("WALL_SIGN", "oak_wall_sign")
wall_facing("WALL_BANNER", "white_wall_banner")
wall_facing("CHEST", "chest")
wall_facing("TRAPPED_CHEST", "trapped_chest")
wall_facing("FURNACE", "furnace")
wall_facing("ENDER_CHEST", "ender_chest")

six_facing("DISPENSER", "dispenser")
six_facing("DROPPER", "dropper")
six_facing("OBSERVER", "observer")
six_facing("PISTON_BASE", "piston")
six_facing("PISTON_STICKY_BASE", "sticky_piston")
simple("PISTON_MOVING_PIECE", "minecraft:air")   # 移动中活塞臂：静态放置无意义
for m in (8, 9, 10, 12):
    put("PISTON_MOVING_PIECE", m, "minecraft:air")
put("HOPPER", None, "minecraft:hopper")
put("HOPPER", 0, "minecraft:hopper[facing=down]")
for m in range(2, 6):
    put("HOPPER", m, f"minecraft:hopper[facing={FACING_IDX6[m]}]")

button("STONE_BUTTON", "stone_button")
button("WOOD_BUTTON", "oak_button")

# 拉杆：EnumOrientation 0..7
put("LEVER", None, "minecraft:lever")
LEVER_MAP = {0: "[face=ceiling,facing=west]", 1: "[face=wall,facing=east]", 2: "[face=wall,facing=west]",
             3: "[face=wall,facing=south]", 4: "[face=wall,facing=north]", 5: "[face=floor,facing=south]",
             6: "[face=floor,facing=east]", 7: "[face=ceiling,facing=north]"}
for m in range(16):
    put("LEVER", m, "minecraft:lever" + LEVER_MAP[m & 7])

put("TRIPWIRE_HOOK", None, "minecraft:tripwire_hook")
for m in range(8):
    att = ",attached=true" if m & 4 else ""
    put("TRIPWIRE_HOOK", m, f"minecraft:tripwire_hook[facing={HOOK_FACE[m & 3]}{att}]")
simple("TRIPWIRE", "minecraft:tripwire")
for m in (0, 4):
    put("TRIPWIRE", m, "minecraft:tripwire")

put("RAILS", None, "minecraft:rail")
for m in range(10):
    put("RAILS", m, f"minecraft:rail[shape={RAIL_SHAPE[m]}]")
put("POWERED_RAIL", None, "minecraft:powered_rail")
for m in range(16):
    put("POWERED_RAIL", m, f"minecraft:powered_rail[shape={RAIL_SHAPE[m & 7]}]" if (m & 7) < 6
        else "minecraft:powered_rail")

for b, inv in [("DAYLIGHT_DETECTOR", ""), ("DAYLIGHT_DETECTOR_INVERTED", "inverted=true,")]:
    put(b, None, f"minecraft:daylight_detector[{inv}power=0]".replace("[power=0]", "") or "minecraft:daylight_detector")
    put(b, None, f"minecraft:daylight_detector" + (f"[{inv[:-1]}]" if inv else ""))
    for m in range(16):
        put(b, m, f"minecraft:daylight_detector[{inv}power={m}]")

# 树叶：&3 树种，恒 persistent 防结构树叶腐烂
for m in range(16):
    put("LEAVES", m, f"minecraft:{WOOD_SP[m & 3]}_leaves[persistent=true]")
    put("LEAVES_2", m, f"minecraft:{['acacia', 'dark_oak'][m & 1]}_leaves[persistent=true]")
put("LEAVES", None, "minecraft:oak_leaves[persistent=true]")
put("LEAVES_2", None, "minecraft:acacia_leaves[persistent=true]")

# 原木：&3 树种，(>>2)&3: 0=y 1=x 2=z 3=全皮(wood)
for base, sp4 in [("LOG", WOOD_SP[:4]), ("LOG_2", ["acacia", "dark_oak", "acacia", "dark_oak"])]:
    put(base, None, f"minecraft:{sp4[0]}_log")
    for m in range(16):
        sp = sp4[m & 3]
        axis = (m >> 2) & 3
        if axis == 3:
            put(base, m, f"minecraft:{sp}_wood")
        else:
            put(base, m, f"minecraft:{sp}_log[axis={['y', 'x', 'z'][axis]}]")

variants("WOOD", [s + "_planks" for s in WOOD_SP])

for b, n in [("FENCE", "oak_fence"), ("SPRUCE_FENCE", "spruce_fence"), ("BIRCH_FENCE", "birch_fence"),
             ("JUNGLE_FENCE", "jungle_fence"), ("ACACIA_FENCE", "acacia_fence"),
             ("DARK_OAK_FENCE", "dark_oak_fence"), ("NETHER_FENCE", "nether_brick_fence")]:
    simple(b, f"minecraft:{n}")
    put(b, 0, f"minecraft:{n}")

for b, n in [("FENCE_GATE", "oak_fence_gate"), ("SPRUCE_FENCE_GATE", "spruce_fence_gate"),
             ("BIRCH_FENCE_GATE", "birch_fence_gate"), ("JUNGLE_FENCE_GATE", "jungle_fence_gate"),
             ("ACACIA_FENCE_GATE", "acacia_fence_gate"), ("DARK_OAK_FENCE_GATE", "dark_oak_fence_gate")]:
    put(b, None, f"minecraft:{n}")
    for m in range(16):
        openv = ",open=true" if m & 4 else ""
        put(b, m, f"minecraft:{n}[facing={HORIZ[m & 3]}{openv}]")

# 床（1.12 颜色在 TE，默认红床）
put("BED_BLOCK", None, "minecraft:red_bed[part=foot,facing=south]")
for m in range(16):
    part = "head" if m & 8 else "foot"
    put("BED_BLOCK", m, f"minecraft:red_bed[part={part},facing={HORIZ[m & 3]}]")

put("SIGN_POST", None, "minecraft:oak_sign")
put("STANDING_BANNER", None, "minecraft:white_banner")
for m in range(16):
    put("SIGN_POST", m, f"minecraft:oak_sign[rotation={m}]")
    put("STANDING_BANNER", m, f"minecraft:white_banner[rotation={m}]")

put("SKULL", None, "minecraft:skeleton_skull")
put("SKULL", 1, "minecraft:skeleton_skull")
for m in range(2, 6):
    put("SKULL", m, f"minecraft:skeleton_wall_skull[facing={FACING_IDX6[m]}]")

put("CAULDRON", None, "minecraft:cauldron")
put("CAULDRON", 0, "minecraft:cauldron")
for m in (1, 2, 3):
    put("CAULDRON", m, f"minecraft:water_cauldron[level={m}]")

# 红石类
put("REDSTONE_WIRE", None, "minecraft:redstone_wire")
for m in range(16):
    put("REDSTONE_WIRE", m, "minecraft:redstone_wire")
simple("REDSTONE_LAMP_OFF", "minecraft:redstone_lamp")
put("REDSTONE_LAMP_OFF", 0, "minecraft:redstone_lamp")
simple("REDSTONE_LAMP_ON", "minecraft:redstone_lamp[lit=true]")
put("REDSTONE_LAMP_ON", 0, "minecraft:redstone_lamp[lit=true]")
for b, lit in [("REDSTONE_TORCH_OFF", "lit=false"), ("REDSTONE_TORCH_ON", "lit=true")]:
    put(b, None, f"minecraft:redstone_torch[{lit}]")
    put(b, 5, f"minecraft:redstone_torch[{lit}]")
    for m, f in [(1, "east"), (2, "west"), (3, "south"), (4, "north")]:
        put(b, m, f"minecraft:redstone_wall_torch[facing={f},{lit}]")
for b, powered in [("DIODE_BLOCK_OFF", ""), ("DIODE_BLOCK_ON", ",powered=true")]:
    put(b, None, "minecraft:repeater")
    for m in range(16):
        delay = ((m >> 2) & 3) + 1
        put(b, m, f"minecraft:repeater[facing={HOOK_FACE[m & 3]},delay={delay}{powered}]")
put("REDSTONE_COMPARATOR_OFF", None, "minecraft:comparator")
for m in range(16):
    put("REDSTONE_COMPARATOR_OFF", m, f"minecraft:comparator[facing={HOOK_FACE[m & 3]}]")

# 植物 / 小型
variants("RED_ROSE", ["poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                      "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"])
simple("YELLOW_FLOWER", "minecraft:dandelion")
put("YELLOW_FLOWER", 0, "minecraft:dandelion")
simple("BROWN_MUSHROOM", "minecraft:brown_mushroom")
put("BROWN_MUSHROOM", 0, "minecraft:brown_mushroom")
simple("RED_MUSHROOM", "minecraft:red_mushroom")
put("RED_MUSHROOM", 0, "minecraft:red_mushroom")
DP = ["sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"]
put("DOUBLE_PLANT", None, "minecraft:tall_grass")
for m in range(8):
    put("DOUBLE_PLANT", m, f"minecraft:{DP[m] if m < 6 else DP[0]}[half=lower]")
for m in range(8, 16):
    put("DOUBLE_PLANT", m, "minecraft:large_fern[half=upper]")
put("SAPLING", None, "minecraft:oak_sapling")
for m in range(16):
    sp = WOOD_SP[m & 7] if (m & 7) < 6 else "oak"
    put("SAPLING", m, f"minecraft:{sp}_sapling")
simple("VINE", "minecraft:vine")
for m in range(16):
    props = [p for bit, p in [(1, "south"), (2, "west"), (4, "north"), (8, "east")] if m & bit]
    put("VINE", m, "minecraft:vine" + ("[" + ",".join(f"{p}=true" for p in props) + "]" if props else ""))
simple("SNOW", "minecraft:snow")
for m in range(8):
    put("SNOW", m, f"minecraft:snow[layers={m + 1}]")
put("HAY_BLOCK", 0, "minecraft:hay_block[axis=y]")
put("HAY_BLOCK", 4, "minecraft:hay_block[axis=x]")
put("HAY_BLOCK", 8, "minecraft:hay_block[axis=z]")
simple("HAY_BLOCK", "minecraft:hay_block")
put("COCOA", None, "minecraft:cocoa[facing=north]")
put("ANVIL", None, "minecraft:anvil")
for m in range(16):
    dmg = ["anvil", "chipped_anvil", "damaged_anvil"][min((m >> 2) & 3, 2)]
    put("ANVIL", m, f"minecraft:{dmg}[facing={HORIZ[m & 3]}]")

# 流体
simple("WATER", "minecraft:water")
simple("STATIONARY_WATER", "minecraft:water")
simple("LAVA", "minecraft:lava")
simple("STATIONARY_LAVA", "minecraft:lava")
for b in ("WATER", "STATIONARY_WATER"):
    for m in range(16):
        put(b, m, "minecraft:water" if (m & 7) == 0 else f"minecraft:water[level={m & 7}]")
for b in ("LAVA", "STATIONARY_LAVA"):
    for m in range(16):
        put(b, m, "minecraft:lava" if (m & 7) == 0 else f"minecraft:lava[level={m & 7}]")

# 简单直映射
for b, t in {
    "OBSIDIAN": "obsidian", "BEDROCK": "bedrock", "COBBLESTONE": "cobblestone",
    "MOSSY_COBBLESTONE": "mossy_cobblestone", "GRAVEL": "gravel", "CLAY": "clay",
    "HARD_CLAY": "terracotta", "ICE": "ice", "PACKED_ICE": "packed_ice",
    "SNOW_BLOCK": "snow_block", "GRASS": "grass_block", "MYCEL": "mycelium",
    "NETHERRACK": "netherrack", "MAGMA": "magma_block", "SEA_LANTERN": "sea_lantern",
    "BOOKSHELF": "bookshelf", "WORKBENCH": "crafting_table", "NOTE_BLOCK": "note_block",
    "JUKEBOX": "jukebox", "BREWING_STAND": "brewing_stand", "FLOWER_POT": "flower_pot",
    "WEB": "cobweb", "DEAD_BUSH": "dead_bush", "THIN_GLASS": "glass_pane",
    "IRON_BARS": "iron_bars", "IRON_FENCE": "iron_bars", "BRICK": "bricks",
    "TNT": "tnt", "MOB_SPAWNER": "spawner", "MELON_BLOCK": "melon",
    "PUMPKIN": "carved_pumpkin", "CACTUS": "cactus", "SUGAR_CANE_BLOCK": "sugar_cane",
    "WATER_LILY": "lily_pad", "TALLGRASS": "grass",
    "IRON_PLATE": "heavy_weighted_pressure_plate", "GOLD_PLATE": "light_weighted_pressure_plate",
    "STONE_PLATE": "stone_pressure_plate", "WOOD_PLATE": "oak_pressure_plate",
    "COAL_BLOCK": "coal_block", "IRON_BLOCK": "iron_block", "GOLD_BLOCK": "gold_block",
    "LAPIS_BLOCK": "lapis_block", "REDSTONE_BLOCK": "redstone_block",
    "COAL_ORE": "coal_ore", "IRON_ORE": "iron_ore", "GOLD_ORE": "gold_ore",
    "DIAMOND_ORE": "diamond_ore", "EMERALD_ORE": "emerald_ore", "LAPIS_ORE": "lapis_ore",
    "REDSTONE_ORE": "redstone_ore",
}.items():
    simple(b, f"minecraft:{t}")
    put(b, 0, f"minecraft:{t}")

# OTG PlantType 植物别名（来自 .bc 的 Grass()/Plant() 行，P2 直接复用）
for b, t in {
    "ALLIUM": "allium", "AZUREBLUET": "azure_bluet", "BLUEORCHID": "blue_orchid",
    "BROWNMUSHROOM": "brown_mushroom", "DANDELION": "dandelion", "POPPY": "poppy",
    "DOUBLETALLGRASS": "tall_grass", "FERN": "fern", "LARGEFERN": "large_fern",
    "LILAC": "lilac", "ORANGETULIP": "orange_tulip", "OXEYEDAISY": "oxeye_daisy",
    "PEONY": "peony", "PINKTULIP": "pink_tulip", "REDMUSHROOM": "red_mushroom",
    "REDTULIP": "red_tulip", "ROSEBUSH": "rose_bush", "SUNFLOWER": "sunflower",
    "WHITETULIP": "white_tulip",
}.items():
    simple(b, f"minecraft:{t}")

# ---------------- 覆盖率校验 ----------------
dm_names = set()
with open(OTG_DM, encoding="utf-8", errors="ignore") as f:
    for ln in f:
        mm = re.match(r"^\s{4}([A-Z_0-9]+)\(", ln)
        if mm:
            dm_names.add(mm.group(1))

observed = collections.defaultdict(set)
with open(TSV, encoding="utf-8") as f:
    next(f)
    for ln in f:
        tok = ln.split("\t")[0]
        head = tok.split(":")[0]
        if head.islower():
            continue
        parts = tok.split(":")
        observed[parts[0]].add(parts[1] if len(parts) > 1 else None)

covered_bases = {k.split(":")[0].upper() for k in M}
junk, uncovered = [], []
for base, metas in sorted(observed.items()):
    if base in covered_bases:
        continue
    (junk if base not in dm_names else uncovered).append(base)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
ordered = {"__comment__": "由 dev/gen_legacy_map.py 生成：1.12 旧原版名(+meta) -> 1.20.1。手工改动请改生成器再重跑。"}
for k in sorted(M):
    ordered[k] = M[k]
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(ordered, f, ensure_ascii=False, indent=2)

print(f"entries written: {len(M)} -> {OUT}")
print(f"junk (非材料假token,已忽略) [{len(junk)}]: {', '.join(junk)}")
print(f"UNCOVERED (真材料未编码,需补!) [{len(uncovered)}]: {', '.join(uncovered)}")
