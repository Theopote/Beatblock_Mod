package com.beatblock.engine.layer;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLayerWorldPersistenceTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@TempDir
	Path tempDir;

	@Test
	void roundTripsLayersToWorldFile() throws Exception {
		BlockPos pos = new BlockPos(2, 64, 3);
		StageObject stage = StageObjectSystem.fromBlocks("stage-a", "Tower", List.of(pos));
		Map<BlockPos, BlockState> captured = new LinkedHashMap<>();
		captured.put(pos, Blocks.STONE.getDefaultState());
		BuildLayer layer = new BuildLayer(
			"layer-a",
			"Tower",
			stage,
			LayerVisibilityState.FREE_HIDDEN,
			captured,
			null
		);

		StageObjectSystem stageObjects = new StageObjectSystem();
		BuildLayerManager manager = new BuildLayerManager(stageObjects);
		manager.registerRestored(layer);
		BuildLayerGroup group = manager.createGroup("group-a", List.of("layer-a"));
		assert group != null;

		RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));
		Path file = BuildLayerWorldPersistence.layerFileForDimension(tempDir, dimension);
		BuildLayerWorldPersistence.save(file, dimension, manager);

		manager.purgeAllLayers();
		assertTrue(manager.getAll().isEmpty());

		assertTrue(BuildLayerWorldPersistence.load(file, manager));
		assertEquals(1, manager.getAll().size());
		assertEquals(1, manager.getAllGroups().size());
		BuildLayer loaded = manager.getAll().iterator().next();
		assertEquals("layer-a", loaded.getId());
		assertEquals(group.getId(), loaded.getGroupId());
		assertEquals(LayerVisibilityState.FREE_HIDDEN, loaded.getState());
	}

	@Test
	void loadReturnsFalseWhenFileMissing() throws Exception {
		BuildLayerManager manager = new BuildLayerManager(new StageObjectSystem());
		Path file = tempDir.resolve("missing.json");
		assertFalse(BuildLayerWorldPersistence.load(file, manager));
	}

	@Test
	void loadFallsBackToBackupWhenPrimaryIsCorrupt() throws Exception {
		BlockPos pos = new BlockPos(4, 64, 5);
		StageObject stage = StageObjectSystem.fromBlocks("stage-backup", "Backup", List.of(pos));
		BuildLayerManager manager = new BuildLayerManager(new StageObjectSystem());
		BuildLayer layer = new BuildLayer(
			"layer-backup", "Original", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null);
		manager.registerRestored(layer);
		RegistryKey<World> dimension = RegistryKey.of(
			RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));
		Path file = BuildLayerWorldPersistence.layerFileForDimension(tempDir, dimension);

		BuildLayerWorldPersistence.save(file, dimension, manager);
		layer.setName("Updated");
		BuildLayerWorldPersistence.save(file, dimension, manager);
		Files.writeString(file, "{broken", java.nio.charset.StandardCharsets.UTF_8);

		manager.clear();
		assertTrue(BuildLayerWorldPersistence.load(file, manager));
		assertEquals("Original", manager.get("layer-backup").getName());
	}
}
