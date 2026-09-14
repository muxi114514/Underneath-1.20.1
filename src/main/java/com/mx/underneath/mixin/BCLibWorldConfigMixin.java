package com.mx.underneath.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 修复 BCLib 20.0.13 的启动竞态崩溃（本包修复，非本模组功能）。
 *
 * <p>根因：{@code WorldConfig.MODS} 是无同步 ArrayList，{@code registerModCache} 的
 * contains+add 复合操作在 Forge <b>并行</b> common_setup 下被 BCLib 与 BetterNether
 * 同时调用 → ArrayList 内部数组损坏（AIOOBE "Index 1 out of bounds for length 0"）。
 *
 * <p>修法：HEAD 注入 + cancel 整体替换该方法，在 MODS 自身监视器内完成 contains+add。
 * registerModCache 是该列表唯一写入口，全部写经此串行化即完备；读发生在加载期之后（单线程）。
 *
 * <p>{@code @Pseudo}=目标类缺席时静默跳过（包里移除 BCLib 不崩）；
 * {@code targets} 用字符串 → 编译期无需 BCLib 依赖。
 */
@Pseudo
@Mixin(targets = "org.betterx.worlds.together.world.WorldConfig", remap = false)
public abstract class BCLibWorldConfigMixin {

    @Shadow
    @Final
    private static List<String> MODS;

    @Inject(method = "registerModCache", at = @At("HEAD"), cancellable = true, remap = false)
    private static void underneath$syncRegisterModCache(String modID, CallbackInfo ci) {
        synchronized (MODS) {
            if (!MODS.contains(modID)) {
                MODS.add(modID);
            }
        }
        ci.cancel();
    }
}
