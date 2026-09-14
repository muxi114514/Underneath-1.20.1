package com.mx.underneath.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mx.underneath.bo.BOFlattener;
import com.mx.underneath.bo.BOObject;
import com.mx.underneath.bo.BOPlacer;
import com.mx.underneath.bo.BORegistry;
import com.mx.underneath.bo.BORotation;
import com.mx.underneath.bo.OTGStructureExpander;
import com.mx.underneath.Underneath;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * 调试指令（OP）：{@code /underneath placebo <对象名> [旋转]} 在脚下放置任意 BO 对象。
 * 默认走 L1.5 扁平化（确定性分支已展开成整体）；随机选楼层（L2）未展开、只在结果里计数提示。
 * P1③ 的验收入口——feature 接入随 P2 群系一起做。
 */
@Mod.EventBusSubscriber(modid = Underneath.MOD_ID)
public final class UnderneathCommands {

    private UnderneathCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("underneath")
                .requires(source -> source.hasPermission(2))
                // 运行时地形探针：采样当前维度真实 finalDensity 列，输出各 Y 段实/空——数据包与引擎接线的最终裁判
                .then(Commands.literal("density").executes(UnderneathCommands::probeDensity))
                // L2 调试：脚下拼城（默认 citygrid，半径 8 chunk）——只铺周围半径内零件保证局部完整，验证拼装
                .then(Commands.literal("city")
                        .executes(ctx -> buildCity(ctx, "citygrid", 8))
                        .then(Commands.argument("root", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(BORegistry.INSTANCE.names(), builder))
                                .executes(ctx -> buildCity(ctx, StringArgumentType.getString(ctx, "root"), 8))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> buildCity(ctx, StringArgumentType.getString(ctx, "root"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("placebo")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(BORegistry.INSTANCE.names(), builder))
                                .executes(ctx -> place(ctx, 0))
                                .then(Commands.argument("rotation", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        new String[]{"north", "west", "south", "east"}, builder))
                                        .executes(ctx -> place(ctx,
                                                BORotation.parse(StringArgumentType.getString(ctx, "rotation"))))))));
    }

    private static int place(CommandContext<CommandSourceStack> ctx, int rotSteps) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT);
        BOFlattener.Flattened flat = BORegistry.INSTANCE.getFlattened(name);
        if (flat == null) {
            ctx.getSource().sendFailure(Component.literal("未找到对象: " + name));
            return 0;
        }
        if (flat.blockCount() == 0 && flat.rndChoices().length == 0) {
            ctx.getSource().sendFailure(Component.literal("对象为空或全部解析失败: " + name));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());
        RandomSource random = level.getRandom();

        // 统一落块器（与 worldgen bo_scatter Feature 共用）
        BOPlacer.Result result = BOPlacer.place(level, origin, flat, rotSteps, random);

        final int count = result.placed();
        final int nbtCount = result.nbtApplied();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "已放置 %s：%d 方块（拼装 %d 片，调色板 %d，方块实体 %d/%d%s%s%s）",
                name, count, flat.expandedPieces(), flat.palette().length,
                nbtCount, flat.nbtBlockIdx().length,
                flat.unresolvedLines() > 0 ? "，随机分支未展开 " + flat.unresolvedLines() + " 行(L2)" : "",
                flat.missingObjects() > 0 ? "，缺对象 " + flat.missingObjects() : "",
                flat.truncated() ? "，已截断!" : "")), true);
        return count;
    }

    /**
     * L2 城市：脚下展开整城，但只铺**玩家周围 radius(chunk) 内**的零件——保证局部完整、不半途截断，
     * 便于验证拼装（街道连通/楼朝向）。8M 方块硬上限仅防 OOM。
     */
    private static int buildCity(CommandContext<CommandSourceStack> ctx, String root, int radius) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());
        long seed = level.getSeed();
        // 忠实 OTG 展开（环境全放行=无碰撞/无群系裁剪的"理想城"，用于快测拼装）；
        // RNG 按 OTG plotter 语义：y 恒 0
        java.util.Random rng0 = OTGStructureExpander.randomForCoords(origin.getX() + 8, 0, origin.getZ() + 7, seed);
        OTGStructureExpander.Result city = OTGStructureExpander.expand(BORegistry.INSTANCE,
                root.toLowerCase(Locale.ROOT), origin.getX(), origin.getY(), origin.getZ(), 0,
                rng0, OTGStructureExpander.FREE);
        if (city.pieces().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("城市为空/根未找到: " + root));
            return 0;
        }
        int ocx = origin.getX() >> 4;
        int ocz = origin.getZ() >> 4;
        RandomSource random = level.getRandom();
        long blocks = 0;
        int placedPieces = 0;
        int inRange = 0;
        boolean capped = false;
        for (OTGStructureExpander.Piece p : city.pieces()) {
            if (Math.abs((p.x() >> 4) - ocx) > radius || Math.abs((p.z() >> 4) - ocz) > radius) {
                continue;
            }
            inRange++;
            BOObject obj = BORegistry.INSTANCE.get(p.name());
            if (obj == null) {
                continue;
            }
            blocks += BOPlacer.placeOwnBlocks(level, new BlockPos(p.x(), p.y(), p.z()), obj, p.rot(), random);
            placedPieces++;
            if (blocks > 8_000_000L) {
                capped = true;
                break;
            }
        }
        final int pieces = placedPieces;
        final int rng = inRange;
        final long blk = blocks;
        final boolean cap = capped;
        final int total = city.pieces().size();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "已拼装 %s：半径%d内 %d/%d 零件(全城展开 %d)，%d 方块%s%s",
                root, radius, pieces, rng, total, blk,
                city.missing() > 0 ? "，缺零件 " + city.missing() : "",
                cap ? "，放置达 800 万上限停" : "")), true);
        return pieces;
    }

    /**
     * 双路诊断：同一列并排打印【finalDensity 函数意见】与【硬盘上真实方块】。
     * 两行一致=地形正确(看不见=维度漆黑/感知问题)；两行矛盾=放方块无视密度=真 gen bug。
     */
    private static int probeDensity(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        var fd = level.getChunkSource().randomState().router().finalDensity();
        BlockPos p = BlockPos.containing(ctx.getSource().getPosition());
        int x = p.getX();
        int z = p.getZ();

        String densLine = "密度@" + x + "," + z + ": " + segments(y -> fd.compute(
                new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z)) > 0);
        String blockLine = "方块: " + segments(y -> !level.getBlockState(
                new BlockPos(x, y, z)).isAir());

        ctx.getSource().sendSuccess(() -> Component.literal(densLine), false);
        ctx.getSource().sendSuccess(() -> Component.literal(blockLine), false);
        return 1;
    }

    /** Y-64..320 步 4 扫描 solidity 判定器，返回 "实[a-b] 空[b-c] ..." 分段串。 */
    private static String segments(java.util.function.IntPredicate solidAt) {
        StringBuilder sb = new StringBuilder();
        boolean prev = false;
        int segStart = -64;
        for (int y = -64; y <= 320; y += 4) {
            boolean solid = solidAt.test(y);
            if (y == -64) {
                prev = solid;
            } else if (solid != prev || y == 320) {
                sb.append(prev ? "实" : "空").append('[').append(segStart).append('-').append(y).append("] ");
                prev = solid;
                segStart = y;
            }
        }
        return sb.toString();
    }
}
