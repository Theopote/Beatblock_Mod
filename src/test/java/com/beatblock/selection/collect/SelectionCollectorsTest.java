package com.beatblock.selection.collect;

import com.beatblock.selection.BrushShape;
import com.beatblock.selection.ConnectedCellLookup;
import com.beatblock.selection.SelectionRegions;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class SelectionCollectorsTest {

	@Test
	void boxCollectorRejectsOversizedVolume() {
		var cornerA = new BlockPos(0, 64, 0);
		var cornerB = new BlockPos(100, 164, 100);
		var result = BoxSelectionCollector.collect(
			null,
			cornerA,
			cornerB,
			true,
			10,
			null
		);

		assertTrue(result.failed());
		assertEquals(
			BBTexts.get(
				"beatblock.selection.error.box.volume_exceeded",
				SelectionRegions.cuboidVolume(cornerA, cornerB),
				10
			),
			result.errorMessage()
		);
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
		assertEquals(
			BBTexts.get("beatblock.selection.error.line.candidates_exceeded", 50),
			result.errorMessage()
		);
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
		long worst = (2L * 20 + 1) * (2L * 20 + 1) * (2L * 20 + 1);
		assertEquals(
			BBTexts.get("beatblock.selection.error.brush.sphere_envelope_exceeded", worst, 100),
			result.errorMessage()
		);
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
