package com.mx.underneath.init;

import com.mojang.serialization.Codec;
import com.mx.underneath.Underneath;
import com.mx.underneath.worldgen.FromImageBiomeSource;
import com.mx.underneath.worldgen.OTGTerrainDensity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** worldgen 类型注册：OTG 地形密度函数 + FromImage 群系源。城市走 OTG plotter+Feature，不再用原版 Structure。 */
public final class ModWorldgenTypes {

    public static final DeferredRegister<Codec<? extends DensityFunction>> DENSITY_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Underneath.MOD_ID);

    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Underneath.MOD_ID);

    public static final RegistryObject<Codec<? extends DensityFunction>> OTG_TERRAIN =
            DENSITY_TYPES.register("otg_terrain", () -> OTGTerrainDensity.CODEC.codec());

    public static final RegistryObject<Codec<? extends BiomeSource>> FROM_IMAGE =
            BIOME_SOURCES.register("from_image", () -> FromImageBiomeSource.CODEC);

    private ModWorldgenTypes() {
    }
}
