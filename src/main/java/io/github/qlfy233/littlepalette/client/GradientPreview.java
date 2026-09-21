package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 渐变预览条：显示调色板全部颜色沿折线的渐变（1 段/色 = 线性插值），
 * 与搜索几何（Lab 折线）直觉一致。
 */
class GradientPreview extends AbstractWidget {

    private final IntArraySupplier colors;

    @FunctionalInterface
    interface IntArraySupplier {
        /** 返回全部颜色 RGB；少于 2 个返回 null。 */
        int[] get();
    }

    GradientPreview(int x, int y, int w, int h, IntArraySupplier colors) {
        super(x, y, w, h, Component.empty());
        this.colors = colors;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int[] cs = this.colors.get();
        if (cs == null || cs.length < 2) {
            return;
        }
        // 折线渐变：把条宽均分为 n-1 段，段内线性插值
        int segs = cs.length - 1;
        for (int dx = 0; dx < this.width; dx++) {
            float t = this.width <= 1 ? 0 : dx / (float) (this.width - 1) * segs;
            int seg = Math.min((int) t, segs - 1);
            float local = t - seg;
            g.fill(this.getX() + dx, this.getY(), this.getX() + dx + 1, this.getY() + this.height,
                    lerpColor(cs[seg], cs[seg + 1], local));
        }
        g.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFF808080);
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r = (int) ((c1 >> 16 & 0xFF) * (1 - t) + (c2 >> 16 & 0xFF) * t);
        int gr = (int) ((c1 >> 8 & 0xFF) * (1 - t) + (c2 >> 8 & 0xFF) * t);
        int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
