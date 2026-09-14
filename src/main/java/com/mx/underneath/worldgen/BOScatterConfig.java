package com.mx.underneath.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

/**
 * BO 散布配置（对应 OTG Tree()/CustomObject() 资源行）。
 *
 * <p>anchor 两模式：{@code floor}=区间内随机起点向下找"空气踩实心"地面（≈OTG randomY+BlockCheck 地面检查）；
 * {@code random}=区间内随机 Y 直接放（忠实无检查的 OTG 行为——浮空灰岩/埋藏废墟正是靠它）。
 * on_blocks 用字符串运行时按名解析（缺 mod 自动失配跳过，不做解析期硬依赖）。
 */
public record BOScatterConfig(List<WeightedName> objects, int attempts, String anchor,
                              int minY, int maxY, int yOffset, boolean randomRotation,
                              List<String> onBlocks, String mode) implements FeatureConfiguration {

    /** OTG Tree 语义：按序掷选，第一个命中即放置并结束本次尝试。 */
    public record WeightedName(String name, int chance) {
        public static final Codec<WeightedName> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("name").forGetter(WeightedName::name),
                Codec.INT.optionalFieldOf("chance", 100).forGetter(WeightedName::chance)
        ).apply(inst, WeightedName::new));
    }

    public static final Codec<BOScatterConfig> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            WeightedName.CODEC.listOf().fieldOf("objects").forGetter(BOScatterConfig::objects),
            Codec.INT.fieldOf("attempts").forGetter(BOScatterConfig::attempts),
            Codec.STRING.optionalFieldOf("anchor", "floor").forGetter(BOScatterConfig::anchor),
            Codec.INT.fieldOf("min_y").forGetter(BOScatterConfig::minY),
            Codec.INT.fieldOf("max_y").forGetter(BOScatterConfig::maxY),
            Codec.INT.optionalFieldOf("y_offset", 0).forGetter(BOScatterConfig::yOffset),
            Codec.BOOL.optionalFieldOf("random_rotation", true).forGetter(BOScatterConfig::randomRotation),
            Codec.STRING.listOf().optionalFieldOf("on_blocks", List.of()).forGetter(BOScatterConfig::onBlocks),
            // legacy=行级参数(既有特征)；tree=OTG Tree() 资源(行级 attempts/chance+对象头 Y/旋转/检查，
            // 每候选独立坐标、失败续试)；custom_object=OTG CustomObject() 资源(全对象头 Frequency/Rarity)
            Codec.STRING.optionalFieldOf("mode", "legacy").forGetter(BOScatterConfig::mode)
    ).apply(inst, BOScatterConfig::new));
}
