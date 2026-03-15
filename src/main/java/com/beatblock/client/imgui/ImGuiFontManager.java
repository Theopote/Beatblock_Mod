package com.beatblock.client.imgui;

import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;

/**
 * ImGui 多语言字体：优先系统字体（CJK + 西里尔等），可选内置资源，最后回退默认字体。
 * 目标：支持英文、中文、日韩、西里尔等，方便国际玩家。
 */
public final class ImGuiFontManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiFontManager.class);
	private static final float FONT_SIZE = 16.0f;

	/** 系统字体路径：Windows / macOS / Linux 常见 CJK + 多语言字体 */
	private static final String[] SYSTEM_FONT_PATHS = {
		// Windows
		"C:/Windows/Fonts/msyh.ttc",
		"C:/Windows/Fonts/msyhbd.ttc",
		"C:/Windows/Fonts/simhei.ttf",
		"C:/Windows/Fonts/simsun.ttc",
		"C:/Windows/Fonts/meiryo.ttc",
		"C:/Windows/Fonts/malgun.ttf",
		// macOS
		"/System/Library/Fonts/PingFang.ttc",
		"/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
		"/Library/Fonts/Arial Unicode.ttf",
		// Linux
		"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
		"/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
		"/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
		"/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
	};

	/** 模组内置字体（可选）：放入 assets/beatblock/fonts/ 即可生效 */
	private static final String BUNDLED_FONT_PATH = "/assets/beatblock/fonts/NotoSansSC-Regular.ttf";

	/**
	 * 初始化多语言字体：合并 Default + 中文全量，并尝试加入日/韩/西里尔（若 API 存在）。
	 */
	public static void initializeFonts(ImGuiIO io) {
		ImFontAtlas atlas = io.getFonts();
		try {
			atlas.clear();
		} catch (Throwable t) {
			try { atlas.clearFonts(); } catch (Throwable ignored) {}
			try { atlas.clearTexData(); } catch (Throwable ignored) {}
		}

		short[] glyphRanges = buildMultiLanguageGlyphRanges(io);
		ImFontConfig config = new ImFontConfig();
		config.setPixelSnapH(true);
		config.setOversampleH(2);
		config.setOversampleV(2);

		config.setGlyphRanges(glyphRanges);
		// 1) 优先系统字体
		boolean loaded = tryLoadSystemFonts(io, config);
		// 2) 再试内置资源
		if (!loaded) {
			loaded = tryLoadBundledFont(io, config, glyphRanges);
		}
		// 3) 回退默认（仅拉丁等）
		if (!loaded) {
			LOGGER.warn("[BeatBlock] No CJK system/bundled font found; UI will show ? for Chinese etc.");
			atlas.addFontDefault();
		}

		if (!atlas.isBuilt()) {
			atlas.build();
		}
		config.destroy();
		LOGGER.info("[BeatBlock] ImGui fonts initialized (multi-language support)");
	}

	/** 合并 Default + ChineseFull，并尽量加入 Japanese/Korean/Cyrillic */
	private static short[] buildMultiLanguageGlyphRanges(ImGuiIO io) {
		ImFontAtlas a = io.getFonts();
		try {
			imgui.ImFontGlyphRangesBuilder builder = new imgui.ImFontGlyphRangesBuilder();
			builder.addRanges(a.getGlyphRangesDefault());
			builder.addRanges(a.getGlyphRangesChineseFull());
			tryAddRanges(builder, a, "getGlyphRangesJapanese");
			tryAddRanges(builder, a, "getGlyphRangesKorean");
			tryAddRanges(builder, a, "getGlyphRangesCyrillic");
			tryAddRanges(builder, a, "getGlyphRangesThai");
			tryAddRanges(builder, a, "getGlyphRangesVietnamese");
			return builder.buildRanges();
		} catch (Throwable t) {
			LOGGER.debug("[BeatBlock] GlyphRangesBuilder fallback: ChineseFull only");
			return a.getGlyphRangesChineseFull();
		}
	}

	private static void tryAddRanges(imgui.ImFontGlyphRangesBuilder builder, ImFontAtlas atlas, String methodName) {
		try {
			java.lang.reflect.Method m = atlas.getClass().getMethod(methodName);
			short[] ranges = (short[]) m.invoke(atlas);
			if (ranges != null && ranges.length > 0) {
				builder.addRanges(ranges);
			}
		} catch (Throwable ignored) {}
	}

	private static boolean tryLoadSystemFonts(ImGuiIO io, ImFontConfig config) {
		for (String path : SYSTEM_FONT_PATHS) {
			File f = new File(path);
			if (!f.exists() || !f.canRead()) continue;
			try {
				io.getFonts().addFontFromFileTTF(path, FONT_SIZE, config);
				LOGGER.info("[BeatBlock] Loaded system font: {}", path);
				return true;
			} catch (Throwable e) {
				LOGGER.trace("[BeatBlock] Skip font {}: {}", path, e.getMessage());
			}
		}
		return false;
	}

	private static boolean tryLoadBundledFont(ImGuiIO io, ImFontConfig config, short[] glyphRanges) {
		try (InputStream in = ImGuiFontManager.class.getResourceAsStream(BUNDLED_FONT_PATH)) {
			if (in == null) return false;
			byte[] data = in.readAllBytes();
			if (data.length == 0) return false;
			io.getFonts().addFontFromMemoryTTF(data, FONT_SIZE, config, glyphRanges);
			LOGGER.info("[BeatBlock] Loaded bundled font: {} ({} bytes)", BUNDLED_FONT_PATH, data.length);
			return true;
		} catch (Throwable e) {
			LOGGER.debug("[BeatBlock] No bundled font: {}", e.getMessage());
			return false;
		}
	}
}
