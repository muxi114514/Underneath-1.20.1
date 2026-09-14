package com.mx.underneath.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 深渊传送门门帘（参照 NetherPortalBlock：暗红旋涡贴图 + townaura 粒子=WorldConfig 实锤）。
 * 存续校验用两侧并集框材料（强化深板岩/灰烬石）；传送为单程语义，见 {@link UnderneathTeleporter}。
 */
public class UnderneathPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    protected static final VoxelShape X_AABB = Block.box(0, 0, 6, 16, 16, 10);
    protected static final VoxelShape Z_AABB = Block.box(6, 0, 0, 10, 16, 16);

    public UnderneathPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AABB : X_AABB;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis dirAxis = dir.getAxis();
        Direction.Axis myAxis = state.getValue(AXIS);
        boolean outOfPlane = dirAxis.isHorizontal() && myAxis != dirAxis;
        // 门平面内邻更（左右/上下）：邻块非门帘且门形不再完整 → 熄灭
        if (!outOfPlane && !neighborState.is(this)
                && !UnderneathPortalShape.isCompleteAt(level, pos, myAxis)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (entity.isPassenger() || entity.isVehicle() || !entity.canChangeDimensions()) {
            return;
        }
        if (entity.isOnPortalCooldown()) {
            entity.setPortalCooldown();   // 站在门内持续刷新，走出后才开始衰减
            return;
        }
        entity.setPortalCooldown();
        UnderneathTeleporter.transfer(entity, serverLevel);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F,
                    random.nextFloat() * 0.4F + 0.8F, false);
        }
        // PortalParticleType: townaura（WorldConfig 实锤）
        for (int i = 0; i < 4; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double dx = (random.nextDouble() - 0.5) * 0.3;
            double dy = (random.nextDouble() - 0.5) * 0.3;
            double dz = (random.nextDouble() - 0.5) * 0.3;
            if (state.getValue(AXIS) == Direction.Axis.X) {
                z = pos.getZ() + 0.5 + 0.25 * (random.nextBoolean() ? 1 : -1);
            } else {
                x = pos.getX() + 0.5 + 0.25 * (random.nextBoolean() ? 1 : -1);
            }
            level.addParticle(ParticleTypes.MYCELIUM, x, y, z, dx, dy, dz);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
