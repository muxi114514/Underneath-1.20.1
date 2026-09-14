package com.mx.underneath.event;

import com.mx.underneath.Underneath;
import com.mx.underneath.init.ModDimensions;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 深渊摔落伤害大幅降低（×0.1）：重力保持原版（用户拍板：GravityFactor 0.07 观感太怪已弃），
 * 但天降/跳楼动线要求摔不死——130 格穹顶跳主腔 → 等效 13 格 ≈ 5 心重伤不死；
 * 30 格内跳楼几乎无伤。调整手感改 {@link #FACTOR}。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public final class FallDamageHandler {

    private static final float FACTOR = 0.1F;

    private FallDamageHandler() {
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity().level().dimension().equals(ModDimensions.UNDERNEATH)) {
            event.setDistance(event.getDistance() * FACTOR);
        }
    }
}
