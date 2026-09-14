package com.mx.underneath.init;

import com.mx.underneath.Underneath;
import com.mx.underneath.worldgen.BOScatterFeature;
import com.mx.underneath.worldgen.CityPlotFeature;
import com.mx.underneath.worldgen.ReplacedBlocksFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** worldgen Feature 注册：bo_scatter=BO 散布；city_plot=OTG 式城市网格铺放(碰撞无缝密铺)。 */
public final class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, Underneath.MOD_ID);

    public static final RegistryObject<BOScatterFeature> BO_SCATTER =
            FEATURES.register("bo_scatter", BOScatterFeature::new);

    public static final RegistryObject<CityPlotFeature> CITY_PLOT =
            FEATURES.register("city_plot", () -> new CityPlotFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<ReplacedBlocksFeature> REPLACED_BLOCKS =
            FEATURES.register("replaced_blocks", () -> new ReplacedBlocksFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }
}
