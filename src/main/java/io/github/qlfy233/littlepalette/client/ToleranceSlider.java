package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * 容差滑块。value 0~1 映射到 ΔE 0~60（见 {@link PaletteSearch#toleranceFromSlider}）。
 *
 * <p>注意配方（GUI-RECIPES §4）：构造器里传的 message 只是占位，
 * 用 Component.empty() + 立即 updateMessage()，避免闪一下字面量。
 */
class ToleranceSlider extends AbstractSliderButton {

    private final ToleranceConsumer onValueChanged;

    interface ToleranceConsumer {
        void accept(double sliderValue);
    }

    ToleranceSlider(int x, int y, int w, int h, double value, ToleranceConsumer onValueChanged) {
        super(x, y, w, h, Component.empty(), value);
        this.onValueChanged = onValueChanged;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        float deltaE = PaletteSearch.toleranceFromSlider(this.value);
        this.setMessage(Component.translatable("gui.littlepalette.tolerance",
                String.format("%.0f", deltaE)));
    }

    @Override
    protected void applyValue() {
        this.onValueChanged.accept(this.value);
    }
}
