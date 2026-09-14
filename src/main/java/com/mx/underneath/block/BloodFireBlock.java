package com.mx.underneath.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 血焰（血焰打火石打出的火）：SoulFireBlock 模式——本质是火（烧实体/可踩灭/火焰粒子音效），
 * 不蔓延不烧方块；任意实心顶面可立。仅换色，伤害与普通火一致。
 */
public class BloodFireBlock extends BaseFireBlock {

    public BloodFireBlock(Properties properties) {
        super(properties, 1.0F);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }
}
