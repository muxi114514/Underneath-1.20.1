package com.mx.underneath.worldgen;

import com.mojang.serialization.Codec;
import com.mx.underneath.bo.BOObject;
import com.mx.underneath.bo.BOPlacer;
import com.mx.underneath.bo.BORegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * 城市落地 Feature（注册名 {@code underneath:city_plot}）：每 chunk 生成时向 {@link CityPlotter}
 * 取本 chunk 应放的结构零件，逐件裁剪到本 chunk 写入区放置——只写当前 chunk，不触邻块。
 * 群系判定走 BiomeSource 直采（plot 决策涉及未生成 chunk，不能查 level）。
 */
public final class CityPlotFeature extends Feature<NoneFeatureConfiguration> {

    public CityPlotFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        BiomeSource source = ctx.chunkGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getLevel().getChunkSource().randomState().sampler();
        // OTG 群系判定列=chunk 内 (+8,+7)；城基 Y50 → quart y=12
        CityPlotter.BiomeGate gate = (ccx, ccz) -> source
                .getNoiseBiome((ccx << 2) + 2, 12, (ccz << 2) + 1, sampler)
                .unwrapKey().map(k -> k.location().toString()).orElse("");
        List<CityPlotter.Placement> pieces = CityPlotter.chunkPieces(level.getLevel(), cx, cz, gate);
        if (pieces.isEmpty()) {
            return false;
        }
        int minX = cx << 4;
        int minZ = cz << 4;
        BoundingBox chunkBox = new BoundingBox(
                minX, level.getMinBuildHeight(), minZ,
                minX + 15, level.getMaxBuildHeight() - 1, minZ + 15);
        RandomSource random = ctx.random();
        for (CityPlotter.Placement p : pieces) {
            BOObject obj = BORegistry.INSTANCE.get(p.name());
            if (obj == null) {
                continue;
            }
            BOPlacer.placeOwnBlocksClipped(level, new BlockPos(p.x(), p.y(), p.z()),
                    obj, p.rot(), random, chunkBox);
        }
        return true;
    }
}
