package com.mx.underneath.event;

import com.mx.underneath.Underneath;
import com.mx.underneath.bo.SpawnerPool;
import com.mx.underneath.init.ModDimensions;
import com.mx.underneath.init.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 深渊自然刷怪（2026-09-14 用户拍板）：血湖里生成鳍鱼（epca:fins）、其余位置生成
 * {@link SpawnerPool} 池怪（30 种），<b>无视亮度</b>；直接 addFreshEntity 绕过 EPCA 生成
 * 谓词（光照/phase/恒 false，javap 实锤）与原版位置检查（血=自定义流体，原版水生判定不认）。
 *
 * <p>防卡限制（config {@code __natural__} 可调）：每玩家 64 格内怪 ≤mobCap/鱼 ≤finsCap，
 * 尝试间隔 mobInterval/finsInterval tick，每次每玩家至多 1 只；掷点全程 getChunkNow 守卫。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public final class UnderneathNaturalSpawner {

    private static final int MIN_R = 24;
    private static final int MAX_R = 56;

    private UnderneathNaturalSpawner() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.UNDERNEATH)) {
            return;
        }
        SpawnerPool.UnderneathNaturalSpawnerConfig cfg = SpawnerPool.naturalConfig();
        if (!cfg.enabled()) {
            return;
        }
        long time = level.getGameTime();
        boolean mobTick = time % cfg.mobInterval() == 0;
        boolean finsTick = time % cfg.finsInterval() == 0;
        if (!mobTick && !finsTick) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (mobTick) {
                trySpawnMob(level, player, cfg);
            }
            if (finsTick) {
                trySpawnFins(level, player, cfg);
            }
        }
    }

    private static void trySpawnMob(ServerLevel level, ServerPlayer player,
                                    SpawnerPool.UnderneathNaturalSpawnerConfig cfg) {
        List<SpawnerPool.Entry> pool = SpawnerPool.get();
        if (pool.isEmpty() || countNearby(level, player, false) >= cfg.mobCap()) {
            return;
        }
        RandomSource random = level.random;
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos pos = pickGroundPos(level, player, random);
            if (pos == null) {
                continue;
            }
            EntityType<?> type = EntityType.byString(pickWeighted(pool, random)).orElse(null);
            if (type == null) {
                continue;
            }
            if (!level.noCollision(type.getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
                continue;
            }
            Entity entity = type.create(level);
            if (entity == null) {
                return;
            }
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            if (entity instanceof Mob mob) {
                ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(pos),
                        MobSpawnType.NATURAL, null, null);
            }
            level.addFreshEntity(entity);
            return;
        }
    }

    private static void trySpawnFins(ServerLevel level, ServerPlayer player,
                                     SpawnerPool.UnderneathNaturalSpawnerConfig cfg) {
        EntityType<?> fins = EntityType.byString("epca:fins").orElse(null);
        if (fins == null || countNearby(level, player, true) >= cfg.finsCap()) {
            return;
        }
        RandomSource random = level.random;
        for (int attempt = 0; attempt < 6; attempt++) {
            BlockPos pos = pickPos(player, random, 16, 48, 20);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            if (!chunk.getFluidState(pos).getType().isSame(ModFluids.BLOOD.get())) {
                continue;
            }
            Entity entity = fins.create(level);
            if (entity == null) {
                return;
            }
            entity.moveTo(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(entity);
            return;
        }
    }

    /** 掷点后单列下扫找「两格空气+脚下实心」的落脚位（同列同 chunk，一次守卫）。 */
    private static BlockPos pickGroundPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos start = pickPos(player, random, MIN_R, MAX_R, 16);
        LevelChunk chunk = level.getChunkSource().getChunkNow(start.getX() >> 4, start.getZ() >> 4);
        if (chunk == null) {
            return null;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
                start.getX(), start.getY(), start.getZ());
        for (int down = 0; down < 20 && cursor.getY() > level.getMinBuildHeight() + 1; down++) {
            if (chunk.getBlockState(cursor).isAir()
                    && chunk.getBlockState(cursor.above()).isAir()
                    && chunk.getBlockState(cursor.below()).isFaceSturdy(level, cursor.below(),
                            net.minecraft.core.Direction.UP)) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }

    /** 按权重从池中掷选一个实体 id。 */
    private static String pickWeighted(List<SpawnerPool.Entry> pool, RandomSource random) {
        int total = 0;
        for (SpawnerPool.Entry e : pool) {
            total += e.weight();
        }
        int roll = random.nextInt(Math.max(total, 1));
        for (SpawnerPool.Entry e : pool) {
            roll -= e.weight();
            if (roll < 0) {
                return e.id();
            }
        }
        return pool.get(pool.size() - 1).id();
    }

    private static BlockPos pickPos(ServerPlayer player, RandomSource random,
                                    int minR, int maxR, int yRange) {
        double angle = random.nextDouble() * Math.PI * 2;
        double r = minR + random.nextDouble() * (maxR - minR);
        int x = Mth.floor(player.getX() + Math.cos(angle) * r);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * r);
        int y = player.getBlockY() + random.nextInt(yRange * 2 + 1) - yRange;
        return new BlockPos(x, y, z);
    }

    /** 玩家 64 格内的 epca 实体计数（fins 与其余分开管）。 */
    private static int countNearby(ServerLevel level, ServerPlayer player, boolean fins) {
        List<Entity> list = level.getEntities((Entity) null, player.getBoundingBox().inflate(64),
                e -> {
                    var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                            .getKey(e.getType());
                    if (key == null || !"epca".equals(key.getNamespace())) {
                        return false;
                    }
                    return fins == "fins".equals(key.getPath());
                });
        return list.size();
    }
}
