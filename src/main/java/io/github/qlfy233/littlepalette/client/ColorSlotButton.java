package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * 颜色槽：显示颜色块；若条目有来源方块则叠加画方块图标。
 * colorSupplier 返回 0xRRGGBB；&lt;0 表示空槽。
 * blockSupplier 接收任意 int 返回该槽的来源方块（无则 null）。
 */
class ColorSlotButton extends AbstractWidget {

    private final IntSupplier colorSupplier;
    private final IntFunction<Block> blockSupplier;
    private final Runnable onClick;

    ColorSlotButton(int x, int y, int size, IntSupplier colorSupplier,
                    IntFunction<Block> blockSupplier, Runnable onClick) {
        super(x, y, size, size, Component.empty());
        this.colorSupplier = colorSupplier;
        this.blockSupplier = blockSupplier;
        this.onClick = onClick;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int color = this.colorSupplier.getAsInt();
        if (color < 0) {
            g.fill(this.getX() + 1, this.getY() + 1,
                    this.getX() + this.width - 1, this.getY() + this.height - 1, 0xFF202020);
            g.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFF404040);
        } else {
            g.fill(this.getX() + 1, this.getY() + 1,
                    this.getX() + this.width - 1, this.getY() + this.height - 1, 0xFF000000 | color);
            g.renderOutline(this.getX(), this.getY(), this.width, this.height,
                    this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF808080);
            // 有来源方块：叠画物品图标（16x16 居中，覆盖纯色块大半）
            Block b = this.blockSupplier.apply(0);
            if (b != null) {
                g.renderItem(b.asItem().getDefaultInstance(), this.getX(), this.getY());
            }
        }
    }

    /** 悬停时给 Screen 用的展示物品（tooltip 来源），无来源方块返回 EMPTY。 */
    public ItemStack displayStack() {
        Block b = this.blockSupplier.apply(0);
        return b == null ? ItemStack.EMPTY : b.asItem().getDefaultInstance();
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
