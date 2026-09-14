package com.mx.underneath.block;

import com.mx.underneath.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.Tags;

/**
 * 血肉块 / 多孔血肉块（移植自 BOP 1.20.1 {@code FleshBlock}，授权使用）。
 *
 * <p>保留：踩上减速（×0.95）、剪刀刮血肉→变多孔血肉+掉腐肉。
 * 裁剪：BOP 的 randomTick 增生（会长毛发/脓泡/眼球茎——依赖 4 个不移植的 BOP 方块），
 * 故注册属性不带 {@code randomTicks()}。
 */
public class FleshBlock extends Block {

    public FleshBlock(Block.Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.95D, 1.0D, 0.95D));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        // 剪刀只对「血肉块」生效（多孔血肉已是刮过的形态）
        if (stack.is(Tags.Items.SHEARS) && state.getBlock() == ModBlocks.FLESH.get()) {
            if (!level.isClientSide) {
                Direction face = hit.getDirection();
                Direction popDir = face.getAxis() == Direction.Axis.Y ? player.getDirection().getOpposite() : face;
                level.playSound(null, pos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.setBlock(pos, ModBlocks.POROUS_FLESH.get().defaultBlockState(), 11);
                ItemEntity drop = new ItemEntity(level,
                        pos.getX() + 0.5D + popDir.getStepX() * 0.65D,
                        pos.getY() + 0.1D,
                        pos.getZ() + 0.5D + popDir.getStepZ() * 0.65D,
                        new ItemStack(Items.ROTTEN_FLESH, 1));
                drop.setDeltaMovement(
                        0.05D * popDir.getStepX() + level.random.nextDouble() * 0.02D,
                        0.05D,
                        0.05D * popDir.getStepZ() + level.random.nextDouble() * 0.02D);
                level.addFreshEntity(drop);
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                level.gameEvent(player, GameEvent.SHEAR, pos);
                player.awardStat(Stats.ITEM_USED.get(Items.SHEARS));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
