# -*- coding: utf-8 -*-
"""OTGStructureExpander 的 Python 同构复刻——跑真实 BO4 数据离线验证。
与 Java 逐段对齐：JRandom(java.util.Random)、randomForCoords、children 惰性掷骰、
两阶段 cycle、required 回滚、canOverride 延后阶段、BranchFrequency、深度预算。
"""
import os, re, sys, collections

BO_ROOT = r"E:\Underneath-1.20.1\src\main\resources\data\underneath\bo"

MASK48 = (1 << 48) - 1
MASK64 = (1 << 64) - 1

def to_s32(v):
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= (1 << 31) else v

def to_s64(v):
    v &= MASK64
    return v - (1 << 64) if v >= (1 << 63) else v

class JRandom:
    def __init__(self, seed):
        self.set_seed(seed)
    def set_seed(self, seed):
        self.seed = (seed ^ 0x5DEECE66D) & MASK48
    def _next(self, bits):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & MASK48
        return to_s32(self.seed >> (48 - bits))
    def next_double(self):
        hi = self._next(26)  # 26 位非负
        lo = self._next(27)
        return ((hi << 27) + lo) / float(1 << 53)
    def next_long(self):
        hi = self._next(32)
        lo = self._next(32)
        return to_s64((hi << 32) + lo)
    def next_boolean(self):
        return self._next(1) != 0
    def next_int(self, bound):
        if bound <= 0:
            raise ValueError
        if (bound & -bound) == bound:
            return to_s32((bound * self._next(31)) >> 31)
        while True:
            bits = self._next(31)
            val = bits % bound
            if bits - val + (bound - 1) >= 0:
                return val

def random_for_coords(x, y, z, seed):
    r = JRandom(seed)
    l1 = to_s64(r.next_long() + 1)
    l2 = to_s64(r.next_long() + 1)
    l3 = to_s64(r.next_long() + 1)
    r.set_seed(to_s64((x * l1 + y * l2 + z * l3) ^ seed))
    return r

# ---- 数据解析（对齐 BOParser）----
BObj = collections.namedtuple("BObj", "name can_override branch_freq branches")
BLine = collections.namedtuple("BLine", "x y z required weighted total nodes")
BNode = collections.namedtuple("BNode", "name rot chance depth")

ROT = {"north": 0, "west": 1, "south": 2, "east": 3}

def parse_bo4(path, name):
    can_override = False
    branch_freq = 0
    branches = []
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            low = line.lower()
            if low.startswith("canoverride:"):
                can_override = "true" in low
            elif low.startswith("branchfrequency:"):
                try: branch_freq = int(line.split(":", 1)[1].strip())
                except ValueError: pass
            else:
                m = re.match(r"(branch|br|weightedbranch|wbr)\((.*)\)\s*$", low)
                if m:
                    weighted = m.group(1) in ("weightedbranch", "wbr")
                    args = [a.strip() for a in m.group(2).split(",")]
                    if len(args) < 5:
                        continue
                    x, y, z = int(args[0]), int(args[1]), int(args[2])
                    i = 3
                    required = False
                    if args[i] in ("true", "false"):
                        required = args[i] == "true"
                        i += 1
                    nodes = []
                    while i + 3 < len(args) and args[i + 1] in ROT:
                        nm = args[i]
                        rot = ROT[args[i + 1]]
                        try: chance = float(args[i + 2])
                        except ValueError: chance = 100.0
                        try: depth = int(float(args[i + 3]))
                        except ValueError: depth = 0
                        nodes.append(BNode(nm, rot, chance, depth))
                        i += 4
                        if required and not weighted:
                            break
                    total = 100.0
                    if i < len(args):
                        try: total = float(args[i])
                        except ValueError: pass
                    if nodes:
                        branches.append(BLine(x, y, z, required, weighted, total, nodes))
    return BObj(name, can_override, branch_freq, branches)

def load_registry():
    reg = {}
    for dirpath, _dirs, files in os.walk(BO_ROOT):
        for fn in files:
            if fn.endswith(".bo4") or fn.endswith(".bo3"):
                nm = fn[:-4].lower()
                if nm not in reg:
                    reg[nm] = os.path.join(dirpath, fn)
    return reg

REG_PATHS = load_registry()
REG_CACHE = {}

def get_obj(name):
    if name not in REG_CACHE:
        p = REG_PATHS.get(name)
        REG_CACHE[name] = parse_bo4(p, name) if p else None
    return REG_CACHE[name]

# ---- 旋转（BORotation：分支偏移每步 (x,z)→(z,−x)）----
def branch_offset(x, z, rot):
    for _ in range(rot & 3):
        x, z = z, -x
    return x, z

# ---- 展开引擎（与 OTGStructureExpander.java 同构）----
class Node:
    __slots__ = ("name obj x y z rot required cx cz parent cur mx nid "
                 "from_weighted done cannot deleted rolling children").split()
    def __init__(self, eng, parent, name, x, y, z, rot, required, cur, mx):
        self.name = name; self.obj = get_obj(name)
        self.x = x; self.y = y; self.z = z; self.rot = rot
        self.required = required
        self.cx = x >> 4; self.cz = z >> 4
        self.parent = parent; self.cur = cur; self.mx = mx
        self.nid = eng.next_id; eng.next_id += 1
        self.from_weighted = False
        self.done = self.cannot = self.deleted = self.rolling = False
        self.children = None

class Engine:
    def __init__(self, rng, env_occupied=None, env_biome=None, minimum_size=False):
        self.rng = rng
        self.occ = env_occupied or (lambda cx, cz: False)
        self.bio = env_biome or (lambda cx, cz: True)
        self.min_size = minimum_size
        self.next_id = 0
        self.tried = 0
        self.all = []
        self.all_hash = set()
        self.by_chunk = collections.defaultdict(list)
        self.by_name = collections.defaultdict(list)
        self.spawning_co = False
        self.spawned_this = self.spawned_last = False
        self.req_for_opt = None
        self.spawning_req_for_opt = False
        self.missing = 0

    def roll(self, line):
        nodes = line.nodes
        if not nodes: return None
        if line.weighted:
            cum = sum(n.chance for n in nodes)
            total = max(line.total, cum)
            r = self.rng.next_double() * total
            for n in nodes:
                if n.chance > 0 and n.chance >= r:
                    return n
                r -= n.chance
                if r < 0: r = 0
            return None
        for n in nodes:
            if self.rng.next_double() * line.total <= n.chance:
                return n
        return None

    def kids(self, n):
        if n.children is None:
            n.children = []
            if n.obj:
                for line in n.obj.branches:
                    sel = self.roll(line)
                    if sel is None: continue
                    ox, oz = branch_offset(line.x, line.z, n.rot)
                    rot = (n.rot + sel.rot) & 3
                    cur = n.cur if line.required else n.cur + 1
                    mx = n.mx
                    if sel.depth > 0 and not self.min_size:
                        cur = 0; mx = sel.depth
                    if self.min_size: mx = 0
                    if (mx > 0 and cur <= mx) or line.required:
                        c = Node(self, n, sel.name, n.x + ox, n.y + line.y, n.z + oz, rot,
                                 line.required, cur, mx)
                        c.from_weighted = line.weighted
                        n.children.append(c)
        return n.children

    def cached_kids(self, n):
        return n.children or []

    def calc(self, root_name, x, y, z, rot):
        root = Node(self, None, root_name, x, y, z, rot, False, 0, 0)
        if root.obj is None:
            return None
        self.add_cache(root)
        cycle = 0
        co_started = False
        done = False
        while not done:
            self.spawned_last = self.spawned_this
            self.spawned_this = False
            cycle += 1
            if cycle > 512 or self.tried > 60000:
                print("WARN guard", cycle, self.tried); break
            self.traverse(root, True)
            self.traverse(root, False)
            done = all(n.done for n in self.all)
            if done and not co_started:
                co_started = True
                self.spawning_co = True
                done = False
                for n in list(self.all):
                    for c in self.kids(n):
                        if not c.required and c.obj and c.obj.can_override:
                            n.done = False; c.done = False; c.cannot = False
            if root.cannot:
                return None
        return [n for n in self.all if not n.cannot], cycle

    def traverse(self, item, req_only):
        if not item.done:
            self.add_branches(item, False, req_only)
        elif not item.cannot:
            for c in self.kids(item):
                if not c.cannot and item.done:
                    self.traverse(c, req_only)

    def add_branches(self, item, only_spawned, req_only):
        if not self.spawning_co:
            for c in self.kids(item):
                if (not c.cannot or not c.done) and c.obj and c.obj.can_override and not c.required:
                    c.cannot = True; c.done = True
        if not req_only:
            item.done = True
        else:
            if all(c.required or c.done or c.cannot for c in self.kids(item)):
                item.done = True
        if not item.cannot:
            for c in self.kids(item):
                if c.nid in self.all_hash:
                    continue
                can = True
                if c.obj is None:
                    c.done = True; c.cannot = True; self.missing += 1
                if c.done or c.cannot:
                    continue
                if req_only and not c.required:
                    continue
                if (c.mx == 0 or c.cur > c.mx) and not c.required:
                    can = False
                self.tried += 1
                if self.min_size and c.from_weighted:
                    c.done = True; c.cannot = True; continue
                if can and not self.min_size and self.occ(c.cx, c.cz):
                    can = False
                if can and c.obj.branch_freq > 0 and not self.check_freq(c):
                    can = False
                if can and not self.min_size and not self.bio(c.cx, c.cz):
                    can = False
                if can and not c.obj.can_override and self.has_collision(c):
                    can = False
                if can:
                    if not self.kids(c):
                        c.done = True
                    self.spawned_this = True
                    self.add_cache(c)
                    if not self.spawning_req_for_opt and not c.required:
                        self.spawning_req_for_opt = True
                        self.req_for_opt = c
                        self.traverse(c, True)
                        self.spawning_req_for_opt = False
                        can = any(b is c for b in self.by_chunk.get((c.cx, c.cz), []))
                    elif only_spawned and not self.spawning_req_for_opt and c.required:
                        self.traverse(c, True)
                if not can:
                    rolled = False
                    if not c.done and not c.cannot:
                        c.done = True; c.cannot = True
                        if c.required:
                            self.rollback(item, req_only)
                            rolled = True
                    if rolled:
                        break
            if not only_spawned and not req_only and not item.cannot:
                for c in self.kids(item):
                    if c.nid in self.all_hash and (c.required or (self.spawning_co and c.obj and not c.obj.can_override)) and not c.cannot:
                        self.traverse(c, False)
            if not only_spawned and req_only and not item.cannot:
                for c in self.kids(item):
                    if c.nid in self.all_hash and c.required:
                        self.traverse(c, True)

    def rollback(self, br, req_only):
        if self.spawning_req_for_opt and self.req_for_opt is not None and self.req_for_opt.parent is br:
            return
        br.cannot = True; br.done = True; br.deleted = True; br.rolling = True
        self.delete_kids(br)
        if br.nid in self.all_hash:
            self.rm_cache(br)
        if br.parent is not None and not br.parent.rolling:
            if br.required:
                self.rollback(br.parent, req_only)
            else:
                parent_done = all(s.done or s.cannot for s in self.cached_kids(br.parent))
                if not parent_done and not (self.spawning_req_for_opt and self.req_for_opt is br):
                    br.parent.done = False
                    if not self.spawning_req_for_opt:
                        if req_only:
                            self.add_branches(br.parent, False, True)
                        else:
                            self.add_branches(br.parent, True, False)
                    else:
                        self.spawning_req_for_opt = False
                        self.add_branches(br.parent, False, True)
                        self.spawning_req_for_opt = True
        br.rolling = False

    def delete_kids(self, br):
        for c in self.cached_kids(br):
            c.cannot = True; c.done = True; c.deleted = True
            if not c.rolling:
                c.rolling = True
                self.delete_kids(c)
                c.rolling = False
            if c.nid in self.all_hash:
                self.rm_cache(c)

    def check_freq(self, c):
        for (ox, oz) in self.by_name.get(c.name, []):
            import math
            if math.floor(math.sqrt((c.cx - ox) ** 2 + (c.cz - oz) ** 2)) <= c.obj.branch_freq:
                return False
        return True

    def has_collision(self, c):
        for ex in self.by_chunk.get((c.cx, c.cz), []):
            if ex.obj and not ex.obj.can_override:
                return True   # 简化（城市数据不触发）
        return False

    def add_cache(self, n):
        self.all.append(n)
        self.all_hash.add(n.nid)
        self.by_chunk[(n.cx, n.cz)].append(n)
        self.by_name[n.name].append((n.cx, n.cz))

    def rm_cache(self, n):
        self.all.remove(n)
        self.all_hash.discard(n.nid)
        lst = self.by_chunk.get((n.cx, n.cz))
        if lst and n in lst:
            lst.remove(n)
        nl = self.by_name.get(n.name)
        if nl and (n.cx, n.cz) in nl:
            nl.remove((n.cx, n.cz))


def classify(name):
    for p in ("skyscraper_", "ruinscraper_", "single_"):
        if name.startswith(p):
            return name
    return None

def run(seed, label, occ=None, bio=None):
    rng = random_for_coords(0 + 8, 0, 0 + 7, seed)
    eng = Engine(rng, occ, bio)
    out = eng.calc("citygrid", 0, 50, 0, 0)
    if out is None:
        print(label, ": FAILED (root rollback)")
        return None
    pieces, cycles = out
    buildings = collections.Counter()
    levels = collections.Counter()
    cxs = [p.cx for p in pieces]; czs = [p.cz for p in pieces]
    for p in pieces:
        b = classify(p.name)
        if b: buildings[b] += 1
        m = re.match(r"l(\d)-", p.name)
        if m: levels["L" + m.group(1)] += 1
    print("%s: %d pieces, %d cycles, span X[%d..%d] Z[%d..%d] (%dx%d chunks), missing=%d" % (
        label, len(pieces), cycles, min(cxs), max(cxs), min(czs), max(czs),
        max(cxs) - min(cxs) + 1, max(czs) - min(czs) + 1, eng.missing))
    print("   distinct buildings: %d  %s" % (len(buildings), sorted(buildings)[:8]))
    print("   street levels:", dict(levels))
    return set((p.name, p.x, p.z) for p in pieces)

if __name__ == "__main__":
    print("registry:", len(REG_PATHS), "objects")
    # minimumSize
    eng = Engine(JRandom(0), minimum_size=True)
    out = eng.calc("citygrid", 0, 0, 0, 0)
    if out:
        ch = set((n.cx, n.cz) for n in out[0])
        cxs = [c[0] for c in ch]; czs = [c[1] for c in ch]
        print("citygrid minimumSize: top=%d right=%d bottom=%d left=%d -> %dx%d" % (
            -min(czs), max(cxs), max(czs), -min(cxs),
            max(cxs) - min(cxs) + 1, max(czs) - min(czs) + 1))
    a = run(12345, "seed 12345")
    b = run(67890, "seed 67890")
    if a and b:
        print("cross-seed identical:", a == b)
    # 遮挡测试：东侧 x>=5 chunk 全占用 → 城市东缘应被回滚裁剪
    occ = lambda cx, cz: cx >= 5
    run(12345, "seed 12345 occluded x>=5", occ=occ)
    # spawner_city 快测
    rng = random_for_coords(8, 0, 7, 777)
    eng = Engine(rng)
    out = eng.calc("single_skyscraper_spawner_city", 0, 66, 0, 0)
    print("spawner_city:", "EMPTY(空地)" if out is None or len(out[0]) <= 1 else
          "%d pieces (%s...)" % (len(out[0]), out[0][1].name if len(out[0]) > 1 else ""))
    # 多种子 spawner 分布统计
    cnt = collections.Counter()
    for s in range(200):
        rng = random_for_coords(s * 16 + 8, 0, 7, 42)
        eng = Engine(rng)
        out = eng.calc("single_skyscraper_spawner_city", s * 16, 66, 0, 0)
        if out is None or len(out[0]) <= 1:
            cnt["empty"] += 1
        else:
            root_child = [n for n in out[0] if n.parent is not None and n.parent.parent is None]
            cnt[root_child[0].name if root_child else "?"] += 1
    print("spawner_city 200 rolls:", dict(cnt.most_common(6)), "... empty rate:", cnt["empty"] / 200.0)
