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
}
