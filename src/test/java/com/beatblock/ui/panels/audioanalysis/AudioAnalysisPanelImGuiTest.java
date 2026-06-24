package com.beatblock.ui.panels.audioanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioAnalysisPanelImGuiTest {

	@Test
	void collapseTextTruncatesLongPaths() {
		String longPath = "C:/very/long/path/to/audio/file/name/that/exceeds/limit/song.mp3";
		String collapsed = AudioAnalysisPanelImGui.collapseText(longPath);
		assertTrue(collapsed.length() <= AudioAnalysisPanelImGui.COLLAPSED_TEXT_MAX_CHARS);
		assertTrue(collapsed.contains("…"));
	}

	@Test
	void shouldCollapseValueForWindowsPath() {
		assertTrue(AudioAnalysisPanelImGui.shouldCollapseValue("C:\\music\\song.wav"));
		assertFalse(AudioAnalysisPanelImGui.shouldCollapseValue("short"));
	}

	@Test
	void decodePayloadStripsNullTerminator() {
		assertEquals("asset-id", AudioAnalysisPanelImGui.decodePayloadText("asset-id\u0000extra".getBytes()));
	}
}
