package com.beatblock.engine;

import com.beatblock.engine.influence.InfluenceFrame;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.selection.BlockStateLookup;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildSequencerTest {

	private StageObjectSystem stageObjectSystem;
	private BuildLayerManager buildLayerManager;
	private BuildSequencer sequencer;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		stageObjectSystem = new StageObjectSystem();
		buildLayerManager = new BuildLayerManager(stageObjectSystem);
		sequencer = new BuildSequencer(stageObjectSystem, buildLayerManager);
	}

	@Test
	void scheduleReturnsNullWhenTargetMissing() {
		var event = new TimelineAnimationEvent(
			"ev1", 0.0, 1.0, "build", "missing", 1f, Map.of());
		assertNull(sequencer.schedule(event));
	}

	@Test
	void scheduleDissolveRemovesReversedOrderFirst() {
		BlockPos p0 = new BlockPos(0, 64, 0);
		BlockPos p1 = new BlockPos(1, 64, 0);
		BlockPos p2 = new BlockPos(2, 64, 0);
		BlockState stone = Blocks.STONE.getDefaultState();
		stageObjectSystem.register(StageObjectSystem.fromBlocks("stage1", "Stage", List.of(p0, p1, p2)));

		var event = new TimelineAnimationEvent(
			"ev_dissolve", 0.0, 3.0, "build", "stage1", 1f,
			Map.of("buildMode", "wall", "buildDissolve", "true"));
		sequencer.schedule(event);

		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(p0, stone);
		world.put(p1, stone);
		world.put(p2, stone);
		BlockStateLookup lookup = pos -> world.getOrDefault(pos.toImmutable(), Blocks.AIR.getDefaultState());

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 1.0, lookup, pos -> true);

		assertEquals(1, frame.getWorldMutations().size());
		assertEquals(p2, frame.getWorldMutations().getFirst().pos());
		assertEquals(Blocks.AIR.getDefaultState(), frame.getWorldMutations().getFirst().toState());
		assertEquals("existence_dissolve", frame.getVfxTriggers().getFirst().kind());
	}

	@Test
	void scheduleCreatesInstanceWithOrderedBlocksAndTiming() {
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		);
		stageObjectSystem.register(StageObjectSystem.fromBlocks("stage1", "Stage", blocks));

		var event = new TimelineAnimationEvent(
			"ev1", 10.0, 2.0, "build", "stage1", 1f, Map.of("buildMode", "wall"));
		BuildSequencer.BuildInstance instance = sequencer.schedule(event);

		assertNotNull(instance);
		assertEquals("ev1", instance.getEventId());
		assertEquals(2, instance.getTotalBlocks());
		assertEquals(0, instance.getPlacedCount());
		assertEquals(1, sequencer.getActiveInstances().size());
	}

	@Test
	void contributeExistenceMutationsPlacesBlocksOverTime() {
		BlockPos p0 = new BlockPos(0, 64, 0);
		BlockPos p1 = new BlockPos(1, 64, 0);
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState placed = Blocks.STONE.getDefaultState();

		sequencer.enqueueBuildInstance(new BuildSequencer.BuildInstance(
			"ev1", List.of(p0, p1), placed, null, 10.0, 12.0, false));

		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(p0, air);
		world.put(p1, air);
		BlockStateLookup lookup = pos -> world.getOrDefault(pos.toImmutable(), air);

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 9.0, lookup, pos -> true);
		assertEquals(0, frame.getWorldMutations().size());

		sequencer.contributeExistenceMutations(frame, 11.0, lookup, pos -> true);
		assertEquals(1, frame.getWorldMutations().size());
		assertEquals(placed, frame.getWorldMutations().getFirst().toState());
		assertEquals("existence_place", frame.getVfxTriggers().getFirst().kind());

		sequencer.contributeExistenceMutations(frame, 12.0, lookup, pos -> true);
		assertEquals(2, frame.getWorldMutations().size());
		assertTrue(sequencer.getActiveInstances().isEmpty());
	}

	@Test
	void layerRevealUsesCapturedStates() {
		BlockPos pos = new BlockPos(0, 64, 0);
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState captured = Blocks.GOLD_BLOCK.getDefaultState();
		StageObject stageObject = StageObjectSystem.fromBlocks("layer_stage", "Layer", List.of(pos));
		stageObjectSystem.register(stageObject);

		Map<BlockPos, BlockState> capturedStates = new LinkedHashMap<>();
		capturedStates.put(pos, captured);
		BuildLayer layer = new BuildLayer(
			"layer1", "Layer", stageObject, LayerVisibilityState.FREE_HIDDEN, capturedStates, null);
		buildLayerManager.registerRestored(layer);

		var event = new TimelineAnimationEvent(
			"ev_layer", 5.0, 1.0, "build", "layer_stage", 1f, Map.of("layerId", "layer1"));
		sequencer.schedule(event);

		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(pos, air);
		BlockStateLookup lookup = p -> world.getOrDefault(p.toImmutable(), air);

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 6.0, lookup, p -> true);

		assertEquals(1, frame.getWorldMutations().size());
		assertEquals(captured, frame.getWorldMutations().getFirst().toState());
	}

	@Test
	void advancesPlacedCountEvenWhenChunksNotLoaded() {
		BlockPos p0 = new BlockPos(0, 64, 0);
		BlockPos p1 = new BlockPos(1, 64, 0);
		BuildSequencer.BuildInstance instance = new BuildSequencer.BuildInstance(
			"ev1", List.of(p0, p1), Blocks.STONE.getDefaultState(), null, 10.0, 12.0, false);
		sequencer.enqueueBuildInstance(instance);

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 12.0, pos -> Blocks.AIR.getDefaultState(), pos -> false);

		assertEquals(2, instance.getPlacedCount());
		assertTrue(instance.isFinished());
		assertTrue(frame.getWorldMutations().isEmpty());
		assertTrue(sequencer.getActiveInstances().isEmpty());
	}
}
