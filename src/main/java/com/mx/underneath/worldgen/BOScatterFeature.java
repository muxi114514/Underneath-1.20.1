package com.mx.underneath.worldgen;

import com.mx.underneath.bo.BOFlattener;
import com.mx.underneath.bo.BOObject;
import com.mx.underneath.bo.BOPlacer;
import com.mx.underneath.bo.BORegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

/**
 * BO 散布 Feature（P2⑤）：把 P1 的 BO 对象按 OTG Tree/CustomObject 语义撒进地形。
 * 每 chunk 调一次，attempts 次尝试各选随机 x/z 与 Y（floor 扫地面 / random 直放）。
 * 大型对象横向 ≤±15，落在 worldgen 3×3 区域写入许可内。
 */
public final class BOScatterFeature extends Feature<BOScatterConfig> {

    public BOScatterFeature() {
        super(BOScatterConfig.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<BOScatterConfig> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BOScatterConfig cfg = ctx.config();
        BlockPos origin = ctx.origin();

        if ("tree".equals(cfg.mode())) {
            return placeTreeMode(level, random, cfg, origin);
        }
        if ("custom_object".equals(cfg.mode())) {
            return placeCustomObjectMode(level, random, cfg, origin);
        }

        int span = cfg.maxY() - cfg.minY() + 1;
        if (span <= 0 || cfg.attempts() <= 0) {
            return false;
        }
        // 着块白名单：运行时按名解析，未知项静默忽略（缺 mod 回退原则）
        Set<Block> on = new HashSet<>();
        for (String id : cfg.onBlocks()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Block b = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
            if (b != null) {
                on.add(b);
            }
        }

        boolean anyPlaced = false;
        boolean floorMode = !"random".equals(cfg.anchor());
        for (int a = 0; a < cfg.attempts(); a++) {
            int x = origin.getX() + random.nextInt(16);
            int z = origin.getZ() + random.nextInt(16);
            int y0 = cfg.minY() + random.nextInt(span);
            int y;
            if (floorMode) {
                y = findFloor(level, x, z, y0, cfg.minY(), on);
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
            } else {
                y = y0;
            }
            // OTG Tree 语义：按序掷选，第一个命中的对象放置并结束本次尝试
            for (BOScatterConfig.WeightedName entry : cfg.objects()) {
                if (random.nextInt(100) >= entry.chance()) {
                    continue;
                }
                BOFlattener.Flattened flat = BORegistry.INSTANCE.getFlattened(entry.name());
                if (flat != null && (flat.blockCount() > 0 || flat.rndChoices().length > 0)) {
                    int rot = cfg.randomRotation() ? random.nextInt(4) : 0;
                    BOPlacer.place(level, new BlockPos(x, y + cfg.yOffset(), z), flat, rot, random);
                    anyPlaced = true;
                }
                break;
            }
        }
        return anyPlaced;
    }

    /**
     * OTG Tree() 资源忠实语义（TreeGen#spawnInChunk）：attempts 次尝试，每次<b>按序</b>对各候选掷
     * chance%，命中则在<b>候选自己的随机坐标</b>按对象头（randomY/旋转/SourceBlocks/BlockCheck）
     * 尝试生成——<b>生成失败继续下一候选</b>，真实成功才结束本次尝试（chance 全 100 的多候选
     * 列表因此全都活跃：前者检查不过就轮到后者）。
     */
    private boolean placeTreeMode(WorldGenLevel level, RandomSource random, BOScatterConfig cfg, BlockPos origin) {
        boolean any = false;
        for (int a = 0; a < cfg.attempts(); a++) {
            for (BOScatterConfig.WeightedName entry : cfg.objects()) {
                if (random.nextInt(100) < entry.chance()
                        && tryOtgSpawn(level, random, entry.name(), origin)) {
                    any = true;
                    break;
                }
            }
        }
        return any;
    }

    /** OTG CustomObject() 资源忠实语义（BO3#process）：每对象独立按<b>自身头</b> Frequency 次、Rarity% 尝试。 */
    private boolean placeCustomObjectMode(WorldGenLevel level, RandomSource random, BOScatterConfig cfg, BlockPos origin) {
        boolean any = false;
        for (BOScatterConfig.WeightedName entry : cfg.objects()) {
            BOObject obj = BORegistry.INSTANCE.get(entry.name());
            if (obj == null) {
                continue;
            }
            for (int i = 0; i < obj.frequency; i++) {
                if (obj.rarity > random.nextDouble() * 100.0) {
                    any |= tryOtgSpawn(level, random, entry.name(), origin);
                }
            }
        }
        return any;
    }

    /** 单次生成尝试：OTG 起点=chunk 中心+rand16（即 +8..+23，可入邻 chunk）+ 对象头 randomY/旋转
     *  + BOPlacer 的 populate 域(2×2)/SourceBlocks/BlockCheck 检查。 */
    private static boolean tryOtgSpawn(WorldGenLevel level, RandomSource random, String name, BlockPos origin) {
        BOObject obj = BORegistry.INSTANCE.get(name);
        BOFlattener.Flattened flat = BORegistry.INSTANCE.getFlattened(name);
        if (obj == null || flat == null
                || (flat.blockCount() == 0 && flat.rndChoices().length == 0 && obj.entityLines.isEmpty())) {
            return false;
        }
        int x = origin.getX() + 8 + random.nextInt(16);
        int z = origin.getZ() + 8 + random.nextInt(16);
        // 数据集散布对象全为 randomY（min==max 直取）；夹在世界高度内
        int minY = Math.max(obj.minHeight, level.getMinBuildHeight() + 1);
        int maxY = Math.min(obj.maxHeight, level.getMaxBuildHeight() - 1);
        if (maxY < minY) {
            return false;
        }
        int y = minY == maxY ? minY : minY + random.nextInt(maxY - minY + 1);
        int rot = obj.rotateRandomly ? random.nextInt(4) : 0;
        return BOPlacer.tryPlaceOtg(level, new BlockPos(x, y, z), flat, obj, rot, random,
                origin.getX(), origin.getZ());
    }

    /** 从 fromY 向下找第一个"空气且脚下实心"的地面位；on 非空时脚下方块须在名单内。 */
    private static int findFloor(WorldGenLevel level, int x, int z, int fromY, int minY, Set<Block> on) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = fromY; y > minY; y--) {
            if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                continue;
            }
            BlockState below = level.getBlockState(pos.set(x, y - 1, z));
            if (below.isAir() || !below.isFaceSturdy(level, pos, Direction.UP)) {
                continue;
            }
            if (!on.isEmpty() && !on.contains(below.getBlock())) {
                continue;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }
}
