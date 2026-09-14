package com.mx.underneath.portal;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.Predicate;

/**
 * 系统生成的传送门锚点位置（per-level）：进深渊=穹顶强化深板岩小门、出主世界=灰烬石门。
 * 传送时先找近处已有锚点复用，失效（门帘被拆）则移除并另造。主线程访问（changeDimension 流程）。
 */
public final class PortalAnchorSavedData extends SavedData {

    private static final String ID = "underneath_portal_anchors";
    private final LongList anchors = new LongArrayList();

    public static PortalAnchorSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PortalAnchorSavedData::load, PortalAnchorSavedData::new, ID);
    }

    static PortalAnchorSavedData load(CompoundTag tag) {
        PortalAnchorSavedData data = new PortalAnchorSavedData();
        for (long l : tag.getLongArray("anchors")) {
            data.anchors.add(l);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("anchors", anchors.toLongArray());
        return tag;
    }

    public void add(BlockPos pos) {
        anchors.add(pos.asLong());
        setDirty();
    }

    /** 找 (x,z) 水平距离 maxDist 内最近的有效锚点；无效锚点（validator 假）顺手清除。 */
    public BlockPos findNearest(int x, int z, int maxDist, Predicate<BlockPos> validator) {
        BlockPos best = null;
        double bestSq = (double) maxDist * maxDist;
        for (int i = anchors.size() - 1; i >= 0; i--) {
            BlockPos pos = BlockPos.of(anchors.getLong(i));
            double dx = pos.getX() - x;
            double dz = pos.getZ() - z;
            double sq = dx * dx + dz * dz;
            if (sq > bestSq) {
                continue;
            }
            if (!validator.test(pos)) {
                anchors.removeLong(i);
                setDirty();
                continue;
            }
            best = pos;
            bestSq = sq;
        }
        return best;
    }
}
