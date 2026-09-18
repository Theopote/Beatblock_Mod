package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostGenerationFocusHelperTest {

	@Test
	void findEarliestAnimationForTarget_prefersMatchingObject() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, 0, 10);
		TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of("targetObject", "obj-a"));
		TimelineOperations.addEvent(clip, 0.5, EventType.ANIMATION, Map.of("targetObject", "obj-b"));
		TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of("targetObject", "obj-a"));

		TimelineEventRef ref = PostGenerationFocusHelper.findEarliestAnimationForTarget(timeline, "obj-a");
		assertNotNull(ref);
		assertEquals(1.0, ref.event().getTimeSeconds(), 1e-9);
	}

	@Test
	void findEarliestAnimation_returnsEarliestAcrossTracks() {
		Timeline timeline = Timeline.createDefault();
		Clip blockClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, 0, 10);
		Clip autoClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineOperations.addEvent(blockClip, 3.0, EventType.ANIMATION, Map.of());
		TimelineOperations.addEvent(autoClip, 1.5, EventType.ANIMATION, Map.of());

		TimelineEventRef ref = PostGenerationFocusHelper.findEarliestAnimation(timeline);
		assertNotNull(ref);
		assertEquals(1.5, ref.event().getTimeSeconds(), 1e-9);
	}

	@Test
	void findEarliestAnimationForTarget_returnsNullWhenNoMatch() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 0, 10);
		TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of("targetObject", "other"));

		assertNull(PostGenerationFocusHelper.findEarliestAnimationForTarget(timeline, "missing"));
	}
}
