package com.beatblock.selection.collect;

import com.beatblock.selection.BrushShape;
import com.beatblock.selection.ConnectedCellLookup;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionCollectorsTest {

	@Test
	void boxCollectorRejectsOversizedVolume() {
		var result = BoxSelectionCollector.collect(
			null,
			new BlockPos(0, 64, 0),
			new BlockPos(100, 164, 100),
			true,
			10,
			null
		);

		assertTrue(result.failed());
		assertTrue(result.errorMessage().contains("框选体积"));
	}

	@Test
	void lineCollectorRejectsTooManyCandidates() {
		var result = LineSelectionCollector.collect(
			null,
			new BlockPos(0, 64, 0),
			new BlockPos(500, 64, 0),
			4,
			true,
			50,
			null
		);

		assertTrue(result.failed());
		assertTrue(result.errorMessage().contains("线选候选方块超过上限"));
	}

	@Test
	void brushCollectorRejectsOversizedSphere() {
		var result = BrushSelectionCollector.collect(
			null,
			new BlockPos(0, 64, 0),
			BrushShape.SPHERE,
			20,
			true,
			100,
			null
		);

		assertTrue(result.failed());
		assertTrue(result.errorMessage().contains("球体包络"));
	}

	@Test
	void connectedCollectorReportsTruncationNotice() {
		Map<BlockPos, Integer> grid = new HashMap<>();
		for (int x = 0; x < 5; x++) {
			grid.put(new BlockPos(x, 64, 0), 1);
		}
		var result = ConnectedSelectionCollector.collect(
			ConnectedCellLookup.fromMaterialGrid(grid),
			new BlockPos(0, 64, 0),
			null,
			null,
			false,
			false,
			3,
			64,
			null,
			"out of reach",
			"truncated %d selected %d"
		);

		assertFalse(result.failed());
		assertEquals(3, result.blocks().size());
		assertTrue(result.noticeMessage().contains("truncated 3"));
	}
}
