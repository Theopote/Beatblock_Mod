package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.FrequencyBands;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmClassifierTest {

	@Test
	void returnsEmptyListForNullBeats() {
		assertTrue(RhythmClassifier.classify(null, List.of()).isEmpty());
	}

	@Test
	void defaultsToKickWhenNoBandData() {
		List<RhythmEvent> events = RhythmClassifier.classify(
			List.of(new DetectedBeat(1.0, 0.8f)),
			List.of()
		);
		assertEquals(1, events.size());
		assertEquals(RhythmType.KICK, events.getFirst().getType());
	}

	@Test
	void classifiesLowDominantBandsAsKick() {
		List<RhythmEvent> events = RhythmClassifier.classify(
			List.of(new DetectedBeat(2.0, 0.7f)),
			List.of(new FrequencyBands(2.0, 0.8f, 0.1f, 0.1f))
		);
		assertEquals(RhythmType.KICK, events.getFirst().getType());
	}

	@Test
	void classifiesMidDominantBandsAsSnare() {
		List<RhythmEvent> events = RhythmClassifier.classify(
			List.of(new DetectedBeat(3.0, 0.6f)),
			List.of(new FrequencyBands(3.0, 0.1f, 0.7f, 0.2f))
		);
		assertEquals(RhythmType.SNARE, events.getFirst().getType());
	}

	@Test
	void classifiesHighDominantBandsAsHiHat() {
		List<RhythmEvent> events = RhythmClassifier.classify(
			List.of(new DetectedBeat(4.0, 0.5f)),
			List.of(new FrequencyBands(4.0, 0.1f, 0.2f, 0.7f))
		);
		assertEquals(RhythmType.HIHAT, events.getFirst().getType());
	}

	@Test
	void picksClosestBandFrameByTime() {
		List<RhythmEvent> events = RhythmClassifier.classify(
			List.of(new DetectedBeat(1.5, 0.9f)),
			List.of(
				new FrequencyBands(1.0, 0.9f, 0.05f, 0.05f),
				new FrequencyBands(3.0, 0.05f, 0.05f, 0.9f)
			)
		);
		assertEquals(RhythmType.KICK, events.getFirst().getType());
	}
}
