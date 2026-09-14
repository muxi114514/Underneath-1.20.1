package com.mx.underneath.mixin;

import com.mx.underneath.worldgen.WorldSeedCapture;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 世界种子捕获：{@code RandomState.create(NoiseGeneratorSettings,HolderGetter,long)} 静态工厂
 * 的 HEAD——它内部 {@code new RandomState(...)} 的构造器体里跑 noiseRouter.mapAll(接线密度函数)，
 * 故在此 HEAD 写入 seed，保证同一次创建里 {@code underneath:otg_terrain} 的 mapAll 能取到种子。
 *
 * <p>为何不注构造器：Mixin 禁止构造器 @At("HEAD")（super 前插码非法，曾崩服）；静态方法 HEAD 合法。
 * ChunkMap 生成用的正是这个 create 重载，覆盖世界生成路径。
 */
@Mixin(RandomState.class)
public class RandomStateSeedMixin {

    @Inject(
            method = "create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;",
            at = @At("HEAD"),
            remap = true
    )
    private static void underneath$captureSeed(NoiseGeneratorSettings settings,
                                               HolderGetter<NormalNoise.NoiseParameters> noises,
                                               long seed,
                                               CallbackInfoReturnable<RandomState> cir) {
        WorldSeedCapture.set(seed);
    }
}
