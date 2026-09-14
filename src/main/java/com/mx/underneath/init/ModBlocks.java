package com.mx.underneath.init;

import com.mx.underneath.Underneath;
import com.mx.underneath.block.BloodFireBlock;
import com.mx.underneath.block.FleshBlock;
import com.mx.underneath.portal.UnderneathPortalBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册。属性参数对齐 BOP 1.20.1（授权使用）：
 * 血肉块=珊瑚音效(音量1/音高0.5)、赤陶红地图色、硬度0.4、无 randomTicks（增生机制已裁剪）。
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Underneath.MOD_ID);

    /** 血液的方块形态（LiquidBlock，供 createLegacyBlock/世界放置用）。 */
    public static final RegistryObject<LiquidBlock> BLOOD = BLOCKS.register("blood",
            () -> new LiquidBlock(ModFluids.BLOOD,
                    BlockBehaviour.Properties.copy(Blocks.WATER).mapColor(MapColor.COLOR_RED)));

    public static final RegistryObject<Block> FLESH = BLOCKS.register("flesh",
            () -> new FleshBlock(fleshProperties()));

    public static final RegistryObject<Block> POROUS_FLESH = BLOCKS.register("porous_flesh",
            () -> new FleshBlock(fleshProperties()));

    /** 深渊传送门门帘（P0b）：对齐原版下界门（无碰撞/不可挖/光 11/活塞阻挡），
     *  另加基岩级抗爆——穹顶锚点门要求不可被怪物/爆炸摧毁（框=强化深板岩本就防拆）。 */
    public static final RegistryObject<Block> PORTAL = BLOCKS.register("portal",
            () -> new UnderneathPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).noCollission().strength(-1.0F, 3600000.0F)
                    .sound(SoundType.GLASS).lightLevel(state -> 11).noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    /** 血焰（血焰打火石打出，只换色本质是火）：属性对齐灵魂火，光 12。 */
    public static final RegistryObject<Block> BLOOD_FIRE = BLOCKS.register("blood_fire",
            () -> new BloodFireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).replaceable().noCollission().instabreak()
                    .lightLevel(state -> 12).sound(SoundType.WOOL).noLootTable()
                    .pushReaction(PushReaction.DESTROY)));

    // ---------------- CoT(ContentTweaker/ContentCreator)专属方块忠实重注册 ----------------
    // 原 RLCraft Dregora 用 CraftTweaker 脚本注册(Dregora_ContentTweaker/ContentCreator.zs)，1.20.1 无 CoT。
    // 属性照脚本：reinforced_concrete=石声/硬30抗6000/镐3级；iron_plate_rust=金属声；resource_crate=金属声硬10。
    public static final RegistryObject<Block> REINFORCED_CONCRETE = BLOCKS.register("reinforced_concrete",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE)
                    .strength(30.0F, 6000.0F).requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> IRON_PLATE_RUST_REINFORCED = BLOCKS.register("iron_plate_rust_reinforced",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL)
                    .strength(30.0F, 6000.0F).requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> RESOURCE_CRATE = BLOCKS.register("resource_crate",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL)
                    .strength(10.0F).requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> BONE_PILE = BLOCKS.register("bone_pile",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).sound(SoundType.BONE_BLOCK)
                    .strength(2.0F)));

    public static final RegistryObject<Block> CONCRETE_SLAB_REINFORCED = BLOCKS.register("concrete_slab_reinforced",
            () -> new SlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE)
                    .strength(30.0F, 6000.0F).requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> CONCRETE_STAIRS_REINFORCED = BLOCKS.register("concrete_stairs_reinforced",
            () -> new StairBlock(() -> REINFORCED_CONCRETE.get().defaultBlockState(),
                    BlockBehaviour.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE)
                            .strength(30.0F, 6000.0F).requiresCorrectToolForDrops()));

    private static BlockBehaviour.Properties fleshProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_RED)
                .strength(0.4F)
                .sound(new SoundType(1.0F, 0.5F,
                        SoundEvents.CORAL_BLOCK_BREAK, SoundEvents.CORAL_BLOCK_STEP,
                        SoundEvents.CORAL_BLOCK_PLACE, SoundEvents.CORAL_BLOCK_HIT,
                        SoundEvents.CORAL_BLOCK_FALL));
    }

    private ModBlocks() {
    }
}
