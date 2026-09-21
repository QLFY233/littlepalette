package io.github.qlfy233.littlepalette.client;

import io.github.qlfy233.littlepalette.LittlePalette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 方块颜色索引：遍历所有注册方块，从烘焙模型纹理提取"代表色"（CIELAB）。
 *
 * <p><b>构建时机</b>：不在资源重载回调里直接构建——那时烘焙模型可能还没就绪。
 * 这里只把索引标记为失效（ready=false），真正的构建推迟到第一次打开界面时
 * （{@link #ensureBuilt()}，主线程、模型已烘焙），之后一直复用。
 * 材质包/模组方块的支持：资源一重载（启动、F3+T、换材质包）监听器就把索引作废，
 * 下次打开界面自动用新资源重建。
 */
public class ColorIndex implements ResourceManagerReloadListener {

    public static final ColorIndex INSTANCE = new ColorIndex();

    /** 排除的方块：没有有意义的纹理颜色。 */
    private static final Set<String> EXCLUDED = Set.of(
            "air", "cave_air", "void_air", "light", "barrier",
            "water", "lava", "bubble_column", "moving_piston",
            "piston_head", "structure_void", "structure_block", "jigsaw");

    public record Entry(BlockState state, float[] lab) {
    }

    /** block id（不含命名空间）小写 -> 条目列表，用于"方块种类"筛选。 */
    public final Map<String, List<Entry>> byCategory = new HashMap<>();

    /** 全量条目。 */
    public final List<Entry> all = new ArrayList<>();

    private volatile boolean ready = false;

    private ColorIndex() {
    }

    public boolean isReady() {
        return this.ready;
    }

    /** 资源重载时只作废不重建（此时烘焙模型不可靠）。 */
    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.ready = false;
    }

    /**
     * 确保索引可用；失效时在当前线程（主线程）重建。
     * 首次构建遍历全部方块读纹理，可能耗时几百毫秒，属于一次性成本。
     */
    public synchronized void ensureBuilt() {
        if (this.ready) {
            return;
        }
        this.byCategory.clear();
        this.all.clear();
        try {
            this.build();
        } catch (Exception e) {
            LittlePalette.LOGGER.error("Failed to build color index", e);
        }
        this.ready = true;
        LittlePalette.LOGGER.info("Color index built: {} blocks in {} categories",
                this.all.size(), this.byCategory.size());
    }

    private void build() {
        BlockModelShaperAdapter shaper = new BlockModelShaperAdapter();
        RandomSource random = RandomSource.create();
        BlockPos pos = BlockPos.ZERO;
        Set<Block> seen = new HashSet<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (EXCLUDED.contains(id.getPath()) || !seen.add(block)) {
                continue;
            }
            // 只取 default state：同一方块的所有 state 共享纹理，不必重复提取
            BlockState state = block.defaultBlockState();
            try {
                int rgb = shaper.averageColor(state, random, pos);
                if (rgb < 0) {
                    continue;   // 无烘焙几何（自定义渲染器等），跳过
                }
                float[] lab = ColorMath.rgbToLab((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                Entry entry = new Entry(state, lab);
                this.all.add(entry);
                this.byCategory.computeIfAbsent(id.getPath(), k -> new ArrayList<>()).add(entry);
            } catch (Exception e) {
                // 个别方块模型怪异，跳过即可，不能让整个索引失败
            }
        }
    }

    // ------------------------------------------------------------------
    // 调色板文件（config/littlepalette/palettes.json）
    // ------------------------------------------------------------------

    public static Path palettesFile() {
        return FMLPaths.CONFIGDIR.get().resolve("littlepalette").resolve("palettes.json");
    }

    public static List<Palette> loadPalettes() {
        return Palette.loadAll(palettesFile());
    }

    public static void savePalettes(List<Palette> palettes) {
        Palette.saveAll(palettes, palettesFile());
    }
}
