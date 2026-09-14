package com.mx.underneath.otg;

import java.util.Random;

/**
 * OTG/1.12 {@code NoiseGeneratorPerlin(Octaves)} 逐语义移植（点采样版）。
 *
 * <p>保留全部原始怪癖：坐标 16777216 取模包裹（Java 负余数语义照旧）、逐倍频
 * "频率×d3、振幅÷d3"的 legacy 阶梯（低频振幅最大）、2D 首角用 {@code func_4110_a}
 * 而非 grad 的经典 bug。构造后不可变，worldgen 多线程只读安全。
 * 纯 Java 零 MC 依赖——可离线 javac 跑剖面自证。
 */
public final class OTGLegacyNoise {

    private final Octave[] octaves;

    public OTGLegacyNoise(Random random, int count) {
        this.octaves = new Octave[count];
        for (int i = 0; i < count; i++) {
            this.octaves[i] = new Octave(random);
        }
    }

    /** 3D 点采样；坐标为 OTG 噪声网格 cell 单位（横 4 格/纵 8 格一 cell），可为分数。 */
    public double noise3(double cellX, double cellY, double cellZ,
                         double xScale, double yScale, double zScale) {
        double total = 0.0;
        double d3 = 1.0;
        for (Octave o : octaves) {
            double dx = wrap(cellX * d3 * xScale);
            double dz = wrap(cellZ * d3 * zScale);
            double dy = cellY * d3 * yScale;          // y 不做包裹（原版如此）
            total += o.sample3(dx, dy, dz) / d3;
            d3 /= 2.0;
        }
        return total;
    }

    /** 2D 点采样（noiseHeight 用）。 */
    public double noise2(double cellX, double cellZ, double xScale, double zScale) {
        double total = 0.0;
        double d3 = 1.0;
        for (Octave o : octaves) {
            double dx = wrap(cellX * d3 * xScale);
            double dz = wrap(cellZ * d3 * zScale);
            total += o.sample2(dx, dz) / d3;
            d3 /= 2.0;
        }
        return total;
    }

    /** 原版精度包裹：floor 拆分 → 整部 %16777216（Java 负余数）→ 拼回。 */
    private static double wrap(double v) {
        long i = floorLong(v);
        double frac = v - i;
        i %= 16777216L;
        return frac + i;
    }

    private static long floorLong(double v) {
        long l = (long) v;
        return v < l ? l - 1 : l;
    }

    /** 单倍频：置换表与偏移的构造顺序 = 1.12 原版（xCoord/yCoord/zCoord 先于洗牌）。 */
    static final class Octave {
        private final int[] p = new int[512];
        private final double xCoord;
        private final double yCoord;
        private final double zCoord;

        Octave(Random random) {
            this.xCoord = random.nextDouble() * 256.0;
            this.yCoord = random.nextDouble() * 256.0;
            this.zCoord = random.nextDouble() * 256.0;
            for (int i = 0; i < 256; i++) {
                p[i] = i;
            }
            for (int j = 0; j < 256; j++) {
                int k = random.nextInt(256 - j) + j;
                int l = p[j];
                p[j] = p[k];
                p[k] = l;
                p[j + 256] = p[j];
            }
        }

        double sample3(double xIn, double yIn, double zIn) {
            double x = xIn + xCoord;
            int xi = (int) x;
            if (x < xi) xi--;
            int ix = xi & 0xff;
            x -= xi;
            double fx = x * x * x * (x * (x * 6 - 15) + 10);

            double z = zIn + zCoord;
            int zi = (int) z;
            if (z < zi) zi--;
            int iz = zi & 0xff;
            z -= zi;
            double fz = z * z * z * (z * (z * 6 - 15) + 10);

            double y = yIn + yCoord;
            int yi = (int) y;
            if (y < yi) yi--;
            int iy = yi & 0xff;
            y -= yi;
            double fy = y * y * y * (y * (y * 6 - 15) + 10);

            int a = p[ix] + iy;
            int aa = p[a] + iz;
            int ab = p[a + 1] + iz;
            int b = p[ix + 1] + iy;
            int ba = p[b] + iz;
            int bb = p[b + 1] + iz;

            double l1 = lerp(fx, grad(p[aa], x, y, z), grad(p[ba], x - 1, y, z));
            double l2 = lerp(fx, grad(p[ab], x, y - 1, z), grad(p[bb], x - 1, y - 1, z));
            double l3 = lerp(fx, grad(p[aa + 1], x, y, z - 1), grad(p[ba + 1], x - 1, y, z - 1));
            double l4 = lerp(fx, grad(p[ab + 1], x, y - 1, z - 1), grad(p[bb + 1], x - 1, y - 1, z - 1));
            return lerp(fz, lerp(fy, l1, l2), lerp(fy, l3, l4));
        }

        double sample2(double xIn, double zIn) {
            double x = xIn + xCoord;
            int xi = (int) x;
            if (x < xi) xi--;
            int ix = xi & 0xff;
            x -= xi;
            double fx = x * x * x * (x * (x * 6 - 15) + 10);

            double z = zIn + zCoord;
            int zi = (int) z;
            if (z < zi) zi--;
            int iz = zi & 0xff;
            z -= zi;
            double fz = z * z * z * (z * (z * 6 - 15) + 10);

            int a = p[ix];
            int aa = p[a] + iz;
            int b = p[ix + 1];
            int ba = p[b] + iz;
            // 原版怪癖：首角用 func_4110_a，其余用 grad
            double l1 = lerp(fx, gradQuirk(p[aa], x, z), grad(p[ba], x - 1, 0.0, z));
            double l2 = lerp(fx, grad(p[aa + 1], x, 0.0, z - 1), grad(p[ba + 1], x - 1, 0.0, z - 1));
            return lerp(fz, l1, l2);
        }

        private static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        private static double gradQuirk(int hash, double x, double z) {
            int h = hash & 0xf;
            double u = (double) (1 - ((h & 8) >> 3)) * x;
            double v = h >= 4 ? (h != 12 && h != 14 ? z : x) : 0.0;
            return ((h & 1) != 0 ? -u : u) + ((h & 2) != 0 ? -v : v);
        }

        private static double grad(int hash, double x, double y, double z) {
            int h = hash & 0xf;
            double u = h >= 8 ? y : x;
            double v = h >= 4 ? (h != 12 && h != 14 ? z : x) : y;
            return ((h & 1) != 0 ? -u : u) + ((h & 2) != 0 ? -v : v);
        }
    }
}
