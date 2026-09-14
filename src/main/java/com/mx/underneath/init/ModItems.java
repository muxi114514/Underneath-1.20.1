package com.mx.underneath.init;

import com.mx.underneath.Underneath;
import com.mx.underneath.items.BloodFlintAndSteelItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品注册：血肉块/血液桶/血焰打火石 + CoT 方块物品。
 * （VC 41 种自建物品已删除——2026-09-14 换用 variedcommodities 1.20.1 移植版真身，原名直传。）
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Underneath.MOD_ID);

    /** 创造栏分组收录（建筑），注册时顺带归档。 */
    private static final List<RegistryObject<Item>> BUILDING = new ArrayList<>();

    // ---------------- CoT 专属方块的 BlockItem（忠实重注册）----------------
    public static final RegistryObject<Item> REINFORCED_CONCRETE = blockItem("reinforced_concrete", ModBlocks.REINFORCED_CONCRETE);
    public static final RegistryObject<Item> IRON_PLATE_RUST_REINFORCED = blockItem("iron_plate_rust_reinforced", ModBlocks.IRON_PLATE_RUST_REINFORCED);
    public static final RegistryObject<Item> RESOURCE_CRATE = blockItem("resource_crate", ModBlocks.RESOURCE_CRATE);
    public static final RegistryObject<Item> BONE_PILE = blockItem("bone_pile", ModBlocks.BONE_PILE);
    public static final RegistryObject<Item> CONCRETE_SLAB_REINFORCED = blockItem("concrete_slab_reinforced", ModBlocks.CONCRETE_SLAB_REINFORCED);
    public static final RegistryObject<Item> CONCRETE_STAIRS_REINFORCED = blockItem("concrete_stairs_reinforced", ModBlocks.CONCRETE_STAIRS_REINFORCED);

    private static RegistryObject<Item> blockItem(String name, RegistryObject<? extends net.minecraft.world.level.block.Block> block) {
        RegistryObject<Item> item = ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        BUILDING.add(item);
        return item;
    }

    // ---------------- 血/肉 ----------------
    public static final RegistryObject<Item> FLESH = ITEMS.register("flesh",
            () -> new BlockItem(ModBlocks.FLESH.get(), new Item.Properties()));
    public static final RegistryObject<Item> POROUS_FLESH = ITEMS.register("porous_flesh",
            () -> new BlockItem(ModBlocks.POROUS_FLESH.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLOOD_BUCKET = ITEMS.register("blood_bucket",
            () -> new BucketItem(ModFluids.BLOOD,
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    /** 血焰打火石（回响碎片+辛西纳石合成）：深渊传送门唯一点火钥匙，普通使用打出血焰。 */
    public static final RegistryObject<Item> BLOOD_FLINT_AND_STEEL = ITEMS.register("blood_flint_and_steel",
            () -> new BloodFlintAndSteelItem(new Item.Properties().durability(64)));

    /** 创造栏收录（mod 总线 {@link BuildCreativeModeTabContentsEvent}）。 */
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(FLESH.get());
            event.accept(POROUS_FLESH.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BLOOD_BUCKET.get());
            event.accept(BLOOD_FLINT_AND_STEEL.get());
        } else if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            BUILDING.forEach(item -> event.accept(item.get()));
        }
    }

    private ModItems() {
    }
}
