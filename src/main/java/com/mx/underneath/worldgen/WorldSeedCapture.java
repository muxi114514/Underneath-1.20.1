package com.mx.underneath.worldgen;

import com.mx.underneath.Underneath;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 世界种子握手。主通道＝{@code RandomStateSeedMixin} 在 {@code RandomState.create} 头部
 * 每次创建前写入（种子恰好在该 RandomState 的 mapAll 接线前就位，最可靠）；
 * {@link ServerAboutToStartEvent} 仅作兜底。{@link OTGTerrainDensity#mapAll} 取用。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public final class WorldSeedCapture {

    private static volatile Long seed;

    private WorldSeedCapture() {
    }

    /** RandomState.create HEAD（mixin）调用——每个 RandomState 接线前的即时种子。 */
    public static void set(long value) {
        seed = value;
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        seed = event.getServer().getWorldData().worldGenOptions().seed();
    }

    public static Long get() {
        return seed;
    }
}
