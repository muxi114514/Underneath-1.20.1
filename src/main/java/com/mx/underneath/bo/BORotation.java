package com.mx.underneath.bo;

import net.minecraft.world.level.block.Rotation;

import java.util.Locale;

/**
 * OTG 旋转语义（逐字镜像 1.12 OTG 源码，三套坐标变换各司其职，勿混用）：
 * <ul>
 *   <li>枚举顺序 <b>NORTH(0)→WEST(1)→SOUTH(2)→EAST(3)</b>，合成=id 相加 mod 4（{@code Rotation.next}）；</li>
 *   <li><b>分支偏移</b>（{@code getRotatedCoord}）：每步 (x,z)→(z,−x)；</li>
 *   <li><b>BO3 方块</b>（{@code BO3BlockFunction.rotate}）：每步 (x,z)→(z,−x)；</li>
 *   <li><b>BO4 方块</b>（chunk 对齐 0..15，{@code getRotatedBO3CoordsJustified}）：每步 (x,z)→(15−z,x)。</li>
 * </ul>
 * 方块态旋转方向与所属坐标变换保持同向（BO3/分支=逆时针，BO4=顺时针），保证几何自洽；
 * 若进游戏发现旋转件朝向反了，只需翻转 {@link #stateRotation} 一处。
 */
public final class BORotation {

    /** 解析 OTG 旋转名/数字 → 步数 id（未知按 OTG 默认 WEST=1）。 */
    public static int parse(String s) {
        String v = s.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "NORTH", "0" -> 0;
            case "WEST", "1" -> 1;
            case "SOUTH", "2" -> 2;
            case "EAST", "3" -> 3;
            default -> 1;
        };
    }

    /** 分支偏移旋转：每步 (x,z)→(z,−x)。返回 {x,z}。 */
    public static int[] branchOffset(int x, int z, int steps) {
        for (int i = 0; i < (steps & 3); i++) {
            int nx = z;
            z = -x;
            x = nx;
        }
        return new int[]{x, z};
    }

    /** BO3 方块坐标旋转：每步 (x,z)→(z,−x)。返回 {x,z}。 */
    public static int[] bo3Block(int x, int z, int steps) {
        return branchOffset(x, z, steps);
    }

    /** BO4 方块坐标旋转（chunk 对齐 0..15）：每步 (x,z)→(15−z,x)。返回 {x,z}。 */
    public static int[] bo4Block(int x, int z, int steps) {
        for (int i = 0; i < (steps & 3); i++) {
            int nx = 15 - z;
            z = x;
            x = nx;
        }
        return new int[]{x, z};
    }

    /** 方块态旋转：与所属坐标变换同向。BO3/分支（逆时针）与 BO4（顺时针）互为反向。 */
    public static Rotation stateRotation(int steps, boolean bo4) {
        int s = steps & 3;
        if (s == 0) {
            return Rotation.NONE;
        }
        if (s == 2) {
            return Rotation.CLOCKWISE_180;
        }
        boolean cw = bo4 ? (s == 1) : (s == 3);
        return cw ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
    }

    private BORotation() {
    }
}
