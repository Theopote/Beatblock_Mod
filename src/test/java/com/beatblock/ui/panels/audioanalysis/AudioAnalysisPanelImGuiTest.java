package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.audio.beatmap.SectionLabel;
import com.beatblock.ui.i18n.BBTexts;
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

	@Test
	void sectionLabelTextMapsKnownLabels() {
		assertEquals(
			BBTexts.get("beatblock.audio.section.label.intro"),
			AudioAnalysisPanelImGui.sectionLabelText(SectionLabel.INTRO)
		);
		assertEquals(
			BBTexts.get("beatblock.audio.section.label.chorus"),
			AudioAnalysisPanelImGui.sectionLabelText(SectionLabel.CHORUS)
		);
		assertEquals(
			BBTexts.get("beatblock.audio.section.label.unknown"),
			AudioAnalysisPanelImGui.sectionLabelText(null)
		);
	}

	@Test
	void formatSectionTimeRangeUsesSeconds() {
		assertEquals(
			BBTexts.get("beatblock.audio.section.time_range", 1.0, 4.5),
			AudioAnalysisPanelImGui.formatSectionTimeRange(1000, 4500)
		);
	}
}
