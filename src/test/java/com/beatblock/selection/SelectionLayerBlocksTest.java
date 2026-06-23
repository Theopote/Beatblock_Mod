package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionLayerBlocksTest {

	@Test
	void excludeClaimedFiltersMatchingPositions() {
		BlockPos claimed = new BlockPos(0, 64, 0);
		BlockPos free = new BlockPos(1, 64, 0);
		List<BlockPos> input = List.of(claimed, free, new BlockPos(2, 64, 0));

		List<BlockPos> out = SelectionLayerBlocks.excludeClaimed(input, pos -> pos.equals(claimed));

		assertEquals(List.of(free, new BlockPos(2, 64, 0)), out);
	}

	@Test
	void countClaimedReturnsMatchingCount() {
		Set<BlockPos> claimed = Set.of(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0));
		List<BlockPos> input = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0)
		);

		assertEquals(2, SelectionLayerBlocks.countClaimed(input, claimed::contains));
	}
}
