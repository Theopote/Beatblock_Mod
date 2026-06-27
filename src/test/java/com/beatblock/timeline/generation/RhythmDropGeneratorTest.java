package com.beatblock.timeline.generation;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmDropGeneratorTest {

	private Timeline timeline;
	private StageObjectSystem stageObjects;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		stageObjects = new StageObjectSystem();
	}

	@Test
	void generateWritesEventsOnBeatGrid() {
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 1f));
		timeline.addFeatureEvent("kick", new FeatureEvent(2.0, 1f));
		timeline.addFeatureEvent("kick", new FeatureEvent(3.0, 1f));

		List<BlockPos> positions = List.of(
			new BlockPos(1, 64, 1),
			new BlockPos(2, 64, 2)
		);
		var config = new RhythmDropGenerator.Config(0.5, false, 1.0, 6.0, RhythmDropGenerator.DEFAULT_ANCHOR_ID);

		RhythmDropGenerator.Outcome outcome = RhythmDropGenerator.generate(timeline, stageObjects, positions, config);

		assertTrue(outcome.success());
		assertEquals(2, outcome.eventCount());
		assertEquals(RhythmDropGenerator.DEFAULT_ANCHOR_ID, outcome.targetObjectId());
		assertNotNull(stageObjects.get(RhythmDropGenerator.DEFAULT_ANCHOR_ID));

		List<TimelineAnimationEvent> events = timeline.getAnimationEvents(Timeline.TRACK_ID_ANIMATION_BLOCK);
		assertEquals(2, events.size());
		assertEquals("RhythmDrop", events.get(0).getAnimationTypeId());
		assertEquals(0.0, events.get(0).getTimeSeconds(), 1e-9);
		assertEquals(0.0, events.get(1).getTimeSeconds(), 1e-9);
		assertEquals(1, events.get(0).getParameters().get("singleBlockX"));
		assertEquals(2, events.get(1).getParameters().get("singleBlockX"));
	}

	@Test
	void generateFailsWithoutLandingPositions() {
		var outcome = RhythmDropGenerator.generate(
			timeline, stageObjects, List.of(), RhythmDropGenerator.Config.defaults(0.0));
		assertFalse(outcome.success());
	}
}
