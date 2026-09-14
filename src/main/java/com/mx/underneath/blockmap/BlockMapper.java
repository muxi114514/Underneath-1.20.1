package com.mx.underneath.blockmap;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.StringReader;
import com.mx.underneath.Underneath;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.12 OTG 方块 token → 1.20.1 {@link BlockState} 映射器。
 *
 * <p>数据驱动：合并 {@code data/<ns>/block_map/*.json}（扁平对象，键=旧 token 小写，
 * 值=现代方块串，支持 {@code ns:name[prop=value]} 属性写法，经原版 {@link BlockStateParser} 解析）。
 * token 规范：{@code stained_clay:14}（旧版名+meta）/ {@code quark:iron_plate:1}（模组名+meta）/
 * 不带 meta 的裸名；查询顺序=精确带 meta → 去 meta 基名 → 回退占位。
 *
 * <p>线程安全：解析结果存不可变 Map，经 volatile 一次性换新；世界生成多线程只读，无锁。
 * 未映射 token 记录进有界集合（cap 512，防内存泄漏）并回退「品红釉陶」占位——故意刺眼，便于排查。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public class BlockMapper extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    /** 未映射/解析失败时的占位方块：品红釉陶，进世界一眼可辨。 */
    private static final BlockState FALLBACK = Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState();
    private static final int UNKNOWN_LOG_CAP = 512;

    public static final BlockMapper INSTANCE = new BlockMapper();

    /** token(小写) → 目标方块态；不可变，整体换新。 */
    private volatile Map<String, BlockState> resolved = Collections.emptyMap();
    /** 已告警过的未知 token（有界，防刷屏与泄漏）。 */
    private final Set<String> loggedUnknown = ConcurrentHashMap.newKeySet();

    private BlockMapper() {
        super(GSON, "block_map");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, String> raw = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            if (!file.getValue().isJsonObject()) {
                Underneath.LOGGER.warn("block_map {} 不是 JSON 对象，跳过", file.getKey());
                continue;
            }
            for (Map.Entry<String, JsonElement> e : file.getValue().getAsJsonObject().entrySet()) {
                raw.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue().getAsString());
            }
        }

        ImmutableMap.Builder<String, BlockState> builder = ImmutableMap.builder();
        int failed = 0;
        for (Map.Entry<String, String> e : raw.entrySet()) {
            try {
                BlockState state = BlockStateParser
                        .parseForBlock(BuiltInRegistries.BLOCK.asLookup(), new StringReader(e.getValue()), false)
                        .blockState();
                builder.put(e.getKey(), state);
            } catch (Exception ex) {
                // 单条失败不影响整体（目标 mod 不在场时正常现象），回退占位
                builder.put(e.getKey(), FALLBACK);
                failed++;
                if (failed <= 20) {
                    Underneath.LOGGER.warn("block_map 条目解析失败（目标缺失？）: {} -> {} ({})",
                            e.getKey(), e.getValue(), ex.getMessage());
                }
            }
        }
        this.resolved = builder.buildKeepingLast();
        this.loggedUnknown.clear();
        Underneath.LOGGER.info("BlockMapper 载入 {} 条映射（{} 条目标缺失回退占位）", raw.size(), failed);
    }

    /**
     * 解析旧 token 为 1.20.1 方块态。永不返回 null。
     *
     * @param token 旧方块 token（如 {@code STAINED_CLAY:14} / {@code quark:iron_plate:1}），大小写不敏感
     */
    public BlockState resolve(String token) {
        String key = token.toLowerCase(java.util.Locale.ROOT).trim();
        BlockState state = lookup(key);
        if (state != null) {
            return state;
        }
        if (this.loggedUnknown.size() < UNKNOWN_LOG_CAP && this.loggedUnknown.add(key)) {
            Underneath.LOGGER.warn("BlockMapper 未映射 token: {}（回退占位）", key);
        }
        return FALLBACK;
    }

    /** 查询链：精确 → 去尾 meta 基名 → 剥 "minecraft:" 前缀重走（Dregora 数据混用 1.12 现代 id 写法）。 */
    private BlockState lookup(String key) {
        Map<String, BlockState> map = this.resolved;
        BlockState state = map.get(key);
        if (state != null) {
            return state;
        }
        int idx = key.lastIndexOf(':');
        if (idx > 0 && isNumeric(key.substring(idx + 1))) {
            state = map.get(key.substring(0, idx));
            if (state != null) {
                return state;
            }
        }
        if (key.startsWith("minecraft:")) {
            return lookup(key.substring("minecraft:".length()));
        }
        return null;
    }

    /** token 是否显式映射（不含回退）。 */
    public boolean isMapped(String token) {
        return this.resolved.containsKey(token.toLowerCase(java.util.Locale.ROOT).trim());
    }

    /** 同 {@link #resolve} 但未映射返回 null（不回退占位、不告警）——SourceBlocks/BlockCheck 集合构建用。 */
    public BlockState resolveOrNull(String token) {
        return lookup(token.toLowerCase(java.util.Locale.ROOT).trim());
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
