package io.github.qlfy233.littlepalette.client;

import net.minecraft.util.Mth;

/**
 * 颜色数学：RGB ↔ CIELAB 转换 + 色距。
 *
 * <p>为什么不用 RGB 欧氏距离：RGB 距离和人眼感知不一致——绿色通道的微小变化
 * 人眼很敏感，蓝色通道的大变化反而无感（绿色权重 ≈ 红色 2 倍 ≈ 蓝色 3 倍）。
 * CIELAB 是为"感知均匀"设计的色彩空间：两色 ΔE 越小，人眼觉得越接近。
 * 相似度阈值用 ΔE 表达，容差滑块的含义对所有颜色都一致。
 */
public final class ColorMath {

    private ColorMath() {
    }

    /** sRGB (0-255) → CIELAB。标准 D65 白点、gamma 展开后转 XYZ 再转 Lab。 */
    public static float[] rgbToLab(int r8, int g8, int b8) {
        double r = srgbToLinear(r8 / 255.0);
        double g = srgbToLinear(g8 / 255.0);
        double b = srgbToLinear(b8 / 255.0);

        // sRGB → XYZ（D65）
        double x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375;
        double y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
        double z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041;

        // XYZ → Lab（D65 参考白）
        double xn = 0.95047, yn = 1.00000, zn = 1.08883;
        double fx = labF(x / xn);
        double fy = labF(y / yn);
        double fz = labF(z / zn);

        return new float[]{
                (float) (116 * fy - 16),
                (float) (500 * (fx - fy)),
                (float) (200 * (fy - fz))
        };
    }

    private static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }

    /**
     * CIE76 色距：Lab 空间欧氏距离。
     * 实践中足够用；CIEDE2000 更准但公式复杂一倍，对本 mod 的"找相似方块"增益很小。
     */
    public static float deltaE(float[] lab1, float[] lab2) {
        float dl = lab1[0] - lab2[0];
        float da = lab1[1] - lab2[1];
        float db = lab1[2] - lab2[2];
        return Mth.sqrt(dl * dl + da * da + db * db);
    }

    /**
     * 点 P 到线段 AB 的最短距离（Lab 空间）。
     *
     * <p>渐变搜索的几何本质：两色 A、B 定义一条线段，"渐变带"是线段周围半径 r 的
     * 圆柱体。P 在线段上的投影点 t∈[0,1] 表示"这个颜色位于渐变的哪个位置"，
     * 垂距表示"偏离渐变带多远"。垂距 ≤ 容差即命中，t 用于排序展示。
     */
    public static float distanceToSegment(float[] p, float[] a, float[] b) {
        float abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        float apx = p[0] - a[0], apy = p[1] - a[1], apz = p[2] - a[2];
        float abLenSq = abx * abx + aby * aby + abz * abz;
        float t;
        if (abLenSq < 1e-6f) {
            t = 0;   // 两色相同，退化为点
        } else {
            t = Mth.clamp((apx * abx + apy * aby + apz * abz) / abLenSq, 0.0f, 1.0f);
        }
        float cx = apx - t * abx, cy = apy - t * aby, cz = apz - t * abz;
        return Mth.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** 点到线段投影参数 t（0 = 靠近色 A，1 = 靠近色 B），用于结果排序。 */
    public static float segmentProjectionT(float[] p, float[] a, float[] b) {
        float abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        float apx = p[0] - a[0], apy = p[1] - a[1], apz = p[2] - a[2];
        float abLenSq = abx * abx + aby * aby + abz * abz;
        if (abLenSq < 1e-6f) {
            return 0;
        }
        return Mth.clamp((apx * abx + apy * aby + apz * abz) / abLenSq, 0.0f, 1.0f);
    }
}
