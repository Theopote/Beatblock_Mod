package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageObjectSystemTest {

	private StageObjectSystem system;

	@BeforeEach
	void setUp() {
		system = new StageObjectSystem();
	}

	@Test
	void registerGetAndRemove() {
		StageObject obj = StageObjectSystem.fromBlocks("s1", "Tower", List.of(new BlockPos(0, 64, 0)));
		system.register(obj);

		assertEquals(1, system.size());
		assertEquals(obj, system.get("s1"));

		assertTrue(system.remove("s1"));
		assertNull(system.get("s1"));
	}

	@Test
	void fromSelectionSnapshotComputesCenter() {
		List<BlockPos> blocks = List.of(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0));
		StageObject obj = StageObjectSystem.fromSelectionSnapshot(
			"snap", "Snap", blocks, GroupSortingStrategy.RADIAL, 0.25);

		assertNotNull(obj.getCenter());
		assertEquals(1.5, obj.getCenter().x, 1e-6);
		assertEquals(GroupSortingStrategy.RADIAL, obj.getGroupSpec().getSortingStrategy());
		assertEquals(0.25, obj.getGroupSpec().getStaggerDelaySeconds(), 1e-9);
	}

	@Test
	void clearRemovesAllRegisteredObjects() {
		system.register(StageObjectSystem.fromBlocks("a", "A", List.of(new BlockPos(0, 64, 0))));
		system.register(StageObjectSystem.fromBlocks("b", "B", List.of(new BlockPos(1, 64, 0))));
		system.clear();
		assertEquals(0, system.size());
	}

	@Test
	void fromSelectionCuboidEmbedsCuboidSpec() {
		BlockPos a = new BlockPos(1, 64, 2);
		BlockPos b = new BlockPos(3, 66, 4);
		StageObject obj = StageObjectSystem.fromSelectionCuboid(
			"cube", "Cube", List.of(a, b), a, b, false);
		assertEquals("selection_cuboid", obj.getGroupSpec().getSourceType());
	}
}
