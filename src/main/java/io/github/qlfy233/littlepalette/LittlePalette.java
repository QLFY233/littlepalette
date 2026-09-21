package io.github.qlfy233.littlepalette;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Little Palette —— 渐变配色辅助 mod。
 *
 * <p>主类只放公共常量，所有逻辑都在 client 包里（本 mod 纯客户端）。
 */
@Mod(LittlePalette.MODID)
public class LittlePalette {
    public static final String MODID = "littlepalette";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LittlePalette() {
    }
}
