package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionMergeTest {

	@Test
	void newReplacesExistingSelection() {
		LinkedHashSet<BlockPos> current = new LinkedHashSet<>(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		));
		List<BlockPos> incoming = List.of(new BlockPos(5, 64, 5));

		LinkedHashSet<BlockPos> merged = SelectionMerge.apply(current, incoming, SelectionOperation.NEW);

		assertEquals(1, merged.size());
		assertTrue(merged.contains(new BlockPos(5, 64, 5)));
	}

	@Test
	void addUnionsBlocks() {
		LinkedHashSet<BlockPos> current = new LinkedHashSet<>(List.of(new BlockPos(0, 64, 0)));
		List<BlockPos> incoming = List.of(new BlockPos(1, 64, 0), new BlockPos(0, 64, 0));

		LinkedHashSet<BlockPos> merged = SelectionMerge.apply(current, incoming, SelectionOperation.ADD);

		assertEquals(2, merged.size());
	}

	@Test
	void subtractRemovesBlocks() {
		LinkedHashSet<BlockPos> current = new LinkedHashSet<>(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0)
		));

		LinkedHashSet<BlockPos> merged = SelectionMerge.apply(
			current,
			List.of(new BlockPos(1, 64, 0)),
			SelectionOperation.SUBTRACT
		);

		assertEquals(2, merged.size());
		assertTrue(merged.contains(new BlockPos(0, 64, 0)));
		assertTrue(merged.contains(new BlockPos(2, 64, 0)));
	}

	@Test
	void intersectKeepsOverlapOnly() {
		LinkedHashSet<BlockPos> current = new LinkedHashSet<>(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0)
		));
		List<BlockPos> incoming = List.of(
			new BlockPos(1, 64, 0),
			new BlockPos(3, 64, 0)
		);

		LinkedHashSet<BlockPos> merged = SelectionMerge.apply(current, incoming, SelectionOperation.INTERSECT);

		assertEquals(1, merged.size());
		assertTrue(merged.contains(new BlockPos(1, 64, 0)));
	}

	@Test
	void intersectWithEmptyIncomingClearsSelection() {
		LinkedHashSet<BlockPos> current = new LinkedHashSet<>(List.of(new BlockPos(0, 64, 0)));

		LinkedHashSet<BlockPos> merged = SelectionMerge.apply(current, List.of(), SelectionOperation.INTERSECT);

		assertTrue(merged.isEmpty());
	}
}
