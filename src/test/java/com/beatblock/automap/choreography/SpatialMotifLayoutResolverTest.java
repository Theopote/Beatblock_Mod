package com.beatblock.automap.choreography;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class SpatialMotifLayoutResolverTest {

	private BlockAnimationEngine engine;

	@BeforeEach
	void setUp() {
		engine = BeatBlock.getContext().blockAnimationEngine();
		engine.getStageObjectSystem().clear();
	}

	@Test
	void resolvesCentersFromRegisteredStageObjects() {
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-a", "A", List.of(new BlockPos(10, 64, 0))));
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-b", "B", List.of(new BlockPos(0, 64, 0))));

		SpatialMotifLayout layout = SpatialMotifLayoutResolver.resolve(
			List.of("tower-a", "tower-b"),
			MotifAxis.X,
			engine
		);

		assertEquals(new Vec3d(10.5, 64.0, 0.5), layout.centerOf("tower-a"));
		assertEquals(new Vec3d(0.5, 64.0, 0.5), layout.centerOf("tower-b"));
	}

	@Test
	void fallsBackToSyntheticForMissingParticipants() {
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-a", "A", List.of(new BlockPos(4, 64, 0))));

		SpatialMotifLayout layout = SpatialMotifLayoutResolver.resolve(
			List.of("tower-a", "tower-missing"),
			MotifAxis.X,
			engine
		);

		assertEquals(new Vec3d(4.5, 64.0, 0.5), layout.centerOf("tower-a"));
		assertEquals(new Vec3d(0.0, 0.0, 0.0), layout.centerOf("tower-missing"));
	}

	@Test
	void cascadeOrderFollowsRealWorldXPositions() {
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-a", "A", List.of(new BlockPos(10, 64, 0))));
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-b", "B", List.of(new BlockPos(0, 64, 0))));
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"tower-c", "C", List.of(new BlockPos(20, 64, 0))));

		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			1.0,
			SpatialMotifId.CASCADE,
			List.of("tower-a", "tower-b", "tower-c"),
			MotifAxis.X,
			0.1,
			"pulse",
			0.8f,
			0.5,
			0
		);
		SpatialMotifLayout layout = SpatialMotifLayoutResolver.resolve(
			phrase.participantIds(),
			phrase.axis(),
			engine
		);
		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals(3, events.size());
		assertEquals("tower-b", events.get(0).targetObjectId());
		assertEquals("tower-a", events.get(1).targetObjectId());
		assertEquals("tower-c", events.get(2).targetObjectId());
		assertTrue(events.get(1).timeSeconds() > events.get(0).timeSeconds());
	}

	@Test
	void resolveFromContextUsesBeatBlockEngine() {
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks(
			"stage-main", "Main", List.of(new BlockPos(2, 70, 2))));

		SpatialMotifLayout layout = SpatialMotifLayoutResolver.resolveFromContext(
			List.of("stage-main"),
			MotifAxis.X
		);

		Map<String, Vec3d> centers = layout.centers();
		assertEquals(1, centers.size());
		assertEquals(new Vec3d(2.5, 70.0, 2.5), centers.get("stage-main"));
	}
}
