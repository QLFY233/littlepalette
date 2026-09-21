package io.github.qlfy233.littlepalette.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个调色板：名字 + 最多 8 个条目。
 *
 * <p>每个条目 = 一个颜色（ARGB 语义，实际存 0xRRGGBB）+ 一个可选的来源方块
 * （注册名字符串，可为 null）。来源方块只用于 UI 展示（槽位画方块图标），
 * 搜索始终用颜色值。
 *
 * <p>序列化格式（config/littlepalette/palettes.json 的数组元素）：
 * <pre>{ "name": "晚霞", "colors": [16711680, ...], "blocks": ["minecraft:redstone_block", null, ...] }</pre>
 */
public class Palette {
    public static final int MAX_COLORS = 8;

    private String name;
    private final List<Integer> colors = new ArrayList<>();
    /** 与 colors 平行；允许 null（纯取色的条目没有来源方块）。 */
    private final List<String> blocks = new ArrayList<>();

    public Palette(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Integer> getColors() {
        return this.colors;
    }

    /** 条目 i 的来源方块注册名，无则 null；越界返回 null。 */
    public String getBlockId(int index) {
        if (index >= 0 && index < this.blocks.size()) {
            return this.blocks.get(index);
        }
        return null;
    }

    public int colorCount() {
        return this.colors.size();
    }

    public boolean isFull() {
        return this.colors.size() >= MAX_COLORS;
    }

    /** 追加一个颜色（可带来源方块）。完全相同的颜色会合并。 */
    public boolean addColor(int rgb, String blockId) {
        if (this.isFull()) {
            return false;
        }
        int c = rgb & 0x00FFFFFF;
        if (this.colors.contains(c)) {
            return true;   // 重复颜色不重复加
        }
        this.colors.add(c);
        this.blocks.add(blockId);
        return true;
    }

    /** 替换第 index 个条目（放入方块时光标覆盖已有槽位）。 */
    public void setColorAt(int index, int rgb, String blockId) {
        if (index >= 0 && index < this.colors.size()) {
            this.colors.set(index, rgb & 0x00FFFFFF);
            this.blocks.set(index, blockId);
        }
    }

    public void removeColor(int index) {
        if (index >= 0 && index < this.colors.size()) {
            this.colors.remove(index);
            this.blocks.remove(index);
        }
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    public static Palette fromJson(JsonObject obj) {
        Palette palette = new Palette(obj.get("name").getAsString());
        JsonArray colors = obj.getAsJsonArray("colors");
        JsonArray blocks = obj.has("blocks") ? obj.getAsJsonArray("blocks") : new JsonArray();
        for (int i = 0; i < colors.size() && i < MAX_COLORS; i++) {
            palette.colors.add(colors.get(i).getAsInt() & 0x00FFFFFF);
            palette.blocks.add(i < blocks.size() && !blocks.get(i).isJsonNull()
                    ? blocks.get(i).getAsString() : null);
        }
        return palette;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", this.name);
        JsonArray colors = new JsonArray();
        JsonArray blocks = new JsonArray();
        for (int i = 0; i < this.colors.size(); i++) {
            colors.add(this.colors.get(i));
            String b = this.blocks.get(i);
            if (b == null) {
                blocks.add(com.google.gson.JsonNull.INSTANCE);
            } else {
                blocks.add(b);
            }
        }
        obj.add("colors", colors);
        obj.add("blocks", blocks);
        return obj;
    }

    // ------------------------------------------------------------------
    // 存取
    // ------------------------------------------------------------------

    public static List<Palette> loadAll(Path file) {
        List<Palette> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            JsonArray arr = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                result.add(Palette.fromJson(arr.get(i).getAsJsonObject()));
            }
        } catch (IOException | IllegalStateException e) {
            Path backup = file.resolveSibling("palettes.json.corrupt");
            try {
                Files.move(file, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
        return result;
    }

    public static void saveAll(List<Palette> palettes, Path file) {
        JsonArray arr = new JsonArray();
        for (Palette p : palettes) {
            arr.add(p.toJson());
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, arr.toString());
        } catch (IOException ignored) {
        }
    }
}
