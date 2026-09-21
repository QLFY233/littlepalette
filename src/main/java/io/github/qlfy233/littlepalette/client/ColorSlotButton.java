package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/**
 * 颜色槽：自己画颜色块（不依赖默认按钮纹理，否则会把颜色盖住），点击删除该颜色。
 * colorSupplier 返回 0xRRGGBB；&lt;0 表示空槽。
 */
class ColorSlotButton extends AbstractWidget {

    private final IntSupplier colorSupplier;
    private final Runnable onClick;

    ColorSlotButton(int x, int y, int size, IntSupplier colorSupplier, Runnable onClick) {
        super(x, y, size, size, Component.empty());
        this.colorSupplier = colorSupplier;
        this.onClick = onClick;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int color = this.colorSupplier.getAsInt();
        if (color < 0) {
            // 空槽：暗色占位
            g.fill(this.getX() + 1, this.getY() + 1,
                    this.getX() + this.width - 1, this.getY() + this.height - 1, 0xFF202020);
            g.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFF404040);
        } else {
            g.fill(this.getX() + 1, this.getY() + 1,
                    this.getX() + this.width - 1, this.getY() + this.height - 1, 0xFF000000 | color);
            g.renderOutline(this.getX(), this.getY(), this.width, this.height,
                    this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF808080);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onClick.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
