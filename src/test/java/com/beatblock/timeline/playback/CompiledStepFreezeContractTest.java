package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.generation.StepBurstEventFactory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract: unresolved STEP is frozen inside CompiledTimelineSnapshot;
 * live runtime camera must not change the compiled block order.
 */
class CompiledStepFreezeContractTest {

	@Test
	void compileExpandsStepIntoBurstEventsWithStableIds() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower",
			"Tower",
			List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(2, 64, 0))
		));

		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 120.0);
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"step-a",
			1.0,
			0.25,
			animationId,
			"tower",
			1f,
			stepParams()
		));
		String sourceStepId = timeline.getStageEvents().stream()
			.filter(StepBurstEventFactory::isStepDispatch)
			.map(TimelineAnimationEvent::getEventId)
			.findFirst()
			.orElseThrow();

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline, engine);

		assertEquals(3, snapshot.stageEvents().size());
		assertEquals(3, snapshot.compiledStageEvents().size());
		for (TimelineAnimationEvent event : snapshot.stageEvents()) {
			assertFalse(StepBurstEventFactory.isStepDispatch(event));
			assertEquals("BURST", event.getParameters().get("dispatchModel"));
			assertEquals(sourceStepId, event.getParameters().get("bakedFromStepEventId"));
			assertTrue(event.getEventId().startsWith(sourceStepId + "#burst#"));
		}
		assertEquals(
			List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(2, 64, 0)),
			snapshot.stageEvents().stream()
				.map(e -> StepBurstEventFactory.readSingleBlockPos(e.getParameters()))
				.toList()
		);
	}

	@Test
	void liveRuntimeCameraDoesNotChangeCompiledStepOrder() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"wall",
			"Wall",
			List.of(
				new BlockPos(0, 64, 0),
				new BlockPos(2, 64, 0),
				new BlockPos(4, 64, 0)
			)
		));

		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 120.0);
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"step-cam",
			2.0,
			0.2,
			animationId,
			"wall",
			1f,
			stepParamsWithCameraEdge()
		));

		var cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		var clip = TimelineOperations.addClip(cameraTrack, 0.0, 8.0);
		TimelineOperations.addEvent(clip, 2.0, EventType.CAMERA_KEYFRAME, Map.of(
			"x", 10.0,
			"y", 66.0,
			"z", 0.0,
			"yawDeg", 90.0,
			"pitchDeg", 0.0
		));

		engine.setRuntimeCameraPosition(new Vec3d(-100, 66, 0));
		engine.setRuntimeCameraOrientation(270f, 0f);
		CompiledTimelineSnapshot first = TimelineCompiler.compile(timeline, engine);

		engine.setRuntimeCameraPosition(new Vec3d(100, 66, 0));
		engine.setRuntimeCameraOrientation(90f, 0f);
		CompiledTimelineSnapshot second = TimelineCompiler.compile(timeline, engine);

		List<BlockPos> orderA = first.stageEvents().stream()
			.map(e -> StepBurstEventFactory.readSingleBlockPos(e.getParameters()))
			.toList();
		List<BlockPos> orderB = second.stageEvents().stream()
			.map(e -> StepBurstEventFactory.readSingleBlockPos(e.getParameters()))
			.toList();
		assertEquals(orderA, orderB);
		assertEquals(first.metadata().sourceFingerprint(), second.metadata().sourceFingerprint());
	}

	@Test
	void documentStillHoldsUnresolvedStepAfterCompile() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"obj", "Obj", List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0))));

		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"live-step", 0.5, 0.2, animationId, "obj", 1f, stepParams()));

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline, engine);
		assertFalse(snapshot.stageEvents().stream().anyMatch(StepBurstEventFactory::isStepDispatch));
		assertTrue(timeline.getStageEvents().stream().anyMatch(StepBurstEventFactory::isStepDispatch));
		assertNotEquals(timeline.getStageEvents().size(), snapshot.stageEvents().size());
	}

	@Test
	void unresolvedStepAtRuntimeThrowsInsteadOfExpanding() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower", "Tower", List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0))));

		TimelineAnimationEvent step = new TimelineAnimationEvent(
			"raw-step",
			1.0,
			0.25,
			animationId,
			"tower",
			1f,
			stepParams()
		);
		assertTrue(StepBurstEventFactory.isStepDispatch(step));

		IllegalStateException thrown = assertThrows(
			IllegalStateException.class,
			() -> engine.scheduleTimelineEvent(step, new double[0], 120.0)
		);
		assertTrue(thrown.getMessage().contains("Unresolved STEP"));
		assertEquals(0, engine.getAnimationPlayer().getActiveInstances().size());
	}

	@Test
	void compileWithoutEngineMayRetainStepButPlaybackLoadRejectsIt() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower", "Tower", List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0))));

		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"step-a", 1.0, 0.25, animationId, "tower", 1f, stepParams()));

		CompiledTimelineSnapshot inspection = TimelineCompiler.compile(timeline);
		assertTrue(inspection.stageEvents().stream().anyMatch(StepBurstEventFactory::isStepDispatch));

		PlaybackEngine pe = new PlaybackEngine();
		IllegalStateException thrown = assertThrows(
			IllegalStateException.class,
			() -> pe.load(inspection)
		);
		assertTrue(thrown.getMessage().contains("Unresolved STEP"));

		CompiledTimelineSnapshot playback = TimelineCompiler.compileForPlayback(timeline, engine, null);
		assertFalse(playback.stageEvents().stream().anyMatch(StepBurstEventFactory::isStepDispatch));
		pe.load(playback);
		assertTrue(pe.isLoaded());
	}

	private static Map<String, Object> stepParams() {
		Map<String, Object> params = new HashMap<>();
		params.put("dispatchModel", "STEP");
		params.put("spatialMode", "SEQUENTIAL");
		params.put("blocksPerBeat", 1);
		params.put("stepStartMode", "IMMEDIATE");
		params.put("inheritGroupSpatial", false);
		return params;
	}

	private static Map<String, Object> stepParamsWithCameraEdge() {
		Map<String, Object> params = stepParams();
		params.put("cameraEdgePriority", 1.0);
		params.put("cameraFrustumGating", true);
		return params;
	}
}
