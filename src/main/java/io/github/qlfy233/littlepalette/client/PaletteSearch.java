package io.github.qlfy233.littlepalette.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索器：把"调色板颜色 + 筛选条件"翻译成方块列表。
 *
 * <p>几何解释（CIELAB 空间）：
 * <ul>
 *   <li>1 色 —— 相似色：以调色板颜色为球心、容差为半径的球</li>
 *   <li>N 色 —— 渐变：所有颜色依次连成<b>折线</b>（polyline），容差为半径的"胶囊体"。
 *       结果按<b>沿折线的归一化位置 t</b> 排序，展示顺序就是从色 1 渐变到色 N 的顺序。
 *       （2 色是折线的特例=线段；3+ 色自然扩展为多段渐变）</li>
 * </ul>
 */
public final class PaletteSearch {

    public record Result(BlockState state, float gradientT, float distance) {
    }

    public static final int FULL_BLOCK_ONLY = 1;
    public static final int FULL_BLOCK_EXCLUDED = 2;
    public static final int FULL_BLOCK_ANY = 0;

    private PaletteSearch() {
    }

    /**
     * @param paletteLabs   调色板所有颜色的 Lab（1 个 = 相似色模式，N 个 = 渐变模式）
     * @param tolerance     容差（ΔE）
     * @param category      方块种类筛选（block id path，null = 全部）
     * @param fullBlockMode FULL_BLOCK_ANY / ONLY / EXCLUDED
     */
    public static List<Result> search(List<float[]> paletteLabs, float tolerance,
                                      String category, int fullBlockMode) {
        ColorIndex index = ColorIndex.INSTANCE;
        List<Result> results = new ArrayList<>();
        boolean gradientMode = paletteLabs.size() >= 2;

        // 预计算折线各段长度与累计长度（把 t 归一化到 0~1）
        int segCount = paletteLabs.size() - 1;
        float[] segLen = new float[Math.max(segCount, 1)];
        float totalLen = 0;
        for (int i = 0; i < segCount; i++) {
            segLen[i] = (float) Mth.length(
                    paletteLabs.get(i)[0] - paletteLabs.get(i + 1)[0],
                    paletteLabs.get(i)[1] - paletteLabs.get(i + 1)[1],
                    paletteLabs.get(i)[2] - paletteLabs.get(i + 1)[2]);
            totalLen += segLen[i];
        }

        for (ColorIndex.Entry entry : index.all) {
            Block block = entry.state().getBlock();
            if (category != null && !category.equals(categoryOf(block))) {
                continue;
            }
            if (fullBlockMode != FULL_BLOCK_ANY) {
                boolean full = entry.state().isCollisionShapeFullBlock(
                        net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
                if (fullBlockMode == FULL_BLOCK_ONLY && !full) {
                    continue;
                }
                if (fullBlockMode == FULL_BLOCK_EXCLUDED && full) {
                    continue;
                }
            }

            float t;
            float distance;
            if (gradientMode) {
                // 折线最近距离 + 所在线段内的投影位置
                float bestDist = Float.MAX_VALUE;
                float bestT = 0;
                float accLen = 0;
                for (int i = 0; i < segCount; i++) {
                    float[] a = paletteLabs.get(i), b = paletteLabs.get(i + 1);
                    float d = distanceToSegment(entry.lab(), a, b);
                    if (d < bestDist) {
                        bestDist = d;
                        float local = segmentProjectionT(entry.lab(), a, b);
                        bestT = segLen[i] < 1e-6f ? 0 : (accLen + local * segLen[i]);
                    }
                    accLen += segLen[i];
                }
                if (bestDist > tolerance) {
                    continue;
                }
                distance = bestDist;
                t = totalLen < 1e-6f ? 0.5f : bestT / totalLen;
            } else {
                distance = ColorMath.deltaE(entry.lab(), paletteLabs.get(0));
                if (distance > tolerance) {
                    continue;
                }
                t = 0.5f;
            }
            results.add(new Result(entry.state(), t, distance));
        }

        results.sort((r1, r2) -> gradientMode
                ? Float.compare(r1.gradientT(), r2.gradientT())
                : Float.compare(r1.distance(), r2.distance()));
        return results;
    }

    private static String categoryOf(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "" : id.getPath();
    }

    /** 点 P 到线段 AB 的最短距离（Lab 空间）。 */
    static float distanceToSegment(float[] p, float[] a, float[] b) {
        float abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        float apx = p[0] - a[0], apy = p[1] - a[1], apz = p[2] - a[2];
        float abLenSq = abx * abx + aby * aby + abz * abz;
        float t;
        if (abLenSq < 1e-6f) {
            t = 0;
        } else {
            t = Mth.clamp((apx * abx + apy * aby + apz * abz) / abLenSq, 0.0f, 1.0f);
        }
        float cx = apx - t * abx, cy = apy - t * aby, cz = apz - t * abz;
        return Mth.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** 点到线段投影参数 t（0 = 靠近色 A，1 = 靠近色 B）。 */
    static float segmentProjectionT(float[] p, float[] a, float[] b) {
        float abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        float apx = p[0] - a[0], apy = p[1] - a[1], apz = p[2] - a[2];
        float abLenSq = abx * abx + aby * aby + abz * abz;
        if (abLenSq < 1e-6f) {
            return 0;
        }
        return Mth.clamp((apx * abx + apy * aby + apz * abz) / abLenSq, 0.0f, 1.0f);
    }

    /** 滑块 0~1 → ΔE 容差。ΔE≈2.3 是人眼恰可察觉，>10 是明显不同。 */
    public static float toleranceFromSlider(double slider) {
        return (float) Mth.lerp((float) slider, 0.0f, 60.0f);
    }
}
