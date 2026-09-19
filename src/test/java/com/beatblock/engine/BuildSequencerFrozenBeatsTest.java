package com.beatblock.engine;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.testutil.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P0-2：BUILD pacing 只消费冻结 beats/bpm，不读 live Timeline。
 */
class BuildSequencerFrozenBeatsTest {

	private StageObjectSystem stages;
	private BuildSequencer sequencer;

	@BeforeAll
	static void bootstrap() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		stages = new StageObjectSystem();
		stages.register(StageObjectSystem.fromBlocks(
			"tower",
			"Tower",
			List.of(
				new net.minecraft.util.math.BlockPos(0, 64, 0),
				new net.minecraft.util.math.BlockPos(1, 64, 0),
				new net.minecraft.util.math.BlockPos(2, 64, 0),
				new net.minecraft.util.math.BlockPos(3, 64, 0)
			)
		));
		sequencer = new BuildSequencer(stages, new com.beatblock.engine.layer.BuildLayerManager(stages));
	}

	@Test
	void scheduleStoresTimestampsFromProvidedFrozenBeats() {
		TimelineAnimationEvent event = new TimelineAnimationEvent(
			"build-1", 1.0, 4.0, "Pulse", "tower", 1f,
			Map.of("actionMode", "BUILD", "buildMode", "wall"));

		double[] frozenBeats = {1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0};
		List<Double> expected = BuildSequencer.computeBlockTimestamps(
			4, 1.0, 5.0, frozenBeats, 120.0);
		assertNotNull(expected);

		BuildSequencer.BuildInstance paced = sequencer.schedule(event, frozenBeats, 120.0);
		assertNotNull(paced);
		assertEquals(expected, paced.getBlockTimestamps());
	}

	@Test
	void withoutBeatsFallsBackToLinearInterpolation() {
		TimelineAnimationEvent event = new TimelineAnimationEvent(
			"build-linear", 1.0, 2.0, "Pulse", "tower", 1f,
			Map.of("actionMode", "BUILD", "buildMode", "wall"));

		assertNull(BuildSequencer.computeBlockTimestamps(4, 1.0, 3.0, null, 120.0));
		assertNull(BuildSequencer.computeBlockTimestamps(4, 1.0, 3.0, new double[0], 120.0));

		BuildSequencer.BuildInstance linear = sequencer.schedule(event, null, 0.0);
		assertNotNull(linear);
		assertNull(linear.getBlockTimestamps());
	}

	@Test
	void computeBlockTimestampsIsPureFunctionOfBeatsAndBpm() {
		double[] beats = {0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5};
		List<Double> at120 = BuildSequencer.computeBlockTimestamps(8, 0.0, 4.0, beats, 120.0);
		assertNotNull(at120);
		assertEquals(8, at120.size());
		assertEquals(at120, BuildSequencer.computeBlockTimestamps(8, 0.0, 4.0, beats, 120.0));

		// Different beat grid must change timestamps (pure frozen input)
		double[] shifted = {0.0, 0.8, 1.6, 2.4, 3.2, 4.0, 4.8, 5.6};
		List<Double> shiftedStamps = BuildSequencer.computeBlockTimestamps(8, 0.0, 6.0, shifted, 120.0);
		assertNotNull(shiftedStamps);
		assertNotEquals(at120, shiftedStamps);
	}
}
