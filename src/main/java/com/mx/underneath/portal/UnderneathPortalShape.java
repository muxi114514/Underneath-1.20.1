package com.mx.underneath.portal;

import com.mx.underneath.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 深渊传送门门形判定（1.20.1 原版 PortalShape 算法的泛化重写：框材料按维度分侧）。
 *
 * <p>点燃白名单（一条规则推出全部平衡语义，无需任何特殊标记）：
 * <ul>
 * <li><b>主世界侧＝强化深板岩</b>——深暗古城中央巨门（内空~18×4 落在原版 2-21×3-21 范围内）；
 *     玩家无法获取强化深板岩 → 古城=唯一主动入口，玩家在主世界搭灰烬石门点不亮；</li>
 * <li><b>深渊侧＝Lycanites 灰烬石系</b>——portal_ruins 废墟门体（双灰烬石柱+断楣，补 2 块闭合
 *     2×6 门框）；灰烬石只在深渊可得（ashen_rocks 散布件）。运行时按名解析，不加编译依赖。</li>
 * </ul>
 * 系统生成的回程门不走点燃判定（直接放门帘）；门帘<b>存续</b>校验用两侧并集（拆主世界灰烬门
 * 框=门灭且按点燃规则不可重燃）。
 */
public final class UnderneathPortalShape {

    private static final int MIN_WIDTH = 2;
    private static final int MAX_WIDTH = 21;
    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 21;

    /** 灰烬石白名单（惰性按名解析；缺 Lycanites 时为空集=深渊侧点不亮）。 */
    private static volatile Set<Block> ashenFrames;

    private static Set<Block> ashenFrames() {
        Set<Block> set = ashenFrames;
        if (set == null) {
            set = new HashSet<>();
            for (String name : new String[]{"ashenstone", "ashenstonepillar", "ashenstonepolished"}) {
                Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("lycanitesmobs", name));
                if (b != null && b != Blocks.AIR) {
                    set.add(b);
                }
            }
            ashenFrames = set;
        }
        return set;
    }

    /** 点燃用框判定（分侧）。 */
    public static Predicate<BlockState> frameFor(boolean underneathSide) {
        return underneathSide
                ? state -> ashenFrames().contains(state.getBlock())
                : state -> state.is(Blocks.REINFORCED_DEEPSLATE);
    }

    /** 存续校验用框判定（两侧并集：门帘支撑无需分维度）。 */
    public static boolean isFrameAny(BlockState state) {
        return state.is(Blocks.REINFORCED_DEEPSLATE) || ashenFrames().contains(state.getBlock());
    }

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private final Predicate<BlockState> frame;
    private BlockPos bottomLeft;
    private int width;
    private int height;
    private int portalBlocks;

    private UnderneathPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis axis,
                                  Predicate<BlockState> frame) {
        this.level = level;
        this.axis = axis;
        this.frame = frame;
        this.rightDir = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        this.bottomLeft = calculateBottomLeft(pos);
        if (bottomLeft != null) {
            this.width = calculateWidth();
            if (width > 0) {
                this.height = calculateHeight();
            }
        }
    }

    /** 从空位出发（点燃位/校验位）在两个轴向上找有效门形。 */
    public static Optional<UnderneathPortalShape> findAt(LevelAccessor level, BlockPos pos,
                                                         boolean underneathSide) {
        Predicate<BlockState> frame = frameFor(underneathSide);
        UnderneathPortalShape x = new UnderneathPortalShape(level, pos, Direction.Axis.X, frame);
        if (x.isValid()) {
            return Optional.of(x);
        }
        UnderneathPortalShape z = new UnderneathPortalShape(level, pos, Direction.Axis.Z, frame);
        return z.isValid() ? Optional.of(z) : Optional.empty();
    }

    /** 门帘存续校验（并集框材料）：pos 处门形是否仍完整且内空全为门帘。 */
    public static boolean isCompleteAt(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        UnderneathPortalShape shape = new UnderneathPortalShape(level, pos, axis,
                UnderneathPortalShape::isFrameAny);
        return shape.isValid() && shape.portalBlocks == shape.width * shape.height;
    }

    private boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(ModBlocks.PORTAL.get());
    }

    private BlockPos calculateBottomLeft(BlockPos pos) {
        int minY = Math.max(level.getMinBuildHeight(), pos.getY() - MAX_HEIGHT);
        while (pos.getY() > minY && isEmpty(level.getBlockState(pos.below()))) {
            pos = pos.below();
        }
        Direction left = rightDir.getOpposite();
        int dist = edgeDistance(pos, left) - 1;
        return dist < 0 ? null : pos.relative(left, dist);
    }

    private int calculateWidth() {
        int w = edgeDistance(bottomLeft, rightDir);
        return (w < MIN_WIDTH || w > MAX_WIDTH) ? 0 : w;
    }

    /** 沿 dir 数连续"内空且脚下是框"的格数，撞到框即返回长度，否则 0。 */
    private int edgeDistance(BlockPos start, Direction dir) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= MAX_WIDTH; i++) {
            cursor.set(start).move(dir, i);
            BlockState state = level.getBlockState(cursor);
            if (!isEmpty(state)) {
                return frame.test(state) ? i : 0;
            }
            BlockState below = level.getBlockState(cursor.move(Direction.DOWN));
            if (!frame.test(below)) {
                return 0;
            }
        }
        return 0;
    }

    private int calculateHeight() {
        portalBlocks = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int h = distanceUntilTop(cursor);
        return (h < MIN_HEIGHT || h > MAX_HEIGHT || !hasTopFrame(cursor, h)) ? 0 : h;
    }

    private int distanceUntilTop(BlockPos.MutableBlockPos cursor) {
        for (int j = 0; j < MAX_HEIGHT; j++) {
            cursor.set(bottomLeft).move(Direction.UP, j).move(rightDir, -1);
            if (!frame.test(level.getBlockState(cursor))) {
                return j;
            }
            cursor.set(bottomLeft).move(Direction.UP, j).move(rightDir, width);
            if (!frame.test(level.getBlockState(cursor))) {
                return j;
            }
            for (int i = 0; i < width; i++) {
                cursor.set(bottomLeft).move(Direction.UP, j).move(rightDir, i);
                BlockState state = level.getBlockState(cursor);
                if (!isEmpty(state)) {
                    return j;
                }
                if (state.is(ModBlocks.PORTAL.get())) {
                    portalBlocks++;
                }
            }
        }
        return MAX_HEIGHT;
    }

    private boolean hasTopFrame(BlockPos.MutableBlockPos cursor, int h) {
        for (int i = 0; i < width; i++) {
            cursor.set(bottomLeft).move(Direction.UP, h).move(rightDir, i);
            if (!frame.test(level.getBlockState(cursor))) {
                return false;
            }
        }
        return true;
    }

    public boolean isValid() {
        return bottomLeft != null && width >= MIN_WIDTH && height >= MIN_HEIGHT;
    }

    /** 填充门帘（flag 18=通知客户端+不触发邻更连锁）。 */
    public void createPortalBlocks() {
        BlockState portal = ModBlocks.PORTAL.get().defaultBlockState()
                .setValue(UnderneathPortalBlock.AXIS, axis);
        BlockPos.betweenClosed(bottomLeft,
                        bottomLeft.relative(Direction.UP, height - 1).relative(rightDir, width - 1))
                .forEach(pos -> level.setBlock(pos, portal, 18));
    }
}
