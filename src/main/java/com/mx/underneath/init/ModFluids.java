package com.mx.underneath.init;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mx.underneath.Underneath;
import com.mx.underneath.fluid.BloodFluid;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidInteractionRegistry;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 流体注册（血液）。参数移植自 BOP 1.20.1 {@code ModFluidTypes}（授权使用）：
 * 密度 3000/黏度 6000（比水黏稠）、可灭火、无坠落缓冲修正；
 * 客户端=血液贴图 + 水下暗红雾（0.407/0.121/0.137，视距 0.125~5）。
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Underneath.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, Underneath.MOD_ID);

    public static final RegistryObject<FluidType> BLOOD_TYPE = FLUID_TYPES.register("blood",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.underneath.blood")
                    .fallDistanceModifier(0F)
                    .canExtinguish(true)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                    .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)
                    .density(3000)
                    .viscosity(6000)) {

                @Override
                @Nullable
                public BlockPathTypes getBlockPathType(FluidState state, BlockGetter level, BlockPos pos,
                                                       @Nullable Mob mob, boolean canFluidLog) {
                    return canFluidLog ? super.getBlockPathType(state, level, pos, mob, true) : null;
                }

                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        private static final ResourceLocation BLOOD_UNDERWATER =
                                new ResourceLocation(Underneath.MOD_ID, "textures/block/blood_underwater.png");
                        private static final ResourceLocation BLOOD_STILL =
                                new ResourceLocation(Underneath.MOD_ID, "block/blood_still");
                        private static final ResourceLocation BLOOD_FLOW =
                                new ResourceLocation(Underneath.MOD_ID, "block/blood_flow");

                        @Override
                        public ResourceLocation getStillTexture() {
                            return BLOOD_STILL;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return BLOOD_FLOW;
                        }

                        @Override
                        public ResourceLocation getRenderOverlayTexture(Minecraft mc) {
                            return BLOOD_UNDERWATER;
                        }

                        @Override
                        public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level,
                                                       int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                            return new Vector3f(0.407F, 0.121F, 0.137F);
                        }

                        @Override
                        public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance,
                                                    float partialTick, float nearDistance, float farDistance, FogShape shape) {
                            RenderSystem.setShaderFogStart(0.125F);
                            RenderSystem.setShaderFogEnd(5.0F);
                        }
                    });
                }
            });

    public static final RegistryObject<FlowingFluid> BLOOD = FLUIDS.register("blood", BloodFluid.Source::new);
    public static final RegistryObject<FlowingFluid> FLOWING_BLOOD = FLUIDS.register("flowing_blood", BloodFluid.Flowing::new);

    /**
     * 流体交互（移植 BOP 机制）：任何其他流体流动接触血液 → 凝成血肉（其源）/多孔血肉（其流）。
     * 须在 FMLCommonSetup 的 enqueueWork 中调用（注册表已冻结、且 FluidInteractionRegistry 非线程安全）。
     */
    public static void registerFluidInteractions() {
        // 他液流动碰血 → 他液这格凝成血肉（血源=flesh/流动血=porous）。
        // 血已挂 minecraft:water tag（2026-09-14 用户拍板，鱼类/游泳物理生效）——
        // FluidInteractionRegistry 按 FluidType 走、与 tag 正交，凝肉交互不受影响；
        // 岩浆碰血也仍走本交互（不会按原版 water 路径成石/黑曜石），原味保留。
        for (var entry : ForgeRegistries.FLUID_TYPES.get().getEntries()) {
            FluidType type = entry.getValue();
            if (type != ForgeMod.EMPTY_TYPE.get() && type != BLOOD_TYPE.get()) {
                FluidInteractionRegistry.addInteraction(type, new FluidInteractionRegistry.InteractionInformation(
                        BLOOD_TYPE.get(),
                        fluidState -> fluidState.isSource()
                                ? ModBlocks.FLESH.get().defaultBlockState()
                                : ModBlocks.POROUS_FLESH.get().defaultBlockState()));
            }
        }
        // 反方向：血流动碰水 → 血这格凝成血肉（水源=flesh/流水=porous），双向接触都凝。
        FluidInteractionRegistry.addInteraction(BLOOD_TYPE.get(), new FluidInteractionRegistry.InteractionInformation(
                ForgeMod.WATER_TYPE.get(),
                fluidState -> fluidState.isSource()
                        ? ModBlocks.FLESH.get().defaultBlockState()
                        : ModBlocks.POROUS_FLESH.get().defaultBlockState()));
    }

    private ModFluids() {
    }
}
