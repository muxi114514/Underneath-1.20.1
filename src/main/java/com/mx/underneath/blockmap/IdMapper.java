package com.mx.underneath.blockmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mx.underneath.Underneath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 ID 映射器（战利品表 / 实体 / 物品），BlockMapper 同款数据驱动套路。
 *
 * <p>数据格式（{@code data/<ns>/<dir>/*.json}）：扁平对象 旧id→新id；
 * 物品映射的值还可为 {@code {"id":"...","tag":{...}}}（tag 经 JsonOps→NbtOps 转 NBT，
 * 用于染色皮甲等）。键 {@code "__substituted__"} 是嵌套分组：条目照常生效，
 * 但<b>单列标明「近似降级替代」</b>（用户要求：便于日后逐个换更贴切的目标）；
 * 其余 {@code "__*"} 键视为注释忽略。未映射 id 原样放行（identity 缺省）。
 *
 * <p><b>config 覆盖层</b>（2026-09-14 用户拍板"刷怪笼/映射可配置"）：
 * {@code config/underneath/<目录名>.json} 的条目<b>优先于数据包</b>——首次启动自动导出
 * 模板（内容=全部近似替代条目，如城市刷怪笼实体 epca:ripper），改哪条哪条生效、
 * 删条目=恢复默认；解析失败仅告警不崩。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public class IdMapper extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    public static final IdMapper LOOT = new IdMapper("loot_map");
    public static final IdMapper ENTITY = new IdMapper("entity_map");
    public static final IdMapper ITEM = new IdMapper("item_map");

    /** 物品映射目标：id + 可选附加 NBT（如染色皮甲的 display.color）。 */
    public record Mapping(String id, @Nullable CompoundTag tag) {
    }

    private final String directory;
    private volatile Map<String, Mapping> map = Map.of();

    private IdMapper(String directory) {
        super(GSON, directory);
        this.directory = directory;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(LOOT);
        event.addListener(ENTITY);
        event.addListener(ITEM);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Mapping> merged = new HashMap<>();
        JsonObject substitutedTemplate = new JsonObject();
        for (JsonElement file : files.values()) {
            if (!file.isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : file.getAsJsonObject().entrySet()) {
                if ("__substituted__".equals(e.getKey()) && e.getValue().isJsonObject()) {
                    for (Map.Entry<String, JsonElement> s : e.getValue().getAsJsonObject().entrySet()) {
                        merged.put(s.getKey().toLowerCase(Locale.ROOT), toMapping(s.getValue()));
                        substitutedTemplate.add(s.getKey(), s.getValue());
                    }
                } else if (!e.getKey().startsWith("__")) {
                    merged.put(e.getKey().toLowerCase(Locale.ROOT), toMapping(e.getValue()));
                }
            }
        }
        int overrides = applyConfigOverrides(merged, substitutedTemplate);
        this.map = Map.copyOf(merged);
        Underneath.LOGGER.info("IdMapper[{}] 载入 {} 条（近似替代 {} 条，config 覆盖 {} 条）",
                this.directory, merged.size(), substitutedTemplate.size(), overrides);
    }

    /** config 覆盖层：读 {@code config/underneath/<目录>.json} 叠加；首缺则导出替代条目模板。 */
    private int applyConfigOverrides(Map<String, Mapping> merged, JsonObject template) {
        Path file = FMLPaths.CONFIGDIR.get().resolve("underneath").resolve(this.directory + ".json");
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                JsonObject tpl = new JsonObject();
                tpl.addProperty("__comment__",
                        "此处条目覆盖数据包同名映射(旧id->新id)，删除条目=恢复默认；以下为当前的近似替代项，可改为更贴切目标。");
                for (Map.Entry<String, JsonElement> e : template.entrySet()) {
                    tpl.add(e.getKey(), e.getValue());
                }
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(tpl));
                return 0;
            }
            JsonObject obj = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (obj == null) {
                return 0;
            }
            int n = 0;
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (!e.getKey().startsWith("__")) {
                    merged.put(e.getKey().toLowerCase(Locale.ROOT), toMapping(e.getValue()));
                    n++;
                }
            }
            return n;
        } catch (Exception ex) {
            Underneath.LOGGER.warn("IdMapper[{}] config 覆盖层读取失败（忽略）: {}", this.directory, ex.toString());
            return 0;
        }
    }

    private static Mapping toMapping(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject obj = value.getAsJsonObject();
            CompoundTag tag = null;
            if (obj.has("tag")) {
                Tag converted = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, obj.get("tag"));
                if (converted instanceof CompoundTag compound) {
                    tag = compound;
                }
            }
            return new Mapping(obj.get("id").getAsString(), tag);
        }
        return new Mapping(value.getAsString(), null);
    }

    /** 映射 id（未命中原样放行）。 */
    public String mapId(String oldId) {
        Mapping m = this.map.get(oldId.toLowerCase(Locale.ROOT).trim());
        return m != null ? m.id() : oldId;
    }

    /** 物品映射（含可选附加 NBT）；未命中返回 null（调用方保持原样）。 */
    @Nullable
    public Mapping mapping(String oldId) {
        return this.map.get(oldId.toLowerCase(Locale.ROOT).trim());
    }
}
