package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 渐变预览条：两色模式下显示调色板前两色之间的渐变，
 * 让用户直观看到"搜索的是什么渐变带"。
 */
class GradientPreview extends AbstractWidget {

    private final IntArraySupplier colors;

    @FunctionalInterface
    interface IntArraySupplier {
        int[] get();
    }

    GradientPreview(int x, int y, int w, int h, IntArraySupplier colors) {
        super(x, y, w, h, Component.empty());
        this.colors = colors;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int[] pair = this.colors.get();
        if (pair == null || pair.length < 2) {
            return;
        }
        int c1 = pair[0], c2 = pair[1];
        for (int dx = 0; dx < this.width; dx++) {
            float t = this.width <= 1 ? 0 : dx / (float) (this.width - 1);
            g.fill(this.getX() + dx, this.getY(), this.getX() + dx + 1, this.getY() + this.height,
                    lerpColor(c1, c2, t));
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
