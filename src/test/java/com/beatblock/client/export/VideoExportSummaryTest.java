package com.beatblock.client.export;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.CameraKeyframe;
import com.beatblock.timeline.GlobalEvent;
import com.beatblock.timeline.GlobalEventType;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineMarker;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoExportSummaryTest {

	@TempDir
	Path tempDir;

	@Test
	void halfOpenRangeCountsOnlyEventsInsideSlice() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(40);
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage", "S", List.of(new BlockPos(0, 64, 0))));

		timeline.addAutoAnimationEvent(event("a", 5.0, animId, "stage"));
		timeline.addAutoAnimationEvent(event("b", 20.0, animId, "stage"));
		timeline.addAutoAnimationEvent(event("c", 30.0, animId, "stage"));
		timeline.addCameraKeyframe(new CameraKeyframe(19.0));
		timeline.addCameraKeyframe(new CameraKeyframe(25.0));
		timeline.addGlobalEvent(new GlobalEvent(22.0, GlobalEventType.SPECIAL, "flash"));
		timeline.addMarker(new TimelineMarker("m1", 21.0, "Drop", MarkerType.DROP));
		timeline.addMarker(new TimelineMarker("m2", 35.0, "Out", MarkerType.GENERIC));

		Path out = tempDir.resolve("show.mp4");
		var snapshot = VideoExportSummary.build(
			timeline, 20.0, 30.0, 1920, 1080, 60, true, "C:/tracks/song.mp3",
			out.toString(), null);

		assertEquals("00:20.000 → 00:30.000", snapshot.rangeSpanLabel());
		assertEquals("10.0s", snapshot.durationHumanLabel());
		assertEquals(600, snapshot.totalFrames());
		assertEquals("1920 × 1080", snapshot.resolutionLabel());
		assertEquals("song.mp3", snapshot.audioFileLabel());
		assertTrue(snapshot.includeAudio());
		assertTrue(snapshot.cameraTimelineDriven());
		assertTrue(snapshot.vfxEnabled());
		assertEquals(1, snapshot.stageEventsInRange());
		assertEquals(1, snapshot.cameraKeyframesInRange());
		assertEquals(1, snapshot.vfxEventsInRange());
		assertEquals(1, snapshot.markersInRange());
		assertTrue(snapshot.outputLabel().endsWith("show.mp4"));
		assertFalse(snapshot.nativeResolution());
	}

	@Test
	void clockAndDurationFormatting() {
		assertEquals("00:10.000", VideoExportSummary.formatClock(10.0));
		assertEquals("01:24.500", VideoExportSummary.formatClock(84.5));
		assertEquals("1:02:03.000", VideoExportSummary.formatClock(3723.0));
		assertEquals("1m 14.5s", VideoExportSummary.formatDurationHuman(74.5));
		assertEquals("14.5s", VideoExportSummary.formatDurationHuman(14.5));
		assertEquals("4,470", VideoExportSummary.formatGrouped(4470));
	}

	@Test
	void inRangeIsHalfOpen() {
		assertTrue(VideoExportSummary.inRange(20.0, 20.0, 30.0));
		assertFalse(VideoExportSummary.inRange(30.0, 20.0, 30.0));
		assertFalse(VideoExportSummary.inRange(19.9, 20.0, 30.0));
	}

	private static TimelineAnimationEvent event(String id, double time, String animId, String target) {
		return new TimelineAnimationEvent(
			id, time, 1.0, animId, target, 1f,
			Map.of("animationType", animId, "targetObject", target, "durationSeconds", 1.0));
	}
}
