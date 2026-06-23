package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionRegionsTest {

	@Test
	void cuboidVolumeCountsInclusiveBounds() {
		assertEquals(1L, SelectionRegions.cuboidVolume(
			new BlockPos(0, 64, 0),
			new BlockPos(0, 64, 0)
		));
		assertEquals(24L, SelectionRegions.cuboidVolume(
			new BlockPos(0, 64, 0),
			new BlockPos(3, 65, 2)
		));
	}

	@Test
	void cuboidPositionsEnumeratesAllBlocksWithinLimit() {
		List<BlockPos> blocks = SelectionRegions.cuboidPositions(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 1),
			100
		);
		assertEquals(4, blocks.size());
		assertEquals(4, new HashSet<>(blocks).size());
	}

	@Test
	void cuboidPositionsReturnsNullWhenVolumeExceedsMaxBlocks() {
		assertNull(SelectionRegions.cuboidPositions(
			new BlockPos(0, 64, 0),
			new BlockPos(10, 74, 10),
			100
		));
	}

	@Test
	void cuboidPositionsReturnsEmptyForDegenerateInput() {
		assertEquals(0, SelectionRegions.cuboidPositions(null, new BlockPos(1, 1, 1), 10).size());
	}

	@Test
	void containsInBoundsChecksInclusiveCuboid() {
		BlockPos min = new BlockPos(0, 64, 0);
		BlockPos max = new BlockPos(2, 65, 1);
		assertTrue(SelectionRegions.containsInBounds(new BlockPos(1, 64, 1), min, max));
		assertFalse(SelectionRegions.containsInBounds(new BlockPos(3, 64, 0), min, max));
		assertFalse(SelectionRegions.containsInBounds(new BlockPos(0, 64, 0), null, max));
	}
}
