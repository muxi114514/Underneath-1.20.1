package com.mx.underneath.client;

import com.mx.underneath.Underneath;
import com.mx.underneath.init.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端注册入口（仅客户端加载）。
 *
 * <p>①维度渲染特效：暗红浓雾（维度类型 JSON {@code "effects": "underneath:underneath"} 指向此注册）——
 * 原版只认 overworld/the_nether/the_end 三套，自定义维度须借 Forge 的
 * {@link RegisterDimensionSpecialEffectsEvent} 登记。
 * ②血液流体渲染层设为半透明（模组流体默认 solid，贴图带 alpha 时会渲染出错）。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(new ResourceLocation(Underneath.MOD_ID, "underneath"),
                new UnderneathDimensionEffects());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModFluids.BLOOD.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_BLOOD.get(), RenderType.translucent());
        });
    }
}
