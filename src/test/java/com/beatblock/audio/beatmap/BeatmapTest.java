package com.beatblock.audio.beatmap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BeatmapTest {

	private Beatmap sampleBeatmap() {
		return new Beatmap(
			1,
			new BeatmapMeta("song.wav", 120000, 120, 1.0, "4/4", 44100, "", "", null, null, null),
			List.of(
				new BeatEvent(500, "kick", 0.8f, AnchorType.ARRIVE, 0, 0, 0),
				new BeatEvent(1500, "snare", 0.6f, AnchorType.ARRIVE, 1, 0, 1),
				new BeatEvent(2500, "hihat", 0.4f, AnchorType.DEPART, 2, 0, 2)
			),
			List.of(new MusicSection(0, 60000, SectionLabel.INTRO, 0.3f)),
			null,
			null
		);
	}

	@Test
	void beatsForBandFiltersByLegacyBandMapping() {
		Beatmap beatmap = sampleBeatmap();
		assertEquals(1, beatmap.beatsForBand(FrequencyBand.LOW).size());
		assertEquals(1, beatmap.beatsForBand(FrequencyBand.MID).size());
		assertEquals(1, beatmap.beatsForBand(FrequencyBand.HIGH).size());
	}

	@Test
	void beatsInRangeIsHalfOpenInterval() {
		Beatmap beatmap = sampleBeatmap();
		assertEquals(2, beatmap.beatsInRange(500, 2000).size());
		assertEquals(0, beatmap.beatsInRange(2600, 3000).size());
	}

	@Test
	void sectionAtFindsContainingSection() {
		Beatmap beatmap = sampleBeatmap();
		assertNotNullSection(beatmap.sectionAt(1000));
		assertNull(beatmap.sectionAt(70000));
	}

	private static void assertNotNullSection(MusicSection section) {
		assertEquals(SectionLabel.INTRO, section.label());
	}
}
