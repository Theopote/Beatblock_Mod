package com.beatblock.timeline.playback;

import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
        assertFalse(snapshot.restoreWorldMutations());
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
        if (sourceEvent != null) {
            sourceEvent.setParameter("x", 99.0);
        }
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

        if (compiled.target() != null) {
            assertEquals(List.of(new BlockPos(1, 2, 3)), compiled.target().blocks());
        }
        assertEquals("Original", engine.getAnimationPlayer().getActiveInstances().getFirst().getTarget().getName());
        if (compiled.animationDefinition() != null) {
            assertEquals(animationId, compiled.animationDefinition().getId());
        }
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

	@Test
	void phaseBSnapshotIncludesMarkersAudioAndValidationReport() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(30);
		timeline.setMetadata("audioPath", "C:/does/not/exist/track.wav");
		timeline.setMetadata("audioAssetId", "asset-1");
		timeline.addMarker(new TimelineMarker("mk1", 4.0, "Drop", MarkerType.DROP));
		timeline.addMarker(new TimelineMarker("mk2", 1.0, "Intro", MarkerType.GENERIC));

		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage", "S", List.of(new BlockPos(0, 64, 0))));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"ev", 2.0, 1.0, animId, "stage", 1f,
			Map.of("animationType", animId, "targetObject", "stage", "durationSeconds", 1.0)));

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline, engine, null);

		assertEquals(2, snapshot.markers().size());
		assertEquals("mk2", snapshot.markers().getFirst().id()); // sorted by time
		assertEquals(30.0, snapshot.durationSeconds(), 1e-9);
		assertTrue(snapshot.audio().pathPresent());
		assertFalse(snapshot.audio().fileExists());
		assertEquals("asset-1", snapshot.audio().assetId());
		assertNotNull(snapshot.validationReport());
		assertTrue(snapshot.validationReport().hasWarnings()); // missing audio file
		assertEquals(1, snapshot.compiledStageEvents().size());
	}

	@Test
	void phaseBBuildLayersAreFrozenAtCompileTime() {
		StageObjectSystem stages = new StageObjectSystem();
		BuildLayerManager layers = new BuildLayerManager(stages);
		CommandManager commands = new CommandManager();
		commands.execute(new CreateLayerCommand(layers, "Tower", List.of(new BlockPos(1, 64, 2))));
		assertEquals(1, layers.getAll().size());
		String layerId = layers.getAll().iterator().next().getId();
		String stageId = layers.get(layerId).getStageObjectId();

		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("audioPath", "x.wav");
		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline, null, layers);

		assertEquals(1, snapshot.buildLayers().size());
		CompiledBuildLayer compiled = snapshot.buildLayers().getFirst();
		assertEquals(layerId, compiled.layerId());
		assertEquals(stageId, compiled.stageObjectId());
		assertEquals(1, compiled.blockCount());

		// Live manager mutation must not affect snapshot
		layers.dissolveLayer(layers.get(layerId));
		assertEquals(0, layers.getAll().size());
		assertEquals(1, snapshot.buildLayers().size());
		assertEquals(List.of(new BlockPos(1, 64, 2)), snapshot.buildLayers().getFirst().blocks());
	}
	@Test
	void strictRejectsErrorsWhileSkipPolicyReturnsSkippedEventIds() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10);
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String validAnimation = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage", "Stage", List.of(new BlockPos(0, 64, 0))));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"valid", 1.0, 1.0, validAnimation, "stage", 1f, Map.of(
				"animationType", validAnimation, "targetObject", "stage", "durationSeconds", 1.0)));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"invalid", 2.0, 1.0, "missing-preset", "stage", 1f, Map.of(
				"animationType", "missing-preset", "targetObject", "stage", "durationSeconds", 1.0)));

		assertThrows(TimelineCompilationException.class,
			() -> TimelineCompiler.compile(timeline, engine, null, CompilePolicy.STRICT));

		CompileResult result = TimelineCompiler.compile(
			timeline, engine, null, CompilePolicy.SKIP_INVALID_EVENTS);
		String invalidId = timeline.getStageEvents().stream()
			.filter(e -> "missing-preset".equals(e.getAnimationTypeId()))
			.findFirst().orElseThrow().getEventId();
		assertEquals(List.of(invalidId), result.skippedEventIds());
		assertEquals(List.of(validAnimation), result.snapshot().stageEvents().stream()
			.map(TimelineAnimationEvent::getAnimationTypeId).toList());
		assertTrue(result.report().hasErrors());
	}

	@Test
	void skipPolicyNeverBypassesFatalErrors() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(Double.NaN);

		assertThrows(TimelineCompilationException.class,
			() -> TimelineCompiler.compile(timeline, null, null, CompilePolicy.SKIP_INVALID_EVENTS));
	}
}
