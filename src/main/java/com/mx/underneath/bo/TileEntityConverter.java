package com.mx.underneath.bo;

import com.mx.underneath.Underneath;
import com.mx.underneath.blockmap.IdMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.12 方块实体 NBT → 1.20.1 转换器。
 *
 * <p>按<b>已放置的方块</b>派发（方块已经过 BlockMapper 映射，比旧 TE id 可靠）：
 * <ul>
 *   <li>旗帜：1.12 颜色在 TE Base（且<b>色值反转</b>：15=白）→ 换成对应彩色旗帜方块，
 *       Patterns 保留但 Color 同样按 {@code 15-旧值} 反转；</li>
 *   <li>床：TE color（正向 dye id）→ 彩色床方块；</li>
 *   <li>花盆：TE Item/Data → {@code potted_*} 方块（1.13 起盆内容进方块）；</li>
 *   <li>告示牌：Text1..4 → 1.20 {@code front_text.messages}（置 waxed 防编辑）；</li>
 *   <li>头颅：SkullType/Rot → 具体头颅方块+旋转；</li>
 *   <li>刷怪笼：SpawnData/SpawnPotentials 换 1.18+ 包裹格式，实体 id 过 {@link IdMapper#ENTITY}，
 *       装备物品过 {@link IdMapper#ITEM}（含 VC→染色皮甲），Passengers 递归；</li>
 *   <li>容器类：LootTable（含 charm 小写 lootTable）过 {@link IdMapper#LOOT}，Items 逐项转换。</li>
 * </ul>
 * 物品特例：{@code minecraft:skull} 按 Damage 分头颅；{@code minecraft:record_*}→{@code music_disc_*}；
 * 旧 {@code ench} 附魔标签丢弃（短 id 制，观测数据中无关键用例）。
 */
public final class TileEntityConverter {

    private static final String[] SKULL_ITEMS = {
            "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull", "minecraft:zombie_head",
            "minecraft:player_head", "minecraft:creeper_head", "minecraft:dragon_head"};
    private static final String[] SKULL_FLOOR = {
            "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head"};
    private static final String[] SKULL_WALL = {
            "skeleton_wall_skull", "wither_skeleton_wall_skull", "zombie_wall_head",
            "player_wall_head", "creeper_wall_head", "dragon_wall_head"};

    /** 花盆内容 (Item[:Data]) → potted 方块。 */
    private static final Map<String, String> POTTED = new HashMap<>();

    static {
        String[] flowers = {"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"};
        for (int i = 0; i < flowers.length; i++) {
            POTTED.put("minecraft:red_flower:" + i, "potted_" + flowers[i]);
        }
        POTTED.put("minecraft:red_flower", "potted_poppy");
        POTTED.put("minecraft:yellow_flower", "potted_dandelion");
        String[] saplings = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        for (int i = 0; i < saplings.length; i++) {
            POTTED.put("minecraft:sapling:" + i, "potted_" + saplings[i] + "_sapling");
        }
        POTTED.put("minecraft:sapling", "potted_oak_sapling");
        POTTED.put("minecraft:cactus", "potted_cactus");
        POTTED.put("minecraft:deadbush", "potted_dead_bush");
        POTTED.put("minecraft:red_mushroom", "potted_red_mushroom");
        POTTED.put("minecraft:brown_mushroom", "potted_brown_mushroom");
        POTTED.put("minecraft:tallgrass", "potted_fern");
        POTTED.put("minecraft:tallgrass:2", "potted_fern");
        POTTED.put("minecraft:air", "flower_pot");
    }

    /** 入口：对 pos 上已放置的方块应用旧 TE 数据。 */
    public static void apply(LevelAccessor level, BlockPos pos, CompoundTag old) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        try {
            if (block instanceof AbstractBannerBlock) {
                banner(level, pos, state, old);
            } else if (block instanceof BedBlock) {
                bed(level, pos, state, old);
            } else if (block instanceof FlowerPotBlock) {
                flowerPot(level, pos, old);
            } else if (block instanceof SignBlock) {
                sign(level, pos, old);
            } else if (block instanceof AbstractSkullBlock) {
                skull(level, pos, state, old);
            } else if (block instanceof SpawnerBlock) {
                spawner(level, pos, old);
            } else if (old.contains("Items", Tag.TAG_LIST) || old.contains("LootTable")
                    || old.contains("lootTable") || old.contains("RecordItem", Tag.TAG_COMPOUND)) {
                container(level, pos, old);
            }
        } catch (Exception ex) {
            Underneath.LOGGER.warn("TE 转换失败 @{} ({}): {}", pos, block, ex.toString());
        }
    }

    // ---------------- 各类型 ----------------

    private static void banner(LevelAccessor level, BlockPos pos, BlockState state, CompoundTag old) {
        int base = old.contains("Base") ? old.getInt("Base") : 15;
        DyeColor dye = DyeColor.byId(15 - (base & 15));    // 1.12 旗帜色值反转
        boolean wall = state.getBlock() instanceof WallBannerBlock;
        swap(level, pos, state, dye.getName() + (wall ? "_wall_banner" : "_banner"));

        if (old.contains("Patterns", Tag.TAG_LIST)) {
            ListTag patterns = old.getList("Patterns", Tag.TAG_COMPOUND).copy();
            for (Tag t : patterns) {
                CompoundTag p = (CompoundTag) t;
                p.putInt("Color", 15 - (p.getInt("Color") & 15));
            }
            CompoundTag tag = new CompoundTag();
            tag.put("Patterns", patterns);
            loadInto(level, pos, tag);
        }
    }

    private static void bed(LevelAccessor level, BlockPos pos, BlockState state, CompoundTag old) {
        int color = old.contains("color") ? old.getInt("color") : DyeColor.RED.getId();
        swap(level, pos, state, DyeColor.byId(color & 15).getName() + "_bed");
    }

    private static void flowerPot(LevelAccessor level, BlockPos pos, CompoundTag old) {
        String item = old.getString("Item");
        int data = old.getInt("Data");
        String potted = POTTED.getOrDefault(item + ":" + data, POTTED.get(item));
        if (potted != null) {
            Block nb = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", potted));
            if (nb != null) {
                level.setBlock(pos, nb.defaultBlockState(), 2);
            }
        }
    }

    private static void sign(LevelAccessor level, BlockPos pos, CompoundTag old) {
        ListTag messages = new ListTag();
        for (int i = 1; i <= 4; i++) {
            String text = old.getString("Text" + i);
            messages.add(StringTag.valueOf(text.isEmpty() ? "\"\"" : text));
        }
        CompoundTag front = new CompoundTag();
        front.put("messages", messages);
        front.putString("color", "black");
        front.putBoolean("has_glowing_text", false);
        ListTag empty = new ListTag();
        for (int i = 0; i < 4; i++) {
            empty.add(StringTag.valueOf("\"\""));
        }
        CompoundTag back = new CompoundTag();
        back.put("messages", empty);
        back.putString("color", "black");
        back.putBoolean("has_glowing_text", false);

        CompoundTag tag = new CompoundTag();
        tag.put("front_text", front);
        tag.put("back_text", back);
        tag.putBoolean("is_waxed", true);    // 装饰牌防玩家改写
        loadInto(level, pos, tag);
    }

    private static void skull(LevelAccessor level, BlockPos pos, BlockState state, CompoundTag old) {
        int type = Math.min(Math.max(old.getByte("SkullType"), 0), 5);
        boolean wall = state.getBlock() instanceof WallSkullBlock;
        Block nb = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("minecraft", (wall ? SKULL_WALL : SKULL_FLOOR)[type]));
        if (nb == null) {
            return;
        }
        BlockState ns = withProps(state, nb.defaultBlockState());
        if (!wall && ns.hasProperty(BlockStateProperties.ROTATION_16)) {
            ns = ns.setValue(BlockStateProperties.ROTATION_16, old.getByte("Rot") & 15);
        }
        level.setBlock(pos, ns, 2);
    }

    private static void spawner(LevelAccessor level, BlockPos pos, CompoundTag old) {
        CompoundTag tag = new CompoundTag();
        for (String key : new String[]{"Delay", "MinSpawnDelay", "MaxSpawnDelay", "SpawnCount",
                "MaxNearbyEntities", "RequiredPlayerRange", "SpawnRange"}) {
            if (old.contains(key)) {
                tag.putShort(key, old.getShort(key));
            }
        }
        boolean parasite = false;
        if (old.contains("SpawnData", Tag.TAG_COMPOUND)) {
            CompoundTag entity = convertEntity(old.getCompound("SpawnData"));
            parasite = entity.getString("id").startsWith("epca:");
            tag.put("SpawnData", wrapSpawnData(entity));
        }
        if (old.contains("SpawnPotentials", Tag.TAG_LIST)) {
            ListTag potentials = new ListTag();
            for (Tag t : old.getList("SpawnPotentials", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) t;
                CompoundTag entity = e.contains("Entity", Tag.TAG_COMPOUND)
                        ? e.getCompound("Entity") : new CompoundTag();
                CompoundTag converted = convertEntity(entity);
                parasite |= converted.getString("id").startsWith("epca:");
                CompoundTag entry = new CompoundTag();
                entry.put("data", wrapSpawnData(converted));
                entry.putInt("weight", e.contains("Weight") ? Math.max(e.getInt("Weight"), 1) : 1);
                potentials.add(entry);
            }
            tag.put("SpawnPotentials", potentials);
        }
        // 寄生虫笼（映射后含 epca 实体）→ 候选池整体替换为 config 池（用户拍板：30 种全家桶）
        if (parasite && !SpawnerPool.get().isEmpty()) {
            tag.remove("SpawnData");
            tag.put("SpawnPotentials", SpawnerPool.buildPotentials());
        }
        loadInto(level, pos, tag);
    }

    /**
     * SpawnData 包裹：entity + custom_spawn_rules 全光照带（0-15）。
     * 带 custom_spawn_rules 时 BaseSpawner <b>跳过 SpawnPlacements 生成谓词</b>——EPCA 等 mod 怪
     * 要求光照&lt;8（javap 实锤），城市楼体自带灯=笼子全灭；1.12 笼无此限制，全光照带=忠实修复。
     */
    public static CompoundTag wrapSpawnData(CompoundTag entity) {
        CompoundTag wrap = new CompoundTag();
        wrap.put("entity", entity);
        CompoundTag rules = new CompoundTag();
        rules.put("block_light_limit", lightRange());
        rules.put("sky_light_limit", lightRange());
        wrap.put("custom_spawn_rules", rules);
        return wrap;
    }

    private static CompoundTag lightRange() {
        CompoundTag range = new CompoundTag();
        range.putInt("min_inclusive", 0);
        range.putInt("max_inclusive", 15);
        return range;
    }

    private static void container(LevelAccessor level, BlockPos pos, CompoundTag old) {
        CompoundTag tag = new CompoundTag();
        String loot = old.contains("LootTable") ? old.getString("LootTable")
                : old.contains("lootTable") ? old.getString("lootTable") : null;   // charm:crate 用小写
        if (loot != null && !loot.isEmpty()) {
            tag.putString("LootTable", IdMapper.LOOT.mapId(loot));
            if (old.contains("LootTableSeed")) {
                tag.putLong("LootTableSeed", old.getLong("LootTableSeed"));
            }
        }
        if (old.contains("Items", Tag.TAG_LIST)) {
            ListTag items = new ListTag();
            for (Tag t : old.getList("Items", Tag.TAG_COMPOUND)) {
                items.add(convertItem((CompoundTag) t));
            }
            tag.put("Items", items);
        }
        if (old.contains("RecordItem", Tag.TAG_COMPOUND)) {
            tag.put("RecordItem", convertItem(old.getCompound("RecordItem")));
        }
        if (!tag.isEmpty()) {
            loadInto(level, pos, tag);
        }
    }

    // ---------------- 实体/物品 ----------------

    /** 实体 NBT 转换：id 映射 + 装备物品映射 + Passengers 递归。 */
    private static CompoundTag convertEntity(CompoundTag old) {
        CompoundTag entity = old.copy();
        if (entity.contains("id")) {
            entity.putString("id", IdMapper.ENTITY.mapId(entity.getString("id")));
        }
        for (String key : new String[]{"ArmorItems", "HandItems"}) {
            if (entity.contains(key, Tag.TAG_LIST)) {
                ListTag converted = new ListTag();
                for (Tag t : entity.getList(key, Tag.TAG_COMPOUND)) {
                    CompoundTag stack = (CompoundTag) t;
                    converted.add(stack.isEmpty() ? stack : convertItem(stack));
                }
                entity.put(key, converted);
            }
        }
        if (entity.contains("Passengers", Tag.TAG_LIST)) {
            ListTag passengers = new ListTag();
            for (Tag t : entity.getList("Passengers", Tag.TAG_COMPOUND)) {
                passengers.add(convertEntity((CompoundTag) t));
            }
            entity.put("Passengers", passengers);
        }
        return entity;
    }

    /** 物品堆转换：Damage 变体特例 + record 改名 + IdMapper.ITEM（可带附加 NBT，如染色皮甲）。 */
    private static CompoundTag convertItem(CompoundTag old) {
        String id = old.getString("id");
        int damage = old.getShort("Damage");
        CompoundTag extraTag = null;

        if ("minecraft:skull".equals(id)) {
            id = SKULL_ITEMS[Math.min(Math.max(damage, 0), 5)];
        } else if (id.startsWith("minecraft:record_")) {
            id = "minecraft:music_disc_" + id.substring("minecraft:record_".length());
        } else {
            IdMapper.Mapping mapping = IdMapper.ITEM.mapping(id);
            if (mapping != null) {
                id = mapping.id();
                extraTag = mapping.tag() != null ? mapping.tag().copy() : null;
            }
        }

        CompoundTag stack = new CompoundTag();
        if (old.contains("Slot")) {
            stack.putByte("Slot", old.getByte("Slot"));
        }
        stack.putString("id", id);
        stack.putByte("Count", old.contains("Count") ? old.getByte("Count") : 1);
        if (extraTag != null) {
            stack.put("tag", extraTag);
        } else if (old.contains("tag", Tag.TAG_COMPOUND)) {
            CompoundTag tag = old.getCompound("tag").copy();
            tag.remove("ench");    // 1.12 短 id 附魔制，丢弃（观测数据无关键用例）
            if (!tag.isEmpty()) {
                stack.put("tag", tag);
            }
        }
        return stack;
    }

    // ---------------- 工具 ----------------

    /** 换方块并保留原方块态的同名属性（facing/part/rotation…）。 */
    private static void swap(LevelAccessor level, BlockPos pos, BlockState from, String newBlockPath) {
        Block nb = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", newBlockPath));
        if (nb != null && nb != from.getBlock()) {
            level.setBlock(pos, withProps(from, nb.defaultBlockState()), 2);
        }
    }

    private static BlockState withProps(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = copyProp(from, to, property);
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copyProp(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    /** 把转换后的 tag 灌进 BE；运行时(placebo/ServerLevel)补发客户端包，worldgen(WorldGenRegion)存盘后随区块下发。 */
    private static void loadInto(LevelAccessor level, BlockPos pos, CompoundTag tag) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        be.load(tag);
        be.setChanged();
        // 运行时才需即时同步；worldgen 阶段无观察客户端、区块随后整体下发，跳过补包
        if (level instanceof ServerLevel server) {
            BlockState state = server.getBlockState(pos);
            server.sendBlockUpdated(pos, state, state, 3);
            // sendBlockUpdated 不携带 BE 数据——必须显式补发 BE 数据包，否则已加载区块客户端看到空数据（"全白旗"根因）
            var packet = be.getUpdatePacket();
            if (packet != null) {
                server.getChunkSource().chunkMap
                        .getPlayers(new net.minecraft.world.level.ChunkPos(pos), false)
                        .forEach(player -> player.connection.send(packet));
            }
        }
    }

    private TileEntityConverter() {
    }
}
