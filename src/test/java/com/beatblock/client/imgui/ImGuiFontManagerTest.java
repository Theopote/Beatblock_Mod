package com.beatblock.client.imgui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImGuiFontManagerTest {

	@Test
	void langCorpusIncludesChineseFromZhCn() {
		String corpus = ImGuiFontManager.loadLangCorpusForGlyphs();
		assertFalse(corpus.isBlank());
		assertTrue(corpus.contains("偏好"), "zh_cn preferences title chars must bake into glyph atlas");
		assertTrue(corpus.contains("快捷"), "zh_cn shortcut chars must bake into glyph atlas");
		assertTrue(corpus.contains("导出"), "zh_cn export chars must bake into glyph atlas");
	}
}
