package com.beatblock.engine.layer;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.testutil.StubBlockWorld;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLayerManagerVisibilityTest {

	private BuildLayerManager manager;
	private BlockPos pos;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		manager = new BuildLayerManager(new StageObjectSystem());
		pos = new BlockPos(0, 64, 0);
	}

	@Test
	void hideLayerCapturesBlockStateAndMarksHidden() {
		var stone = Blocks.STONE.getDefaultState();
		StubBlockWorld.Handle world = StubBlockWorld.create(Map.of(pos, stone));
		registerVisibleLayer("layer-1", List.of(pos));

		BuildLayer layer = manager.get("layer-1");
		assertTrue(manager.hideLayer(layer, world.world()));
		assertEquals(LayerVisibilityState.FREE_HIDDEN, layer.getState());
		assertEquals(stone, layer.getCapturedStates().get(pos));
	}

	@Test
	void showLayerRestoresVisibilityStateFromHidden() {
		var stone = Blocks.STONE.getDefaultState();
		StubBlockWorld.Handle world = StubBlockWorld.create(Map.of(pos, Blocks.AIR.getDefaultState()));
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Layer", List.of(pos));
		manager.registerRestored(new BuildLayer(
			"layer-2", "Layer", stage, LayerVisibilityState.FREE_HIDDEN, Map.of(pos, stone), null));

		BuildLayer layer = manager.get("layer-2");
		assertTrue(manager.showLayer(layer, world.world()));
		assertEquals(LayerVisibilityState.FREE_VISIBLE, layer.getState());
	}

	@Test
	void hideThenShowRoundTripPreservesCapturedSnapshot() {
		var stone = Blocks.STONE.getDefaultState();
		StubBlockWorld.Handle world = StubBlockWorld.create(Map.of(pos, stone));
		registerVisibleLayer("layer-3", List.of(pos));
		BuildLayer layer = manager.get("layer-3");

		manager.hideLayer(layer, world.world());
		manager.showLayer(layer, world.world());

		assertEquals(LayerVisibilityState.FREE_VISIBLE, layer.getState());
		assertEquals(stone, layer.getCapturedStates().get(pos));
	}

	private void registerVisibleLayer(String id, List<BlockPos> blocks) {
		StageObject stage = StageObjectSystem.fromBlocks(id + "_stage", "Layer", blocks);
		manager.registerRestored(new BuildLayer(
			id, "Layer", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));
	}
}
