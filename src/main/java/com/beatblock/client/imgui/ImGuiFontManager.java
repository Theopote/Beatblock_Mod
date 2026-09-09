package com.beatblock.client.imgui;

import com.beatblock.timeline.rendering.TimelineLayout;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGuiIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ImGui 多语言字体：优先系统字体（CJK + 西里尔等），可选内置资源，最后回退默认字体。
 * <p>
 * 中文显示为「？」通常是字形未烘焙进 ImGui 图集（范围过窄），不是 lang JSON 编码损坏。
 * 因此除 ChineseSimplifiedCommon 外，会把 {@code lang/*.json} 实际用字 {@code addText} 进图集。
 */
public final class ImGuiFontManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiFontManager.class);
	private static final float FONT_SIZE = 16.0f;

	/** 纯图标按钮用字号，与轨道行高一致，使字形高度接近方形按钮边长。 */
	public static final float ICON_BUTTON_FONT_PX = TimelineLayout.ROW_HEIGHT;

	/** 独立烘焙的 BeatBlock 图标字体（仅私用区），供方形图标按钮 {@code pushFont}；可能为 null。 */
	private static ImFont iconButtonFont;

	/** 系统字体路径：Windows / macOS / Linux 常见 CJK + 多语言字体 */
	private static final String[] SYSTEM_FONT_PATHS = {
		"C:/Windows/Fonts/msyh.ttc",
		"C:/Windows/Fonts/msyhbd.ttc",
		"C:/Windows/Fonts/simhei.ttf",
		"C:/Windows/Fonts/simsun.ttc",
		"C:/Windows/Fonts/meiryo.ttc",
		"C:/Windows/Fonts/malgun.ttf",
		"/System/Library/Fonts/PingFang.ttc",
		"/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
		"/Library/Fonts/Arial Unicode.ttf",
		"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
		"/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
		"/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
		"/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
	};

	/** 模组内置字体（系统字体不可用时） */
	private static final String[] BUNDLED_FONT_PATHS = {
		"/assets/beatblock/fonts/NotoSansSC-Regular.ttf",
		"/assets/beatblock/fonts/SimHei.ttf",
	};

	private static final String ICON_FONT_PATH = "/assets/beatblock/fonts/BeatBlock.ttf";

	/** 语言包语料：保证 Preferences / Export 等新增中文也能进图集 */
	private static final List<String> LANG_CORPUS_PATHS = List.of(
		"/assets/beatblock/lang/zh_cn.json",
		"/assets/beatblock/lang/en_us.json"
	);

	private static final short[] ICON_GLYPH_RANGES = { (short) 0xF000, (short) 0xF500, 0 };

	public static void initializeFonts(ImGuiIO io) {
		ImFontAtlas atlas = io.getFonts();
		try {
			atlas.clear();
		} catch (Throwable t) {
			try { atlas.clearFonts(); } catch (Throwable e) {
				LOGGER.debug("ImGui font atlas clearFonts failed during reset", e);
			}
			try { atlas.clearTexData(); } catch (Throwable e) {
				LOGGER.debug("ImGui font atlas clearTexData failed during reset", e);
			}
		}
		iconButtonFont = null;

		short[] glyphRanges = buildMultiLanguageGlyphRanges(io);
		ImFontConfig config = new ImFontConfig();
		config.setPixelSnapH(true);
		config.setOversampleH(2);
		config.setOversampleV(2);
		config.setGlyphRanges(glyphRanges);

		boolean loaded = tryLoadSystemFonts(io, config);
		if (!loaded) {
			loaded = tryLoadBundledFonts(io, config, glyphRanges);
		}
		if (!loaded) {
			LOGGER.warn("[BeatBlock] No CJK system/bundled font found; UI will show ? for Chinese etc.");
			atlas.addFontDefault();
		}

		tryLoadIconFontMerged(atlas);
		tryLoadIconButtonFontStandalone(atlas);

		if (!atlas.isBuilt()) {
			atlas.build();
		}
		config.destroy();
		LOGGER.info("[BeatBlock] ImGui fonts initialized (multi-language support)");
	}

	public static ImFont getIconButtonFont() {
		return iconButtonFont;
	}

	/**
	 * Common 范围 + lang 实际用字 + 标点小块。避免 ChineseFull 撑爆图集，同时减少漏字「？」。
	 */
	private static short[] buildMultiLanguageGlyphRanges(ImGuiIO io) {
		ImFontAtlas a = io.getFonts();
		try {
			imgui.ImFontGlyphRangesBuilder builder = new imgui.ImFontGlyphRangesBuilder();
			builder.addRanges(a.getGlyphRangesDefault());
			try {
				builder.addRanges(a.getGlyphRangesChineseSimplifiedCommon());
			} catch (Throwable ignored) {
				builder.addRanges(a.getGlyphRangesChineseFull());
			}
			builder.addRanges(CJK_PUNCT_AND_KANA);
			String langCorpus = loadLangCorpusForGlyphs();
			if (!langCorpus.isEmpty()) {
				builder.addText(langCorpus);
			}
			builder.addText(HARDCODED_UI_CJK_CORPUS);
			tryAddRanges(builder, a, "getGlyphRangesJapanese");
			tryAddRanges(builder, a, "getGlyphRangesKorean");
			tryAddRanges(builder, a, "getGlyphRangesCyrillic");
			tryAddRanges(builder, a, "getGlyphRangesThai");
			tryAddRanges(builder, a, "getGlyphRangesVietnamese");
			return builder.buildRanges();
		} catch (Throwable t) {
			LOGGER.debug("[BeatBlock] GlyphRangesBuilder fallback", t);
			return buildFallbackRanges(a);
		}
	}

	/** 包可见：单测确认语料可读且含中文。 */
	static String loadLangCorpusForGlyphs() {
		StringBuilder sb = new StringBuilder(64 * 1024);
		for (String path : LANG_CORPUS_PATHS) {
			try (InputStream in = ImGuiFontManager.class.getResourceAsStream(path)) {
				if (in == null) {
					LOGGER.debug("[BeatBlock] Lang corpus missing: {}", path);
					continue;
				}
				sb.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			} catch (Throwable e) {
				LOGGER.warn("[BeatBlock] Failed to load lang corpus {}", path, e);
			}
		}
		return sb.toString();
	}

	private static final short[] CJK_PUNCT_AND_KANA = {
		(short) 0x2000, (short) 0x206F,
		(short) 0x3000, (short) 0x303F,
		(short) 0x3040, (short) 0x309F,
		(short) 0x30A0, (short) 0x30FF,
		(short) 0xFF00, (short) 0xFFEF,
		0
	};

	/** 仍写在 Java 源码里、可能不经 lang JSON 的中文兜底。 */
	private static final String HARDCODED_UI_CJK_CORPUS =
		"工具时间线事件属性动画库导入音乐智能映射设置确定取消打开保存新建编辑删除复制粘贴撤销重做播放暂停频段低频中频高频" +
			"展开子轨道折叠子轨道展开子选项折叠子选项展开详情面板折叠详情展开详情" +
			"音频波形方块自动摄像机烘焙绑定映射自动吸附节拍网格磁吸循环入点出点清除缩放适应轨道轨高重置回滚预览持久天降规则细节混合触发持续片段预设" +
			"导出视频帧率分辨率混入编码渲染就绪输出路径范围起点终点隐藏编辑器状态路径无法帧离线逐方块动画与镜头不会录制界面或原版" +
			"魔棒连通减选求交图层跳过整列切片笔刷套索框选线选主混音建造还原节奏特征";

	private static short[] buildFallbackRanges(ImFontAtlas a) {
		try {
			imgui.ImFontGlyphRangesBuilder b = new imgui.ImFontGlyphRangesBuilder();
			b.addRanges(a.getGlyphRangesDefault());
			try {
				b.addRanges(a.getGlyphRangesChineseSimplifiedCommon());
			} catch (Throwable ignored) {
				b.addRanges(a.getGlyphRangesChineseFull());
			}
			b.addRanges(CJK_PUNCT_AND_KANA);
			String corpus = loadLangCorpusForGlyphs();
			if (!corpus.isEmpty()) {
				b.addText(corpus);
			}
			return b.buildRanges();
		} catch (Throwable t2) {
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
		} catch (Throwable e) {
			LOGGER.trace("ImFontAtlas.{} unavailable", methodName, e);
		}
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

	private static boolean tryLoadBundledFonts(ImGuiIO io, ImFontConfig config, short[] glyphRanges) {
		for (String path : BUNDLED_FONT_PATHS) {
			try (InputStream in = ImGuiFontManager.class.getResourceAsStream(path)) {
				if (in == null) continue;
				byte[] data = in.readAllBytes();
				if (data.length == 0) continue;
				io.getFonts().addFontFromMemoryTTF(data, FONT_SIZE, config, glyphRanges);
				LOGGER.info("[BeatBlock] Loaded bundled font: {} ({} bytes)", path, data.length);
				return true;
			} catch (Throwable e) {
				LOGGER.debug("[BeatBlock] Bundled font skip {}: {}", path, e.getMessage());
			}
		}
		return false;
	}

	private static boolean tryLoadIconFontMerged(ImFontAtlas atlas) {
		try (InputStream in = ImGuiFontManager.class.getResourceAsStream(ICON_FONT_PATH)) {
			if (in == null) {
				LOGGER.debug("[BeatBlock] No icon font found: {}", ICON_FONT_PATH);
				return false;
			}
			byte[] data = in.readAllBytes();
			if (data.length == 0) return false;

			ImFontConfig iconConfig = new ImFontConfig();
			iconConfig.setMergeMode(true);
			iconConfig.setPixelSnapH(true);
			iconConfig.setOversampleH(2);
			iconConfig.setOversampleV(2);

			atlas.addFontFromMemoryTTF(data, FONT_SIZE, iconConfig, ICON_GLYPH_RANGES);
			iconConfig.destroy();
			LOGGER.info("[BeatBlock] Loaded icon font (merged): {} ({} bytes)", ICON_FONT_PATH, data.length);
			return true;
		} catch (Throwable e) {
			LOGGER.debug("[BeatBlock] Load icon font failed: {}", e.getMessage());
			return false;
		}
	}

	private static void tryLoadIconButtonFontStandalone(ImFontAtlas atlas) {
		iconButtonFont = null;
		try (InputStream in = ImGuiFontManager.class.getResourceAsStream(ICON_FONT_PATH)) {
			if (in == null) return;
			byte[] data = in.readAllBytes();
			if (data.length == 0) return;

			ImFontConfig cfg = new ImFontConfig();
			cfg.setMergeMode(false);
			cfg.setPixelSnapH(true);
			cfg.setOversampleH(2);
			cfg.setOversampleV(2);

			iconButtonFont = atlas.addFontFromMemoryTTF(data, ICON_BUTTON_FONT_PX, cfg, ICON_GLYPH_RANGES);
			cfg.destroy();
			LOGGER.info("[BeatBlock] Loaded icon button font: {} px={}", ICON_FONT_PATH, ICON_BUTTON_FONT_PX);
		} catch (Throwable e) {
			LOGGER.debug("[BeatBlock] Icon button font skipped: {}", e.getMessage());
		}
	}
}
