package io.github.qlfy233.littlepalette.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.qlfy233.littlepalette.LittlePalette;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** 按键绑定：默认 O 打开调色板界面。 */
@EventBusSubscriber(modid = LittlePalette.MODID, value = Dist.CLIENT)
public final class KeyBindings {

    public static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.littlepalette.open_gui",
            KeyConflictContext.IN_GAME,          // 只在游戏内生效，主菜单不响应
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.littlepalette");

    private KeyBindings() {
    }

    /** mod bus：注册按键，否则按键不会出现在「选项 → 控制」里。 */
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI);
    }

    /** game bus：每 tick 检查。consumeClick 必须放 while 里（官方文档要求）。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_GUI.consumeClick()) {
            Minecraft.getInstance().setScreen(new PaletteScreen());
        }
    }
}
