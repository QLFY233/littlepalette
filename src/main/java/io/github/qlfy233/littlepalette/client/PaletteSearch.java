package io.github.qlfy233.littlepalette.client;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索器：把"调色板颜色 + 筛选条件"翻译成方块列表。
 *
 * <p>两种模式（几何解释都在 CIELAB 空间）：
 * <ul>
 *   <li>1 色 —— 相似色：以调色板颜色为球心、容差为半径的球</li>
 *   <li>2 色 —— 渐变：两色连成线段、容差为半径的圆柱；结果按投影位置 t 排序，
 *       展示顺序天然就是"从色 A 渐变到色 B"</li>
 * </ul>
 */
public final class PaletteSearch {

    /** 结果条目：方块状态 + 渐变位置（单色模式恒为 0.5，用于统一排序逻辑）。 */
    public record Result(BlockState state, float gradientT, float distance) {
    }

    public static final int FULL_BLOCK_ONLY = 1;
    public static final int FULL_BLOCK_EXCLUDED = 2;
    public static final int FULL_BLOCK_ANY = 0;

    private PaletteSearch() {
    }

    /**
     * @param paletteColorA   第一个颜色（Lab）
     * @param paletteColorB   第二个颜色（Lab），null 表示单色模式
     * @param tolerance       容差（ΔE 单位，滑块 0~100）
     * @param category        方块种类筛选（block id path，null = 全部）
     * @param fullBlockMode   FULL_BLOCK_ANY / ONLY / EXCLUDED
     */
    public static List<Result> search(float[] paletteColorA, float[] paletteColorB,
                                      float tolerance, String category, int fullBlockMode) {
        ColorIndex index = ColorIndex.INSTANCE;
        List<Result> results = new ArrayList<>();

        boolean gradientMode = paletteColorB != null;

        for (ColorIndex.Entry entry : index.all) {
            Block block = entry.state().getBlock();
            if (category != null && !category.equals(indexId(block))) {
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

            float distance;
            float t = 0.5f;
            if (gradientMode) {
                distance = ColorMath.distanceToSegment(entry.lab(), paletteColorA, paletteColorB);
                if (distance > tolerance) {
                    continue;
                }
                t = ColorMath.segmentProjectionT(entry.lab(), paletteColorA, paletteColorB);
            } else {
                distance = ColorMath.deltaE(entry.lab(), paletteColorA);
                if (distance > tolerance) {
                    continue;
                }
            }
            results.add(new Result(entry.state(), t, distance));
        }

        // 渐变模式按 t 排序 = 按渐变顺序展示；单色模式按色距排序 = 越像越靠前
        results.sort((r1, r2) -> gradientMode
                ? Float.compare(r1.gradientT(), r2.gradientT())
                : Float.compare(r1.distance(), r2.distance()));
        return results;
    }

    private static String indexId(Block block) {
        // ColorIndex 用 block 的注册名 path 做分类键；这里反向取
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    /** 滑块 0~1 → ΔE 容差。ΔE≈2.3 是人眼恰可察觉，>10 是明显不同。 */
    public static float toleranceFromSlider(double slider) {
        return (float) Mth.lerp(slider, 0.0, 60.0);
    }
}
