package com.mx.underneath.bo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mx.underneath.Underneath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 城市刷怪笼候选池（2026-09-14 用户拍板：EPCA 全部 30 种怪物——刷怪蛋名单排除
 * 头颅类 walking_*_head、一二阶召唤柱 stage_i/ii_beckon、鳍 fins「陆地出鱼太抽象」；
 * 黏液/血肉蛋无同名实体，各取中等体型代表 size1/size2）。
 *
 * <p>config：{@code config/underneath/spawner_pool.json}（id→权重平表，首缺自动导出默认；
 * 改权重/增删条目重启生效）。加载时逐条校验实体注册，无效条目告警丢弃。
 * 特殊键 {@code __natural__}＝深渊自然刷怪参数（{@link UnderneathNaturalSpawnerConfig}）。
 */
public final class SpawnerPool {

    /** 深渊自然刷怪参数（上限/间隔防卡；用户拍板：血湖出鳍鱼、陆上出池怪、无视亮度）。 */
    public record UnderneathNaturalSpawnerConfig(boolean enabled, int mobCap, int finsCap,
                                                 int mobInterval, int finsInterval) {
        static final UnderneathNaturalSpawnerConfig DEFAULT =
                new UnderneathNaturalSpawnerConfig(true, 24, 6, 40, 200);
    }

    private static volatile UnderneathNaturalSpawnerConfig naturalConfig;

    public static UnderneathNaturalSpawnerConfig naturalConfig() {
        get();   // 触发加载
        UnderneathNaturalSpawnerConfig c = naturalConfig;
        return c != null ? c : UnderneathNaturalSpawnerConfig.DEFAULT;
    }

    private static final String[] DEFAULT = {
            "epca:curbug", "epca:flying_carrier", "epca:infested_bat", "epca:infested_chicken",
            "epca:infested_cow", "epca:infested_drowned", "epca:infested_enderman",
            "epca:infested_endermite", "epca:infested_fox", "epca:infested_husk",
            "epca:infested_pig", "epca:infested_pillager", "epca:infested_sheep",
            "epca:infested_silverfish", "epca:infested_skeleton", "epca:infested_slime_size1",
            "epca:infested_villager", "epca:infested_vindicator", "epca:infested_wolf",
            "epca:infested_zombie", "epca:infested_zombie_villager", "epca:large_incomplete_form",
            "epca:light_carrier", "epca:living_flesh_size2", "epca:medium_incomplete_form",
            "epca:mozzie", "epca:reshape_longarms", "epca:reshape_yelloweye",
            "epca:ripper", "epca:small_incomplete_form"};

    private static final Gson GSON = new GsonBuilder().setLenient().setPrettyPrinting().create();

    public record Entry(String id, int weight) {
    }

    private static volatile List<Entry> pool;

    private SpawnerPool() {
    }

    public static List<Entry> get() {
        List<Entry> p = pool;
        if (p == null) {
            synchronized (SpawnerPool.class) {
                p = pool;
                if (p == null) {
                    pool = p = load();
                }
            }
        }
        return p;
    }

    private static List<Entry> load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("underneath").resolve("spawner_pool.json");
        List<Entry> out = new ArrayList<>();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                JsonObject tpl = new JsonObject();
                tpl.addProperty("__comment__",
                        "城市刷怪笼候选池(实体id->权重)：增删条目/调权重后重启生效，删除本文件=恢复默认30种。"
                        + " __natural__=深渊自然刷怪(池怪+血湖鳍鱼，无视亮度)：cap=每玩家64格内上限，interval=尝试间隔tick。");
                JsonObject nat = new JsonObject();
                nat.addProperty("enabled", true);
                nat.addProperty("mob_cap", UnderneathNaturalSpawnerConfig.DEFAULT.mobCap());
                nat.addProperty("fins_cap", UnderneathNaturalSpawnerConfig.DEFAULT.finsCap());
                nat.addProperty("mob_interval", UnderneathNaturalSpawnerConfig.DEFAULT.mobInterval());
                nat.addProperty("fins_interval", UnderneathNaturalSpawnerConfig.DEFAULT.finsInterval());
                tpl.add("__natural__", nat);
                for (String id : DEFAULT) {
                    tpl.addProperty(id, 1);
                }
                Files.writeString(file, GSON.toJson(tpl));
                for (String id : DEFAULT) {
                    addValidated(out, id, 1);
                }
                naturalConfig = UnderneathNaturalSpawnerConfig.DEFAULT;
            } else {
                JsonObject obj = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (obj != null) {
                    for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                        if (!e.getKey().startsWith("__")) {
                            addValidated(out, e.getKey(), Math.max(e.getValue().getAsInt(), 1));
                        }
                    }
                    naturalConfig = readNatural(obj.getAsJsonObject("__natural__"));
                }
            }
        } catch (Exception ex) {
            Underneath.LOGGER.warn("[spawner-pool] config 读取失败，回退默认池: {}", ex.toString());
            out.clear();
            for (String id : DEFAULT) {
                addValidated(out, id, 1);
            }
        }
        Underneath.LOGGER.info("[spawner-pool] 载入 {} 种刷怪笼候选", out.size());
        return List.copyOf(out);
    }

    private static UnderneathNaturalSpawnerConfig readNatural(JsonObject nat) {
        UnderneathNaturalSpawnerConfig d = UnderneathNaturalSpawnerConfig.DEFAULT;
        if (nat == null) {
            return d;
        }
        try {
            return new UnderneathNaturalSpawnerConfig(
                    !nat.has("enabled") || nat.get("enabled").getAsBoolean(),
                    nat.has("mob_cap") ? nat.get("mob_cap").getAsInt() : d.mobCap(),
                    nat.has("fins_cap") ? nat.get("fins_cap").getAsInt() : d.finsCap(),
                    Math.max(nat.has("mob_interval") ? nat.get("mob_interval").getAsInt() : d.mobInterval(), 1),
                    Math.max(nat.has("fins_interval") ? nat.get("fins_interval").getAsInt() : d.finsInterval(), 1));
        } catch (Exception ex) {
            return d;
        }
    }

    private static void addValidated(List<Entry> out, String id, int weight) {
        if (EntityType.byString(id).isPresent()) {
            out.add(new Entry(id, weight));
        } else {
            Underneath.LOGGER.warn("[spawner-pool] 实体 {} 未注册，条目忽略", id);
        }
    }

    /** 构建 1.18+ SpawnPotentials 列表（每条带全光照 custom_spawn_rules，见 wrapSpawnData）。 */
    public static ListTag buildPotentials() {
        ListTag list = new ListTag();
        for (Entry e : get()) {
            CompoundTag entity = new CompoundTag();
            entity.putString("id", e.id());
            CompoundTag entry = new CompoundTag();
            entry.put("data", TileEntityConverter.wrapSpawnData(entity));
            entry.putInt("weight", e.weight());
            list.add(entry);
        }
        return list;
    }
}
