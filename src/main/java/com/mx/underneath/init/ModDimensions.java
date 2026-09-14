package com.mx.underneath.init;

import com.mx.underneath.Underneath;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * 维度相关的 {@link ResourceKey} 常量。
 *
 * <p>维度本体与维度类型是数据驱动的（见 {@code data/underneath/dimension[_type]/underneath.json}），
 * 这里只集中声明其 Key，供指令传送、传送门跳转（P0b）、以及后续 Java 侧引用维度使用，
 * 避免各处硬编码字符串 {@code "underneath:underneath"}。
 */
public final class ModDimensions {

    /** 维度（Level）键：{@code underneath:underneath}。 */
    public static final ResourceKey<Level> UNDERNEATH =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Underneath.MOD_ID, "underneath"));

    /** 维度类型键：{@code underneath:underneath}。 */
    public static final ResourceKey<DimensionType> UNDERNEATH_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(Underneath.MOD_ID, "underneath"));

    private ModDimensions() {
    }
}
