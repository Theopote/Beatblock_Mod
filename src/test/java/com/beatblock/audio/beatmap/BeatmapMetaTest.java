package com.beatblock.audio.beatmap;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatmapMetaTest {

	@Test
	void hasStemSeparationWhenModeAndStemsPresent() {
		BeatmapMeta meta = new BeatmapMeta(
			"song.wav", 60000, 120, 0.9, "4/4", 44100,
			"2024-01-01", "1.0", "electronic", "demucs",
			Map.of("drums", "stems/drums.wav")
		);
		assertTrue(meta.hasStemSeparation());
	}

	@Test
	void hasStemSeparationFalseWithoutStemsOrMode() {
		BeatmapMeta noMode = new BeatmapMeta(
			"song.wav", 60000, 120, 0.9, "4/4", 44100,
			"2024-01-01", "1.0", null, null, null);
		BeatmapMeta emptyStems = new BeatmapMeta(
			"song.wav", 60000, 120, 0.9, "4/4", 44100,
			"2024-01-01", "1.0", null, "demucs", Map.of());

		assertFalse(noMode.hasStemSeparation());
		assertFalse(emptyStems.hasStemSeparation());
	}
}
