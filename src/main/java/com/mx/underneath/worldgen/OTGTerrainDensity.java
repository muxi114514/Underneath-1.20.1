package com.mx.underneath.worldgen;

import com.mojang.serialization.MapCodec;
import com.mx.underneath.otg.OTGTerrainMath;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import javax.annotation.Nullable;

/**
 * OTG Underneath 地形密度函数（注册名 {@code underneath:otg_terrain}）。
 *
 * <p>数学全部在 {@link OTGTerrainMath}（纯类、可离线剖面自证）；本类只做引擎接线：
 * 数据包解析出无种子实例 → RandomState 接线（{@link #mapAll}）时从
 * {@link WorldSeedCapture}（ServerAboutToStartEvent 捕获，早于一切维度创建）拿种子生成带种子实例。
 * JSON 里包 {@code minecraft:interpolated} 使用（引擎按 4×8 角点采样=OTG 原网格）。
 */
public final class OTGTerrainDensity implements DensityFunction.SimpleFunction {

    public static final KeyDispatchDataCodec<OTGTerrainDensity> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(OTGTerrainDensity::new));

    @Nullable
    private final OTGTerrainMath math;

    public OTGTerrainDensity() {
        this.math = null;
    }

    private OTGTerrainDensity(OTGTerrainMath math) {
        this.math = math;
    }

    /** 一次性诊断：抓实际生成时前若干次腔体高度的 compute 调用（坐标+返回值）。 */
    private static final java.util.concurrent.atomic.AtomicInteger DIAG =
            new java.util.concurrent.atomic.AtomicInteger(0);

    @Override
    public double compute(FunctionContext ctx) {
        if (math == null) {
            if (DIAG.getAndIncrement() < 3) {
                com.mx.underneath.Underneath.LOGGER.warn(
                        "OTG compute 未接种子(math=null)@({},{},{}) → 兜底 1.0(实心)!",
                        ctx.blockX(), ctx.blockY(), ctx.blockZ());
            }
            return 1.0;     // 未接种子：全实心兜底（若日志出现此行=种子接线失败=真因）
        }
        double d = math.density(ctx.blockX() / 4.0, ctx.blockY() / 8.0, ctx.blockZ() / 4.0) / 128.0;
        int y = ctx.blockY();
        if (y >= 100 && y <= 140 && DIAG.getAndIncrement() < 8) {
            com.mx.underneath.Underneath.LOGGER.info(
                    "OTG compute@({},{},{}) = {} → {}", ctx.blockX(), y, ctx.blockZ(),
                    String.format("%.3f", d), d > 0 ? "实心" : "空气");
        }
        return d;
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        if (math == null) {
            Long seed = WorldSeedCapture.get();
            if (seed != null) {
                return visitor.apply(new OTGTerrainDensity(new OTGTerrainMath(seed)));
            }
        }
        return visitor.apply(this);
    }

    @Override
    public double minValue() {
        return -16.0;
    }

    @Override
    public double maxValue() {
        return 16.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
