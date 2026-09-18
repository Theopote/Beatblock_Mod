package com.beatblock.timeline.generation;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class GenerationReapplyGuardTest {

	@Test
	void bindingMapBlockRequiresConfirmationWhenGeneratedEventsExist() {
		Timeline timeline = Timeline.createDefault();
		String trackId = Timeline.blockAnimationFeatureTrackId("kick");
		timeline.addTrack(new Track(trackId, "kick", TrackType.ANIMATION));
		var clip = TimelineOperations.addClip(timeline, trackId, 0, 4);
		TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of(
			"generatedBy", AnimationBindingEngine.GENERATED_BY_MARK
		));

		assertTrue(GenerationReapplyGuard.requiresConfirmation(
			timeline, GenerationReapplyGuard.Kind.BINDING_MAP_BLOCK));
		assertEquals(1, GenerationReapplyGuard.affectedEventCount(
			timeline, GenerationReapplyGuard.Kind.BINDING_MAP_BLOCK));
	}

	@Test
	void bindingMapAutoSkipsConfirmationOnEmptyTrack() {
		Timeline timeline = Timeline.createDefault();
		assertFalse(GenerationReapplyGuard.requiresConfirmation(
			timeline, GenerationReapplyGuard.Kind.BINDING_MAP_AUTO));
	}

	@Test
	void smartAutoMapCountsReplaceableEventsAcrossTracks() {
		Timeline timeline = Timeline.createDefault();
		var clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 4);
		TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of(
			TimelineGenerationMetadataSupport.PARAM_ORIGIN, TimelineEventOrigin.GENERATED.name(),
			TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID, TimelineGeneratorIds.SMART_AUTOMAP
		));
		assertTrue(GenerationReapplyGuard.requiresConfirmation(
			timeline, GenerationReapplyGuard.Kind.SMART_AUTO_MAP));
		assertEquals(1, GenerationReapplyGuard.affectedEventCount(
			timeline, GenerationReapplyGuard.Kind.SMART_AUTO_MAP));
	}
}
