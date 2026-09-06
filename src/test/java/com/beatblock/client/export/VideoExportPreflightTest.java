package com.beatblock.client.export;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.playback.TimelineDiagnostic;
import com.beatblock.timeline.playback.TimelineDiagnosticSeverity;
import com.beatblock.timeline.playback.TimelineValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoExportPreflightTest {

	@TempDir
	Path tempDir;

	@Test
	void defaultTimelineIsReadyForStrictExport() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		var status = VideoExportPreflight.evaluate(timeline, null, null);
		assertTrue(status.readyForStrictExport());
		assertTrue(status.canExport());
		assertEquals(0, status.errorCount());
		assertTrue(status.compiledSnapshot() != null);
		assertTrue(VideoExportPreflight.isSnapshotCurrent(status.compiledSnapshot(), timeline));
	}

	@Test
	void missingAnimationPresetBlocksExportPreflight() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		BlockAnimationEngine engine = new BlockAnimationEngine();
		timeline.addAutoAnimationEvent(event("x", 1.0, "NoSuchPreset", "t"));

		var status = VideoExportPreflight.evaluate(timeline, engine, null);
		assertFalse(status.readyForStrictExport());
		assertFalse(status.canExport());
		assertTrue(status.errorCount() >= 1);
		assertTrue(status.compiledSnapshot() == null);
		assertTrue(TimelineValidator.validate(timeline, engine).hasErrors());
	}

	@Test
	void missingStageObjectIsElevatedToCannotExport() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		timeline.addAutoAnimationEvent(event("ev", 1.0, animId, "tower"));

		Path out = tempDir.resolve("out.mp4");
		var status = VideoExportPreflight.evaluate(new VideoExportPreflight.Request(
			timeline, engine, null,
			true, out.toString(), 0, 5, 1920, 1080, 60,
			false, true
		));
		assertFalse(status.canExport());
		assertTrue(status.compiledSnapshot() == null);
		assertTrue(status.blockerMessages().stream().anyMatch(m -> m.contains("tower")));
	}

	@Test
	void readyStatusUsesStrictCompileGateNotValidatorOnly() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		Path out = tempDir.resolve("ok.mp4");
		var status = VideoExportPreflight.evaluate(new VideoExportPreflight.Request(
			timeline, null, null,
			true, out.toString(), 0, 5, 1920, 1080, 60,
			false, true
		));
		assertTrue(status.canExport());
		assertNotNull(status.compiledSnapshot());
		assertEquals(timeline.getStageEventsGeneration(), status.compiledSnapshot().sourceGeneration());
	}

	@Test
	void ffmpegMissingAndInvalidRangeBlock() throws Exception {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		Path out = tempDir.resolve("clip.mp4");
		var status = VideoExportPreflight.evaluate(new VideoExportPreflight.Request(
			timeline, null, null,
			false, out.toString(), 5, 1, 1920, 1080, 60,
			false, true
		));
		assertFalse(status.canExport());
		assertTrue(status.blockers().stream().anyMatch(f -> "ffmpeg_missing".equals(f.id())));
		assertTrue(status.blockers().stream().anyMatch(f -> "invalid_range".equals(f.id())));
	}

	@Test
	void outputCollisionIsNoticeNotBlocker() throws Exception {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		Path out = tempDir.resolve("exists.mp4");
		Files.writeString(out, "old");
		var status = VideoExportPreflight.evaluate(new VideoExportPreflight.Request(
			timeline, null, null,
			true, out.toString(), 0, 2, 1280, 720, 30,
			false, true
		));
		assertTrue(status.canExport());
		assertTrue(status.notices().stream().anyMatch(f -> "output_collision".equals(f.id())));
		assertTrue(status.notices().stream().anyMatch(f -> "disk_estimate".equals(f.id())));
	}

	@Test
	void formatMissingStageObjectLabel() {
		String label = VideoExportPreflight.formatTimelineFinding(new TimelineDiagnostic(
			TimelineValidator.RULE_MISSING_STAGE_OBJECT,
			TimelineDiagnosticSeverity.WARNING,
			"Event ev targets missing RuntimeStageObject \"tower\"",
			"ev",
			1.0,
			null
		));
		assertTrue(label.contains("tower"));
	}

	private static TimelineAnimationEvent event(String id, double time, String animId, String target) {
		return new TimelineAnimationEvent(
			id, time, 1.0, animId, target, 1f,
			Map.of("animationType", animId, "targetObject", target, "durationSeconds", 1.0));
	}
}
