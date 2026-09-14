package com.mx.underneath.worldgen;

import com.mx.underneath.Underneath;
import com.mx.underneath.bo.BORegistry;
import com.mx.underneath.bo.OTGStructureExpander;
import com.mx.underneath.otg.OTGTerrainMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTG {@code CustomStructurePlotter#plotStructures} 的忠实移植（1.12 源码逐段对应）。
 *
 * <p>原版城市群的真实分布机制：
 * <ul>
 * <li><b>逐 chunk 增量贪心</b>：chunk 首次生成时尝试起结构；无论成败标 plotted，
 *     结构占用的全部 chunk 亦然。rarity=100/frequency=0 下城市尽可能密铺，
 *     间隙由 minimumSize 放不下的碎片空间自然形成（残楼/血湖过渡带）。</li>
 * <li><b>4-pass 空地扫描</b>：从当前 chunk 向四方向扩张量测连续可用区（边界=已占 chunk/无城群系），
 *     区域须容纳结构 minimumSize（required-only 包围盒，WBR 忽略）；随后把包围盒
 *     <b>居中放进空地并保证当前 chunk 在盒内</b>，无 FixedRotation 的结构 4 向随机旋转。</li>
 * <li><b>展开即碰撞</b>：结构分支撞已占 chunk / 无城群系 → required 回滚剪枝 → 城市边缘自然裁齐。</li>
 * <li><b>timesSpawned 均衡</b>：多候选结构按（已生成次数升序、尺寸降序）轮流优先。</li>
 * </ul>
 *
 * <p>状态与 OTG 同构地<b>持久化</b>（{@link CityPlotSavedData}，对应 1.12 的存盘 structurecache）：
 * 此前的"光锥 region 独立模拟拼接"已废——贪心密铺是强耦合系统，先起的城挡后起的城、影响
 * 链式传播任意远，有限光锥不收敛（同构模拟实测 region 拼接 61% chunk 归属不一致=城市被
 * 拼缝撕成 16-chunk 碎片、道路错位断头）。增量贪心的布局随生成触发序而定（同种子重建世界
 * 不同布局）——OTG 原版本就如此（populate rand 非确定）。
 *
 * <p>结构表（数据侧已全量验证）：CityGrid（Y=50 randomY、随机旋转、12 起点群系）、
 * single_skyscraper_spawner_city（highestSolidBlock 65-75、FixedRotation NORTH、见缝插针，
 * 掷中大楼放不下即整体回滚=空地）、GodlySpike-50（Y=55、Underspikes Edge/Pillar）、
 * GodlySpikeCeiling（Y=190、Underspikes Edge）。
 *
 * <p>既知偏差：城市分支伸进"已生成 chunk"的零件份额只能丢弃（OTG 1.12 可直写已生成区块，
 * 1.20 feature 阶段只能写本 chunk）——探索前沿总向未生成方向扩，低频，debug 日志计数。
 */
public final class CityPlotter {

    /** chunk→群系 id path 的查询口（feature 侧用 BiomeSource+sampler 实现）。 */
    public interface BiomeGate {
        String biome(int cx, int cz);
    }

    /** 一个已定位零件。 */
    public record Placement(String name, int x, int y, int z, int rot) {
    }

    /** 结构定义（.bo4 头 + .bc CustomStructure 行的数据快照）。group=BO3Group 组名（null=无），
     *  groupFreq=组内最小中心距（chunk，OTG bo4Groups；>0 才生效）。 */
    private record Def(String root, int fixedRot, boolean surfaceY, int minY, int maxY,
                       int frequency, String group, int groupFreq,
                       Set<String> starts, Set<String> branches) {
    }

    private static final Set<String> CITY_STARTS = Set.of(
            "underneath:underneath", "underneath:underneath_edge", "underneath:underneath_hole",
            "underneath:underneath_pillar", "underneath:corrupted", "underneath:corrupted_edge",
            "underneath:corrupted_pillar", "underneath:defiled_caverns", "underneath:underforest",
            "underneath:underspikes", "underneath:underspikes_edge", "underneath:underspikes_pillar");
    /** 分支许可=起点群系+rarity 0 的 Remnants Outter Edge（城可伸入但不起点；远古遗迹群系无城）。 */
    private static final Set<String> CITY_BRANCHES;

    static {
        Set<String> s = new HashSet<>(CITY_STARTS);
        s.add("underneath:remnants_outer_edge");
        CITY_BRANCHES = Set.copyOf(s);
    }

    private static final Set<String> SPIKE_BIOMES = Set.of(
            "underneath:underspikes_edge", "underneath:underspikes_pillar", "underneath:underspikes");

    private static final List<Def> DEFS = List.of(
            new Def("citygrid", -1, false, 50, 50, 0, null, 0, CITY_STARTS, CITY_BRANCHES),
            new Def("single_skyscraper_spawner_city", 0, true, 65, 75, 0, null, 0, CITY_STARTS, CITY_BRANCHES),
            // access_duct：锚在穹顶顶面(highestSolidBlock 241-255)、舱体经 y-110 分支悬在主腔上空 Y~141；
            // BO3Group access_duct:10=组内中心距≥10 chunk（没有它=舱密铺碎化空间→城市全灭，实测教训）
            new Def("access_duct", 0, true, 241, 255, 0, "access_duct", 10, CITY_BRANCHES, CITY_BRANCHES),
            // NewCrystals:0=组频率 0 无效果（OTG >0 才登记）；spike 自身 Frequency:1
            new Def("godlyspike-50", -1, false, 55, 55, 1, null, 0,
                    Set.of("underneath:underspikes_edge", "underneath:underspikes_pillar"), SPIKE_BIOMES),
            new Def("godlyspikeceiling", -1, false, 190, 190, 0, null, 0,
                    Set.of("underneath:underspikes_edge"), SPIKE_BIOMES));

    private static final int BIOME_CACHE_CAP = 100_000;
    private static final Map<String, OTGStructureExpander.MinSize> MINSIZE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, OTGTerrainMath> TERRAIN_CACHE = new ConcurrentHashMap<>();

    private CityPlotter() {
    }

    /** 本 chunk 应放置的结构零件（空=无）。chunk 首次生成时增量做 OTG 起点尝试并领取 pending。 */
    public static List<Placement> chunkPieces(ServerLevel level, int cx, int cz, BiomeGate gate) {
        CityPlotSavedData data = CityPlotSavedData.get(level);
        synchronized (data) {
            long ck = ChunkPos.asLong(cx, cz);
            if (!data.plotted.contains(ck)) {
                tryChunk(data, cx, cz, level.getSeed(), gate);
                data.plotted.add(ck);
            }
            data.generated.add(ck);
            data.setDirty();
            List<Placement> out = data.pending.remove(ck);
            if (out != null) {
                data.setDirty();
                return out;
            }
            return List.of();
        }
    }

    /** OTG plotStructures 单 chunk 逻辑：群系候选 → timesSpawned/尺寸排序 → 逐结构尝试。 */
    private static void tryChunk(CityPlotSavedData data, int cx, int cz, long seed, BiomeGate rawGate) {
        if (data.biomeCache.size() > BIOME_CACHE_CAP) {
            data.biomeCache.clear();
        }
        BiomeGate gate = (ccx, ccz) -> {
            long key = ChunkPos.asLong(ccx, ccz);
            String b = data.biomeCache.get(key);
            if (b == null) {
                b = rawGate.biome(ccx, ccz);
                data.biomeCache.put(key, b);
            }
            return b;
        };
        String biome = gate.biome(cx, cz);
        List<Def> candidates = new ArrayList<>(2);
        for (Def d : DEFS) {
            if (d.starts().contains(biome)) {
                candidates.add(d);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        // OTG bo4sBySize 插入排序：timesSpawned 少者优先，其次尺寸大者优先
        candidates.sort((a, b) -> {
            int ta = data.timesSpawned.getOrDefault(a.root(), 0);
            int tb = data.timesSpawned.getOrDefault(b.root(), 0);
            if (ta != tb) {
                return Integer.compare(ta, tb);
            }
            return Integer.compare(sizeArea(b), sizeArea(a));
        });
        Random plotRand = OTGStructureExpander.randomForCoords((cx << 4) + 8, 1, (cz << 4) + 7, seed);
        long ck = ChunkPos.asLong(cx, cz);
        for (Def def : candidates) {
            if (tryPlotAt(data, def, cx, cz, seed, plotRand, gate) && data.occupied.contains(ck)) {
                return;
            }
        }
    }

    /** OTG plotStructures 单结构尝试：4-pass 扫描 → fit → 旋转/落点 → 展开。返回是否成功生成。 */
    private static boolean tryPlotAt(CityPlotSavedData data, Def def, int cx, int cz, long seed,
                                     Random plotRand, BiomeGate gate) {
        OTGStructureExpander.MinSize ms = minSize(def.root());
        int structureLength = ms.right() + ms.left() + 1;
        int structureWidth = ms.top() + ms.bottom() + 1;

        for (int pass = 1; pass <= 4; pass++) {
            int[] area = scanArea(pass, cx, cz, def, structureLength, structureWidth, data, gate);
            int left = area[0], right = area[1], top = area[2], bottom = area[3];
            int areaLength = left + right + 1;
            int areaWidth = top + bottom + 1;

            boolean fitsNS = structureLength <= areaLength && structureWidth <= areaWidth;
            boolean fitsEW = structureLength <= areaWidth && structureWidth <= areaLength;
            boolean fits = def.fixedRot() < 0 ? (fitsNS || fitsEW)
                    : (def.fixedRot() == 0 || def.fixedRot() == 2) ? fitsNS : fitsEW;
            if (!fits) {
                continue;   // 本 pass 面积不足，换扩张方向再试
            }

            // 旋转决策（OTG：能转就 4 选 1；只满足横放则 E/W 二选一）
            int rotation;
            if (def.fixedRot() >= 0) {
                rotation = def.fixedRot();
            } else {
                rotation = plotRand.nextBoolean() ? 0 : 2;
                if (fitsNS && fitsEW) {
                    rotation = plotRand.nextInt(4);
                } else if (fitsEW) {
                    rotation = plotRand.nextBoolean() ? 3 : 1;
                }
            }
            int lenRot = (rotation == 0 || rotation == 2) ? structureLength : structureWidth;
            int widRot = (rotation == 0 || rotation == 2) ? structureWidth : structureLength;

            // 包围盒居中放进空地，且当前 chunk 必在盒内（OTG 928-946 公式）
            int bbX = (int) Math.floor(cx - left + ((left + right + 1) / 2d) - (lenRot / 2d));
            if (bbX > cx) {
                bbX = cx;
            } else if (bbX + lenRot < cx) {
                bbX = cx - lenRot + 1;
            }
            int bbZ = (int) Math.floor(cz - top + ((top + bottom + 1) / 2d) - (widRot / 2d));
            if (bbZ > cz) {
                bbZ = cz;
            } else if (bbZ + widRot < cz) {
                bbZ = cz - widRot + 1;
            }
            // OTG 旋转序 N0→W1→S2→E3（Rotation.EAST=OTG 序 3）
            int spawnCx = bbX + (rotation == 0 ? ms.left() : rotation == 3 ? ms.bottom()
                    : rotation == 2 ? ms.right() : ms.top());
            int spawnCz = bbZ + (rotation == 0 ? ms.top() : rotation == 3 ? ms.left()
                    : rotation == 2 ? ms.bottom() : ms.right());

            // 结构中心（OTG bo4CenterSpawnCoord：组间距按中心记）
            int centerCx = bbX + (int) Math.floor(((rotation == 0 || rotation == 2)
                    ? ms.left() + ms.right() + 1 : ms.bottom() + ms.top() + 1) / 2d);
            int centerCz = bbZ + (int) Math.floor(((rotation == 0 || rotation == 2)
                    ? ms.top() + ms.bottom() + 1 : ms.left() + ms.right() + 1) / 2d);
            if (allowedByFrequency(data, def, spawnCx, spawnCz, centerCx, centerCz)) {
                int startX = spawnCx << 4;
                int startZ = spawnCz << 4;
                int startY = resolveStartY(def, startX, startZ, seed);
                if (startY >= 0) {
                    OTGStructureExpander.Env env = new OTGStructureExpander.Env() {
                        @Override
                        public boolean isChunkOccupied(int ccx, int ccz) {
                            return data.occupied.contains(ChunkPos.asLong(ccx, ccz));
                        }

                        @Override
                        public boolean biomeAllowsBranch(int ccx, int ccz) {
                            return def.branches().contains(gate.biome(ccx, ccz));
                        }
                    };
                    // OTG：结构 RNG 按起点坐标+种子派生，y 此刻恒为 0（start.y 在 checks 后才写入）
                    Random structRng = OTGStructureExpander.randomForCoords(startX + 8, 0, startZ + 7, seed);
                    OTGStructureExpander.Result res = OTGStructureExpander.expand(
                            BORegistry.INSTANCE, def.root(), startX, startY, startZ, rotation, structRng, env);
                    if (res.spawned()) {
                        // 分桶必须先于 plotted.addAll：件份额落在"此前已生成"的 chunk 只能丢弃
                        int dropped = binPending(data, res.pieces());
                        data.occupied.addAll(res.chunks());
                        data.plotted.addAll(res.chunks());
                        data.timesSpawned.merge(def.root(), 1, Integer::sum);
                        if (def.frequency() > 0) {
                            data.startsByName.computeIfAbsent(def.root(), k -> new ArrayList<>())
                                    .add(new int[]{spawnCx, spawnCz});
                        }
                        if (def.group() != null && def.groupFreq() > 0) {
                            data.spawnedByGroup.computeIfAbsent(def.group(), k -> new ArrayList<>())
                                    .add(new int[]{centerCx, centerCz, def.groupFreq()});
                        }
                        data.setDirty();
                        if (dropped > 0) {
                            Underneath.LOGGER.debug("[city] {} @chunk({},{}) 伸入已生成区，丢弃 {} 份零件份额",
                                    def.root(), spawnCx, spawnCz, dropped);
                        }
                        return true;
                    }
                }
            }
            return false;   // OTG：找到足够面积并尝试过就停止扫描（无论成败）→ 换下一结构
        }
        return false;
    }

    /** OTG 4-pass 空地扫描（含其 fixedRotation 提前退出怪癖，忠实照抄）。返回 {left,right,top,bottom}。 */
    private static int[] scanArea(int pass, int cx, int cz, Def def, int structureLength, int structureWidth,
                                  CityPlotSavedData data, BiomeGate gate) {
        int left = 0, right = 0, top = 0, bottom = 0;
        boolean leftF = pass == 2 || pass == 4;
        boolean rightF = pass == 1 || pass == 3;
        boolean topF = pass == 1 || pass == 2;
        boolean bottomF = pass == 3 || pass == 4;
        int fixedRot = def.fixedRot();
        int scan = 0;
        while (!(leftF && rightF && topF && bottomF)) {
            scan++;
            if (!rightF && xSatisfied(fixedRot, right + left + 1, structureLength, structureWidth)) {
                rightF = true;
            }
            if (!rightF) {
                for (int i = -top; i <= bottom; i++) {
                    if (blocked(def, cx + scan, cz + i, data, gate)) {
                        rightF = true;
                        break;
                    }
                }
                if (!rightF) {
                    right++;
                }
            }
            if (!leftF && xSatisfied(fixedRot, right + left + 1, structureLength, structureWidth)) {
                leftF = true;
            }
            if (!leftF) {
                for (int i = -top; i <= bottom; i++) {
                    if (blocked(def, cx - scan, cz + i, data, gate)) {
                        leftF = true;
                        break;
                    }
                }
                if (!leftF) {
                    left++;
                }
            }
            if (!bottomF && zSatisfied(fixedRot, bottom + top + 1, structureLength, structureWidth)) {
                bottomF = true;
            }
            if (!bottomF) {
                for (int i = -left; i <= right; i++) {
                    if (blocked(def, cx + i, cz + scan, data, gate)) {
                        bottomF = true;
                        break;
                    }
                }
                if (!bottomF) {
                    bottom++;
                }
            }
            if (!topF && zSatisfied(fixedRot, bottom + top + 1, structureLength, structureWidth)) {
                topF = true;
            }
            if (!topF) {
                for (int i = -left; i <= right; i++) {
                    if (blocked(def, cx + i, cz - scan, data, gate)) {
                        topF = true;
                        break;
                    }
                }
                if (!topF) {
                    top++;
                }
            }
        }
        return new int[]{left, right, top, bottom};
    }

    private static boolean xSatisfied(int fixedRot, int span, int structureLength, int structureWidth) {
        if (fixedRot < 0) {
            return span >= structureWidth && span >= structureLength;
        }
        return (fixedRot == 0 || fixedRot == 2) ? span >= structureWidth : span >= structureLength;
    }

    private static boolean zSatisfied(int fixedRot, int span, int structureLength, int structureWidth) {
        if (fixedRot < 0) {
            return span >= structureWidth && span >= structureLength;
        }
        return (fixedRot == 0 || fixedRot == 2) ? span >= structureLength : span >= structureWidth;
    }

    /** 扫描边界：已处理/已占用 chunk，或该 chunk 群系的结构名单不含本结构（rarity 0 也算在名单内）。 */
    private static boolean blocked(Def def, int cx, int cz, CityPlotSavedData data, BiomeGate gate) {
        long ck = ChunkPos.asLong(cx, cz);
        if (data.plotted.contains(ck) || data.occupied.contains(ck)) {
            return true;
        }
        return !def.branches().contains(gate.biome(cx, cz));
    }

    /** OTG isBO4AllowedToSpawnAtByFrequency：同名最小间距 + BO3Group 组内中心距（access_duct:10 靠它稀疏）。 */
    private static boolean allowedByFrequency(CityPlotSavedData data, Def def,
                                              int spawnCx, int spawnCz, int centerCx, int centerCz) {
        if (def.frequency() > 0) {
            List<int[]> coords = data.startsByName.get(def.root());
            if (coords != null) {
                for (int[] c : coords) {
                    double d = Math.floor(Math.sqrt(Math.pow(spawnCx - c[0], 2) + Math.pow(spawnCz - c[1], 2)));
                    if (d <= def.frequency()) {
                        return false;
                    }
                }
            }
        }
        if (def.group() != null && def.groupFreq() > 0) {
            List<int[]> centers = data.spawnedByGroup.get(def.group());
            if (centers != null) {
                for (int[] c : centers) {
                    int radius = Math.max(def.groupFreq(), c[2]);
                    double d = Math.floor(Math.sqrt(Math.pow(centerCx - c[0], 2) + Math.pow(centerCz - c[1], 2)));
                    if (d <= radius) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** 起点 Y：randomY(min==max)=常数；highestSolidBlock=按 OTG 地形密度求地表+1，越界=-1 失败。 */
    private static int resolveStartY(Def def, int startX, int startZ, long seed) {
        if (!def.surfaceY()) {
            return def.minY();
        }
        // UseCenterForHighestBlock：取起点 chunk 中心列（minimumSize 未导出时 OTG 即此列）。
        // 忠实 OTG：从世界顶向下找第一个实心。spawner_city(65-75)：穹顶完整列 highest≈250 → 校验失败，
        // 只有穹顶破洞、直见主腔地板的列才出独立残楼；access_duct(241-255)：正好落穹顶顶面。
        OTGTerrainMath math = TERRAIN_CACHE.computeIfAbsent(seed, OTGTerrainMath::new);
        int x = startX + 8;
        int z = startZ + 7;
        if (def.maxY() < 196) {
            // 目标在主腔内：穹顶带粗采样（步长 8）fail-fast，命中实心=穹顶完整=该列必失败
            for (int y = 252; y >= 196; y -= 8) {
                if (math.densityBlock(x, y, z) > 0) {
                    return -1;
                }
            }
        }
        for (int y = 259; y >= 40; y--) {
            if (math.densityBlock(x, y, z) > 0) {
                int startY = y + 1;
                return (startY < def.minY() || startY > def.maxY()) ? -1 : startY;
            }
        }
        return -1;
    }

    private static int sizeArea(Def def) {
        OTGStructureExpander.MinSize ms = minSize(def.root());
        return (ms.right() + ms.left() + 1) * (ms.top() + ms.bottom() + 1);
    }

    private static OTGStructureExpander.MinSize minSize(String root) {
        return MINSIZE_CACHE.computeIfAbsent(root,
                r -> OTGStructureExpander.minimumSize(BORegistry.INSTANCE, r));
    }

    /** 零件按其世界 AABB 覆盖的 chunk 分桶入 pending；"此前已生成"chunk 的份额丢弃（返回丢弃数）。 */
    private static int binPending(CityPlotSavedData data, List<OTGStructureExpander.Piece> pieces) {
        int dropped = 0;
        for (OTGStructureExpander.Piece p : pieces) {
            int[] wb = OTGStructureExpander.worldBounds(p.name(), BORegistry.INSTANCE.get(p.name()),
                    p.x(), p.y(), p.z(), p.rot());
            if (wb == null) {
                continue;   // 纯分支容器（0 方块）无需放置
            }
            for (int ccx = wb[0] >> 4; ccx <= wb[3] >> 4; ccx++) {
                for (int ccz = wb[2] >> 4; ccz <= wb[5] >> 4; ccz++) {
                    long ck = ChunkPos.asLong(ccx, ccz);
                    if (data.generated.contains(ck)) {
                        dropped++;   // 该 chunk 的 feature 已执行过，写不进去（OTG 可直写，1.20 不能）
                        continue;
                    }
                    data.pending.computeIfAbsent(ck, k -> new ArrayList<>())
                            .add(new Placement(p.name(), p.x(), p.y(), p.z(), p.rot()));
                }
            }
        }
        return dropped;
    }
}
