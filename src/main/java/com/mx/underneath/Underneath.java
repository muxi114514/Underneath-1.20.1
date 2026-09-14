package com.mx.underneath;

import com.mojang.logging.LogUtils;
import com.mx.underneath.init.ModBlocks;
import com.mx.underneath.init.ModDimensions;
import com.mx.underneath.init.ModFeatures;
import com.mx.underneath.init.ModFluids;
import com.mx.underneath.init.ModItems;
import com.mx.underneath.init.ModWorldgenTypes;
import com.mx.underneath.worldgen.CityPlotSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Underneath 主类（模组入口）。
 *
 * <p>定位：把 RLCraft Dregora 的 1.12.2 OTG 预设维度「Underneath」重建到 1.20.1 Forge。
 * 方案＝原生噪声地形（不移植卡顿的 OTG 地形引擎）＋ 移植 OTG 的 BO 建筑内容。
 *
 * <p>单一职责：本类只做模组入口与生命周期编排；注册收敛到 init 包。
 * 维度与维度类型走数据驱动（{@code data/underneath/dimension[_type]/underneath.json}）；
 * 方块映射见 {@code blockmap.BlockMapper}（Forge 总线自注册）；
 * 客户端（维度雾效/流体渲染层）见 {@code client.ClientSetup}。
 */
@Mod(Underneath.MOD_ID)
public class Underneath {

    public static final String MOD_ID = "underneath";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Underneath() {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // P1：血液流体 + 血肉块（Underneath 维度的「水」与「冰」，移植自 BOP 1.20.1，授权使用）
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        // P2⑤：bo_scatter worldgen Feature（数据包 configured_feature 引用）
        ModFeatures.FEATURES.register(modEventBus);
        // P2-OTG：OTG 地形密度函数 + FromImage 群系源
        ModWorldgenTypes.DENSITY_TYPES.register(modEventBus);
        ModWorldgenTypes.BIOME_SOURCES.register(modEventBus);
        // P3：地下巨城 Structure + StructurePiece

        modEventBus.addListener(ModItems::addCreative);
        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Underneath 初始化中……");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 流体交互注册须在主线程同步段（注册表已冻结，且 FluidInteractionRegistry 非线程安全）
        event.enqueueWork(ModFluids::registerFluidInteractions);
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        // 主线程预热城市布点 SavedData：DimensionDataStorage 内部 map 非线程安全，
        // 必须先于 worldgen 线程的首次访问完成 computeIfAbsent 写入
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ModDimensions.UNDERNEATH)) {
            CityPlotSavedData.get(level);
        }
    }
}
