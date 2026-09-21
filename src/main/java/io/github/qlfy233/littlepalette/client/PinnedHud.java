package io.github.qlfy233.littlepalette.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import com.google.gson.JsonObject;
import io.github.qlfy233.littlepalette.LittlePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * "Pin" 功能：把搜索结果以横排方块图标 pin 在屏幕右上角（HUD 层，游戏中常驻显示）。
 *
 * <p>实现为自定义 GUI 层（RegisterGuiLayersEvent.registerAboveAll，签名已核实：
 * LayeredDraw.Layer.render(GuiGraphics, DeltaTracker)）。
 * 开关由 PaletteScreen 的 Pin 按钮 / P 键控制，状态持久化在 config。
 * 只画方块图标本体，无底框无边框。
 */
@EventBusSubscriber(modid = LittlePalette.MODID, value = Dist.CLIENT)
public final class PinnedHud {

    public static final int CELL = 18;      // 每个图标占位
    public static final int MAX_PIN = 16;   // 右上角横排最多显示个数

    private static final List<PaletteSearch.Result> pinned = new ArrayList<>();

    private static boolean enabled = loadEnabled();

    private PinnedHud() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
        saveEnabled();
    }

    /** 用新的搜索结果替换 pin 内容。 */
    public static void setPinned(List<PaletteSearch.Result> results) {
        pinned.clear();
        if (results != null) {
            pinned.addAll(results.subList(0, Math.min(results.size(), MAX_PIN)));
        }
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(LittlePalette.MODID, "pinned"),
                PinnedHud::renderLayer);
    }

    private static void renderLayer(GuiGraphics g, DeltaTracker delta) {
        if (!enabled || pinned.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int w = g.guiWidth();
        int totalW = pinned.size() * CELL;
        int x0 = w - totalW - 4;
        int y0 = 4;

        // 只画方块图标本体，不加底框/边框
        for (int i = 0; i < pinned.size(); i++) {
            int x = x0 + i * CELL;
            g.renderItem(pinned.get(i).state().getBlock().asItem().getDefaultInstance(), x + 1, y0 + 1);
        }
    }

    // ------------------------------------------------------------------
    // 持久化（pin 开关存 config，内容是会话态不存）
    // ------------------------------------------------------------------

    private static boolean loadEnabled() {
        var file = ColorIndex.palettesFile().resolveSibling("pinned.json");
        if (!java.nio.file.Files.exists(file)) {
            return false;
        }
        try {
            return com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file))
                    .getAsJsonObject().get("enabled").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static void saveEnabled() {
        try {
            var file = ColorIndex.palettesFile().resolveSibling("pinned.json");
            java.nio.file.Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", enabled);
            java.nio.file.Files.writeString(file, obj.toString());
        } catch (Exception ignored) {
        }
    }
}
