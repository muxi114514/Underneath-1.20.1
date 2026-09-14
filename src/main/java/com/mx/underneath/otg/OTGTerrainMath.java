package com.mx.underneath.otg;

import java.util.HashMap;
import java.util.Random;

/**
 * OTG {@code ChunkProviderOTG#generateTerrainNoise} 的 1:1 数学移植（Underneath 预设常量已烘入）。
 *
 * <p>公式：{@code 密度 = 体积噪声(vol1/vol2 按 interp 选择,×Volatility) + columnHeight + CHC[ySec]}；
 * {@code columnHeight=(heightFactor−ySec)×12×128/heightCap/volatilityFactor}（正值×4，顶 3 段衰减到 −10）；
 * volatilityFactor=−1.7（负→梯度反转=下空上实的倒置世界）。CHC 分段线性 = 引擎 4×8 角点三线性插值等价。
 * 输出为 OTG 原始单位，÷128 即 vanilla 密度域。纯 Java 零 MC 依赖、构造后不可变（线程安全）。
 */
public final class OTGTerrainMath {

    // WorldConfig.ini：FractureHorizontal 1.1 → ×2.1；FractureVertical −2.0 → ÷3（纵向压扁=横向撕裂感）
    private static final double XZ_SCALE = 684.41200000000003D * 2.1D;
    private static final double Y_SCALE = 684.41200000000003D / 3.0D;
    private static final double VOLATILITY1 = 2.0, VOLATILITY2 = 2.0;
    private static final double VOL_WEIGHT1 = 1.5, VOL_WEIGHT2 = 1.45;
    private static final double HEIGHT_SUM = (0.9 * 4.0 - 1.0) / 8.0;          // BiomeHeight 0.9
    private static final double VOLATILITY_FACTOR = -2.0 * 0.9 + 0.1;          // BiomeVolatility −2.0 → −1.7
    private static final double MAX_AVG_HEIGHT = 0.0, MAX_AVG_DEPTH = 0.0;
    private static final int Y_SECTIONS = 33;                                   // heightCap 256 → 33 段×8 格
    private static final double HEIGHT_CAP = 256.0;

    /** Underneath.bc CustomHeightControl 33 段（index 0=最底层）。 */
    private static final double[] CHC = {
            1000.0, 90.0, -30.0, -50.0, 40.0, 100.0, 300.0, 100.0, -20.0, -90.0,
            -120.0, -140.0, -180.0, -200.0, -220.0, -240.0, -200.0, -160.0, -120.0, -110.0,
            -100.0, -90.0, -80.0, -70.0, -60.0, -50.0, -40.0, -30.0, -20.0, -10.0,
            0.0, -70.0, -10.0
    };

    private final OTGLegacyNoise vol1;
    private final OTGLegacyNoise vol2;
    private final OTGLegacyNoise interp;
    private final OTGLegacyNoise noiseHeight;
    /** 2D noiseHeight 单列缓存（{cellX, cellZ, nh}）：只依赖列坐标，引擎按列扫描时同列 384 个 y
     *  全部命中——16 倍频 2D 噪声调用量降 ~99.7%。ThreadLocal=worldgen 多线程零锁；严格等价缓存。 */
    private final ThreadLocal<double[]> nhCache =
            ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, 0.0});

    /**
     * 采样模式开关（2026-09-14 用户拍板，保留双路径可回退）：
     * true＝OTG 原版语义——噪声只在 4×8 网格<b>整数角点</b>求值、方块密度=8 角三线性插值
     * （1.12 ChunkProviderOTG 正是如此；快数十倍且带原版的插值棱面感）；
     * false＝逐方块直采（P2-OTG 首版行为，更平滑细腻的"高清"地形）。改此常量重编译即切换。
     */
    public static final boolean CORNER_INTERPOLATION = true;

    /** 角点密度缓存：key=(cellX 24 位|cellZ 24 位|ySec 8 位) 无碰撞编码。ThreadLocal 零锁。 */
    private final ThreadLocal<HashMap<Long, Double>> cornerCache = ThreadLocal.withInitial(HashMap::new);
    private static final int CORNER_CACHE_CAP = 200_000;

    /** 生成器构造顺序沿用 OTG（vol1→vol2→interp→noiseHeight，同一 Random 流）。 */
    public OTGTerrainMath(long seed) {
        Random random = new Random(seed);
        this.vol1 = new OTGLegacyNoise(random, 16);
        this.vol2 = new OTGLegacyNoise(random, 16);
        this.interp = new OTGLegacyNoise(random, 8);
        this.noiseHeight = new OTGLegacyNoise(random, 16);
    }

    /** 便捷入口：方块坐标 → vanilla 密度域（>0 实心）。按 {@link #CORNER_INTERPOLATION} 分发。 */
    public double densityBlock(double blockX, double blockY, double blockZ) {
        if (!CORNER_INTERPOLATION) {
            return density(blockX / 4.0, blockY / 8.0, blockZ / 4.0) / 128.0;
        }
        return interpolated(blockX, blockY, blockZ) / 128.0;
    }

    /** OTG 原版采样：8 个 4×8 网格角点密度三线性插值。 */
    private double interpolated(double blockX, double blockY, double blockZ) {
        int cx = (int) Math.floor(blockX / 4.0);
        int cy = (int) Math.floor(blockY / 8.0);
        int cz = (int) Math.floor(blockZ / 4.0);
        double fx = blockX / 4.0 - cx;
        double fy = blockY / 8.0 - cy;
        double fz = blockZ / 4.0 - cz;
        double c000 = corner(cx, cy, cz);
        double c100 = corner(cx + 1, cy, cz);
        double c001 = corner(cx, cy, cz + 1);
        double c101 = corner(cx + 1, cy, cz + 1);
        double c010 = corner(cx, cy + 1, cz);
        double c110 = corner(cx + 1, cy + 1, cz);
        double c011 = corner(cx, cy + 1, cz + 1);
        double c111 = corner(cx + 1, cy + 1, cz + 1);
        double x00 = c000 + (c100 - c000) * fx;
        double x01 = c001 + (c101 - c001) * fx;
        double x10 = c010 + (c110 - c010) * fx;
        double x11 = c011 + (c111 - c011) * fx;
        double z0 = x00 + (x01 - x00) * fz;
        double z1 = x10 + (x11 - x10) * fz;
        return z0 + (z1 - z0) * fy;
    }

    private double corner(int cellX, int ySec, int cellZ) {
        HashMap<Long, Double> cache = cornerCache.get();
        if (cache.size() > CORNER_CACHE_CAP) {
            cache.clear();
        }
        long key = ((cellX & 0xFFFFFFL) << 32) | ((cellZ & 0xFFFFFFL) << 8) | ((ySec + 8) & 0xFFL);
        Double v = cache.get(key);
        if (v == null) {
            v = density(cellX, ySec, cellZ);
            cache.put(key, v);
        }
        return v;
    }

    /**
     * OTG 噪声网格坐标（cell：横 4 格/纵 8 格）下的密度，OTG 原始单位。
     * cySec 可为分数——引擎 interpolated 只在整角采样，CHC 用分段线性衔接（与角点三线性等价）。
     */
    public double density(double cellX, double cySec, double cellZ) {
        if (cySec < 0) {
            return 1000.0;                        // 世界地基以下恒实心
        }
        if (cySec > Y_SECTIONS) {
            return -30.0;                         // 高度帽以上恒空
        }

        double[] cache = nhCache.get();
        double nh;
        if (cache[0] == cellX && cache[1] == cellZ) {
            nh = cache[2];
        } else {
            nh = computeNoiseHeight(cellX, cellZ);
            cache[0] = cellX;
            cache[1] = cellZ;
            cache[2] = nh;
        }

        double heightFactor = Y_SECTIONS * (2.0 + HEIGHT_SUM + nh * 0.2) / 4.0;
        double columnHeight = (heightFactor - cySec) * 12.0 * 128.0 / HEIGHT_CAP / VOLATILITY_FACTOR;
        if (columnHeight > 0.0) {
            columnHeight *= 4.0;
        }

        // ---- 体积噪声：vol1/vol2 依 interp 噪声选择或线性混合 ----
        double v1 = vol1.noise3(cellX, cySec, cellZ, XZ_SCALE, Y_SCALE, XZ_SCALE) / 512.0 * VOLATILITY1;
        double v2 = vol2.noise3(cellX, cySec, cellZ, XZ_SCALE, Y_SCALE, XZ_SCALE) / 512.0 * VOLATILITY2;
        double vi = (interp.noise3(cellX, cySec, cellZ, XZ_SCALE / 80.0, Y_SCALE / 160.0, XZ_SCALE / 80.0) / 10.0 + 1.0) / 2.0;
        double out;
        if (vi < VOL_WEIGHT1) {
            out = v1;
        } else if (vi > VOL_WEIGHT2) {
            out = v2;
        } else {
            out = v1 + vi * (v2 - v1);
        }

        out += columnHeight;

        // 顶部 3 段强制衰减到 −10（先于 CHC，原版顺序）
        if (cySec > Y_SECTIONS - 4) {
            double t = (cySec - (Y_SECTIONS - 4)) / 3.0;
            if (t > 1.0) {
                t = 1.0;
            }
            out = out * (1.0 - t) + -10.0 * t;
        }

        out += chc(cySec);
        return out;
    }

    /** 2D noiseHeight 管线（原版归一化魔法数逐行保留）。 */
    private double computeNoiseHeight(double cellX, double cellZ) {
        double nh = noiseHeight.noise2(cellX, cellZ, 200.0, 200.0) / 8000.0;
        if (nh < 0.0) {
            nh = -nh * 0.3;
        }
        nh = nh * 3.0 - 2.0;
        if (nh < 0.0) {
            nh /= 2.0;
            if (nh < -1.0) {
                nh = -1.0;
            }
            nh -= MAX_AVG_DEPTH;
            nh /= 1.4;
            nh /= 2.0;
        } else {
            if (nh > 1.0) {
                nh = 1.0;
            }
            nh += MAX_AVG_HEIGHT;
            nh /= 8.0;
        }
        return nh;
    }

    private static double chc(double ySec) {
        int i = (int) Math.floor(ySec);
        if (i >= CHC.length - 1) {
            return CHC[CHC.length - 1];
        }
        if (i < 0) {
            return CHC[0];
        }
        double f = ySec - i;
        return CHC[i] + f * (CHC[i + 1] - CHC[i]);
    }
}
