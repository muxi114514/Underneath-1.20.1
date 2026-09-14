package com.mx.underneath.fluid;

import com.mx.underneath.Underneath;
import com.mx.underneath.init.ModBlocks;
import com.mx.underneath.init.ModFluids;
import com.mx.underneath.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidType;

import java.util.Optional;

/**
 * 血液流体——Underneath 维度的「水」。
 *
 * <p>移植自 BOP 1.20.1 {@code BloodFluid}（授权使用）：比水黏稠（tickDelay=7/坡度探测=3），
 * 不可无限增殖，防爆 100。裁剪了 BOP 的滴血粒子与环境音（依赖其粒子/音效注册，不值得为此移植）。
 *
 * <p>Forge 坑：模组流体<b>必须重写</b> {@link #getFluidType()}——基类对未知流体直接抛异常
 * （BOP 多加载器构架靠 {@code MixinBloodFluid} 补这一刀，我们纯 Forge 直接重写）。
 */
public abstract class BloodFluid extends FlowingFluid {

    /** 血液家族 tag（数据侧 {@code data/underneath/tags/fluids/blood.json}），供流动替换判定与跨模组识别。 */
    public static final TagKey<Fluid> BLOOD_TAG =
            TagKey.create(Registries.FLUID, new ResourceLocation(Underneath.MOD_ID, "blood"));

    @Override
    public Fluid getFlowing() {
        return ModFluids.FLOWING_BLOOD.get();
    }

    @Override
    public Fluid getSource() {
        return ModFluids.BLOOD.get();
    }

    @Override
    public Item getBucket() {
        return ModItems.BLOOD_BUCKET.get();
    }

    @Override
    public FluidType getFluidType() {
        return ModFluids.BLOOD_TYPE.get();
    }

    /** 血不可无限：两源相邻不生成新源。 */
    @Override
    protected boolean canConvertToSource(Level level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    @Override
    public int getSlopeFindDistance(LevelReader level) {
        return 3;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return ModBlocks.BLOOD.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == ModFluids.BLOOD.get() || fluid == ModFluids.FLOWING_BLOOD.get();
    }

    @Override
    public int getDropOff(LevelReader level) {
        return 1;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 7;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter getter, BlockPos pos, Fluid other, Direction direction) {
        return direction == Direction.DOWN && !other.is(BLOOD_TAG);
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    /** 流动血。 */
    public static class Flowing extends BloodFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    /** 血源。 */
    public static class Source extends BloodFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
