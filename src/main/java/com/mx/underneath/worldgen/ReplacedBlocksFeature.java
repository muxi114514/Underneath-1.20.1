package com.mx.underneath.worldgen;

import com.mojang.serialization.Codec;
import com.mx.underneath.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * OTG {@code ReplacedBlocks} 的忠实移植（注册名 {@code underneath:replaced_blocks}，raw_generation 步）：
 * 原版 .bc：{@code (STATIONARY_WATER,air,10,50),(biomesoplenty:blood,air,10,50),(STATIONARY_LAVA,quark:basalt,0,255)}
 * ——水位 63 但 Y10-50 抽干成空气（底部空腔=干陆地+传送门遗迹，只留 Y50-63 水环贴主腔洼地）、
 * 全维度无裸岩浆（→平滑玄武岩）。
 *
 * <p>时序在 carver 之后、一切结构/散布之前 → 建筑自带的岩浆宝库/血池不受影响。
 * 岩浆唯一来源=洞穴 carver 的 lava_level(Y10) → 只扫 Y0-50 即覆盖全部实际替换点。
 */
public final class ReplacedBlocksFeature extends Feature<NoneFeatureConfiguration> {

    public ReplacedBlocksFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        int minX = origin.getX() & ~15;
        int minZ = origin.getZ() & ~15;
        Block blood = ModBlocks.BLOOD.get();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState basalt = Blocks.SMOOTH_BASALT.defaultBlockState();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 50; y++) {
                    BlockState s = level.getBlockState(mp.set(minX + x, y, minZ + z));
                    if (s.is(Blocks.LAVA)) {
                        level.setBlock(mp, basalt, 2);
                        any = true;
                    } else if (y >= 10 && s.getBlock() == blood) {
                        level.setBlock(mp, air, 2);
                        any = true;
                    }
                }
            }
        }
        return any;
    }
}
