# -*- coding: utf-8 -*-
"""三维校验（P1 方法论）：新捆包表 SNBT 良构 + 物品名单 + 属性白名单"""
import json, glob, re, os

os.chdir(r"E:\Underneath-1.20.1")
pack = set(l.strip() for l in open("dev/pack_items_1.70.txt", encoding="utf-8"))
bad_snbt = []; bad_item = []; bad_attr = []
ATTR_OK = re.compile(r'(generic\.[a-z_]+|forge:[a-z_.]+|[a-z_]+:[a-z_.]+)')

def scan_tag(s, f):
    depth = 0; instr = False; esc = False
    for ch in s:
        if esc:
            esc = False; continue
        if ch == "\\":
            esc = True; continue
        if ch == '"':
            instr = not instr; continue
        if instr:
            continue
        if ch in "{[":
            depth += 1
        elif ch in "}]":
            depth -= 1
        if depth < 0:
            bad_snbt.append(f); return
    if depth != 0 or instr:
        bad_snbt.append(f)

def walk(o, f):
    if isinstance(o, dict):
        if str(o.get("type", "")).endswith("item") and "name" in o:
            n = o["name"]
            if not n.startswith("minecraft:") and n not in pack and not n.startswith("underneath:"):
                bad_item.append((f, n))
        if str(o.get("function", "")).endswith("set_nbt"):
            scan_tag(o.get("tag", ""), f)
        if str(o.get("function", "")).endswith("set_attributes"):
            for m in o.get("modifiers", []):
                if not ATTR_OK.fullmatch(str(m.get("attribute", ""))):
                    bad_attr.append((f, m.get("attribute")))
        for v in o.values():
            walk(v, f)
    elif isinstance(o, list):
        for v in o:
            walk(v, f)

files = [f for f in glob.glob("src/main/resources/data/underneath/loot_tables/**/*.json", recursive=True)
         if "loot_bundles" in f]
for f in files:
    walk(json.load(open(f, encoding="utf-8")), f)
print("捆包表数:", len(files))
print("SNBT 坏:", len(set(bad_snbt)), "| 非法物品:", len(set(bad_item)), "| 非法属性:", len(set(bad_attr)))
for x in list(set(bad_item))[:10]:
    print("  item:", x)
for x in list(set(bad_snbt))[:5]:
    print("  snbt:", x)
for x in list(set(bad_attr))[:5]:
    print("  attr:", x)
