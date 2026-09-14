package com.mx.underneath.worldgen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 城市布点的持久状态（OTG 1.12 structurecache 的 1.20 对应物，存 SavedData）。
 *
 * <p>为什么必须持久：citygrid 是贪心密铺的强耦合系统——先起的城挡后起的城，影响可链式
 * 传播任意远，任何有限光锥的分区模拟都不收敛（region 拼接实测 61% chunk 归属不一致=
 * 城市被拼缝撕碎）。OTG 原版正是靠存盘 structurecache 增量贪心；这里同构移植。
 *
 * <p>并发契约：所有集合的读写（含 {@link #save}）都必须持有本实例锁——
 * {@link CityPlotter} 在 {@code synchronized(data)} 内操作，autosave 主线程在
 * {@code save} 内自锁。{@link #get} 的 DimensionDataStorage 内部 map 非线程安全，
 * 由主线程 LevelEvent.Load 预热（见 {@code Underneath#onLevelLoad}），此后均为纯读命中。
 */
public final class CityPlotSavedData extends SavedData {

    private static final String ID = "underneath_city_plot";

    /** 已做过起点尝试（或被结构覆盖）的 chunk：挡扫描、挡再次尝试，不挡分支（OTG 同款）。 */
    final LongOpenHashSet plotted = new LongOpenHashSet();
    /** feature 已实际执行过的 chunk（领取过 pending）：后来的城件落此=写不进，只能丢弃。 */
    final LongOpenHashSet generated = new LongOpenHashSet();
    /** 已被结构占用的 chunk：挡扫描 + 挡分支（跨结构碰撞）。 */
    final LongOpenHashSet occupied = new LongOpenHashSet();
    /** 已展开、待所在 chunk 生成时领取放置的零件。 */
    final Long2ObjectOpenHashMap<List<CityPlotter.Placement>> pending = new Long2ObjectOpenHashMap<>();
    /** 结构均衡计数（OTG bo4sBySize 排序依据）。 */
    final Map<String, Integer> timesSpawned = new HashMap<>();
    /** 同名结构起点坐标（Frequency 最小间距用），值=[cx,cz]。 */
    final Map<String, List<int[]>> startsByName = new HashMap<>();
    /** BO3Group 组内已生成中心（组间距用），值=[centerCx,centerCz,radius]。 */
    final Map<String, List<int[]>> spawnedByGroup = new HashMap<>();
    /** 群系查询缓存（不持久化；4-pass 扫描/分支 gate 高频重查同一 chunk）。 */
    final Long2ObjectOpenHashMap<String> biomeCache = new Long2ObjectOpenHashMap<>();

    public static CityPlotSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CityPlotSavedData::load, CityPlotSavedData::new, ID);
    }

    static CityPlotSavedData load(CompoundTag tag) {
        CityPlotSavedData data = new CityPlotSavedData();
        for (long l : tag.getLongArray("plotted")) {
            data.plotted.add(l);
        }
        for (long l : tag.getLongArray("occupied")) {
            data.occupied.add(l);
        }
        for (long l : tag.getLongArray("generated")) {
            data.generated.add(l);
        }
        ListTag pend = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pend.size(); i++) {
            CompoundTag e = pend.getCompound(i);
            ListTag ps = e.getList("p", Tag.TAG_COMPOUND);
            List<CityPlotter.Placement> list = new ArrayList<>(ps.size());
            for (int j = 0; j < ps.size(); j++) {
                CompoundTag p = ps.getCompound(j);
                list.add(new CityPlotter.Placement(p.getString("n"),
                        p.getInt("x"), p.getInt("y"), p.getInt("z"), p.getInt("r")));
            }
            data.pending.put(e.getLong("c"), list);
        }
        CompoundTag times = tag.getCompound("times");
        for (String k : times.getAllKeys()) {
            data.timesSpawned.put(k, times.getInt(k));
        }
        readCoordList(tag.getList("starts", Tag.TAG_COMPOUND), data.startsByName, false);
        readCoordList(tag.getList("groups", Tag.TAG_COMPOUND), data.spawnedByGroup, true);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        synchronized (this) {
            tag.putLongArray("plotted", plotted.toLongArray());
            tag.putLongArray("occupied", occupied.toLongArray());
            tag.putLongArray("generated", generated.toLongArray());
            ListTag pend = new ListTag();
            for (var e : pending.long2ObjectEntrySet()) {
                CompoundTag c = new CompoundTag();
                c.putLong("c", e.getLongKey());
                ListTag ps = new ListTag();
                for (CityPlotter.Placement p : e.getValue()) {
                    CompoundTag pt = new CompoundTag();
                    pt.putString("n", p.name());
                    pt.putInt("x", p.x());
                    pt.putInt("y", p.y());
                    pt.putInt("z", p.z());
                    pt.putInt("r", p.rot());
                    ps.add(pt);
                }
                c.put("p", ps);
                pend.add(c);
            }
            tag.put("pending", pend);
            CompoundTag times = new CompoundTag();
            timesSpawned.forEach(times::putInt);
            tag.put("times", times);
            tag.put("starts", writeCoordList(startsByName, false));
            tag.put("groups", writeCoordList(spawnedByGroup, true));
            return tag;
        }
    }

    private static void readCoordList(ListTag list, Map<String, List<int[]>> out, boolean withRadius) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int[] v = withRadius
                    ? new int[]{e.getInt("x"), e.getInt("z"), e.getInt("r")}
                    : new int[]{e.getInt("x"), e.getInt("z")};
            out.computeIfAbsent(e.getString("n"), k -> new ArrayList<>()).add(v);
        }
    }

    private static ListTag writeCoordList(Map<String, List<int[]>> map, boolean withRadius) {
        ListTag list = new ListTag();
        for (var e : map.entrySet()) {
            for (int[] v : e.getValue()) {
                CompoundTag c = new CompoundTag();
                c.putString("n", e.getKey());
                c.putInt("x", v[0]);
                c.putInt("z", v[1]);
                if (withRadius) {
                    c.putInt("r", v[2]);
                }
                list.add(c);
            }
        }
        return list;
    }
}
