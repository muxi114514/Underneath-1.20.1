package com.mx.underneath.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mx.underneath.Underneath;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * OTG BiomeMode:FromImage 的 1.20 移植（注册名 {@code underneath:from_image}）。
 *
 * <p>语义对齐 OTG {@code LayerFromImage}(ContinueNormal)：图层位于 Voronoi 缩放之下 →
 * <b>1 像素 = 4×4 格 = 恰好 1.20 的 quart 坐标</b>，零换算；像素 = quart − offset；
 * 图外/未知色 → fallback（承担原版 Normal 模式的自然分布，此处为 multi_noise）。
 * 图惰性加载自 mod jar {@code data/underneath/worldgen_image/map.png}，存为字节索引（3000²≈9MB）。
 */
public final class FromImageBiomeSource extends BiomeSource {

    public static final Codec<FromImageBiomeSource> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(s -> s.entries),
            BiomeSource.CODEC.fieldOf("fallback").forGetter(s -> s.fallback),
            Codec.INT.fieldOf("x_offset").forGetter(s -> s.xOffset),
            Codec.INT.fieldOf("z_offset").forGetter(s -> s.zOffset)
    ).apply(inst, FromImageBiomeSource::new));

    /** 颜色（#RRGGBB）→ 群系。 */
    public record Entry(String color, Holder<Biome> biome) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("color").forGetter(Entry::color),
                Biome.CODEC.fieldOf("biome").forGetter(Entry::biome)
        ).apply(inst, Entry::new));

        int rgb() {
            return Integer.parseInt(color.replace("#", ""), 16) & 0xFFFFFF;
        }
    }

    private static final String IMAGE_PATH = "/data/underneath/worldgen_image/map.png";

    private final List<Entry> entries;
    private final BiomeSource fallback;
    private final int xOffset;
    private final int zOffset;
    private volatile Grid grid;     // 惰性加载；volatile 发布，多线程只读

    public FromImageBiomeSource(List<Entry> entries, BiomeSource fallback, int xOffset, int zOffset) {
        this.entries = entries;
        this.fallback = fallback;
        this.xOffset = xOffset;
        this.zOffset = zOffset;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(entries.stream().map(Entry::biome), fallback.possibleBiomes().stream());
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        Grid g = grid();
        if (g != null) {
            int px = quartX - xOffset;
            int pz = quartZ - zOffset;
            if (px >= 0 && px < g.width && pz >= 0 && pz < g.height) {
                int idx = g.data[pz * g.width + px];
                if (idx >= 0) {
                    return entries.get(idx).biome();
                }
            }
        }
        return fallback.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }

    private Grid grid() {
        Grid g = this.grid;
        if (g == null) {
            synchronized (this) {
                g = this.grid;
                if (g == null) {
                    this.grid = g = Grid.load(entries);
                }
            }
        }
        return g;
    }

    private static final class Grid {
        static final Grid EMPTY = new Grid(0, 0, new byte[0]);
        final int width;
        final int height;
        final byte[] data;      // 每像素 = entries 下标；-1 = 未知色（→fallback）

        private Grid(int width, int height, byte[] data) {
            this.width = width;
            this.height = height;
            this.data = data;
        }

        static Grid load(List<Entry> entries) {
            try (InputStream in = FromImageBiomeSource.class.getResourceAsStream(IMAGE_PATH)) {
                if (in == null) {
                    Underneath.LOGGER.error("map.png 不存在于 {}，FromImage 全域回退 fallback", IMAGE_PATH);
                    return EMPTY;
                }
                BufferedImage img = ImageIO.read(in);
                int w = img.getWidth();
                int h = img.getHeight();
                Map<Integer, Byte> colorIdx = new HashMap<>();
                for (int i = 0; i < entries.size() && i < 127; i++) {
                    colorIdx.putIfAbsent(entries.get(i).rgb(), (byte) i);
                }
                int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
                byte[] data = new byte[w * h];
                Map<Integer, Integer> unknown = new HashMap<>();
                for (int i = 0; i < rgb.length; i++) {
                    Byte idx = colorIdx.get(rgb[i] & 0xFFFFFF);
                    if (idx != null) {
                        data[i] = idx;
                    } else {
                        data[i] = -1;
                        if (unknown.size() < 16) {
                            unknown.merge(rgb[i] & 0xFFFFFF, 1, Integer::sum);
                        }
                    }
                }
                if (!unknown.isEmpty()) {
                    Underneath.LOGGER.info("map.png 未映射颜色(→fallback)样本: {}",
                            unknown.keySet().stream().map(c -> String.format("#%06x", c)).toList());
                }
                Underneath.LOGGER.info("FromImage 已加载 map.png {}x{}（1 像素=4x4 格，覆盖 ±{} 格）", w, h, w * 2);
                return new Grid(w, h, data);
            } catch (IOException e) {
                Underneath.LOGGER.error("map.png 读取失败，FromImage 全域回退 fallback", e);
                return EMPTY;
            }
        }
    }
}
