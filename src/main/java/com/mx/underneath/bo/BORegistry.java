package com.mx.underneath.bo;

import com.mx.underneath.Underneath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BO 对象注册表：reload 时只建索引（342MB 文本 + 37936 个 nbt 不能全量预载），
 * 首次取用才解析并缓存（{@link ConcurrentHashMap}，worldgen 多线程安全）。
 *
 * <p>BO 对象按<b>文件名去后缀小写</b>索引（OTG 按名引用、打包脚本保证无重名）；
 * <b>nbt 按全路径索引</b>（`10-chestc0r0.nbt` 这类基名重复 4469 组，不能按名）——
 * Block 行里的 nbt 参数是<b>相对其 BO 文件目录</b>的路径，支持 {@code ../..} 上溯
 * （共享宝箱库在 {@code bo/chests/**}），经 {@link #resolveNbtPath} 归一化后查索引。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public final class BORegistry implements ResourceManagerReloadListener {

    private static final String DIRECTORY = "bo";

    public static final BORegistry INSTANCE = new BORegistry();

    private record Entry(Resource resource, boolean bo4, String dir) {
    }

    /** 对象名 → 资源（reload 整体换新，不可变读）。 */
    private volatile Map<String, Entry> index = Collections.emptyMap();
    /** nbt 全路径（如 {@code bo/chests/loot/common.nbt}）→ 资源。 */
    private volatile Map<String, Resource> nbtIndex = Collections.emptyMap();
    /** 已解析 BO 缓存（reload 清空）。 */
    private final Map<String, BOObject> parsed = new ConcurrentHashMap<>();
    /** 扁平化缓存（reload 清空）。 */
    private final Map<String, BOFlattener.Flattened> flattened = new ConcurrentHashMap<>();
    /** 已加载 nbt 缓存（存 Optional 以缓存失败；返回副本防调用方污染）。 */
    private final Map<String, Optional<CompoundTag>> nbtCache = new ConcurrentHashMap<>();

    private BORegistry() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, Entry> map = new HashMap<>();
        Map<String, Resource> nbts = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(DIRECTORY, loc -> {
            String p = loc.getPath();
            return p.endsWith(".bo3") || p.endsWith(".bo4") || p.endsWith(".bo2") || p.endsWith(".nbt");
        }).entrySet()) {
            String path = e.getKey().getPath();
            if (path.endsWith(".nbt")) {
                nbts.put(path, e.getValue());
                continue;
            }
            String stem = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'))
                    .toLowerCase(Locale.ROOT);
            String dir = path.substring(0, Math.max(path.lastIndexOf('/'), 0));
            map.put(stem, new Entry(e.getValue(), path.endsWith(".bo4"), dir));
        }
        this.index = Collections.unmodifiableMap(map);
        this.nbtIndex = Collections.unmodifiableMap(nbts);
        this.parsed.clear();
        this.flattened.clear();
        this.nbtCache.clear();
        Underneath.LOGGER.info("BORegistry 索引 {} 个 BO 对象 + {} 个 nbt（惰性解析）", map.size(), nbts.size());
    }

    /** 全部对象名（指令补全用）。 */
    public Set<String> names() {
        return this.index.keySet();
    }

    /** 是否存在该对象。 */
    public boolean contains(String name) {
        return this.index.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /** 取对象（未解析则即时解析并缓存）；不存在或解析失败返回 null。 */
    @Nullable
    public BOObject get(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        BOObject cached = this.parsed.get(key);
        if (cached != null) {
            return cached;
        }
        Entry entry = this.index.get(key);
        if (entry == null) {
            return null;
        }
        return this.parsed.computeIfAbsent(key, k -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(entry.resource().open(), StandardCharsets.ISO_8859_1))) {
                BOObject obj = BOParser.parse(k, entry.bo4(), entry.dir(), reader);
                Underneath.LOGGER.debug("BO 解析: {}（{} 方块, {} 随机, {} 分支行）",
                        k, obj.blockCount(), obj.rndChoices.length, obj.branches.size());
                return obj;
            } catch (Exception ex) {
                Underneath.LOGGER.error("BO 解析失败: {}", k, ex);
                return null;    // computeIfAbsent 返回 null 不缓存，下次仍会重试
            }
        });
    }

    /** 取扁平化结果（L1.5：确定性分支已展开）；根对象不存在返回 null。 */
    @Nullable
    public BOFlattener.Flattened getFlattened(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!this.index.containsKey(key)) {
            return null;
        }
        return this.flattened.computeIfAbsent(key, k -> BOFlattener.flatten(this, k));
    }

    /**
     * 把 Block 行的 nbt 相对路径解析为索引全路径（与打包脚本同规则规范化 + {@code ..} 上溯）；
     * 不在索引中返回 null。
     */
    @Nullable
    public String resolveNbtPath(String sourceDir, String rawArg) {
        String arg = rawArg.toLowerCase(Locale.ROOT).replace('\\', '/').replace(' ', '_')
                .replaceAll("[^a-z0-9/._-]", "_");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : (sourceDir + "/" + arg).split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
            } else {
                stack.addLast(part);
            }
        }
        String path = String.join("/", stack);
        return this.nbtIndex.containsKey(path) ? path : null;
    }

    /** 加载 nbt（gzip 优先、raw 兜底；缓存；返回副本）。 */
    @Nullable
    public CompoundTag loadNbt(String fullPath) {
        Optional<CompoundTag> cached = this.nbtCache.computeIfAbsent(fullPath, p -> {
            Resource res = this.nbtIndex.get(p);
            if (res == null) {
                return Optional.empty();
            }
            try (InputStream in = res.open()) {
                return Optional.of(NbtIo.readCompressed(in));
            } catch (Exception gz) {
                try (InputStream in = res.open()) {
                    return Optional.of(NbtIo.read(new DataInputStream(in)));
                } catch (Exception raw) {
                    Underneath.LOGGER.warn("nbt 读取失败: {} ({})", p, raw.getMessage());
                    return Optional.empty();
                }
            }
        });
        return cached.map(CompoundTag::copy).orElse(null);
    }
}
