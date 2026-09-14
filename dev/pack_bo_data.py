# -*- coding: utf-8 -*-
# 把 Underneath-2.0.jar 里的全部 .bo3/.bo4 规范化后打进 mod 资源 data/underneath/bo/
# 规范化：路径+文件名全小写、空格->下划线（ResourceLocation 只认 [a-z0-9/._-]）
# 直接读 jar（绕开 Windows MAX_PATH）；.nbt 方块实体 P1④ 再入
import zipfile, os, re, collections

JAR = r"D:\relink\114514\.minecraft\versions\RLCD1.1.2.b\mods\Underneath-2.0.jar"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "underneath", "bo")
PREFIX = "assets/worldpacker/Underneath/WorldObjects/"

BAD = re.compile(r"[^a-z0-9/._-]")

def sanitize(rel):
    s = rel.lower().replace(" ", "_")
    s = BAD.sub("_", s)
    return s

def main():
    zf = zipfile.ZipFile(JAR)
    written, skipped = 0, 0
    total_bytes = 0
    seen = {}
    stems = collections.defaultdict(list)  # 文件名去后缀 -> 出处（对象名索引冲突检测）
    for name in zf.namelist():
        if not name.startswith(PREFIX):
            continue
        low = name.lower()
        if not low.endswith((".bo3", ".bo4", ".bo2", ".nbt")):
            continue
        rel = sanitize(name[len(PREFIX):])
        if rel in seen:
            print(f"路径冲突(跳过): {name}  ==  {seen[rel]}")
            skipped += 1
            continue
        seen[rel] = name
        stem = rel.rsplit("/", 1)[-1].rsplit(".", 1)[0]
        stems[stem].append(rel)
        if low.endswith(".nbt"):
            data = zf.read(name)     # 二进制原样（gzip NBT）
        else:
            # 文本 BO 剥注释与空行瘦身（解析器本就忽略）；latin-1 无损往返
            text = zf.read(name).decode("latin-1")
            lines = [ln for ln in text.splitlines() if ln.strip() and not ln.lstrip().startswith("#")]
            data = ("\n".join(lines) + "\n").encode("latin-1")
        total_bytes += len(data)
        dst = os.path.join(OUT, rel.replace("/", os.sep))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "wb") as f:
            f.write(data)
        written += 1
    dup_stems = {k: v for k, v in stems.items() if len(v) > 1}
    print(f"written: {written} files, {total_bytes / 1048576:.1f} MB (raw), skipped: {skipped}")
    print(f"对象名(去后缀)冲突数: {len(dup_stems)}")
    for k, v in list(dup_stems.items())[:10]:
        print(f"  {k}: {v}")

main()
