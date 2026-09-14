package com.mx.underneath.event;

import com.google.gson.Gson;
import com.mx.underneath.Underneath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 战利品表 config 覆盖层（2026-09-14 用户拍板"战利品表可配置"）：
 * 把本模组任意一张表复制到 {@code config/underneath/loot_tables/<同路径>.json} 修改即生效
 * （{@link LootTableLoadEvent} 加载期整表替换）——玩家/包作者无需做数据包。
 * 只覆盖 {@code underneath:} 命名空间；解析失败仅告警并保留原表。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public final class LootOverrideHandler {

    private static final Gson GSON = Deserializers.createLootTableSerializer().create();
    private static volatile boolean dirEnsured;

    private LootOverrideHandler() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();
        if (!Underneath.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        Path root = FMLPaths.CONFIGDIR.get().resolve("underneath").resolve("loot_tables");
        if (!dirEnsured) {
            dirEnsured = true;
            try {
                Files.createDirectories(root);
            } catch (Exception ignored) {
            }
        }
        Path file = root.resolve(id.getPath() + ".json");
        if (Files.notExists(file)) {
            return;
        }
        try {
            LootTable table = GSON.fromJson(Files.readString(file), LootTable.class);
            if (table != null) {
                event.setTable(table);
                Underneath.LOGGER.info("[loot-config] 覆盖 {}", id);
            }
        } catch (Exception ex) {
            Underneath.LOGGER.warn("[loot-config] {} 解析失败，保留原表: {}", id, ex.toString());
        }
    }
}
