package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchUpdateEventsCommandTest {

	private static Timeline timelineWithEvents(TimelineAnimationEvent... events) {
		Timeline timeline = Timeline.createDefault();
		for (TimelineAnimationEvent e : events) {
			new AddTimelineAnimationEventCommand(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, e).execute();
		}
		return timeline;
	}

	private static List<TimelineAnimationEvent> reload(Timeline timeline) {
		return timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK);
	}

	@Test
	void setEnergyAppliesToAllSelectedEvents() {
		Timeline timeline = timelineWithEvents(
			new TimelineAnimationEvent("a", 1.0, 1.0, "BlockTap", "stage", 0.2f, Map.of()),
			new TimelineAnimationEvent("b", 2.0, 1.0, "BlockTap", "stage", 0.3f, Map.of())
		);
		List<TimelineAnimationEvent> selected = reload(timeline);

		var cmd = new BatchUpdateEventsCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, selected,
			new BatchUpdateEventsCommand.BatchUpdateOptions().setEnergy(0.9f));
		cmd.execute();

		for (TimelineAnimationEvent e : reload(timeline)) {
			assertEquals(0.9f, e.getEnergy(), 1e-6);
		}
	}

	@Test
	void scaleDurationPreservesRelativeDifferences() {
		Timeline timeline = timelineWithEvents(
			new TimelineAnimationEvent("a", 1.0, 1.0, "BlockTap", "stage", 1f, Map.of()),
			new TimelineAnimationEvent("b", 5.0, 2.0, "BlockTap", "stage", 1f, Map.of())
		);
		List<TimelineAnimationEvent> selected = reload(timeline);

		var cmd = new BatchUpdateEventsCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, selected,
			new BatchUpdateEventsCommand.BatchUpdateOptions().scaleDuration(2.0));
		cmd.execute();

		List<TimelineAnimationEvent> updated = reload(timeline).stream()
			.sorted(Comparator.comparingDouble(TimelineAnimationEvent::getTimeSeconds))
			.toList();
		assertEquals(2.0, updated.get(0).getDurationSeconds(), 1e-6);
		assertEquals(4.0, updated.get(1).getDurationSeconds(), 1e-6);
	}

	@Test
	void setDurationForcesAllSelectedToSameValue() {
		Timeline timeline = timelineWithEvents(
			new TimelineAnimationEvent("a", 1.0, 1.0, "BlockTap", "stage", 1f, Map.of()),
			new TimelineAnimationEvent("b", 5.0, 3.0, "BlockTap", "stage", 1f, Map.of())
		);
		List<TimelineAnimationEvent> selected = reload(timeline);

		var cmd = new BatchUpdateEventsCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, selected,
			new BatchUpdateEventsCommand.BatchUpdateOptions().setDuration(0.5));
		cmd.execute();

		for (TimelineAnimationEvent e : reload(timeline)) {
			assertEquals(0.5, e.getDurationSeconds(), 1e-6);
		}
	}

	@Test
	void setParameterChangesBlockTypeWithoutTouchingOtherExtensions() {
		Timeline timeline = timelineWithEvents(
			new TimelineAnimationEvent("a", 1.0, 1.0, "build", "stage", 1f,
				Map.of("placeBlock", "minecraft:diamond_block", "buildMode", "wall"))
		);
		List<TimelineAnimationEvent> selected = reload(timeline);

		var cmd = new BatchUpdateEventsCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, selected,
			new BatchUpdateEventsCommand.BatchUpdateOptions().setParameter("placeBlock", "minecraft:gold_block"));
		cmd.execute();

		TimelineAnimationEvent updated = reload(timeline).getFirst();
		assertEquals("minecraft:gold_block", updated.getParameters().get("placeBlock"));
		assertEquals("wall", updated.getParameters().get("buildMode"));
	}

	@Test
	void setActionModeSwitchesAllSelectedEvents() {
		Timeline timeline = timelineWithEvents(
			new TimelineAnimationEvent("a", 1.0, 1.0, "build", "stage", 1f, Map.of())
		);
		List<TimelineAnimationEvent> selected = reload(timeline);

		var cmd = new BatchUpdateEventsCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, selected,
			new BatchUpdateEventsCommand.BatchUpdateOptions().setActionMode(TimelineAnimationActionMode.PLACE));
		cmd.execute();

		assertEquals(TimelineAnimationActionMode.PLACE, reload(timeline).getFirst().getActionMode());
	}

	@Test
	void undoRestoresExactOriginalValues() {
		Timeline timeline = timelineWithEvents(
			new TimelineAnimationEvent("a", 1.0, 1.0, "BlockTap", "stage", 0.2f,
				Map.of("placeBlock", "minecraft:diamond_block"))
		);
		List<TimelineAnimationEvent> selected = reload(timeline);

		var cmd = new BatchUpdateEventsCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, selected,
			new BatchUpdateEventsCommand.BatchUpdateOptions()
				.setEnergy(0.9f)
				.scaleDuration(3.0)
				.setParameter("placeBlock", "minecraft:gold_block"));
		cmd.execute();
		cmd.undo();

		TimelineAnimationEvent restored = reload(timeline).getFirst();
		assertEquals(0.2f, restored.getEnergy(), 1e-6);
		assertEquals(1.0, restored.getDurationSeconds(), 1e-6);
		assertEquals("minecraft:diamond_block", restored.getParameters().get("placeBlock"));
	}

	@Test
	void eventThatNoLongerExistsIsSkippedNotThrown() {
		Timeline timeline = timelineWithEvents(
			new TimelineAnimationEvent("a", 1.0, 1.0, "BlockTap", "stage", 0.2f, Map.of())
		);
		TimelineAnimationEvent ghost = new TimelineAnimationEvent(
			"does-not-exist", 9.0, 1.0, "BlockTap", "stage", 0.2f, Map.of());

		var cmd = new BatchUpdateEventsCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, List.of(ghost),
			new BatchUpdateEventsCommand.BatchUpdateOptions().setEnergy(0.9f));

		cmd.execute();
		cmd.undo();
		assertEquals(0.2f, reload(timeline).getFirst().getEnergy(), 1e-6);
	}
}
