package com.mx.underneath.bo;

import com.mx.underneath.Underneath;
import com.mx.underneath.blockmap.BlockMapper;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BO2/BO3/BO4 文本解析器（OTG 格式）。
 *
 * <p>函数名长短两套都认（OTG {@code BO3Loader/BO4Loader} 注册表）：Block/B、RandomBlock/RB、
 * Branch/BR、WeightedBranch/WBR；实体/粒子/刷怪/检查类（E/P/S/MD/BC/BCN/LC/MC/MCN/MCO）暂跳过。
 * 方块 token 经 {@link BlockMapper} 解析成 1.20.1 方块态并调色板去重。
 *
 * <p>分支行格式差异（对齐 OTG 源码）：BO4 = {@code x,y,z,required,[名,旋转,几率,深度]×N[,totalChance][,组名]}；
 * BO3 = {@code x,y,z,[名,旋转,几率]×N[,totalChance]}。required 行只取第一个节点（OTG 同款语义）。
 */
public final class BOParser {

    public static BOObject parse(String name, boolean bo4, String sourceDir, BufferedReader reader) throws Exception {
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        List<BlockState> palette = new ArrayList<>();
        List<int[]> blocks = new ArrayList<>();          // [x,y,z,stateIdx]
        List<String> nbtNames = new ArrayList<>();
        List<int[]> rndPos = new ArrayList<>();          // [x,y,z]
        List<int[]> rndChoices = new ArrayList<>();
        List<String[]> rndNbtList = new ArrayList<>();   // 每随机块的候选 nbt（与 rndChoices 同条同序）
        List<BOObject.BranchLine> branches = new ArrayList<>();

        String spawnHeight = "randomY";
        int minHeight = 0, maxHeight = 256;
        boolean isTree = false, rotateRandomly = false;
        boolean canOverride = false;
        int branchFrequency = 0;
        int frequency = 0;
        double rarity = 100.0;
        List<String> sourceTokens = List.of("air");
        int maxPctOutside = 100;
        boolean outsidePlaceAnyway = true;
        List<BOObject.BlockCheckLine> checks = new ArrayList<>();
        List<BOObject.EntityLine> entityLines = new ArrayList<>();

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int paren = line.indexOf('(');
            String lower = line.toLowerCase(Locale.ROOT);
            if (paren > 0 && line.endsWith(")")) {
                String fn = lower.substring(0, paren);
                String[] args = splitArgs(line.substring(paren + 1, line.length() - 1));
                switch (fn) {
                    case "block", "b" -> {
                        if (args.length >= 4) {
                            int idx = paletteOf(args[3], paletteIndex, palette);
                            blocks.add(new int[]{parseInt(args[0]), parseInt(args[1]), parseInt(args[2]), idx});
                            nbtNames.add(args.length >= 5 && isNbt(args[4]) ? args[4] : null);
                        }
                    }
                    case "randomblock", "rb" -> {
                        if (args.length >= 5) {
                            rndPos.add(new int[]{parseInt(args[0]), parseInt(args[1]), parseInt(args[2])});
                            List<Integer> choices = new ArrayList<>();
                            List<String> choiceNbt = new ArrayList<>();   // 每候选的 nbt（城市战利品在此！之前被 i++ 丢弃）
                            int i = 3;
                            while (i < args.length) {
                                String mat = args[i++];
                                String cnbt = null;
                                if (i < args.length && isNbt(args[i])) {
                                    cnbt = args[i];    // 材质与几率之间的可选 NBT 文件参数——保留(容器 loot)
                                    i++;
                                }
                                int chance = i < args.length ? (int) parseDouble(args[i++]) : 100;
                                choices.add(paletteOf(mat, paletteIndex, palette));
                                choices.add(chance);
                                choiceNbt.add(cnbt);
                            }
                            rndChoices.add(choices.stream().mapToInt(Integer::intValue).toArray());
                            rndNbtList.add(choiceNbt.toArray(new String[0]));
                        }
                    }
                    case "branch", "br" -> parseBranch(args, bo4, false, branches);
                    case "weightedbranch", "wbr" -> parseBranch(args, bo4, true, branches);
                    case "blockcheck", "bc", "blockchecknot", "bcn" -> {
                        if (args.length >= 4) {
                            boolean not = fn.startsWith("blockchecknot") || fn.equals("bcn");
                            checks.add(new BOObject.BlockCheckLine(
                                    parseInt(args[0]), parseInt(args[1]), parseInt(args[2]), not,
                                    BlockMatcher.compile(Arrays.asList(args).subList(3, args.length))));
                        }
                    }
                    case "entity", "e" -> {
                        // Entity(x,y,z,名,数量)——beckon_spawn 等直放实体的 BO3
                        if (args.length >= 5) {
                            entityLines.add(new BOObject.EntityLine(
                                    parseInt(args[0]), parseInt(args[1]), parseInt(args[2]),
                                    args[3].trim(), Math.max(1, parseIntSafe(args[4], 1))));
                        }
                    }
                    default -> {
                        // 实体/粒子/刷怪/光照检查类：跳过
                    }
                }
            } else {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = lower.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    switch (key) {
                        case "spawnheight" -> spawnHeight = value;
                        case "minheight" -> minHeight = parseIntSafe(value, minHeight);
                        case "maxheight" -> maxHeight = parseIntSafe(value, maxHeight);
                        case "tree" -> isTree = "true".equalsIgnoreCase(value);
                        case "rotaterandomly" -> rotateRandomly = "true".equalsIgnoreCase(value);
                        case "canoverride" -> canOverride = "true".equalsIgnoreCase(value);
                        case "branchfrequency" -> branchFrequency = parseIntSafe(value, 0);
                        case "frequency" -> frequency = parseIntSafe(value, 0);
                        case "rarity" -> rarity = parseDoubleSafe(value, 100.0);
                        case "sourceblock", "sourceblocks" -> sourceTokens = List.of(value.split(","));
                        case "maxpercentageoutsidesourceblock" -> maxPctOutside = parseIntSafe(value, 100);
                        case "outsidesourceblock" -> outsidePlaceAnyway = !"dontplace".equalsIgnoreCase(value.trim());
                        default -> {
                        }
                    }
                }
            }
        }

        int n = blocks.size();
        int[] xyz = new int[n * 3];
        int[] stateIdx = new int[n];
        String[] nbts = new String[n];
        boolean anyNbt = false;
        for (int i = 0; i < n; i++) {
            int[] b = blocks.get(i);
            xyz[i * 3] = b[0];
            xyz[i * 3 + 1] = b[1];
            xyz[i * 3 + 2] = b[2];
            stateIdx[i] = b[3];
            nbts[i] = nbtNames.get(i);
            anyNbt |= nbts[i] != null;
        }
        int rn = rndPos.size();
        int[] rXyz = new int[rn * 3];
        for (int i = 0; i < rn; i++) {
            int[] p = rndPos.get(i);
            rXyz[i * 3] = p[0];
            rXyz[i * 3 + 1] = p[1];
            rXyz[i * 3 + 2] = p[2];
        }
        // 随机块 nbt：任一候选有 nbt 才保留数组（否则空，省内存）
        boolean anyRndNbt = false;
        for (String[] arr : rndNbtList) {
            for (String s : arr) {
                if (s != null) {
                    anyRndNbt = true;
                    break;
                }
            }
            if (anyRndNbt) {
                break;
            }
        }
        return new BOObject(name, bo4, sourceDir, palette.toArray(new BlockState[0]), xyz, stateIdx,
                rXyz, rndChoices.toArray(new int[0][]),
                anyRndNbt ? rndNbtList.toArray(new String[0][]) : new String[0][],
                anyNbt ? nbts : new String[0],
                List.copyOf(branches), spawnHeight, minHeight,
                // OTG BO4Config 规范化：max<min 时取 min（GodlySpike-50 数据 46<55 → 55）
                Math.max(maxHeight, minHeight), isTree, rotateRandomly, canOverride, branchFrequency,
                frequency, rarity, BlockMatcher.compile(sourceTokens), maxPctOutside, outsidePlaceAnyway,
                List.copyOf(checks), List.copyOf(entityLines));
    }

    /**
     * 分支行解析（BO4 四元组 / BO3 三元组，required/weighted 兼容处理）。
     */
    private static void parseBranch(String[] args, boolean bo4, boolean weighted,
                                    List<BOObject.BranchLine> branches) {
        if (args.length < (bo4 ? 5 : 6)) {
            return;
        }
        int x = parseInt(args[0]), y = parseInt(args[1]), z = parseInt(args[2]);
        int i = 3;
        boolean required = false;
        // BO4 第 4 参为 required 布尔；BO3 无（但宽松兼容：见着布尔就吃掉）
        if (i < args.length && ("true".equalsIgnoreCase(args[i]) || "false".equalsIgnoreCase(args[i]))) {
            required = "true".equalsIgnoreCase(args[i]);
            i++;
        }
        int step = bo4 ? 4 : 3;
        List<BOObject.BranchNode> nodes = new ArrayList<>();
        // 节点参数必须齐全（BO4 四个/BO3 三个，镜像 OTG 的 i < size-3 循环条件）——
        // 否则行尾 branchGroup 字符串会被误当节点名吞掉
        while (i + step - 1 < args.length && !isNumeric(args[i])) {
            String name = args[i];
            String rot = args[i + 1];
            double chance = isNumeric(args[i + 2]) ? parseDouble(args[i + 2]) : 100.0;
            int depth = bo4 && isNumeric(args[i + 3]) ? (int) parseDouble(args[i + 3]) : 0;
            nodes.add(new BOObject.BranchNode(name.toLowerCase(Locale.ROOT), BORotation.parse(rot), chance, depth));
            i += step;
            // OTG：required 的**普通 Branch** 只用第一个节点；但 required 的 **WeightedBranch 仍保留全部候选**
            // （在所有候选里轮盘赌，required 只保证"必生成其一"）。之前一律 break 把城市 WBR 砍成单楼=全城一种建筑。
            if (required && !weighted) {
                break;
            }
        }
        double totalChance = 100.0;
        if (i < args.length && isNumeric(args[i])) {
            totalChance = parseDouble(args[i]);
        }
        if (!nodes.isEmpty()) {
            branches.add(new BOObject.BranchLine(x, y, z, required, weighted, totalChance, List.copyOf(nodes)));
        }
    }

    private static int paletteOf(String token, Map<BlockState, Integer> index, List<BlockState> palette) {
        BlockState state = BlockMapper.INSTANCE.resolve(token);
        Integer idx = index.get(state);
        if (idx == null) {
            idx = palette.size();
            palette.add(state);
            index.put(state, idx);
        }
        return idx;
    }

    private static String[] splitArgs(String body) {
        String[] parts = body.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private static boolean isNbt(String arg) {
        return arg.toLowerCase(Locale.ROOT).endsWith(".nbt");
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = s.charAt(0) == '-' ? 1 : 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            Underneath.LOGGER.debug("BO 参数非数字: {}", s);
            return 0;
        }
    }

    private static double parseDoubleSafe(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private BOParser() {
    }
}
