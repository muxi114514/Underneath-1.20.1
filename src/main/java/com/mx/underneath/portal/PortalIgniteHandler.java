package com.mx.underneath.portal;

import com.mx.underneath.Underneath;
import com.mx.underneath.init.ModDimensions;
import com.mx.underneath.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * 传送门点燃：<b>血焰打火石</b>（回响碎片+辛西纳石合成——古城的材料开古城的门）右键框内
 * → 门形判定（白名单按维度分侧）→ 放置门帘；原版打火石不再能点门。
 * 非深渊维度一律按"主世界侧"规则（只认强化深板岩=古城巨门；地狱/末地无此天然结构）。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public final class PortalIgniteHandler {

    private PortalIgniteHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.BLOOD_FLINT_AND_STEEL.get())) {
            return;
        }
        Level level = event.getLevel();
        BlockPos firePos = event.getPos().relative(event.getFace());
        boolean underneathSide = level.dimension().equals(ModDimensions.UNDERNEATH);
        Optional<UnderneathPortalShape> shape =
                UnderneathPortalShape.findAt(level, firePos, underneathSide);
        if (shape.isEmpty()) {
            return;   // 不成门形 → 交回原版打火石逻辑（正常放火）
        }
        if (!level.isClientSide) {
            shape.get().createPortalBlocks();
            level.playSound(null, firePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                    1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
            stack.hurtAndBreak(1, event.getEntity(),
                    p -> p.broadcastBreakEvent(event.getHand()));
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
