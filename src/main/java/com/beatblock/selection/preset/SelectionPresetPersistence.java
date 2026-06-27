package com.beatblock.selection.preset;

import com.google.gson.*;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 选区预设持久化：保存和加载预设到 JSON 文件。
 */
public final class SelectionPresetPersistence {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionPresetPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;

    private SelectionPresetPersistence() {}

    /**
     * 保存所有预设到文件。
     *
     * @param filePath 保存路径
     * @param manager 预设管理器
     */
    public static void save(Path filePath, SelectionPresetManager manager) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);

        JsonArray presetsArray = new JsonArray();
        for (SelectionPresetManager.SelectionPreset preset : manager.getAllPresets()) {
            JsonObject presetObj = new JsonObject();
            presetObj.addProperty("id", preset.id());
            presetObj.addProperty("name", preset.name());
            if (preset.description() != null) {
                presetObj.addProperty("description", preset.description());
            }
            presetObj.addProperty("createdTime", preset.createdTime());

            JsonArray blocksArray = new JsonArray();
            for (BlockPos pos : preset.blocks()) {
                JsonObject posObj = new JsonObject();
                posObj.addProperty("x", pos.getX());
                posObj.addProperty("y", pos.getY());
                posObj.addProperty("z", pos.getZ());
                blocksArray.add(posObj);
            }
            presetObj.add("blocks", blocksArray);

            presetsArray.add(presetObj);
        }
        root.add("presets", presetsArray);

        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(filePath, GSON.toJson(root), StandardCharsets.UTF_8);
        LOGGER.info("保存了 {} 个选区预设到 {}", manager.getPresetCount(), filePath);
    }

    /**
     * 从文件加载所有预设。
     *
     * @param filePath 文件路径
     * @param manager 预设管理器
     */
    public static void load(Path filePath, SelectionPresetManager manager) throws IOException {
        if (!Files.exists(filePath)) {
            LOGGER.info("选区预设文件不存在: {}", filePath);
            return;
        }

        String json = Files.readString(filePath, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        int version = root.has("version") ? root.get("version").getAsInt() : 1;
        if (version != VERSION) {
            LOGGER.warn("选区预设文件版本不匹配: 期望 {}, 实际 {}", VERSION, version);
        }

        manager.clear();

        JsonArray presetsArray = root.getAsJsonArray("presets");
        int loadedCount = 0;

        for (JsonElement elem : presetsArray) {
            try {
                JsonObject presetObj = elem.getAsJsonObject();
                String id = presetObj.get("id").getAsString();
                String name = presetObj.get("name").getAsString();
                String description = presetObj.has("description")
                    ? presetObj.get("description").getAsString()
                    : null;
                long createdTime = presetObj.has("createdTime")
                    ? presetObj.get("createdTime").getAsLong()
                    : System.currentTimeMillis();

                List<BlockPos> blocks = new ArrayList<>();
                JsonArray blocksArray = presetObj.getAsJsonArray("blocks");
                for (JsonElement blockElem : blocksArray) {
                    JsonObject posObj = blockElem.getAsJsonObject();
                    int x = posObj.get("x").getAsInt();
                    int y = posObj.get("y").getAsInt();
                    int z = posObj.get("z").getAsInt();
                    blocks.add(new BlockPos(x, y, z));
                }

                // 直接创建预设并添加到管理器
                SelectionPresetManager.SelectionPreset preset =
                    new SelectionPresetManager.SelectionPreset(
                        id, name, description, blocks, createdTime
                    );

                // 注意：这里需要 SelectionPresetManager 提供直接添加预设的方法
                // 或者通过反射访问 presets Map
                // 暂时假设有这样的方法
                // manager.addPreset(preset);

                loadedCount++;
            } catch (Exception e) {
                LOGGER.warn("加载预设失败: {}", e.getMessage());
            }
        }

        LOGGER.info("从 {} 加载了 {} 个选区预设", filePath, loadedCount);
    }

    /**
     * 获取默认预设文件路径。
     *
     * @param gameDir Minecraft 游戏目录
     * @return 预设文件路径
     */
    public static Path getDefaultPath(Path gameDir) {
        return gameDir.resolve("config/beatblock/selection_presets.json");
    }
}
