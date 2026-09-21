package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 调色板主界面（按 O 打开）。
 *
 * <p>布局（全部相对 this.width/height 计算）：
 * <pre>
 * ┌────────────────────────────────────────────────┐
 * │                 Little Palette                  │
 * │  [<] [调色板名(EditBox)] [>]  [新建] [删除]        │
 * │  颜色槽 [■][■][■][■]...    [取色] [采样]          │
 * │  渐变预览条（两色时显示）                          │
 * │  容差 ▬▬▬▬●▬▬▬   [种类▾] [完整方块▾]  共 N 个      │
 * │  ┌────────────────────────────────────────┐    │
 * │  │ 结果网格（renderItem，悬停 tooltip）        │    │
 * │  └────────────────────────────────────────┘    │
 * │                                     [完成]      │
 * └────────────────────────────────────────────────┘
 * </pre>
 */
public class PaletteScreen extends Screen {

    private static final int PANEL_BG = 0xC0101010;
    private static final int PANEL_LINE = 0xFF606060;
    private static final int SLOT_SIZE = 18;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int RESULT_CELL = 20;
    private static final int RESULT_COLS = 20;
    private static final int RESULT_ROWS = 6;
    private static final int RESULT_MAX = RESULT_COLS * RESULT_ROWS;

    private final List<Palette> palettes;
    private int currentPaletteIndex = 0;

    // 搜索状态
    private double toleranceSliderValue = 0.5;
    private String categoryFilter = null;
    private int fullBlockMode = PaletteSearch.FULL_BLOCK_ANY;

    private List<PaletteSearch.Result> results = List.of();

    // 悬停
    private ItemStack hoveredStack = ItemStack.EMPTY;

    // 控件引用
    private EditBox nameBox;
    private final List<ColorSlotButton> colorSlots = new ArrayList<>();
    private Button pickButton;
    private Button sampleButton;
    private GradientPreview gradientPreview;

    public PaletteScreen() {
        super(Component.translatable("gui.littlepalette.title"));
        this.palettes = new ArrayList<>(ColorIndex.loadPalettes());
        if (this.palettes.isEmpty()) {
            this.palettes.add(new Palette("Palette 1"));
        }
    }

    private Palette current() {
        return this.palettes.get(this.currentPaletteIndex);
    }

    // ------------------------------------------------------------------
    // init
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        // 首次打开（或资源重载后）在这里同步构建颜色索引：
        // 此时代码在主线程、模型已烘焙，是唯一安全可靠的构建时机。
        ColorIndex.INSTANCE.ensureBuilt();

        int cx = this.width / 2;
        int rowY = 30;

        // —— 第一行：调色板切换/新建/删除 + 名称编辑 ——
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> switchPalette(-1))
                .bounds(cx - 200, rowY, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> switchPalette(1))
                .bounds(cx - 176, rowY, 20, 20).build());

        this.nameBox = new EditBox(this.font, cx - 150, rowY + 2, 120, 16,
                Component.translatable("gui.littlepalette.palette_name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(current().getName());
        this.nameBox.setResponder(text -> {
            current().setName(text);
            savePalettes();
        });
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.littlepalette.new"),
                        b -> createPalette())
                .bounds(cx + 110, rowY, 44, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.littlepalette.delete"),
                        b -> deletePalette())
                .bounds(cx + 158, rowY, 44, 20).build());

        // —— 第二行：8 个颜色槽 + 取色 + 采样 ——
        rowY += 26;
        this.colorSlots.clear();
        int slotsWidth = Palette.MAX_COLORS * (SLOT_SIZE + 2);
        int slotX = cx - 200;
        for (int i = 0; i < Palette.MAX_COLORS; i++) {
            final int idx = i;
            ColorSlotButton slot = new ColorSlotButton(slotX + i * (SLOT_SIZE + 2), rowY, SLOT_SIZE,
                    () -> colorAt(idx),          // 供渲染的颜色
                    () -> removeColor(idx));     // 点击删除
            this.colorSlots.add(this.addRenderableWidget(slot));
        }
        this.pickButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.littlepalette.pick"), b -> openPicker())
                .bounds(cx - 200 + slotsWidth + 8, rowY - 1, 48, 20).build());
        this.sampleButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.littlepalette.sample"), b -> sampleHeld())
                .bounds(cx - 200 + slotsWidth + 60, rowY - 1, 48, 20).build());

        // —— 第三行：渐变预览 ——
        rowY += 24;
        this.gradientPreview = this.addRenderableWidget(new GradientPreview(
                cx - 200, rowY, 120, 6,
                () -> current().colorCount() >= 2
                        ? new int[]{current().getColors().get(0), current().getColors().get(1)}
                        : null));

        // —— 第四行：容差滑块 + 筛选按钮 + 结果计数 ——
        rowY += 12;
        this.addRenderableWidget(new ToleranceSlider(cx - 200, rowY, 110, 16,
                this.toleranceSliderValue, v -> {
            this.toleranceSliderValue = v;
            refreshResults();
        }));

        List<String> categories = new ArrayList<>();
        categories.add("");                       // 空字符串 = 全部（避免在 CycleButton 里放 null）
        categories.addAll(ColorIndex.INSTANCE.byCategory.keySet().stream().sorted().toList());
        this.addRenderableWidget(CycleButton.<String>builder((String s) ->
                        s.isEmpty()
                                ? Component.translatable("gui.littlepalette.category_all")
                                : Component.translatable("gui.littlepalette.category_item", s))
                .withValues(categories)
                .withInitialValue(this.categoryFilter == null ? "" : this.categoryFilter)
                .create(cx - 82, rowY, 110, 16, Component.empty(),
                        (btn, val) -> {
                            this.categoryFilter = val.isEmpty() ? null : val;
                            refreshResults();
                        }));

        this.addRenderableWidget(CycleButton.<Integer>builder((Integer m) ->
                        Component.translatable(switch (m) {
                            case PaletteSearch.FULL_BLOCK_ONLY -> "gui.littlepalette.full_only";
                            case PaletteSearch.FULL_BLOCK_EXCLUDED -> "gui.littlepalette.full_excluded";
                            default -> "gui.littlepalette.full_any";
                        }))
                .withValues(PaletteSearch.FULL_BLOCK_ANY,
                        PaletteSearch.FULL_BLOCK_ONLY,
                        PaletteSearch.FULL_BLOCK_EXCLUDED)
                .withInitialValue(this.fullBlockMode)
                .create(cx + 36, rowY, 110, 16, Component.empty(),
                        (btn, val) -> {
                            this.fullBlockMode = val;
                            refreshResults();
                        }));

        // —— 底部：完成 ——
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        b -> onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());

        refreshResults();
    }

    private int colorAt(int index) {
        Palette p = current();
        return index < p.colorCount() ? p.getColors().get(index) : -1;
    }

    // ------------------------------------------------------------------
    // 调色板管理
    // ------------------------------------------------------------------

    private void switchPalette(int dir) {
        int n = this.palettes.size();
        this.currentPaletteIndex = (this.currentPaletteIndex + dir + n) % n;
        savePalettes();
        this.rebuildWidgets();
    }

    private void createPalette() {
        this.palettes.add(new Palette("Palette " + (this.palettes.size() + 1)));
        this.currentPaletteIndex = this.palettes.size() - 1;
        savePalettes();
        this.rebuildWidgets();
    }

    private void deletePalette() {
        if (this.palettes.size() <= 1) {
            return;
        }
        this.palettes.remove(this.currentPaletteIndex);
        this.currentPaletteIndex = Math.min(this.currentPaletteIndex, this.palettes.size() - 1);
        savePalettes();
        this.rebuildWidgets();
    }

    private void savePalettes() {
        ColorIndex.savePalettes(this.palettes);
    }

    // ------------------------------------------------------------------
    // 颜色操作
    // ------------------------------------------------------------------

    private void openPicker() {
        if (current().isFull()) {
            return;
        }
        this.minecraft.setScreen(new ColorPickerScreen(this, argb -> {
            current().addColor(argb);
            savePalettes();
            refreshResults();
        }));
    }

    private void sampleHeld() {
        if (current().isFull() || this.minecraft.player == null) {
            return;
        }
        // 只读主手物品，不消耗玩家物品（采样 ≠ 使用）
        ItemStack held = this.minecraft.player.getInventory().getSelected();
        if (held.isEmpty()) {
            return;
        }
        Block block = Block.byItem(held.getItem());
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            return;   // 非方块物品（byItem 返回 AIR）
        }
        BlockModelShaperAdapter adapter = new BlockModelShaperAdapter();
        int rgb = adapter.averageColor(block.defaultBlockState(),
                net.minecraft.util.RandomSource.create(),
                net.minecraft.core.BlockPos.ZERO);
        if (rgb < 0) {
            return;
        }
        current().addColor(0xFF000000 | rgb);
        savePalettes();
        refreshResults();
    }

    private void removeColor(int index) {
        current().removeColor(index);
        savePalettes();
        refreshResults();
    }

    // ------------------------------------------------------------------
    // 搜索
    // ------------------------------------------------------------------

    private void refreshResults() {
        if (!ColorIndex.INSTANCE.isReady() || current().colorCount() == 0) {
            this.results = List.of();
            return;
        }
        Palette p = current();
        int c0 = p.getColors().get(0);
        float[] labA = ColorMath.rgbToLab((c0 >> 16) & 0xFF, (c0 >> 8) & 0xFF, c0 & 0xFF);
        float[] labB = null;
        if (p.colorCount() >= 2) {
            int c1 = p.getColors().get(1);
            labB = ColorMath.rgbToLab((c1 >> 16) & 0xFF, (c1 >> 8) & 0xFF, c1 & 0xFF);
        }
        float tolerance = PaletteSearch.toleranceFromSlider(this.toleranceSliderValue);
        this.results = PaletteSearch.search(labA, labB, tolerance,
                this.categoryFilter, this.fullBlockMode);
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int panelX1 = cx - 210, panelY1 = 22;
        int panelX2 = cx + 210, panelY2 = this.height - 36;
        g.fill(panelX1, panelY1, panelX2, panelY2, PANEL_BG);
        g.renderOutline(panelX1, panelY1, panelX2 - panelX1, panelY2 - panelY1, PANEL_LINE);

        g.drawCenteredString(this.font, this.title, cx, 10, LABEL_COLOR);

        renderResultCount(g);

        // 控件（含颜色槽、结果由 renderItem 画在控件层下面？不——结果先画，控件最后画）
        renderResults(g, mouseX, mouseY);

        for (var r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }

        if (!this.hoveredStack.isEmpty()) {
            g.renderTooltip(this.font, this.hoveredStack, mouseX, mouseY);
        }
    }

    private void renderResultCount(GuiGraphics g) {
        String key = current().colorCount() >= 2
                ? "gui.littlepalette.results_gradient"
                : "gui.littlepalette.results";
        String text = this.results.size() >= RESULT_MAX
                ? Component.translatable(key, RESULT_MAX + "+").getString()
                : Component.translatable(key, this.results.size()).getString();
        g.drawString(this.font, text, this.width / 2 - 200, 148, 0xFF909090, false);
    }

    private void renderResults(GuiGraphics g, int mouseX, int mouseY) {
        int startX = this.width / 2 - 200;
        int startY = 158;
        this.hoveredStack = ItemStack.EMPTY;

        int shown = Math.min(this.results.size(), RESULT_MAX);
        for (int i = 0; i < shown; i++) {
            int col = i % RESULT_COLS, row = i / RESULT_COLS;
            int x = startX + col * RESULT_CELL;
            int y = startY + row * RESULT_CELL;
            ItemStack stack = new ItemStack(this.results.get(i).state().getBlock());
            g.renderItem(stack, x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                this.hoveredStack = stack;
            }
        }
    }

    // ------------------------------------------------------------------
    // 输入 & 生命周期
    // ------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC：编辑名字时先让文本框失焦，再按一次才关界面
        if (keyCode == 256 && this.nameBox.isFocused()) {
            this.nameBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        savePalettes();
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
