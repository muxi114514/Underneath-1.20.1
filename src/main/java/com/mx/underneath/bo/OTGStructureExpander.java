package com.mx.underneath.bo;

import com.mx.underneath.Underneath;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * OTG {@code BO4CustomStructure} 分支展开引擎的 1:1 移植（1.12 源码逐段对应）。
 *
 * <p>核心语义（全部按源码钉死，勿凭直觉改）：
 * <ul>
 * <li><b>cycle 脉冲</b>：每轮先 spawn 全部 required 分支、再 spawn optional 分支，一轮长一层；
 *     optional spawn 后立即递归 spawn 其 required 子链（失败只回滚到该 optional 为止）。</li>
 * <li><b>掷骰在 children 惰性生成时</b>（共享结构级 Random，消耗顺序=遍历顺序）：普通 Branch 逐候选独立掷
 *     {@code nextDouble()*totalChance <= chance} 首中者胜；WeightedBranch 掷一次轮盘
 *     {@code chance >= r ? 中 : r-=chance}，落在 totalChance 空隙=什么都不出（required 也一样，无兜底——
 *     spawner_city 的 2% 空地正是这么来的）。</li>
 * <li><b>required 失败 → 回滚父分支</b>并级联（required 父继续向上滚），子树全删；滚到根=整结构取消。</li>
 * <li><b>深度</b>：required 不消耗；候选节点 depth&gt;0 时重置 currentDepth=0/maxDepth=depth
 *     （L1 毛细街道的衰减预算）；optional 进入条件 {@code (max>0 && cur<=max) || required}。</li>
 * <li><b>碰撞</b>：跨结构=chunk 占用（Env 回调，所有分支都查）；结构内=同 chunk 的 !CanOverride 分支
 *     AABB 相交（城市零件全 CanOverride 故不触发，但机制保留）；biome=Env 回调（所有分支都查，
 *     城市伸进无城群系即回滚剪枝）。</li>
 * <li><b>BranchFrequency</b>：同名分支在本结构内的最小 chunk 距离（8 车道大道端片=15）。</li>
 * <li><b>CanOverride optional 延后阶段</b>：主体全部完成后统一激活再跑（L1 毛细街道走这条）。</li>
 * </ul>
 * 未移植（本数据集 0 使用，已全量验证）：branchGroup 协调、mustBeBelowOther/mustBeInside/
 * cannotBeInside、水面检查、spawnDelayed 重试、SmoothingArea（SmoothRadius 全 0）。
 */
public final class OTGStructureExpander {

    /** 防御护栏（OTG 无上限；防数据环导致假死）。 */
    private static final int MAX_TRIED = 60000;
    private static final int MAX_CYCLES = 512;

    /** 展开环境：跨结构占用与群系许可（minimumSize 计算时不查）。 */
    public interface Env {
        boolean isChunkOccupied(int cx, int cz);

        boolean biomeAllowsBranch(int cx, int cz);
    }

    /** 全放行环境（调试指令用）。 */
    public static final Env FREE = new Env() {
        @Override
        public boolean isChunkOccupied(int cx, int cz) {
            return false;
        }

        @Override
        public boolean biomeAllowsBranch(int cx, int cz) {
            return true;
        }
    };

    public record Piece(String name, int x, int y, int z, int rot) {
    }

    /** spawned=root 未被回滚且至少放了自己；chunks=全部占用 chunk（ChunkPos.asLong）。 */
    public record Result(List<Piece> pieces, Set<Long> chunks, boolean spawned, int missing) {
    }

    /** minimumSize：required-only 展开的 chunk 包围盒（相对起点 chunk 的 top/right/bottom/left，OTG 序）。 */
    public record MinSize(int top, int right, int bottom, int left) {
    }

    /** BranchDataItem 移植。 */
    private static final class Node {
        final String name;
        final BOObject obj;
        final int x;
        final int y;
        final int z;
        final int rot;
        final boolean required;
        final int cx;
        final int cz;
        final Node parent;
        final int currentDepth;
        final int maxDepth;
        final int id;
        /** 出自 WeightedBranch 行（minimumSize 模式忽略加权分支用）。 */
        boolean fromWeighted;
        boolean done;
        boolean cannot;
        boolean beingRolledBack;
        List<Node> children;

        Node(OTGStructureExpander e, Node parent, String name, int x, int y, int z, int rot,
             boolean required, int currentDepth, int maxDepth) {
            this.name = name;
            this.obj = e.registry.get(name);
            this.x = x;
            this.y = y;
            this.z = z;
            this.rot = rot;
            this.required = required;
            this.cx = x >> 4;
            this.cz = z >> 4;
            this.parent = parent;
            this.currentDepth = currentDepth;
            this.maxDepth = maxDepth;
            this.id = e.nextId++;
        }
    }

    private final BORegistry registry;
    private final Random random;
    private final Env env;
    private final boolean minimumSize;

    private int nextId = 0;
    private int branchesTried = 0;
    private final List<Node> all = new ArrayList<>();
    private final Set<Integer> allHash = new HashSet<>();
    private final Map<Long, List<Node>> byChunk = new HashMap<>();
    private final Map<String, List<int[]>> byName = new HashMap<>();
    private boolean spawningCanOverride = false;
    private boolean spawningReqChildrenForOptional = false;
    private Node currentReqForOptional = null;
    private int missing = 0;

    private OTGStructureExpander(BORegistry registry, Random random, Env env, boolean minimumSize) {
        this.registry = registry;
        this.random = random;
        this.env = env;
        this.minimumSize = minimumSize;
    }

    /**
     * 展开一座结构。RNG 必须由调用方按 OTG plotter 语义构造：
     * {@code randomForCoords(startX+8, 0, startZ+7, worldSeed)}（起点 y 在 OTG 里此刻恒为 0）。
     */
    public static Result expand(BORegistry registry, String rootName, int startX, int startY, int startZ,
                                int rot, Random rng, Env env) {
        OTGStructureExpander e = new OTGStructureExpander(registry, rng, env, false);
        return e.calculate(rootName.toLowerCase(Locale.ROOT), startX, startY, startZ, rot);
    }

    /** required-only 最小尺寸（确定性，调用方缓存）。 */
    public static MinSize minimumSize(BORegistry registry, String rootName) {
        OTGStructureExpander e = new OTGStructureExpander(registry, new Random(0L), FREE, true);
        Result r = e.calculate(rootName.toLowerCase(Locale.ROOT), 0, 0, 0, 0);
        int top = 0, right = 0, bottom = 0, left = 0;
        for (long ck : r.chunks()) {
            int cx = ChunkPos.getX(ck);
            int cz = ChunkPos.getZ(ck);
            if (cx > right) {
                right = cx;
            }
            if (cx < left) {
                left = cx;
            }
            if (cz > bottom) {
                bottom = cz;
            }
            if (cz < top) {
                top = cz;
            }
        }
        return new MinSize(-top, right, bottom, -left);
    }

    // ---- 主循环（calculateBranches 移植） ----

    private Result calculate(String rootName, int startX, int startY, int startZ, int rot) {
        Node root = new Node(this, null, rootName, startX, startY, startZ, rot, false, 0, 0);
        if (root.obj == null) {
            return new Result(List.of(), Set.of(), false, 1);
        }
        addToCaches(root);

        int cycle = 0;
        boolean canOverridePhaseStarted = false;
        boolean processingDone = false;
        while (!processingDone) {
            cycle++;
            if (cycle > MAX_CYCLES || this.branchesTried > MAX_TRIED) {
                Underneath.LOGGER.warn("OTG 展开护栏触发({} cycle {} tried)，截断 {}", cycle, this.branchesTried, rootName);
                break;
            }

            traverseAndSpawn(root, true);
            traverseAndSpawn(root, false);

            processingDone = true;
            for (Node n : this.all) {
                if (!n.done) {
                    processingDone = false;
                    break;
                }
            }

            // CanOverride optional 延后阶段：主体完成后统一激活（L1 毛细街道）
            if (processingDone && !canOverridePhaseStarted) {
                canOverridePhaseStarted = true;
                this.spawningCanOverride = true;
                processingDone = false;
                for (Node n : this.all) {
                    for (Node child : children(n)) {
                        if (!child.required && child.obj != null && child.obj.canOverride) {
                            n.done = false;
                            child.done = false;
                            child.cannot = false;
                        }
                    }
                }
            }

            if (root.cannot) {
                return new Result(List.of(), Set.of(), false, this.missing);
            }
        }

        List<Piece> pieces = new ArrayList<>(this.all.size());
        Set<Long> chunks = new HashSet<>();
        for (Node n : this.all) {
            if (!n.cannot) {
                pieces.add(new Piece(n.name, n.x, n.y, n.z, n.rot));
                chunks.add(ChunkPos.asLong(n.cx, n.cz));
            }
        }
        return new Result(pieces, chunks, !pieces.isEmpty(), this.missing);
    }

    // ---- children 惰性生成（BranchDataItem.getChildren 移植：掷骰在此发生） ----

    private List<Node> children(Node n) {
        if (n.children == null) {
            n.children = new ArrayList<>();
            if (n.obj != null) {
                for (BOObject.BranchLine line : n.obj.branches) {
                    BOObject.BranchNode sel = roll(line, this.random);
                    if (sel == null) {
                        continue;
                    }
                    int[] off = BORotation.branchOffset(line.x(), line.z(), n.rot);
                    int childRot = (n.rot + sel.rotId()) & 3;
                    int cur = line.required() ? n.currentDepth : n.currentDepth + 1;
                    int max = n.maxDepth;
                    if (sel.depth() > 0 && !this.minimumSize) {
                        cur = 0;
                        max = sel.depth();
                    }
                    if (this.minimumSize) {
                        max = 0;
                    }
                    if ((max > 0 && cur <= max) || line.required()) {
                        Node child = new Node(this, n, sel.name(), n.x + off[0], n.y + line.y(),
                                n.z + off[1], childRot, line.required(), cur, max);
                        child.fromWeighted = line.weighted();
                        n.children.add(child);
                    }
                }
            }
        }
        return n.children;
    }

    /** 已缓存 children（不新掷），供回滚删除用（getChildren(true) 语义）。 */
    private List<Node> cachedChildren(Node n) {
        return n.children == null ? List.of() : n.children;
    }

    /** 掷骰（BO4BranchFunction / BO4WeightedBranchFunction.toCustomObjectCoordinate 移植）。 */
    private static BOObject.BranchNode roll(BOObject.BranchLine line, Random rng) {
        List<BOObject.BranchNode> nodes = line.nodes();
        if (nodes.isEmpty()) {
            return null;
        }
        if (line.weighted()) {
            double cum = 0;
            for (BOObject.BranchNode nd : nodes) {
                cum += nd.chance();
            }
            double total = line.totalChance();
            if (cum > total) {
                total = cum;
            }
            double r = rng.nextDouble() * total;
            for (BOObject.BranchNode nd : nodes) {
                if (nd.chance() > 0 && nd.chance() >= r) {
                    return nd;
                }
                r -= nd.chance();
                if (r < 0) {
                    r = 0;
                }
            }
            return null;   // totalChance 空隙=不出（required 亦然，OTG 无兜底）
        }
        for (BOObject.BranchNode nd : nodes) {
            if (rng.nextDouble() * line.totalChance() <= nd.chance()) {
                return nd;
            }
        }
        return null;
    }

    // ---- 遍历与生成（traverseAndSpawnChildBranches / addBranches 移植） ----

    private void traverseAndSpawn(Node item, boolean requiredOnly) {
        if (!item.done) {
            addBranches(item, false, requiredOnly);
        } else if (!item.cannot) {
            for (Node child : children(item)) {
                if (!child.cannot && item.done) {
                    traverseAndSpawn(child, requiredOnly);
                }
            }
        }
    }

    private void addBranches(Node item, boolean traverseOnlySpawnedChildren, boolean requiredOnly) {
        // CanOverride optional 在主体阶段一律推迟
        if (!this.spawningCanOverride) {
            for (Node child : children(item)) {
                if ((!child.cannot || !child.done)
                        && child.obj != null && child.obj.canOverride && !child.required) {
                    child.cannot = true;
                    child.done = true;
                }
            }
        }

        if (!requiredOnly) {
            item.done = true;
        } else {
            boolean onlyRequired = true;
            for (Node child : children(item)) {
                if (!child.required && !child.done && !child.cannot) {
                    onlyRequired = false;
                    break;
                }
            }
            if (onlyRequired) {
                item.done = true;
            }
        }

        if (!item.cannot) {
            for (Node child : children(item)) {
                if (!this.allHash.contains(child.id)) {
                    boolean canSpawn = true;
                    boolean chunkIneligible = false;
                    boolean spaceOccupied = false;
                    boolean freqNotPassed = false;

                    if (child.obj == null) {
                        child.done = true;
                        child.cannot = true;
                        this.missing++;
                    }
                    if (child.done || child.cannot) {
                        continue;
                    }

                    // required 阶段跳过 optional（branchGroup 数据 0 使用，组协调恒过）
                    if (requiredOnly && !child.required) {
                        continue;
                    }

                    if ((child.maxDepth == 0 || child.currentDepth > child.maxDepth) && !child.required) {
                        canSpawn = false;
                    }

                    this.branchesTried++;

                    if (this.minimumSize && child.fromWeighted) {
                        child.done = true;
                        child.cannot = true;
                        continue;
                    }

                    if (canSpawn && !this.minimumSize && this.env.isChunkOccupied(child.cx, child.cz)) {
                        canSpawn = false;
                        chunkIneligible = true;
                    }
                    if (canSpawn && child.obj.branchFrequency > 0 && !checkBranchFrequency(child)) {
                        canSpawn = false;
                        freqNotPassed = true;
                    }
                    if (canSpawn && !this.minimumSize && !this.env.biomeAllowsBranch(child.cx, child.cz)) {
                        canSpawn = false;
                        chunkIneligible = true;
                    }
                    if (canSpawn && !child.obj.canOverride && hasCollision(child)) {
                        canSpawn = false;
                        spaceOccupied = true;
                    }

                    if (canSpawn) {
                        if (children(child).isEmpty()) {
                            child.done = true;
                        }
                        addToCaches(child);

                        // optional spawn 后立即 spawn 其 required 子链；若被回滚则视为本分支失败
                        if (!this.spawningReqChildrenForOptional && !child.required) {
                            this.spawningReqChildrenForOptional = true;
                            this.currentReqForOptional = child;
                            traverseAndSpawn(child, true);
                            this.spawningReqChildrenForOptional = false;
                            boolean found = false;
                            List<Node> inChunk = this.byChunk.get(ChunkPos.asLong(child.cx, child.cz));
                            if (inChunk != null) {
                                for (Node b : inChunk) {
                                    if (b == child) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            canSpawn = found;
                        } else if (traverseOnlySpawnedChildren && !this.spawningReqChildrenForOptional
                                && child.required) {
                            traverseAndSpawn(child, true);
                        }
                    }

                    if (!canSpawn) {
                        boolean rolledBack = false;
                        if (!child.done && !child.cannot) {
                            child.done = true;
                            child.cannot = true;
                            // required 分支失败=组失败（branchGroup 数据 0 使用，无兄弟可救）→ 回滚父分支
                            if (child.required) {
                                rollBackBranch(item, requiredOnly);
                                rolledBack = true;
                            }
                        }
                        if (rolledBack) {
                            break;
                        }
                    }
                }
            }

            if (!traverseOnlySpawnedChildren && !requiredOnly && !item.cannot) {
                for (Node child : children(item)) {
                    if (this.allHash.contains(child.id)
                            && (child.required
                                || (this.spawningCanOverride && child.obj != null && !child.obj.canOverride))
                            && !child.cannot) {
                        traverseAndSpawn(child, false);
                    }
                }
            }
            if (!traverseOnlySpawnedChildren && requiredOnly && !item.cannot) {
                for (Node child : children(item)) {
                    if (this.allHash.contains(child.id) && child.required) {
                        traverseAndSpawn(child, true);
                    }
                }
            }
        }
    }

    // ---- 回滚（rollBackBranch / deleteBranchChildren 移植） ----

    private void rollBackBranch(Node branch, boolean requiredOnly) {
        if (this.spawningReqChildrenForOptional && this.currentReqForOptional != null
                && this.currentReqForOptional.parent == branch) {
            return;
        }
        branch.cannot = true;
        branch.done = true;
        branch.beingRolledBack = true;
        deleteChildren(branch);

        if (this.allHash.contains(branch.id)) {
            removeFromCaches(branch);
        }

        if (branch.parent != null && !branch.parent.beingRolledBack) {
            if (branch.required) {
                rollBackBranch(branch.parent, requiredOnly);
            } else {
                boolean parentDone = true;
                for (Node sibling : cachedChildren(branch.parent)) {
                    if (!sibling.done && !sibling.cannot) {
                        parentDone = false;
                        break;
                    }
                }
                if (!parentDone && !(this.spawningReqChildrenForOptional && this.currentReqForOptional == branch)) {
                    branch.parent.done = false;
                    if (!this.spawningReqChildrenForOptional) {
                        if (requiredOnly) {
                            addBranches(branch.parent, false, true);
                        } else {
                            addBranches(branch.parent, true, false);
                        }
                    } else {
                        this.spawningReqChildrenForOptional = false;
                        addBranches(branch.parent, false, true);
                        this.spawningReqChildrenForOptional = true;
                    }
                }
            }
        }
        branch.beingRolledBack = false;
    }

    private void deleteChildren(Node branch) {
        for (Node child : cachedChildren(branch)) {
            child.cannot = true;
            child.done = true;
            if (!child.beingRolledBack) {
                child.beingRolledBack = true;
                deleteChildren(child);
                child.beingRolledBack = false;
            }
            if (this.allHash.contains(child.id)) {
                removeFromCaches(child);
            }
        }
    }

    // ---- 检查与缓存 ----

    private boolean checkBranchFrequency(Node child) {
        int radius = child.obj.branchFrequency;
        List<int[]> coords = this.byName.get(child.name);
        if (coords != null) {
            for (int[] c : coords) {
                double d = Math.floor(Math.sqrt(Math.pow(child.cx - c[0], 2) + Math.pow(child.cz - c[1], 2)));
                if (d <= radius) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasCollision(Node child) {
        List<Node> inChunk = this.byChunk.get(ChunkPos.asLong(child.cx, child.cz));
        if (inChunk == null) {
            return false;
        }
        int[] cb = worldBounds(child);
        for (Node ex : inChunk) {
            if (ex.obj != null && !ex.obj.canOverride) {
                int[] eb = worldBounds(ex);
                if (cb != null && eb != null
                        && eb[3] >= cb[0] && eb[0] <= cb[3]
                        && eb[4] >= cb[1] && eb[1] <= cb[4]
                        && eb[5] >= cb[2] && eb[2] <= cb[5]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final Map<String, int[]> LOCAL_BOUNDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int[] NO_BOUNDS = new int[0];

    /** 零件旋转后的世界 AABB {minX,minY,minZ,maxX,maxY,maxZ}；空件 null。 */
    public static int[] worldBounds(String name, BOObject obj, int x, int y, int z, int rot) {
        int[] lb = LOCAL_BOUNDS.computeIfAbsent(name, k -> {
            if (obj == null || (obj.xyz.length == 0 && obj.rndXyz.length == 0)) {
                return NO_BOUNDS;
            }
            int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, obj.bo4 ? 1 : 0};
            for (int i = 0; i < obj.xyz.length; i += 3) {
                b[0] = Math.min(b[0], obj.xyz[i]);
                b[3] = Math.max(b[3], obj.xyz[i]);
                b[1] = Math.min(b[1], obj.xyz[i + 1]);
                b[4] = Math.max(b[4], obj.xyz[i + 1]);
                b[2] = Math.min(b[2], obj.xyz[i + 2]);
                b[5] = Math.max(b[5], obj.xyz[i + 2]);
            }
            for (int i = 0; i < obj.rndXyz.length; i += 3) {
                b[0] = Math.min(b[0], obj.rndXyz[i]);
                b[3] = Math.max(b[3], obj.rndXyz[i]);
                b[1] = Math.min(b[1], obj.rndXyz[i + 1]);
                b[4] = Math.max(b[4], obj.rndXyz[i + 1]);
                b[2] = Math.min(b[2], obj.rndXyz[i + 2]);
                b[5] = Math.max(b[5], obj.rndXyz[i + 2]);
            }
            return b;
        });
        if (lb.length == 0) {
            return null;
        }
        boolean bo4 = lb[6] != 0;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int[][] corners = {{lb[0], lb[2]}, {lb[3], lb[2]}, {lb[0], lb[5]}, {lb[3], lb[5]}};
        for (int[] c : corners) {
            int[] rc = bo4 ? BORotation.bo4Block(c[0], c[1], rot) : BORotation.bo3Block(c[0], c[1], rot);
            minX = Math.min(minX, rc[0]);
            maxX = Math.max(maxX, rc[0]);
            minZ = Math.min(minZ, rc[1]);
            maxZ = Math.max(maxZ, rc[1]);
        }
        return new int[]{x + minX, y + lb[1], z + minZ, x + maxX, y + lb[4], z + maxZ};
    }

    private int[] worldBounds(Node n) {
        return worldBounds(n.name, n.obj, n.x, n.y, n.z, n.rot);
    }

    private void addToCaches(Node n) {
        this.all.add(n);
        this.allHash.add(n.id);
        this.byChunk.computeIfAbsent(ChunkPos.asLong(n.cx, n.cz), k -> new ArrayList<>()).add(n);
        this.byName.computeIfAbsent(n.name, k -> new ArrayList<>()).add(new int[]{n.cx, n.cz});
    }

    private void removeFromCaches(Node n) {
        this.all.remove(n);
        this.allHash.remove(n.id);
        List<Node> inChunk = this.byChunk.get(ChunkPos.asLong(n.cx, n.cz));
        if (inChunk != null) {
            inChunk.remove(n);
            if (inChunk.isEmpty()) {
                this.byChunk.remove(ChunkPos.asLong(n.cx, n.cz));
            }
        }
        List<int[]> coords = this.byName.get(n.name);
        if (coords != null) {
            // OTG 语义：只删一个条目（同名同 chunk 可多件，如 spike 的 128 条同点 WBR）
            for (int i = 0; i < coords.size(); i++) {
                if (coords.get(i)[0] == n.cx && coords.get(i)[1] == n.cz) {
                    coords.remove(i);
                    break;
                }
            }
            if (coords.isEmpty()) {
                this.byName.remove(n.name);
            }
        }
    }

    /** OTG RandomHelper#getRandomForCoords。 */
    public static Random randomForCoords(int x, int y, int z, long seed) {
        Random r = new Random(seed);
        long l1 = r.nextLong() + 1;
        long l2 = r.nextLong() + 1;
        long l3 = r.nextLong() + 1;
        r.setSeed((x * l1 + y * l2 + z * l3) ^ seed);
        return r;
    }
}
