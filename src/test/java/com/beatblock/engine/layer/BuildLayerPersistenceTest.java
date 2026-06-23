package com.beatblock.engine.layer;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.google.gson.JsonArray;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildLayerPersistenceTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void roundTripsLayerWithCapturedStates() {
		BlockPos pos = new BlockPos(4, 64, -2);
		BlockState captured = Blocks.GOLD_BLOCK.getDefaultState();
		StageObject stage = StageObjectSystem.fromBlocks("stage-a", "Stage A", List.of(pos));

		Map<BlockPos, BlockState> capturedStates = new LinkedHashMap<>();
		capturedStates.put(pos, captured);
		BuildLayer original = new BuildLayer(
			"layer-a",
			"Gold Layer",
			stage,
			LayerVisibilityState.FREE_HIDDEN,
			capturedStates,
			"clip-42"
		);

		StageObjectSystem stageObjects = new StageObjectSystem();
		BuildLayerManager manager = new BuildLayerManager(stageObjects);
		manager.registerRestored(original);

		JsonArray json = BuildLayerPersistence.toJson(manager);
		assertEquals(1, json.size());

		StageObjectSystem restoredStages = new StageObjectSystem();
		BuildLayerManager restored = new BuildLayerManager(restoredStages);
		BuildLayerPersistence.loadInto(restored, json);

		assertEquals(1, restored.getAll().size());
		BuildLayer loaded = restored.getAll().iterator().next();
		assertEquals("layer-a", loaded.getId());
		assertEquals("Gold Layer", loaded.getName());
		assertEquals(LayerVisibilityState.FREE_HIDDEN, loaded.getState());
		assertEquals("clip-42", loaded.getBoundClipId());
		assertEquals(List.of(pos), loaded.getStageObject().getBlocks());
		assertEquals(captured, loaded.getCapturedStates().get(pos));
	}

	@Test
	void loadIntoClearsExistingLayers() {
		StageObjectSystem stageObjects = new StageObjectSystem();
		BuildLayerManager manager = new BuildLayerManager(stageObjects);
		BlockPos pos = new BlockPos(0, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Old", List.of(pos));
		manager.registerRestored(new BuildLayer(
			"old", "Old", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		StageObject newStage = StageObjectSystem.fromBlocks("s2", "New", List.of(new BlockPos(1, 64, 0)));
		JsonArray arr = new JsonArray();
		arr.add(manualLayerJson("new-layer", "New Layer", newStage));

		BuildLayerPersistence.loadInto(manager, arr);

		assertEquals(1, manager.getAll().size());
		assertEquals("new-layer", manager.getAll().iterator().next().getId());
	}

	private static com.google.gson.JsonObject manualLayerJson(String id, String name, StageObject stage) {
		com.google.gson.JsonObject root = new com.google.gson.JsonObject();
		root.addProperty("id", id);
		root.addProperty("name", name);
		root.addProperty("state", LayerVisibilityState.FREE_VISIBLE.name());
		root.addProperty("stageObjectId", stage.getId());
		JsonArray blocks = new JsonArray();
		for (BlockPos pos : stage.getBlocks()) {
			com.google.gson.JsonObject p = new com.google.gson.JsonObject();
			p.addProperty("x", pos.getX());
			p.addProperty("y", pos.getY());
			p.addProperty("z", pos.getZ());
			blocks.add(p);
		}
		root.add("blocks", blocks);
		root.add("capturedStates", new JsonArray());
		return root;
	}
}
