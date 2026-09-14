package com.mx.underneath.bo;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 解析后的单个 BO 对象（BO2/BO3/BO4 通用内存表示）。
 *
 * <p>紧凑存储：{@code palette}（去重方块态）+ 平行数组 {@code xyz}（x,y,z 交错）/{@code stateIdx}。
 * 342MB 文本数据惰性解析，每对象仅驻留数组，不建对象海。
 *
 * <p>分支模型（对齐 OTG）：一行 Branch = 一个 {@link BranchLine}（坐标+required+totalChance+若干候选节点）。
 * BO4 行格式 {@code (x,y,z,required,[名,旋转,几率,深度]×N[,totalChance][,branchGroup])}；
 * BO3 行格式 {@code (x,y,z,[名,旋转,几率]×N[,totalChance])}。
 * required 行只取第一个节点且必生成；非 required 单节点且几率≥totalChance 亦为确定性——两者可被 L1.5 扁平化。
 *
 * <p>RandomBlock 语义（OTG）：按序尝试每个 (state,chance)，roll(100)&lt;chance 则放置并停止。
 */
public final class BOObject {

    /** 分支候选节点。rotId=OTG 旋转 id（N0/W1/S2/E3）。 */
    public record BranchNode(String name, int rotId, double chance, int depth) {
    }

    /** 一行 BlockCheck/BlockCheckNot（BO3；坐标随放置旋转变换，not=BCN 反义）。 */
    public record BlockCheckLine(int x, int y, int z, boolean not, BlockMatcher matcher) {
    }

    /** 一行 Entity（BO3 直放实体，如 beckon_spawn 的寄生虫召唤者；id=1.12 原名，放置时过 entity_map）。 */
    public record EntityLine(int x, int y, int z, String id, int count) {
    }

    /** 一行 Branch/WeightedBranch。 */
    public record BranchLine(int x, int y, int z, boolean required, boolean weighted,
                             double totalChance, List<BranchNode> nodes) {

        /** 是否确定性（可扁平化）：required 恒生成；非 required 单节点且几率拉满。 */
        public boolean deterministic() {
            if (this.nodes.isEmpty()) {
                return false;
            }
            if (this.required) {
                return true;
            }
            return !this.weighted && this.nodes.size() == 1
                    && this.nodes.get(0).chance() >= this.totalChance;
        }
    }

    public final String name;
    /** 文件类型：BO4 的方块坐标是 chunk 对齐 0..15，旋转变换与 BO3 不同。 */
    public final boolean bo4;
    /** 源文件所在资源目录（如 {@code bo/biomeobjects/access_duct}），nbt 相对路径的解析基准。 */
    public final String sourceDir;
    public final BlockState[] palette;
    /** 普通方块：x,y,z 交错三元组。 */
    public final int[] xyz;
    /** 普通方块：对应 palette 下标。 */
    public final int[] stateIdx;
    /** 随机方块：x,y,z 交错三元组。 */
    public final int[] rndXyz;
    /** 随机方块：每条=[stateIdx,chance, stateIdx,chance, ...]。 */
    public final int[][] rndChoices;
    /** 随机方块每候选的 NBT 文件名（与 rndChoices 同条同序：rndNbt[k][c]=第 k 条第 c 候选的 nbt，null=无）。
     *  城市战利品/刷怪笼多在 RandomBlock 的容器候选上（如 DROPPER+city loot.nbt）。长度 0=全无。 */
    public final String[][] rndNbt;
    /** 方块实体 NBT 文件名（与 xyz 同序，null=无；P1④ 接入；长度 0=全无）。 */
    public final String[] nbtNames;

    public final List<BranchLine> branches;
    public final String spawnHeight;
    public final int minHeight;
    public final int maxHeight;
    public final boolean isTree;
    public final boolean rotateRandomly;
    /** OTG CanOverride：true=不参与分支碰撞（城市零件全为 true）。 */
    public final boolean canOverride;
    /** OTG BranchFrequency：>0 时同名分支在结构内的最小 chunk 距离（如 8 车道大道端片=15）。 */
    public final int branchFrequency;
    /** OTG 头 Frequency（CustomObject 资源=每 chunk 尝试次数）。 */
    public final int frequency;
    /** OTG 头 Rarity（每次尝试的百分比几率）。 */
    public final double rarity;
    /** SourceBlocks 匹配器（默认 AIR）。 */
    public final BlockMatcher sourceBlocks;
    /** MaxPercentageOutsideSourceBlock（默认 100=不限）。 */
    public final int maxPctOutside;
    /** OutsideSourceBlock: placeAnyway(true)/dontPlace(false)，默认 placeAnyway。 */
    public final boolean outsidePlaceAnyway;
    /** BlockCheck/BlockCheckNot 行（生成前提检查）。 */
    public final List<BlockCheckLine> checks;
    /** Entity 行（直放实体）。 */
    public final List<EntityLine> entityLines;

    BOObject(String name, boolean bo4, String sourceDir, BlockState[] palette, int[] xyz, int[] stateIdx,
             int[] rndXyz, int[][] rndChoices, String[][] rndNbt, String[] nbtNames, List<BranchLine> branches,
             String spawnHeight, int minHeight, int maxHeight, boolean isTree, boolean rotateRandomly,
             boolean canOverride, int branchFrequency, int frequency, double rarity,
             BlockMatcher sourceBlocks, int maxPctOutside, boolean outsidePlaceAnyway,
             List<BlockCheckLine> checks, List<EntityLine> entityLines) {
        this.name = name;
        this.bo4 = bo4;
        this.sourceDir = sourceDir;
        this.palette = palette;
        this.xyz = xyz;
        this.stateIdx = stateIdx;
        this.rndXyz = rndXyz;
        this.rndChoices = rndChoices;
        this.rndNbt = rndNbt;
        this.nbtNames = nbtNames;
        this.branches = branches;
        this.spawnHeight = spawnHeight;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.isTree = isTree;
        this.rotateRandomly = rotateRandomly;
        this.canOverride = canOverride;
        this.branchFrequency = branchFrequency;
        this.frequency = frequency;
        this.rarity = rarity;
        this.sourceBlocks = sourceBlocks;
        this.maxPctOutside = maxPctOutside;
        this.outsidePlaceAnyway = outsidePlaceAnyway;
        this.checks = checks;
        this.entityLines = entityLines;
    }

    /** OTG spawnAllBlocks：placeAnyway 且比例不限 → 源方块检查零开销直通。 */
    public boolean skipSourceCheck() {
        return this.outsidePlaceAnyway && this.maxPctOutside >= 100;
    }

    public int blockCount() {
        return this.stateIdx.length;
    }

    public boolean hasBranches() {
        return !this.branches.isEmpty();
    }

    /** 随机方块按 OTG 语义掷选：命中返回 palette 下标，未命中返回 -1。 */
    public int rollRandom(int[] choices, RandomSource random) {
        for (int i = 0; i + 1 < choices.length; i += 2) {
            if (random.nextInt(100) < choices[i + 1]) {
                return choices[i];
            }
        }
        return -1;
    }
}
