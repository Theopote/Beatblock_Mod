package com.beatblock.engine;

import com.beatblock.engine.influence.InfluenceFrame;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildSequencerTest {

	private StageObjectSystem stageObjectSystem;
	private BuildSequencer sequencer;

	@BeforeEach
	void setUp() {
		stageObjectSystem = new StageObjectSystem();
		sequencer = new BuildSequencer(stageObjectSystem, new com.beatblock.engine.layer.BuildLayerManager(stageObjectSystem));
	}

	@Test
	void scheduleReturnsNullWhenTargetMissing() {
		var event = new TimelineAnimationEvent(
			"ev1", 0.0, 1.0, "build", "missing", 1f, Map.of());
		assertNull(sequencer.schedule(event));
	}

	@Test
	void advancesPlacedCountEvenWhenChunksNotLoaded() {
		BlockPos p0 = new BlockPos(0, 64, 0);
		BlockPos p1 = new BlockPos(1, 64, 0);
		BuildSequencer.BuildInstance instance = new BuildSequencer.BuildInstance(
			"ev1", List.of(p0, p1), null, null, 10.0, 12.0, false);
		sequencer.enqueueBuildInstance(instance);

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 12.0, pos -> null, pos -> false);

		assertEquals(2, instance.getPlacedCount());
		assertTrue(instance.isFinished());
		assertTrue(frame.getWorldMutations().isEmpty());
		assertTrue(sequencer.getActiveInstances().isEmpty());
	}
}
