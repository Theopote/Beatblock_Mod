package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineAnimationActionMode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preview / Formal / Export share one compiled-program semantics:
 * no live Timeline cursor on the driver, and animate dispatch decisions
 * come from typed payload (not raw Map keys).
 */
class CompiledProgramSemanticsContractTest {

	@Test
	void driverNoLongerKeepsPreviewLiveCursorFields() {
		List<String> fieldNames = Arrays.stream(com.beatblock.client.BeatBlockClientDriver.class.getDeclaredFields())
			.map(Field::getName)
			.toList();
		assertFalse(fieldNames.contains("scheduledStageEventIds"));
		assertFalse(fieldNames.contains("stageEventCursor"));
	}

	@Test
	void compiledAnimateSemanticsPreferPayloadExtensionsOverRawMapShape() {
		TimelineAnimationEvent event = new TimelineAnimationEvent(
			"e1",
			1.0,
			0.5,
			"pulse",
			"tower",
			1f,
			Map.of(
				"actionMode", "ANIMATE",
				"dispatchModel", "BURST",
				"playbackSemantics", "STATEFUL"
			)
		);
		CompiledStageEvent compiled = new CompiledStageEvent(event, null, null, 1L);
		assertEquals(PlaybackSemantics.STATEFUL, compiled.semantics());
	}

	@Test
	void compileAndScheduleSingleBlockBurstUsesPayloadNotLiveMapMutation() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower", "Tower", List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0))));

		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"burst",
			1.0,
			0.25,
			animationId,
			"tower",
			1f,
			Map.of(
				"actionMode", TimelineAnimationActionMode.ANIMATE.name(),
				"dispatchModel", "BURST",
				"inheritGroupSpatial", false,
				"spatialMode", "ALL",
				"singleBlockX", 1,
				"singleBlockY", 64,
				"singleBlockZ", 0
			)
		));

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compile(timeline, engine);
		assertEquals(1, snapshot.compiledStageEvents().size());
		CompiledStageEvent compiled = snapshot.compiledStageEvents().getFirst();
		assertTrue(compiled.event().getPayload() instanceof com.beatblock.timeline.payload.StageEventPayload.Animate);

		engine.scheduleTimelineEvent(compiled, snapshot.referenceBeatTimesSeconds(), snapshot.bpm());
		assertEquals(1, engine.getAnimationPlayer().getActiveInstances().size());
		assertTrue(engine.getAnimationPlayer().getActiveInstances().getFirst().isActiveAt(1.05));
	}

	@Test
	void previewAndFormalAnimateBothUseFrozenCompiledTarget() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower", "Tower", List.of(
				new BlockPos(0, 64, 0),
				new BlockPos(1, 64, 0),
				new BlockPos(2, 64, 0)
			)));

		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"pulse",
			1.0,
			0.5,
			animationId,
			"tower",
			1f,
			Map.of(
				"actionMode", TimelineAnimationActionMode.ANIMATE.name(),
				"dispatchModel", "BURST",
				"spatialMode", "ALL"
			)
		));

		CompiledTimelineSnapshot snapshot = TimelineCompiler.compileForPlayback(timeline, engine, null);
		CompiledStageEvent compiled = snapshot.compiledStageEvents().getFirst();
		assertEquals(3, compiled.target().blocks().size());

		// Live catalog mutates without bumping Timeline documentGeneration.
		engine.getStageObjectSystem().remove("tower");
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower", "Tower", List.of(new BlockPos(9, 64, 9))));
		assertEquals(1, engine.getStageObjectSystem().get("tower").getBlocks().size());

		engine.clear();
		engine.scheduleTimelineEvent(compiled, snapshot.referenceBeatTimesSeconds(), snapshot.bpm());
		assertEquals(
			java.util.Set.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(2, 64, 0)),
			scheduledBlocks(engine)
		);
	}

	@Test
	void stageEventDispatcherPreviewUsesCompiledWhenPresent() {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		String animationId = engine.getAnimationLibrary().getAll().keySet().iterator().next();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower", "Tower", List.of(
				new BlockPos(0, 64, 0),
				new BlockPos(1, 64, 0)
			)));

		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"pulse",
			1.0,
			0.5,
			animationId,
			"tower",
			1f,
			Map.of(
				"actionMode", TimelineAnimationActionMode.ANIMATE.name(),
				"dispatchModel", "BURST",
				"spatialMode", "ALL"
			)
		));
		CompiledTimelineSnapshot snapshot = TimelineCompiler.compileForPlayback(timeline, engine, null);
		CompiledStageEvent compiled = snapshot.compiledStageEvents().getFirst();
		assertEquals(2, compiled.target().blocks().size());

		engine.getStageObjectSystem().remove("tower");
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower", "Tower", List.of(new BlockPos(5, 64, 5))));
		assertEquals(1, engine.getStageObjectSystem().get("tower").getBlocks().size());

		com.beatblock.runtime.BeatBlockContext context = com.beatblock.runtime.BeatBlockContext.builder()
			.blockAnimationEngine(engine)
			.build();
		var dispatcher = new com.beatblock.client.StageEventDispatcher(
			new com.beatblock.client.StageEventDispatcher.Host() {
				@Override
				public com.beatblock.runtime.BeatBlockContext ctx() {
					return context;
				}

				@Override
				public CompiledStageEvent resolveCompiled(TimelineAnimationEvent event) {
					return compiled;
				}

				@Override
				public void recordActionReport(
					TimelineAnimationEvent event, int mutationCount, String status, String detail
				) {}

				@Override
				public void captureTimelineMutationOriginalState(
					net.minecraft.world.World world,
					BlockPos pos,
					net.minecraft.block.BlockState currentState
				) {}
			},
			() -> false
		);

		dispatcher.apply(compiled.event(), compiled, true, snapshot.referenceBeatTimesSeconds(), snapshot.bpm());
		assertEquals(
			java.util.Set.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)),
			scheduledBlocks(engine)
		);
	}

	private static java.util.Set<BlockPos> scheduledBlocks(BlockAnimationEngine engine) {
		java.util.Set<BlockPos> blocks = new java.util.LinkedHashSet<>();
		for (var instance : engine.getAnimationPlayer().getActiveInstances()) {
			blocks.addAll(instance.getTarget().getBlocks());
		}
		return blocks;
	}
}
