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
 * 一个调色板：名字 + 最多 8 个颜色。
 *
 * <p>序列化格式就是 config/littlepalette/palettes.json 里的一个数组元素：
 * <pre>{ "name": "晚霞", "colors": [16711680, 16746496, 15658734] }</pre>
 * 颜色用 int 存（ARGB，alpha 恒为 255），直接 JSON 数字即可。
 */
public class Palette {
    /** 限制数量防止 UI 溢出，8 个颜色对建筑配色绰绰有余。 */
    public static final int MAX_COLORS = 8;

    private String name;
    private final List<Integer> colors = new ArrayList<>();

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

    public int colorCount() {
        return this.colors.size();
    }

    public boolean isFull() {
        return this.colors.size() >= MAX_COLORS;
    }

    public boolean addColor(int argb) {
        if (this.isFull()) {
            return false;
        }
        // 合并完全相同的颜色（重复采样同一个方块没意义）
        if (!this.colors.contains(argb & 0x00FFFFFF)) {
            this.colors.add(argb & 0x00FFFFFF);
        }
        return true;
    }

    public void removeColor(int index) {
        if (index >= 0 && index < this.colors.size()) {
            this.colors.remove(index);
        }
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    public static Palette fromJson(JsonObject obj) {
        Palette palette = new Palette(obj.get("name").getAsString());
        JsonArray arr = obj.getAsJsonArray("colors");
        for (int i = 0; i < arr.size() && i < MAX_COLORS; i++) {
            palette.colors.add(arr.get(i).getAsInt() & 0x00FFFFFF);
        }
        return palette;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", this.name);
        JsonArray arr = new JsonArray();
        for (int c : this.colors) {
            arr.add(c);
        }
        obj.add("colors", arr);
        return obj;
    }

    // ------------------------------------------------------------------
    // 存取（config/littlepalette/palettes.json）
    // ------------------------------------------------------------------

    public static List<Palette> loadAll(Path file) {
        List<Palette> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            String text = Files.readString(file);
            JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                result.add(Palette.fromJson(arr.get(i).getAsJsonObject()));
            }
        } catch (IOException | IllegalStateException e) {
            // 文件损坏时不清空用户数据：重命名备份后从空开始，用户还能手工找回
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
        } catch (IOException e) {
            // 保存失败不打断游戏，只在日志里提示
        }
    }
}
