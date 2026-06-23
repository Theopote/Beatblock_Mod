package com.beatblock.timeline.rendering;

import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.WaveformData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackRegistryTest {

	@Test
	void localizedNameMapsKnownRhythmKeys() {
		assertEquals("底鼓", TrackRegistry.localizedName("kick"));
		assertEquals("军鼓", TrackRegistry.localizedName("SNARE"));
		assertEquals("custom_stem", TrackRegistry.localizedName("custom_stem"));
	}

	@Test
	void buildAudioSubTracksIncludesWaveformAndOrderedFeatureRows() {
		Timeline timeline = Timeline.createDefault();
		timeline.setWaveform(new WaveformData(new float[]{0.5f}, 1.0, 44100));
		timeline.addFeatureEvent("hihat", new FeatureEvent(0.5, 0.4f));
		timeline.addFeatureEvent("kick", new FeatureEvent(0.0, 0.9f));
		timeline.addFeatureEvent("bass", new FeatureEvent(1.0, 0.6f));

		var tracks = TrackRegistry.buildAudioSubTracks(timeline);

		assertFalse(tracks.isEmpty());
		assertEquals("waveform", tracks.getFirst().getKey());
		assertEquals(TrackDefinition.VisualType.WAVEFORM, tracks.getFirst().getVisualType());
		assertEquals("kick", tracks.get(1).getKey());
		assertEquals("hihat", tracks.get(2).getKey());
		assertEquals("bass", tracks.get(3).getKey());
	}

	@Test
	void buildBlockAnimationControlTracksFollowsRhythmThenMelodicOrder() {
		Timeline timeline = Timeline.createDefault();
		timeline.addTrack(new Track(
			Timeline.blockAnimationFeatureTrackId("bass"),
			"Bass",
			TrackType.ANIMATION
		));
		timeline.addTrack(new Track(
			Timeline.blockAnimationFeatureTrackId("kick"),
			"Kick",
			TrackType.ANIMATION
		));
		timeline.addTrack(new Track(
			Timeline.blockAnimationFeatureTrackId("custom"),
			"Custom",
			TrackType.ANIMATION
		));

		var tracks = TrackRegistry.buildBlockAnimationControlTracks(timeline);

		assertEquals(3, tracks.size());
		assertEquals("kick", Timeline.blockAnimationFeatureKeyFromTrackId(tracks.get(0).getKey()));
		assertEquals("bass", Timeline.blockAnimationFeatureKeyFromTrackId(tracks.get(1).getKey()));
		assertEquals("custom", Timeline.blockAnimationFeatureKeyFromTrackId(tracks.get(2).getKey()));
		assertEquals(TrackDefinition.VisualType.ANIMATION_CLIP, tracks.get(0).getVisualType());
	}

	@Test
	void buildAudioSubTracksReturnsEmptyForNullTimeline() {
		assertTrue(TrackRegistry.buildAudioSubTracks(null).isEmpty());
		assertTrue(TrackRegistry.buildBlockAnimationControlTracks(null).isEmpty());
	}
}
