package com.mx.underneath.portal;

import com.mx.underneath.Underneath;
import com.mx.underneath.init.ModBlocks;
import com.mx.underneath.init.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Function;

/**
 * 深渊传送门的单程传送逻辑（用户拍板设计，两侧回程门材料<b>反接</b>防误伤）：
 * <ul>
 * <li><b>主世界→深渊</b>：1:1 坐标传到穹顶顶面，落点旁找/造<b>强化深板岩</b>小锚点门
 *     （不可挖掘=怪物拆不掉、玩家也拆不掉的永久回程锚点）；玩家从穹顶破洞/管道降入主腔
 *     （配合 0.07 低重力）。</li>
 * <li><b>深渊→主世界</b>：底腔废墟门点火出去，1:1 坐标传到地表，落点找/造<b>灰烬石</b>门
 *     （可拆的"战利品门"；拆毁后玩家自搭点不亮——点燃白名单按维度分侧的自然推论）。</li>
 * </ul>
 * 锚点复用：128 格内已有系统门直接传过去，不重复造（{@link PortalAnchorSavedData}）。
 */
public final class UnderneathTeleporter implements ITeleporter {

    private static final int REUSE_RADIUS = 128;

    /** 双向入口：由门帘 entityInside 调用。 */
    public static void transfer(Entity entity, ServerLevel current) {
        ResourceKey<Level> destKey = current.dimension().equals(ModDimensions.UNDERNEATH)
                ? Level.OVERWORLD : ModDimensions.UNDERNEATH;
        ServerLevel dest = current.getServer().getLevel(destKey);
        if (dest == null) {
            Underneath.LOGGER.warn("[portal] 目标维度 {} 不存在，传送取消", destKey.location());
            return;
        }
        entity.changeDimension(dest, new UnderneathTeleporter());
    }

    @Override
    public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld,
                                    Function<ServerLevel, PortalInfo> defaultPortalInfo) {
        int x = Mth.floor(entity.getX());
        int z = Mth.floor(entity.getZ());
        boolean toUnderneath = destWorld.dimension().equals(ModDimensions.UNDERNEATH);
        PortalAnchorSavedData anchors = PortalAnchorSavedData.get(destWorld);
        BlockPos door = anchors.findNearest(x, z, REUSE_RADIUS,
                p -> destWorld.getBlockState(p).is(ModBlocks.PORTAL.get()));
        if (door == null) {
            door = toUnderneath ? buildRoofPortal(destWorld, x, z) : buildSurfacePortal(destWorld, x, z);
            anchors.add(door);
            Underneath.LOGGER.info("[portal] 在 {} {} 生成回程门", destWorld.dimension().location(), door);
        }
        return new PortalInfo(Vec3.atBottomCenterOf(door), Vec3.ZERO, entity.getYRot(), entity.getXRot());
    }

    @Override
    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld,
                              float yaw, Function<Boolean, Entity> repositionEntity) {
        return repositionEntity.apply(false);   // false=跳过原版下界门搜索/生成逻辑
    }

    /** 深渊侧锚点门：穹顶顶面，强化深板岩框+平台。返回门帘内空左下格。 */
    private static BlockPos buildRoofPortal(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);   // 强制同步生成：未加载 chunk 的 getHeight=minBuildHeight（基岩层门实测坑）
        int air = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        // 穹顶完整列 air≈249-253；破洞列直见主腔（<240）→ 在洞上方 249 造悬空平台
        int baseY = (air < 240 || air > 250) ? 249 : air;
        return buildPortal(level, x, baseY, z, Blocks.REINFORCED_DEEPSLATE);
    }

    /** 主世界侧回程门：地表，灰烬石框+平台（可拆；拆后按点燃规则不可重燃）。 */
    private static BlockPos buildSurfacePortal(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);   // 同上：必须先生成 chunk 才能拿到真实地表高度
        int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Block ashen = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("lycanitesmobs", "ashenstone"));
        if (ashen == null || ashen == Blocks.AIR) {
            ashen = Blocks.DEEPSLATE_TILES;   // 缺 Lycanites 的保险（1.70 不会走到）
        }
        return buildPortal(level, x, baseY, z, ashen);
    }

    /** 造 4×5 竖直门（axis=X）+ 6×3 平台，清出门洞空间。返回门帘内空左下格。 */
    private static BlockPos buildPortal(ServerLevel level, int x, int baseY, int z, Block frame) {
        BlockState frameState = frame.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // 平台（站脚层，盖住液面/虚空）
        for (int dx = -1; dx <= 4; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(cursor.set(x + dx, baseY - 1, z + dz), frameState, 3);
            }
        }
        // 清空门体+门前后（树叶/地形凸起）
        for (int dx = 0; dx <= 3; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(cursor.set(x + dx, baseY + dy, z + dz),
                            Blocks.AIR.defaultBlockState(), 18);
                }
            }
        }
        // 框：x..x+3 × baseY..baseY+4 @ z，外圈
        for (int dx = 0; dx <= 3; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                if (dx == 0 || dx == 3 || dy == 0 || dy == 4) {
                    level.setBlock(cursor.set(x + dx, baseY + dy, z), frameState, 3);
                }
            }
        }
        // 门帘（flag 18：框已就位，避免放置顺序触发熄灭校验）
        BlockState portal = ModBlocks.PORTAL.get().defaultBlockState()
                .setValue(UnderneathPortalBlock.AXIS, Direction.Axis.X);
        for (int dx = 1; dx <= 2; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlock(cursor.set(x + dx, baseY + dy, z), portal, 18);
            }
        }
        return new BlockPos(x + 1, baseY + 1, z);
    }
}
