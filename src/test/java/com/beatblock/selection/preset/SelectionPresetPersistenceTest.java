package com.beatblock.selection.preset;

import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionPresetPersistenceTest {

	@TempDir
	Path tempDir;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void resetManager() {
		SelectionPresetManager.getInstance().clear();
	}

	@Test
	void roundTripsPresetsThroughJsonFile() throws Exception {
		Path file = tempDir.resolve("selection_presets.json");
		SelectionPresetManager manager = SelectionPresetManager.getInstance();
		SelectionPresetManager.SelectionPreset saved = manager.savePreset(
			"Tower",
			java.util.Set.of(new BlockPos(1, 64, 2), new BlockPos(3, 70, 4)),
			"test preset"
		);

		SelectionPresetPersistence.save(file, manager);
		assertTrue(Files.exists(file));

		manager.clear();
		SelectionPresetPersistence.load(file, manager);

		assertEquals(1, manager.getPresetCount());
		SelectionPresetManager.SelectionPreset loaded = manager.getAllPresets().getFirst();
		assertEquals(saved.id(), loaded.id());
		assertEquals("Tower", loaded.name());
		assertEquals("test preset", loaded.description());
		assertEquals(saved.createdTime(), loaded.createdTime());
		assertEquals(List.of(new BlockPos(1, 64, 2), new BlockPos(3, 70, 4)), loaded.blocks());
	}

	@Test
	void loadIsNoOpWhenFileMissing() throws Exception {
		Path file = tempDir.resolve("missing.json");
		SelectionPresetManager manager = SelectionPresetManager.getInstance();
		manager.savePreset("A", java.util.Set.of(new BlockPos(0, 64, 0)), null);

		SelectionPresetPersistence.load(file, manager);

		assertEquals(1, manager.getPresetCount());
	}

	@Test
	void putPresetPreservesIdOnLoad() throws Exception {
		SelectionPresetManager manager = SelectionPresetManager.getInstance();
		String id = "fixed-id-123";
		manager.putPreset(new SelectionPresetManager.SelectionPreset(
			id,
			"Named",
			null,
			List.of(new BlockPos(5, 64, 5)),
			42L
		));

		Path file = tempDir.resolve("presets.json");
		SelectionPresetPersistence.save(file, manager);
		manager.clear();
		SelectionPresetPersistence.load(file, manager);

		assertEquals(1, manager.getPresetCount());
		SelectionPresetManager.SelectionPreset loaded = manager.getAllPresets().getFirst();
		assertEquals(id, loaded.id());
		assertEquals(42L, loaded.createdTime());
	}
}
