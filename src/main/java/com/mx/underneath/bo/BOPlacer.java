package com.mx.underneath.bo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扁平化 BO 的统一落块器：placebo 指令与 worldgen Feature 共用。
 *
 * <p>{@link TileEntityConverter} 已泛化到 {@code LevelAccessor}，故 worldgen(WorldGenRegion) 与
 * placebo(ServerLevel) 都能套方块实体 NBT（战利品箱/刷怪笼/旗帜）；运行时补包仅在 ServerLevel 触发。
 */
public final class BOPlacer {

    /** placed=落块数，nbtApplied=方块实体已应用，nbtSkipped=保留字段(现恒 0)。 */
    public record Result(int placed, int nbtApplied, int nbtSkipped) {
    }

    private BOPlacer() {
    }

    public static Result place(LevelAccessor level, BlockPos origin, BOFlattener.Flattened flat,
                               int rotSteps, RandomSource random) {
        // 旋转施加在整块扁平结果上（分支式坐标变换 + 同向方块态旋转）
        Rotation stateRot = BORotation.stateRotation(rotSteps, false);
        BlockState[] palette = flat.palette();
        BlockState[] rotated = new BlockState[palette.length];
        for (int i = 0; i < palette.length; i++) {
            rotated[i] = stateRot == Rotation.NONE ? palette[i] : palette[i].rotate(stateRot);
        }

        Map<Integer, String> nbtByIndex = new HashMap<>();
        for (int k = 0; k < flat.nbtBlockIdx().length; k++) {
            nbtByIndex.put(flat.nbtBlockIdx()[k], flat.nbtPaths()[k]);
        }

        int placed = 0;
        int nbtApplied = 0;
        List<BlockPos> fix = new ArrayList<>();
        int[] xyz = flat.xyz();
        int[] idx = flat.stateIdx();
        for (int i = 0; i < idx.length; i++) {
            int[] rc = BORotation.branchOffset(xyz[i * 3], xyz[i * 3 + 2], rotSteps);
            BlockPos placePos = origin.offset(rc[0], xyz[i * 3 + 1], rc[1]);
            BlockState st = rotated[idx[i]];
            level.setBlock(placePos, st, 2);
            placed++;
            if (needsConnectionFix(st)) {
                fix.add(placePos);
            }
            String nbtPath = nbtByIndex.get(i);
            if (nbtPath != null) {
                // TileEntityConverter 已泛化到 LevelAccessor：worldgen(WorldGenRegion) 与 placebo(ServerLevel) 通用
                var tag = BORegistry.INSTANCE.loadNbt(nbtPath);
                if (tag != null) {
                    TileEntityConverter.apply(level, placePos, tag);
                    nbtApplied++;
                }
            }
        }
        int[] rXyz = flat.rndXyz();
        int[][] rChoices = flat.rndChoices();
        for (int i = 0; i < rChoices.length; i++) {
            int c = rollRandom(rChoices[i], random);
            if (c >= 0) {
                int[] rc = BORotation.branchOffset(rXyz[i * 3], rXyz[i * 3 + 2], rotSteps);
                BlockPos pos = origin.offset(rc[0], rXyz[i * 3 + 1], rc[1]);
                BlockState st = rotated[rChoices[i][c * 2]];
                level.setBlock(pos, st, 2);
                placed++;
                if (needsConnectionFix(st)) {
                    fix.add(pos);
                }
                if (applyFlatRndNbt(level, pos, flat, i, c)) {
                    nbtApplied++;
                } else {
                    fixBareSpawner(level, pos, st);
                }
            }
        }
        fixupConnections(level, fix);
        return new Result(placed, nbtApplied, 0);
    }

    /** Flattened 随机块候选 nbt（扁平化期已解析成全路径）应用；无 nbt/加载失败返回 false。 */
    private static boolean applyFlatRndNbt(LevelAccessor level, BlockPos pos,
                                           BOFlattener.Flattened flat, int rndIdx, int choiceIdx) {
        String[][] rn = flat.rndNbt();
        if (rndIdx >= rn.length || choiceIdx >= rn[rndIdx].length || rn[rndIdx][choiceIdx] == null) {
            return false;
        }
        CompoundTag tag = BORegistry.INSTANCE.loadNbt(rn[rndIdx][choiceIdx]);
        if (tag == null) {
            return false;
        }
        TileEntityConverter.apply(level, pos, tag);
        return true;
    }

    /**
     * 只放一个零件<b>自身</b>的方块（不含分支）——L2 城市逐零件落地用。
     * 分支由 {@link OTGStructureExpander} 展开成独立零件，各自调本方法。
     */
    public static int placeOwnBlocks(LevelAccessor level, BlockPos origin, BOObject obj,
                                     int rot, RandomSource random) {
        Rotation stateRot = BORotation.stateRotation(rot, obj.bo4);
        BlockState[] rotated = new BlockState[obj.palette.length];
        for (int i = 0; i < rotated.length; i++) {
            rotated[i] = stateRot == Rotation.NONE ? obj.palette[i] : obj.palette[i].rotate(stateRot);
        }
        int placed = 0;
        List<BlockPos> fix = new ArrayList<>();
        int[] xyz = obj.xyz;
        int[] idx = obj.stateIdx;
        for (int i = 0; i < idx.length; i++) {
            int[] rc = obj.bo4
                    ? BORotation.bo4Block(xyz[i * 3], xyz[i * 3 + 2], rot)
                    : BORotation.bo3Block(xyz[i * 3], xyz[i * 3 + 2], rot);
            BlockPos pos = origin.offset(rc[0], xyz[i * 3 + 1], rc[1]);
            BlockState st = rotated[idx[i]];
            level.setBlock(pos, st, 2);
            if (needsConnectionFix(st)) {
                fix.add(pos);
            }
            placed++;
        }
        int[] rx = obj.rndXyz;
        for (int i = 0; i < obj.rndChoices.length; i++) {
            int c = rollRandom(obj.rndChoices[i], random);
            if (c >= 0) {
                int[] rc = obj.bo4
                        ? BORotation.bo4Block(rx[i * 3], rx[i * 3 + 2], rot)
                        : BORotation.bo3Block(rx[i * 3], rx[i * 3 + 2], rot);
                BlockPos pos = origin.offset(rc[0], rx[i * 3 + 1], rc[1]);
                BlockState st = rotated[obj.rndChoices[i][c * 2]];
                level.setBlock(pos, st, 2);
                if (needsConnectionFix(st)) {
                    fix.add(pos);
                }
                if (!applyRndNbt(level, pos, obj, i, c)) {
                    fixBareSpawner(level, pos, st);
                }
                placed++;
            }
        }
        fixupConnections(level, fix);
        return placed;
    }

    /**
     * 放一个零件自身方块，但**只放落在 {@code box}（当前 chunk 写入区）内的**——
     * 原版 Structure 逐 chunk 落地用（一栋楼跨多 chunk 时，各 chunk 各放自己那部分）。
     */
    public static void placeOwnBlocksClipped(net.minecraft.world.level.LevelAccessor level, BlockPos origin,
                                             BOObject obj, int rot, RandomSource random,
                                             net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        Rotation stateRot = BORotation.stateRotation(rot, obj.bo4);
        BlockState[] rotated = new BlockState[obj.palette.length];
        for (int i = 0; i < rotated.length; i++) {
            rotated[i] = stateRot == Rotation.NONE ? obj.palette[i] : obj.palette[i].rotate(stateRot);
        }
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        List<BlockPos> fix = new ArrayList<>();
        int[] xyz = obj.xyz;
        int[] idx = obj.stateIdx;
        for (int i = 0; i < idx.length; i++) {
            int[] rc = obj.bo4
                    ? BORotation.bo4Block(xyz[i * 3], xyz[i * 3 + 2], rot)
                    : BORotation.bo3Block(xyz[i * 3], xyz[i * 3 + 2], rot);
            mp.set(origin.getX() + rc[0], origin.getY() + xyz[i * 3 + 1], origin.getZ() + rc[1]);
            if (box.isInside(mp)) {
                BlockState st = rotated[idx[i]];
                level.setBlock(mp, st, 2);
                if (needsConnectionFix(st)) {
                    fix.add(mp.immutable());
                }
                // 套方块实体 NBT（城市战利品箱/刷怪笼/旗帜等）——TileEntityConverter 已泛化到 worldgen
                if (i < obj.nbtNames.length && obj.nbtNames[i] != null) {
                    String path = BORegistry.INSTANCE.resolveNbtPath(obj.sourceDir, obj.nbtNames[i]);
                    CompoundTag tag = path == null ? null : BORegistry.INSTANCE.loadNbt(path);
                    if (tag != null) {
                        TileEntityConverter.apply(level, mp.immutable(), tag);
                    }
                }
            }
        }
        int[] rx = obj.rndXyz;
        for (int i = 0; i < obj.rndChoices.length; i++) {
            int c = rollRandom(obj.rndChoices[i], random);
            if (c >= 0) {
                int[] rc = obj.bo4
                        ? BORotation.bo4Block(rx[i * 3], rx[i * 3 + 2], rot)
                        : BORotation.bo3Block(rx[i * 3], rx[i * 3 + 2], rot);
                mp.set(origin.getX() + rc[0], origin.getY() + rx[i * 3 + 1], origin.getZ() + rc[1]);
                if (box.isInside(mp)) {
                    BlockState st = rotated[obj.rndChoices[i][c * 2]];
                    level.setBlock(mp, st, 2);
                    if (needsConnectionFix(st)) {
                        fix.add(mp.immutable());
                    }
                    if (!applyRndNbt(level, mp.immutable(), obj, i, c)) {
                        fixBareSpawner(level, mp.immutable(), st);
                    }
                }
            }
        }
        fixupConnections(level, fix);
    }

    /**
     * OTG {@code BO3.trySpawnAt} 语义的带检查放置（Tree/CustomObject 散布用）：
     * ①<b>populate 域检查</b>（OTG IsInAreaBeingPopulated：对象必须整体落在 popChunk..+1 的
     * 2×2 chunk 区内，出界=整体放弃——30×30 的 destructor 在原版正是被它压成窄窗低频，
     * 缺了它城市会被炸成筛子）；②BlockCheck/BlockCheckNot 前提（坐标随旋转变换）；
     * ③SourceBlocks 逐块统计——outside 超 MaxPercentageOutsideSourceBlock 预算则<b>整体放弃</b>；
     * ④放置时 dontPlace 模式跳过 outside 位置的方块。返回是否生成。
     *
     * @param popMinX populate 区最小方块 X（=触发 chunk 的 minBlockX）；域为 [popMinX, popMinX+31]
     * @param popMinZ 同上 Z
     */
    public static boolean tryPlaceOtg(LevelAccessor level, BlockPos origin, BOFlattener.Flattened flat,
                                      BOObject obj, int rotSteps, RandomSource random,
                                      int popMinX, int popMinZ) {
        int[] wb = OTGStructureExpander.worldBounds(obj.name, obj,
                origin.getX(), origin.getY(), origin.getZ(), rotSteps);
        if (wb != null && (wb[0] < popMinX || wb[3] > popMinX + 31 || wb[2] < popMinZ || wb[5] > popMinZ + 31)) {
            return false;
        }
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (BOObject.BlockCheckLine c : obj.checks) {
            int[] rc = BORotation.bo3Block(c.x(), c.z(), rotSteps);
            BlockState s = level.getBlockState(mp.set(origin.getX() + rc[0], origin.getY() + c.y(), origin.getZ() + rc[1]));
            // BC：位置方块不在清单 → 阻止；BCN：在清单 → 阻止
            if (c.not() == c.matcher().matches(s)) {
                return false;
            }
        }

        int[] xyz = flat.xyz();
        int[] idx = flat.stateIdx();
        int[] rXyz = flat.rndXyz();
        int[][] rChoices = flat.rndChoices();
        int n = idx.length;
        int rn = rChoices.length;
        boolean[] skip = null;
        if (!obj.skipSourceCheck() && n + rn > 0) {
            skip = new boolean[n + rn];
            int budget = (int) ((n + rn) * (obj.maxPctOutside / 100.0));
            boolean limit = obj.maxPctOutside < 100;
            for (int i = 0; i < n + rn; i++) {
                int bx = i < n ? xyz[i * 3] : rXyz[(i - n) * 3];
                int by = i < n ? xyz[i * 3 + 1] : rXyz[(i - n) * 3 + 1];
                int bz = i < n ? xyz[i * 3 + 2] : rXyz[(i - n) * 3 + 2];
                int[] rc = BORotation.branchOffset(bx, bz, rotSteps);
                boolean outside = !obj.sourceBlocks.matches(
                        level.getBlockState(mp.set(origin.getX() + rc[0], origin.getY() + by, origin.getZ() + rc[1])));
                if (outside && limit && --budget < 0) {
                    return false;
                }
                skip[i] = outside && !obj.outsidePlaceAnyway;
            }
        }

        Rotation stateRot = BORotation.stateRotation(rotSteps, false);
        BlockState[] palette = flat.palette();
        BlockState[] rotated = new BlockState[palette.length];
        for (int i = 0; i < palette.length; i++) {
            rotated[i] = stateRot == Rotation.NONE ? palette[i] : palette[i].rotate(stateRot);
        }
        Map<Integer, String> nbtByIndex = new HashMap<>();
        for (int k = 0; k < flat.nbtBlockIdx().length; k++) {
            nbtByIndex.put(flat.nbtBlockIdx()[k], flat.nbtPaths()[k]);
        }
        List<BlockPos> fix = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (skip != null && skip[i]) {
                continue;
            }
            int[] rc = BORotation.branchOffset(xyz[i * 3], xyz[i * 3 + 2], rotSteps);
            BlockPos placePos = origin.offset(rc[0], xyz[i * 3 + 1], rc[1]);
            BlockState st = rotated[idx[i]];
            level.setBlock(placePos, st, 2);
            if (needsConnectionFix(st)) {
                fix.add(placePos);
            }
            String nbtPath = nbtByIndex.get(i);
            if (nbtPath != null) {
                var tag = BORegistry.INSTANCE.loadNbt(nbtPath);
                if (tag != null) {
                    TileEntityConverter.apply(level, placePos, tag);
                }
            }
        }
        for (int i = 0; i < rn; i++) {
            if (skip != null && skip[n + i]) {
                continue;
            }
            int c = rollRandom(rChoices[i], random);
            if (c >= 0) {
                int[] rc = BORotation.branchOffset(rXyz[i * 3], rXyz[i * 3 + 2], rotSteps);
                BlockPos pos = origin.offset(rc[0], rXyz[i * 3 + 1], rc[1]);
                BlockState st = rotated[rChoices[i][c * 2]];
                level.setBlock(pos, st, 2);
                if (needsConnectionFix(st)) {
                    fix.add(pos);
                }
                if (!applyFlatRndNbt(level, pos, flat, i, c)) {
                    fixBareSpawner(level, pos, st);
                }
            }
        }
        fixupConnections(level, fix);
        spawnEntities(level, origin, obj, rotSteps, random);
        return true;
    }

    /** Entity 行直放实体（beckon_spawn 等）：经 entity_map 映射，worldgen/指令两态可用。 */
    private static void spawnEntities(LevelAccessor level, BlockPos origin, BOObject obj,
                                      int rotSteps, RandomSource random) {
        if (obj.entityLines.isEmpty() || !(level instanceof net.minecraft.world.level.ServerLevelAccessor sla)) {
            return;
        }
        for (BOObject.EntityLine e : obj.entityLines) {
            String id = com.mx.underneath.blockmap.IdMapper.ENTITY.mapId(
                    e.id().toLowerCase(java.util.Locale.ROOT));
            var typeOpt = net.minecraft.world.entity.EntityType.byString(id);
            if (typeOpt.isEmpty()) {
                continue;
            }
            int[] rc = BORotation.bo3Block(e.x(), e.z(), rotSteps);
            double ex = origin.getX() + rc[0] + 0.5;
            double ey = origin.getY() + e.y();
            double ez = origin.getZ() + rc[1] + 0.5;
            for (int k = 0; k < e.count(); k++) {
                net.minecraft.world.entity.Entity ent = typeOpt.get().create(sla.getLevel());
                if (ent == null) {
                    continue;
                }
                ent.moveTo(ex, ey, ez, random.nextFloat() * 360F, 0F);
                if (ent instanceof net.minecraft.world.entity.Mob mob) {
                    mob.finalizeSpawn(sla, sla.getCurrentDifficultyAt(BlockPos.containing(ex, ey, ez)),
                            net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
                    mob.setPersistenceRequired();
                }
                sla.addFreshEntityWithPassengers(ent);
            }
        }
    }

    /** OTG RandomBlock 语义：按序掷选，返回命中的**候选下标**(0-based，用于取 stateIdx=choices[idx*2] 与 nbt)；全不中=-1。 */
    private static int rollRandom(int[] choices, RandomSource random) {
        for (int i = 0; i + 1 < choices.length; i += 2) {
            if (random.nextInt(100) < choices[i + 1]) {
                return i / 2;
            }
        }
        return -1;
    }

    // ---- 邻接形状修正（1.20 连接态在 1.12 数据里不存在，靠放置后重算——原版结构模板同款机制） ----

    /** 需要邻接重算的连接类方块：门上下半同步/玻璃板栏杆栅栏墙连接臂/楼梯转角/钟乳石粗细/双箱/藤蔓/红石线。 */
    private static boolean needsConnectionFix(BlockState s) {
        Block b = s.getBlock();
        return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof DoorBlock
                || b instanceof StairBlock || b instanceof FenceGateBlock
                || b instanceof PointedDripstoneBlock || b instanceof ChestBlock
                || b instanceof RedStoneWireBlock || b instanceof TripWireBlock
                || b instanceof VineBlock;
    }

    /**
     * 对放置的连接类方块做 {@code Block.updateFromNeighbourShapes}（StructureTemplate 同款），
     * 并双向刷新其 6 邻中已存在的连接类方块（跨 chunk 边界的栏杆/大道护栏两侧才能都伸臂）。
     */
    private static void fixupConnections(LevelAccessor level, List<BlockPos> targets) {
        for (BlockPos pos : targets) {
            applyShapeUpdate(level, pos);
            for (Direction d : Direction.values()) {
                BlockPos np = pos.relative(d);
                if (needsConnectionFix(level.getBlockState(np))) {
                    applyShapeUpdate(level, np);
                }
            }
        }
    }

    private static void applyShapeUpdate(LevelAccessor level, BlockPos pos) {
        BlockState cur = level.getBlockState(pos);
        BlockState upd = Block.updateFromNeighbourShapes(cur, level, pos);
        if (upd != cur) {
            level.setBlock(pos, upd, 2 | 16);
        }
    }

    /** 无 NBT 的裸刷怪笼（quark:monster_box→spawner 映射产物）：填 {@link SpawnerPool} 候选池
     *  （config 可调 30 种），免得出默认猪笼。池条目均带全光照 custom_spawn_rules——
     *  跳过 EPCA 光照<8/恒 false 生成谓词（实测坑）。 */
    private static void fixBareSpawner(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.SPAWNER)) {
            return;
        }
        if (SpawnerPool.get().isEmpty()) {
            return;   // EPCA 缺席（池条目全被注册校验滤除）→ 保持原样
        }
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            var tag = spawner.saveWithoutMetadata();
            tag.remove("SpawnData");
            tag.put("SpawnPotentials", SpawnerPool.buildPotentials());
            spawner.load(tag);
        }
    }

    /** 应用随机块命中候选的 nbt（城市容器 loot/刷怪笼）。返回是否应用成功。 */
    private static boolean applyRndNbt(net.minecraft.world.level.LevelAccessor level, BlockPos pos,
                                       BOObject obj, int rndIdx, int choiceIdx) {
        if (rndIdx < obj.rndNbt.length && choiceIdx < obj.rndNbt[rndIdx].length
                && obj.rndNbt[rndIdx][choiceIdx] != null) {
            String path = BORegistry.INSTANCE.resolveNbtPath(obj.sourceDir, obj.rndNbt[rndIdx][choiceIdx]);
            CompoundTag tag = path == null ? null : BORegistry.INSTANCE.loadNbt(path);
            if (tag != null) {
                TileEntityConverter.apply(level, pos, tag);
                return true;
            }
        }
        return false;
    }
}
