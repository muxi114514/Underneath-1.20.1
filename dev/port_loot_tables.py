# -*- coding: utf-8 -*-
# ④c：dregora + charm 1.12 战利品表 → 1.20.1，输出到 data/underneath/loot_tables/**
# 待移植清单来自 loot_map/loot.json（单一事实源）；嵌套 loot_table 引用递归跟进
# 关键转换：物品改名 / set_data 烘焙(区间拆条) / spawn_egg+EntityTag→具体蛋 / 引用重写
import json, os, zipfile, sys, collections
import re as _re2
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "data", "underneath")
JARS = {
    "dregora": (r"D:\relink\114514\.minecraft\versions\RLCD1.1.2.b\mods\DregoraRL-3.9.jar", "assets/dregora/loot_tables/"),
    "charm": (r"D:\relink\114514\.minecraft\versions\RLCD1.1.2.b\mods\Charm-1.12.2-1.4.1.jar", "assets/charm/loot_tables/"),
}

# ---------- 1.12 物品名映射 ----------
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
          "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
DYES = ["ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye",
        "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye",
        "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"]
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]

def color_fam(fmt):
    return lambda d: fmt.format(COLORS[d & 15])

# name -> (data->newname) 函数 或 直接字符串
DATA_ITEMS = {
    "wool": color_fam("{}_wool"), "carpet": color_fam("{}_carpet"),
    "stained_glass": color_fam("{}_stained_glass"), "stained_glass_pane": color_fam("{}_stained_glass_pane"),
    "stained_hardened_clay": color_fam("{}_terracotta"),
    "concrete": color_fam("{}_concrete"), "concrete_powder": color_fam("{}_concrete_powder"),
    "bed": color_fam("{}_bed"),
    "dye": lambda d: DYES[d & 15],
    "banner": lambda d: COLORS[15 - (d & 15)] + "_banner",
    "planks": lambda d: WOODS[min(d, 5)] + "_planks",
    "sapling": lambda d: WOODS[min(d, 5)] + "_sapling",
    "log": lambda d: WOODS[min(d & 3, 3)] + "_log",
    "log2": lambda d: ["acacia", "dark_oak"][d & 1] + "_log",
    "leaves": lambda d: WOODS[d & 3] + "_leaves",
    "leaves2": lambda d: ["acacia", "dark_oak"][d & 1] + "_leaves",
    "fish": lambda d: ["cod", "salmon", "tropical_fish", "pufferfish"][min(d, 3)],
    "cooked_fish": lambda d: ["cooked_cod", "cooked_salmon"][min(d, 1)],
    "golden_apple": lambda d: "enchanted_golden_apple" if d == 1 else "golden_apple",
    "skull": lambda d: ["skeleton_skull", "wither_skeleton_skull", "zombie_head",
                        "player_head", "creeper_head", "dragon_head"][min(d, 5)],
    "stone": lambda d: ["stone", "granite", "polished_granite", "diorite",
                        "polished_diorite", "andesite", "polished_andesite"][min(d, 6)],
    "dirt": lambda d: ["dirt", "coarse_dirt", "podzol"][min(d, 2)],
    "sand": lambda d: ["sand", "red_sand"][min(d, 1)],
    "sandstone": lambda d: ["sandstone", "chiseled_sandstone", "cut_sandstone"][min(d, 2)],
    "stonebrick": lambda d: ["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
                             "chiseled_stone_bricks"][min(d, 3)],
    "prismarine": lambda d: ["prismarine", "prismarine_bricks", "dark_prismarine"][min(d, 2)],
    "quartz_block": lambda d: ["quartz_block", "chiseled_quartz_block", "quartz_pillar"][min(d, 2)],
    "cobblestone_wall": lambda d: ["cobblestone_wall", "mossy_cobblestone_wall"][min(d, 1)],
    "sponge": lambda d: ["sponge", "wet_sponge"][min(d, 1)],
    "red_flower": lambda d: ["poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                             "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"][min(d, 8)],
    "double_plant": lambda d: ["sunflower", "lilac", "tall_grass", "large_fern",
                               "rose_bush", "peony"][min(d, 5)],
    "coal": lambda d: "charcoal" if d == 1 else "coal",
}
SIMPLE_ITEMS = {
    "golden_rail": "powered_rail",
    "reeds": "sugar_cane", "web": "cobweb", "wooden_door": "oak_door", "boat": "oak_boat",
    "waterlily": "lily_pad", "deadbush": "dead_bush", "yellow_flower": "dandelion",
    "tallgrass": "fern", "grass": "grass_block", "hardened_clay": "terracotta",
    "netherbrick": "nether_brick", "nether_brick": "nether_bricks",
    "melon": "melon_slice", "melon_block": "melon", "speckled_melon": "glistering_melon_slice",
    "fireworks": "firework_rocket", "firework_charge": "firework_star",
    "chorus_fruit_popped": "popped_chorus_fruit", "noteblock": "note_block",
    "mob_spawner": "spawner", "snow": "snow_block", "snow_layer": "snow",
    "wooden_pressure_plate": "oak_pressure_plate", "wooden_button": "oak_button",
    "trapdoor": "oak_trapdoor", "fence": "oak_fence", "fence_gate": "oak_fence_gate",
    "wooden_slab": "oak_slab", "stone_slab": "smooth_stone_slab", "wooden_stairs": "oak_stairs",
    "oak_stairs": "oak_stairs", "mycelium": "mycelium", "sign": "oak_sign",
    "chorus_plant": "chorus_plant", "slime": "slime_block", "magma": "magma_block",
    "workbench": "crafting_table", "furnace": "furnace", "brick_block": "bricks",
    "wool": "white_wool", "thin_glass": "glass_pane", "glowstone_dust": "glowstone_dust",
    "book_and_quill": "writable_book", "writable_book": "writable_book",
    "empty_map": "map", "map": "map", "filled_map": "filled_map",
    "gold_horse_armor": "golden_horse_armor", "golden_horse_armor": "golden_horse_armor",
}

# 战利品表引用重写（vanilla 改名 + iceandfire 前缀，与 loot_map/loot.json 一致）
REF_FIXED = {
    "minecraft:chests/village_blacksmith": "minecraft:chests/village/village_weaponsmith",
    "quark:chests/pirate_chest": "minecraft:chests/shipwreck_treasure",
    # 原包断链（golden_loot 文件不存在，1.12 同样加载不到）→ 就近指 golden_tools
    "dregora:rlcraft/loot_bundles/golden_loot": "underneath:rlcraft/loot_bundles/golden_tools",
}
IAF = {"cyclops_cave", "fire_dragon_female_cave", "fire_dragon_male_cave",
       "ice_dragon_female_cave", "ice_dragon_male_cave", "lightning_dragon_female_cave",
       "lightning_dragon_male_cave", "fire_dragon_roost", "ice_dragon_roost", "lightning_dragon_roost"}

report_items = collections.Counter()
report_unknown = collections.Counter()
report_notes = []

# ---------- 1.70 目标包物品全集（缓存；在场同名 mod 物品直传有效）----------
PACK_MODS = r"D:\relink\114514\.minecraft\versions\supersecret1.20.1updateRL-1.70\mods"
PACK_CACHE = os.path.join(ROOT, "dev", "pack_items_1.70.txt")

def load_pack_items():
    import re
    if os.path.exists(PACK_CACHE):
        with open(PACK_CACHE, encoding="utf-8") as f:
            return set(ln.strip() for ln in f if ln.strip())
    items = set()
    for fn in os.listdir(PACK_MODS):
        if not fn.endswith(".jar"):
            continue
        try:
            z = zipfile.ZipFile(os.path.join(PACK_MODS, fn))
            for n in z.namelist():
                m = re.match(r"assets/([^/]+)/models/item/(.+)\.json$", n)
                if m:
                    items.add(f"{m.group(1)}:{m.group(2)}")
        except Exception:
            pass
    with open(PACK_CACHE, "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(items)))
    return items

PACK_ITEMS = load_pack_items()
MATERIAL_FIX = {"gold": "golden", "wood": "wooden"}

# 1.12→1.20 斯巴达武器类型改名（词序反转之外的类型名变化）
SPARTAN_TYPE_FIX = {"hammer": "battle_hammer", "mace": "flanged_mace", "staff": "quarterstaff",
                    "throwing_knife": "dagger", "throwing_axe": "tomahawk", "crossbow": "heavy_crossbow"}

def spartan_typed(ns, typ, mat):
    typ = SPARTAN_TYPE_FIX.get(typ, typ)
    mat = MATERIAL_FIX.get(mat, mat)
    if mat == "silver":
        mat = "iron"     # 1.20 斯巴达无银系 → 铁系降级
    return f"{ns}:{mat}_{typ}"

# spartanfire 1.12 → spartanfire_rlc 1.20：族名与类型名双改（八大族×24 类型 1.20 全在，精确映射）
SF_FAMILIES = [("_fire_dragonbone", "flamed_dragon_bone"), ("_ice_dragonbone", "iced_dragon_bone"),
               ("_lightning_dragonbone", "lightning_dragon_bone"), ("_dragonbone", "dragon_bone"),
               ("_desert_venom", "desert_myrmex_stinger"), ("_jungle_venom", "jungle_myrmex_stinger")]
SF_TYPE_FIX = {"hammer": "battle_hammer", "mace": "flanged_mace", "staff": "quarterstaff",
               "crossbow": "heavy_crossbow", "throwing_axe": "tomahawk"}

# 唱片替代池：vanilla 12 + 1.70 包内 mod 碟 22（排除 cataclysm 9 张 boss 专属碟，避免贬值 boss 奖励）
DISC_POOL = ["minecraft:music_disc_" + d for d in
             ["13", "cat", "blocks", "chirp", "far", "mall",
              "mellohi", "stal", "strad", "ward", "11", "wait"]] + \
            ["quark:music_disc_" + d for d in
             ["chatter", "clock", "crickets", "drips", "endermosh", "fire", "ocean", "rain", "wind"]] + \
            ["betterend:music_disc_" + d for d in
             ["ender_hollow", "endseeker", "eo_dracona", "grasping_at_stars",
              "moonlit_undercurrents", "strange_and_alien"]] + \
            ["betternether:music_disc_" + d for d in ["gloom_wisps", "gloom_woods", "gloomsculk"]] + \
            ["biomeswevegone:music_disc_" + d for d in ["better_days", "pixie_club"]] + \
            ["supplementaries:music_disc_heave_ho", "takesapillage:bastille_blues_music_disc"]

def mod_rules(ns, path):
    """规则化 mod 物品映射（spartanfire 族名/类型名改名 + spartanweaponry 类型改名 + 唱片/巨魔武器）"""
    if ns == "spartanfire":
        for suffix, family in SF_FAMILIES:
            if path.endswith(suffix):
                typ = path[: -len(suffix)]
                return f"spartanfire_rlc:{family}_{SF_TYPE_FIX.get(typ, typ)}"
        return None
    if ns == "spartanweaponry" and "_" in path:
        typ, _, mat = path.rpartition("_")
        return spartan_typed(ns, typ, mat)
    # charm/BOP 自定义唱片（rlcraft_music 捆包）→ vanilla+包内 mod 碟 34 张稳定轮换（同名恒同碟）
    if ns in ("charm", "biomesoplenty") and path.startswith("record"):
        return DISC_POOL[sum(map(ord, path)) % len(DISC_POOL)]
    # IAF 巨魔武器 1.12 点分名 → 1.20 下划线真身（troll_weapon.axe → troll_weapon_axe）
    if ns == "iceandfire" and path.startswith("troll_weapon."):
        return "iceandfire:troll_weapon_" + path[len("troll_weapon."):]
    return None

# 1.70 缺席/改名 mod 物品逐条映射（近似替代——原 id 即键，日后可细调）
MOD_ITEMS = {
    # —— 捆包重建后新暴露的一批（2026-09-14 三维校验 49 项清零）——
    "pigstep:pigstep": "minecraft:music_disc_pigstep",              # 1.20 原版自带
    "notreepunching:pickaxe/flint": "simple_difficulty:flint_pickaxe",
    "rlmixins:steel_helmet": "minecraft:iron_helmet",               # 1.70 无钢甲
    "rlmixins:steel_chestplate": "minecraft:iron_chestplate",
    "rlmixins:steel_leggings": "minecraft:iron_leggings",
    "rlmixins:steel_boots": "minecraft:iron_boots",
    # 污秽斯巴达 1.20 真身 modid=defiledspartan（词序反转，umbrium/scarlite 全系在场）
    "spartandefiled:saber_umbrium": "defiledspartan:umbrium_saber",
    "spartandefiled:halberd_umbrium": "defiledspartan:umbrium_halberd",
    "spartandefiled:spear_umbrium": "defiledspartan:umbrium_spear",
    "spartanfire:battleaxe_desert": "spartanfire_rlc:desert_myrmex_chitin_battleaxe",
    # SpartanShields 1.20.1 官方版在包内（modid 未变、命名反转），银盾等全注册（lang 实锤）
    "spartanshields:shield_basic_stone": "spartanshields:stone_basic_shield",
    "spartanshields:shield_tower_wood": "spartanshields:wooden_tower_shield",
    "spartanshields:shield_tower_gold": "spartanshields:golden_tower_shield",
    "spartanshields:shield_tower_silver": "spartanshields:silver_tower_shield",
    "spartanshields:shield_tower_obsidian": "spartanshields:obsidian_tower_shield",
    "mujmajnkraftsbettersurvival:itemgoldnunchaku": "bettersurvival:itemgoldnunchaku",
    "mujmajnkraftsbettersurvival:itemgolddagger": "bettersurvival:itemgolddagger",
    "mujmajnkraftsbettersurvival:itemgoldspear": "bettersurvival:itemgoldspear",
    "mujmajnkraftsbettersurvival:itemgoldhammer": "spartanweaponry:golden_battle_hammer",
    "mujmajnkraftsbettersurvival:itemgoldbattleaxe": "spartanweaponry:golden_battleaxe",
    # VC 物品：variedcommodities 1.20.1 移植版在包（2026-09-14 用户提供），1.12 原名全保留
    # → 全部直传（PACK_ITEMS 名单已并入 384 物品），本模组自建 41 种及其映射条目已删除。
    "mod_lavacow:bonestew": "minecraft:suspicious_stew",
    "mod_lavacow:bonesword": "minecraft:iron_sword",
    "mod_lavacow:dreamcatcher": "minecraft:string",
    "mod_lavacow:famine": "minecraft:diamond_hoe",
    "mod_lavacow:war": "minecraft:diamond_sword",
    "mod_lavacow:faminearmor_boots": "minecraft:chainmail_boots",
    "mod_lavacow:faminearmor_chestplate": "minecraft:chainmail_chestplate",
    "mod_lavacow:faminearmor_leggings": "minecraft:chainmail_leggings",
    "mod_lavacow:halo_necklace": "minecraft:gold_ingot",
    "mod_lavacow:holy_grenade": "minecraft:tnt",
    "mod_lavacow:kings_crown": "minecraft:golden_helmet",
    "mod_lavacow:moltenpan": "minecraft:iron_shovel",
    "mod_lavacow:raven_whistle": "minecraft:goat_horn",
    "mod_lavacow:reapers_scythe": "minecraft:diamond_hoe",
    "mod_lavacow:skeletonking_mace": "spartanweaponry:diamond_flanged_mace",
    "mod_lavacow:sonicbomb": "minecraft:tnt",
    "mod_lavacow:tooth_dagger": "spartanweaponry:iron_dagger",
    "mod_lavacow:undertaker_shovel": "minecraft:iron_shovel",
    "mod_lavacow:vespa_dagger": "spartanweaponry:iron_dagger",
    "mod_lavacow:zombiepiranha_item": "minecraft:cod",
    "quark:pirate_hat": "minecraft:leather_helmet",
    "quark:candle": "minecraft:candle",
    "quark:chain": "minecraft:chain",
    "notreepunching:flint_shard": "minecraft:flint",
    "notreepunching:knife/gold": "simple_difficulty:gold_knife",
    "notreepunching:mattock/gold": "simple_difficulty:gold_mattock",
    "notreepunching:pottery/bucket": "minecraft:bucket",
    "notreepunching:pottery/flower_pot": "minecraft:flower_pot",
    "notreepunching:pottery/large_vessel": "minecraft:barrel",
    "notreepunching:pottery/small_vessel": "minecraft:flower_pot",
    "notreepunching:rock/andesite": "simple_difficulty:andesite_loose_rock",
    "notreepunching:rock/basalt": "simple_difficulty:stone_loose_rock",
    "notreepunching:rock/diorite": "simple_difficulty:diorite_loose_rock",
    "notreepunching:rock/granite": "simple_difficulty:granite_loose_rock",
    "notreepunching:rock/red_sandstone": "simple_difficulty:red_sandstone_loose_rock",
    "notreepunching:rock/sandstone": "simple_difficulty:sandstone_loose_rock",
    "notreepunching:rock/stone": "simple_difficulty:stone_loose_rock",
    "bountifulbaubles:amuletcross": "bountifulcurios:amulet_cross",
    "bountifulbaubles:amuletsinempty": "bountifulcurios:amulet_sin_empty",
    "bountifulbaubles:brokenblackdragonscale": "bountifulcurios:broken_black_dragon_scale",
    "bountifulbaubles:disintegrationtablet": "bountifulcurios:disintegration_tablet",
    "bountifulbaubles:potionrecall": "bountifulcurios:potion_recall",
    "bountifulbaubles:potionwormhole": "bountifulcurios:potion_wormhole",
    "bountifulbaubles:ringoverclocking": "bountifulcurios:ring_flywheel",
    "bountifulbaubles:spectralsilt": "bountifulcurios:spectral_silt",
    "bountifulbaubles:trinketapple": "bountifulcurios:apple",
    "bountifulbaubles:trinketballoon": "bountifulcurios:balloon",
    "bountifulbaubles:trinketbezoar": "bountifulcurios:bezoar",
    "bountifulbaubles:trinketbrokenheart": "bountifulcurios:broken_heart",
    "bountifulbaubles:trinketobsidianskull": "bountifulcurios:obsidian_skull",
    "bountifulbaubles:trinketvitamins": "bountifulcurios:vitamins",
    "foodexpansion:itembacon": "foodexpansion:bacon",
    "foodexpansion:itembatwing": "foodexpansion:bat_wing",
    "foodexpansion:itembeetrootnoodles": "foodexpansion:beetroot_noodles",
    "foodexpansion:itemcactusfruit": "foodexpansion:cactus_fruit",
    "foodexpansion:itemcarrotseedsoup": "foodexpansion:carrot_seed_soup",
    "foodexpansion:itemchocolatebar": "foodexpansion:chocolate_bar",
    "foodexpansion:itemcompressedflesh": "foodexpansion:compressed_flesh",
    "foodexpansion:itemcookedbatwing": "foodexpansion:cooked_bat_wing",
    "foodexpansion:itemfriedegg": "foodexpansion:fried_egg",
    "foodexpansion:itemhorsemeat": "foodexpansion:horse_meat",
    "foodexpansion:itemjelly": "foodexpansion:jelly",
    "foodexpansion:itemparrotmeat": "foodexpansion:parrot_meat",
    "foodexpansion:itempolarbearmeat": "foodexpansion:polar_bear_meat",
    "foodexpansion:itemspidersoup": "foodexpansion:spider_soup",
    "biomesoplenty:ash": "minecraft:charcoal",
    "biomesoplenty:brown_dye": "minecraft:cocoa_beans",
    "biomesoplenty:fleshchunk": "underneath:flesh",
    "biomesoplenty:gem": "minecraft:emerald",
    "biomesoplenty:jar_empty": "minecraft:glass_bottle",
    "biomesoplenty:mud_brick": "minecraft:brick",
    "biomesoplenty:mudball": "minecraft:clay_ball",
    "biomesoplenty:shroompowder": "minecraft:brown_mushroom",
    "biomesoplenty:terrarium": "minecraft:glass",
    "rustic:barrel": "vinery:dark_cherry_barrel",
    "rustic:chili_pepper": "minecraft:sweet_berries",
    "rustic:dust_tiny_iron": "minecraft:iron_nugget",
    "rustic:fluid_bottle": "vinery:big_bottle",
    "rustic:grapes": "vinery:red_grape",
    "rustic:lantern_wood": "minecraft:lantern",
    "rustic:tomato": "minecraft:apple",
    "rustic:vase": "minecraft:flower_pot",
    "rustic:wildberries": "minecraft:sweet_berries",
    "mujmajnkraftsbettersurvival:itemsilverbattleaxe": "bettersurvival:itemsilverbattleaxe",
    "mujmajnkraftsbettersurvival:itemsilverdagger": "bettersurvival:itemsilverdagger",
    "mujmajnkraftsbettersurvival:itemsilverhammer": "bettersurvival:itemsilverhammer",
    "mujmajnkraftsbettersurvival:itemsilvernunchaku": "bettersurvival:itemsilvernunchaku",
    "mujmajnkraftsbettersurvival:itemsilverspear": "bettersurvival:itemsilverspear",
    "mujmajnkraftsbettersurvival:itemlumiumhammer": "bettersurvival:itemancientmetalhammer",
    "xat:glow_ring": "minecraft:gold_nugget",
    "xat:glowing_gem": "minecraft:amethyst_shard",
    "xat:glowing_ingot": "minecraft:gold_ingot",
    "xat:glowing_powder": "minecraft:glowstone_dust",
    "xat:mana_candy": "minecraft:sugar",
    "iceandfire:goldpile": "minecraft:gold_nugget",
    "iceandfire:silverpile": "iceandfire:silver_nugget",
    "iceandfire:jar_empty": "minecraft:glass_bottle",
    "iceandfire:troll_weapon.column": "spartanweaponry:iron_quarterstaff",
    "iceandfire:troll_weapon.hammer": "spartanweaponry:iron_battle_hammer",
    "armorunder:auto_chestplate_liner": "minecraft:leather_chestplate",
    "armorunder:auto_leggings_liner": "minecraft:leather_leggings",
    "armorunder:goopak_cool": "minecraft:snowball",
    "armorunder:goopak_heat": "minecraft:magma_cream",
    "scalinghealth:healingitem": "simple_difficulty:heart_dust",
    "scalinghealth:heartcontainer": "simple_difficulty:heart_crystal",
    "scalinghealth:heartdust": "simple_difficulty:heart_dust",
    "simpledifficulty:charcoal_filter": "simple_difficulty:charcoal_filter",
    "simpledifficulty:juice": "simple_difficulty:juice/juice_apple",
    "simpledifficulty:purified_water_bottle": "simple_difficulty:purified_water_bottle",
    "aquaculture:fish": "minecraft:cod",
    "aquaculture:loot": "aquaculture:neptunes_bounty",
    "charm:charged_emerald": "minecraft:emerald",
    "charm:suspicious_soup": "minecraft:suspicious_stew",
    "qualitytools:emerald_amulet": "minecraft:emerald",
    "qualitytools:emerald_ring": "minecraft:emerald",
    "roughtweaks:medikit": "roughtweaks:medkit",
    "roughtweaks:medikitenchanted": "roughtweaks:medkit_enchanted",
    "advanced-fishing:fish": "minecraft:cod",
    "contenttweaker:bone_pile": "minecraft:bone_block",
    "antiqueatlas:empty_antique_atlas": "minecraft:map",
    "comforts:rope": "minecraft:lead",
    "craftablechainmail:chainmail_plate": "minecraft:iron_ingot",
    "potionfingers:ring": "minecraft:gold_nugget",
    "baubles:ring": "minecraft:gold_nugget",
    "astikorcarts:wheel": "minecraft:iron_ingot",
    "spartanshields:shield_basic_gold": "spartanshields:golden_basic_shield",
    "dynamictrees:dirtbucket": "minecraft:dirt",
    "levelup2:surfaceore": "minecraft:iron_ore",
    "wearablebackpacks:backpack": "minecraft:chest",
    "inspirations:books": "minecraft:book",
    "classyhats:hat": "minecraft:leather_helmet",
}

# quark 彩花盆 16 色（1.20 Quark 已删）→ 原版花盆
for _c in ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
           "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"]:
    MOD_ITEMS[f"quark:colored_flowerpot_{_c}"] = "minecraft:flower_pot"

# 名单陈旧覆盖：pack_items_1.70.txt 里有、但当前包运行时实际未注册的物品（config 裁剪/mod 改版，游戏日志实锤）。
# 必须置于 PACK_ITEMS 直传之前，且对映射产物也要过一遍。
STALE_FIX = {
    "quark:root": "minecraft:hanging_roots",                                     # Quark-4.0 已删
    "simple_difficulty:purified_water_bottle": "simple_difficulty:purified_water_bucket",
    "vinery:big_bottle": "vinery:wine_bottle",                                   # letsdo-vinery 1.4.41 改名
    "spartanweaponry:quiver_arrow": "spartanweaponry:small_arrow_quiver",        # 1.20 箭袋分级化
}


def map_mod_item(raw, ns, path):
    """mod 物品映射总入口：陈旧覆盖→直传→斯巴达词序→规则→逐条表；产物再过名单校验"""
    if raw in STALE_FIX:
        return STALE_FIX[raw]
    if raw in PACK_ITEMS:
        return raw
    cand = spartan_reorder(ns, path) or mod_rules(ns, path) or MOD_ITEMS.get(raw)
    if cand is None:
        return None
    cand = STALE_FIX.get(cand, cand)
    cns = cand.split(":")[0]
    if cns not in ("minecraft", "underneath") and cand not in PACK_ITEMS:
        report_notes.append(f"!! 映射产物无效(不在1.70名单): {raw} -> {cand}")
        return None
    return cand

def spartan_reorder(ns, path):
    """1.12 <类型>_<材质> → 1.20 <材质>_<类型>（斯巴达系词序反转），撞包内名单验证"""
    if "_" not in path:
        return None
    for cut in range(len(path)):
        if path[cut] != "_":
            continue
        typ, mat = path[:cut], path[cut + 1:]
        cand = f"{ns}:{MATERIAL_FIX.get(mat, mat)}_{typ}"
        if cand in PACK_ITEMS:
            return cand
    return None

def map_ref(ref, queue):
    ns, _, path = ref.partition(":")
    if ref in REF_FIXED:
        return REF_FIXED[ref]
    # rlcraft/loot_bundles/** 截断替代已撤销（2026-09-14 用户"8x6 箱太穷"）：
    # 捆包与 loot/loot_bundles/** 同管线整体移植，武器海物品走既有映射链（MOD_ITEMS/斯巴达/1.70 对撞）
    if ns in ("dregora", "charm"):
        queue.add(ref)
        return "underneath:" + (("charm/" + path) if ns == "charm" else path)
    if ref in REF_FIXED:
        return REF_FIXED[ref]
    if ns == "iceandfire" and path in IAF:
        return "iceandfire:chest/" + path
    return ref

# 1.12 驼峰属性名 → 1.16+ 蛇形（set_attributes 解析失败=整表报废，全空箱主因）
ATTR_FIX = {
    "generic.armorToughness": "generic.armor_toughness",
    "generic.attackDamage": "generic.attack_damage",
    "generic.attackKnockback": "generic.attack_knockback",
    "generic.attackSpeed": "generic.attack_speed",
    "generic.followRange": "generic.follow_range",
    "generic.knockbackResistance": "generic.knockback_resistance",
    "generic.maxHealth": "generic.max_health",
    "generic.movementSpeed": "generic.movement_speed",
    # 1.12 Forge 触及距离（大写 D 非法 ResourceLocation，demonic_staff 整表阵亡主因）→ 1.20.1 Forge 攻击触及
    "generic.reachDistance": "forge:entity_reach",
    # 1.70 无对应属性：potioncore.digSpeed 还是非法 ResourceLocation（大写 S），
    # 留着会让 SetAttributesFunction 解析失败 → 整张 unique_weapons_rare 变空箱。None=丢弃该 modifier。
    "potioncore.digSpeed": None,
}

# ---------- 断链「药水类型」重映射（武器涂药 / 药水物品共用）----------
# 机制：更好生存 CommonEventHandler#onLivingAttack 读武器 {Potion:"<药水类型>",remainingPotionHits:N}，
#       用 PotionUtils.getPotion 按【药水类型】(BuiltInRegistries.POTION) 解析后 potion.getEffects() 逐条施加。
#       故目标必须是 1.70 真实注册的【药水类型】（不是 MobEffect，且 CustomPotionEffects 不算数）。
# 目标已逐一核实存在：simple_difficulty 的 burst(爆炸)/launch/lightning/fire/random_teleport/recoil；
#                    bettersurvival 的 blindness/milk；其余落原版药水。
POTION_MAP = {
    # PotionCore（1.70 无此 mod）→ Simple Difficulty Reforge 同义药水
    "potioncore:explode": "simple_difficulty:burst",             # 惠惠法杖：命中爆炸
    "potioncore:launch": "simple_difficulty:launch",             # 永恒嘟嘟：击飞
    "potioncore:lightning": "simple_difficulty:lightning",       # Keraunos/Mjolnir：落雷
    "potioncore:fire": "simple_difficulty:fire",                 # 点燃
    "potioncore:strong_fire": "simple_difficulty:fire",          # 无强化版 → 普通 fire
    "potioncore:strong_teleport": "simple_difficulty:random_teleport",
    # srparasites（→EPCA，但无同义药水）→ 就近原版/SD
    "srparasites:viral": "minecraft:poison",                     # 寄生感染 → 中毒
    "srparasites:repel": "simple_difficulty:recoil",             # 排斥 → 反震（击退攻击者）
    # 缺席 mod 的效果
    "mod_lavacow:soiled": "minecraft:slowness",                  # 污秽 → 缓慢
    "lycanitesmobs:smited": "simple_difficulty:fire",            # 熔烧(Melt'exun 火焰主题)；lycanites0.1.0-alpha 无药水类型
    # BetterSurvival 旧命名空间 → 1.70 MX 移植版命名空间
    "mujmajnkraftsbettersurvival:milk": "bettersurvival:milk",
    # 原版从未注册致盲药水 → 用 BetterSurvival 的 blindness 药水（Heaven's Blade 致盲本就一直失效）
    "minecraft:blindness": "bettersurvival:blindness",
    # xat=Trinkets and Baubles「矮人变身药水」，1.70 无种族系统无对应 → 任意无害替代（矮人=强壮矿工）
    "xat:dwarf": "minecraft:strength",
    "xat:extended_dwarf": "minecraft:long_strength",
}


def map_potion(pid):
    """断链药水 id → 1.70 可用药水类型；未收录的原样放行（原版有效药水不受影响）。"""
    return POTION_MAP.get(pid, pid)


# 1.12 数字附魔 id → 注册名（隐藏附魔载体 ench:[{id:N,lvl:M}]，1.20 只认 Enchantments 字符串名，不转=静默丢失）
ENCH_NUMERIC_112 = {
    0: "protection", 1: "fire_protection", 2: "feather_falling", 3: "blast_protection",
    4: "projectile_protection", 5: "respiration", 6: "aqua_affinity", 7: "thorns",
    8: "depth_strider", 9: "frost_walker", 10: "binding_curse",
    16: "sharpness", 17: "smite", 18: "bane_of_arthropods", 19: "knockback",
    20: "fire_aspect", 21: "looting", 22: "sweeping",
    32: "efficiency", 33: "silk_touch", 34: "unbreaking", 35: "fortune",
    48: "power", 49: "punch", 50: "flame", 51: "infinity",
    61: "luck_of_the_sea", 62: "lure", 70: "mending", 71: "vanishing_curse",
}


# ---------- 彩蛋武器全家桶箱（2026-09-13 二次拍板：epic/rare 原表复原 Dregora 分布）----------
# 从转换后的 unique_weapons_epic 派生：每个条目独立成 rolls=1 的池 → 一箱必出全部 21 件史诗彩蛋武器。
# 取用：/setblock ~ ~ ~ chest{LootTable:"underneath:loot/unique_loot_epic/all_unique_weapons"}
def write_all_unique(table):
    pools = [{"rolls": 1, "entries": [e]}
             for pool in table.get("pools", []) for e in pool.get("entries", [])]
    dst = os.path.join(RES, "loot_tables", "loot", "unique_loot_epic", "all_unique_weapons.json")
    with open(dst, "w", encoding="utf-8") as f:
        json.dump({"type": "minecraft:chest", "pools": pools}, f, ensure_ascii=False, indent=2)

# ---------- 附魔统一映射（rlmixins 转换 / set_nbt / enchant_randomly 三处共用）----------
def _load_jlme():
    """SME 连写名 → jlme 蛇形名 反查表（运行时从 jlme jar lang 构建）"""
    try:
        z = zipfile.ZipFile(os.path.join(PACK_MODS, "jlme-1.20.1forge-ver1.9b-all.jar"))
        d = json.loads(z.read("assets/jlme/lang/en_us.json"))
        out = {}
        for k in d:
            if k.startswith("enchantment.jlme."):
                name = k.split(".")[2]
                out.setdefault(name.replace("_", ""), name)
        return out
    except Exception:
        return {}

JLME_FLAT = _load_jlme()

# SME 在 jlme 无同构名的手工语义配对（None=无合适对应，丢弃该附魔）
ENCH_MANUAL = {
    "somanyenchantments:advancedknockback": "minecraft:knockback",
    "somanyenchantments:advancedpunch": "minecraft:punch",
    "somanyenchantments:atomicdeconstructor": "jlme:executioner",
    "somanyenchantments:blessededge": "jlme:advanced_smite",
    "somanyenchantments:brutality": "jlme:brutal_hits",
    "somanyenchantments:butchering": "jlme:butcher",
    "somanyenchantments:disorientatingblade": "jlme:crippling_edge",
    "somanyenchantments:fieryedge": "jlme:advanced_fire_aspect",
    "somanyenchantments:inhumane": "minecraft:smite",
    "somanyenchantments:innerberserk": "jlme:double_edge",
    "somanyenchantments:lessersmite": "minecraft:smite",
    "somanyenchantments:lunasblessing": "minecraft:sharpness",
    "somanyenchantments:solsblessing": "minecraft:sharpness",
    "somanyenchantments:thunderstormsbestowment": "minecraft:sharpness",
    "somanyenchantments:magmawalker": "jlme:lava_strider",
    "somanyenchantments:meltdown": "jlme:heating",
    "somanyenchantments:penetratingedge": "jlme:armor_piercing",
    "somanyenchantments:physicalprotection": "jlme:advanced_protection",
    "somanyenchantments:reinforcedsharpness": "jlme:advanced_sharpness",
    "somanyenchantments:rune_arrowpiercing": "jlme:arrow_piercing",
    "somanyenchantments:rune_piercingcapabilities": "jlme:piercing_capabilities",
    "somanyenchantments:strengthenedvitality": "jlme:vitality",
    "somanyenchantments:subjectbiology": "jlme:viper",
    "somanyenchantments:subjectphysics": "minecraft:knockback",
    "somanyenchantments:supremebaneofarthropods": "jlme:supreme_ba",
    "somanyenchantments:truestrike": "jlme:critical_strike",
    "somanyenchantments:unsheathing": "jlme:lethal_cadence",
    "somanyenchantments:curseofvulnerability": "jlme:vulnerability",
    "somanyenchantments:curseofholding": "minecraft:binding_curse",
    "somanyenchantments:pandorascurse": "minecraft:vanishing_curse",
    "charm:clumsiness_curse": "minecraft:binding_curse",
    "charm:harming_curse": "minecraft:vanishing_curse",
    # —— 2026-09-13 全量注册名校验补漏（enchant_randomly 解析期硬校验，一个未知 id 废整张表）——
    # 1.20 bettersurvival(MX port) 没有的三个 1.12 附魔
    "bettersurvival:blast": "minecraft:knockback",            # 爆炸冲击→击退（爆裂观感由 burst 涂药承担）
    "bettersurvival:penetration": "jlme:armor_piercing",
    "bettersurvival:smelting": "jlme:heating",
    "bettersurvival:fling": "minecraft:knockback",
    "charm:homing": "minecraft:loyalty",                      # 1.70 无 charm；投掷回归→忠诚
    "defiledlands:destructive": "minecraft:sharpness",        # 1.20 DefiledLands 港版无附魔
    "defiledlands:sharpshooter": "minecraft:power",
    "simpledifficulty:heating": "jlme:heating",               # 正确 modid 是 simple_difficulty 且其无附魔
    # 1.20 SpartanWeaponry(RLC版) 仅注册 razors_edge 系
    "spartanweaponry:incendiary": "jlme:advanced_fire_aspect",
    "spartanweaponry:return": "minecraft:loyalty",
    "spartanweaponry:propulsion": "minecraft:power",
    "spartanweaponry:rapid_load": "minecraft:quick_charge",
    "spartanweaponry:supercharge": "minecraft:power",
}

def map_enchant(ench_id):
    """附魔 id → 1.70 可用 id；None=丢弃。minecraft/在场 ns 原样放行。"""
    ns, _, name = ench_id.partition(":")
    if ns == "mujmajnkraftsbettersurvival":
        # 先换 ns 再落入 ENCH_MANUAL 查表——1.20 港版缺的 blast/penetration/smelting 需二次映射，不能早返回
        ench_id = "bettersurvival:" + name
    if ench_id in ENCH_MANUAL:
        return ENCH_MANUAL[ench_id]
    if ns == "somanyenchantments":
        snake = JLME_FLAT.get(name.replace("_", ""))
        if snake:
            return "jlme:" + snake
        report_notes.append(f"附魔无对应已丢弃: {ench_id}")
        return None
    if ns == "potioncore":
        return None
    return ench_id

def clean_functions(entry):
    """所有 item entry 出口统一清洗：一个 1.20 不认的函数会废掉整张表"""
    fns = entry.get("functions")
    if not fns:
        return
    out = []
    for fn in fns:
        name = fn.get("function", "").replace("minecraft:", "")
        if name == "set_data":
            continue                      # meta 已烘进物品名，剥掉
        if name == "set_nbt":
            tag = fn.get("tag", "")
            # 涂药/药水物品的 Potion 类型：断链 id 重映射到 1.70 真实注册的药水类型（见 POTION_MAP）
            tag = _re2.sub(r'(Potion:\s*)(["\'])([a-z0-9_]+:[a-z0-9_]+)\2',
                           lambda m: m.group(1) + m.group(2) + map_potion(m.group(3)) + m.group(2),
                           tag)
            # 1.12 数字附魔 ench:[...] → Enchantments:[...]（id:100 等模组动态 id 无对应→丢弃并报告）
            def _ench_numeric(m):
                out12 = []
                for mm in _re2.finditer(r'\{id:(\d+),\s*lvl:(\d+)s?\}', m.group(1)):
                    nm = ENCH_NUMERIC_112.get(int(mm.group(1)))
                    if nm is None:
                        report_notes.append(f"1.12 数字附魔 id={mm.group(1)} 非原版无法对应已丢弃")
                        continue
                    out12.append('{id:"minecraft:%s",lvl:%ss}' % (nm, mm.group(2)))
                return "Enchantments:[" + ",".join(out12) + "]"
            tag = _re2.sub(r'\bench:\[([^\]]*)\]', _ench_numeric, tag)
            # CustomPotionEffects 数字 Id 1~27=原版跨版本一致原样保留；>27=1.12 模组动态 id 指错对象→丢弃该条
            def _cpe(m):
                ents = _re2.findall(r'\{[^{}]*\}', m.group(1))
                kept = []
                for e in ents:
                    mid = _re2.search(r'\bId:(\d+)', e)
                    if mid and int(mid.group(1)) > 27:
                        report_notes.append(f"CustomPotionEffects 模组数字效果 Id={mid.group(1)} 已丢弃")
                        continue
                    kept.append(e)
                return "CustomPotionEffects:[" + ",".join(kept) + "]"
            tag = _re2.sub(r'CustomPotionEffects:\[([^\]]*)\]', _cpe, tag)
            # 1.70 无 rlmixins 自定义药水 → 近似浓再生（保留风味名）
            tag = tag.replace("rlmixins:curse_break", "minecraft:strong_regeneration")
            # 1.20 无效字段 LocName/LocLore 清除（注意 LocLore 必须先于 Lore 转换删掉）
            tag = _re2.sub(r',?\s*LocName:"[^"]*"', "", tag)
            tag = _re2.sub(r',?\s*LocLore:\[[^\]]*\]', "", tag)
            # 1.12 纯文本 display.Name/CustomName → 1.20 JSON 文本（否则风味名全丢）
            # 文本进单引号 SNBT 字符串必须转义内嵌撇号——"Megumin's" 之类会提前截断字符串废整表（37 表实锤主因）
            tag = _re2.sub(r'((?:Custom)?Name):"([^"]*)"',
                           lambda m: "%s:'{\"text\":\"%s\"}'"
                                     % (m.group(1), m.group(2).replace('"', '').replace("'", "\\'")),
                           tag)
            # Lore 数组项同样 JSON 化
            def _lore(m):
                parts = _re2.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
                return "Lore:[" + ",".join("'{\"text\":\"%s\"}'" % p.replace('"', '').replace("'", "\\'")
                                           for p in parts) + "]"
            tag = _re2.sub(r'Lore:\[([^\]]*)\]', _lore, tag)
            # 附魔清洗：统一走 map_enchant（jlme 迁移/近似配对/无对应移除）
            def _ench(m):
                mapped = map_enchant(m.group(1))
                return "" if mapped is None else '{id:"%s",lvl:%ss}' % (mapped, m.group(2))
            tag = _re2.sub(r'\{id:"([a-z_]+:[a-z_.]+)",\s*lvl:(\d+)s?\}', _ench, tag)
            # LocName 位于 display 首键时，删除后残留前导/尾随逗号（{,Lore=骨头表主因）一并清扫
            tag = (tag.replace(",,", ",").replace("[,", "[").replace(",]", "]")
                      .replace("{,", "{").replace(",}", "}"))
            fn["tag"] = tag
        if name == "enchant_randomly" and "enchantments" in fn:
            # 统一走 map_enchant；剔空则去字段（=vanilla 全随机，保留"有附魔"意图）
            keep = [m for m in (map_enchant(e) for e in fn["enchantments"]) if m is not None]
            if keep:
                fn["enchantments"] = keep
            else:
                fn.pop("enchantments", None)
        if name == "set_attributes":
            mods = []
            for mod in fn.get("modifiers", []):
                a = mod.get("attribute")
                if a in ATTR_FIX:
                    repl = ATTR_FIX[a]
                    if repl is None:
                        continue          # 非法/无对应属性（如 potioncore.digSpeed）→ 丢弃该 modifier
                    mod["attribute"] = repl
                mods.append(mod)
            if not mods:
                continue                  # 全部 modifier 被丢 → 整个 set_attributes 函数丢弃（防非法项废整表）
            fn["modifiers"] = mods
        elif fn.get("function") == "rlmixins:enchant_specific":
            # 1.70 无 rlmixins：定附魔+定等级 → 等价 set_nbt Enchantments
            # 注意：附魔 id 必须在此先过 map_enchant——本分支的产物不会再被本循环的 set_nbt 分支清洗！
            ench = map_enchant(fn.get("enchantment", "minecraft:sharpness"))
            if ench is None:
                continue    # 无对应附魔 → 丢弃该函数
            lvl = int(fn.get("levels", 1))
            fn = {"function": "minecraft:set_nbt",
                  "tag": "{Enchantments:[{id:\"%s\",lvl:%ds}]}" % (ench, lvl)}
        out.append(fn)
    # —— 合并多条无条件 set_nbt {Enchantments:[...]} 为一条 ——
    # 原理：1.20 SetNbtFunction 走 CompoundTag.merge，列表键是整体覆盖不是追加，
    # 多条各带一个附魔时只有最后一条存活；且若排在 enchant_randomly 之后还会抹掉随机附魔。
    # 故：抽出全部纯 Enchantments 条目合成一条（按 id 去重取高等级），插到
    # 首个原位置与首个 enchant_randomly 之前（set_nbt 先落、随机附魔后续追加，两不相伤）。
    kept, chunks, first_idx, era_idx = [], [], None, None
    for fn in out:
        nm = fn.get("function", "").replace("minecraft:", "")
        if nm == "set_nbt" and "conditions" not in fn:
            m = _re2.fullmatch(r'\{Enchantments:\[(.*)\]\}', fn.get("tag", "").strip(), _re2.S)
            if m:
                if first_idx is None:
                    first_idx = len(kept)
                chunks.append(m.group(1))
                continue
        if nm == "enchant_randomly" and era_idx is None:
            era_idx = len(kept)
        kept.append(fn)
    if chunks:
        seen = {}
        for c in chunks:
            for m in _re2.finditer(r'\{id:"([^"]+)",\s*lvl:(\d+)s?\}', c):
                eid, lvl = m.group(1), int(m.group(2))
                if lvl > seen.get(eid, -1):
                    seen[eid] = max(lvl, seen.get(eid, 0))
        merged = {"function": "minecraft:set_nbt",
                  "tag": "{Enchantments:[%s]}" % ",".join('{id:"%s",lvl:%ds}' % kv for kv in seen.items())}
        pos = first_idx if era_idx is None else min(first_idx, era_idx)
        kept.insert(pos, merged)
    out = kept
    if out:
        entry["functions"] = out
    else:
        entry.pop("functions", None)

def get_set_data(entry):
    for fn in entry.get("functions", []):
        if fn.get("function", "").replace("minecraft:", "") == "set_data":
            return fn.get("data")
    return None

def strip_fn(entry, name):
    entry["functions"] = [f for f in entry.get("functions", [])
                          if f.get("function", "").replace("minecraft:", "") != name]
    if not entry["functions"]:
        del entry["functions"]

def egg_entity(entry):
    """spawn_egg 的 set_nbt EntityTag id"""
    for fn in entry.get("functions", []):
        if fn.get("function", "").replace("minecraft:", "") == "set_nbt":
            tag = fn.get("tag", "")
            # SNBT 字符串里抠 EntityTag id
            import re
            m = re.search(r'EntityTag\s*:\s*\{[^}]*id\s*:\s*"?([a-z_:]+)"?', tag)
            if m:
                return m.group(1), fn
    return None, None

def convert_item_entry(entry, out_entries):
    raw = entry.get("name", "").strip().lower()   # 原包有 Diamond_axe 式大小写混写
    entry["name"] = raw
    ns, _, path = raw.partition(":")
    if ns != "minecraft":
        mapped = map_mod_item(raw, ns, path)
        if mapped is not None:
            entry["name"] = mapped
            report_items[mapped] += 1
        else:
            report_unknown[raw] += 1              # 真缺席/改名 → 报告待映射
        clean_functions(entry)
        out_entries.append(entry)
        return
    data = get_set_data(entry)

    if path == "spawn_egg":
        ent, fn = egg_entity(entry)
        if ent:
            base = ent.replace("minecraft:", "")
            # 1.12→1.20 实体改名（蛋名跟实体走；vindication_illager 蛋=charm/treasure/dangerous 整表阵亡主因）
            base = {"vindication_illager": "vindicator", "evocation_illager": "evoker",
                    "illusion_illager": "illusioner", "zombie_pigman": "zombified_piglin",
                    "snowman": "snow_golem", "villager_golem": "iron_golem"}.get(base, base)
            egg = base + "_spawn_egg"
            entry["name"] = "minecraft:" + egg
            entry.get("functions", []).remove(fn)
            if not entry.get("functions"):
                entry.pop("functions", None)
        else:
            entry["name"] = "minecraft:zombie_spawn_egg"
            report_notes.append("spawn_egg 无 EntityTag → zombie_spawn_egg（请查看）")
        report_items[entry["name"]] += 1
        clean_functions(entry)
        out_entries.append(entry)
        return

    if path in DATA_ITEMS:
        fam = DATA_ITEMS[path]
        if isinstance(data, dict):           # data 区间 → 拆成多条同权重
            lo, hi = int(data.get("min", 0)), int(data.get("max", 0))
            strip_fn(entry, "set_data")
            clean_functions(entry)
            for d in range(lo, hi + 1):
                e2 = json.loads(json.dumps(entry))
                e2["name"] = "minecraft:" + fam(d)
                report_items[e2["name"]] += 1
                out_entries.append(e2)
            return
        d = int(data) if data is not None else 0
        entry["name"] = "minecraft:" + fam(d)
        strip_fn(entry, "set_data")
    elif path in SIMPLE_ITEMS:
        entry["name"] = "minecraft:" + SIMPLE_ITEMS[path]
        if data is not None:
            strip_fn(entry, "set_data")
    elif path.startswith("record_"):
        entry["name"] = "minecraft:music_disc_" + path[len("record_"):]
    else:
        # 原名保留（1.20 同名物品占多数）；set_data 若还挂着 → 剥掉并报告
        if data not in (None, 0):
            report_notes.append(f"未知 data 用法: {raw} data={data}")
        if data is not None:
            strip_fn(entry, "set_data")
    clean_functions(entry)
    report_items[entry["name"]] += 1
    out_entries.append(entry)

def convert_table(table, queue):
    for pool in table.get("pools", []):
        new_entries = []
        for entry in pool.get("entries", []):
            t = entry.get("type", "item").replace("minecraft:", "")
            if t == "item":
                convert_item_entry(entry, new_entries)
            elif t == "loot_table":
                entry["name"] = map_ref(entry.get("name", ""), queue)
                clean_functions(entry)
                new_entries.append(entry)
            else:
                clean_functions(entry)
                new_entries.append(entry)
        pool["entries"] = new_entries
        clean_functions(pool)      # pool 级 functions 兜底
    return table

# 深渊废墟传送门语境改造（2026-09-14 用户拍板）：传送门点火物=血焰打火石
# （echo_shard+betternether 辛西纳石合成），废墟旁 Remnant Flint Chest 的三张 flint
# 子表同步换成新钥匙及其材料（仅此三表定点替换，不做全局映射——别处的燧石/铁不动）。
TABLE_TWEAKS = {
    "underneath/flint/flint_1": {"minecraft:flint_and_steel": "underneath:blood_flint_and_steel"},
    "underneath/flint/flint_2": {"minecraft:flint": "minecraft:echo_shard",
                                 "minecraft:iron_nugget": "betternether:cincinnasite"},
    "underneath/flint/flint_3": {"minecraft:flint": "minecraft:echo_shard"},
}

def apply_tweaks(table, out_rel):
    tweaks = TABLE_TWEAKS.get(out_rel)
    if not tweaks:
        return
    for pool in table.get("pools", []):
        for e in pool.get("entries", []):
            if e.get("type", "item").replace("minecraft:", "") == "item" and e.get("name") in tweaks:
                e["name"] = tweaks[e["name"]]

def main():
    # 待移植清单：loot_map/loot.json 里 dregora:/charm: 的键
    with open(os.path.join(RES, "loot_map", "loot.json"), encoding="utf-8") as f:
        lm = json.load(f)
    queue = {k for k in lm if k.startswith(("dregora:", "charm:"))}
    zips = {ns: zipfile.ZipFile(j) for ns, (j, _) in JARS.items()}
    done, missing = set(), []
    while queue:
        ref = queue.pop()
        if ref in done:
            continue
        done.add(ref)
        ns, _, path = ref.partition(":")
        jar, prefix = JARS[ns]
        try:
            raw = zips[ns].read(prefix + path + ".json")
        except KeyError:
            missing.append(ref)
            continue
        # strict=False：1.12 表（尤其 lore 成书）字符串里有裸控制字符，MC 的 Gson 宽松可读
        text = raw.decode("utf-8", "replace")
        # 原包手写坏损修复：city.json 的 conditions": 丢了开引号
        import re as _re
        text = _re.sub(r'(?<!")conditions":', '"conditions":', text)
        try:
            table = convert_table(json.loads(text, strict=False), queue)
        except json.JSONDecodeError as e:
            report_notes.append(f"!! 宽松语法解析失败，跳过: {ref} ({e})")
            continue
        out_rel = ("charm/" + path) if ns == "charm" else path
        apply_tweaks(table, out_rel)
        if out_rel == "loot/unique_loot_epic/unique_weapons_epic":
            write_all_unique(table)         # 派生"全家桶"必出箱（epic/rare 原表保持 Dregora 原始权重）
        dst = os.path.join(RES, "loot_tables", out_rel.replace("/", os.sep) + ".json")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "w", encoding="utf-8") as f:
            json.dump(table, f, ensure_ascii=False, indent=2)
    print(f"移植完成: {len(done) - len(missing)} 个表, 缺源: {missing}")
    print(f"\n### 产出物品 distinct={len(report_items)}（人工核对 1.20.1 名是否全部有效）###")
    for k, v in sorted(report_items.items()):
        print(f"{v:5d}  {k}")
    if report_unknown:
        print("\n### 未映射 mod 物品（需处理）###")
        for k, v in report_unknown.most_common():
            print(f"{v:5d}  {k}")
    if report_notes:
        print("\n### 备注 ###")
        for n in sorted(set(report_notes)):
            print("  " + n)

main()
