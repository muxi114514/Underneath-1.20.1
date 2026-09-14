# -*- coding: utf-8 -*-
# VariedCommodities 物品移植·资产部分（CC BY-NC 3.0，署名 Noppes）：
#   提取 41 张物品贴图 + 4 套盔甲层贴图（_1→_layer_1），生成模型 json，合并双语 lang
import zipfile, os, json

VJ = r"D:\relink\114514\.minecraft\versions\RLCD1.1.2.b\mods\VariedCommodities_1.12.2-(31Mar23).jar"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "underneath")

# 名字 -> (模型类型, 英文名, 中文名)
HANDHELD, GENERATED = "handheld", "generated"
ITEMS = {
    "coin_gold":      (GENERATED, "Golden Coin", "金币"),
    "coin_bronze":    (GENERATED, "Bronze Coin", "青铜币"),
    "coin_diamond":   (GENERATED, "Diamond Coin", "钻石币"),
    "ancient_coin":   (GENERATED, "Ancient Coin", "古代金币"),
    "banjo":          (GENERATED, "Banjo", "班卓琴"),
    "violin":         (GENERATED, "Violin", "小提琴"),
    "mana":           (GENERATED, "Mana", "魔力精华"),
    "broken_bottle":  (HANDHELD, "Broken Bottle", "碎酒瓶"),
    "holyhandgrenade": (GENERATED, "Holy Hand Grenade", "神圣手雷"),
    "bo_staff":       (HANDHELD, "Bo Staff", "白蜡长棍"),
    "wooden_staff":   (HANDHELD, "Wooden Staff", "木杖"),
    "demonic_staff":  (HANDHELD, "Demonic Staff", "恶魔法杖"),
    "bronze_dagger":  (HANDHELD, "Bronze Dagger", "青铜短剑"),
    "emerald_dagger": (HANDHELD, "Emerald Dagger", "绿宝石短剑"),
    "frost_dagger":   (HANDHELD, "Frost Dagger", "冰霜短剑"),
    "mithril_dagger": (HANDHELD, "Mithril Dagger", "秘银短剑"),
    "katana":         (HANDHELD, "Katana", "武士刀"),
    "sai":            (HANDHELD, "Sai", "武士钗"),
    "cleaver":        (HANDHELD, "Cleaver", "剁肉刀"),
    "excalibur":      (HANDHELD, "Excalibur", "王者之剑"),
    "chicken_sword":  (HANDHELD, "Chicken Sword", "烧鸡剑"),
    "crowbar":        (HANDHELD, "Crowbar", "撬棍"),
    "wrench":         (HANDHELD, "Wrench", "扳手"),
    "pipe_wrench":    (HANDHELD, "Pipe Wrench", "管钳"),
    "hammer":         (HANDHELD, "Hammer", "锤子"),
    "sledge_hammer":  (HANDHELD, "Sledgehammer", "大锤"),
    "lead_pipe":      (HANDHELD, "Lead Pipe", "铅管"),
    "infantry_helmet": (GENERATED, "Infantry Helmet", "步兵头盔"),
    "mithril_chest":  (GENERATED, "Mithril Chestplate", "秘银胸甲"),
    "soldier_head":   (GENERATED, "Soldier Helmet", "士兵头盔"),
    "soldier_chest":  (GENERATED, "Soldier Chestplate", "士兵胸甲"),
    "soldier_legs":   (GENERATED, "Soldier Leggings", "士兵护腿"),
    "soldier_bottom": (GENERATED, "Soldier Trenchcoat", "士兵风衣"),
    "commissar_head": (GENERATED, "Commissar Helmet", "政委军帽"),
    "commissar_chest": (GENERATED, "Commissar Chestplate", "政委制服"),
    "commissar_legs": (GENERATED, "Commissar Leggings", "政委护腿"),
    "commissar_bottom": (GENERATED, "Commissar Trenchcoat", "政委风衣"),
    "nanorum_head":   (GENERATED, "Nanorum Helmet", "纳米头盔"),
    "nanorum_chest":  (GENERATED, "Nanorum Chestplate", "纳米胸甲"),
    "nanorum_legs":   (GENERATED, "Nanorum Leggings", "纳米护腿"),
    "nanorum_boots":  (GENERATED, "Nanorum Boots", "纳米靴"),
}
ARMOR_LAYERS = ["soldier_1", "soldier_2", "commissar_1", "commissar_2",
                "nanorum_1", "nanorum_2", "infantry_1", "mithril_1", "mithril_2"]

def main():
    zf = zipfile.ZipFile(VJ)
    # 1) 物品贴图
    tex_dir = os.path.join(ASSETS, "textures", "item")
    os.makedirs(tex_dir, exist_ok=True)
    for name in ITEMS:
        data = zf.read(f"assets/variedcommodities/textures/items/{name}.png")
        with open(os.path.join(tex_dir, name + ".png"), "wb") as f:
            f.write(data)
    # 2) 盔甲层贴图（_1 → _layer_1）
    armor_dir = os.path.join(ASSETS, "textures", "models", "armor")
    os.makedirs(armor_dir, exist_ok=True)
    for layer in ARMOR_LAYERS:
        base, n = layer.rsplit("_", 1)
        data = zf.read(f"assets/variedcommodities/textures/models/armor/{layer}.png")
        with open(os.path.join(armor_dir, f"{base}_layer_{n}.png"), "wb") as f:
            f.write(data)
    # 3) 物品模型
    model_dir = os.path.join(ASSETS, "models", "item")
    os.makedirs(model_dir, exist_ok=True)
    for name, (kind, _, _) in ITEMS.items():
        with open(os.path.join(model_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "item/" + kind,
                       "textures": {"layer0": "underneath:item/" + name}}, f, indent=2)
    # 4) 合并 lang
    for lang, idx in (("en_us", 1), ("zh_cn", 2)):
        path = os.path.join(ASSETS, "lang", lang + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for name, tup in ITEMS.items():
            data[f"item.underneath.{name}"] = tup[idx]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"VC 资产移植完成：{len(ITEMS)} 物品贴图+模型, {len(ARMOR_LAYERS)} 盔甲层, lang 已合并")

main()
