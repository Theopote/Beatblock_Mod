package com.beatblock.audio.beatmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeatmapMusicSectionTest {

	@Test
	void durationMsIsEndMinusStart() {
		MusicSection section = new MusicSection(1000, 4500, SectionLabel.CHORUS, 0.7f);
		assertEquals(3500, section.durationMs());
		assertEquals(SectionLabel.CHORUS, section.label());
		assertEquals(0.7f, section.energyMean(), 1e-6f);
	}
}
