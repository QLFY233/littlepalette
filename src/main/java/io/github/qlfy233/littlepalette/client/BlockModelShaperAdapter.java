package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 从 BakedModel 提取方块代表色的适配层。
 *
 * <p>链路（全部签名已用 javap 核实）：
 * BlockModelShaper.getBlockModel(state)
 *   → BakedModel.getQuads(state, side, random)
 *   → BakedQuad.getSprite() / getTintIndex()
 *   → TextureAtlasSprite.contents().getOriginalImage()
 *   → NativeImage.getPixelRGBA(x, y)   （注意字节序是 ABGR！）
 *
 * <p>为什么用 getQuads 而不是只取 particle icon：particle 对草方块、原木等
 * 常常不是"玩家看到的颜色"（草方块 particle 是泥土色）。按 quads 面积加权
 * 才能拿到草方块表面的绿色，且草的 tint 再经 BlockColors 染色。
 */
class BlockModelShaperAdapter {

    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * 计算方块状态的加权平均色，返回 0x00RRGGBB；拿不到几何/纹理时返回 -1。
     */
    int averageColor(BlockState state, RandomSource random, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockModelShaper shaper = mc.getModelManager().getBlockModelShaper();
        BakedModel model = shaper.getBlockModel(state);
        if (model == null) {
            return -1;
        }

        long r = 0, g = 0, b = 0, wsum = 0;

        for (Direction side : DIRECTIONS) {
            List<BakedQuad> quads = model.getQuads(state, side, random);
            if (quads != null) {
                for (BakedQuad quad : quads) {
                    int[] rgbw = quadColor(quad, state, mc);
                    if (rgbw == null) {
                        continue;
                    }
                    long area = quadArea(quad);
                    if (area <= 0) {
                        continue;
                    }
                    r += (long) rgbw[0] * area;
                    g += (long) rgbw[1] * area;
                    b += (long) rgbw[2] * area;
                    wsum += area;
                }
            }
        }
        // 无侧面 quads 的模型（有些自定义方块全部 quads 挂在 null side 上）
        List<BakedQuad> unculled = model.getQuads(state, null, random);
        if (unculled != null) {
            for (BakedQuad quad : unculled) {
                int[] rgbw = quadColor(quad, state, mc);
                if (rgbw == null) {
                    continue;
                }
                long area = quadArea(quad);
                if (area <= 0) {
                    continue;
                }
                r += (long) rgbw[0] * area;
                g += (long) rgbw[1] * area;
                b += (long) rgbw[2] * area;
                wsum += area;
            }
        }

        if (wsum == 0) {
            return -1;
        }
        int rr = (int) Mth.clamp(r / (double) wsum, 0, 255);
        int gg = (int) Mth.clamp(g / (double) wsum, 0, 255);
        int bb = (int) Mth.clamp(b / (double) wsum, 0, 255);
        return (rr << 16) | (gg << 8) | bb;
    }

    /**
     * 单个 quad 的颜色（已应用 tint）+ 面积占比权重。
     * 返回 [r, g, b, weightMilli]（权重用千分比整数，避免浮点累计误差），
     * 纹理读取失败返回 null。
     */
    private int[] quadColor(BakedQuad quad, BlockState state, Minecraft mc) {
        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite == null || sprite.contents() == null) {
            return null;
        }
        var original = sprite.contents().getOriginalImage();
        if (original == null) {
            return null;
        }
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }

        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = original.getPixelRGBA(x, y);   // ★ ABGR，不是 ARGB
                int a = (abgr >>> 24) & 0xFF;
                if (a < 32) {
                    continue;   // 透明像素不计入
                }
                // ABGR → RGB
                int pr = abgr & 0xFF;
                int pg = (abgr >>> 8) & 0xFF;
                int pb = (abgr >>> 16) & 0xFF;
                r += pr;
                g += pg;
                b += pb;
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        int cr = (int) (r / n), cg = (int) (g / n), cb = (int) (b / n);

        // biome tint：草/树叶/水等按 tintIndex 经 BlockColors 染色
        int tint = quad.getTintIndex();
        if (tint != -1) {
            try {
                int tinted = mc.getBlockColors().getColor(state, null, null, tint);
                // BlockColors 返回 -1 表示无颜色（未注册处理器），保持原色
                if (tinted != -1) {
                    int tr = (tinted >> 16) & 0xFF;
                    int tg = (tinted >> 8) & 0xFF;
                    int tb = tinted & 0xFF;
                    cr = cr * tr / 255;
                    cg = cg * tg / 255;
                    cb = cb * tb / 255;
                }
            } catch (Exception ignored) {
                // level 为 null 时个别 ColorResolver 可能要求非空，跳过染色即可
            }
        }

        // 面积权重：quad 在模型总纹理面积里越小，对整体颜色影响越小。
        // 这里把面积换算成千分比，主面通常占大头。
        long area = quadArea(quad);
        int weightMilli = (int) Mth.clamp(area / 1000, 1, 1000);
        return new int[]{cr, cg, cb, weightMilli};
    }

    /**
     * quad 的视觉面积（近似）：用纹理像素数区分 16x16 主面和 1x1 装饰面。
     * BakedQuad 不公开顶点坐标，纹理大小是现成可用的近似——主面通常用完整纹理。
     */
    private long quadArea(BakedQuad quad) {
        var sprite = quad.getSprite();
        if (sprite == null) {
            return 1;
        }
        return (long) sprite.contents().width() * sprite.contents().height();
    }
}
