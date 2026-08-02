package com.beatblock.timeline.playback;

import com.beatblock.timeline.GlobalEvent;
import com.beatblock.timeline.GlobalEventType;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.project.OscProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CreatorAlphaGoldenProjectTest {
	@TempDir Path tempDir;

	@Test
	void normalPlaybackAndReconstructSeekProduceSameLogicalStateAtThirtySeconds() {
		TimelineAnimationEvent transientPulse = event("pulse", 5, "ANIMATE", "tower", "pulse");
		TimelineAnimationEvent placeTower = event("place", 10, "PLACE", "tower", "place");
		TimelineAnimationEvent buildArch = event("build", 20, "BUILD", "arch", "build");
		CompiledTimelineSnapshot program = new CompiledTimelineSnapshot(
			List.of(transientPulse, placeTower, buildArch),
			List.of(
				new CompiledStageEvent(transientPulse, null, null, 0),
				new CompiledStageEvent(placeTower, null, null, 1),
				new CompiledStageEvent(buildArch, null, null, 2)),
			new CompiledCameraTrack(List.of()), List.of(), List.of(),
			List.of(
				new CompiledGlobalEvent("rain", 12, new GlobalEventPayload.LocalVisualWeather("Rain", "rain", 0)),
				new CompiledGlobalEvent("flash", 15, new GlobalEventPayload.ScreenFlash("Hit", 1, 1, 1, 0.2)),
				new CompiledGlobalEvent("tint", 25, new GlobalEventPayload.ScreenTint("Blue", 0.5, 0, 0, 1, 0))),
			CompiledAudioReference.empty(), new double[0], 120, 60, true, 1, null);

		assertEquals(PlaybackStateDigest.playTo(program, 30), PlaybackStateDigest.reconstructAt(program, 30));
	}

	@Test
	void saveLoadCompilePreservesExecutableFingerprintAndProjectMetadata() throws Exception {
		Timeline original = Timeline.createDefault();
		original.setName("Creator Alpha Golden");
		original.setDurationSeconds(60);
		original.setMetadata("projectId", "golden-project-1");
		original.setMetadata("bpm", 128.0);
		original.addMarker(new TimelineMarker("drop", 24, "Drop", MarkerType.DROP));
		original.addCameraKeyframe(new com.beatblock.timeline.CameraKeyframe(8));
		original.addGlobalEvent(new GlobalEvent(16, GlobalEventType.SCREEN_TINT, "Verse Tint"));

		CompiledTimelineSnapshot before = TimelineCompiler.compile(original);
		Path project = tempDir.resolve("creator-alpha-golden.osc");
		OscProjectStore.save(project, original);
		Timeline restored = Timeline.createDefault();
		OscProjectStore.load(project, null, restored);
		CompiledTimelineSnapshot after = TimelineCompiler.compile(restored);

		assertEquals(CompiledProgramFingerprint.compute(before), CompiledProgramFingerprint.compute(after));
		assertEquals("golden-project-1", after.metadata().projectId());
		assertEquals(TimelineCompiler.COMPILER_VERSION, after.metadata().compilerVersion());
		assertEquals(after.metadata().sourceFingerprint(), CompiledProgramFingerprint.compute(after));
		assertFalse(after.metadata().sourceFingerprint().isBlank());
	}

	@Test
	void fingerprintIsIndependentOfParameterMapIterationOrder() {
		java.util.LinkedHashMap<String, Object> forward = new java.util.LinkedHashMap<>();
		forward.put("a", 1); forward.put("z", 2); forward.put("playbackSemantics", "STATEFUL");
		java.util.LinkedHashMap<String, Object> reversed = new java.util.LinkedHashMap<>();
		reversed.put("playbackSemantics", "STATEFUL"); reversed.put("z", 2); reversed.put("a", 1);
		TimelineAnimationEvent first = new TimelineAnimationEvent("e", 1, 1, "pulse", "tower", 1, forward);
		TimelineAnimationEvent second = new TimelineAnimationEvent("e", 1, 1, "pulse", "tower", 1, reversed);
		CompiledTimelineSnapshot a = snapshotOf(first);
		CompiledTimelineSnapshot b = snapshotOf(second);
		assertEquals(CompiledProgramFingerprint.compute(a), CompiledProgramFingerprint.compute(b));
	}

	private static CompiledTimelineSnapshot snapshotOf(TimelineAnimationEvent event) {
		return new CompiledTimelineSnapshot(List.of(event), List.of(new CompiledStageEvent(event, null, null, 0)),
			new CompiledCameraTrack(List.of()), List.of(), List.of(), List.of(), CompiledAudioReference.empty(),
			new double[0], 120, 60, true, 1, null);
	}
	private static TimelineAnimationEvent event(String id, double time, String mode, String target, String animation) {
		return new TimelineAnimationEvent(id, time, 1, animation, target, 1,
			Map.of("actionMode", mode, "playbackSemantics", "ANIMATE".equals(mode) ? "TRANSIENT" : "STATEFUL"));
	}
}
