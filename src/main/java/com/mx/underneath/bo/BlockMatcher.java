package com.mx.underneath.bo;

import com.mx.underneath.blockmap.BlockMapper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OTG MaterialSet 的 1.20 匹配器（SourceBlocks / BlockCheck 材料清单共用）。
 *
 * <p>语义对齐 OTG：{@code Solid}=任意实心、{@code AIR}=空气；无 meta 的 1.12 token 匹配<b>全部 meta 家族</b>
 * （CONCRETE=16 色混凝土、STONE=石+花岗闪长安山全系……），带 meta 精确到该 meta 对应方块。
 * 1.20 无 meta，家族经 legacy 表按 token:0..15 展开收集为 Block 集合（Block 级匹配，态属性忽略）。
 */
public record BlockMatcher(boolean matchSolid, boolean matchAir, Set<Block> blocks) {

    /** 1.12 数字 id 微表（仅数据中实际观测到的；legacy 表无数字键）。 */
    private static final Map<String, String> NUMERIC_IDS = Map.of(
            "81", "cactus", "102", "thin_glass", "124", "redstone_lamp_on", "154", "hopper");

    public boolean matches(BlockState state) {
        if (this.matchAir && state.isAir()) {
            return true;
        }
        if (this.matchSolid && !state.isAir() && state.blocksMotion()) {
            return true;
        }
        return this.blocks.contains(state.getBlock());
    }

    /** 编译材料 token 列表；未知 token 静默跳过（缺 mod 回退原则）。 */
    public static BlockMatcher compile(List<String> tokens) {
        boolean solid = false;
        boolean air = false;
        Set<Block> blocks = new HashSet<>();
        for (String raw : tokens) {
            String t = raw.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            if ("solid".equals(t)) {
                solid = true;
                continue;
            }
            if ("air".equals(t)) {
                air = true;
                continue;
            }
            String mapped = NUMERIC_IDS.get(t);
            if (mapped != null) {
                t = mapped;
            }
            int colon = t.lastIndexOf(':');
            boolean hasMeta = colon > 0 && isDigits(t.substring(colon + 1));
            if (hasMeta) {
                addBlock(blocks, t);
            } else {
                // 无 meta=匹配全 meta 家族（OTG MaterialSet 语义）
                addBlock(blocks, t);
                for (int m = 0; m < 16; m++) {
                    addBlock(blocks, t + ":" + m);
                }
            }
        }
        return new BlockMatcher(solid, air, Set.copyOf(blocks));
    }

    private static void addBlock(Set<Block> out, String token) {
        BlockState s = BlockMapper.INSTANCE.resolveOrNull(token);
        if (s != null) {
            out.add(s.getBlock());
            return;
        }
        // 现代 id（如 minecraft:purple_glazed_terracotta）直查注册表兜底
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(token);
        if (rl != null) {
            Block b = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(rl);
            if (b != null && b != net.minecraft.world.level.block.Blocks.AIR) {
                out.add(b);
            }
        }
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
