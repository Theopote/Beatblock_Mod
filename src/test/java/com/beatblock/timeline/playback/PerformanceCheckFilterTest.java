package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceCheckFilterTest {

	@AfterEach
	void tearDown() {
		PerformanceCheckController.clear();
	}

	@Test
	void filteredProblemsRespectsSeverityMode() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(5);
		// unbound → warning; missing preset → error
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"w", 1.0, 1.0, "Pulse", "", 1f,
			Map.of("animationType", "Pulse", "targetObject", "", "durationSeconds", 1.0)));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"e", 2.0, 1.0, "NoSuchPresetXYZ", "t", 1f,
			Map.of("animationType", "NoSuchPresetXYZ", "targetObject", "t", "durationSeconds", 1.0)));

		BlockAnimationEngine engine = new BlockAnimationEngine();
		PerformanceCheckController.checkOnly(timeline, engine, null);
		assertTrue(PerformanceCheckController.lastReport().hasErrors());
		assertTrue(PerformanceCheckController.lastReport().hasWarnings());

		PerformanceCheckController.setProblemFilterMode(PerformanceCheckController.FILTER_ALL);
		int all = PerformanceCheckController.filteredProblems().size();
		assertTrue(all >= 2);

		PerformanceCheckController.setProblemFilterMode(PerformanceCheckController.FILTER_ERRORS);
		assertTrue(PerformanceCheckController.filteredProblems().stream()
			.allMatch(d -> d.severity() == TimelineDiagnosticSeverity.ERROR));
		assertEquals(
			PerformanceCheckController.lastReport().errorCount(),
			PerformanceCheckController.filteredProblems().size()
		);

		PerformanceCheckController.setProblemFilterMode(PerformanceCheckController.FILTER_WARNINGS);
		assertTrue(PerformanceCheckController.filteredProblems().stream()
			.allMatch(d -> d.severity() == TimelineDiagnosticSeverity.WARNING));
	}
}
