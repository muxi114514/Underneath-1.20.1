package com.mx.underneath.bo;

import com.mx.underneath.Underneath;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * L1.5 刚性分支扁平化：从根对象出发，递归展开<b>确定性</b>分支
 * （required 行 / 非 required 单节点几率拉满），把整棵拼装树合并成一份平坦方块列表。
 * 随机选楼层（多节点池/WeightedBranch）不展开、只计数——那是 L2 拼装器的活。
 *
 * <p>坐标/旋转严格走 {@link BORotation}（镜像 OTG）：子原点=父原点+分支偏移按父旋转变换；
 * 子旋转=节点旋转+父旋转（mod 4）；方块按各自文件类型（BO3/BO4）的变换旋转。
 *
 * <p>护栏：深度 ≤{@value #MAX_DEPTH}（数据里 MaxBranchDepth=10）；方块预算 {@value #MAX_BLOCKS}
 * 超限即截断置 truncated（防环/防爆内存）。
 */
public final class BOFlattener {

    private static final int MAX_DEPTH = 24;
    private static final int MAX_BLOCKS = 4_000_000;

    /** 扁平化结果（坐标已含全部旋转，放置时不得再按文件类型旋转）。
     *  rndNbt=随机块每候选的 nbt 全路径（与 rndChoices 平行；全无时长度 0）。 */
    public record Flattened(BlockState[] palette, int[] xyz, int[] stateIdx,
                            int[] rndXyz, int[][] rndChoices, String[][] rndNbt,
                            int[] nbtBlockIdx, String[] nbtPaths,
                            int expandedPieces, int unresolvedLines, int missingObjects, boolean truncated) {

        public int blockCount() {
            return this.stateIdx.length;
        }
    }

    /** 可增长 int 缓冲（避免 4M 级 ArrayList&lt;int[]&gt; 对象海）。 */
    private static final class IntBuf {
        int[] a = new int[1024];
        int n = 0;

        void add(int v) {
            if (this.n == this.a.length) {
                this.a = Arrays.copyOf(this.a, this.a.length << 1);
            }
            this.a[this.n++] = v;
        }

        int[] toArray() {
            return Arrays.copyOf(this.a, this.n);
        }
    }

    private final BORegistry registry;
    private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
    private final List<BlockState> palette = new ArrayList<>();
    private final IntBuf xyz = new IntBuf();
    private final IntBuf stateIdx = new IntBuf();
    private final IntBuf rndXyz = new IntBuf();
    private final List<int[]> rndChoices = new ArrayList<>();
    private final List<String[]> rndNbt = new ArrayList<>();
    private boolean anyRndNbt = false;
    private final IntBuf nbtBlockIdx = new IntBuf();
    private final List<String> nbtPaths = new ArrayList<>();
    private int expandedPieces = 0;
    private int unresolvedLines = 0;
    private int missingObjects = 0;
    private boolean truncated = false;

    private BOFlattener(BORegistry registry) {
        this.registry = registry;
    }

    public static Flattened flatten(BORegistry registry, String rootName) {
        BOFlattener f = new BOFlattener(registry);
        f.emit(rootName.toLowerCase(java.util.Locale.ROOT), 0, 0, 0, 0, 0);
        return new Flattened(f.palette.toArray(new BlockState[0]), f.xyz.toArray(), f.stateIdx.toArray(),
                f.rndXyz.toArray(), f.rndChoices.toArray(new int[0][]),
                f.anyRndNbt ? f.rndNbt.toArray(new String[0][]) : new String[0][],
                f.nbtBlockIdx.toArray(), f.nbtPaths.toArray(new String[0]),
                f.expandedPieces, f.unresolvedLines, f.missingObjects, f.truncated);
    }

    private void emit(String name, int ox, int oy, int oz, int rot, int depth) {
        if (this.truncated) {
            return;
        }
        if (depth > MAX_DEPTH || this.stateIdx.n > MAX_BLOCKS) {
            this.truncated = true;
            Underneath.LOGGER.warn("BO 扁平化截断（depth={}, blocks={}）于 {}", depth, this.stateIdx.n, name);
            return;
        }
        BOObject obj = this.registry.get(name);
        if (obj == null) {
            this.missingObjects++;
            return;
        }
        this.expandedPieces++;

        // 本次访问的旋转后调色板（每对象每访问算一遍，调色板极小）
        Rotation stateRot = BORotation.stateRotation(rot, obj.bo4);
        int[] mapped = new int[obj.palette.length];
        for (int i = 0; i < obj.palette.length; i++) {
            mapped[i] = globalPaletteOf(stateRot == Rotation.NONE ? obj.palette[i] : obj.palette[i].rotate(stateRot));
        }

        for (int i = 0; i < obj.stateIdx.length; i++) {
            int[] rc = obj.bo4
                    ? BORotation.bo4Block(obj.xyz[i * 3], obj.xyz[i * 3 + 2], rot)
                    : BORotation.bo3Block(obj.xyz[i * 3], obj.xyz[i * 3 + 2], rot);
            int blockIndex = this.stateIdx.n;
            this.xyz.add(ox + rc[0]);
            this.xyz.add(oy + obj.xyz[i * 3 + 1]);
            this.xyz.add(oz + rc[1]);
            this.stateIdx.add(mapped[obj.stateIdx[i]]);
            // 方块实体 nbt：在扁平化期解析成全路径（相对该 BO 文件目录，含 ../.. 上溯）
            if (obj.nbtNames.length > 0 && obj.nbtNames[i] != null) {
                String resolved = this.registry.resolveNbtPath(obj.sourceDir, obj.nbtNames[i]);
                if (resolved != null) {
                    this.nbtBlockIdx.add(blockIndex);
                    this.nbtPaths.add(resolved);
                }
            }
        }
        for (int i = 0; i < obj.rndChoices.length; i++) {
            int[] rc = obj.bo4
                    ? BORotation.bo4Block(obj.rndXyz[i * 3], obj.rndXyz[i * 3 + 2], rot)
                    : BORotation.bo3Block(obj.rndXyz[i * 3], obj.rndXyz[i * 3 + 2], rot);
            this.rndXyz.add(ox + rc[0]);
            this.rndXyz.add(oy + obj.rndXyz[i * 3 + 1]);
            this.rndXyz.add(oz + rc[1]);
            int[] src = obj.rndChoices[i];
            int[] dst = new int[src.length];
            for (int j = 0; j + 1 < src.length; j += 2) {
                dst[j] = mapped[src[j]];
                dst[j + 1] = src[j + 1];
            }
            this.rndChoices.add(dst);
            // 随机块每候选 nbt（chestfiller 楼板宝箱在此）：扁平化期解析成全路径
            String[] nbts = null;
            if (i < obj.rndNbt.length && obj.rndNbt[i] != null) {
                for (int c = 0; c < obj.rndNbt[i].length; c++) {
                    if (obj.rndNbt[i][c] != null) {
                        String resolved = this.registry.resolveNbtPath(obj.sourceDir, obj.rndNbt[i][c]);
                        if (resolved != null) {
                            if (nbts == null) {
                                nbts = new String[obj.rndNbt[i].length];
                            }
                            nbts[c] = resolved;
                            this.anyRndNbt = true;
                        }
                    }
                }
            }
            this.rndNbt.add(nbts == null ? new String[0] : nbts);
        }

        for (BOObject.BranchLine line : obj.branches) {
            if (!line.deterministic()) {
                this.unresolvedLines++;
                continue;
            }
            BOObject.BranchNode node = line.nodes().get(0);
            int[] off = BORotation.branchOffset(line.x(), line.z(), rot);
            emit(node.name(), ox + off[0], oy + line.y(), oz + off[1], (rot + node.rotId()) & 3, depth + 1);
        }
    }

    private int globalPaletteOf(BlockState state) {
        Integer idx = this.paletteIndex.get(state);
        if (idx == null) {
            idx = this.palette.size();
            this.palette.add(state);
            this.paletteIndex.put(state, idx);
        }
        return idx;
    }
}
