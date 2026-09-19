package com.beatblock.audio.analysis;

import com.beatblock.audio.beatmap.AnchorType;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapMeta;
import com.beatblock.audio.beatmap.MusicSection;
import com.beatblock.audio.beatmap.SectionLabel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeatmapFeatureTimelineAdapterTest {

	@Test
	void adaptsBeatsBandsAndBpmFromBeatmap() {
		Beatmap beatmap = sampleBeatmap();

		AudioFeatureTimeline features = BeatmapFeatureTimelineAdapter.fromBeatmap(beatmap);

		assertNotNull(features);
		assertEquals(16.0, features.getDurationSeconds(), 1e-6);
		assertEquals(120f, features.getBpm(), 1e-3);
		assertFalse(features.getBeats().isEmpty());
		assertFalse(features.getBands().isEmpty());
		assertTrue(features.getBands().stream().anyMatch(b -> b.getLow() > 0f));
		assertTrue(features.getBands().stream().anyMatch(b -> b.getMid() > 0f));
		assertNotNull(features.getBeatGrid());
	}

	public static Beatmap sampleBeatmap() {
		return new Beatmap(
			1,
			new BeatmapMeta("song.wav", 16000, 120, 0.9, "4/4", 44100, "t1", "1", null, null, null),
			List.of(
				new BeatEvent(0, "kick", 0.8f, AnchorType.ARRIVE, 0, 0, 0),
				new BeatEvent(500, "snare", 0.7f, AnchorType.ARRIVE, 1, 0, 1),
				new BeatEvent(1000, "hihat", 0.5f, AnchorType.ARRIVE, 2, 0, 2),
				new BeatEvent(2000, "kick", 0.85f, AnchorType.ARRIVE, 3, 1, 0)
			),
			List.of(
				new MusicSection(0, 8000, SectionLabel.INTRO, 0.6f),
				new MusicSection(8000, 16000, SectionLabel.CHORUS, 0.9f)
			),
			null,
			null
		);
	}
}
