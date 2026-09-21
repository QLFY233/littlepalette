package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * HSV 取色器：SV 色彩平面 + 色相条 + 实时预览（配方来自 docs/GUI-RECIPES.md §2.2）。
 *
 * <p>已核实的 API 事实：
 * <ul>
 *   <li>Mth.hsvToRgb 返回全透明色（alpha 写死 0），必须用 Mth.hsvToArgb(h, s, v, 255)</li>
 *   <li>fillGradient 只支持竖向，SV 平面横轴靠逐列拼接</li>
 *   <li>HSV 各分量范围 0.0~1.0（不是 0~360）</li>
 * </ul>
 */
public class ColorPickerScreen extends Screen {

    private static final int PLANE_SIZE = 128;
    private static final int BAR_HEIGHT = 12;
    private static final int BORDER = 0xFF808080;
    private static final int LABEL_COLOR = 0xFFE0E0E0;

    public interface ColorConsumer {
        void accept(int rgb);
    }

    private final Screen parent;
    private final ColorConsumer consumer;

    private float hue = 0.55f, saturation = 0.85f, value = 1.0f;

    private enum DragTarget {PLANE, HUE}

    private DragTarget dragging = null;

    private int planeX, planeY, hueX, hueY;

    public ColorPickerScreen(Screen parent, ColorConsumer consumer) {
        super(Component.translatable("gui.littlepalette.color_picker"));
        this.parent = parent;
        this.consumer = consumer;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.planeX = cx - PLANE_SIZE / 2 - (PLANE_SIZE / 2 + 4);
        this.planeY = 50;
        this.hueX = cx + 4;
        this.hueY = 50;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.littlepalette.confirm"),
                        b -> confirm())
                .bounds(cx - 60, this.height - 50, 56, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.littlepalette.cancel"),
                        b -> cancel())
                .bounds(cx + 4, this.height - 50, 56, 20).build());
    }

    private int hueBarWidth() {
        return PLANE_SIZE / 2 - 4;
    }

    private int opaqueColor() {
        return Mth.hsvToArgb(this.hue, this.saturation, this.value, 255);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(this.font, this.title, this.width / 2, 28, LABEL_COLOR);

        renderSvPlane(g);
        renderHueBar(g);
        renderPreview(g);

        for (var r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderSvPlane(GuiGraphics g) {
        for (int col = 0; col < PLANE_SIZE; col++) {
            float s = PLANE_SIZE <= 1 ? 0.0f : col / (float) (PLANE_SIZE - 1);
            g.fillGradient(
                    this.planeX + col, this.planeY,
                    this.planeX + col + 1, this.planeY + PLANE_SIZE,
                    Mth.hsvToArgb(this.hue, s, 1.0f, 255),
                    Mth.hsvToArgb(this.hue, s, 0.0f, 255));
        }
        g.renderOutline(this.planeX, this.planeY, PLANE_SIZE, PLANE_SIZE, BORDER);

        int mx = this.planeX + Math.round(this.saturation * (PLANE_SIZE - 1));
        int my = this.planeY + Math.round((1.0f - this.value) * (PLANE_SIZE - 1));
        g.renderOutline(mx - 3, my - 3, 7, 7, 0xFF000000);
        g.renderOutline(mx - 2, my - 2, 5, 5, 0xFFFFFFFF);
    }

    private void renderHueBar(GuiGraphics g) {
        int w = this.hueBarWidth();
        for (int col = 0; col < w; col++) {
            float h = w <= 1 ? 0.0f : col / (float) (w - 1);
            g.fill(this.hueX + col, this.hueY, this.hueX + col + 1, this.hueY + BAR_HEIGHT,
                    Mth.hsvToArgb(h, 1.0f, 1.0f, 255));
        }
        g.renderOutline(this.hueX, this.hueY, w, BAR_HEIGHT, BORDER);

        int mx = this.hueX + Math.round(this.hue * (w - 1));
        g.fill(mx - 1, this.hueY - 2, mx + 1, this.hueY + BAR_HEIGHT + 2, 0xFFFFFFFF);
    }

    private void renderPreview(GuiGraphics g) {
        int py = this.hueY + BAR_HEIGHT + 8;
        g.fill(this.hueX, py, this.hueX + this.hueBarWidth(), py + 16, this.opaqueColor());
        g.renderOutline(this.hueX, py, this.hueBarWidth(), 16, BORDER);
        g.drawString(this.font,
                Component.translatable("gui.littlepalette.color_preview",
                        String.format("#%06X", this.opaqueColor() & 0xFFFFFF)),
                this.hueX, py + 22, LABEL_COLOR, false);
    }

    // ------------------------------------------------------------------
    // 输入
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        DragTarget t = hitTest(mx, my);
        if (t != null) {
            this.dragging = t;
            applyDrag(t, mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (super.mouseDragged(mx, my, button, dragX, dragY)) {
            return true;
        }
        if (this.dragging != null) {
            applyDrag(this.dragging, mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    private DragTarget hitTest(double mx, double my) {
        if (mx >= this.planeX && mx < this.planeX + PLANE_SIZE
                && my >= this.planeY && my < this.planeY + PLANE_SIZE) {
            return DragTarget.PLANE;
        }
        if (mx >= this.hueX && mx < this.hueX + this.hueBarWidth()
                && my >= this.hueY - 2 && my < this.hueY + BAR_HEIGHT + 2) {
            return DragTarget.HUE;
        }
        return null;
    }

    private void applyDrag(DragTarget target, double mx, double my) {
        switch (target) {
            case PLANE -> {
                this.saturation = (float) Mth.clamp((mx - this.planeX) / (PLANE_SIZE - 1), 0.0, 1.0);
                this.value = 1.0f - (float) Mth.clamp((my - this.planeY) / (PLANE_SIZE - 1), 0.0, 1.0);
            }
            case HUE -> {
                int w = this.hueBarWidth();
                this.hue = (float) Mth.clamp((mx - this.hueX) / (w - 1), 0.0, 1.0);
            }
        }
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    private void confirm() {
        // 回调里父界面会 refreshResults；回到父界面
        this.consumer.accept(this.opaqueColor() & 0xFFFFFF);
        this.minecraft.setScreen(this.parent);
    }

    private void cancel() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
