package com.beatblock.timeline.rendering;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class TimelineAnimationFeatureMapperTest {

	@Test
	void selectAnimationRuleReturnsKickRuleForDemucsBlockTrack() {
		TimelineAnimationFeatureMapper.AnimationMappingRule rule =
			TimelineAnimationFeatureMapper.selectAnimationRule("kick", true, true);

		assertNotNull(rule);
		assertEquals("bounce", rule.animationType());
		assertEquals("drums", rule.sourceStem());
	}

	@Test
	void shouldUseSustainGenerationForDemucsBlockBassInMixedMode() {
		assertTrue(TimelineAnimationFeatureMapper.shouldUseSustainGeneration("bass", true, true, "mixed"));
		assertFalse(TimelineAnimationFeatureMapper.shouldUseSustainGeneration("kick", true, true, "mixed"));
		assertFalse(TimelineAnimationFeatureMapper.shouldUseSustainGeneration("bass", true, true, "trigger"));
	}

	@Test
	void isGeneratedMappingAnimationEventDetectsDropMarker() {
		TimelineEvent event = TimelineOperations.addEvent(
			TimelineOperations.addClip(Timeline.createDefault(), Timeline.TRACK_ID_ANIMATION_AUTO, 0, 5),
			1.0,
			EventType.ANIMATION,
			Map.of("generatedBy", "audio-asset-drop-trigger")
		);

		assertTrue(TimelineAnimationFeatureMapper.isGeneratedMappingAnimationEvent(event));
		assertFalse(TimelineAnimationFeatureMapper.isGeneratedMappingAnimationEvent(
			TimelineOperations.addEvent(
				TimelineOperations.addClip(Timeline.createDefault(), Timeline.TRACK_ID_ANIMATION_AUTO, 0, 5),
				2.0,
				EventType.ANIMATION,
				Map.of()
			)
		));
	}

	@Test
	void populateFromAudioFeaturesMapsKickToBlockFeatureTrack() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("separationMode", "demucs");
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.9f));

		TimelineAnimationFeatureMapper.populateFromAudioFeatures(
			timeline, TimelineTrackMeta.ROW_ANIM_BLOCK, () -> "stage-a");

		String featureTrackId = Timeline.blockAnimationFeatureTrackId("kick");
		assertNotNull(timeline.getTrack(featureTrackId));
		assertFalse(timeline.getAnimationEvents(featureTrackId).isEmpty());
		TimelineAnimationEvent ev = timeline.getAnimationEvents(featureTrackId).getFirst();
		assertEquals("stage-a", ev.getTargetObjectId());
		assertEquals(1.0, ev.getTimeSeconds(), 1e-6);
	}
}
