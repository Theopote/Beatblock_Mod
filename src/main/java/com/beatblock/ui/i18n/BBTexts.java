package com.beatblock.ui.i18n;

import net.minecraft.client.resource.language.I18n;

/**
 * BeatBlock ImGui UI 字符串本地化：读取 {@code assets/beatblock/lang/*.json}，
 * 跟随 Minecraft 客户端语言设置。
 */
public final class BBTexts {

	private BBTexts() {
	}

	public static String get(String key) {
		return I18n.translate(key);
	}

	public static String get(String key, Object... args) {
		return I18n.translate(key, args);
	}

	/** ImGui 窗口标题：本地化可见标题 + 稳定 ID（### 后缀供停靠/布局识别）。 */
	public static String windowTitle(String titleKey, String stableId) {
		return get(titleKey) + "###" + stableId;
	}

	public static String[] labels(String... keys) {
		String[] result = new String[keys.length];
		for (int i = 0; i < keys.length; i++) {
			result[i] = get(keys[i]);
		}
		return result;
	}

	/** 音频特征轨显示名；未知 key 回退为原 key。 */
	public static String trackName(String key) {
		if (key == null || key.isBlank()) {
			return key != null ? key : "";
		}
		String translationKey = "beatblock.track." + key.toLowerCase();
		String translated = get(translationKey);
		return translated.equals(translationKey) ? key : translated;
	}
}
