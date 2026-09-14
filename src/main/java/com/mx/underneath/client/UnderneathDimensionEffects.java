package com.mx.underneath.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Underneath 的客户端维度渲染特效：无天空、暗红浓雾。
 *
 * <p>对应原 OTG WorldConfig 的 {@code UseCustomFogColor + FogColorRed/Green/Blue(0.4/0.08/0.08)}
 * 与 {@code HasSkyLight:false / IsNightWorld:true}。维度 JSON 的 {@code effects} 字段指向本特效
 * （{@code "underneath:underneath"}），由 {@link ClientSetup} 在 mod 总线注册。
 *
 * <p>构造参数：cloudLevel=NaN（无云）、hasGround=true、skyType=NONE（不渲染天空/日月星）、
 * forceBrightLightmap=false、constantAmbientLight=false。
 */
public class UnderneathDimensionEffects extends DimensionSpecialEffects {

    /** 暗红雾色（R,G,B）。 */
    private static final Vec3 FOG_COLOR = new Vec3(0.4D, 0.08D, 0.08D);

    public UnderneathDimensionEffects() {
        super(Float.NaN, true, SkyType.NONE, false, false);
    }

    /** 雾色恒为暗红，不随亮度变化（营造压抑的地下氛围）。 */
    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return FOG_COLOR;
    }

    /** 处处浓雾：视野受限，强化「地底」封闭感。 */
    @Override
    public boolean isFoggyAt(int x, int z) {
        return true;
    }
}
