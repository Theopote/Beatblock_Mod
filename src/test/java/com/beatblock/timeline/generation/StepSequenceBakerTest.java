package com.beatblock.timeline.generation;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.GroupSpec;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepSequenceBakerTest {

	private BlockAnimationEngine engine;

	@BeforeEach
	void setUp() {
		engine = new BlockAnimationEngine();
		BeatBlock.blockAnimationEngine = engine;
	}

	@AfterEach
	void tearDown() {
		BeatBlock.blockAnimationEngine = null;
	}

	@Test
	void bakesStepEventIntoBurstEventsOnSameTrack() {
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0));
		StageObject stage = new StageObject(
			"stage-a", "Stage", blocks, Vec3d.ZERO, GroupSpec.manualSnapshot());
		engine.getStageObjectSystem().register(stage);

		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 120.0);
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		TimelineOperations.addEvent(clip, 0.0, EventType.ANIMATION, Map.of(
			"dispatchModel", "STEP",
			"animationType", "BlockTap",
			"targetObject", "stage-a",
			"durationSeconds", 0.5,
			"energy", 1.0f,
			"stepStartMode", "IMMEDIATE",
			"pacingMode", "BEAT_GRID"
		));

		StepSequenceBaker.BakeResult result = StepSequenceBaker.bake(timeline, null, Vec3d.ZERO);

		assertEquals(1, result.stepEventsBaked());
		assertEquals(2, result.burstEventsCreated());
		assertEquals(2, timeline.getAutoAnimationEvents().size());
		for (var event : timeline.getAutoAnimationEvents()) {
			assertFalse(StepBurstEventFactory.isStepDispatch(event.getParameters()));
			assertEquals("BURST", event.getParameters().get("dispatchModel"));
		}
	}

	@Test
	void skipsWhenStageObjectMissing() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0, 1);
		TimelineOperations.addEvent(clip, 0, EventType.ANIMATION, Map.of(
			"dispatchModel", "STEP",
			"targetObject", "missing",
			"animationType", "BlockTap"
		));

		StepSequenceBaker.BakeResult result = StepSequenceBaker.bake(timeline, null, Vec3d.ZERO);
		assertEquals(0, result.stepEventsBaked());
		assertTrue(result.stepEventsSkipped() >= 1);
	}
}
