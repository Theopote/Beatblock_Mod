package com.beatblock.timeline.playback;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.command.CommandManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineAutoRepairTest {
	@Test
	void durationRepairIsAppliedThroughUndoableCommandTransaction() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		TimelineEvent event = new TimelineEvent("broken-duration", 1.0, EventType.ANIMATION, Map.of(
			"animationType", "bounce", "targetObject", "stage", "durationSeconds", -2.0));
		clip.addEvent(event);
		TimelineDiagnostic diagnostic = TimelineDiagnostic.error(
			TimelineValidator.RULE_NON_POSITIVE_EVENT_DURATION,
			"Non-positive duration", event.getId(), event.getTimeSeconds());
		TimelineValidationReport report = new TimelineValidationReport(List.of(diagnostic), 1, 0, 0, 0);
		CommandManager commands = new CommandManager();

		TimelineAutoRepair.RepairResult result = TimelineAutoRepair.apply(timeline, report, null, commands);
		assertEquals(List.of("broken-duration"), result.repairedEventIds());
		assertTrue(result.unresolved().isEmpty());
		assertEquals(1.0, ((Number) event.getParameters().get("durationSeconds")).doubleValue());
		assertEquals(1, commands.undoCount());

		commands.undo();
		assertEquals(-2.0, ((Number) event.getParameters().get("durationSeconds")).doubleValue());
		commands.redo();
		assertEquals(1.0, ((Number) event.getParameters().get("durationSeconds")).doubleValue());
	}

	@Test
	void unsafeDiagnosticsRemainExplicitlyUnresolved() {
		Timeline timeline = Timeline.createDefault();
		TimelineDiagnostic diagnostic = TimelineDiagnostic.error(
			TimelineValidator.RULE_MISSING_ANIMATION_PRESET,
			"Missing preset", "missing", 1.0);
		TimelineValidationReport report = new TimelineValidationReport(List.of(diagnostic), 1, 0, 0, 0);
		CommandManager commands = new CommandManager();

		TimelineAutoRepair.RepairResult result = TimelineAutoRepair.apply(timeline, report, null, commands);
		assertTrue(result.repairedEventIds().isEmpty());
		assertEquals(List.of(diagnostic), result.unresolved());
		assertEquals(0, commands.undoCount());
	}
}