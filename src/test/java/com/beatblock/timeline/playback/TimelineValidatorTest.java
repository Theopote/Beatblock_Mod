package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimelineValidatorTest {

	@Test
	void cleanTimelineWithValidEventHasNoErrorsOrWarnings() throws Exception {
		java.nio.file.Path audio = java.nio.file.Files.createTempFile("bb-audio", ".wav");
		try {
			Timeline timeline = Timeline.createDefault();
			timeline.setDurationSeconds(10);
			timeline.setMetadata("audioPath", audio.toString());
			BlockAnimationEngine engine = new BlockAnimationEngine();
			String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
			engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
				"stage-a", "Stage A", List.of(new BlockPos(0, 64, 0))));
			timeline.addAutoAnimationEvent(event("ev1", 1.0, animId, "stage-a"));

			TimelineValidationReport report = TimelineValidator.validate(timeline, engine);
			assertFalse(report.hasErrors());
			assertFalse(report.hasWarnings(), () -> report.problems().toString());
			assertEquals(1, report.animationEventCount());
			assertTrue(report.infoCount() >= 3);
		} finally {
			java.nio.file.Files.deleteIfExists(audio);
		}
	}

	@Test
	void unboundTargetIsWarningNotError() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		timeline.setMetadata("audioPath", "x.wav");
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		timeline.addAutoAnimationEvent(event("u1", 0.5, animId, ""));

		TimelineValidationReport report = TimelineValidator.validate(timeline, engine);
		assertFalse(report.hasErrors());
		assertTrue(report.hasWarnings());
		assertTrue(report.problems().stream()
			.anyMatch(d -> TimelineValidator.RULE_UNBOUND_TARGET.equals(d.ruleId())));
	}

	@Test
	void missingPresetIsError() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		timeline.setMetadata("audioPath", "x.wav");
		BlockAnimationEngine engine = new BlockAnimationEngine();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage-a", "Stage A", List.of(new BlockPos(1, 64, 0))));
		timeline.addAutoAnimationEvent(event("bad", 1.0, "DefinitelyNotARealPreset", "stage-a"));

		TimelineValidationReport report = TimelineValidator.validate(timeline, engine);
		assertTrue(report.hasErrors());
		assertTrue(report.problems().stream()
			.anyMatch(d -> TimelineValidator.RULE_MISSING_ANIMATION_PRESET.equals(d.ruleId())));
	}

	@Test
	void duplicateEventIdsAreErrors() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		timeline.setMetadata("audioPath", "x.wav");
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage-a", "Stage A", List.of(new BlockPos(2, 64, 0))));
		// addAutoAnimationEvent assigns unique TimelineEvent ids; inject duplicates at clip level
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = com.beatblock.timeline.TimelineOperations.addClip(track, 0, 10);
		Map<String, Object> params = Map.of(
			"animationType", animId,
			"targetObject", "stage-a",
			"durationSeconds", 1.0
		);
		clip.addEvent(new com.beatblock.timeline.TimelineEvent(
			"dup-id", 1.0, com.beatblock.timeline.EventType.ANIMATION, params));
		clip.addEvent(new com.beatblock.timeline.TimelineEvent(
			"dup-id", 2.0, com.beatblock.timeline.EventType.ANIMATION, params));

		TimelineValidationReport report = TimelineValidator.validate(timeline, engine);
		assertTrue(report.hasErrors());
		assertTrue(report.problems().stream()
			.anyMatch(d -> TimelineValidator.RULE_DUPLICATE_EVENT_ID.equals(d.ruleId())));
	}

	@Test
	void eventOutsideTimelineIsWarning() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(5);
		timeline.setMetadata("audioPath", "x.wav");
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage-a", "Stage A", List.of(new BlockPos(3, 64, 0))));
		timeline.addAutoAnimationEvent(event("late", 12.0, animId, "stage-a"));

		TimelineValidationReport report = TimelineValidator.validate(timeline, engine);
		assertTrue(report.hasWarnings());
		assertTrue(report.problems().stream()
			.anyMatch(d -> TimelineValidator.RULE_EVENT_OUTSIDE_TIMELINE.equals(d.ruleId())));
	}

	@Test
	void missingStageObjectIsWarning() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		timeline.setMetadata("audioPath", "x.wav");
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		timeline.addAutoAnimationEvent(event("m1", 1.0, animId, "gone-stage"));

		TimelineValidationReport report = TimelineValidator.validate(timeline, engine);
		assertTrue(report.hasWarnings());
		assertTrue(report.problems().stream()
			.anyMatch(d -> TimelineValidator.RULE_MISSING_STAGE_OBJECT.equals(d.ruleId())));
	}

	@Test
	void gatePlayBlocksWhenErrorsPresent() {
		PerformanceCheckController.clear();
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		BlockAnimationEngine engine = new BlockAnimationEngine();
		timeline.addAutoAnimationEvent(event("x", 1.0, "NoSuchPreset", "t"));

		boolean[] played = {false};
		var report = PerformanceCheckController.gatePlay(timeline, engine, null, () -> played[0] = true);
		assertTrue(report.hasErrors());
		assertFalse(played[0]);
		assertTrue(PerformanceCheckController.hasBlockedPlayAction());

		PerformanceCheckController.forcePlayDespiteErrors();
		assertTrue(played[0]);
		PerformanceCheckController.clear();
	}

	@Test
	void nonFiniteDurationIsFatalAndCompilerRejectsIt() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(Double.NaN);

		TimelineValidationReport report = TimelineValidator.validate(timeline, null);
		assertTrue(report.hasFatalErrors());
		assertTrue(report.problems().stream()
			.anyMatch(d -> "non_finite_timeline_duration".equals(d.ruleId())));
		assertThrows(TimelineCompilationException.class,
			() -> TimelineCompiler.compile(timeline, null, null));
	}

	@Test
	void cameraKeyframesAreNotDoubleCounted() {
		Timeline timeline = Timeline.createDefault();
		timeline.addCameraKeyframe(new com.beatblock.timeline.CameraKeyframe(1.0));

		TimelineValidationReport report = TimelineValidator.validate(timeline, null);
		assertEquals(1, report.cameraKeyframeCount());
	}
	private static TimelineAnimationEvent event(String id, double time, String anim, String target) {
		return new TimelineAnimationEvent(id, time, 1.0, anim, target, 1f, Map.of(
			"animationType", anim,
			"targetObject", target,
			"durationSeconds", 1.0
		));
	}
}
