package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 调色板主界面 v2。
 *
 * <p>新增：
 * <ul>
 *   <li>底部渲染玩家背包 36 格 —— 点击物品拾取副本（不消耗），再点调色板槽位放入</li>
 *   <li>光标携带方块（{@link #carried}，仿容器界面的 cursor stack）</li>
 *   <li>结果网格滚轮滚动（scissor 裁剪 + scrollOffset）</li>
 *   <li>Pin 按钮：把结果推到 HUD（{@link PinnedHud}）</li>
 * </ul>
 */
public class PaletteScreen extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int RESULT_CELL = 18;
    private static final int RESULT_COLS = 23;
    private static final int RESULT_MAX_ROWS = 6;
    private static final int RESULT_VIEW_H = RESULT_MAX_ROWS * RESULT_CELL;

    private final List<Palette> palettes;
    private int currentPaletteIndex = 0;

    // 搜索状态
    private double toleranceSliderValue = 0.5;
    private String categoryFilter = null;
    private int fullBlockMode = PaletteSearch.FULL_BLOCK_ANY;
    private int scrollOffset = 0;               // 已滚过的行数
    private List<PaletteSearch.Result> results = List.of();
    private int totalRows = 0;

    // 悬停
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int hoveredX, hoveredY;

    // 光标携带（点击背包物品后的副本，点击颜色槽放入）
    private ItemStack carried = ItemStack.EMPTY;

    // 控件引用
    private EditBox nameBox;
    private final List<ColorSlotButton> colorSlots = new ArrayList<>();

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
    // 布局常量（渲染与命中测试共用）
    // ------------------------------------------------------------------

    private int resultX, resultY, resultW;       // 结果区
    private int invX, invY;                      // 背包 9x3 区左上
    private int hotbarX, hotbarY;                // 快捷栏 9 格左上

    private void computeLayout() {
        int cx = this.width / 2;
        this.resultX = cx - 207;
        this.resultY = 118;
        this.resultW = RESULT_COLS * RESULT_CELL;
        this.invX = cx - 81;
        this.invY = this.height - 110;
        this.hotbarX = cx - 81;
        this.hotbarY = this.height - 24;
    }

    // ------------------------------------------------------------------
    // init
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        ColorIndex.INSTANCE.ensureBuilt();
        this.computeLayout();

        int cx = this.width / 2;
        int rowY = 30;

        // —— 第一行：调色板切换/新建/删除 + 名称 ——
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> switchPalette(-1))
                .bounds(cx - 207, rowY, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> switchPalette(1))
                .bounds(cx - 183, rowY, 20, 20).build());

        this.nameBox = new EditBox(this.font, cx - 157, rowY + 2, 110, 16,
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
                .bounds(cx - 41, rowY, 44, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.littlepalette.delete"),
                        b -> deletePalette())
                .bounds(cx + 7, rowY, 44, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.littlepalette.pin"),
                        b -> pinResults())
                .bounds(cx + 129, rowY, 44, 20).build());

        // —— 第二行：8 个颜色槽 + 取色 + 采样 ——
        rowY += 26;
        this.colorSlots.clear();
        for (int i = 0; i < Palette.MAX_COLORS; i++) {
            final int idx = i;
            this.colorSlots.add(this.addRenderableWidget(new ColorSlotButton(
                    cx - 207 + i * (SLOT_SIZE + 2), rowY, SLOT_SIZE,
                    () -> colorAt(idx),
                    ignored -> blockAt(idx),
                    () -> clickColorSlot(idx))));
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.littlepalette.pick"),
                        b -> openPicker())
                .bounds(cx - 207 + Palette.MAX_COLORS * (SLOT_SIZE + 2) + 8, rowY - 1, 48, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.littlepalette.sample"),
                        b -> sampleHeld())
                .bounds(cx - 207 + Palette.MAX_COLORS * (SLOT_SIZE + 2) + 60, rowY - 1, 48, 20).build());

        // —— 第三行：渐变预览（多色折线） ——
        rowY += 24;
        this.addRenderableWidget(new GradientPreview(cx - 207, rowY, 120, 6, this::previewColors));

        // —— 第四行：容差 + 筛选 ——
        rowY += 12;
        this.addRenderableWidget(new ToleranceSlider(cx - 207, rowY, 110, 16,
                this.toleranceSliderValue, v -> {
            this.toleranceSliderValue = v;
            refreshResults();
        }));

        List<String> categories = new ArrayList<>();
        categories.add("");
        categories.addAll(ColorIndex.INSTANCE.byCategory.keySet().stream().sorted().toList());
        this.addRenderableWidget(CycleButton.<String>builder((String s) ->
                        s.isEmpty()
                                ? Component.translatable("gui.littlepalette.category_all")
                                : Component.translatable("gui.littlepalette.category_item", s))
                .withValues(categories)
                .withInitialValue(this.categoryFilter == null ? "" : this.categoryFilter)
                .create(cx - 89, rowY, 110, 16, Component.empty(),
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
                .create(cx + 29, rowY, 110, 16, Component.empty(),
                        (btn, val) -> {
                            this.fullBlockMode = val;
                            refreshResults();
                        }));

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        b -> onClose())
                .bounds(cx + 151, rowY, 56, 16).build());

        refreshResults();
    }

    private int[] previewColors() {
        Palette p = current();
        if (p.colorCount() < 2) {
            return null;
        }
        int[] c = new int[p.colorCount()];
        for (int i = 0; i < c.length; i++) {
            c[i] = p.getColors().get(i);
        }
        return c;
    }

    private int colorAt(int index) {
        Palette p = current();
        return index < p.colorCount() ? p.getColors().get(index) : -1;
    }

    /** 条目 i 的来源方块（无/无效 id 返回 null）。 */
    private Block blockAt(int index) {
        String id = current().getBlockId(index);
        if (id == null) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return null;
        }
        // DefaultedRegistry.get 对未知 id 返回默认值（AIR），不会拋异常
        Block block = BuiltInRegistries.BLOCK.get(rl);
        return block == net.minecraft.world.level.block.Blocks.AIR ? null : block;
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

    /** 点击颜色槽：有光标方块 → 放入/覆盖；无 → 删除该槽。 */
    private void clickColorSlot(int index) {
        if (!this.carried.isEmpty()) {
            Block block = Block.byItem(this.carried.getItem());
            if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                return;
            }
            String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(block).toString();
            int rgb = sampleBlockRgb(block);
            if (rgb < 0) {
                return;
            }
            if (index < current().colorCount()) {
                current().setColorAt(index, rgb, blockId);   // 覆盖
            } else {
                current().addColor(rgb, blockId);            // 追加
            }
            // 放入后光标副本消失（类似创造模式放置；背包物品从未被消耗）
            this.carried = ItemStack.EMPTY;
        } else {
            current().removeColor(index);
        }
        savePalettes();
        refreshResults();
    }

    private void openPicker() {
        if (current().isFull()) {
            return;
        }
        this.minecraft.setScreen(new ColorPickerScreen(this, rgb -> {
            current().addColor(rgb, null);
            savePalettes();
            refreshResults();
        }));
    }

    private void sampleHeld() {
        if (current().isFull() || this.minecraft.player == null) {
            return;
        }
        ItemStack held = this.minecraft.player.getInventory().getSelected();
        if (held.isEmpty()) {
            return;
        }
        Block block = Block.byItem(held.getItem());
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            return;
        }
        int rgb = sampleBlockRgb(block);
        if (rgb < 0) {
            return;
        }
        current().addColor(rgb,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString());
        savePalettes();
        refreshResults();
    }

    /** 取方块的 RGB（优先用索引里已算好的，缓存友好）。 */
    private int sampleBlockRgb(Block block) {
        for (ColorIndex.Entry e : ColorIndex.INSTANCE.all) {
            if (e.state().getBlock() == block) {
                // Lab → 反推近似 RGB 不划算，直接重算一次（单方块成本极低）
                BlockModelShaperAdapter adapter = new BlockModelShaperAdapter();
                int rgb = adapter.averageColor(block.defaultBlockState(),
                        net.minecraft.util.RandomSource.create(),
                        net.minecraft.core.BlockPos.ZERO);
                return rgb;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // 搜索
    // ------------------------------------------------------------------

    private void refreshResults() {
        if (!ColorIndex.INSTANCE.isReady() || current().colorCount() == 0) {
            this.results = List.of();
            this.totalRows = 0;
            this.scrollOffset = 0;
            return;
        }
        Palette p = current();
        List<float[]> labs = new ArrayList<>();
        for (int c : p.getColors()) {
            labs.add(ColorMath.rgbToLab((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF));
        }
        float tolerance = PaletteSearch.toleranceFromSlider(this.toleranceSliderValue);
        this.results = PaletteSearch.search(labs, tolerance,
                this.categoryFilter, this.fullBlockMode);
        this.totalRows = (this.results.size() + RESULT_COLS - 1) / RESULT_COLS;
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0,
                Math.max(0, this.totalRows - RESULT_MAX_ROWS));
    }

    private void pinResults() {
        PinnedHud.setPinned(this.results);
        if (!PinnedHud.isEnabled()) {
            PinnedHud.toggle();
        }
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;

        g.drawCenteredString(this.font, this.title, cx, 10, LABEL_COLOR);

        renderResultCount(g);
        renderResults(g, mouseX, mouseY);
        renderInventory(g, mouseX, mouseY);

        for (var r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }

        // 光标携带的方块（最上层，跟随鼠标）
        if (!this.carried.isEmpty()) {
            g.renderItem(this.carried, mouseX - 8, mouseY - 8);
        }

        // 悬停 tooltip：颜色槽显示来源方块，其他区域显示悬停物品
        if (!this.hoveredStack.isEmpty()) {
            g.renderTooltip(this.font, this.hoveredStack, mouseX, mouseY);
        } else {
            for (ColorSlotButton slot : this.colorSlots) {
                if (slot.isHoveredOrFocused()) {
                    ItemStack display = slot.displayStack();
                    if (!display.isEmpty()) {
                        g.renderTooltip(this.font, display, mouseX, mouseY);
                    }
                    break;
                }
            }
        }
    }

    private void renderResultCount(GuiGraphics g) {
        String key = current().colorCount() >= 2
                ? "gui.littlepalette.results_gradient"
                : "gui.littlepalette.results";
        String text = this.results.size() > RESULT_COLS * RESULT_MAX_ROWS * 4
                ? Component.translatable(key, (RESULT_COLS * RESULT_MAX_ROWS * 4) + "+").getString()
                : Component.translatable(key, this.results.size()).getString();
        g.drawString(this.font, text, this.resultX, 108, 0xFF909090, false);
    }

    private void renderResults(GuiGraphics g, int mouseX, int mouseY) {
        this.hoveredStack = ItemStack.EMPTY;
        if (this.results.isEmpty()) {
            if (current().colorCount() == 0) {
                g.drawString(this.font,
                        Component.translatable("gui.littlepalette.empty_hint"),
                        this.resultX + 4, this.resultY + 4, 0xFF707070, false);
            } else if (!ColorIndex.INSTANCE.isReady()) {
                g.drawString(this.font,
                        Component.translatable("gui.littlepalette.index_loading"),
                        this.resultX + 4, this.resultY + 4, 0xFF707070, false);
            }
            return;
        }

        // scissor 裁剪滚动区
        g.enableScissor(this.resultX, this.resultY,
                this.resultX + this.resultW, this.resultY + RESULT_VIEW_H);
        int start = this.scrollOffset * RESULT_COLS;
        int end = Math.min(this.results.size(),
                start + RESULT_COLS * (RESULT_MAX_ROWS + 1));   // 多画一行供裁剪
        for (int i = start; i < end; i++) {
            int col = i % RESULT_COLS;
            int row = i / RESULT_COLS - this.scrollOffset;
            int x = this.resultX + col * RESULT_CELL;
            int y = this.resultY + row * RESULT_CELL;
            ItemStack stack = new ItemStack(this.results.get(i).state().getBlock());
            g.renderItem(stack, x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                this.hoveredStack = stack;
            }
        }
        g.disableScissor();

        // 滚动条
        if (this.totalRows > RESULT_MAX_ROWS) {
            int barX = this.resultX + this.resultW + 2;
            int trackH = RESULT_VIEW_H;
            int barH = Math.max(20, trackH * RESULT_MAX_ROWS / this.totalRows);
            int barY = this.resultY + (trackH - barH) * this.scrollOffset
                    / (this.totalRows - RESULT_MAX_ROWS);
            g.fill(barX, this.resultY, barX + 3, this.resultY + trackH, 0xFF303030);
            g.fill(barX, barY, barX + 3, barY + barH, 0xFF909090);
        }
    }

    /** 渲染玩家背包 9x3 + 快捷栏 9 格（槽位坐标来自 InventoryMenu 的 Slot.x/y）。 */
    private void renderInventory(GuiGraphics g, int mouseX, int mouseY) {
        Player player = this.minecraft.player;
        if (player == null) {
            return;
        }
        List<Slot> slots = player.inventoryMenu.slots;

        // 主背包 INV_SLOT_START(9) ~ INV_SLOT_END(45)：前 27 个是 9x3，后 9 个是快捷栏
        int invStart = InventoryMenu.INV_SLOT_START;      // 9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Slot slot = slots.get(invStart + row * 9 + col);
                int x = this.invX + col * 18;
                int y = this.invY + row * 18;
                drawSlot(g, slot, x, y, mouseX, mouseY);
            }
        }
        int hotbarStart = InventoryMenu.USE_ROW_SLOT_START;   // 36
        for (int col = 0; col < 9; col++) {
            Slot slot = slots.get(hotbarStart + col);
            drawSlot(g, slot, this.hotbarX + col * 18, this.hotbarY, mouseX, mouseY);
        }
    }

    private void drawSlot(GuiGraphics g, Slot slot, int x, int y, int mouseX, int mouseY) {
        g.fill(x, y, x + 16, y + 16, 0xFF181818);
        g.renderOutline(x, y, 16, 16, 0xFF3F3F3F);
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            g.renderItem(stack, x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                this.hoveredStack = stack;
            }
        }
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
        // 背包区域命中：拾取副本到光标（不消耗真实物品）
        int hit = inventoryHitTest((int) mx, (int) my);
        if (hit >= 0) {
            Player player = this.minecraft.player;
            ItemStack stack = player.inventoryMenu.getSlot(hit).getItem();
            this.carried = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            return true;
        }
        // 点空白处丢弃光标物品
        if (!this.carried.isEmpty()) {
            this.carried = ItemStack.EMPTY;
            return true;
        }
        return false;
    }

    /** 返回命中的 inventoryMenu 槽位全局编号，未命中返回 -1。 */
    private int inventoryHitTest(int mx, int my) {
        Player player = this.minecraft.player;
        if (player == null) {
            return -1;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = this.invX + col * 18;
                int y = this.invY + row * 18;
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                    return InventoryMenu.INV_SLOT_START + row * 9 + col;
                }
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = this.hotbarX + col * 18;
            if (mx >= x && mx < x + 16 && my >= this.hotbarY && my < this.hotbarY + 16) {
                return InventoryMenu.USE_ROW_SLOT_START + col;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double xScroll, double yScroll) {
        // 悬停在结果区时滚动结果；否则走控件默认逻辑
        if (mx >= this.resultX && mx < this.resultX + this.resultW + 6
                && my >= this.resultY && my < this.resultY + RESULT_VIEW_H) {
            if (this.totalRows > RESULT_MAX_ROWS) {
                this.scrollOffset = Mth.clamp(
                        this.scrollOffset - (int) Math.signum(yScroll),
                        0, this.totalRows - RESULT_MAX_ROWS);
            }
            return true;
        }
        return super.mouseScrolled(mx, my, xScroll, yScroll);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && this.nameBox.isFocused()) {
            this.nameBox.setFocused(false);
            return true;
        }
        if (keyCode == 256 && !this.carried.isEmpty()) {
            this.carried = ItemStack.EMPTY;   // ESC 先丢光标物品
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
