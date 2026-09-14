package com.mx.underneath.event;

import com.mx.underneath.Underneath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * EPCA 实体的刷怪笼放行（javap 铁证 2026-09-14）：EPCA 把一批实体（flying_carrier/
 * infested_villager/small_incomplete_form 等）的 SpawnPlacements 谓词写成「光照&lt;0」
 * ＝<b>恒 false</b>——任何环境的刷怪笼都刷不出（正常怪是「光照&lt;8」）。
 * 兜底：SPAWNER 类型 + epca 命名空间一律 ALLOW（不限维度——主世界没有天然 epca 笼，
 * 只影响玩家手放测试，零平衡风险；城市笼另有 SpawnData custom_spawn_rules 数据级修复）。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public final class SpawnerRuleHandler {

    private SpawnerRuleHandler() {
    }

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getSpawnType() != MobSpawnType.SPAWNER) {
            return;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntityType());
        if (key != null && "epca".equals(key.getNamespace())) {
            event.setResult(Event.Result.ALLOW);
        }
    }
}
