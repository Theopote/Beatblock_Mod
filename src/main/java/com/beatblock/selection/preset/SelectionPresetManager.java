package com.beatblock.selection.preset;

import net.minecraft.util.math.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 选区预设管理器：保存和加载命名选区。
 * <p>
 * 功能：
 * - 保存当前选区为命名预设
 * - 加载预设到当前选区
 * - 管理多个预设（创建/删除/重命名）
 * - 持久化到配置文件
 */
public final class SelectionPresetManager {

    private static final SelectionPresetManager INSTANCE = new SelectionPresetManager();

    private final Map<String, SelectionPreset> presets = new LinkedHashMap<>();

    private SelectionPresetManager() {}

    public static SelectionPresetManager getInstance() {
        return INSTANCE;
    }

    /**
     * 保存选区为预设。
     *
     * @param name 预设名称
     * @param blocks 方块位置列表
     * @param description 可选描述
     * @return 创建的预设
     */
    public SelectionPreset savePreset(String name, Set<BlockPos> blocks, @Nullable String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("预设名称不能为空");
        }

        String id = UUID.randomUUID().toString();
        SelectionPreset preset = new SelectionPreset(
            id,
            name,
            description,
            new ArrayList<>(blocks),
            System.currentTimeMillis()
        );

        presets.put(id, preset);
        return preset;
    }

    /**
     * 加载预设的方块列表。
     *
     * @param presetId 预设 ID
     * @return 方块位置列表，如果预设不存在返回空列表
     */
    public List<BlockPos> loadPreset(String presetId) {
        SelectionPreset preset = presets.get(presetId);
        if (preset == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(preset.blocks());
    }

    /**
     * 获取所有预设。
     */
    public List<SelectionPreset> getAllPresets() {
        return new ArrayList<>(presets.values());
    }

    /**
     * 根据名称查找预设。
     */
    public @Nullable SelectionPreset findByName(String name) {
        if (name == null) return null;
        for (SelectionPreset preset : presets.values()) {
            if (preset.name().equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * 删除预设。
     *
     * @param presetId 预设 ID
     * @return 是否成功删除
     */
    public boolean deletePreset(String presetId) {
        return presets.remove(presetId) != null;
    }

    /**
     * 重命名预设。
     *
     * @param presetId 预设 ID
     * @param newName 新名称
     * @return 是否成功重命名
     */
    public boolean renamePreset(String presetId, String newName) {
        SelectionPreset old = presets.get(presetId);
        if (old == null || newName == null || newName.isBlank()) {
            return false;
        }

        SelectionPreset updated = new SelectionPreset(
            old.id(),
            newName,
            old.description(),
            old.blocks(),
            old.createdTime()
        );

        presets.put(presetId, updated);
        return true;
    }

    /**
     * 更新预设描述。
     *
     * @param presetId 预设 ID
     * @param newDescription 新描述
     * @return 是否成功更新
     */
    public boolean updateDescription(String presetId, @Nullable String newDescription) {
        SelectionPreset old = presets.get(presetId);
        if (old == null) {
            return false;
        }

        SelectionPreset updated = new SelectionPreset(
            old.id(),
            old.name(),
            newDescription,
            old.blocks(),
            old.createdTime()
        );

        presets.put(presetId, updated);
        return true;
    }

    /**
     * 获取预设数量。
     */
    public int getPresetCount() {
        return presets.size();
    }

    /**
     * 清空所有预设。
     */
    public void clear() {
        presets.clear();
    }

    /**
     * 检查预设名称是否已存在。
     */
    public boolean nameExists(String name) {
        return findByName(name) != null;
    }

    /**
     * 生成唯一的预设名称。
     *
     * @param baseName 基础名称
     * @return 唯一名称（如果重复会添加数字后缀）
     */
    public String generateUniqueName(String baseName) {
        if (!nameExists(baseName)) {
            return baseName;
        }

        int counter = 2;
        String candidate;
        do {
            candidate = baseName + " " + counter;
            counter++;
        } while (nameExists(candidate) && counter < 1000);

        return candidate;
    }

    /**
     * 选区预设数据类。
     */
    public record SelectionPreset(
        String id,
        String name,
        @Nullable String description,
        List<BlockPos> blocks,
        long createdTime
    ) {
        public SelectionPreset {
            blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        }

        public int getBlockCount() {
            return blocks.size();
        }

        public String getFormattedTime() {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(createdTime));
        }
    }
}
