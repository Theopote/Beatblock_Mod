package com.beatblock.video;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 视频导出预设：常用导出配置的快捷方式。
 * <p>
 * 提供 YouTube、4K、社交媒体等常见平台的推荐设置。
 */
public final class VideoExportPresets {

    private VideoExportPresets() {}

    /**
     * 预设类型。
     */
    public enum PresetType {
        YOUTUBE_1080P("YouTube 1080p (推荐)", "适用于大部分 YouTube 视频", 1920, 1080, 60),
        YOUTUBE_720P("YouTube 720p", "较小文件，上传快", 1280, 720, 60),
        YOUTUBE_4K("YouTube 4K", "最高画质，需要强大硬件", 3840, 2160, 60),
        BILIBILI_1080P("Bilibili 1080p", "B站推荐设置", 1920, 1080, 60),
        TIKTOK_VERTICAL("TikTok 竖屏", "抖音/TikTok 竖屏视频", 1080, 1920, 30),
        INSTAGRAM_SQUARE("Instagram 方形", "Instagram 方形视频", 1080, 1080, 30),
        TWITTER_720P("Twitter 720p", "推特视频", 1280, 720, 30),
        CUSTOM("自定义", "自定义分辨率和帧率", 1920, 1080, 60);

        private final String displayName;
        private final String description;
        private final int width;
        private final int height;
        private final int fps;

        PresetType(String displayName, String description, int width, int height, int fps) {
            this.displayName = displayName;
            this.description = description;
            this.width = width;
            this.height = height;
            this.fps = fps;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getFps() { return fps; }
    }

    /**
     * 从预设创建导出设置。
     *
     * @param preset 预设类型
     * @param outputPath 输出路径
     * @param startTime 开始时间（秒）
     * @param endTime 结束时间（秒）
     * @param includeAudio 是否包含音频
     * @return 导出设置
     */
    public static VideoExportSettings fromPreset(
        PresetType preset,
        Path outputPath,
        double startTime,
        double endTime,
        boolean includeAudio
    ) {
        return new VideoExportSettings(
            outputPath,
            preset.getWidth(),
            preset.getHeight(),
            preset.getFps(),
            startTime,
            endTime,
            includeAudio
        );
    }

    /**
     * 下拉框顺序：全部平台预设 + 自定义。
     */
    public static PresetType[] comboPresets() {
        List<PresetType> presets = new ArrayList<>(getAllPresets());
        presets.add(PresetType.CUSTOM);
        return presets.toArray(PresetType[]::new);
    }

    /**
     * 预设对应的 i18n 标签键（{@code beatblock.export.preset.<name>}）。
     */
    public static String labelKey(PresetType preset) {
        return "beatblock.export.preset." + preset.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 预设对应的 i18n 描述键。
     */
    public static String descriptionKey(PresetType preset) {
        return labelKey(preset) + ".desc";
    }

    public static int indexOf(PresetType preset) {
        PresetType[] all = comboPresets();
        for (int i = 0; i < all.length; i++) {
            if (all[i] == preset) {
                return i;
            }
        }
        return 0;
    }

    public static PresetType presetAtIndex(int index) {
        PresetType[] all = comboPresets();
        if (index < 0 || index >= all.length) {
            return getRecommended();
        }
        return all[index];
    }

    /**
     * 获取所有可用预设（不含自定义）。
     */
    public static List<PresetType> getAllPresets() {
        List<PresetType> presets = new ArrayList<>();
        for (PresetType preset : PresetType.values()) {
            if (preset != PresetType.CUSTOM) {
                presets.add(preset);
            }
        }
        return Collections.unmodifiableList(presets);
    }

    /**
     * 根据名称查找预设。
     */
    public static @Nullable PresetType findByName(String name) {
        if (name == null) return null;
        for (PresetType preset : PresetType.values()) {
            if (preset.name().equalsIgnoreCase(name) ||
                preset.getDisplayName().equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * 获取推荐预设（YouTube 1080p）。
     */
    public static PresetType getRecommended() {
        return PresetType.YOUTUBE_1080P;
    }

    /**
     * 验证自定义分辨率是否合理。
     *
     * @param width 宽度
     * @param height 高度
     * @return 错误消息，如果有效返回 null
     */
    public static @Nullable String validateResolution(int width, int height) {
        if (width < 128 || height < 128) {
            return "分辨率过小（最小 128×128）";
        }
        if (width > 7680 || height > 4320) {
            return "分辨率过大（最大 8K: 7680×4320）";
        }
        if (width % 2 != 0 || height % 2 != 0) {
            return "宽度和高度必须为偶数（H.264 要求）";
        }
        long pixels = (long) width * height;
        if (pixels > 33177600) { // 8K
            return "像素数过多（最大 33M 像素）";
        }
        return null;
    }

    /**
     * 验证帧率是否合理。
     *
     * @param fps 帧率
     * @return 错误消息，如果有效返回 null
     */
    public static @Nullable String validateFps(int fps) {
        if (fps < 1 || fps > 120) {
            return "帧率超出范围（1-120 fps）";
        }
        return null;
    }

    /**
     * 估算文件大小（MB）。
     *
     * @param width 宽度
     * @param height 高度
     * @param fps 帧率
     * @param durationSeconds 时长（秒）
     * @return 估算的文件大小（MB）
     */
    public static double estimateFileSize(int width, int height, int fps, double durationSeconds) {
        // 粗略估算：假设 H.264 压缩率为每像素每帧 0.1 bit
        long pixels = (long) width * height;
        double totalFrames = durationSeconds * fps;
        double bits = pixels * totalFrames * 0.1;
        double bytes = bits / 8.0;
        return bytes / (1024.0 * 1024.0); // 转换为 MB
    }
}
