package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddTimelineAnimationEventCommandTest {

	@Test
	void buildParamsIncludesCoreFields() {
		var event = new TimelineAnimationEvent(
			"ev1", 2.0, 1.5, "build", "stage-a", 0.7f,
			Map.of("buildMode", "wall", "eventOrigin", TimelineEventOrigin.MANUAL.name()));

		Map<String, Object> params = AddTimelineAnimationEventCommand.buildParams(event);

		assertEquals(TimelineAnimationActionMode.ANIMATE.name(), params.get("actionMode"));
		assertEquals("build", params.get("animationType"));
		assertEquals("stage-a", params.get("targetObject"));
		assertEquals(0.7f, ((Number) params.get("energy")).floatValue(), 1e-6);
		assertEquals(1.5, ((Number) params.get("durationSeconds")).doubleValue(), 1e-9);
		assertEquals("wall", params.get("buildMode"));
		assertEquals("MANUAL", params.get("eventOrigin"));
	}

	@Test
	void executeAndUndoManageClipLifecycle() {
		Timeline timeline = Timeline.createDefault();
		var event = new TimelineAnimationEvent(
			"ev1", 4.0, 2.0, "pulse", "stage", 1f, Map.of());

		AddTimelineAnimationEventCommand command = new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, event);
		command.execute();
		assertEquals(1, timeline.getBlockAnimationEvents().size());
		assertEquals(4.0, timeline.getBlockAnimationEvents().getFirst().getTimeSeconds(), 1e-9);

		command.undo();
		assertTrue(timeline.getBlockAnimationEvents().isEmpty());
	}
}
