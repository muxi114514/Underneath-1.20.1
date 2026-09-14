package com.mx.underneath.items;

import com.mx.underneath.init.ModBlocks;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 血焰打火石（回响碎片+辛西纳石合成）：深渊传送门的唯一点火钥匙
 * （点门逻辑在 {@code PortalIgniteHandler}，事件先于本方法）；普通使用打出血焰
 * （{@link com.mx.underneath.block.BloodFireBlock}，只换色本质是火）；营火/蜡烛照常可点。
 */
public class BloodFlintAndSteelItem extends FlintAndSteelItem {

    public BloodFlintAndSteelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        BlockPos clickedPos = ctx.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        if (CampfireBlock.canLight(clickedState) || CandleBlock.canLight(clickedState)
                || CandleCakeBlock.canLight(clickedState)) {
            return super.useOn(ctx);   // 营火/蜡烛=原版行为
        }
        BlockPos firePos = clickedPos.relative(ctx.getClickedFace());
        BlockState fire = ModBlocks.BLOOD_FIRE.get().defaultBlockState();
        if (!level.getBlockState(firePos).isAir() || !fire.canSurvive(level, firePos)) {
            return InteractionResult.FAIL;
        }
        level.playSound(player, firePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
        level.setBlock(firePos, fire, 11);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, firePos);
        ItemStack stack = ctx.getItemInHand();
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, firePos, stack);
        }
        if (player != null) {
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(ctx.getHand()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
