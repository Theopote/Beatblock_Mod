package com.beatblock.timeline.playback;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineCompilerTest {

	@Test
	@SuppressWarnings("unchecked")
	void compiledSnapshotIsSortedAndIsolatedFromDocumentEdits() {
		Timeline timeline = Timeline.createDefault();
		List<String> mutableTags = new ArrayList<>(List.of("initial"));
		timeline.addAutoAnimationEvent(event("later", 4.0, Map.of("tags", mutableTags)));
		timeline.addAutoAnimationEvent(event("earlier", 1.0, Map.of()));

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline);
		timeline.addAutoAnimationEvent(event("added-after-compile", 2.0, Map.of()));
		mutableTags.add("mutated");

		assertEquals(List.of("earlier", "later"), snapshot.stageEvents().stream()
			.map(TimelineAnimationEvent::getAnimationTypeId).toList());
		assertEquals(2, snapshot.stageEvents().size());
		Object frozenTags = snapshot.stageEvents().get(1).getParameters().get("tags");
		assertEquals(List.of("initial"), frozenTags);
		assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) frozenTags).add("x"));
	}

	@Test
	void snapshotDefensivelyCopiesBeatArrayAndCapturesPlaybackPolicy() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 128.0);
		timeline.setMetadata("timelineActionRollbackMode", "performance");
		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline);

		double[] first = snapshot.referenceBeatTimesSeconds();
		double[] second = snapshot.referenceBeatTimesSeconds();
		assertNotSame(first, second);
		assertEquals(128.0, snapshot.bpm(), 1e-9);
		assertTrue(!snapshot.restoreWorldMutations());
	}

	@Test
	void cameraTrackIsSortedAndIsolatedFromLaterEdits() {
		Timeline timeline = Timeline.createDefault();
		var cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		var later = TimelineOperations.addClip(cameraTrack, 5.0, 7.0);
		var earlier = TimelineOperations.addClip(cameraTrack, 1.0, 3.0);
		var sourceEvent = TimelineOperations.addEvent(earlier, 1.0, EventType.CAMERA_KEYFRAME,
			Map.of("x", 2.0, "tags", new ArrayList<>(List.of("original"))));

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline);
		sourceEvent.setParameter("x", 99.0);
		TimelineOperations.addClip(cameraTrack, 0.0, 0.5);

		assertEquals(List.of(1.0, 5.0), snapshot.cameraTrack().clips().stream()
			.map(CompiledCameraTrack.CameraClip::startTimeSeconds).toList());
		var compiledEvent = snapshot.cameraTrack().clips().getFirst().events().getFirst();
		assertEquals(2.0, compiledEvent.parameters().get("x"));
		assertThrows(UnsupportedOperationException.class,
			() -> compiledEvent.parameters().put("x", 3.0));
	}

	@Test
	void compiledAnimateEventKeepsResolvedTargetAfterRuntimeRegistryChanges() {
		Timeline timeline = Timeline.createDefault();
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage", "Original", List.of(new BlockPos(1, 2, 3))));
		timeline.addAutoAnimationEvent(event(animationId, 1.0, Map.of()));

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline, engine);
		engine.getStageObjectSystem().clear();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage", "Replacement", List.of(new BlockPos(9, 9, 9))));
		CompiledStageEvent compiled = snapshot.compiledStageEvents().getFirst();
		engine.scheduleTimelineEvent(compiled, new double[0], snapshot.bpm());

		assertEquals(List.of(new BlockPos(1, 2, 3)), compiled.target().blocks());
		assertEquals("Original", engine.getAnimationPlayer().getActiveInstances().getFirst().getTarget().getName());
		assertEquals(animationId, compiled.animationDefinition().getId());
	}

	private static TimelineAnimationEvent event(String id, double time, Map<String, Object> params) {
		return new TimelineAnimationEvent(id, time, 1.0, id, "stage", 1f, params);
	}

	@Test
	void unsupportedMutableParameterTypeFailsCompilationEarly() {
		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(event("bad", 1.0, Map.of("custom", new Object())));

		var ex = assertThrows(TimelineCompilationException.class, () -> TimelineCompiler.compile(timeline));
		assertTrue(ex.getMessage().contains("Unsupported mutable parameter type"));
		assertTrue(ex.getMessage().contains("java.lang.Object"));
	}
}
