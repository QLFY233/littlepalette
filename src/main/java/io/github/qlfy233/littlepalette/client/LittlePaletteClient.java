package io.github.qlfy233.littlepalette.client;

import io.github.qlfy233.littlepalette.LittlePalette;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * 客户端入口。所有 net.minecraft.client.* 的访问都限制在 client 包里，
 * 保证专用服务器不会因为加载客户端类而崩溃。
 */
@Mod(value = LittlePalette.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = LittlePalette.MODID, value = Dist.CLIENT)
public class LittlePaletteClient {

    public LittlePaletteClient() {
    }

    /**
     * 把颜色索引挂进客户端资源重载管线：
     * 启动、按 F3+T、切换材质包时都会自动重建颜色索引。
     * 这也是"支持模组方块和材质包"的关键——颜色永远从当前资源包的纹理实时提取。
     */
    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ColorIndex.INSTANCE);
    }
}
